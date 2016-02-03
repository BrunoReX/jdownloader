package org.jdownloader.gui.views.downloads.action;

import java.awt.Image;
import java.awt.event.ActionEvent;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.appwork.utils.ImageProvider.ImageProvider;
import org.jdownloader.controlling.contextmenu.CustomizableTableContextAppAction;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.gui.views.SelectionInfo;
import org.jdownloader.images.AbstractIcon;
import org.jdownloader.images.NewTheme;

import jd.controlling.downloadcontroller.DownloadWatchDog;
import jd.gui.swing.jdgui.JDGui;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;

public class ForceDownloadAction extends CustomizableTableContextAppAction<FilePackage, DownloadLink> {

    private static final long   serialVersionUID = 7107840091963427544L;

    private final static String NAME             = _GUI._.ForceDownloadAction_ForceDownloadAction();
    private final static Icon   ICON             = getICON();

    private final static Icon getICON() {
        final Image add = NewTheme.I().getImage("media-playback-start", 20);
        final Image play = NewTheme.I().getImage("prio_3", 14);
        return new ImageIcon(ImageProvider.merge(add, play, -4, 0, 6, 10));
    }

    public ForceDownloadAction() {
        super();
        setSmallIcon(ICON);
        setName(NAME);
    }

    @Override
    public boolean isEnabled() {
        return super.isEnabled() && DownloadWatchDog.getInstance().getStateMachine().isState(DownloadWatchDog.IDLE_STATE, DownloadWatchDog.RUNNING_STATE, DownloadWatchDog.PAUSE_STATE, DownloadWatchDog.STOPPED_STATE);
    }

    public void actionPerformed(ActionEvent e) {
        if (!isEnabled()) {
            return;
        }
        final SelectionInfo<FilePackage, DownloadLink> selection = getSelection();
        JDGui.help(_GUI._.ForceDownloadAction_actionPerformed_help_title_(), _GUI._.ForceDownloadAction_actionPerformed_help_msg_(), new AbstractIcon(IconKey.ICON_BOTTY_ROBOT_INFO, -1));
        DownloadWatchDog.getInstance().resume(selection.getChildren());
        DownloadWatchDog.getInstance().forceDownload(selection.getChildren());

    }

}