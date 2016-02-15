package org.jdownloader.extensions.schedulerV2.actions;

import javax.swing.JLabel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.appwork.utils.formatter.SizeFormatter;
import org.appwork.utils.logging2.LogInterface;
import org.jdownloader.extensions.schedulerV2.gui.SpeedSpinner;
import org.jdownloader.extensions.schedulerV2.translate.T;

@ScheduleActionIDAnnotation("SET_DOWNLOADSPEED")
public class SetDownloadspeedAction extends AbstractScheduleAction<SetDownloadspeedActionConfig> {

    public SetDownloadspeedAction(String configJson) {
        super(configJson);
    }

    @Override
    public String getReadableName() {
        return T.T.action_setDownloadspeed();
    }

    @Override
    public void execute(LogInterface logger) {
        org.jdownloader.settings.staticreferences.CFG_GENERAL.DOWNLOAD_SPEED_LIMIT_ENABLED.setValue(true);
        org.jdownloader.settings.staticreferences.CFG_GENERAL.DOWNLOAD_SPEED_LIMIT.setValue(getConfig().getDownloadspeed());
    }

    @Override
    protected void createPanel() {

        panel.put(new JLabel(T.T.addScheduleEntryDialog_speed() + ":"), "gapleft 10,");

        final SpeedSpinner downloadspeedSpinner = new SpeedSpinner(0l, 100 * 1024 * 1024 * 1024l, 1l);
        downloadspeedSpinner.setValue(getConfig().getDownloadspeed());
        downloadspeedSpinner.addChangeListener(new ChangeListener() {

            @Override
            public void stateChanged(ChangeEvent e) {
                Object value = downloadspeedSpinner.getValue();
                if (value instanceof Number) {
                    getConfig().setDownloadspeed(((Number) value).intValue());
                }
            }
        });
        panel.put(downloadspeedSpinner, "");
    }

    @Override
    public String getReadableParameter() {
        return SizeFormatter.formatBytes(Long.valueOf(getConfig().getDownloadspeed())) + "/s";
    }
}
