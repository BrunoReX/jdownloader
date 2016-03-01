package org.jdownloader.gui.views.downloads.overviewpanel;

import java.awt.event.HierarchyListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.Box;
import javax.swing.JSeparator;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

import jd.controlling.downloadcontroller.DownloadController;
import jd.controlling.downloadcontroller.DownloadWatchDog;
import jd.controlling.packagecontroller.AbstractNode;
import jd.gui.swing.jdgui.interfaces.View;
import jd.gui.swing.jdgui.menu.ChunksEditor;
import jd.gui.swing.jdgui.menu.ParalellDownloadsEditor;
import jd.gui.swing.jdgui.menu.ParallelDownloadsPerHostEditor;
import jd.gui.swing.jdgui.menu.SpeedlimitEditor;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLinkProperty;
import jd.plugins.FilePackage;
import jd.plugins.FilePackageProperty;

import org.appwork.controlling.StateEvent;
import org.appwork.controlling.StateEventListener;
import org.appwork.storage.config.ValidationException;
import org.appwork.storage.config.events.GenericConfigEventListener;
import org.appwork.storage.config.handler.BooleanKeyHandler;
import org.appwork.storage.config.handler.KeyHandler;
import org.appwork.swing.MigPanel;
import org.appwork.utils.swing.EDTRunner;
import org.appwork.utils.swing.SwingUtils;
import org.jdownloader.controlling.AggregatedNumbers;
import org.jdownloader.controlling.download.DownloadControllerListener;
import org.jdownloader.gui.event.GUIListener;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.gui.views.downloads.DownloadsView;
import org.jdownloader.gui.views.downloads.table.DownloadsTable;
import org.jdownloader.settings.staticreferences.CFG_GUI;

public class DownloadOverview extends AbstractOverviewPanel<AggregatedNumbers> implements DownloadControllerListener, HierarchyListener, GenericConfigEventListener<Boolean>, GUIListener {

    private static final AtomicBoolean INCLUDE_DISABLED = new AtomicBoolean(false) {
                                                            {
                                                                final AtomicBoolean variable = this;
                                                                CFG_GUI.OVERVIEW_PANEL_DOWNLOAD_PANEL_INCLUDE_DISABLED_LINKS.getEventSender().addListener(new GenericConfigEventListener<Boolean>() {

                                                                    @Override
                                                                    public void onConfigValueModified(KeyHandler<Boolean> keyHandler, Boolean newValue) {
                                                                        variable.set(Boolean.TRUE.equals(newValue));
                                                                    }

                                                                    @Override
                                                                    public void onConfigValidatorError(KeyHandler<Boolean> keyHandler, Boolean invalidValue, ValidationException validateException) {
                                                                    }
                                                                });
                                                                variable.set(CFG_GUI.OVERVIEW_PANEL_DOWNLOAD_PANEL_INCLUDE_DISABLED_LINKS.isEnabled());
                                                            }
                                                        };

    private final class FailedEntry extends DataEntry<AggregatedNumbers> {
        private FailedEntry(String label) {
            super(label);
        }

        @Override
        public void setData(AggregatedNumbers total, AggregatedNumbers filtered, AggregatedNumbers selected) {
            final boolean includeDisabled = INCLUDE_DISABLED.get();
            if (total != null) {
                setTotal(total.getFailedString(includeDisabled));
            }
            if (filtered != null) {
                setFiltered(filtered.getFailedString(includeDisabled));
            }
            if (selected != null) {
                setSelected(selected.getFailedString(includeDisabled));
            }
        }

        @Override
        public BooleanKeyHandler getVisibleKeyHandler() {
            return CFG_GUI.OVERVIEW_PANEL_DOWNLOAD_LINKS_FAILED_COUNT_VISIBLE;
        }
    }

    private final class SkippedEntry extends DataEntry<AggregatedNumbers> {
        private SkippedEntry(String label) {
            super(label);
        }

