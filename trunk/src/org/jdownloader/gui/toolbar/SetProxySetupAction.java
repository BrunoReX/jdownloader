package org.jdownloader.gui.toolbar;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;

import jd.controlling.proxy.ProxyController;
import jd.gui.swing.jdgui.views.settings.panels.proxy.SaveAsProxyProfileAction;

import org.appwork.utils.StringUtils;
import org.appwork.utils.swing.dialog.Dialog;
import org.jdownloader.controlling.contextmenu.ActionContext;
import org.jdownloader.controlling.contextmenu.CustomizableAppAction;
import org.jdownloader.controlling.contextmenu.Customizer;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.translate._JDT;

public class SetProxySetupAction extends CustomizableAppAction implements ActionContext {

    public SetProxySetupAction() {
        addContextSetup(this);
        setName(_GUI.T.SetProxySetupAction_SetProxySetupAction_());
        setIconKey(IconKey.ICON_PROXY);
    }

    private String path = null;

    public static String getTranslationForPath() {
        return _JDT.T.SetProxySetupAction_getTranslationForPath();
    }

    @Customizer(link = "#getTranslationForPath")
    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
        setEnabled(isValidPath(path));
        updateIcon(path);
    }

    /**
     * @param path
     */
    public void updateIcon(String path) {
        if (StringUtils.equals(ProxyController.getInstance().getLatestProfilePath(), path) && StringUtils.isNotEmpty(path)) {
            setIconKey(IconKey.ICON_PROXY_ROTATE);
        } else {
            setIconKey(IconKey.ICON_PROXY);
        }
    }

    /**
     * @param path
     * @return
     */
    public boolean isValidPath(String path) {
        return new File(path).exists() && path.endsWith(SaveAsProxyProfileAction.JDPROXIES);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            ProxyController.getInstance().importFrom(new File(getPath()));

        } catch (IOException e1) {
            Dialog.getInstance().showExceptionDialog(_GUI.T.lit_error_occured(), e1.getMessage(), e1);
        } finally {
            updateIcon(getPath());
        }
    }

}
