package org.jdownloader.gui.views.linkgrabber;

import java.awt.AWTKeyStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.LayoutManager;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
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
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;

import jd.controlling.TaskQueue;
import jd.controlling.downloadcontroller.DownloadController;
import jd.controlling.linkcollector.LinkCollector;
import jd.controlling.linkcollector.LinkCollector.MoveLinksMode;
import jd.controlling.linkcrawler.CrawledLink;
import jd.controlling.linkcrawler.CrawledPackage;
import jd.controlling.linkcrawler.CrawledPackage.TYPE;
import jd.controlling.packagecontroller.AbstractNode;
import jd.gui.swing.jdgui.JDGui;
import jd.gui.swing.jdgui.WarnLevel;
import jd.plugins.DownloadLink.AvailableStatus;
import net.miginfocom.swing.MigLayout;

import org.appwork.swing.MigPanel;
import org.appwork.swing.components.circlebar.CircledProgressBar;
import org.appwork.swing.components.circlebar.ImagePainter;
import org.appwork.swing.exttable.DropHighlighter;
import org.appwork.swing.exttable.ExtCheckBoxMenuItem;
import org.appwork.swing.exttable.ExtColumn;
import org.appwork.swing.exttable.ExtDefaultRowSorter;
import org.appwork.swing.exttable.ExtOverlayRowHighlighter;
import org.appwork.swing.exttable.ExtTable;
import org.appwork.uio.UIOManager;
import org.appwork.utils.StringUtils;
import org.appwork.utils.event.queue.QueueAction;
import org.appwork.utils.logging2.LogSource;
import org.appwork.utils.swing.EDTRunner;
import org.appwork.utils.swing.dialog.Dialog;
import org.jdownloader.actions.AppAction;
import org.jdownloader.controlling.contextmenu.CustomizableAppAction;
import org.jdownloader.controlling.contextmenu.MenuContainer;
import org.jdownloader.controlling.contextmenu.MenuContainerRoot;
import org.jdownloader.controlling.contextmenu.MenuItemData;
import org.jdownloader.controlling.contextmenu.MenuLink;
import org.jdownloader.controlling.contextmenu.SeparatorData;
import org.jdownloader.controlling.contextmenu.gui.ExtPopupMenu;
import org.jdownloader.controlling.contextmenu.gui.MenuBuilder;
import org.jdownloader.extensions.extraction.BooleanStatus;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.gui.views.SelectionInfo;
import org.jdownloader.gui.views.components.packagetable.PackageControllerTable;
import org.jdownloader.gui.views.downloads.table.HorizontalScrollbarAction;
import org.jdownloader.gui.views.linkgrabber.bottombar.MenuManagerLinkgrabberTabBottombar;
import org.jdownloader.gui.views.linkgrabber.contextmenu.ConfirmLinksContextAction;
import org.jdownloader.gui.views.linkgrabber.contextmenu.MenuManagerLinkgrabberTableContext;
import org.jdownloader.images.AbstractIcon;
import org.jdownloader.logging.LogController;
import org.jdownloader.settings.staticreferences.CFG_GENERAL;
import org.jdownloader.settings.staticreferences.CFG_GUI;
import org.jdownloader.settings.staticreferences.CFG_LINKGRABBER;
import org.jdownloader.translate._JDT;
import org.jdownloader.updatev2.gui.LAFOptions;

public class LinkGrabberTable extends PackageControllerTable<CrawledPackage, CrawledLink> {

    private static final long          serialVersionUID   = 8843600834248098174L;

    private HashMap<KeyStroke, Action> shortCutActions;
    private LogSource                  logger;
    private static LinkGrabberTable    INSTANCE;
    private final boolean              dupeManagerEnabled = CFG_GENERAL.CFG.isDupeManagerEnabled();

