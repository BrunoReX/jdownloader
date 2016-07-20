package org.jdownloader.gui.views.downloads.table;

import java.awt.AWTKeyStroke;
import java.awt.Color;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.DropMode;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.TransferHandler;

import jd.controlling.TaskQueue;
import jd.controlling.packagecontroller.AbstractNode;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import net.miginfocom.swing.MigLayout;

import org.appwork.swing.exttable.DropHighlighter;
import org.appwork.swing.exttable.ExtCheckBoxMenuItem;
import org.appwork.swing.exttable.ExtColumn;
import org.appwork.utils.StringUtils;
import org.appwork.utils.event.queue.QueueAction;
import org.appwork.utils.logging2.LogSource;
import org.appwork.utils.os.CrossSystem;
import org.appwork.utils.swing.EDTRunner;
import org.appwork.utils.swing.dialog.Dialog;
import org.jdownloader.actions.AppAction;
import org.jdownloader.controlling.contextmenu.CustomizableAppAction;
import org.jdownloader.controlling.contextmenu.MenuContainer;
import org.jdownloader.controlling.contextmenu.MenuItemData;
import org.jdownloader.controlling.contextmenu.MenuLink;
import org.jdownloader.controlling.contextmenu.SeparatorData;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.helpdialogs.HelpDialog;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.gui.views.SelectionInfo;
import org.jdownloader.gui.views.components.packagetable.PackageControllerTable;
import org.jdownloader.gui.views.downloads.MenuManagerDownloadTabBottomBar;
import org.jdownloader.gui.views.downloads.action.DownloadTabActionUtils;
import org.jdownloader.gui.views.downloads.contextmenumanager.MenuManagerDownloadTableContext;
import org.jdownloader.images.AbstractIcon;
import org.jdownloader.logging.LogController;
import org.jdownloader.settings.GraphicalUserInterfaceSettings.DeleteFileOptions;
import org.jdownloader.settings.staticreferences.CFG_GUI;

public class DownloadsTable extends PackageControllerTable<FilePackage, DownloadLink> {

    private static final long          serialVersionUID = 8843600834248098174L;
    private HashMap<KeyStroke, Action> shortCutActions;
    private final LogSource            logger;
    private static DownloadsTable      INSTANCE         = null;

    public DownloadsTable(final DownloadsTableModel tableModel) {
        super(tableModel);
        INSTANCE = this;

        this.addRowHighlighter(new DropHighlighter(null, new Color(27, 164, 191, 75)));
        this.setTransferHandler(new DownloadsTableTransferHandler(this));
        this.setDragEnabled(true);
        this.setDropMode(DropMode.ON_OR_INSERT_ROWS);
        logger = LogController.getInstance().getLogger(DownloadsTable.class.getName());
        setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        setLayout(new MigLayout("ins 0", "[grow]", "[grow]"));

    }

    protected void fireColumnModelUpdate() {
        super.fireColumnModelUpdate();
        new EDTRunner() {

            @Override
            protected void runInEDT() {

                boolean alllocked = true;
                for (ExtColumn<?> c : getModel().getColumns()) {
                    if (c.isResizable()) {
                        alllocked = false;
                        break;
                    }
                }
                if (alllocked) {
                    JScrollPane sp = (JScrollPane) getParent().getParent();

                    CFG_GUI.HORIZONTAL_SCROLLBARS_IN_DOWNLOAD_TABLE_ENABLED.setValue(true);
                    setColumnSaveID("hBAR");
                    setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
                    sp.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
                    sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

                }
            }
        };

    }

    protected JPopupMenu columnControlMenu(final ExtColumn<AbstractNode> extColumn) {
        JPopupMenu popup = super.columnControlMenu(extColumn);
        popup.add(new JSeparator());
        popup.add(new ExtCheckBoxMenuItem(new HorizontalScrollbarAction(this, CFG_GUI.HORIZONTAL_SCROLLBARS_IN_DOWNLOAD_TABLE_ENABLED)));
        return popup;
    }

    protected boolean onDoubleClick(final MouseEvent e, final AbstractNode obj) {
        return false;
    }

    @Override
    public boolean isSearchEnabled() {
        return false;
    }

    @Override
    protected boolean onShortcutDelete(final java.util.List<AbstractNode> selectedObjects, final KeyEvent evt, final boolean direct) {
        final SelectionInfo<FilePackage, DownloadLink> selectionInfo = getSelectionInfo();
        TaskQueue.getQueue().add(new QueueAction<Void, RuntimeException>() {

            @Override
            protected Void run() throws RuntimeException {
                DownloadTabActionUtils.deleteLinksRequest(selectionInfo, _GUI.T.RemoveSelectionAction_actionPerformed_(), DeleteFileOptions.REMOVE_LINKS_ONLY, evt.isControlDown());
                return null;
            }
        });
        return true;

    }

