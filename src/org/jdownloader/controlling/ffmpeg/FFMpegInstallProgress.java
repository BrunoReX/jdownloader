package org.jdownloader.controlling.ffmpeg;

import javax.swing.Icon;

import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.gui.views.downloads.columns.ETAColumn;
import org.jdownloader.images.AbstractIcon;
import org.jdownloader.plugins.PluginTaskID;

import jd.plugins.PluginProgress;

public class FFMpegInstallProgress extends PluginProgress {

    private String message;

    @Override
    public PluginTaskID getID() {
        return PluginTaskID.FFMPEG_INSTALLATION;
    }

    public FFMpegInstallProgress() {
        super(0, 100, null);
        setIcon(new AbstractIcon(IconKey.ICON_LOGO_FFMPEG, 18));

    }

    @Override
    public Icon getIcon(Object requestor) {
        if (requestor instanceof ETAColumn) {
            return null;
        }
        return super.getIcon(requestor);
    }

    @Override
    public String getMessage(Object requestor) {
        if (requestor instanceof ETAColumn) {
            return "";
        }
        return _GUI.T.FFMpegInstallProgress_getMessage();
    }

}
