package org.jdownloader.gui.views.downloads.columns;

import java.awt.FontMetrics;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import jd.controlling.downloadcontroller.SingleDownloadController;
import jd.controlling.packagecontroller.AbstractNode;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.FilePackageView;
import jd.plugins.PluginForHost;
import jd.plugins.PluginProgress;
import jd.plugins.download.DownloadInterface;
import jd.plugins.download.raf.FileBytesMap.FileBytesMapView;

import org.appwork.swing.components.multiprogressbar.MultiProgressBar;
import org.appwork.swing.components.multiprogressbar.Range;
import org.appwork.swing.components.tooltips.ExtTooltip;
import org.appwork.swing.components.tooltips.PanelToolTip;
import org.appwork.swing.components.tooltips.ToolTipController;
import org.appwork.swing.components.tooltips.TooltipPanel;
import org.appwork.swing.exttable.columns.ExtProgressColumn;
import org.appwork.utils.swing.SwingUtils;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.plugins.FinalLinkState;
import org.jdownloader.updatev2.gui.LAFOptions;

public class ProgressColumn extends ExtProgressColumn<AbstractNode> {

    /**
     *
     */
    private static final long serialVersionUID = 1L;
    private int               big;
    private int               medium;

    public ProgressColumn() {
        super(_GUI.T.ProgressColumn_ProgressColumn());
        FontMetrics fm = determinatedRenderer.getFontMetrics(determinatedRenderer.getFont());

        big = fm.stringWidth(df.format(123.45d) + "%");
        medium = fm.stringWidth(df.format(123.45d));
    }

    public JPopupMenu createHeaderPopup() {

        return FileColumn.createColumnPopup(this, getMinWidth() == getMaxWidth() && getMaxWidth() > 0);

    }

    @Override
    protected boolean isIndeterminated(AbstractNode value, boolean isSelected, boolean hasFocus, int row, int column) {
        return getValue(value) < 0;
    }

    @Override
    public boolean isEnabled(AbstractNode obj) {

        return obj.isEnabled();
    }

    public boolean onDoubleClick(final MouseEvent e, final AbstractNode obj) {
        SwingUtilities.invokeLater(new Runnable() {

            public void run() {
                ToolTipController.getInstance().show(getModel().getTable().createExtTooltip(null));
            }
        });
        return false;
    }

    public ExtTooltip createToolTip(final Point position, final AbstractNode obj) {
        TooltipPanel panel = new TooltipPanel("ins 0,wrap 1", "[grow,fill]", "[][grow,fill]");
        final MultiProgressBar mpb = new MultiProgressBar(1000);
        mpb.setForeground((LAFOptions.getInstance().getColorForTooltipForeground()));

        if (updateRanges(obj, mpb)) {
            JLabel lbl = new JLabel(_GUI.T.ProgressColumn_createToolTip_object_());
            lbl.setForeground((LAFOptions.getInstance().getColorForTooltipForeground()));
            SwingUtils.toBold(lbl);
            panel.add(lbl);
            mpb.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, (LAFOptions.getInstance().getColorForTooltipForeground())));
            panel.add(mpb, "width 300!,height 24!");

