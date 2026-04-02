package org.fakester.common.script;

import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.common.script.builtin.KeywordArgs;
import org.python.core.PyObject;

/**
 * Abstract class defining the system.popup.* scripting interface.
 * <p>
 * Shared between Gateway and Designer scopes. The Gateway provides
 * a real implementation; the Designer provides autocomplete stubs.
 */
public abstract class AbstractPopupScriptModule {

    static {
        BundleUtil.get().addBundle(
            AbstractPopupScriptModule.class.getSimpleName(),
            AbstractPopupScriptModule.class.getClassLoader(),
            AbstractPopupScriptModule.class.getName().replace('.', '/')
        );
    }

    /**
     * system.popup.notify(message, [title], [duration], [blocking], [sessionId])
     * Shows an info-type popup notification.
     */
    @KeywordArgs(
        names = {"message", "title", "duration", "blocking", "sessionId"},
        types = {String.class, String.class, Integer.class, Boolean.class, String.class}
    )
    public void notify(PyObject[] args, String[] keywords) {
        notifyImpl("info", args, keywords, "notify");
    }

    /**
     * system.popup.success(message, [title], [duration], [blocking], [sessionId])
     * Shows a success-type popup notification.
     */
    @KeywordArgs(
        names = {"message", "title", "duration", "blocking", "sessionId"},
        types = {String.class, String.class, Integer.class, Boolean.class, String.class}
    )
    public void success(PyObject[] args, String[] keywords) {
        notifyImpl("success", args, keywords, "success");
    }

    /**
     * system.popup.error(message, [title], [duration], [blocking], [sessionId])
     * Shows an error-type popup notification.
     */
    @KeywordArgs(
        names = {"message", "title", "duration", "blocking", "sessionId"},
        types = {String.class, String.class, Integer.class, Boolean.class, String.class}
    )
    public void error(PyObject[] args, String[] keywords) {
        notifyImpl("error", args, keywords, "error");
    }

    /**
     * system.popup.warning(message, [title], [duration], [blocking], [sessionId])
     * Shows a warning-type popup notification.
     */
    @KeywordArgs(
        names = {"message", "title", "duration", "blocking", "sessionId"},
        types = {String.class, String.class, Integer.class, Boolean.class, String.class}
    )
    public void warning(PyObject[] args, String[] keywords) {
        notifyImpl("warning", args, keywords, "warning");
    }

    /**
     * Implementation method to be overridden by Gateway scope.
     * Designer scope can use a no-op or logging stub.
     */
    protected abstract void notifyImpl(String type, PyObject[] args, String[] keywords, String methodName);
}
