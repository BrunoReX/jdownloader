package org.jdownloader.gui.views.downloads.action;

import java.awt.event.ActionEvent;
import java.util.ArrayList;

import jd.controlling.packagecontroller.AbstractPackageNode;
import jd.gui.swing.jdgui.MainTabbedPane;

import org.jdownloader.controlling.contextmenu.ActionContext;
import org.jdownloader.controlling.contextmenu.CustomizableTableContextAppAction;
import org.jdownloader.controlling.contextmenu.Customizer;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.gui.views.SelectionInfo;
import org.jdownloader.gui.views.SelectionInfo.PackageView;
import org.jdownloader.gui.views.components.packagetable.PackageControllerTable;
import org.jdownloader.gui.views.downloads.table.DownloadsTable;
import org.jdownloader.gui.views.linkgrabber.LinkGrabberTable;
import org.jdownloader.translate._JDT;

public class CollapseExpandContextAction extends CustomizableTableContextAppAction implements ActionContext {
    public CollapseExpandContextAction() {
        super(true, true);
        setIconKey(IconKey.ICON_LIST);

        setTooltipText(_GUI.T.CollapseExpandAllAction_CollapseExpandAllAction());
        updateLabelAndIcon();
    }

    private void updateLabelAndIcon() {
        if (isSelectionOnly()) {
            setName(_GUI.T.CollapseExpandAllAction_CollapseExpandAllAction_selectiononly());
        } else {
            setName(_GUI.T.CollapseExpandAllAction_CollapseExpandAllAction_());
        }
    }

    @Override
    public void initContextDefaults() {
        super.initContextDefaults();
        setSelectionOnly(false);
    }

    private boolean selectionOnly = false;

    @Override
    public void requestUpdate(Object requestor) {
        super.requestUpdate(requestor);
        updateLabelAndIcon();
    }

    public static String getTranslationForSelectionOnly() {
        return _JDT.T.CollapseExpandContextAction_getTranslationForSelectionOnly();
    }

    @Customizer(link = "#getTranslationForSelectionOnly")
    public boolean isSelectionOnly() {
        return selectionOnly;

    }

    public void setSelectionOnly(boolean selectionOnly) {
        this.selectionOnly = selectionOnly;

    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (getTable() != null) {
            final SelectionInfo<?, ?> selection;
            if (isSelectionOnly()) {
                selection = getTable().getSelectionInfo(true, true);
            } else {
                selection = getTable().getSelectionInfo(false, true);
            }
            boolean allexpaned = true;
            final ArrayList<AbstractPackageNode> list = new ArrayList<AbstractPackageNode>();
            for (PackageView<?, ?> p : selection.getPackageViews()) {
                if (!p.isExpanded()) {
                    allexpaned = false;
                }
                list.add(p.getPackage());
            }
            getTable().getModel().setFilePackageExpand(!allexpaned, list.toArray(new AbstractPackageNode[] {}));
        }
    }

    private PackageControllerTable<?, ?> getTable() {
        if (MainTabbedPane.getInstance().isDownloadView()) {
            return DownloadsTable.getInstance();
        } else if (MainTabbedPane.getInstance().isLinkgrabberView()) {
            return LinkGrabberTable.getInstance();
        }
        return null;
    }

}
