package org.jdownloader.extensions.extraction.contextmenu.downloadlist.action;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.appwork.utils.StringUtils;
import org.appwork.utils.swing.dialog.Dialog;
import org.appwork.utils.swing.dialog.DialogCanceledException;
import org.appwork.utils.swing.dialog.DialogClosedException;
import org.jdownloader.extensions.extraction.Archive;
import org.jdownloader.extensions.extraction.contextmenu.downloadlist.AbstractExtractionContextAction;
import org.jdownloader.gui.IconKey;
import org.jdownloader.images.NewTheme;

public class SetExtractPasswordAction extends AbstractExtractionContextAction {

    public SetExtractPasswordAction() {
        super();
        setName(org.jdownloader.extensions.extraction.translate.T.T.contextmenu_password());
        setIconKey(IconKey.ICON_PASSWORD);
    }

    @Override
    protected void onAsyncInitDone() {
        super.onAsyncInitDone();
    }

    public void actionPerformed(ActionEvent e) {
        final List<Archive> lArchives = getArchives();
        if (!isEnabled() || lArchives == null) {
            return;
        } else {
            try {
                final StringBuilder sb = new StringBuilder();
                final LinkedHashSet<String> list = new LinkedHashSet<String>();
                for (Archive archive : lArchives) {
                    final String finalPassword = archive.getSettings().getFinalPassword();
                    if (finalPassword != null) {
                        list.add(finalPassword);
                    }
                }
                for (Archive archive : lArchives) {
                    final List<String> pws = archive.getSettings().getPasswords();
                    if (pws != null) {
                        list.addAll(pws);
                    }
                }

                if (list != null && list.size() > 0) {
                    for (String s : list) {
                        if (sb.length() > 0) {
                            sb.append("\r\n");
                        }
                        sb.append(s);
                    }
                }
                final String pw;
                if (lArchives.size() > 1) {
                    pw = Dialog.getInstance().showInputDialog(0, org.jdownloader.extensions.extraction.translate.T.T.context_password(), (list == null || list.size() == 0) ? org.jdownloader.extensions.extraction.translate.T.T.context_password_msg_multi() : org.jdownloader.extensions.extraction.translate.T.T.context_password_msg2_multi(sb.toString()), null, NewTheme.getInstance().getIcon("password", 32), null, null);
                } else {
                    pw = Dialog.getInstance().showInputDialog(0, org.jdownloader.extensions.extraction.translate.T.T.context_password(), (list == null || list.size() == 0) ? org.jdownloader.extensions.extraction.translate.T.T.context_password_msg(lArchives.get(0).getName()) : org.jdownloader.extensions.extraction.translate.T.T.context_password_msg2(lArchives.get(0).getName(), sb.toString()), null, NewTheme.getInstance().getIcon("password", 32), null, null);
                }
                if (!StringUtils.isEmpty(pw)) {
                    list.add(pw);
                    for (Archive archive : lArchives) {
                        archive.setPasswords(new ArrayList<String>(list));
                    }
                }
            } catch (DialogClosedException e1) {
                e1.printStackTrace();
            } catch (DialogCanceledException e1) {
                e1.printStackTrace();
            }
        }
    }
}
