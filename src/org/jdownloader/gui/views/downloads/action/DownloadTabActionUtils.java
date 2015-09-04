package org.jdownloader.gui.views.downloads.action;

import jd.controlling.downloadcontroller.DownloadController;
import jd.controlling.downloadcontroller.DownloadWatchDog;
import jd.gui.swing.jdgui.JDGui;
import jd.gui.swing.jdgui.WarnLevel;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;

import org.appwork.uio.CloseReason;
import org.appwork.utils.formatter.SizeFormatter;
import org.appwork.utils.swing.EDTHelper;
import org.appwork.utils.swing.IconDialog;
import org.jdownloader.controlling.DownloadLinkAggregator;
import org.jdownloader.controlling.FileCreationManager.DeleteOption;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.gui.views.SelectionInfo;
import org.jdownloader.images.NewTheme;
import org.jdownloader.settings.GraphicalUserInterfaceSettings.DeleteFileOptions;
import org.jdownloader.settings.staticreferences.CFG_GUI;
import org.jdownloader.utils.JDFileUtils;

public class DownloadTabActionUtils {

    public static void deleteLinksRequest(final SelectionInfo<FilePackage, DownloadLink> si, final String msg, final DeleteFileOptions mode, final boolean byPassDialog) {

        final DownloadLinkAggregator agg = new DownloadLinkAggregator();
        agg.setMirrorHandlingEnabled(false);
        agg.setLocalFileUsageEnabled(true);
        agg.update(si.getChildren());

        new EDTHelper<Void>() {

            @Override
            public Void edtRun() {
                if (agg.getTotalCount() == 0) {
                    new IconDialog(0, _GUI._.lit_ups_something_is_wrong(), _GUI._.DownloadController_deleteLinksRequest_nolinks(), NewTheme.I().getIcon("robot_sos", 256), null).show();
                    return null;
                }
                boolean byPassDialog2 = byPassDialog;
                WarnLevel level = WarnLevel.LOW;
                switch (mode) {
                case REMOVE_LINKS_AND_DELETE_FILES:
                    if (agg.getBytesLoaded() > 0) {
                        level = WarnLevel.SEVERE;
                    } else {
                        level = WarnLevel.NORMAL;
                    }
                    break;
                case REMOVE_LINKS_AND_RECYCLE_FILES:
                    if (agg.getBytesLoaded() > 0) {
                        level = WarnLevel.SEVERE;
                    } else if (agg.getFinishedCount() != agg.getTotalCount()) {
                        level = WarnLevel.NORMAL;
                    }
                    break;
                case REMOVE_LINKS_ONLY:
                    if (agg.getBytesLoaded() > 0) {
                        level = WarnLevel.SEVERE;
                    } else if (agg.getFinishedCount() != agg.getTotalCount()) {
                        level = WarnLevel.NORMAL;
                    }
                    break;
                }
                if (!JDGui.bugme(level)) {
                    byPassDialog2 = true;
                }
                if (!byPassDialog2 && !CFG_GUI.CFG.isBypassAllRlyDeleteDialogsEnabled()) {
                    final ConfirmDeleteLinksDialog dialog = new ConfirmDeleteLinksDialog(msg + "\r\n" + _GUI._.DeleteSelectionAction_actionPerformed_affected2(agg.getTotalCount(), SizeFormatter.formatBytes(agg.getBytesLoaded()), DownloadController.getInstance().getChildrenCount() - agg.getTotalCount(), agg.getLocalFileCount()), agg.getBytesLoaded());
                    dialog.setRecycleSupported(JDFileUtils.isTrashSupported());
                    dialog.setMode(mode);
                    dialog.show();
                    if (dialog.getCloseReason() == CloseReason.OK) {
                        switch (dialog.getMode()) {
                        case REMOVE_LINKS_ONLY:
                            DownloadController.getInstance().removeChildren(si.getChildren());
                            break;
                        case REMOVE_LINKS_AND_DELETE_FILES:
                            DownloadController.getInstance().removeChildren(si.getChildren());
                            DownloadWatchDog.getInstance().delete(si.getChildren(), DeleteOption.NULL);
                            break;
                        case REMOVE_LINKS_AND_RECYCLE_FILES:
                            DownloadController.getInstance().removeChildren(si.getChildren());
                            DownloadWatchDog.getInstance().delete(si.getChildren(), DeleteOption.RECYCLE);
                            break;
                        }
                    }
                } else {
                    switch (mode) {
                    case REMOVE_LINKS_ONLY:
                        DownloadController.getInstance().removeChildren(si.getChildren());
                        break;
                    case REMOVE_LINKS_AND_DELETE_FILES:
                        DownloadController.getInstance().removeChildren(si.getChildren());
                        DownloadWatchDog.getInstance().delete(si.getChildren(), DeleteOption.NULL);
                        break;
                    case REMOVE_LINKS_AND_RECYCLE_FILES:
                        DownloadController.getInstance().removeChildren(si.getChildren());
                        DownloadWatchDog.getInstance().delete(si.getChildren(), JDFileUtils.isTrashSupported() ? DeleteOption.RECYCLE : DeleteOption.NULL);
                        break;
                    }
                }
                return null;
            }

        }.start(true);
    }
}
