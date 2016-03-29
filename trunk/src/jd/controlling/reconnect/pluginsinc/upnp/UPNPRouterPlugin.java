package jd.controlling.reconnect.pluginsinc.upnp;

import java.net.URL;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import jd.controlling.reconnect.ProcessCallBack;
import jd.controlling.reconnect.ReconnectConfig;
import jd.controlling.reconnect.ReconnectException;
import jd.controlling.reconnect.ReconnectInvoker;
import jd.controlling.reconnect.ReconnectResult;
import jd.controlling.reconnect.RouterPlugin;
import jd.controlling.reconnect.ipcheck.IP;
import jd.controlling.reconnect.ipcheck.IPCheckException;
import jd.controlling.reconnect.ipcheck.IPCheckProvider;
import jd.controlling.reconnect.ipcheck.InvalidIPRangeException;
import jd.controlling.reconnect.ipcheck.InvalidProviderException;
import jd.controlling.reconnect.pluginsinc.upnp.cling.UPNPDeviceScanner;
import jd.controlling.reconnect.pluginsinc.upnp.cling.UpnpRouterDevice;
import jd.controlling.reconnect.pluginsinc.upnp.translate.T;
import net.miginfocom.swing.MigLayout;

import org.appwork.storage.config.JsonConfig;
import org.appwork.swing.components.ExtButton;
import org.appwork.swing.components.ExtTextField;
import org.appwork.uio.CloseReason;
import org.appwork.uio.ConfirmDialogInterface;
import org.appwork.uio.UIOManager;
import org.appwork.utils.StringUtils;
import org.appwork.utils.formatter.TimeFormatter;
import org.appwork.utils.logging2.LogSource;
import org.appwork.utils.swing.EDTRunner;
import org.appwork.utils.swing.dialog.ConfirmDialog;
import org.appwork.utils.swing.dialog.Dialog;
import org.appwork.utils.swing.dialog.DialogCanceledException;
import org.appwork.utils.swing.dialog.DialogClosedException;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.images.AbstractIcon;
import org.jdownloader.logging.LogController;
import org.jdownloader.settings.advanced.AdvancedConfigManager;
import org.jdownloader.settings.staticreferences.CFG_RECONNECT;

public class UPNPRouterPlugin extends RouterPlugin implements IPCheckProvider {

    public static final String                 ID      = "SIMPLEUPNP";

    private ExtTextField                       serviceTypeTxt;
    private ExtTextField                       controlURLTxt;
    private JLabel                             wanType;

    protected java.util.List<UpnpRouterDevice> devices = null;

    private Icon                               icon;

    private UPUPReconnectSettings              settings;

    public UPNPRouterPlugin() {
        super();
        icon = new AbstractIcon(IconKey.ICON_LOGO_UPNP, 16);
        settings = JsonConfig.create(UPUPReconnectSettings.class);
        AdvancedConfigManager.getInstance().register(settings);

    }

