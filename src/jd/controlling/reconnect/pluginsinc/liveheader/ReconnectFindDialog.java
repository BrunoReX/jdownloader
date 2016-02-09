package jd.controlling.reconnect.pluginsinc.liveheader;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.Comparator;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.Timer;

import org.appwork.swing.MigPanel;
import org.appwork.swing.components.ExtTextArea;
import org.appwork.swing.components.circlebar.CircledProgressBar;
import org.appwork.swing.components.circlebar.ImagePainter;
import org.appwork.utils.formatter.TimeFormatter;
import org.appwork.utils.swing.EDTRunner;
import org.appwork.utils.swing.SwingUtils;
import org.appwork.utils.swing.dialog.AbstractDialog;
import org.appwork.utils.swing.dialog.Dialog;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.images.AbstractIcon;

import jd.controlling.reconnect.ReconnectResult;
import jd.controlling.reconnect.ipcheck.IPConnectionState;
import jd.controlling.reconnect.ipcheck.IPController;
import jd.controlling.reconnect.ipcheck.event.IPControllListener;

public abstract class ReconnectFindDialog extends AbstractDialog<Object> implements IPControllListener {

    public void onIPForbidden(IPConnectionState parameter) {
    }

    public void onIPInvalidated(IPConnectionState parameter) {
    }

    public void onIPChanged(IPConnectionState parameter, IPConnectionState parameter2) {
        setNewIP(parameter2.isOffline() ? "  -  " : parameter2.getExternalIp().toString());
    }

    public void onIPOffline() {
        setSubStatusState(_GUI.T.ReconnectDialog_onIPOffline_(), new AbstractIcon(IconKey.ICON_NETWORK_ERROR, 16));
        setNewIP(_GUI.T.literally_offline());
    }

    public void onIPValidated(IPConnectionState parameter, IPConnectionState parameter2) {
    }

    public void onIPOnline(IPConnectionState parameter) {
        setSubStatusState(_GUI.T.ReconnectDialog_onIPOnline_(), new AbstractIcon(IconKey.ICON_NETWORK_IDLE, 16));
    }

    public void onIPStateChanged(IPConnectionState parameter, IPConnectionState parameter2) {
    }

    private JProgressBar                              bar;
    private CircledProgressBar                        circle;
    private JLabel                                    header;
    private JLabel                                    state;
    private JLabel                                    duration;

    private JLabel                                    newIP;
    private Thread                                    th;
    private Timer                                     updateTimer;
    private long                                      startTime;
    private java.util.List<? extends ReconnectResult> foundList;

    public ReconnectFindDialog() {

        super(Dialog.STYLE_HIDE_ICON, _GUI.T.AutoDetectAction_actionPerformed_d_title(), null, _GUI.T.ReconnectFindDialog_ReconnectFindDialog_ok(), null);

    }

    @Override
    protected Object createReturnValue() {
        return null;
    }

    public void setBarText(final String txt) {
        new EDTRunner() {

            @Override
            protected void runInEDT() {
                bar.setString(txt);
            }
        };
    }

    public void setBarProgress(final int prog) {
        new EDTRunner() {

            @Override
            protected void runInEDT() {
                bar.setValue(prog);
            }
        };
    }

