package org.jdownloader.gui.views.downloads.overviewpanel;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.Timer;

import org.appwork.scheduler.DelayedRunnable;
import org.appwork.storage.config.ValidationException;
import org.appwork.storage.config.events.GenericConfigEventListener;
import org.appwork.storage.config.handler.KeyHandler;
import org.appwork.swing.MigPanel;
import org.appwork.utils.NullsafeAtomicReference;
import org.appwork.utils.swing.EDTHelper;
import org.appwork.utils.swing.EDTRunner;
import org.jdownloader.gui.event.GUIEventSender;
import org.jdownloader.gui.event.GUIListener;
import org.jdownloader.gui.views.SelectionInfo;
import org.jdownloader.gui.views.components.packagetable.PackageControllerTableModel;
import org.jdownloader.settings.GraphicalUserInterfaceSettings.Position;
import org.jdownloader.settings.staticreferences.CFG_GUI;
import org.jdownloader.updatev2.gui.LAFOptions;

import jd.SecondLevelLaunch;
import jd.controlling.downloadcontroller.DownloadWatchDog;
import jd.gui.swing.jdgui.JDGui;
import jd.gui.swing.jdgui.interfaces.View;

public abstract class AbstractOverviewPanel<T> extends MigPanel implements GUIListener, GenericConfigEventListener<Boolean>, HierarchyListener {

    private List<DataEntry<T>>                        dataEntries;
    protected final AtomicBoolean                     visible      = new AtomicBoolean(false);
    protected final NullsafeAtomicReference<Timer>    updateTimer  = new NullsafeAtomicReference<Timer>(null);
    protected final AtomicBoolean                     hasSelection = new AtomicBoolean(false);

    protected final DelayedRunnable                   slowDelayer;
    protected final DelayedRunnable                   fastDelayer;
    private final GenericConfigEventListener<Boolean> relayoutListener;
    protected final PackageControllerTableModel       tableModel;

    private static final ScheduledExecutorService     SERVICE      = DelayedRunnable.getNewScheduledExecutorService();

    public AbstractOverviewPanel(PackageControllerTableModel tableModel) {
        super("ins " + LAFOptions.getInstance().getExtension().customizeOverviewPanelInsets(), "[][grow,fill][]", "[grow,fill]");

        this.tableModel = tableModel;
        LAFOptions.getInstance().applyPanelBackground(this);
        GUIEventSender.getInstance().addListener(this, true);
        final MigPanel info = new MigPanel("ins 2 0 0 0", "[grow]10[grow]", "[grow,fill]2[grow,fill]");
        info.setOpaque(false);
        relayoutListener = new GenericConfigEventListener<Boolean>() {

            @Override
            public void onConfigValueModified(KeyHandler<Boolean> keyHandler, Boolean newValue) {
                new EDTRunner() {

                    @Override
                    protected void runInEDT() {
                        layoutInfoPanel(info);
                        update();

                        revalidate();
                    }

                };
            }

            @Override
            public void onConfigValidatorError(KeyHandler<Boolean> keyHandler, Boolean invalidValue, ValidationException validateException) {
            }
        };
        layoutInfoPanel(info);
        add(info, "pushy,growy");
        slowDelayer = new DelayedRunnable(SERVICE, 500, 5000) {

            @Override
            public void delayedrun() {
                update();
            }
        };
        fastDelayer = new DelayedRunnable(SERVICE, 50, 200) {

            @Override
            public void delayedrun() {
                update();
            }
        };
        CFG_GUI.OVERVIEW_PANEL_TOTAL_INFO_VISIBLE.getEventSender().addListener(this, true);
        CFG_GUI.OVERVIEW_PANEL_SELECTED_INFO_VISIBLE.getEventSender().addListener(this, true);
        CFG_GUI.OVERVIEW_PANEL_VISIBLE_ONLY_INFO_VISIBLE.getEventSender().addListener(this, true);

        CFG_GUI.OVERVIEW_PANEL_SMART_INFO_VISIBLE.getEventSender().addListener(this, true);

        this.addHierarchyListener(this);
        onConfigValueModified(null, null);

        SecondLevelLaunch.GUI_COMPLETE.executeWhenReached(new Runnable() {

            public void run() {
                visible.set(isViewActive());
                fastDelayer.run();
            }
        });
        onConfigValueModified(null, null);

    }

