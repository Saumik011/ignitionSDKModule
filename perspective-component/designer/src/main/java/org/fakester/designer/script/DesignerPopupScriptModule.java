package org.fakester.designer.script;

import com.inductiveautomation.ignition.common.util.LoggerEx;
import org.fakester.common.script.AbstractPopupScriptModule;
import org.python.core.PyObject;

/**
 * Designer-scope stub for system.popup.* functions.
 * <p>
 * Provides autocomplete and documentation in the designer script editor,
 * but logs a warning if actually invoked (popup functions should only
 * be called from Perspective session scripts).
 */
public class DesignerPopupScriptModule extends AbstractPopupScriptModule {

    private static final LoggerEx log = LoggerEx.newBuilder().build("rad.designer.DesignerPopupScriptModule");

    @Override
    protected void notifyImpl(String type, PyObject[] args, String[] keywords, String methodName) {
        log.warn("system.popup." + methodName + "() is not available in the Designer scope. " +
            "This function can only be used in Perspective session scripts.");
    }
}
