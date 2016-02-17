package org.jdownloader.gui.views.linkgrabber.bottombar;

import org.jdownloader.controlling.contextmenu.ActionData;
import org.jdownloader.controlling.contextmenu.MenuContainer;
import org.jdownloader.controlling.contextmenu.MenuContainerRoot;
import org.jdownloader.controlling.contextmenu.SeparatorData;
import org.jdownloader.controlling.contextmenu.TableContext;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.mainmenu.container.OptionalContainer;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.gui.views.downloads.MenuManagerDownloadTabBottomBar;
import org.jdownloader.gui.views.downloads.bottombar.AbstractBottomBarMenuManager;
import org.jdownloader.gui.views.downloads.bottombar.DeleteContainer;
import org.jdownloader.gui.views.downloads.bottombar.HorizontalBoxItem;
import org.jdownloader.gui.views.downloads.bottombar.QuickSettingsMenuContainer;
import org.jdownloader.gui.views.linkgrabber.LinkgrabberOverviewPanelToggleAction;
import org.jdownloader.gui.views.linkgrabber.LinkgrabberPropertiesToggleAction;
import org.jdownloader.gui.views.linkgrabber.LinkgrabberSearchMenuItem;
import org.jdownloader.gui.views.linkgrabber.LinkgrabberSidebarToggleAction;
import org.jdownloader.gui.views.linkgrabber.actions.ConfirmSelectionBarAction;
import org.jdownloader.gui.views.linkgrabber.actions.ConfirmSelectionBarActionSub;
import org.jdownloader.gui.views.linkgrabber.contextmenu.ClearFilteredLinksAction;
import org.jdownloader.gui.views.linkgrabber.contextmenu.ConfirmLinksContextAction.AutoStartOptions;
import org.jdownloader.gui.views.linkgrabber.contextmenu.RemoveIncompleteArchives;

public class MenuManagerLinkgrabberTabBottombar extends AbstractBottomBarMenuManager {
    private static final MenuManagerLinkgrabberTabBottombar INSTANCE = new MenuManagerLinkgrabberTabBottombar();

    /**
     * get the only existing instance of DownloadListContextMenuManager. This is a singleton
     *
     * @return
     */
    public static MenuManagerLinkgrabberTabBottombar getInstance() {
        return INSTANCE;
    }

    @Override
    public String getFileExtension() {
        return ".jdLGBottomBar";
    }

    @Override
    public synchronized MenuContainerRoot getMenuData() {
        return super.getMenuData();
    }

    @Override
    protected String getStorageKey() {
        return "LinkgrabberTabBottomBar";
    }

    @Override
    public String getName() {
        return _GUI.T.gui_config_menumanager_linkgrabberBottom();
    }

    public static void main(String[] args) {
        System.out.println("abc".equals(null));
    }

    public MenuContainerRoot createDefaultStructure() {
        MenuContainerRoot mr = new MenuContainerRoot();

        MenuManagerDownloadTabBottomBar.fillAddLinks(mr);
        //
        //

        mr.add(ClearLinkgrabberAction.class);

        DeleteContainer delete = new DeleteContainer();

        delete.add(setIconKey(new ActionData(GenericDeleteFromLinkgrabberAction.class).putSetup(GenericDeleteFromLinkgrabberAction.DELETE_DISABLED, true).putSetup(IncludedSelectionSetup.INCLUDE_UNSELECTED_LINKS, true).putSetup(IncludedSelectionSetup.INCLUDE_SELECTED_LINKS, true), IconKey.ICON_REMOVE_DISABLED));

        delete.add(setIconKey(new ActionData(GenericDeleteFromLinkgrabberAction.class).putSetup(GenericDeleteFromLinkgrabberAction.DELETE_OFFLINE, true).putSetup(IncludedSelectionSetup.INCLUDE_UNSELECTED_LINKS, true).putSetup(IncludedSelectionSetup.INCLUDE_SELECTED_LINKS, true), IconKey.ICON_REMOVE_OFFLINE));

        delete.add(new SeparatorData());
        delete.add(new ActionData(RemoveIncompleteArchives.class).putSetup(TableContext.ITEM_VISIBLE_FOR_EMPTY_SELECTION, true));

        delete.add(new ActionData(ClearFilteredLinksAction.class));

        mr.add(delete);
        //
        mr.add(new LinkgrabberSearchMenuItem());
        mr.add(new HorizontalBoxItem());
        mr.add(AddFilteredStuffAction.class);

        mr.add(new LeftRightDividerItem());

        mr.add(new ConfirmButtonMenuItem());

        //
        MenuContainer all = new MenuContainer(_GUI.T.ConfirmOptionsAction_actionPerformed_all(), IconKey.ICON_CONFIRMALL);
        MenuContainer selected = new MenuContainer(_GUI.T.ConfirmOptionsAction_actionPerformed_selected(), IconKey.ICON_CONFIRMSELECTEDLINKS);

        all.add(new ActionData(ConfirmSelectionBarActionSub.class).putSetup(ConfirmSelectionBarAction.AUTO_START, AutoStartOptions.DISABLED.toString()).putSetup(ConfirmSelectionBarAction.SELECTION_ONLY, false));
        all.add(new ActionData(ConfirmSelectionBarActionSub.class).putSetup(ConfirmSelectionBarAction.AUTO_START, AutoStartOptions.ENABLED.toString()).putSetup(ConfirmSelectionBarAction.SELECTION_ONLY, false));
        all.add(new ActionData(ConfirmSelectionBarActionSub.class).putSetup(ConfirmSelectionBarAction.AUTO_START, AutoStartOptions.ENABLED.toString()).putSetup(ConfirmSelectionBarAction.FORCE_START, true).putSetup(ConfirmSelectionBarAction.SELECTION_ONLY, false));

        selected.add(new ActionData(ConfirmSelectionBarActionSub.class).putSetup(ConfirmSelectionBarAction.AUTO_START, AutoStartOptions.DISABLED.toString()));
        selected.add(new ActionData(ConfirmSelectionBarActionSub.class).putSetup(ConfirmSelectionBarAction.AUTO_START, AutoStartOptions.ENABLED.toString()));
        selected.add(new ActionData(ConfirmSelectionBarActionSub.class).putSetup(ConfirmSelectionBarAction.AUTO_START, AutoStartOptions.ENABLED.toString()).putSetup(ConfirmSelectionBarAction.FORCE_START, true));

        MenuContainer popup = new MenuContainer("", null);
        popup.add(all);
        popup.add(selected);
        mr.add(popup);

        QuickSettingsMenuContainer quicksettings = new QuickSettingsMenuContainer();

        quicksettings.add(AddAtTopToggleAction.class);
        quicksettings.add(AutoConfirmToggleAction.class);
        quicksettings.add(AutoStartToggleAction.class);
        quicksettings.add(setOptional(LinkFilterToggleAction.class));

        quicksettings.add(new SeparatorData());
        quicksettings.add((LinkgrabberPropertiesToggleAction.class));
        quicksettings.add((LinkgrabberOverviewPanelToggleAction.class));

        quicksettings.add((LinkgrabberSidebarToggleAction.class));
        quicksettings.add(new SeparatorData());
        quicksettings.add(BottomBarMenuManagerAction.class);
        mr.add(quicksettings);
        OptionalContainer opt;
        mr.add(opt = new OptionalContainer(false));

        opt.add(AutoConfirmStopAction.class);
        return mr;
    }
}
