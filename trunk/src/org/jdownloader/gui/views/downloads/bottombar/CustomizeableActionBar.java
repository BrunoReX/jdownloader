package org.jdownloader.gui.views.downloads.bottombar;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Transparency;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.InvocationTargetException;

import javax.swing.AbstractButton;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JSeparator;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import org.appwork.swing.MigPanel;
import org.appwork.swing.components.ExtButton;
import org.appwork.utils.StringUtils;
import org.appwork.utils.swing.EDTRunner;
import org.jdownloader.actions.AppAction;
import org.jdownloader.controlling.contextmenu.CustomizableAppAction;
import org.jdownloader.controlling.contextmenu.MenuContainer;
import org.jdownloader.controlling.contextmenu.MenuContainerRoot;
import org.jdownloader.controlling.contextmenu.MenuItemData;
import org.jdownloader.controlling.contextmenu.MenuLink;
import org.jdownloader.controlling.contextmenu.SeparatorData;
import org.jdownloader.controlling.contextmenu.gui.ExtPopupMenu;
import org.jdownloader.controlling.contextmenu.gui.MenuBuilder;
import org.jdownloader.extensions.ExtensionNotLoadedException;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.views.SelectionInfo;
import org.jdownloader.images.NewTheme;
import org.jdownloader.updatev2.gui.LAFOptions;

import jd.SecondLevelLaunch;
import jd.gui.swing.components.SetIconInterface;
import jd.gui.swing.components.SetLabelInterface;
import jd.gui.swing.jdgui.JDGui;
import net.miginfocom.swing.MigLayout;

public class CustomizeableActionBar extends MigPanel implements PropertyChangeListener {