    protected boolean isViewActive() {
        return new EDTHelper<Boolean>() {

            @Override
            public Boolean edtRun() {

                return isActiveView(JDGui.getInstance().getMainTabbedPane().getSelectedView());
            }
        }.getReturnValue();
    }

    protected void layoutInfoPanel(MigPanel info) {
        info.removeAll();
        HashMap<String, Position> map = CFG_GUI.CFG.getOverviewPositions();
        boolean save = false;
        if (map == null) {
            map = new HashMap<String, Position>();
            save = true;
        }
        HashMap<String, DataEntry<T>> idMap = new HashMap<String, DataEntry<T>>();
        this.dataEntries = new ArrayList<DataEntry<T>>();
        for (DataEntry<T> s : createDataEntries()) {
            Position ret = map.get(s.getId());
            if (ret == null) {
                ret = new Position();
                map.put(s.getId(), ret);
                save = true;
            }
            idMap.put(s.getId(), s);
            if (s.getVisibleKeyHandler() == null || s.getVisibleKeyHandler().isEnabled()) {
                dataEntries.add(s);
            }
            if (s.getVisibleKeyHandler() != null) {
                s.getVisibleKeyHandler().getEventSender().addListener(relayoutListener);
            }
        }
        // selected
        // filtered
        // speed
        // eta

        ArrayList<DataEntry<T>> row1 = new ArrayList<DataEntry<T>>();
        ArrayList<DataEntry<T>> row2 = new ArrayList<DataEntry<T>>();

        for (Entry<String, Position> es : map.entrySet()) {
            DataEntry<T> v = idMap.get(es.getKey());
            if (v == null) {
                continue;
            }
            int x = es.getValue().getX();
            int y = es.getValue().getY();
            ArrayList<DataEntry<T>> row;
            if (y == 0) {
                row = row1;
            } else {
                row = row2;
            }
            while (x >= 0 && y >= 0) {
                while (x >= row.size()) {
                    row.add(null);
                }
                if (row.get(x) != null) {
                    x++;
                    continue;
                }

                row.set(x, v);
                idMap.remove(v.getId());
                break;

            }

        }

        addloop: for (int i = 0; i < dataEntries.size(); i++) {
            DataEntry<T> v = dataEntries.get(i);
            if (!idMap.containsKey(v.getId())) {
                continue;
            }

            if (i % 2 == 0) {
                int index = 0;
                while (true) {
                    while (index >= row1.size()) {
                        row1.add(null);
                    }
                    if (row1.get(index) == null) {
                        row1.set(index, v);
                        continue addloop;
                    } else {
                        index++;
                    }
                }

            } else {
                int index = 0;
                while (true) {
                    while (index >= row2.size()) {
                        row2.add(null);
                    }
                    if (row2.get(index) == null) {
                        row2.set(index, v);
                        continue addloop;
                    } else {
                        index++;
                    }
                }
            }

        }

        if (save) {
            CFG_GUI.CFG.setOverviewPositions(map);
        }

        for (DataEntry<T> de : row1) {
            if (de == null) {
                continue;
            }
            de.addTo(info);
        }

        boolean first = true;
        for (DataEntry<T> de : row2) {
            if (de == null) {
                continue;
            }
            if (first) {
                de.addTo(info, ",newline");
            } else {
                de.addTo(info);
            }

            first = false;
        }
    }

    public List<DataEntry<T>> getDataEntries() {
        return dataEntries;
    }

    protected abstract List<DataEntry<T>> createDataEntries();

    public void removeListeners() {

        new EDTRunner() {

            @Override
            protected void runInEDT() {

                stopUpdateTimer();
                GUIEventSender.getInstance().removeListener(AbstractOverviewPanel.this);
                CFG_GUI.OVERVIEW_PANEL_TOTAL_INFO_VISIBLE.getEventSender().removeListener(AbstractOverviewPanel.this);
                CFG_GUI.OVERVIEW_PANEL_SELECTED_INFO_VISIBLE.getEventSender().removeListener(AbstractOverviewPanel.this);
                CFG_GUI.OVERVIEW_PANEL_VISIBLE_ONLY_INFO_VISIBLE.getEventSender().removeListener(AbstractOverviewPanel.this);
                CFG_GUI.OVERVIEW_PANEL_SMART_INFO_VISIBLE.getEventSender().removeListener(AbstractOverviewPanel.this);

                removeHierarchyListener(AbstractOverviewPanel.this);

            }
        };
    }

