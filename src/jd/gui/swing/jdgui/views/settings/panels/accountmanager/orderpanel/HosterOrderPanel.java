package jd.gui.swing.jdgui.views.settings.panels.accountmanager.orderpanel;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.AbstractAction;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import org.appwork.swing.MigPanel;
import org.appwork.swing.components.ExtButton;
import org.appwork.swing.exttable.utils.MinimumSelectionObserver;
import org.appwork.utils.swing.EDTRunner;
import org.appwork.utils.swing.SwingUtils;
import org.jdownloader.controlling.hosterrule.AccountUsageRule;
import org.jdownloader.controlling.hosterrule.HosterRuleController;
import org.jdownloader.controlling.hosterrule.HosterRuleControllerListener;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.images.AbstractIcon;

import jd.controlling.AccountController;
import jd.controlling.AccountControllerEvent;
import jd.controlling.AccountControllerListener;
import jd.controlling.accountchecker.AccountChecker;
import jd.controlling.accountchecker.AccountCheckerEventListener;
import jd.gui.swing.jdgui.interfaces.SwitchPanel;
import jd.gui.swing.jdgui.views.settings.panels.accountmanager.AccountManager;
import jd.gui.swing.jdgui.views.settings.panels.accountmanager.BuyAction;
import jd.gui.swing.jdgui.views.settings.panels.accountmanager.PremiumAccountTable;
import jd.gui.swing.jdgui.views.settings.panels.accountmanager.RefreshAction;
import net.miginfocom.swing.MigLayout;

public class HosterOrderPanel extends SwitchPanel implements ActionListener, AccountControllerListener, AccountCheckerEventListener, HosterRuleControllerListener {

    private HosterRuleTableModel model;
    private HosterRuleTable      table;
    private MigPanel             tb;

    public HosterOrderPanel(final AccountManager accountManager) {
        super(new MigLayout("ins 0, wrap 1", "[grow,fill]", "[][grow,fill][]"));

        model = new HosterRuleTableModel();
        table = new HosterRuleTable(model);

        JTextArea txt = new JTextArea();
        SwingUtils.setOpaque(txt, false);
        txt.setEditable(false);
        txt.setLineWrap(true);
        txt.setWrapStyleWord(true);
        txt.setFocusable(false);
        // txt.setEnabled(false);
        txt.setText(_GUI.T.HosterOrderPanel_HosterOrderPanel_description_());

        HosterRuleController.getInstance().getEventSender().addListener(this, true);

        AccountController.getInstance().getEventSender().addListener(this, true);
        AccountChecker.getInstance().getEventSender().addListener(this, true);

        tb = new MigPanel("ins 0", "[][][][][grow,fill]", "");
        tb.setOpaque(false);

        NewRuleAction na;
        tb.add(new ExtButton(na = new NewRuleAction()), "sg 1,height 26!");
        na.putValue(AbstractAction.SMALL_ICON, new AbstractIcon(IconKey.ICON_ADD, 20));
        RemoveAction ra;
        tb.add(new ExtButton(ra = new RemoveAction(table)), "sg 1,height 26!");
        table.getSelectionModel().addListSelectionListener(new MinimumSelectionObserver(table, ra, 1));

        tb.add(new ExtButton(new BuyAction((PremiumAccountTable) null)), "sg 2,height 26!");

        tb.add(new ExtButton(new RefreshAction()), "sg 2,height 26!");

        add(txt, "gaptop 0,spanx,growx,pushx,gapbottom 5,wmin 10");
        add(new JScrollPane(table));

        add(tb);

    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // if (searchCombobox.getSelectedItem() == null) {
        // model.refresh(AccountController.getInstance().getPriority(null));
        // } else {
        // model.refresh(AccountController.getInstance().getPriority((DomainInfo) searchCombobox.getSelectedItem()));
        // }

    }

    @Override
    protected void onShow() {
        updateTable();
    }

    @Override
    protected void onHide() {

    }

    @Override
    public void onAccountControllerEvent(AccountControllerEvent event) {
        if (isShown()) {
            updateTable();
        }

    }

    protected void updateTable() {
        new EDTRunner() {
            @Override
            protected void runInEDT() {
                model.resetData();
            }
        };
    }

    @Override
    public void onCheckStarted() {
    }

    @Override
    public void onCheckStopped() {
        if (isShown()) {
            updateTable();
        }
    }

    @Override
    public void onRuleAdded(AccountUsageRule parameter) {
        if (isShown()) {
            updateTable();
            HosterRuleController.getInstance().showEditPanel(parameter);
        }
    }

    @Override
    public void onRuleDataUpdate(AccountUsageRule parameter) {
        if (isShown()) {
            table.repaint();
        }
    }

    @Override
    public void onRuleRemoved(AccountUsageRule parameter) {
        if (isShown()) {
            updateTable();
        }
    }

    @Override
    public void onRuleStructureUpdate() {
        if (isShown()) {
            updateTable();
        }
    }

}
