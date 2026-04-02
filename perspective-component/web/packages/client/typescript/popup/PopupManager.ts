/**
 * PopupManager — Singleton that manages popup notifications in Perspective sessions.
 *
 * Initializes when the module JS bundle loads (called from rad-client-components.ts).
 * ZERO CONFIG: No Perspective component placement required.
 *
 * Delivery mechanism: HTTP polling against /data/radcomponents/popup/pending
 *   - Broadcast popups (system.popup.* with no sessionId) → ALL polling sessions receive it
 *   - Targeted popups (system.popup.* with sessionId="xxx") → only that session receives it
 *
 * The PopupProvider component in the palette is optional and not required for the
 * popup system to function.
 */

import * as React from 'react';
import * as ReactDOM from 'react-dom';
import { PopupOverlay, PopupData } from './PopupOverlay';

/** How often to poll the gateway for pending popups (ms) */
const POLL_INTERVAL_MS = 1500;

/** How many times to retry resolving the Perspective session ID before giving up */
const SESSION_ID_MAX_RETRIES = 20;

/** Delay between each session ID resolution retry (ms) */
const SESSION_ID_RETRY_DELAY_MS = 500;

class PopupManagerImpl {
    private rootElement: HTMLDivElement | null = null;
    private currentTimer: number | null = null;
    private currentPopup: PopupData | null = null;
    private initialized: boolean = false;

    /** Timer handle for the polling loop */
    private pollTimer: number | null = null;

    /**
     * Tracks the last broadcast sequence number seen from the gateway.
     * The gateway uses monotonically increasing sequence numbers for broadcast popups.
     * By sending this back each poll, we ask "give me any broadcasts newer than this".
     */
    private lastBroadcastSeq: number = 0;

    /** The Perspective session ID for this browser tab — used for targeted popups */
    private sessionId: string | null = null;

    /**
     * Initialize the popup system.
     * Called automatically by rad-client-components.ts when the JS bundle loads.
     * This runs for EVERY Perspective session — no component placement needed.
     */
    public initialize(): void {
        if (this.initialized) {
            return;
        }
        this.initialized = true;

        // Create the React portal root attached to document.body
        this.rootElement = document.createElement('div');
        this.rootElement.id = 'rad-popup-root';
        document.body.appendChild(this.rootElement);

        console.log('[PopupManager] Initialized. Resolving session ID and starting poller.');

        // Attempt to resolve the Perspective session ID, then begin polling
        this.resolveSessionId(SESSION_ID_MAX_RETRIES);
    }

    /**
     * Attempts to resolve the Perspective session ID from the client runtime.
     * Retries with a short delay because the PerspectiveClient store may not be
     * ready immediately when the module bundle first loads.
     */
    private resolveSessionId(retriesLeft: number): void {
        const id = this.readSessionIdFromPerspective();
        if (id) {
            this.sessionId = id;
            console.log(`[PopupManager] Session ID resolved: ${id}. Starting poll.`);
            this.schedulePoll(0);
        } else if (retriesLeft > 0) {
            window.setTimeout(
                () => this.resolveSessionId(retriesLeft - 1),
                SESSION_ID_RETRY_DELAY_MS
            );
        } else {
            // Fall back to broadcast-only mode (targeted popups won't be received)
            console.warn(
                '[PopupManager] Could not resolve Perspective session ID. ' +
                'Broadcast popups will still work; targeted popups will not.'
            );
            this.schedulePoll(0);
        }
    }

    /**
     * Reads the Perspective session ID from the global PerspectiveClient object.
     * Tries multiple known property paths for robustness across Perspective versions.
     *
     * The Perspective JS framework is externalized in webpack and available globally
     * as window.PerspectiveClient (mapped via the "externals" config).
     */
    private readSessionIdFromPerspective(): string | null {
        const pc = (window as any).PerspectiveClient;
        if (!pc) {
            return null;
        }

        // Try each known path in order of likelihood
        const candidates = [
            pc?.ClientStore?.sessionId,
            pc?.ClientStore?.props?.sessionId,
            pc?.store?.sessionId,
            pc?.store?.props?.sessionId,
        ];

        for (const candidate of candidates) {
            if (typeof candidate === 'string' && candidate.length > 0) {
                return candidate;
            }
        }

        return null;
    }

    /**
     * Schedules the next poll after the given delay.
     * Cancels any previously scheduled poll to prevent double-polling.
     */
    private schedulePoll(delayMs: number): void {
        if (this.pollTimer !== null) {
            window.clearTimeout(this.pollTimer);
        }
        this.pollTimer = window.setTimeout(() => this.doPoll(), delayMs);
    }

    /**
     * Executes one polling cycle against the gateway REST endpoint.
     * Sends the last broadcast sequence number so the gateway returns only NEW broadcasts.
     */
    private doPoll(): void {
        const params = new URLSearchParams({
            lastBroadcastSeq: String(this.lastBroadcastSeq)
        });

        // Include session ID so the gateway can also check targeted (per-session) queues
        if (this.sessionId) {
            params.set('sessionId', this.sessionId);
        }

        fetch(`/data/radcomponents/popup/pending?${params.toString()}`, {
            credentials: 'include'  // ensures Perspective session cookies are sent
        })
            .then(response => {
                if (!response.ok) {
                    return null;
                }
                return response.json();
            })
            .then(data => {
                if (!data) {
                    return;
                }

                // Always advance broadcastSeq to stay in sync with gateway
                if (typeof data.broadcastSeq === 'number') {
                    this.lastBroadcastSeq = data.broadcastSeq;
                }

                // Show the popup if one was returned
                if (data.popup) {
                    this.show(data.popup as PopupData);
                }
            })
            .catch(err => {
                // Swallow network errors silently — polling will resume normally
                console.debug('[PopupManager] Poll error (will retry):', err);
            })
            .finally(() => {
                // Schedule next poll regardless of success or failure
                this.schedulePoll(POLL_INTERVAL_MS);
            });
    }

    /**
     * Show a popup notification. Replaces any existing popup immediately.
     * Also callable directly (e.g. from PopupProviderDelegate for compatibility).
     */
    public show(data: PopupData): void {
        if (this.currentTimer !== null) {
            window.clearTimeout(this.currentTimer);
            this.currentTimer = null;
        }

        this.currentPopup = data;
        this.render(true);

        const duration = data.duration || 3000;
        this.currentTimer = window.setTimeout(() => {
            this.hide();
        }, duration);
    }

    /**
     * Hide the current popup (triggers fade-out animation in PopupOverlay).
     */
    public hide(): void {
        if (this.currentTimer !== null) {
            window.clearTimeout(this.currentTimer);
            this.currentTimer = null;
        }

        this.currentPopup = null;
        this.render(false);
    }

    /** Render the popup overlay into the portal root. */
    private render(visible: boolean): void {
        if (!this.rootElement) {
            return;
        }

        ReactDOM.render(
            React.createElement(PopupOverlay, {
                data: this.currentPopup,
                visible: visible,
                onClose: () => this.hide()
            }),
            this.rootElement
        );
    }
}

// Singleton — one instance per browser tab
export const PopupManager = new PopupManagerImpl();

// Debug handle (accessible from browser console)
(window as any).__radPopupManager = PopupManager;