            return new PanelToolTip(panel) {
                /**
                 *
                 */
                private static final long serialVersionUID = 1036923322222455495L;
                private Timer             timer;

                /**
                 *
                 */
                public void onShow() {
                    this.timer = new Timer(1000, new ActionListener() {

                        public void actionPerformed(ActionEvent e) {
                            if (updateRanges(obj, mpb)) {
                                repaint();
                            } else {
                                timer.stop();
                                ToolTipController.getInstance().hideTooltip();
                            }
                        }

                    });
                    timer.start();
                }

                /**
                 *
                 */
                public void onHide() {
                    timer.stop();
                }
            };
        } else {
            return null;
        }
    }

    public boolean updateRanges(final AbstractNode obj, final MultiProgressBar mpb) {
        if (obj instanceof DownloadLink) {
            SingleDownloadController controller = ((DownloadLink) obj).getDownloadLinkController();
            DownloadInterface downloadInterface = null;
            FileBytesMapView mapInfo = null;
            if (controller != null && (downloadInterface = controller.getDownloadInstance()) != null && (mapInfo = downloadInterface.getCacheMapView()) != null) {
                mpb.getModel().setMaximum(mapInfo.getSize());
                java.util.List<Range> ranges = new ArrayList<Range>();
                for (int i = 0; i < mapInfo.getMarkedAreas().length; i++) {
                    ranges.add(new Range(mapInfo.getMarkedAreas()[i][0], mapInfo.getMarkedAreas()[i][0] + mapInfo.getMarkedAreas()[i][1]));
                }
                mpb.getModel().setRanges(ranges.toArray(new Range[] {}));
                return true;
            }
        } else if (obj instanceof FilePackage) {
            long size = ((FilePackage) obj).getView().getSize();
            mpb.getModel().setMaximum(size);
            java.util.List<Range> ranges = new ArrayList<Range>();

            boolean readL = ((FilePackage) obj).getModifyLock().readLock();
            try {
                List<DownloadLink> children = ((FilePackage) obj).getChildren();
                long all = 0;
                for (int i = 0; i < children.size(); i++) {
                    ranges.add(new Range(all, all + children.get(i).getView().getBytesLoaded()));
                    all += children.get(i).getView().getBytesTotalEstimated();
                }
                mpb.getModel().setRanges(ranges.toArray(new Range[] {}));
            } finally {
                ((FilePackage) obj).getModifyLock().readUnlock(readL);
            }
            return true;
        }
        return false;
    }

    @Override
    public int getMinWidth() {
        return 16;
    }

    @Override
    public int getDefaultWidth() {
        return 100;
    }

    @Override
    protected String getString(AbstractNode value, long current, long total) {
        if (value instanceof FilePackage) {
            return format(getPercentString(current, total));
        } else {
            final DownloadLink dLink = (DownloadLink) value;
            PluginProgress progress;
            if (dLink.getDefaultPlugin() == null) {
                return _GUI.T.gui_treetable_error_plugin();
            } else if ((progress = dLink.getPluginProgress()) != null && !(progress.getProgressSource() instanceof PluginForHost)) {
                final double prgs = progress.getPercent();
                if (prgs < 0) {
                    return "";
                }
                return format(prgs);
            }
            if (total < 0) {
                if (dLink.getView().getBytesLoaded() <= 0) {
                    return format(0d);
                } else {
                    return "~";
                }
            }
        }
        if (total < 0) {

            return "~";
        }
        return format(getPercentString(current, total));
    }

    private NumberFormat df = NumberFormat.getInstance();

    private String format(double percentString) {
        if (getWidth() < big) {
            if (getWidth() < medium) {
                return "";
            } else {
                return df.format(percentString);
            }
        } else {
            return df.format(percentString) + "%";
        }

    }

    @Override
    protected long getMax(AbstractNode value) {
        if (value instanceof FilePackage) {
            final FilePackage fp = (FilePackage) value;
            final FilePackageView view = fp.getView();
            if (view.isFinished()) {
                return 100;
            } else {
                if (view.getUnknownFileSizes() > 0) {

                    return view.size();

                }
                return Math.max(0, view.getSize());
            }
        } else {
            final DownloadLink dLink = (DownloadLink) value;
            PluginProgress progress = null;
            long size = -1;
            if (dLink.getDefaultPlugin() == null) {
                return 100;
            } else if ((progress = dLink.getPluginProgress()) != null && (progress.isDisplayInProgressColumnEnabled())) {
                return (progress.getTotal());
            } else if (FinalLinkState.CheckFinished(dLink.getFinalLinkState())) {
                return 100;
            } else if ((size = dLink.getView().getBytesTotal()) > 0) {
                return size;
            } else {
                return -1;
            }
        }
    }

    @Override
    protected void prepareGetter(AbstractNode value) {
    }

    @Override
    protected int getFps() {
        return 5;
    }

    @Override
    protected long getValue(AbstractNode value) {
        if (value instanceof FilePackage) {
            final FilePackage fp = (FilePackage) value;
            final FilePackageView view = fp.getView();
            if (view.isFinished()) {
                return 100;
            } else {
                if (view.getUnknownFileSizes() > 0) {
                    return view.getFinalCount();
                }
                return Math.max(0, view.getDone());
            }
        } else {
            final DownloadLink dLink = (DownloadLink) value;
            PluginProgress progress = null;
            if (dLink.getDefaultPlugin() == null) {
                return -1;
            } else if ((progress = dLink.getPluginProgress()) != null && (progress.isDisplayInProgressColumnEnabled())) {

                return (progress.getCurrent());
            } else if (FinalLinkState.CheckFinished(dLink.getFinalLinkState())) {
                return 100;
            } else if (dLink.getView().getBytesTotal() > 0) {
                return dLink.getView().getBytesLoaded();
            } else {
                return 0;
            }
        }
    }

}
