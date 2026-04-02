package org.fakester.gateway.popup;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

import com.inductiveautomation.ignition.common.gson.JsonObject;

/**
 * Singleton in-memory store for popup notifications.
 *
 * <p>Supports two delivery modes:
 * <ol>
 *   <li><b>Targeted</b> — popup queued for a specific Perspective session ID.
 *       Used when {@code system.popup.*} is called with an explicit {@code sessionId}.</li>
 *   <li><b>Broadcast</b> — popup delivered to ALL polling clients via a monotonic
 *       sequence number. Used when no {@code sessionId} is specified (the common case).
 *       Each client tracks the last sequence it has seen and asks for items newer than that,
 *       so every connected session independently receives the same broadcast.</li>
 * </ol>
 *
 * <p>No component placement is required. Any browser tab running the module's JS bundle
 * will poll this store and receive popups automatically.
 */
public class PopupSessionStore {

    private static final PopupSessionStore INSTANCE = new PopupSessionStore();

    // -------------------------------------------------------------------------
    // Broadcast state
    // -------------------------------------------------------------------------

    /** Monotonically increasing counter. Each broadcast popup gets a unique sequence. */
    private final AtomicLong broadcastSeq = new AtomicLong(0);

    /**
     * Ordered map of broadcast items keyed by sequence number.
     * ConcurrentSkipListMap keeps items sorted so we can efficiently find
     * entries newer than a given sequence with {@code higherKey(seq)}.
     */
    private final ConcurrentSkipListMap<Long, JsonObject> broadcastItems =
        new ConcurrentSkipListMap<>();

    /** Broadcast items older than this are pruned to prevent unbounded growth. */
    private static final long BROADCAST_TTL_MS = 30_000; // 30 seconds

    // -------------------------------------------------------------------------
    // Targeted (per-session) state
    // -------------------------------------------------------------------------

    /** Private popup queues keyed by Perspective session ID. */
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<JsonObject>> sessionQueues =
        new ConcurrentHashMap<>();

    private PopupSessionStore() {
        // singleton
    }

    public static PopupSessionStore getInstance() {
        return INSTANCE;
    }

    // -------------------------------------------------------------------------
    // Write operations
    // -------------------------------------------------------------------------

    /**
     * Enqueue a popup for a specific Perspective session (targeted delivery).
     * Only the session with this ID will receive the popup.
     *
     * @param sessionId  the Perspective session ID obtained from the script call
     * @param payload    the popup JSON payload
     */
    public void enqueue(String sessionId, JsonObject payload) {
        sessionQueues
            .computeIfAbsent(sessionId, k -> new ConcurrentLinkedQueue<>())
            .add(payload);
    }

    /**
     * Enqueue a popup for ALL polling clients (broadcast delivery).
     * A new sequence number is assigned to the item; every client that polls
     * with a {@code lastBroadcastSeq} lower than this number will receive it.
     *
     * @param payload  the popup JSON payload
     * @return         the assigned broadcast sequence number
     */
    public long enqueueBroadcast(JsonObject payload) {
        long seq = broadcastSeq.incrementAndGet();

        // Tag with internal metadata for TTL pruning
        JsonObject item = payload.deepCopy();
        item.addProperty("_broadcastSeq", seq);
        item.addProperty("_enqueuedAt", System.currentTimeMillis());

        broadcastItems.put(seq, item);
        pruneExpiredBroadcasts();

        return seq;
    }

    // -------------------------------------------------------------------------
    // Read operations
    // -------------------------------------------------------------------------

    /**
     * Poll for the next pending popup for a given session.
     *
     * <p>Priority:
     * <ol>
     *   <li>Checks the private per-session queue first (targeted delivery).</li>
     *   <li>Then checks for any broadcast newer than {@code lastBroadcastSeq}.</li>
     * </ol>
     *
     * <p>Returns {@code null} if nothing is pending.
     *
     * @param sessionId         the Perspective session ID of the polling client (may be null)
     * @param lastBroadcastSeq  the last broadcast sequence the client has already seen
     * @return                  a {@link PollResult} containing the popup and updated seq, or {@code null}
     */
    public PollResult poll(String sessionId, long lastBroadcastSeq) {

        // 1. Check private (targeted) queue for this session first
        if (sessionId != null && !sessionId.isEmpty()) {
            ConcurrentLinkedQueue<JsonObject> queue = sessionQueues.get(sessionId);
            if (queue != null) {
                JsonObject item = queue.poll();
                if (item != null) {
                    // Private popup — broadcast seq unchanged
                    return new PollResult(item, lastBroadcastSeq);
                }
            }
        }

        // 2. Check broadcast queue for anything newer than what the client has seen
        Long nextSeq = broadcastItems.higherKey(lastBroadcastSeq);
        if (nextSeq != null) {
            JsonObject raw = broadcastItems.get(nextSeq);
            if (raw != null) {
                // Return a clean copy without the internal tagging fields
                JsonObject clean = raw.deepCopy();
                clean.remove("_broadcastSeq");
                clean.remove("_enqueuedAt");
                return new PollResult(clean, nextSeq);
            }
        }

        return null;
    }

    // -------------------------------------------------------------------------
    // Maintenance
    // -------------------------------------------------------------------------

    /**
     * Remove broadcast items older than {@link #BROADCAST_TTL_MS}.
     * Called on each new broadcast enqueue to keep memory bounded.
     */
    private void pruneExpiredBroadcasts() {
        long cutoff = System.currentTimeMillis() - BROADCAST_TTL_MS;
        broadcastItems.entrySet().removeIf(entry -> {
            JsonObject item = entry.getValue();
            if (item.has("_enqueuedAt")) {
                return item.get("_enqueuedAt").getAsLong() < cutoff;
            }
            return false;
        });
    }

    /**
     * Clean up all state for a session (e.g. on session close).
     *
     * @param sessionId  the Perspective session ID to remove
     */
    public void cleanup(String sessionId) {
        sessionQueues.remove(sessionId);
    }

    // -------------------------------------------------------------------------
    // Result type
    // -------------------------------------------------------------------------

    /**
     * Immutable result of a {@link #poll} call, bundling the popup payload
     * together with the updated broadcast sequence number the client should
     * use on its next poll.
     */
    public static final class PollResult {

        /** The popup payload to deliver to the client. */
        public final JsonObject popup;

        /**
         * The broadcast sequence number the client should send on its next poll.
         * If a targeted (private) popup was returned, this equals the client's
         * {@code lastBroadcastSeq} unchanged. If a broadcast popup was returned,
         * this equals the sequence number of that broadcast item.
         */
        public final long broadcastSeq;

        public PollResult(JsonObject popup, long broadcastSeq) {
            this.popup = popup;
            this.broadcastSeq = broadcastSeq;
        }
    }
}