    public LinkGrabberTable(LinkGrabberPanel linkGrabberPanel, final LinkGrabberTableModel tableModel) {
        super(tableModel);
        INSTANCE = this;
        this.addRowHighlighter(new DropHighlighter(null, new Color(27, 164, 191, 75)));

        if (dupeManagerEnabled) {
            this.addRowHighlighter(new ExtOverlayRowHighlighter(null, LAFOptions.getInstance().getColorForLinkgrabberDupeHighlighter()) {

                @Override
                public boolean doHighlight(ExtTable<?> extTable, int row) {
                    final AbstractNode object = tableModel.getObjectbyRow(row);
                    if (object != null && object instanceof CrawledLink) {
                        return DownloadController.getInstance().hasDownloadLinkByID(((CrawledLink) object).getLinkID());
                    }
                    return false;
                }

            });
        }
        this.setTransferHandler(new LinkGrabberTableTransferHandler(this));
        this.setDragEnabled(true);
        this.setDropMode(DropMode.ON_OR_INSERT_ROWS);
        logger = LogController.getInstance().getLogger(LinkGrabberTable.class.getName());

        final MigPanel loaderPanel = new MigPanel("ins 0,wrap 1", "[grow,fill]", "[grow,fill][]");
        // loaderPanel.setPreferredSize(new Dimension(200, 200));

        loaderPanel.setOpaque(false);
        loaderPanel.setBackground(null);

        final CircledProgressBar loader = new CircledProgressBar() {
            public int getAnimationFPS() {
                return 25;
            }
        };

        loader.setValueClipPainter(new ImagePainter(new AbstractIcon(IconKey.ICON_BOTTY_ROBOT, 256), 1.0f));

        loader.setNonvalueClipPainter(new ImagePainter(new AbstractIcon(IconKey.ICON_BOTTY_ROBOT, 256), 0.1f));
        ((ImagePainter) loader.getValueClipPainter()).setBackground(null);
        ((ImagePainter) loader.getValueClipPainter()).setForeground(null);
        loader.setIndeterminate(true);

        loaderPanel.add(loader);

        final JProgressBar ph = new JProgressBar();

        ph.setString(_GUI.T.DownloadsTable_DownloadsTable_init_plugins());

        LinkCollector.CRAWLERLIST_LOADED.executeWhenReached(new Runnable() {

            @Override
            public void run() {
                new EDTRunner() {

                    @Override
                    protected void runInEDT() {
                        ph.setString(_GUI.T.LinkGrabberTable_LinkGrabberTable_object_wait_for_loading_links());

                    }

                };
            }
        });

        ph.setStringPainted(true);
        ph.setIndeterminate(true);
        loaderPanel.add(ph, "alignx center");
        // loaderPanel.setSize(400, 400);

        final LayoutManager orgLayout = getLayout();
        final Component rendererPane = getComponent(0);

        setLayout(new MigLayout("ins 0", "[grow]", "[grow]"));
        removeAll();
        add(loaderPanel, "alignx center,aligny 20%");
        LinkCollector.CRAWLERLIST_LOADED.executeWhenReached(new Runnable() {

            @Override
            public void run() {
                removeLoaderPanel(loaderPanel, orgLayout, rendererPane);
            }
        });
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
        } else if (e.getID() == MouseEvent.MOUSE_RELEASED) {
            if (e.getButton() == MouseEvent.BUTTON2) {
                if ((e.getModifiers() & InputEvent.CTRL_MASK) == 0) {
                    if ((e.getModifiers() & InputEvent.SHIFT_MASK) == 0) {
                        // middle click
                        final int row = rowAtPoint(e.getPoint());
                        final AbstractNode obj = this.getModel().getObjectbyRow(row);
                        if (LinkGrabberTable.this.isRowSelected(row)) {
                            // clicked on a selected row. let's confirm them all
                            ConfirmLinksContextAction.confirmSelection(MoveLinksMode.MANUAL, getSelectionInfo(true, true), org.jdownloader.settings.staticreferences.CFG_LINKGRABBER.LINKGRABBER_AUTO_START_ENABLED.isEnabled(), false, false, null, BooleanStatus.FALSE, CFG_LINKGRABBER.CFG.getDefaultOnAddedOfflineLinksAction(), CFG_LINKGRABBER.CFG.getDefaultOnAddedDupesLinksAction());
                        } else {
                            // clicked on a not-selected row. only add the context item
                            ConfirmLinksContextAction.confirmSelection(MoveLinksMode.MANUAL, new SelectionInfo<CrawledPackage, CrawledLink>(obj), org.jdownloader.settings.staticreferences.CFG_LINKGRABBER.LINKGRABBER_AUTO_START_ENABLED.isEnabled(), false, false, null, BooleanStatus.FALSE, CFG_LINKGRABBER.CFG.getDefaultOnAddedOfflineLinksAction(), CFG_LINKGRABBER.CFG.getDefaultOnAddedDupesLinksAction());

                        }

                    }
                }
            }
        }
        super.processMouseEvent(e);
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