        @Override
        public void setData(AggregatedNumbers total, AggregatedNumbers filtered, AggregatedNumbers selected) {
            final boolean includeDisabled = INCLUDE_DISABLED.get();
            if (total != null) {
                setTotal(total.getSkippedString(includeDisabled));
            }
            if (filtered != null) {
                setFiltered(filtered.getSkippedString(includeDisabled));
            }
            if (selected != null) {
                setSelected(selected.getSkippedString(includeDisabled));
            }
        }

        @Override
        public BooleanKeyHandler getVisibleKeyHandler() {
            return CFG_GUI.OVERVIEW_PANEL_DOWNLOAD_LINKS_SKIPPED_COUNT_VISIBLE;
        }
    }

    private final class FinishedEntry extends DataEntry<AggregatedNumbers> {
        private FinishedEntry(String label) {
            super(label);
        }

        @Override
        public void setData(AggregatedNumbers total, AggregatedNumbers filtered, AggregatedNumbers selected) {
            final boolean includeDisabled = INCLUDE_DISABLED.get();
            if (total != null) {
                setTotal(total.getFinishedString(includeDisabled));
            }
            if (filtered != null) {
                setFiltered(filtered.getFinishedString(includeDisabled));
            }
            if (selected != null) {
                setSelected(selected.getFinishedString(includeDisabled));
            }
        }

        @Override
        public BooleanKeyHandler getVisibleKeyHandler() {
            return CFG_GUI.OVERVIEW_PANEL_DOWNLOAD_LINKS_FINISHED_COUNT_VISIBLE;
        }
    }

    private final class ConnectionsEntry extends DataEntry<AggregatedNumbers> {
        private ConnectionsEntry(String label) {
            super(label);
        }

        @Override
        public void setData(AggregatedNumbers total, AggregatedNumbers filtered, AggregatedNumbers selected) {
            if (total != null) {
                setTotal(Long.toString(total.getConnections()));
            }
            if (filtered != null) {
                setFiltered(Long.toString(filtered.getConnections()));
            }
            if (selected != null) {
                setSelected(Long.toString(selected.getConnections()));
            }
        }

        @Override
        public BooleanKeyHandler getVisibleKeyHandler() {
            return CFG_GUI.OVERVIEW_PANEL_DOWNLOAD_CONNECTIONS_VISIBLE;
        }
    }

    private final class ETAEntry extends DataEntry<AggregatedNumbers> {
        private ETAEntry(String label) {
            super(label);
        }

        @Override
        public void setData(AggregatedNumbers total, AggregatedNumbers filtered, AggregatedNumbers selected) {
            if (total != null) {
                setTotal(total.getEtaString());
            }
            if (filtered != null) {
                setFiltered(filtered.getEtaString());
            }
            if (selected != null) {
                setSelected(selected.getEtaString());
            }
        }

        @Override
        public BooleanKeyHandler getVisibleKeyHandler() {
            return CFG_GUI.OVERVIEW_PANEL_DOWNLOAD_ETAVISIBLE;
        }
    }

    private final class SpeedEntry extends DataEntry<AggregatedNumbers> {
        private SpeedEntry(String label) {
            super(label);
        }

        @Override
        public void setData(AggregatedNumbers total, AggregatedNumbers filtered, AggregatedNumbers selected) {
            if (total != null) {
                setTotal(total.getDownloadSpeedString());
            }
            if (filtered != null) {
                setFiltered(filtered.getDownloadSpeedString());
            }
            if (selected != null) {
                setSelected(selected.getDownloadSpeedString());
            }
        }

        @Override
        public BooleanKeyHandler getVisibleKeyHandler() {
            return CFG_GUI.OVERVIEW_PANEL_DOWNLOAD_SPEED_VISIBLE;
        }
    }

    private final class LinksCountEntry extends DataEntry<AggregatedNumbers> {
        private LinksCountEntry(String label) {
            super(label);
        }

        @Override
        public void setData(AggregatedNumbers total, AggregatedNumbers filtered, AggregatedNumbers selected) {
            if (total != null) {
                setTotal(Integer.toString(total.getLinkCount()));
            }
            if (filtered != null) {
                setFiltered(Integer.toString(filtered.getLinkCount()));
            }
            if (selected != null) {
                setSelected(Integer.toString(selected.getLinkCount()));
            }
        }

