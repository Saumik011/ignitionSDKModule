package org.fakester.gateway.script;

import java.util.UUID;

import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.script.PyArgParser;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.perspective.gateway.api.PerspectiveContext;
import org.fakester.common.script.AbstractPopupScriptModule;
import org.fakester.gateway.popup.PopupSessionStore;
import org.python.core.PyObject;

/**
 * Gateway-scope implementation of {@code system.popup.*} scripting functions.
 *
 * <p>Two delivery modes:
 * <ol>
 *   <li><b>Broadcast</b> (default — no {@code sessionId} argument): the popup is added to
 *       the broadcast sequence in {@link PopupSessionStore}. Every Perspective browser tab
 *       polling the {@code /popup/pending} endpoint will receive it independently.</li>
 *   <li><b>Targeted</b> ({@code sessionId} argument provided): the popup is queued directly
 *       for that specific Perspective session only.</li>
 * </ol>
 *
 * <p>No component placement is needed. The client-side PopupManager polls automatically
 * for every session as soon as the module JS bundle loads.
 */
public class PopupScriptFunctions extends AbstractPopupScriptModule {

    private static final LoggerEx log = LoggerEx.newBuilder().build("rad.gateway.PopupScriptFunctions");

    /**
     * Stored for potential future use (e.g. enumerating sessions for session-specific
     * operations). Not used in broadcast mode since broadcast is driven by sequence numbers.
     */
    private final PerspectiveContext perspectiveContext;

    /** No-arg constructor (required by Ignition's script module loading). */
    public PopupScriptFunctions() {
        this.perspectiveContext = null;
    }

    /**
     * Constructor used by {@code RadGatewayHook.initializeScriptManager}.
     *
     * @param perspectiveContext  the gateway's Perspective context (stored for future use)
     */
    public PopupScriptFunctions(PerspectiveContext perspectiveContext) {
        this.perspectiveContext = perspectiveContext;
    }

    @Override
    protected void notifyImpl(String type, PyObject[] args, String[] keywords, String methodName) {

        PyArgParser parser = PyArgParser.parseArgs(
            args,
            keywords,
            new String[]{"message", "title", "duration", "blocking", "sessionId"},
            new Class<?>[]{String.class, String.class, Integer.class, Boolean.class, String.class},
            methodName
        );

        String message  = parser.requireString("message");
        String title    = parser.getString("title").orElse("");
        int duration    = parser.getInteger("duration").orElse(3000);
        boolean blocking = parser.getBoolean("blocking").orElse(false);
        String sessionId = parser.getString("sessionId").orElse(null);

        // Build the popup payload
        JsonObject payload = new JsonObject();
        payload.addProperty("type",     type);
        payload.addProperty("message",  message);
        payload.addProperty("title",    title);
        payload.addProperty("duration", duration);
        payload.addProperty("blocking", blocking);
        payload.addProperty("id",       UUID.randomUUID().toString());

        if (sessionId != null && !sessionId.isEmpty()) {
            // ── TARGETED ──────────────────────────────────────────────────────
            // Enqueue for one specific Perspective session.
            // The caller obtained the sessionId via system.perspective.getSessionInfo().
            PopupSessionStore.getInstance().enqueue(sessionId, payload);
            log.debugf("Queued targeted %s popup for session '%s': %s", type, sessionId, message);

        } else {
            // ── BROADCAST ─────────────────────────────────────────────────────
            // Enqueue for ALL polling clients via the broadcast sequence mechanism.
            // Each client independently tracks its last-seen sequence number, so
            // every open Perspective tab receives the popup — no session enumeration needed.
            long seq = PopupSessionStore.getInstance().enqueueBroadcast(payload);
            log.debugf("Broadcast %s popup (seq=%d): %s", type, seq, message);
        }
    }
}
