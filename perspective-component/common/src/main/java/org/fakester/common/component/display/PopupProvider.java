package org.fakester.common.component.display;

import com.inductiveautomation.ignition.common.jsonschema.JsonSchema;
import com.inductiveautomation.perspective.common.api.ComponentDescriptor;
import com.inductiveautomation.perspective.common.api.ComponentDescriptorImpl;
import org.fakester.common.RadComponents;


/**
 * Component descriptor for the PopupProvider — an invisible component that enables
 * the popup notification system in Perspective sessions.
 * <p>
 * Users add this component once to a docked view. It renders nothing visible but
 * establishes the websocket channel for receiving popup notifications from
 * {@code system.popup.*} scripting functions.
 */
public class PopupProvider {

    public static final String COMPONENT_ID = "rad.display.popupprovider";

    public static final JsonSchema SCHEMA =
        JsonSchema.parse(RadComponents.class.getResourceAsStream("/popup.props.json"));

    public static final ComponentDescriptor DESCRIPTOR = ComponentDescriptorImpl.ComponentBuilder.newBuilder()
        .setPaletteCategory(RadComponents.COMPONENT_CATEGORY)
        .setId(COMPONENT_ID)
        .setModuleId(RadComponents.MODULE_ID)
        .setSchema(SCHEMA)
        .setName("Popup Provider")
        .setDefaultMetaName("popupProvider")
        .addPaletteEntry("", "Popup Provider",
            "Add to a docked view to enable system.popup.* notifications. Renders nothing visible.",
            null, null)
        .setResources(RadComponents.BROWSER_RESOURCES)
        .build();
}
