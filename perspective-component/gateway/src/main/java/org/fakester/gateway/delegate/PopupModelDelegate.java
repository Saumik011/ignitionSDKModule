package org.fakester.gateway.delegate;

import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.perspective.gateway.api.Component;
import com.inductiveautomation.perspective.gateway.api.ComponentModelDelegate;


/**
 * Gateway-side model delegate for the PopupProvider component.
 * <p>
 * Receives popup requests from {@code PopupScriptFunctions} and fires them
 * as events to the browser-side {@code PopupStoreDelegate} over the websocket.
 * <p>
 * This delegate uses the same pattern as {@link MessageComponentModelDelegate}
 * but specialized for popup notifications.
 */
public class PopupModelDelegate extends ComponentModelDelegate {

    /** Event name for sending popup data to the client */
    public static final String POPUP_EVENT = "popup-notification-event";

    public PopupModelDelegate(Component component) {
        super(component);
    }

    @Override
    protected void onStartup() {
        log.infof("PopupProvider delegate started for '%s'", component.getComponentAddressPath());
    }

    @Override
    protected void onShutdown() {
        log.infof("PopupProvider delegate shut down for '%s'", component.getComponentAddressPath());
    }

    /**
     * Send a popup notification to the client-side PopupStoreDelegate.
     */
    public void sendPopup(JsonObject payload) {
        log.debugf("Sending popup to client: %s", payload.toString());
        fireEvent(POPUP_EVENT, payload);
    }

    @Override
    public void fireEvent(String eventName, JsonObject event) {
        this.component.fireEvent("model", eventName, event);
    }
}