    private final AbstractBottomBarMenuManager manager;

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("visible".equals(evt.getPropertyName())) {
            manager.refresh();
        }
    }

    public CustomizeableActionBar(AbstractBottomBarMenuManager bottomBarMenuManager) {
        super("ins 0 0 0 0,gap " + LAFOptions.getInstance().getExtension().customizeLayoutGetDefaultGap(), "[]", "[]");
        setOpaque(false);
        manager = bottomBarMenuManager;
        manager.addLink(this);
        SecondLevelLaunch.EXTENSIONS_LOADED.executeWhenReached(new Runnable() {

            @Override
            public void run() {
                new EDTRunner() {
                    @Override
                    protected void runInEDT() {
                        updateGui();
                    }
                };
            }
        });
    }

    public void updateGui() {
        if (!SecondLevelLaunch.EXTENSIONS_LOADED.isReached()) {
            return;
        }
        removeAll();
        MenuContainerRoot items = prepare(manager.getMenuData());
        this.setLayout(new MigLayout("ins 0 0 0 0, gap " + LAFOptions.getInstance().getExtension().customizeLayoutGetDefaultGap(), "[]", "[]"));
        AbstractButton ab;
        // System.out.println(this.getColConstraints(list.length));
        MenuItemData last = null;
        for (MenuItemData menudata : items.getItems()) {
            // "height 24!,aligny top"
            try {
                if (!menudata.isVisible()) {
                    continue;
                }
                if (menudata instanceof SeparatorData) {
                    if (last != null && last instanceof SeparatorData) {
                        // no separator dupes
                        continue;
                    }
                    this.add(new JSeparator(SwingConstants.VERTICAL), "gapleft 10,gapright 10,width 2!,pushy,growy");
                    last = menudata;
                    continue;
                }
                if (menudata._getValidateException() != null) {
                    continue;
                }
                if (menudata.getType() == org.jdownloader.controlling.contextmenu.MenuItemData.Type.CONTAINER) {
                    addContainer(menudata);
                    continue;
                } else if (menudata instanceof MenuLink) {
                    addLink(menudata);
                } else if (menudata.getActionData() != null && menudata.getActionData()._isValidDataForCreatingAnAction()) {
                    addAction(menudata);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        revalidate();
        repaint();
    }

    protected MenuContainerRoot prepare(MenuContainerRoot menuData) {
        return menuData;
    }

    protected boolean isValid(MenuItemData menudata) {
        return true;
    }

    private void addLink(MenuItemData menudata) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, ClassNotFoundException, NoSuchMethodException, SecurityException, ExtensionNotLoadedException {
        final JComponent item = menudata.createItem();
        if (StringUtils.isNotEmpty(menudata.getIconKey())) {
            if (item instanceof SetIconInterface) {
                ((SetIconInterface) item).setIcon(NewTheme.I().getIcon(validateIconKey(menudata.getIconKey()), 20));
            }
        }
        if (StringUtils.isNotEmpty(menudata.getName())) {
            if (item instanceof SetLabelInterface) {
                ((SetLabelInterface) item).setText(menudata.getName());
            }
        }
        if (menudata instanceof SelfLayoutInterface) {
            add(item, ((SelfLayoutInterface) menudata).createConstraints());
        } else {
            add(item, "height 24!");
        }
    }

    private void addAction(MenuItemData menudata) throws ClassNotFoundException, NoSuchMethodException, SecurityException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, ExtensionNotLoadedException {
        CustomizableAppAction action = menudata.createAction();
        // action.setEnabled(true);
        action.removePropertyChangeListener(this);
        action.addPropertyChangeListener(this);
        if (!action.isVisible()) {
            return;
        }
        if (StringUtils.isNotEmpty(menudata.getShortcut())) {
            action.setAccelerator(KeyStroke.getKeyStroke(menudata.getShortcut()));
        } else if (MenuItemData.isEmptyValue(menudata.getShortcut())) {
            action.setAccelerator(null);
        }
        JComponent bt = null;
        if (action instanceof SelfComponentFactoryInterface) {
            action.requestUpdate(this);
            bt = ((SelfComponentFactoryInterface) action).createComponent();
        } else if (action.isToggle()) {
            action.requestUpdate(this);
            bt = new JToggleButton(action);
        } else {
            action.requestUpdate(this);
            bt = new ExtButton(action);
        }
        bt.setEnabled(action.isEnabled());

        if (action instanceof SelfLayoutInterface) {
            add(bt, ((SelfLayoutInterface) action).createConstraints());
        } else if (menudata instanceof SelfLayoutInterface) {
            add(bt, ((SelfLayoutInterface) menudata).createConstraints());
        } else {
            if (StringUtils.isEmpty(action.getName())) {

                add(bt, "height 24!,width 24!,aligny top");
            } else {
                add(bt, "height 24!,aligny top");
            }
        }
    }

    private void addContainer(MenuItemData menudata) {
        if (StringUtils.isEmpty(menudata.getName()) && StringUtils.isEmpty(validateIconKey(menudata.getIconKey()))) {
            ExtButton bt = new ExtButton(createPopupAction(menudata, getComponentCount() > 0 ? getComponent(getComponentCount() - 1) : null)) {
                /**
                 *
                 */
                private static final long serialVersionUID = 1L;

                public void setBounds(int x, int y, int width, int height) {
                    Rectangle bounds = new Rectangle(x, y, width, height);
                    LAFOptions.getInstance().getExtension().customizeBoundsForBottombarPopupButton(bounds);
                    super.setBounds(bounds.x, bounds.y, bounds.width, bounds.height);
                }
            };
            add(bt, "height 24!,width 12!,aligny top");
        } else {
            AppAction action = createPopupAction(menudata, null);
            ExtButton bt = new ExtButton(action);
            if (StringUtils.isEmpty(menudata.getName())) {
                add(bt, "height 24!,width " + (Math.max(24, action.getSmallIcon().getIconWidth() + 6)) + "!,aligny top");
            } else {
                add(bt, "height 24!,aligny top");
            }
        }
    }

    private String validateIconKey(String iconKey) {
        if (MenuItemData.isEmptyValue(iconKey)) {
            return "";
        }
        if (StringUtils.isEmpty(iconKey)) {
            return "";
        }
        return iconKey;
    }

    protected ImageIcon createDropdownImage(boolean b, Image back) {
        Image front = NewTheme.I().getImage(b ? IconKey.ICON_POPUPSMALL : IconKey.ICON_POPDOWNSMALL, -1, false);
        int w = back.getWidth(null);
        int h = back.getHeight(null);
        int xoffsetBack = 0;
        int yoffsetBack = 0;
        int xoffsetFront = back.getWidth(null) + front.getWidth(null) + 1;
        int yoffsetFront = (back.getHeight(null) - front.getHeight(null)) / 2;
        final int width = Math.max(xoffsetBack + back.getWidth(null), xoffsetFront + front.getWidth(null));
        final int height = Math.max(yoffsetBack + back.getHeight(null), yoffsetFront + front.getHeight(null));
        final BufferedImage dest = new BufferedImage(width + 2, height, Transparency.TRANSLUCENT);
        final Graphics2D g2 = dest.createGraphics();
        g2.drawImage(back, xoffsetBack, yoffsetBack, null);
        g2.drawImage(front, xoffsetFront, yoffsetFront, null);

        g2.setStroke(new BasicStroke(1, BasicStroke.CAP_ROUND, BasicStroke.CAP_ROUND));
        g2.setColor(new Color(0, 0, 0, 50));
        g2.drawLine(w + 1, 0, w + 1, h);
        g2.setColor(new Color(255, 255, 255, 50));
        g2.drawLine(w + 2, 0, w + 2, h);
        // just to increase icon width
        // g2.setColor(new Color(0, 0, 0, 100));
        // g2.drawLine(w + 14, 0, w + 14, h);
        g2.dispose();
        return new ImageIcon(dest);
    }

    private AppAction createPopupAction(final MenuItemData menudata, final Component reference) {
        return new AppAction() {
            private Component    positionComp;
            private ExtPopupMenu popup;
            private long         lastHide = 0;

            {
                updateIcon(true);
                setName(menudata.getName());
                setTooltipText(menudata.getName());
                positionComp = reference;
            }

            @Override
            public void actionPerformed(final ActionEvent e) {
                // // this workaround avoids that a second click on settings hides the popup and recreates it afterwards in the
                // // actionperformed method
                long timeSinceLastHide = System.currentTimeMillis() - lastHide;
                if (timeSinceLastHide < 250) {
                    //
                    return;

                }
                popup = new ExtPopupMenu() {

                    public void setVisible(boolean b) {

                        super.setVisible(b);
                        if (!b) {
                            lastHide = System.currentTimeMillis();
                            updateIcon(true);
                        } else {
                            updateIcon(false);
                        }

                    };
                };

                new MenuBuilder(manager, popup, (MenuContainer) menudata) {
                    protected void addAction(final JComponent root, final MenuItemData inst, int index, int size) throws InstantiationException, IllegalAccessException, InvocationTargetException, ClassNotFoundException, NoSuchMethodException, ExtensionNotLoadedException {

                        super.addAction(root, inst, index, size);
                    }
                }.run();

                Insets insets = LAFOptions.getInstance().getExtension().customizePopupBorderInsets();
                Dimension pref = popup.getPreferredSize();
                popup.setPreferredSize(pref);
                Component refComponent = positionComp == null ? ((Component) e.getSource()) : positionComp;
                Point loc = refComponent.getLocation();

                Point converted = SwingUtilities.convertPoint(refComponent, loc, JDGui.getInstance().getMainFrame());

                if (converted.x > JDGui.getInstance().getMainFrame().getWidth() / 2) {
                    // right side
                    popup.show((Component) e.getSource(), -popup.getPreferredSize().width + insets.right + ((Component) e.getSource()).getWidth() + 1, -popup.getPreferredSize().height + insets.bottom);

                } else {
                    popup.show(refComponent, -insets.left - 1, -popup.getPreferredSize().height + insets.bottom);
                }

            }

            private void updateIcon(boolean b) {

                if (StringUtils.isEmpty(validateIconKey(menudata.getIconKey()))) {
                    setSmallIcon(NewTheme.I().getIcon(b ? IconKey.ICON_POPUPSMALL : IconKey.ICON_POPDOWNSMALL, -1));

                } else {
                    setSmallIcon(createDropdownImage(b, NewTheme.I().getImage(validateIconKey(menudata.getIconKey()), 18)));
                }
            }
        };
    }

    protected SelectionInfo<?, ?> getCurrentSelection() {
        return null;
    }
}
