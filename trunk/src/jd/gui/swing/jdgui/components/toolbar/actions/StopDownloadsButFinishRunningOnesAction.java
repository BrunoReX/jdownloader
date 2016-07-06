package jd.gui.swing.jdgui.components.toolbar.actions;

import java.awt.event.ActionEvent;

import jd.controlling.downloadcontroller.DownloadLinkCandidate;
import jd.controlling.downloadcontroller.DownloadLinkCandidateResult;
import jd.controlling.downloadcontroller.DownloadSession;
import jd.controlling.downloadcontroller.DownloadSession.STOPMARK;
import jd.controlling.downloadcontroller.DownloadWatchDog;
import jd.controlling.downloadcontroller.DownloadWatchDogJob;
import jd.controlling.downloadcontroller.DownloadWatchDogProperty;
import jd.controlling.downloadcontroller.SingleDownloadController;
import jd.controlling.downloadcontroller.event.DownloadWatchdogListener;

import org.appwork.utils.swing.EDTRunner;
import org.jdownloader.controlling.contextmenu.ActionContext;
import org.jdownloader.controlling.contextmenu.Customizer;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.toolbar.action.AbstractToolBarAction;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.gui.views.downloads.table.DownloadsTableModel;
import org.jdownloader.translate._JDT;

public class StopDownloadsButFinishRunningOnesAction extends AbstractToolBarAction implements DownloadWatchdogListener, ActionContext {

    public StopDownloadsButFinishRunningOnesAction() {
        setIconKey(IconKey.ICON_STOP_CONDITIONAL);
        setSelected(false);
        setEnabled(false);
        DownloadWatchDog.getInstance().getEventSender().addListener(this, true);
        DownloadWatchDog.getInstance().notifyCurrentState(this);
        addContextSetup(this);
    }

    @Override
    public void initContextDefaults() {

        setHideIfDownloadsAreStopped(false);
    }

    public void actionPerformed(ActionEvent e) {
        DownloadWatchDog.getInstance().enqueueJob(new DownloadWatchDogJob() {

            @Override
            public void execute(DownloadSession currentSession) {
                currentSession.toggleStopMark(STOPMARK.RANDOM);
                new EDTRunner() {

                    @Override
                    protected void runInEDT() {
                        DownloadsTableModel.getInstance().setStopSignColumnVisible(true);
                    }
                };
            }

            @Override
            public void interrupt() {
            }

            @Override
            public boolean isHighPriority() {
                return true;
            }
        });
    }

    private boolean            hideIfDownloadsAreStopped     = false;
    public static final String HIDE_IF_DOWNLOADS_ARE_STOPPED = "HideIfDownloadsAreStopped";

    public static String getHideIfDownloadsAreStoppedTranslation() {

        return _JDT.T.PauseDownloadsAction_getHideIfDownloadsAreStoppedTranslation();
    }

    @Customizer(link = "#getHideIfDownloadsAreStoppedTranslation")
    public boolean isHideIfDownloadsAreStopped() {
        return hideIfDownloadsAreStopped;
    }

    public void setHideIfDownloadsAreStopped(boolean showIfDownloadsAreRunning) {
        this.hideIfDownloadsAreStopped = showIfDownloadsAreRunning;
        if (isHideIfDownloadsAreStopped() && !DownloadWatchDog.getInstance().isRunning()) {
            setVisible(false);
        } else {
            setVisible(true);
        }
    }

    @Override
    public String createTooltip() {
        return _GUI.T.StopDownloadsAction_createTooltip();
    }

    @Override
    public void onDownloadWatchdogStateIsStopping() {
        new EDTRunner() {

            @Override
            protected void runInEDT() {
                setEnabled(false);
            }
        };

    }

    @Override
    public void onDownloadWatchdogStateIsStopped() {
        new EDTRunner() {

            @Override
            protected void runInEDT() {
                setEnabled(false);
                setSelected(false);
                if (isHideIfDownloadsAreStopped()) {
                    setVisible(false);
                }
            }
        };
    }

    @Override
    public void onDownloadWatchdogStateIsRunning() {
        new EDTRunner() {

            @Override
            protected void runInEDT() {
                setEnabled(true);
                setVisible(true);
            }
        };
    }

    @Override
    public void onDownloadWatchdogStateIsPause() {
    }

    @Override
    public void onDownloadWatchdogStateIsIdle() {
        new EDTRunner() {

            @Override
            protected void runInEDT() {
                setEnabled(false);
                setSelected(false);
            }
        };
    }

    @Override
    public void onDownloadWatchdogDataUpdate() {
    }

    @Override
    public void onDownloadControllerStart(SingleDownloadController downloadController, DownloadLinkCandidate candidate) {
    }

    @Override
    public void onDownloadControllerStopped(SingleDownloadController downloadController, DownloadLinkCandidate candidate, DownloadLinkCandidateResult result) {
    }

    @Override
    public void onDownloadWatchDogPropertyChange(final DownloadWatchDogProperty propertyChange) {
        new EDTRunner() {

            @Override
            protected void runInEDT() {
                Object ret = propertyChange.getValue();
                setSelected(!DownloadSession.STOPMARK.NONE.equals(ret));
            }
        };
    }

}
