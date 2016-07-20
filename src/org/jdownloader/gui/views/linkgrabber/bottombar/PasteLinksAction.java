package org.jdownloader.gui.views.linkgrabber.bottombar;

import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;

import javax.swing.TransferHandler;

import jd.controlling.ClipboardMonitoring;
import jd.controlling.ClipboardMonitoring.ClipboardContent;
import jd.controlling.linkcollector.LinkCollectingJob;
import jd.controlling.linkcollector.LinkOrigin;
import jd.gui.swing.jdgui.MainTabbedPane;
import jd.gui.swing.jdgui.interfaces.View;

import org.appwork.utils.swing.dialog.Dialog;
import org.appwork.utils.swing.dialog.DialogCanceledException;
import org.appwork.utils.swing.dialog.DialogClosedException;
import org.jdownloader.controlling.contextmenu.ActionContext;
import org.jdownloader.controlling.contextmenu.CustomizableAppAction;
import org.jdownloader.controlling.contextmenu.Customizer;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.gui.views.components.packagetable.PackageControllerTable;
import org.jdownloader.gui.views.components.packagetable.dragdrop.PackageControllerTableTransferable;
import org.jdownloader.gui.views.downloads.DownloadsView;
import org.jdownloader.gui.views.downloads.table.DownloadsTable;
import org.jdownloader.gui.views.linkgrabber.LinkGrabberTable;
import org.jdownloader.gui.views.linkgrabber.LinkGrabberView;
import org.jdownloader.gui.views.linkgrabber.actions.AddLinksProgress;
import org.jdownloader.translate._JDT;

public class PasteLinksAction extends CustomizableAppAction implements ActionContext {

    public static final String DEEP_DECRYPT_ENABLED = "deepDecryptEnabled";
    private boolean            deepDecryptEnabled   = false;

    public static String getTranslationForDeepDecryptEnabled() {
        return _JDT.T.PasteLinksAction_getTranslationForDeepDecryptEnabled();
    }

    @Customizer(link = "#getTranslationForDeepDecryptEnabled")
    public boolean isDeepDecryptEnabled() {
        return deepDecryptEnabled;
    }

    public void setDeepDecryptEnabled(boolean deepDecryptEnabled) {
        this.deepDecryptEnabled = deepDecryptEnabled;
        update();
    }

    private void update() {
        if (isDeepDecryptEnabled()) {
            setName(_GUI.T.PasteLinksAction_PasteLinksAction_deep());
            setIconKey(IconKey.ICON_CLIPBOARD);
        } else {
            setName(_GUI.T.PasteLinksAction_PasteLinksAction());
            setIconKey(IconKey.ICON_CLIPBOARD);
        }
    }

    public PasteLinksAction() {
        update();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        processPaste(isDeepDecryptEnabled());
    }

    public static void processPaste(final boolean deepDecrypt) {
        final View view = MainTabbedPane.getInstance().getSelectedView();
        final Transferable transferable = ClipboardMonitoring.getTransferable();
        if ((view instanceof DownloadsView || view instanceof LinkGrabberView) && transferable.isDataFlavorSupported(PackageControllerTableTransferable.FLAVOR)) {
            final PackageControllerTable<?, ?> table;
            if (view instanceof DownloadsView) {
                table = DownloadsTable.getInstance();
            } else {
                table = LinkGrabberTable.getInstance();
            }
            TransferHandler.getPasteAction().actionPerformed(new ActionEvent(table, ActionEvent.ACTION_PERFORMED, "paste"));
            return;
        }
        new Thread("Add Links Thread") {
            @Override
            public void run() {
                final ClipboardContent content = ClipboardMonitoring.getINSTANCE().getCurrentContent(transferable);
                final LinkCollectingJob crawljob = new LinkCollectingJob(LinkOrigin.PASTE_LINKS_ACTION.getLinkOriginDetails(), content != null ? content.getContent() : null);
                if (content != null) {
                    crawljob.setCustomSourceUrl(content.getBrowserURL());
                }
                crawljob.setDeepAnalyse(deepDecrypt);
                final AddLinksProgress d = new AddLinksProgress(crawljob);
                if (d.isHiddenByDontShowAgain()) {
                    d.getAddLinksDialogThread(crawljob, null).start();
                } else {
                    try {
                        Dialog.getInstance().showDialog(d);
                    } catch (DialogClosedException e1) {
                        e1.printStackTrace();
                    } catch (DialogCanceledException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        }.start();
    }
}
