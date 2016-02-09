package org.jdownloader.gui.views.components.packagetable.context;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

import org.appwork.utils.event.queue.QueueAction;
import org.jdownloader.controlling.contextmenu.CustomizableTableContextAppAction;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.gui.views.downloads.columns.ETAColumn;
import org.jdownloader.gui.views.downloads.table.DownloadsTableModel;
import org.jdownloader.images.AbstractIcon;
import org.jdownloader.plugins.PluginTaskID;

import jd.controlling.TaskQueue;
import jd.controlling.linkchecker.LinkChecker;
import jd.controlling.linkchecker.LinkCheckerHandler;
import jd.controlling.linkcrawler.CheckableLink;
import jd.controlling.linkcrawler.CrawledLink;
import jd.plugins.DownloadLink;
import jd.plugins.PluginProgress;

public class CheckStatusAction extends CustomizableTableContextAppAction {

    public static final class LinkCheckProgress extends PluginProgress {

        public LinkCheckProgress() {
            super(-1, 100, Color.ORANGE);
            icon = new AbstractIcon(IconKey.ICON_HELP, 18);
        }

        @Override
        public String getMessage(Object requestor) {
            if (requestor instanceof ETAColumn) {
                return null;
            }
            return _GUI.T.CheckStatusAction_getMessage_checking();
        }

        @Override
        public PluginTaskID getID() {
            return PluginTaskID.DECRYPTING;
        }

    }

    private static final long serialVersionUID = 6821943398259956694L;

    public CheckStatusAction() {
        super();
        setIconKey(IconKey.ICON_OK);
        setName(_GUI.T.gui_table_contextmenu_check());

    }

    public void actionPerformed(ActionEvent e) {
        if (!isEnabled()) {
            return;
        }
        final List<?> children = getSelection().getChildren();
        TaskQueue.getQueue().add(new QueueAction<Void, RuntimeException>() {

            @Override
            protected Void run() throws RuntimeException {

                final List<CheckableLink> checkableLinks = new ArrayList<CheckableLink>(children.size());
                final LinkCheckProgress linkCheckProgress = new LinkCheckProgress();
                for (Object l : children) {
                    if (l instanceof DownloadLink || l instanceof CrawledLink) {
                        checkableLinks.add(((CheckableLink) l));
                    }
                    if (l instanceof DownloadLink) {
                        final DownloadLink link = (DownloadLink) l;
                        link.addPluginProgress(linkCheckProgress);
                    }
                }
                LinkChecker<CheckableLink> linkChecker = new LinkChecker<CheckableLink>(true);

                linkChecker.setLinkCheckHandler(new LinkCheckerHandler<CheckableLink>() {

                    @Override
                    public void linkCheckDone(CheckableLink l) {
                        if (l instanceof DownloadLink) {
                            final DownloadLink link = (DownloadLink) l;
                            link.removePluginProgress(linkCheckProgress);
                        }
                    }
                });
                linkChecker.check(checkableLinks);
                return null;
            }
        });
        DownloadsTableModel.getInstance().setAvailableColumnVisible(true);
    }
}