    @Override
    protected JPopupMenu onContextMenu(final JPopupMenu popup, final AbstractNode contextObject, final java.util.List<AbstractNode> selection, ExtColumn<AbstractNode> column, MouseEvent ev) {
        /* split selection into downloadlinks and filepackages */
        return DownloadTableContextMenuFactory.getInstance().create(this, popup, contextObject, selection, column, ev);
    }

    @Override
    protected boolean onHeaderSortClick(final MouseEvent e1, final ExtColumn<AbstractNode> oldSortColumn, String oldSortId, ExtColumn<AbstractNode> newColumn) {

        // own thread to
        new Timer(100, new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                Timer t = (Timer) e.getSource();
                t.stop();
                if (oldSortColumn == getModel().getSortColumn()) {
                    return;
                }
                if (getModel().getSortColumn() != null) {
                    if (CFG_GUI.CFG.isHelpDialogsEnabled()) {
                        HelpDialog.show(e1.getLocationOnScreen(), "downloadtabe_sortwarner", Dialog.STYLE_SHOW_DO_NOT_DISPLAY_AGAIN, _GUI.T.DownloadsTable_actionPerformed_sortwarner_title(getModel().getSortColumn().getName()), _GUI.T.DownloadsTable_actionPerformed_sortwarner_text(), new AbstractIcon(IconKey.ICON_SORT, 32));
                    }
                }

            }
        }).start();
        return false;
    }

    @Override
    protected boolean onShortcutCopy(java.util.List<AbstractNode> selectedObjects, KeyEvent evt) {
        if (evt.isAltDown() || evt.isMetaDown() || evt.isAltGraphDown() || evt.isShiftDown()) {
            return false;
        }
        TransferHandler.getCopyAction().actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "copy"));
        return true;
    }

    @Override
    protected boolean onShortcutCut(java.util.List<AbstractNode> selectedObjects, KeyEvent evt) {
        if (evt.isAltDown() || evt.isMetaDown() || evt.isAltGraphDown() || evt.isShiftDown()) {
            return false;
        }
        TransferHandler.getCutAction().actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "cut"));
        return true;
    }

    @Override
    protected boolean onShortcutPaste(java.util.List<AbstractNode> selectedObjects, KeyEvent evt) {
        if (evt.isAltDown() || evt.isMetaDown() || evt.isAltGraphDown() || evt.isShiftDown()) {
            return false;
        }
        TransferHandler.getPasteAction().actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "paste"));
        return true;
    }

    @Override
    protected boolean isDeleteFinalSelectionTrigger(KeyStroke ks) {
        if (super.isDeleteFinalSelectionTrigger(ks)) {
            return true;
        }
        if (CrossSystem.isMac()) {
            if (ks == KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask() | InputEvent.CTRL_MASK | InputEvent.SHIFT_MASK)) {
                return true;
            }
        }
        if (ks == KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask() | InputEvent.SHIFT_MASK)) {
            return true;
        }
        return false;
    }

    @Override
    protected boolean isDeleteSelectionTrigger(KeyStroke ks) {
        if (super.isDeleteSelectionTrigger(ks)) {
            return true;
        }
        if (CrossSystem.isMac()) {
            if (ks == KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask() | InputEvent.CTRL_MASK)) {
                return true;
            }
        }
        if (ks == KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask())) {
            return true;
        }

        return false;
    }

    @Override
    protected boolean processKeyBinding(KeyStroke stroke, KeyEvent evt, int condition, boolean pressed) {
        boolean actionNotified = false;
        try {
            final InputMap map = getInputMap(condition);
            final ActionMap am = getActionMap();

            if (map != null && am != null && isEnabled()) {
                final Object binding = map.get(stroke);
                final Action action = (binding == null) ? null : am.get(binding);
                if (action != null) {
                    if (action instanceof CustomizableAppAction) {
                        ((CustomizableAppAction) action).requestUpdate(this);
                    }
                    if (!action.isEnabled()) {
                        Toolkit.getDefaultToolkit().beep();
                    } else {
                        actionNotified = SwingUtilities.notifyAction(action, stroke, evt, this, evt.getModifiers());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return actionNotified || super.processKeyBinding(stroke, evt, condition, pressed);
    }

    @Override
    protected void processMouseEvent(final MouseEvent e) {
        // a left-click with mouse on empty space under the rows selects last row to improve user experience
        // like dragging the mouse to select rows
        if (e.getID() == MouseEvent.MOUSE_PRESSED) {
            if (SwingUtilities.isLeftMouseButton(e) && !isExpandToggleEvent(e)) {
                if (getSelectionModel().getValueIsAdjusting()) {
                    if (rowAtPoint(e.getPoint()) < 0) {
                        final int rowCount = this.getRowCount();
                        if (rowCount > 0) {
                            this.getSelectionModel().setAnchorSelectionIndex(rowCount - 1);
                        }
                    }
                }
            }
        }
        super.processMouseEvent(e);
    }

    @Override
    public ExtColumn<AbstractNode> getExpandCollapseColumn() {
        return DownloadsTableModel.getInstance().expandCollapse;
    }

    @Override
    public Set<AWTKeyStroke> getFocusTraversalKeys(int id) {
        // important to make ctrl+tab and ctrl+shift+tab work for the main tabbed pane
        return new HashSet<AWTKeyStroke>();
    }

    public void updateContextShortcuts() {

        final InputMap input = getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        final InputMap input2 = getInputMap(JComponent.WHEN_FOCUSED);
        final InputMap input3 = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        final ActionMap actions = getActionMap();

        if (shortCutActions != null) {
            for (Entry<KeyStroke, Action> ks : shortCutActions.entrySet()) {
                Object binding = input.get(ks.getKey());
                input.remove(ks.getKey());
                input2.remove(ks.getKey());
                input3.remove(ks.getKey());
                actions.remove(binding);

            }
        }

        shortCutActions = new HashMap<KeyStroke, Action>();
        fillActions(MenuManagerDownloadTableContext.getInstance().getMenuData());
        fillActions(MenuManagerDownloadTabBottomBar.getInstance().getMenuData());

    }

    private void fillActions(MenuContainer menuData) {
        if (!menuData._isValidated()) {
            return;
        }
        final InputMap input = getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        final InputMap input2 = getInputMap(JComponent.WHEN_FOCUSED);
        final InputMap input3 = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);

        final ActionMap actions = getActionMap();

        for (MenuItemData mi : menuData.getItems()) {
            if (!mi._isValidated()) {
                return;
            }
            if (mi instanceof MenuContainer) {
                fillActions((MenuContainer) mi);
            } else if (mi instanceof SeparatorData) {
                continue;
            } else if (mi instanceof MenuLink) {
                List<AppAction> actionsList = ((MenuLink) mi).createActionsToLink();
                if (actionsList != null) {
                    for (AppAction action : actionsList) {
                        KeyStroke keystroke = (KeyStroke) action.getValue(Action.ACCELERATOR_KEY);
                        if (keystroke != null) {
                            linkAction(input, input2, input3, actions, action, keystroke);
                        }

                    }
                }
                continue;
            } else {
                AppAction action;
                try {
                    if (mi.getActionData() == null || !mi.getActionData()._isValidDataForCreatingAnAction()) {
                        continue;
                    }
                    action = mi.createAction();
                    KeyStroke keystroke;
                    if (StringUtils.isNotEmpty(mi.getShortcut())) {
                        keystroke = KeyStroke.getKeyStroke(mi.getShortcut());
                        if (keystroke != null) {
                            action.setAccelerator(keystroke);
                        }
                    } else if (MenuItemData.isEmptyValue(mi.getShortcut())) {
                        action.setAccelerator(null);
                    }
                    keystroke = (KeyStroke) action.getValue(Action.ACCELERATOR_KEY);
                    linkAction(input, input2, input3, actions, action, keystroke);
                    if (action instanceof CustomizableAppAction) {
                        List<KeyStroke> moreShortCuts = ((CustomizableAppAction) action).getAdditionalShortcuts(keystroke);
                        if (moreShortCuts != null) {
                            for (KeyStroke ks : moreShortCuts) {
                                if (ks != null) {
                                    linkAction(input, input2, input3, actions, action, ks);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        }
    }

    /**
     * @param input
     * @param input2
     * @param input3
     * @param actions
     * @param action
     * @param keystroke
     */
    public void linkAction(final InputMap input, final InputMap input2, final InputMap input3, final ActionMap actions, AppAction action, KeyStroke keystroke) {
        if (action != null && (keystroke) != null) {
            String key = "CONTEXT_ACTION_" + keystroke;
            try {
                Object old = input.get(keystroke);
                if (old != null && action.getClass() != actions.get(old).getClass()) {
                    logger.warning("Duplicate Shortcuts: " + action + " overwrites " + actions.get(old) + "(" + old + ")" + " for keystroke " + keystroke);
                }
            } catch (Exception e) {
                logger.log(e);
            }
            try {
                Object old = input2.get(keystroke);
                if (old != null && action.getClass() != actions.get(old).getClass()) {
                    logger.warning("Duplicate Shortcuts: " + action + " overwrites " + actions.get(old) + "(" + old + ")" + " for keystroke " + keystroke);
                }
            } catch (Exception e) {
                logger.log(e);
            }
            try {
                Object old = input3.get(keystroke);
                if (old != null && action.getClass() != actions.get(old).getClass()) {
                    logger.warning("Duplicate Shortcuts: " + action + " overwrites " + actions.get(old) + "(" + old + ")" + " for keystroke " + keystroke);
                }
            } catch (Exception e) {
                logger.log(e);
            }

            logger.info(keystroke + " -> " + action);

            input.put(keystroke, key);
            input2.put(keystroke, key);
            input3.put(keystroke, key);
            actions.put(key, action);
            shortCutActions.put(keystroke, action);

        }
    }

    public static DownloadsTable getInstance() {
        return INSTANCE;
    }

}
