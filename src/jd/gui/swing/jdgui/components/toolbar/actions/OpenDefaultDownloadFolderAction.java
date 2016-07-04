package jd.gui.swing.jdgui.components.toolbar.actions;

import java.awt.event.ActionEvent;
import java.io.File;

import org.appwork.storage.config.JsonConfig;
import org.appwork.utils.os.CrossSystem;
import org.jdownloader.controlling.packagizer.PackagizerController;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.toolbar.action.AbstractToolBarAction;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.settings.GeneralSettings;

public class OpenDefaultDownloadFolderAction extends AbstractToolBarAction {

    public OpenDefaultDownloadFolderAction() {
        setIconKey(IconKey.ICON_SAVE);
    }

    public void actionPerformed(ActionEvent e) {
        final String dlDir = JsonConfig.create(GeneralSettings.class).getDefaultDownloadFolder();
        if (dlDir == null) {
            return;
        }
        String str = PackagizerController.replaceDynamicTags(dlDir, "packagename", null);
        /* we want to open the dlDir and not its parent folder/select it */
        File file = new File(str);
        while (file != null && !file.exists()) {
            file = file.getParentFile();
        }
        CrossSystem.openFile(file);
    }

    @Override
    public boolean isEnabled() {
        return CrossSystem.isOpenFileSupported();
    }

    @Override
    protected String createTooltip() {
        return _GUI.T.action_open_dlfolder_tooltip();
    }

}