    @Override
    public void hierarchyChanged(HierarchyEvent e) {
        /**
         * Disable/Enable the updatetimer if the panel gets enabled/disabled
         */
        if (!this.isDisplayable()) {
            stopUpdateTimer();
        } else {
            startUpdateTimer();
            fastDelayer.run();
        }
    }

    @Override
    public void onConfigValidatorError(KeyHandler<Boolean> keyHandler, Boolean invalidValue, ValidationException validateException) {
    }

    @Override
    public void onConfigValueModified(KeyHandler<Boolean> keyHandler, Boolean newValue) {
        new EDTHelper<Void>() {

            @Override
            public Void edtRun() {
                final SelectionInfo<?, ?> selectionInfo = tableModel.getTable().getSelectionInfo();
                final boolean containsSelection = !selectionInfo.isEmpty();
                hasSelection.set(containsSelection);
                fastDelayer.run();
                return null;
            }
        }.start(true);
    }

    @Override
    public void onGuiMainTabSwitch(View oldView, final View newView) {
        new EDTRunner() {

            @Override
            protected void runInEDT() {
                if (isActiveView(newView)) {
                    visible.set(true);
                    startUpdateTimer();
                    fastDelayer.run();
                } else {
                    visible.set(false);
                    stopUpdateTimer();
                }
            }
        };

    }

    abstract protected boolean isActiveView(View newView);

    protected void startUpdateTimer() {
        Timer currentTimer = updateTimer.get();
        if (currentTimer != null && currentTimer.isRunning()) {
            return;
        }
        if (DownloadWatchDog.getInstance().isRunning() == false) {
            return;
        }
        currentTimer = new Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!(e.getSource() instanceof Timer)) {
                    return;
                }
                if (e.getSource() != updateTimer.get() || !isDisplayable()) {
                    Timer timer = ((Timer) e.getSource());
                    updateTimer.compareAndSet(timer, null);
                    timer.stop();
                    return;
                }
                fastDelayer.run();
            }
        });
        currentTimer.setRepeats(true);
        updateTimer.set(currentTimer);
        currentTimer.start();
    }

    protected void stopUpdateTimer() {
        Timer old = updateTimer.getAndSet(null);
        if (old != null) {
            old.stop();
        }
    }

    public void update() {
        if (visible.get() == false) {
            return;
        }
        final T total;
        final T filtered;
        final T selected;
        final boolean smart = CFG_GUI.OVERVIEW_PANEL_SMART_INFO_VISIBLE.isEnabled();
        final boolean visibleOnly = CFG_GUI.OVERVIEW_PANEL_VISIBLE_ONLY_INFO_VISIBLE.isEnabled();
        final boolean selectedOnly = CFG_GUI.OVERVIEW_PANEL_SELECTED_INFO_VISIBLE.isEnabled();
        final boolean totalVisible = CFG_GUI.OVERVIEW_PANEL_TOTAL_INFO_VISIBLE.isEnabled();
        final boolean containsSelection = hasSelection.get();
        if (smart || (!visibleOnly && !totalVisible && !selectedOnly)) {
            if (containsSelection) {
                filtered = null;
                total = null;
                selected = createSelected();
            } else {
                filtered = createFiltered();
                total = null;
                selected = null;
            }
        } else {
            if (totalVisible) {
                total = createTotal();
            } else {
                total = null;
            }
            if (visibleOnly) {
                filtered = createFiltered();
            } else {
                filtered = null;
            }
            if (selectedOnly) {
                selected = createSelected();
            } else {
                selected = null;
            }
        }
        new EDTRunner() {

            @Override
            protected void runInEDT() {
                if (!isDisplayable() || visible.get() == false) {
                    return;
                }
                for (DataEntry<T> entry : dataEntries) {
                    entry.updateVisibility(containsSelection);
                    set(entry);
                }
            }

            private void set(DataEntry<T> dataEntry) {
                if (dataEntry != null) {
                    dataEntry.setData(total, filtered, selected);
                }
            }
        }.waitForEDT();
    }

    protected abstract T createSelected();

    protected abstract T createFiltered();

    protected abstract T createTotal();
}