    /**
     * sets the correct router settings automatically
     *
     * @throws InterruptedException
     */
    @Override
    public java.util.List<ReconnectResult> runDetectionWizard(ProcessCallBack processCallBack) throws InterruptedException {
        LogSource logger = LogController.getInstance().getLogger("UPNPReconnect");
        try {
            java.util.List<ReconnectResult> ret = new ArrayList<ReconnectResult>();
            java.util.List<UpnpRouterDevice> devices = getDevices();
            logger.info("Found devices: " + devices);
            for (int i = 0; i < devices.size(); i++) {
                UpnpRouterDevice device = devices.get(i);
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                }

                ReconnectResult res;
                try {
                    processCallBack.setStatusString(this, T.T.try_reconnect(device.getModelname()));
                    logger.info("Try " + device);
                    if (processCallBack.isMethodConfirmEnabled()) {
                        ConfirmDialog d = new ConfirmDialog(UIOManager.STYLE_SHOW_DO_NOT_DISPLAY_AGAIN, _GUI.T.runDetectionWizard_confirm_title(), _GUI.T.UPNPRouterPlugin_runDetectionWizard_confirm_msg(device.getServiceType(), device.getControlURL()), new AbstractIcon("upnp", 32), _GUI.T.lit_continue(), _GUI.T.lit_skip()) {
                            @Override
                            protected String getDontShowAgainLabelText() {
                                return _GUI.T.UPNPRouterPlugin_accept_all();
                            }

                            @Override
                            public String getDontShowAgainKey() {
                                return null;
                            }
                        };
                        ConfirmDialogInterface answer = UIOManager.I().show(ConfirmDialogInterface.class, d);
                        if (answer.getCloseReason() == CloseReason.OK) {
                            if (answer.isDontShowAgainSelected()) {
                                processCallBack.setMethodConfirmEnabled(false);
                            }
                        } else {
                            continue;
                        }
                    }
                    res = new UPNPReconnectInvoker(this, device.getServiceType(), device.getControlURL()).validate();
                    logger.info("REsult " + res);
                    if (res != null && res.isSuccess()) {
                        ret.add(res);
                        processCallBack.setStatus(this, ret);
                        if (i < devices.size() - 1) {

                            if (ret.size() == 1) {
                                Dialog.getInstance().showConfirmDialog(0, _GUI.T.LiveHeaderDetectionWizard_testList_firstSuccess_title(), _GUI.T.LiveHeaderDetectionWizard_testList_firstsuccess_msg(TimeFormatter.formatMilliSeconds(res.getSuccessDuration(), 0)), new AbstractIcon(IconKey.ICON_OK, 32), _GUI.T.LiveHeaderDetectionWizard_testList_ok(), _GUI.T.LiveHeaderDetectionWizard_testList_use());
                            }

                        }
                    }

                } catch (ReconnectException e) {
                    e.printStackTrace();
                } catch (DialogClosedException e) {

                } catch (DialogCanceledException e) {
                    return ret;
                }

            }
            return ret;
        } finally {
            logger.close();
        }

    }

    public IP getExternalIP() throws IPCheckException {
        String ipxml;
        LogSource logger = LogController.getInstance().getLogger("UPNPReconnect");
        try {
            ipxml = UPNPReconnectInvoker.sendRequest(logger, settings.getServiceType(), settings.getControlURL(), "GetExternalIPAddress");
            logger.clear();
        } catch (final Exception e) {
            this.setCanCheckIP(false);

            throw new InvalidProviderException("UPNP Command Error");
        } finally {
            logger.close();
        }
        try {
            final Matcher ipm = Pattern.compile("<\\s*NewExternalIPAddress\\s*>\\s*(.*)\\s*<\\s*/\\s*NewExternalIPAddress\\s*>", Pattern.CASE_INSENSITIVE).matcher(ipxml);
            if (ipm.find()) {
                return IP.getInstance(ipm.group(1));
            }
        } catch (final InvalidIPRangeException e2) {
            throw new InvalidProviderException(e2);
        }
        this.setCanCheckIP(false);

        throw new InvalidProviderException("Unknown UPNP Response Error");
    }

    @Override
    public Icon getIcon16() {
        return icon;
    }

    public void setSetup(ReconnectResult reconnectResult) {
        UPNPReconnectResult r = (UPNPReconnectResult) reconnectResult;
        UPNPReconnectInvoker i = (UPNPReconnectInvoker) r.getInvoker();
        settings.setControlURL(i.getControlURL());
        settings.setModelName(i.getName());
        settings.setServiceType(i.getServiceType());

        JsonConfig.create(ReconnectConfig.class).setSecondsBeforeFirstIPCheck((int) reconnectResult.getOfflineDuration() / 1000);
        JsonConfig.create(ReconnectConfig.class).setSecondsToWaitForIPChange((int) (reconnectResult.getMaxSuccessDuration()) / 1000);
        JsonConfig.create(ReconnectConfig.class).setSecondsToWaitForOffline((int) reconnectResult.getMaxOfflineDuration() / 1000);
        updateGUI();
    }

    @Override
    public JComponent getGUI() {
        final JPanel p = new JPanel(new MigLayout("ins 0 0 0 0,wrap 3", "[][][grow,fill]", "[fill]"));
        p.setOpaque(false);
        JButton find = new ExtButton(new UPNPScannerAction(this)).setTooltipsEnabled(true);
        find.setHorizontalAlignment(SwingConstants.LEFT);
        JButton auto = new ExtButton(new AutoDetectUpnpAction(this)).setTooltipsEnabled(true);
        auto.setHorizontalAlignment(SwingConstants.LEFT);
        this.serviceTypeTxt = new ExtTextField() {

            @Override
            public void onChanged() {
                final String serviceType = UPNPRouterPlugin.this.serviceTypeTxt.getText();
                try {
                    if (StringUtils.isNotEmpty(serviceType) && serviceType.toLowerCase(java.util.Locale.ENGLISH).startsWith("urn:schemas-upnp-org:service:")) {
                        settings.setServiceType(serviceType);
                        UPNPRouterPlugin.this.setCanCheckIP(true);
                    }
                } catch (final Throwable e) {
                }
            }

        };
        this.controlURLTxt = new ExtTextField() {

            @Override
            public void onChanged() {
                final String controlURL = UPNPRouterPlugin.this.controlURLTxt.getText();
                try {
                    if (StringUtils.isNotEmpty(controlURL) && new URL(controlURL).getHost() != null) {
                        settings.setControlURL(controlURL);
                        UPNPRouterPlugin.this.setCanCheckIP(true);
                    }
                } catch (final Throwable e) {
                }
            }

        };
        serviceTypeTxt.setHelpText(T.T.servicetype_help());
        controlURLTxt.setHelpText(T.T.controlURLTxt_help());

        this.wanType = new JLabel();
        p.add(auto, "aligny top,gapright 15,sg buttons");
        p.add(new JLabel(T.T.literally_router()), "");
        p.add(this.wanType, "spanx");
        p.add(find, "aligny top,gapright 15,newline,sg buttons");
        p.add(new JLabel(T.T.literally_service_type()), "");
        p.add(this.serviceTypeTxt);
        p.add(new JLabel(T.T.literally_control_url()), "newline,skip");
        p.add(this.controlURLTxt);

        // p.add(Box.createGlue(), "pushy,growy");
        this.updateGUI();

        return p;
    }

    @Override
    public String getID() {
        return UPNPRouterPlugin.ID;
    }

    public int getIpCheckInterval() {
        return 1;
    }

    public IPCheckProvider getIPCheckProvider() {
        if (!this.isIPCheckEnabled() || !CFG_RECONNECT.CFG.isIPCheckAllowLocalUpnpIpCheckEnabled()) {
            return null;
        }
        return this;
    }

    @Override
    public String getName() {
        return "UPNP - Universal Plug & Play (Fritzbox,...)";
    }

    @Override
    public int getWaittimeBeforeFirstIPCheck() {
        // if ipcheck is done over upnp, we do not have to use long intervals
        return 0;
    }

    public boolean isIPCheckEnabled() {
        return settings.isIPCheckEnabled();
    }

    public void setCanCheckIP(final boolean b) {
        settings.setIPCheckEnabled(b);

    }

    void setDevice(final UpnpRouterDevice upnpRouterDevice) {
        if (upnpRouterDevice == null) {
            settings.setControlURL(null);
            settings.setModelName(null);
            settings.setIPCheckEnabled(false);
            settings.setServiceType(null);
            settings.setWANService(null);
        } else {
            LogController.CL().info(upnpRouterDevice + "");
            settings.setControlURL(upnpRouterDevice.getControlURL());
            settings.setModelName(upnpRouterDevice.getModelname());
            settings.setServiceType(upnpRouterDevice.getServiceType());
            settings.setWANService(upnpRouterDevice.getWanservice());
            this.setCanCheckIP(true);
            this.updateGUI();
        }
    }

    private void updateGUI() {
        new EDTRunner() {

            @Override
            protected void runInEDT() {
                if (UPNPRouterPlugin.this.wanType != null) {
                    try {
                        UPNPRouterPlugin.this.wanType.setText(settings.getModelName() + (settings.getWANService().length() > 0 ? " (" + settings.getWANService() + ")" : ""));
                    } catch (final Throwable e) {
                    }
                    try {
                        UPNPRouterPlugin.this.serviceTypeTxt.setText(settings.getServiceType());
                    } catch (final Throwable e) {
                    }
                    try {
                        UPNPRouterPlugin.this.controlURLTxt.setText(settings.getControlURL());
                    } catch (final Throwable e) {
                    }

                }
            }

        };
    }

    @Override
    public ReconnectInvoker getReconnectInvoker() {
        return new UPNPReconnectInvoker(this, settings.getServiceType(), settings.getControlURL());
    }

    public synchronized java.util.List<UpnpRouterDevice> getCachedDevices() throws InterruptedException {

        return devices;
    }

    public synchronized java.util.List<UpnpRouterDevice> getDevices() throws InterruptedException {

        devices = new UPNPDeviceScanner().scan();
        return devices;
    }
}