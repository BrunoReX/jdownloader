package org.jdownloader.gui.views.linkgrabber;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.KeyStroke;

import org.appwork.swing.MigPanel;
import org.appwork.swing.components.ExtTextField;
import org.appwork.utils.KeyUtils;
import org.appwork.utils.swing.EDTRunner;
import org.appwork.utils.swing.SwingUtils;
import org.jdownloader.actions.AppAction;
import org.jdownloader.controlling.contextmenu.ActionData;
import org.jdownloader.controlling.contextmenu.MenuItemData;
import org.jdownloader.controlling.contextmenu.MenuLink;
import org.jdownloader.extensions.ExtensionNotLoadedException;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.gui.views.downloads.bottombar.SelfLayoutInterface;

public class LinkgrabberSearchMenuItem extends MenuItemData implements MenuLink, SelfLayoutInterface {
    private static final String SHORTCUT2 = "shortcut";

    public LinkgrabberSearchMenuItem() {
        super();
        setName(_GUI._.FilterMenuItem_FilterMenuItem());
        setIconKey(IconKey.ICON_SEARCH);
        setVisible(true);
        //
    }

    @Override
    public List<AppAction> createActionsToLink() {
        ArrayList<AppAction> ret = new ArrayList<AppAction>();
        LinkgrabberSearchField item = LinkgrabberSearchField.getInstance();
        KeyStroke ks = KeyStroke.getKeyStroke(KeyEvent.VK_F, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask());
        try {

            ActionData ad = this.getActionData();
            Object sc = ad.fetchSetup(SHORTCUT2);
            if (sc != null && sc instanceof String) {

                ks = KeyStroke.getKeyStroke((String) sc);
            }

        } catch (Throwable e) {
        }
        AppAction a = item.getFocusAction();
        a.setAccelerator(ks);
        ret.add(a);
        return ret;
    }

    @Override
    public JComponent createSettingsPanel() {

        ActionData ad = this.getActionData();
        if (ad == null) {
            ad = new ActionData();
            setActionData(ad);
        }

        final ActionData actionData = ad;
        MigPanel p = new MigPanel("ins 0", "[grow,fill][]", "[]");
        SwingUtils.setOpaque(p, false);
        final ExtTextField shortcut = new ExtTextField();
        shortcut.setHelpText(_GUI._.InfoPanel_InfoPanel_shortcuthelp2());
        shortcut.setEditable(false);
        KeyStroke ks = KeyStroke.getKeyStroke(KeyEvent.VK_F, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask());
        try {
            Object sc = ad.fetchSetup(SHORTCUT2);
            if (sc != null && sc instanceof String) {

                ks = KeyStroke.getKeyStroke((String) sc);
            }
        } catch (Throwable e) {
        }
        String msg1 = KeyUtils.getShortcutString(ks, true);

        shortcut.setText(msg1);
        shortcut.addKeyListener(new KeyListener() {

            @Override
            public void keyTyped(KeyEvent e) {
            }

            @Override
            public void keyReleased(KeyEvent e) {
            }

            @Override
            public void keyPressed(KeyEvent event) {
                String msg1 = KeyUtils.getShortcutString(event, true);
                KeyStroke currentShortcut = KeyStroke.getKeyStroke(event.getKeyCode(), event.getModifiersEx());
                shortcut.setText(msg1);
                actionData.putSetup(SHORTCUT2, currentShortcut == null ? null : currentShortcut.toString());

            }

        });

        p.add(new JLabel(_GUI._.InfoPanel_InfoPanel_shortcuts()));
        p.add(shortcut, "newline");
        JButton shortCutReset;
        p.add(shortCutReset = new JButton(new AppAction() {
            {
               setIconKey(IconKey.ICON_RESET);
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                new EDTRunner() {

                    @Override
                    protected void runInEDT() {
                        KeyStroke ks = KeyStroke.getKeyStroke(KeyEvent.VK_F, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask());
                        String msg1 = KeyUtils.getShortcutString(ks, true);
                        shortcut.setText(msg1);
                        actionData.putSetup(SHORTCUT2, null);
                        // managerFrame.fireUpdate();
                    }
                };
            }

        }), "width 22!,height 22!");

        return p;
    }

    @Override
    public String createConstraints() {
        return "height 24!,aligny top,gapleft 2,pushx,growx";
    }

    public JComponent createItem() throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, ClassNotFoundException, NoSuchMethodException, SecurityException, ExtensionNotLoadedException {

        LinkgrabberSearchField ret = LinkgrabberSearchField.getInstance();

        return ret;
    }
}