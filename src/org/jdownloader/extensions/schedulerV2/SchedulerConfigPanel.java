package org.jdownloader.extensions.schedulerV2;

import java.net.MalformedURLException;
import java.net.URL;

import javax.swing.AbstractAction;
import javax.swing.JScrollPane;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.appwork.swing.MigPanel;
import org.appwork.swing.components.ExtButton;
import org.appwork.swing.exttable.utils.MinimumSelectionObserver;
import org.appwork.utils.swing.SwingUtils;
import org.jdownloader.extensions.ExtensionConfigPanel;
import org.jdownloader.extensions.schedulerV2.gui.ScheduleTableModel;
import org.jdownloader.extensions.schedulerV2.gui.SchedulerTable;
import org.jdownloader.extensions.schedulerV2.gui.actions.CopyAction;
import org.jdownloader.extensions.schedulerV2.gui.actions.EditAction;
import org.jdownloader.extensions.schedulerV2.gui.actions.NewAction;
import org.jdownloader.extensions.schedulerV2.gui.actions.RemoveAction;
import org.jdownloader.gui.IconKey;
import org.jdownloader.images.AbstractIcon;
import org.jdownloader.updatev2.gui.LAFOptions;

import jd.gui.swing.components.linkbutton.JLink;

public class SchedulerConfigPanel extends ExtensionConfigPanel<SchedulerExtension> {

    /**
     *
     */
    private static final long  serialVersionUID = 1L;
    private SchedulerTable     table;
    private MigPanel           myContainer;
    private ScheduleTableModel tableModel;

    public ScheduleTableModel getTableModel() {
        return tableModel;
    }

    public SchedulerConfigPanel(SchedulerExtension extension) {
        super(extension);
        myContainer = new MigPanel("ins 0, wrap 1", "[grow]", "[][]");
        SwingUtils.setOpaque(myContainer, false);
        add(myContainer, "pushx,pushy,growx,growy,spanx,spany");
        initPanel();

    }

    private void initPanel() {
        myContainer.removeAll();
        myContainer.setLayout("ins 0, wrap 1", "[grow]", "[][]");
        JLink lnk;
        try {
            lnk = new JLink("UNDER DEVELOPMENT: Feel free to test this extension. Click here to give Feedback.", new AbstractIcon(IconKey.ICON_URL, 16), new URL("http://board.jdownloader.org/showthread.php?t=59730"));
        } catch (MalformedURLException e1) {
            lnk = null;
        }

        myContainer.add(lnk);

        lnk.setForeground(LAFOptions.getInstance().getColorForErrorForeground());

        table = new SchedulerTable(extension, tableModel = new ScheduleTableModel(this.extension));
        myContainer.add(new JScrollPane(table), "grow");

        MigPanel bottomMenu = new MigPanel("ins 0", "[]", "[]");
        bottomMenu.setLayout("ins 0", "[][][][fill]", "[]");
        bottomMenu.setOpaque(false);
        myContainer.add(bottomMenu);

        NewAction na;
        bottomMenu.add(new ExtButton(na = new NewAction(table)), "sg 1,height 26!");
        na.putValue(AbstractAction.SMALL_ICON, new AbstractIcon(IconKey.ICON_ADD, 20));

        RemoveAction ra;
        bottomMenu.add(new ExtButton(ra = new RemoveAction(table)), "sg 1,height 26!");

        table.getSelectionModel().addListSelectionListener(new MinimumSelectionObserver(table, ra, 1));

        CopyAction ca;
        bottomMenu.add(new ExtButton(ca = new CopyAction(table)), "sg 1,height 26!");

        table.getSelectionModel().addListSelectionListener(new MinimumSelectionObserver(table, ca, 1));

        final EditAction ea;
        bottomMenu.add(new ExtButton(ea = new EditAction(table)), "sg 1,height 26!");
        ea.setEnabled(table.getSelectedRowCount() == 1);
        table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

            @Override
            public void valueChanged(ListSelectionEvent e) {
                ea.setEnabled(table.getSelectedRowCount() == 1);
            }
        });
        tableModel.updateDataModel();

    }

    @Override
    public void save() {
    }

    @Override
    public void updateContents() {

    }

    public void updateLayout() {

        initPanel();
    }

}
