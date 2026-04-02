/**
 * PopupOverlay — React component for rendering modal popup notifications.
 *
 * Displays a centered modal card with type-based styling (info, success, error, warning),
 * optional backdrop for blocking mode, and CSS fade-in/out animations.
 */

import * as React from 'react';

export interface PopupData {
    type: 'info' | 'success' | 'error' | 'warning';
    message: string;
    title?: string;
    duration?: number;
    blocking?: boolean;
    id?: string;
}

interface PopupOverlayProps {
    data: PopupData | null;
    visible: boolean;
    onClose: () => void;
}

interface PopupOverlayState {
    animating: boolean;
    fadeOut: boolean;
}

const TYPE_ICONS: Record<string, string> = {
    info: 'ℹ️',
    success: '✅',
    error: '❌',
    warning: '⚠️'
};

const TYPE_TITLES: Record<string, string> = {
    info: 'Information',
    success: 'Success',
    error: 'Error',
    warning: 'Warning'
};

export class PopupOverlay extends React.Component<PopupOverlayProps, PopupOverlayState> {

    private fadeOutTimer: number | null = null;

    constructor(props: PopupOverlayProps) {
        super(props);
        this.state = {
            animating: false,
            fadeOut: false
        };
    }

    componentDidUpdate(prevProps: PopupOverlayProps): void {
        // New popup appeared
        if (this.props.visible && !prevProps.visible) {
            this.setState({ animating: true, fadeOut: false });
        }

        // Popup disappearing — trigger fade out
        if (!this.props.visible && prevProps.visible) {
            this.setState({ fadeOut: true });
            this.fadeOutTimer = window.setTimeout(() => {
                this.setState({ animating: false, fadeOut: false });
            }, 300); // match CSS animation duration
        }
    }

    componentWillUnmount(): void {
        if (this.fadeOutTimer !== null) {
            window.clearTimeout(this.fadeOutTimer);
        }
    }

    render(): React.ReactNode {
        const { data, visible, onClose } = this.props;
        const { animating, fadeOut } = this.state;

        // Nothing to render
        if (!data && !animating) {
            return null;
        }

        const popupData = data || ({} as PopupData);
        const type = popupData.type || 'info';
        const title = popupData.title || TYPE_TITLES[type] || 'Notification';
        const message = popupData.message || '';
        const blocking = popupData.blocking || false;
        const icon = TYPE_ICONS[type] || '';

        const containerClass = [
            'rad-popup-container',
            visible && !fadeOut ? 'rad-popup-fade-in' : '',
            fadeOut ? 'rad-popup-fade-out' : ''
        ].filter(Boolean).join(' ');

        const modalClass = [
            'rad-popup-modal',
            `rad-popup--${type}`
        ].join(' ');

        return (
            <div className={containerClass}>
                {blocking && (
                    <div className="rad-popup-backdrop" onClick={onClose} />
                )}
                <div className={modalClass}>
                    <div className="rad-popup-header">
                        <span className="rad-popup-icon">{icon}</span>
                        <span className="rad-popup-title">{title}</span>
                        <button
                            className="rad-popup-close"
                            onClick={onClose}
                            aria-label="Close"
                        >
                            ×
                        </button>
                    </div>
                    <div className="rad-popup-body">
                        <p className="rad-popup-message">{message}</p>
                    </div>
                    <div className="rad-popup-progress">
                        <div
                            className={`rad-popup-progress-bar rad-popup-progress--${type}`}
                            style={{
                                animationDuration: `${popupData.duration || 3000}ms`
                            }}
                        />
                    </div>
                </div>
            </div>
        );
    }
}
