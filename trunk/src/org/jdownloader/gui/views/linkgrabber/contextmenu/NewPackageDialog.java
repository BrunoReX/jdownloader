package org.jdownloader.gui.views.linkgrabber.contextmenu;

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.File;

import javax.swing.JComponent;
import javax.swing.JLabel;

import jd.gui.swing.jdgui.views.settings.components.FolderChooser;

import org.appwork.swing.MigPanel;
import org.appwork.swing.components.ExtTextField;
import org.appwork.utils.StringUtils;

import org.appwork.utils.swing.dialog.AbstractDialog;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.gui.views.SelectionInfo;
import org.jdownloader.gui.views.components.packagetable.LinkTreeUtils;

public class NewPackageDialog extends AbstractDialog<Object> {

    private SelectionInfo<?, ?> selection;
    private ExtTextField        tf;
    private FolderChooser       fc;
    private String              preSet = null;

    public NewPackageDialog(SelectionInfo<?, ?> selection) {
        super(0, _GUI.T.NewPackageDialog_NewPackageDialog_(), null, null, null);
        this.selection = selection;
    }

    protected int getPreferredWidth() {

        return Math.min(Math.max(tf.getPreferredSize().width, fc.getPreferredSize().width) * 2, getDialog().getParent().getWidth());
    }

    protected void initFocus(final JComponent focus) {

    }

    private String getNewName() {
        String defValue = _GUI.T.MergeToPackageAction_actionPerformed_newpackage_();
        try {
            defValue = selection.getFirstPackage().getName();
        } catch (Throwable e2) {
            // too many unsafe casts. catch problems - just to be sure
            org.appwork.utils.logging2.extmanager.LoggerFactory.getDefaultLogger().log(e2);
        }
        return defValue;
    }

    @Override
    protected Object createReturnValue() {
        return null;
    }

    @Override
    public JComponent layoutDialogContent() {
        MigPanel p = new MigPanel("ins 0,wrap 2", "[][grow,fill]", "[]");

        p.add(new JLabel(_GUI.T.NewPackageDialog_layoutDialogContent_newname_()));
        tf = new ExtTextField();

        tf.setText(getNewName());
        p.add(tf);

        p.add(new JLabel(_GUI.T.NewPackageDialog_layoutDialogContent_saveto()));
        fc = new FolderChooser();

        File path = null;
        if (StringUtils.isNotEmpty(preSet)) {
            fc.setText(preSet);
        } else {

            path = LinkTreeUtils.getRawDownloadDirectory(selection.getFirstPackage());

            if (path != null) {
                fc.setText(path.getAbsolutePath());
            }
        }
        p.add(fc, "pushx,growx");
        return p;
    }

    @Override
    protected void packed() {
        tf.addFocusListener(new FocusListener() {

            @Override
            public void focusLost(FocusEvent e) {
            }

            @Override
            public void focusGained(FocusEvent e) {
                tf.selectAll();
            }
        });

        this.tf.requestFocusInWindow();
        this.tf.selectAll();
    }

    public String getName() {
        return tf.getText();
    }

    public void setDownloadFolder(String path) {
        preSet = path;
    }

    public String getDownloadFolder() {
        return fc.getText();
    }
}