        @Override
        public BooleanKeyHandler getVisibleKeyHandler() {
            return CFG_GUI.OVERVIEW_PANEL_DOWNLOAD_LINK_COUNT_VISIBLE;
        }
    }

    private final class DownloadsEntry extends DataEntry<AggregatedNumbers> {
        private DownloadsEntry(String label) {
            super(label);
        }

        @Override
        public void setData(AggregatedNumbers total, AggregatedNumbers filtered, AggregatedNumbers selected) {
            if (total != null) {
                setTotal(Long.toString(total.getRunning()));
            }
            if (filtered != null) {
                setFiltered(Long.toString(filtered.getRunning()));
            }
            if (selected != null) {
                setSelected(Long.toString(selected.getRunning()));
            }
        }

        @Override
        public BooleanKeyHandler getVisibleKeyHandler() {
            return CFG_GUI.OVERVIEW_PANEL_DOWNLOAD_RUNNING_DOWNLOADS_COUNT_VISIBLE;
        }
    }

    private final class BytesLoadedEntry extends DataEntry<AggregatedNumbers> {
        private BytesLoadedEntry(String label) {
            super(label);
        }

        @Override
        public void setData(AggregatedNumbers total, AggregatedNumbers filtered, AggregatedNumbers selected) {
            final boolean includeDisabled = INCLUDE_DISABLED.get();
            if (total != null) {
                setTotal(total.getLoadedBytesString(includeDisabled));
            }
            if (filtered != null) {
                setFiltered(filtered.getLoadedBytesString(includeDisabled));
            }
            if (selected != null) {
                setSelected(selected.getLoadedBytesString(includeDisabled));
            }
        }

        @Override
        public BooleanKeyHandler getVisibleKeyHandler() {
            return CFG_GUI.OVERVIEW_PANEL_DOWNLOAD_BYTES_LOADED_VISIBLE;
        }
    }

    private final class BytesTotalEntry extends DataEntry<AggregatedNumbers> {
        private BytesTotalEntry(String label) {
            super(label);
        }

        @Override
        public void setData(AggregatedNumbers total, AggregatedNumbers filtered, AggregatedNumbers selected) {
            final boolean includeDisabled = INCLUDE_DISABLED.get();
            if (total != null) {
                setTotal(total.getTotalBytesString(includeDisabled));
            }
            if (filtered != null) {
                setFiltered(filtered.getTotalBytesString(includeDisabled));
            }
            if (selected != null) {
                setSelected(selected.getTotalBytesString(includeDisabled));
            }
        }

        @Override
        public BooleanKeyHandler getVisibleKeyHandler() {
            return CFG_GUI.OVERVIEW_PANEL_DOWNLOAD_TOTAL_BYTES_VISIBLE;
        }
    }

    private final class PackagesEntry extends DataEntry<AggregatedNumbers> {
        private PackagesEntry(String label) {
            super(label);
        }

        @Override
        public void setData(AggregatedNumbers total, AggregatedNumbers filtered, AggregatedNumbers selected) {
            if (total != null) {
                setTotal(Integer.toString(total.getPackageCount()));
            }
            if (filtered != null) {
                setFiltered(Integer.toString(filtered.getPackageCount()));
            }
            if (selected != null) {
                setSelected(Integer.toString(selected.getPackageCount()));
            }
        }

        @Override
        public BooleanKeyHandler getVisibleKeyHandler() {
            return CFG_GUI.OVERVIEW_PANEL_DOWNLOAD_PACKAGE_COUNT_VISIBLE;
        }
    }

    /**
     *
     */
    private static final long                   serialVersionUID = 7849517111823717677L;

    private ListSelectionListener               listSelection;
    private TableModelListener                  tableListener;
    private StateEventListener                  stateListener;
    private GenericConfigEventListener<Boolean> settingsListener;

    @Override
    public void onKeyModifier(int parameter) {
    }

