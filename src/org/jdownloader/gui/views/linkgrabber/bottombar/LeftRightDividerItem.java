package org.jdownloader.gui.views.linkgrabber.bottombar;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import javax.swing.JComponent;

import org.appwork.swing.MigPanel;
import org.jdownloader.actions.AppAction;
import org.jdownloader.controlling.contextmenu.MenuItemData;
import org.jdownloader.controlling.contextmenu.MenuLink;
import org.jdownloader.extensions.ExtensionNotLoadedException;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.translate._GUI;

public class LeftRightDividerItem extends MenuItemData implements MenuLink {
    public LeftRightDividerItem() {
        super();
        setName(_GUI.T.LeftRightDividerItem_LeftRightDividerItem());
        setVisible(true);
        setIconKey(IconKey.ICON_RIGHT);
    }

    @Override
    public List<AppAction> createActionsToLink() {
        return null;
    }

    @Override
    public JComponent createSettingsPanel() {
        return null;
    }

    @Override
    public boolean isVisible() {
        return true;
    }

    public JComponent createItem() throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, ClassNotFoundException, NoSuchMethodException, SecurityException, ExtensionNotLoadedException {

        return new MigPanel("ins 0", "[]", "[]");
    }

}
