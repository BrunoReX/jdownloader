package jd.gui.swing.jdgui.views.settings.panels.linkgrabberfilter.editdialog;

import java.awt.Component;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;

import org.appwork.swing.MigPanel;
import org.appwork.swing.exttable.ExtTableModel;
import org.appwork.utils.swing.SwingUtils;
import org.appwork.utils.swing.dialog.Dialog;
import org.appwork.utils.swing.dialog.DialogCanceledException;
import org.appwork.utils.swing.dialog.DialogClosedException;
import org.appwork.utils.swing.dialog.dimensor.RememberLastDialogDimension;
import org.appwork.utils.swing.dialog.locator.RememberAbsoluteDialogLocator;
import org.jdownloader.controlling.filter.LinkFilterController;
import org.jdownloader.controlling.filter.LinkgrabberFilterRule;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.images.AbstractIcon;

import jd.controlling.linkcrawler.CrawledLink;
import jd.gui.swing.jdgui.views.settings.panels.linkgrabberfilter.test.SingleFilterResultTableModel;
import jd.gui.swing.jdgui.views.settings.panels.linkgrabberfilter.test.TestWaitDialog;
import jd.gui.swing.laf.LookAndFeelController;

public class FilterRuleDialog extends ConditionDialog<LinkgrabberFilterRule> {

    private LinkgrabberFilterRule rule;

    public FilterRuleDialog(LinkgrabberFilterRule filterRule) {
        super();
        this.rule = filterRule;
        setTitle(_GUI.T.FilterRuleDialog_FilterRuleDialog_title_());
        setLocator(new RememberAbsoluteDialogLocator(getClass().getSimpleName()));
        setDimensor(new RememberLastDialogDimension(getClass().getSimpleName()));
    }

    protected void runTest(String text) {
        TestWaitDialog d;
        try {

            LinkFilterController lfc = LinkFilterController.createEmptyTestInstance();
            LinkgrabberFilterRule rule = getCurrentCopy();
            rule.setEnabled(true);
            lfc.add(rule);

            java.util.List<CrawledLink> ret = Dialog.getInstance().showDialog(d = new TestWaitDialog(text, _GUI.T.FilterRuleDialog_runTest_title_(rule.toString()), lfc) {

                @Override
                protected ExtTableModel<CrawledLink> createTableModel() {
                    return new SingleFilterResultTableModel();
                }

            });
        } catch (DialogClosedException e) {
            e.printStackTrace();
        } catch (DialogCanceledException e) {
            e.printStackTrace();
        }

    }

    @Override
    protected MigPanel createHeader(String string) {
        MigPanel ret = new MigPanel("ins 0", "[21,fill][][grow,fill]", "[]");
        ret.add(new JSeparator());
        JLabel label;
        ret.add(SwingUtils.toBold(label = new JLabel(string)));
        label.setIcon(new AbstractIcon(IconKey.ICON_LINKGRABBER, 14));
        ret.add(new JSeparator());
        return ret;
    }

    public static void main(String[] args) {
        try {
            LookAndFeelController.getInstance().setUIManager();
            Dialog.getInstance().showDialog(new FilterRuleDialog(new LinkgrabberFilterRule()));
        } catch (DialogClosedException e) {
            e.printStackTrace();
        } catch (DialogCanceledException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected LinkgrabberFilterRule createReturnValue() {
        return rule;
    }

    @Override
    protected void setReturnmask(boolean b) {
        super.setReturnmask(b);
        if (b) {
            save(this.rule);
        }
    }

    /**
     * Returns a Linkgrabberfilter representing current settings. does NOT save the original one
     *
     * @return
     */
    private LinkgrabberFilterRule getCurrentCopy() {
        LinkgrabberFilterRule ret = this.rule.duplicate();
        save(ret);
        return ret;
    }

    private void save(LinkgrabberFilterRule rule) {
        rule.setFilenameFilter(getFilenameFilter());
        rule.setPackagenameFilter(getPackagenameFilter());
        rule.setHosterURLFilter(getHosterFilter());
        rule.setName(getName());
        rule.setFilesizeFilter(getFilersizeFilter());
        rule.setSourceURLFilter(getSourceFilter());
        rule.setFiletypeFilter(getFiletypeFilter());
        rule.setOriginFilter(getOriginFilter());
        rule.setConditionFilter(getConditionFilter());
        rule.setOnlineStatusFilter(getOnlineStatusFilter());
        rule.setPluginStatusFilter(getPluginStatusFilter());
        rule.setAccept(false);
        rule.setTestUrl(getTxtTestUrl());
        rule.setIconKey(getIconKey());

    }

    private void updateGUI() {

        setIconKey(rule.getIconKey());
        setFilenameFilter(rule.getFilenameFilter());
        setPackagenameFilter(rule.getPackagenameFilter());

        setHosterFilter(rule.getHosterURLFilter());
        setName(rule.getName());
        setOriginFilter(rule.getOriginFilter());
        setConditionFilter(rule.getConditionFilter());
        setFilesizeFilter(rule.getFilesizeFilter());
        setOnlineStatusFilter(rule.getOnlineStatusFilter());
        setPluginStatusFilter(rule.getPluginStatusFilter());
        setSourceFilter(rule.getSourceURLFilter());
        setFiletypeFilter(rule.getFiletypeFilter());
        txtTestUrl.setText(rule.getTestUrl());
    }

    protected String getIfText() {
        return _GUI.T.FilterRuleDialog_getIfText_();
    }

    @Override
    public JComponent layoutDialogContent() {
        MigPanel ret = (MigPanel) super.layoutDialogContent();
        // ret.add(createHeader(_GUI.T.FilterRuleDialog_layoutDialogContent_then()),
        // "gaptop 10, spanx,growx,pushx");

        updateGUI();
        JScrollPane sp = new JScrollPane(ret);
        sp.setBorder(null);
        if (rule.isStaticRule()) {
            okButton.setEnabled(false);
            okButton.setText(_GUI.T.PackagizerFilterRuleDialog_layoutDialogContent_cannot_modify_());
            disable(ret);
        }
        return sp;
    }

    private void disable(JComponent ret) {

        ret.setEnabled(false);
        for (Component c : ret.getComponents()) {
            if (c instanceof JComponent) {
                disable((JComponent) c);
            }
        }
    }
}
