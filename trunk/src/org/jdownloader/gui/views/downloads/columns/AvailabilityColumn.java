package org.jdownloader.gui.views.downloads.columns;

import javax.swing.Icon;
import javax.swing.JPopupMenu;

import jd.controlling.linkcrawler.CrawledLink;
import jd.controlling.linkcrawler.CrawledPackage;
import jd.controlling.packagecontroller.AbstractNode;
import jd.controlling.packagecontroller.AbstractPackageNode;
import jd.controlling.packagecontroller.ChildrenView;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;

import org.appwork.storage.config.ValidationException;
import org.appwork.storage.config.events.GenericConfigEventListener;
import org.appwork.storage.config.handler.KeyHandler;
import org.appwork.swing.exttable.ExtColumn;
import org.appwork.swing.exttable.ExtDefaultRowSorter;
import org.appwork.swing.exttable.columns.ExtTextColumn;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.images.NewTheme;
import org.jdownloader.settings.staticreferences.CFG_GUI;

public class AvailabilityColumn extends ExtTextColumn<AbstractNode> implements GenericConfigEventListener<Boolean> {

    private class ColumnHelper {
        private Icon   icon   = null;
        private String string = null;
    }

    /**
     *
     */
    private static final long serialVersionUID = 1L;
    private final String      nothing          = "";
    private final Icon        unknown;
    private final Icon        online;
    private final Icon        offline;
    private final Icon        mixed;
    private ColumnHelper      columnHelper     = new ColumnHelper();
    private boolean           textVisible;

    public JPopupMenu createHeaderPopup() {

        return FileColumn.createColumnPopup(this, getMinWidth() == getMaxWidth() && getMaxWidth() > 0);

    }

    public AvailabilityColumn() {
        super(_GUI.T.AvailabilityColumn_AvailabilityColumn());
        unknown = NewTheme.I().getIcon(IconKey.ICON_HELP, 16);
        online = NewTheme.I().getIcon(IconKey.ICON_TRUE, 16);
        mixed = NewTheme.I().getIcon(IconKey.ICON_TRUE_ORANGE, 16);
        offline = NewTheme.I().getIcon(IconKey.ICON_ERROR, 16);

        CFG_GUI.AVAILABLE_COLUMN_TEXT_VISIBLE.getEventSender().addListener(this, true);
        textVisible = CFG_GUI.CFG.isAvailableColumnTextVisible();
        this.setRowSorter(new ExtDefaultRowSorter<AbstractNode>() {

            @Override
            public int compare(final AbstractNode o1, final AbstractNode o2) {
                String o1s = getTooltipText(o1);
                String o2s = getTooltipText(o2);
                if (o1s == null) {
                    o1s = "";
                }
                if (o2s == null) {
                    o2s = "";
                }
                if (o1s.matches("[0-9]*\\/[0-9]* .*") && o2s.matches("[0-9]*\\/[0-9]* .*")) {
                    int o1i = Integer.parseInt(o1s.split("/")[0]);
                    int o2i = Integer.parseInt(o2s.split("/")[0]);
                    if (this.getSortOrderIdentifier() != ExtColumn.SORT_ASC) {
                        return o1i < o2i ? -1 : 1;
                    } else {
                        return o1i > o2i ? -1 : 1;
                    }
                } else {
                    return o1s.compareTo(o2s);
                }

            }

        });
    }

    @Override
    protected boolean isDefaultResizable() {
        return false;
    }

    @Override
    protected void prepareColumn(AbstractNode value) {
        if (value instanceof DownloadLink) {
            columnHelper.string = null;
            AvailableStatus status = ((DownloadLink) value).getAvailableStatus();
            if (status == null) {
                status = AvailableStatus.UNCHECKED;
            }
            if (textVisible) {
                columnHelper.string = status.getExplanation();
            }
            switch (status) {
            case TRUE:
                columnHelper.icon = online;
                return;
            case FALSE:
                columnHelper.icon = offline;
                return;
            default:
                columnHelper.icon = unknown;
                return;
            }
        } else if (value instanceof AbstractPackageNode) {
            ChildrenView view = ((AbstractPackageNode) value).getView();
            columnHelper.string = view.getMessage(this);
            switch (view.getAvailability()) {
            case MIXED:
                columnHelper.icon = mixed;
                return;
            case OFFLINE:
                columnHelper.icon = offline;
                return;
            case ONLINE:
                columnHelper.icon = online;
                return;
            case UNKNOWN:
                columnHelper.icon = unknown;
                return;
            }
        } else if (value instanceof CrawledLink) {
            columnHelper.string = null;
            DownloadLink dl = ((CrawledLink) value).getDownloadLink();
            if (dl != null) {
                AvailableStatus status = dl.getAvailableStatus();
                if (status == null) {
                    status = AvailableStatus.UNCHECKED;
                }
                if (textVisible) {
                    columnHelper.string = status.getExplanation();
                }
                switch (status) {
                case TRUE:
                    columnHelper.icon = online;
                    return;
                case FALSE:
                    columnHelper.icon = offline;
                    return;
                default:
                    columnHelper.icon = unknown;
                    return;
                }
            }
            columnHelper.icon = unknown;
            return;
        }
    }

    @Override
    protected Icon getIcon(AbstractNode value) {
        return columnHelper.icon;
    }

    @Override
    protected String getTooltipText(AbstractNode value) {
        DownloadLink dl;
        if (value instanceof DownloadLink) {
            AvailableStatus status = ((DownloadLink) value).getAvailableStatus();
            if (status != null) {
                return status.getExplanation();
            }
            return DownloadLink.AvailableStatus.UNCHECKED.getExplanation();
        } else if (value instanceof CrawledLink) {
            CrawledLink cl = (CrawledLink) value;
            dl = cl.getDownloadLink();
            if (dl != null) {
                AvailableStatus status = dl.getAvailableStatus();
                if (status != null) {
                    return status.getExplanation();
                }
                return DownloadLink.AvailableStatus.UNCHECKED.getExplanation();
            }
        } else if (value instanceof AbstractPackageNode) {
            ChildrenView view = ((AbstractPackageNode) value).getView();
            return view.getMessage(this);
        }
        return null;
    }

    @Override
    public int getDefaultWidth() {
        return 100;
    }

    @Override
    public boolean isEnabled(AbstractNode obj) {
        if (obj instanceof CrawledPackage) {
            return ((CrawledPackage) obj).getView().isEnabled();
        }
        return obj.isEnabled();
    }

    @Override
    public int getMinWidth() {
        return -1;
    }

    @Override
    public boolean isDefaultVisible() {
        return false;
    }

    @Override
    public String getStringValue(AbstractNode value) {
        return columnHelper.string;
    }

    @Override
    public void onConfigValidatorError(KeyHandler<Boolean> keyHandler, Boolean invalidValue, ValidationException validateException) {
    }

    @Override
    public void onConfigValueModified(KeyHandler<Boolean> keyHandler, Boolean newValue) {
        textVisible = CFG_GUI.CFG.isAvailableColumnTextVisible();
    }

}