    @Override
    public JComponent layoutDialogContent() {
        MigPanel p = new MigPanel("ins 0,wrap 1", "[grow,fill]", "[]");
        ExtTextArea txt = new ExtTextArea();
        txt.setLabelMode(true);
        p.add(txt);
        txt.setText(_GUI.T.AutoDetectAction_actionPerformed_d_msg());
        bar = new JProgressBar();
        bar.setStringPainted(true);
        bar.setMaximum(100);
        p.add(bar);
        circle = new CircledProgressBar();
        circle.setValueClipPainter(new ImagePainter(new AbstractIcon(IconKey.ICON_RECONNECT, 26), 1.0f));
        ((ImagePainter) circle.getValueClipPainter()).setBackground(Color.WHITE);
        ((ImagePainter) circle.getValueClipPainter()).setForeground(Color.GREEN);

        circle.setNonvalueClipPainter(new ImagePainter(new AbstractIcon(IconKey.ICON_RECONNECT, 26), 0.5f));
        ((ImagePainter) circle.getNonvalueClipPainter()).setBackground(Color.WHITE);
        ((ImagePainter) circle.getNonvalueClipPainter()).setForeground(Color.GREEN);
        MigPanel sp = new MigPanel("ins 0", "[fill][fill][grow,fill][fill][fill]", "[fill][grow,fill]");
        sp.add(circle, "spany 2,gapright 10,height 42!,width 42!");
        sp.add(header = new JLabel(), "spanx 4,width 350:n:n");
        SwingUtils.toBold(header);
        sp.add(state = new JLabel(), "spanx,alignx right");
        state.setHorizontalTextPosition(SwingConstants.LEFT);
        SwingUtils.toBold(header);
        state.setHorizontalAlignment(SwingConstants.RIGHT);
        circle.setIndeterminate(true);
        sp.add(label(_GUI.T.ReconnectDialog_layoutDialogContent_duration()));

        sp.add(duration = new JLabel());

        state.setHorizontalAlignment(SwingConstants.RIGHT);
        sp.add(new JLabel(new AbstractIcon(IconKey.ICON_GO_NEXT, 18)));
        sp.add(label(_GUI.T.ReconnectDialog_layoutDialogContent_currentip()));
        sp.add(newIP = new JLabel(), "width 100!");
        newIP.setHorizontalAlignment(SwingConstants.RIGHT);

        p.add(sp);

        th = new Thread(getClass().getName()) {
            public void run() {
                try {
                    IPController.getInstance().invalidate();
                    IPController.getInstance().validate();
                    if (IPController.getInstance().getIpState().isOffline()) {
                        setSubStatusState(_GUI.T.ReconnectDialog_onIPOffline_(), new AbstractIcon(IconKey.ICON_NETWORK_ERROR, 16));
                        setNewIP(_GUI.T.literally_offline());
                    } else {
                        setSubStatusState(_GUI.T.ReconnectDialog_onIPOnline_(), new AbstractIcon(IconKey.ICON_NETWORK_IDLE, 16));
                        setNewIP(IPController.getInstance().getIpState().getExternalIp().toString());
                    }

                    ReconnectFindDialog.this.run();
                } catch (InterruptedException e) {
                    e.printStackTrace();

                }

                dispose();
            }
        };
        startTime = System.currentTimeMillis();
        updateTimer = new Timer(1000, new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                duration.setText(TimeFormatter.formatMilliSeconds(System.currentTimeMillis() - startTime, 0));

            }
        });
        updateTimer.setRepeats(true);
        updateTimer.start();
        th.start();
        IPController.getInstance().getEventSender().addListener(this);
        return p;
    }

    @Override
    protected void initFocus(JComponent focus) {
        super.initFocus(cancelButton);
    }

    @Override
    protected void packed() {
        super.packed();
        okButton.setEnabled(false);
        okButton.repaint();
        cancelButton.requestFocus();
        okButton.setToolTipText(_GUI.T.ReconnectFindDialog_packed_no_found_script_tooltip());
    }

    @Override
    protected void setReturnmask(boolean b) {
        if (b) {
            // interrupt scxanning and use best script found so far

            Collections.sort(foundList, new Comparator<ReconnectResult>() {

                public int compare(ReconnectResult o1, ReconnectResult o2) {
                    return new Long(o2.getAverageSuccessDuration()).compareTo(new Long(o1.getAverageSuccessDuration()));
                }
            });

            foundList.get(0).getInvoker().getPlugin().setSetup(foundList.get(0));
        }
        super.setReturnmask(b);
    }

    public void setInterruptEnabled(java.util.List<? extends ReconnectResult> list) {
        this.foundList = list;
        new EDTRunner() {

            @Override
            protected void runInEDT() {
                okButton.setEnabled(true);
                okButton.setIcon(new AbstractIcon(IconKey.ICON_OK, 18));
                okButton.setToolTipText(_GUI.T.ReconnectFindDialog_packed_interrupt_tooltip());
            }

        };

    }

    @Override
    public void dispose() {
        super.dispose();
        IPController.getInstance().getEventSender().removeListener(this);
        updateTimer.stop();
        th.interrupt();

    }

    public void setSubStatusState(final String txt, final Icon imageIcon) {
        new EDTRunner() {

            @Override
            protected void runInEDT() {
                state.setText(txt);
                state.setIcon(imageIcon);
            }
        };
    }

    public void setNewIP(final String txt) {
        new EDTRunner() {

            @Override
            protected void runInEDT() {
                newIP.setText(txt);
            }
        };
    }

    public void setSubStatusHeader(final String txt) {
        new EDTRunner() {

            @Override
            protected void runInEDT() {
                header.setText(txt);
            }
        };
    }

    abstract public void run() throws InterruptedException;

    private Component label(String lbl) {
        JLabel ret = new JLabel(lbl);
        ret.setEnabled(false);
        return ret;
    }
}
//