    public DownloadOverview(DownloadsTable table) {
        super(table.getModel());

        // new line

        // DownloadWatchDog.getInstance().getActiveDownloads(), DownloadWatchDog.getInstance().getDownloadSpeedManager().connections()

        CFG_GUI.OVERVIEW_PANEL_DOWNLOAD_PANEL_INCLUDE_DISABLED_LINKS.getEventSender().addListener(this, true);
        final MigPanel settings = new MigPanel("ins 2 0 0 0 ,wrap 3", "[][fill][fill]", "[]2[]");
        SwingUtils.setOpaque(settings, false);
        settings.add(new JSeparator(JSeparator.VERTICAL), "spany,pushy,growy");
        settings.add(new ChunksEditor(true));
        settings.add(new ParalellDownloadsEditor(true));
        settings.add(new ParallelDownloadsPerHostEditor(true));
        settings.add(new SpeedlimitEditor(true));
        CFG_GUI.DOWNLOAD_PANEL_OVERVIEW_SETTINGS_VISIBLE.getEventSender().addListener(settingsListener = new GenericConfigEventListener<Boolean>() {

            @Override
            public void onConfigValidatorError(KeyHandler<Boolean> keyHandler, Boolean invalidValue, ValidationException validateException) {
            }

            @Override
            public void onConfigValueModified(KeyHandler<Boolean> keyHandler, Boolean newValue) {
                settings.setVisible(newValue);
            }
        });
        settings.setVisible(CFG_GUI.DOWNLOAD_PANEL_OVERVIEW_SETTINGS_VISIBLE.isEnabled());
        CFG_GUI.DOWNLOAD_TAB_OVERVIEW_VISIBLE.getEventSender().addListener(this, true);

        add(Box.createHorizontalGlue());

        add(settings, "hidemode 3");
        DownloadController.getInstance().addListener(this, true);
        tableModel.addTableModelListener(tableListener = new TableModelListener() {

            @Override
            public void tableChanged(TableModelEvent e) {
                slowDelayer.run();
            }
        });
        DownloadWatchDog.getInstance().getStateMachine().addListener(stateListener = new StateEventListener() {

            @Override
            public void onStateUpdate(StateEvent event) {
            }

            @Override
            public void onStateChange(final StateEvent event) {
                new EDTRunner() {

                    @Override
                    protected void runInEDT() {
                        if (event.getNewState() == DownloadWatchDog.RUNNING_STATE || event.getNewState() == DownloadWatchDog.PAUSE_STATE) {
                            startUpdateTimer();
                        } else {
                            stopUpdateTimer();
                        }
                    }
                };
            }
        });

        tableModel.getTable().getSelectionModel().addListSelectionListener(listSelection = new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e == null || e.getValueIsAdjusting() || tableModel.isTableSelectionClearing()) {
                    return;
                }
                onConfigValueModified(null, null);
            }
        });

    }

    protected List<DataEntry<AggregatedNumbers>> createDataEntries() {
        DataEntry<AggregatedNumbers> packageCount = new PackagesEntry(_GUI.T.DownloadOverview_DownloadOverview_packages());
        DataEntry<AggregatedNumbers> size = new BytesTotalEntry(_GUI.T.DownloadOverview_DownloadOverview_size());
        DataEntry<AggregatedNumbers> bytesLoaded = new BytesLoadedEntry(_GUI.T.DownloadOverview_DownloadOverview_loaded());
        DataEntry<AggregatedNumbers> runningDownloads = new DownloadsEntry(_GUI.T.DownloadOverview_DownloadOverview_running_downloads());

        DataEntry<AggregatedNumbers> linkCount = new LinksCountEntry(_GUI.T.DownloadOverview_DownloadOverview_links());

        DataEntry<AggregatedNumbers> speed = new SpeedEntry(_GUI.T.DownloadOverview_DownloadOverview_speed());
        DataEntry<AggregatedNumbers> eta = new ETAEntry(_GUI.T.DownloadOverview_DownloadOverview_eta());

        DataEntry<AggregatedNumbers> connections = new ConnectionsEntry(_GUI.T.DownloadOverview_DownloadOverview_connections());

        DataEntry<AggregatedNumbers> finishedDownloads = new FinishedEntry(_GUI.T.DownloadOverview_DownloadOverview_finished_downloads());
        DataEntry<AggregatedNumbers> skippedDownloads = new SkippedEntry(_GUI.T.DownloadOverview_DownloadOverview_skipped_downloads());
        DataEntry<AggregatedNumbers> failedDownloads = new FailedEntry(_GUI.T.DownloadOverview_DownloadOverview_failed_downloads());

        ArrayList<DataEntry<AggregatedNumbers>> entries = new ArrayList<DataEntry<AggregatedNumbers>>();
        entries.add(packageCount);
        entries.add(linkCount);
        entries.add(size);
        entries.add(speed);
        entries.add(bytesLoaded);
        entries.add(eta);
        entries.add(runningDownloads);
        entries.add(connections);
        entries.add(finishedDownloads);
        entries.add(skippedDownloads);
        entries.add(failedDownloads);
        return entries;
    }

    public void removeListeners() {
        super.removeListeners();
        new EDTRunner() {

            @Override
            protected void runInEDT() {
                CFG_GUI.DOWNLOAD_PANEL_OVERVIEW_SETTINGS_VISIBLE.getEventSender().removeListener(settingsListener);
                CFG_GUI.DOWNLOAD_TAB_OVERVIEW_VISIBLE.getEventSender().removeListener(DownloadOverview.this);
                DownloadController.getInstance().removeListener(DownloadOverview.this);
                tableModel.removeTableModelListener(tableListener);
                tableModel.getTable().getSelectionModel().removeListSelectionListener(listSelection);
                DownloadWatchDog.getInstance().getStateMachine().removeListener(stateListener);
            }
        };
    }

    // protected void update() {
    // if (visible.get() == false) return;
    // super.update();
    //
    //
    // }

    @Override
    public void onDownloadControllerAddedPackage(FilePackage pkg) {
    }

    @Override
    public void onDownloadControllerStructureRefresh(FilePackage pkg) {
        slowDelayer.run();
    }

    @Override
    public void onDownloadControllerStructureRefresh() {
        slowDelayer.run();
    }

    @Override
    public void onDownloadControllerStructureRefresh(AbstractNode node, Object param) {
        slowDelayer.run();
    }

    @Override
    public void onDownloadControllerRemovedPackage(FilePackage pkg) {
    }

    @Override
    public void onDownloadControllerRemovedLinklist(List<DownloadLink> list) {
    }

    @Override
    public void onDownloadControllerUpdatedData(DownloadLink downloadlink, DownloadLinkProperty property) {
        slowDelayer.run();
    }

    @Override
    public void onDownloadControllerUpdatedData(FilePackage pkg, FilePackageProperty property) {
        slowDelayer.run();
    }

    @Override
    public void onDownloadControllerUpdatedData(DownloadLink downloadlink) {
        slowDelayer.run();
    }

    @Override
    public void onDownloadControllerUpdatedData(FilePackage pkg) {
        slowDelayer.run();
    }

    @Override
    public void onConfigValueModified(KeyHandler<Boolean> keyHandler, Boolean newValue) {
        if (CFG_GUI.DOWNLOAD_TAB_OVERVIEW_VISIBLE == keyHandler && Boolean.FALSE.equals(newValue)) {
            removeListeners();
            visible.set(false);
        } else {
            super.onConfigValueModified(keyHandler, newValue);
        }
    }

    @Override
    protected boolean isActiveView(View newView) {
        return newView instanceof DownloadsView;
    }

    protected AggregatedNumbers createSelected() {
        return new AggregatedNumbers(tableModel.getTable().getSelectionInfo(true, true));
    }

    protected AggregatedNumbers createFiltered() {
        return new AggregatedNumbers(tableModel.getTable().getSelectionInfo(false, true));
    }

    protected AggregatedNumbers createTotal() {
        return new AggregatedNumbers(tableModel.getTable().getSelectionInfo(false, false));
    }
}
