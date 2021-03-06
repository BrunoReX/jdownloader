package jd.gui.swing.jdgui.views.settings.panels.basicauthentication;

import javax.swing.AbstractAction;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.appwork.swing.MigPanel;
import org.appwork.swing.components.ExtButton;
import org.appwork.swing.exttable.utils.MinimumSelectionObserver;
import org.jdownloader.gui.IconKey;
import org.jdownloader.images.AbstractIcon;

import jd.gui.swing.jdgui.views.settings.components.SettingsComponent;
import net.miginfocom.swing.MigLayout;

public class BasicAuthenticationPanel extends JPanel implements SettingsComponent {
    private static final long                     serialVersionUID = 1L;
    private static final BasicAuthenticationPanel INSTANCE         = new BasicAuthenticationPanel();

    /**
     * get the only existing instance of AccountManager. This is a singleton
     * 
     * @return
     */
    public static BasicAuthenticationPanel getInstance() {
        return BasicAuthenticationPanel.INSTANCE;
    }

    private MigPanel  tb;
    private AuthTable table;

    /**
     * Create a new instance of AccountManager. This is a singleton class. Access the only existing instance by using {@link #getInstance()} .
     */
    private BasicAuthenticationPanel() {
        super(new MigLayout("ins 0,wrap 1", "[grow,fill]", "[grow,fill][]"));

        tb = new MigPanel("ins 0", "[][][grow,fill]", "");
        tb.setOpaque(false);
        setOpaque(false);
        table = new AuthTable();
        NewAction na;
        tb.add(new ExtButton(na = new NewAction(table)), "sg 1,height 26!");
        na.putValue(AbstractAction.SMALL_ICON, new AbstractIcon(IconKey.ICON_ADD, 20));
        RemoveAction ra;
        tb.add(new ExtButton(ra = new RemoveAction(table)), "sg 1,height 26!");
        table.getSelectionModel().addListSelectionListener(new MinimumSelectionObserver(table, ra, 1));
        add(new JScrollPane(table));
        add(tb);

    }

    public String getConstraints() {
        return "wmin 10,height 60:n:n,pushy,growy";
    }

    public boolean isMultiline() {
        return false;
    }

    public AuthTable getTable() {
        return table;
    }

}