                    CFG_GUI.HORIZONTAL_SCROLLBARS_IN_LINKGRABBER_TABLE_ENABLED.setValue(true);
                    setColumnSaveID("hBAR");
                    setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
                    sp.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
                    sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

                }
            }
        };

    }

    protected void removeLoaderPanel(final MigPanel loaderPanel, final LayoutManager orgLayout, final Component rendererPane) {
        new EDTRunner() {

            @Override
            protected void runInEDT() {
                remove(loaderPanel);
                setLayout(orgLayout);

                loaderPanel.setVisible(false);
                add(rendererPane);
                revalidate();
                repaint();
            }
        };
    }

    public void sortPackageChildren(ExtDefaultRowSorter<AbstractNode> rowSorter, String nextSortIdentifier) {
        // TODO:
        // set LinkGrabberTableModel.setTRistate....to false and implement sorter here
    }

    @Override
    protected boolean onSingleClick(MouseEvent e, AbstractNode obj) {
        if (dupeManagerEnabled && obj != null && obj instanceof CrawledLink && DownloadController.getInstance().hasDownloadLinkByID(((CrawledLink) obj).getLinkID())) {
            JDGui.help(_GUI.T.LinkGrabberTable_onSingleClick_dupe_title(), _GUI.T.LinkGrabberTable_onSingleClick_dupe_msg(), new AbstractIcon(IconKey.ICON_COPY, 32));
        }
        return super.onSingleClick(e, obj);
    }

    protected boolean onHeaderSortClick(final MouseEvent event, final ExtColumn<AbstractNode> oldColumn, final String oldIdentifier, ExtColumn<AbstractNode> newColumn) {
        if (((LinkGrabberTableModel) getModel()).isTristateSorterEnabled()) {
            return false;
        }

        //
        if (JDGui.bugme(WarnLevel.NORMAL)) {
            UIOManager.I().showConfirmDialog(UIOManager.LOGIC_DONT_SHOW_AGAIN_IGNORES_CANCEL | Dialog.STYLE_SHOW_DO_NOT_DISPLAY_AGAIN, _JDT.T.getNextSortIdentifier_sort_warning_rly_title_(), _JDT.T.getNextSortIdentifier_sort_warning_rly_msg(newColumn.getName()), new AbstractIcon(IconKey.ICON_HELP, 32), _JDT.T.basics_yes(), _JDT.T.basics_no(), "org.jdownloader.gui.views.linkgrabber.LinkGrabberTable");
        }
        sortPackageChildren(newColumn.getRowSorter(), getModel().getNextSortIdentifier(newColumn.getSortOrderIdentifier()));

        return true;
    }

    @Override
    public boolean isSearchEnabled() {
        return false;
    }

    @Override
    protected boolean onDoubleClick(final MouseEvent e, final AbstractNode obj) {

        return false;
    }

    @Override
    protected JPopupMenu onContextMenu(final JPopupMenu popup, final AbstractNode contextObject, final java.util.List<AbstractNode> selection, final ExtColumn<AbstractNode> column, MouseEvent event) {
        long t = System.currentTimeMillis();
        ExtPopupMenu root = new ExtPopupMenu();
        MenuContainerRoot md = MenuManagerLinkgrabberTableContext.getInstance().getMenuData();

        new MenuBuilder(MenuManagerLinkgrabberTableContext.getInstance(), root, md).run();
        // createLayer(root, md);

        return root;
    }

    protected JPopupMenu columnControlMenu(final ExtColumn<AbstractNode> extColumn) {
        JPopupMenu popup = super.columnControlMenu(extColumn);
        popup.add(new JSeparator());
        popup.add(new ExtCheckBoxMenuItem(new HorizontalScrollbarAction(this, CFG_GUI.HORIZONTAL_SCROLLBARS_IN_LINKGRABBER_TABLE_ENABLED)));
        return popup;
    }

    @Override
    protected boolean onShortcutDelete(final java.util.List<AbstractNode> selectedObjects, final KeyEvent evt, final boolean direct) {
        final SelectionInfo<CrawledPackage, CrawledLink> selection = getSelectionInfo();
        TaskQueue.getQueue().add(new QueueAction<Void, RuntimeException>() {

            @Override
            protected Void run() throws RuntimeException {
                final List<CrawledLink> nodesToDelete = new ArrayList<CrawledLink>();
                boolean containsOnline = false;
                for (final CrawledLink dl : selection.getChildren()) {
                    final CrawledPackage parentNode = dl.getParentNode();
                    if (parentNode != null) {
                        nodesToDelete.add(dl);
                        if ((TYPE.OFFLINE == parentNode.getType() || TYPE.POFFLINE == parentNode.getType())) {
                            continue;
                        }
                        if (dl.getDownloadLink().getAvailableStatus() != AvailableStatus.FALSE) {
                            containsOnline = true;
                        }
                    }
                }
                LinkCollector.requestDeleteLinks(nodesToDelete, containsOnline, _GUI.T.GenericDeleteSelectedToolbarAction_updateName_object_selected_all(), evt.isControlDown(), false, false, false, false);
                return null;
            }
        });
        return true;
    }

    @Override
    protected boolean updateMoveButtonEnabledStatus() {
        return super.updateMoveButtonEnabledStatus();
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
    public ExtColumn<AbstractNode> getExpandCollapseColumn() {
        return LinkGrabberTableModel.getInstance().expandCollapse;
    }

    @Override
    public Set<AWTKeyStroke> getFocusTraversalKeys(int id) {
        // important to make ctrl+tab and ctrl+shift+tab work for the main tabbed pane
        return new HashSet<AWTKeyStroke>();
    }

    @SuppressWarnings("unchecked")
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
        fillActions(MenuManagerLinkgrabberTableContext.getInstance().getMenuData());
        fillActions(MenuManagerLinkgrabberTabBottombar.getInstance().getMenuData());

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
                continue;
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

    protected void linkAction(final InputMap input, final InputMap input2, final InputMap input3, final ActionMap actions, AppAction action, KeyStroke keystroke) {
        if (action != null && keystroke != null) {
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

    public static LinkGrabberTable getInstance() {
        return INSTANCE;
    }

    public void linkAction(AppAction focusAction, KeyStroke ks) {
        if (focusAction == null) {
            return;
        }
        final InputMap input = getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        final InputMap input2 = getInputMap(JComponent.WHEN_FOCUSED);
        final InputMap input3 = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);

        final ActionMap actions = getActionMap();
        this.linkAction(input, input2, input3, actions, focusAction, ks);
    }

}
