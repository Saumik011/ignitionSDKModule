package org.fakester.gateway.endpoint;

import java.util.EnumSet;

import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.dataroutes.RequestContext;
import com.inductiveautomation.ignition.gateway.dataroutes.RouteGroup;
import com.inductiveautomation.perspective.gateway.api.SessionScope;
import com.inductiveautomation.perspective.gateway.comm.Routes;
import jakarta.servlet.http.HttpServletResponse;
import org.fakester.gateway.popup.PopupSessionStore;


/**
 * REST endpoint for the zero-config popup notification system.
 *
 * <p>Exposes a single route:
 * <pre>
 *   GET /data/radcomponents/popup/pending
 *       ?sessionId=&lt;perspectiveSessionId&gt;    (optional — for targeted popups)
 *       &amp;lastBroadcastSeq=&lt;N&gt;               (required — client's last seen broadcast seq)
 * </pre>
 *
 * <p>Response JSON:
 * <pre>
 *   { "popup": { ...payload... }, "broadcastSeq": N }   // popup ready
 *   { "popup": null,             "broadcastSeq": N }   // nothing pending
 * </pre>
 *
 * <p>The client (PopupManager.ts) calls this endpoint every ~1.5 seconds
 * automatically, with no Perspective component placement required.
 * The route requires a valid Perspective session cookie
 * (enforced by {@code Routes.requireSession}).
 */
public final class PopupEndpoints {

    private static final LoggerEx log = LoggerEx.newBuilder().build("rad.gateway.PopupEndpoints");

    private PopupEndpoints() {
        // static utility class
    }

    public static void mountRoutes(RouteGroup routes) {
        routes.newRoute("/popup/pending")
            .type(RouteGroup.TYPE_JSON)
            .handler(PopupEndpoints::fetchPendingPopup)
            .accessControl(Routes.requireSession(EnumSet.of(SessionScope.Client, SessionScope.Designer)))
            .mount();
    }

    /**
     * Returns the next pending popup for the polling client.
     *
     * <p>Query parameters:
     * <ul>
     *   <li>{@code sessionId} — Perspective session ID (optional; enables targeted delivery)</li>
     *   <li>{@code lastBroadcastSeq} — the last broadcast sequence the client has seen (defaults to 0)</li>
     * </ul>
     *
     * <p>The client must advance its stored {@code broadcastSeq} using the value returned
     * in each response, so it doesn't receive the same broadcast popup twice.
     */
    private static JsonObject fetchPendingPopup(RequestContext req, HttpServletResponse res) {

        // Optional: Perspective session ID for targeted popup delivery
        String sessionId = req.getParameter("sessionId");

        // Required: client's last seen broadcast sequence (default 0 for new sessions)
        long lastBroadcastSeq = 0;
        String lastSeqParam = req.getParameter("lastBroadcastSeq");
        if (lastSeqParam != null && !lastSeqParam.isEmpty()) {
            try {
                lastBroadcastSeq = Long.parseLong(lastSeqParam);
            } catch (NumberFormatException ignored) {
                // Bad param — treat as 0 (client will receive any pending broadcasts)
                log.warnf("Invalid lastBroadcastSeq value: '%s' — treating as 0", lastSeqParam);
            }
        }

        JsonObject response = new JsonObject();

        PopupSessionStore.PollResult result =
            PopupSessionStore.getInstance().poll(sessionId, lastBroadcastSeq);

        if (result != null) {
            log.debugf(
                "Delivering popup to session='%s', broadcastSeq=%d",
                sessionId != null ? sessionId : "<broadcast>",
                result.broadcastSeq
            );
            response.add("popup", result.popup);
            response.addProperty("broadcastSeq", result.broadcastSeq);
        } else {
            response.add("popup", null);
            // Echo back the client's own seq so it doesn't reset to 0
            response.addProperty("broadcastSeq", lastBroadcastSeq);
        }

        return response;
    }
}
