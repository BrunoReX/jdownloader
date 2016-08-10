package jd.controlling.reconnect.pluginsinc.liveheader;

import java.awt.Dialog.ModalityType;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import jd.controlling.reconnect.ProcessCallBack;
import jd.controlling.reconnect.ReconnectConfig;
import jd.controlling.reconnect.ReconnectInvoker;
import jd.controlling.reconnect.ReconnectPluginController;
import jd.controlling.reconnect.ReconnectResult;
import jd.controlling.reconnect.RouterPlugin;
import jd.controlling.reconnect.ipcheck.IP;
import jd.controlling.reconnect.pluginsinc.liveheader.recorder.Gui;
import jd.controlling.reconnect.pluginsinc.liveheader.remotecall.RouterData;
import jd.controlling.reconnect.pluginsinc.liveheader.translate.T;
import jd.gui.UserIO;
import net.miginfocom.swing.MigLayout;

import org.appwork.storage.config.JsonConfig;
import org.appwork.storage.config.ValidationException;
import org.appwork.storage.config.events.ConfigEventListener;
import org.appwork.storage.config.handler.KeyHandler;
import org.appwork.swing.components.ExtButton;
import org.appwork.swing.components.ExtPasswordField;
import org.appwork.swing.components.ExtTextField;
import org.appwork.uio.CloseReason;
import org.appwork.uio.ConfirmDialogInterface;
import org.appwork.uio.UIOManager;
import org.appwork.utils.Application;
import org.appwork.utils.StringUtils;
import org.appwork.utils.swing.EDTHelper;
import org.appwork.utils.swing.EDTRunner;
import org.appwork.utils.swing.SwingUtils;
import org.appwork.utils.swing.dialog.ConfirmDialog;
import org.appwork.utils.swing.dialog.Dialog;
import org.appwork.utils.swing.dialog.DialogCanceledException;
import org.appwork.utils.swing.dialog.DialogClosedException;
import org.appwork.utils.swing.dialog.OKCancelCloseUserIODefinition;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.images.AbstractIcon;
import org.jdownloader.logging.LogController;
import org.jdownloader.settings.advanced.AdvancedConfigManager;

public class LiveHeaderReconnect extends RouterPlugin implements ConfigEventListener {

    private ExtTextField     txtUser;
    private ExtPasswordField txtPassword;

    private ExtTextField     txtIP;

    @Override
    public Icon getIcon16() {
        return icon;
    }

    private ExtTextField                txtName;
    private Icon                        icon;

    private LiveHeaderReconnectSettings settings;

    public static final String          ID = "httpliveheader";

    public LiveHeaderReconnect() {
        super();
        this.icon = new AbstractIcon(IconKey.ICON_MODEM, 16);

        // only listen to system to autosend script
        // Send routerscript if there were 3 successful recoinnects in a row
        JsonConfig.create(ReconnectConfig.class)._getStorageHandler().getEventSender().addListener(this);
        settings = JsonConfig.create(LiveHeaderReconnectSettings.class);
        settings._getStorageHandler().getEventSender().addListener(this);
        AdvancedConfigManager.getInstance().register(JsonConfig.create(LiveHeaderReconnectSettings.class));
    }

    void editScript(final boolean wait) {

        // final InputDialog dialog = new InputDialog(Dialog.STYLE_LARGE | Dialog.STYLE_HIDE_ICON, "Script Editor",
        // "Please enter a Liveheader script below.", settings.getScript(), new AbstractIcon(IconKey.ICON_EDIT, 18),
        // T.T.jd_controlling_reconnect_plugins_liveheader_LiveHeaderReconnect_actionPerformed_save(), null);
        // dialog.setPreferredSize(new Dimension(700, 400));

        RouterData editing = settings.getRouterData();
        if (editing == null) {
            editing = new RouterData();

        }
        final RouterData rd = editing;
        editing.setScript(settings.getScript());
        editing.setRouterIP(settings.getRouterIP());
        final LiveHeaderScriptConfirmDialog d = new LiveHeaderScriptConfirmDialog(Dialog.STYLE_HIDE_ICON, T.T.script(getRouterName(editing.getRouterName())), new AbstractIcon("reconnect", 32), _GUI.T.lit_save(), _GUI.T.lit_close(), editing, null, editing.getRouterName()) {
            @Override
            public String getMessage() {
                return T.T.edit_script();
            }

            @Override
            public ModalityType getModalityType() {
                if (wait) {
                    return super.getModalityType();
                } else {
                    return ModalityType.MODELESS;
                }
            }
        };
        new Thread() {
            {
                setDaemon(true);
            }

            @Override
            public void run() {
                try {
                    UIOManager.I().show(OKCancelCloseUserIODefinition.class, d).throwCloseExceptions();

                    validateAndSet(rd);
                    // settings.setScript(rd.getScript());

                } catch (DialogClosedException e) {
                    e.printStackTrace();
                } catch (DialogCanceledException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    private String getRouterName(String routerName) {
        if (StringUtils.isEmpty(routerName)) {
            return _GUI.T.unknown_router_name();
        }
        return routerName;
    }

    protected void validateAndSet(RouterData rd) {
        if (!StringUtils.equals(settings.getScript(), rd.getScript())) {
            settings.setScript(rd.getScript());
            rd.setScriptID(null);
            settings.setRouterData(rd);
        }
    }

    @Override
    public JComponent getGUI() {
        final JPanel p = new JPanel(new MigLayout("ins 0 0 0 0,wrap 3", "[][][grow,fill]", ""));
        p.setOpaque(false);
        // auto search is not ready yet
        // this.btnAuto.setEnabled(false);

        this.txtUser = new ExtTextField();
        txtUser.setHelpText(T.T.LiveHeaderReconnect_getGUI_help_user());
        txtUser.addFocusListener(new FocusListener() {

            public void focusLost(FocusEvent e) {
                settings.setUserName(LiveHeaderReconnect.this.txtUser.getText());
            }

            public void focusGained(FocusEvent e) {
            }
        });

        this.txtPassword = new ExtPasswordField() {
            public void onChanged() {
                settings.setPassword(new String(LiveHeaderReconnect.this.txtPassword.getPassword()));
            }
        };
        txtPassword.setHelpText(T.T.LiveHeaderReconnect_getGUI_help_password());
        this.txtIP = new ExtTextField();
        txtIP.setHelpText(T.T.LiveHeaderReconnect_getGUI_help_ip());

        txtIP.addFocusListener(new FocusListener() {

            public void focusLost(FocusEvent e) {
                settings.setRouterIP(LiveHeaderReconnect.this.txtIP.getText());
            }

            public void focusGained(FocusEvent e) {
            }
        });

        this.txtName = new ExtTextField();
        txtName.setEditable(false);
        txtName.setBorder(null);
        SwingUtils.setOpaque(txtName, false);

        //

        p.add(createButton(new RouterSendAction(this)), "sg buttons,aligny top,newline");
        p.add(new JLabel(T.T.literally_router_model()), "");
        p.add(this.txtName, "spanx");
        //
        p.add(createButton(new GetIPAction(this)), "sg buttons,aligny top,newline");
        p.add(new JLabel(T.T.literally_router_ip()), "");
        p.add(this.txtIP, "spanx");
        //
        p.add(createButton(new ReconnectRecorderAction(this)), "sg buttons,aligny top,newline");
        p.add(new JLabel(T.T.literally_username()), "");
        p.add(this.txtUser, "spanx");
        //
        p.add(createButton(new EditScriptAction(this)), "sg buttons,aligny top,newline");
        p.add(new JLabel(T.T.literally_password()), "");
        p.add(this.txtPassword, "spanx");
        //
        p.add(createButton(new SearchScriptAction(this)), "sg buttons,aligny top,newline");
        // p.add(new JLabel("Control URL"), "newline,skip");
        // p.add(this.controlURLTxt);
        // p.add(Box.createGlue(), "pushy,growy");
        this.updateGUI();
        return p;
    }

    private JButton createButton(AbstractAction autoDetectAction) {
        ExtButton ret = new ExtButton(autoDetectAction);
        ret.setHorizontalAlignment(SwingConstants.LEFT);
        ret.setTooltipsEnabled(true);
        return ret;
    }

    @Override
    public String getID() {
        return LiveHeaderReconnect.ID;
    }

    @Override
    public String getName() {
        return "LiveHeader";
    }

    public void routerRecord() {

        if (JsonConfig.create(ReconnectConfig.class).isIPCheckGloballyDisabled()) {
            UserIO.getInstance().requestMessageDialog(UserIO.ICON_WARNING, T.T.jd_gui_swing_jdgui_settings_panels_downloadandnetwork_advanced_ipcheckdisable_warning_title(), T.T.jd_gui_swing_jdgui_settings_panels_downloadandnetwork_advanced_ipcheckdisable_warning_message());
        } else {
            new Thread() {
                @Override
                public void run() {
                    final String text = LiveHeaderReconnect.this.txtIP.getText().toString();
                    if (StringUtils.isEmpty(text) || !IP.isValidRouterIP(text)) {
                        new GetIPAction(LiveHeaderReconnect.this).actionPerformed(null);
                    }

                    new EDTHelper<Object>() {

                        @Override
                        public Object edtRun() {

                            final Gui jd = new Gui(settings.getRouterIP());
                            try {
                                UIOManager.I().show(null, jd).throwCloseExceptions();

                                if (jd.saved) {
                                    settings.setRouterIP(jd.ip);

                                    if (jd.user != null) {
                                        settings.setUserName(jd.user);
                                    }
                                    if (jd.pass != null) {
                                        settings.setPassword(jd.pass);

                                    }
                                    // changed script.reset router sender state
                                    if ((jd.methode != null && jd.methode.equals(settings.getScript()))) {
                                        settings.setAlreadySendToCollectServer3(false);
                                    }
                                    settings.setScript(jd.methode);
                                    setName("Router Recorder Custom Script");

                                }
                            } catch (DialogClosedException e) {
                                e.printStackTrace();
                            } catch (DialogCanceledException e) {
                                e.printStackTrace();
                            }

                            return null;
                        }

                    }.start();

                }
            }.start();
        }
    }

    public java.util.List<ReconnectResult> runDetectionWizard(ProcessCallBack processCallBack) throws InterruptedException {
        final LiveHeaderDetectionWizard wizard = new LiveHeaderDetectionWizard();
        try {
            return wizard.runOnlineScan(processCallBack);
        } catch (InterruptedException e) {
            throw e;
        } catch (Throwable e) {
            LogController.CL().log(e);
        }
        return null;
    }

    void updateGUI() {
        if (!Application.isHeadless()) {
            new EDTRunner() {
                protected void runInEDT() {
                    try {
                        String str = getRouterName();

                        if (settings.getRouterData().getManufactor() != null && settings.getRouterData().getManufactor().length() > 0) {
                            if (str.length() > 0) {
                                str += " - ";
                            }
                            str += settings.getRouterData().getManufactor();
                        }
                        LiveHeaderReconnect.this.txtName.setText(str);
                    } catch (final Throwable e) {
                        // throws an Throwable if the caller
                        // is a changelistener of this field's document
                    }
                    try {
                        LiveHeaderReconnect.this.txtIP.setText(settings.getRouterIP());
                    } catch (final Throwable e) {
                        // throws an Throwable if the caller
                        // is a changelistener of this field's document
                    }
                    try {
                        LiveHeaderReconnect.this.txtPassword.setPassword(settings.getPassword().toCharArray());
                    } catch (final Throwable e) {
                        // throws an Throwable if the caller
                        // is a changelistener of this field's document
                    }
                    try {
                        LiveHeaderReconnect.this.txtUser.setText(settings.getUserName());
                    } catch (final Throwable e) {
                        // throws an Throwable if the caller
                        // is a changelistener of this field's document
                    }

                }

            };
        }
    }

    public void onConfigValidatorError(KeyHandler<Object> keyHandler, Object invalidValue, ValidationException validateException) {
    }

    public void onConfigValueModified(KeyHandler<Object> keyHandler, Object newValue) {
        if (keyHandler.isChildOf(settings)) {
            updateGUI();
            if (!keyHandler.getKey().equalsIgnoreCase("AlreadySendToCollectServer2")) {
                settings.setAlreadySendToCollectServer3(false);
            }
        } else {
            if (!Application.isHeadless() && settings.isAutoReplaceIPEnabled()) {
                // disabled
                RouterSendAction action = new RouterSendAction(this);
                if (!action.isEnabled()) {
                    return;
                }
                LogController.CL().info("Successful reonnects in a row: " + JsonConfig.create(ReconnectConfig.class).getSuccessCounter());
                synchronized (this) {
                    if (!settings.isAlreadySendToCollectServer3() && ReconnectPluginController.getInstance().getActivePlugin() == this) {
                        if (JsonConfig.create(ReconnectConfig.class).getSuccessCounter() > 3) {
                            if (CloseReason.OK == UIOManager.I().show(ConfirmDialogInterface.class, new ConfirmDialog(UIOManager.LOGIC_DONT_SHOW_AGAIN_IGNORES_OK | Dialog.STYLE_SHOW_DO_NOT_DISPLAY_AGAIN | UIOManager.LOGIC_COUNTDOWN, T.T.LiveHeaderReconnect_onConfigValueModified_ask_title(), T.T.LiveHeaderReconnect_onConfigValueModified_ask_msg(), icon, null, null) {

                                {
                                    setTimeout(5 * 60 * 1000);
                                }

                            }).getCloseReason()) {

                                action.actionPerformed(null);

                            }
                            settings.setAlreadySendToCollectServer3(true);
                        }
                    }
                }
            }
        }
    }

    public void setSetup(ReconnectResult reconnectResult) {
        if (reconnectResult.getInvoker() instanceof LiveHeaderInvoker) {
            LiveHeaderInvoker i = (LiveHeaderInvoker) reconnectResult.getInvoker();
            RouterData rd = ((LiveHeaderReconnectResult) reconnectResult).getRouterData();
            rd.setRouterName(i.getName());
            settings.setRouterData(rd);
            settings.setPassword(i.getPass());
            settings.setUserName(i.getUser());
            settings.setRouterIP(i.getRouter());
            // changed script.reset router sender state
            final LiveHeaderReconnectSettings liveHeaderReconnectSettings = JsonConfig.create(LiveHeaderReconnectSettings.class);
            if (i.getScript() != null && i.getScript().equals(liveHeaderReconnectSettings.getScript())) {
                liveHeaderReconnectSettings.setAlreadySendToCollectServer3(false);
            }

            settings.setScript(i.getScript());
            final ReconnectConfig reconnectConfig = JsonConfig.create(ReconnectConfig.class);
            reconnectConfig.setSecondsBeforeFirstIPCheck((int) reconnectResult.getOfflineDuration() / 1000);
            reconnectConfig.setSecondsToWaitForIPChange((int) (reconnectResult.getMaxSuccessDuration() / 1000));
            reconnectConfig.setSecondsToWaitForOffline((int) reconnectResult.getMaxOfflineDuration() / 1000);
            updateGUI();
        }
    }

    @Override
    public ReconnectInvoker getReconnectInvoker() {
        String script;
        script = settings.getScript();
        if (script == null) {
            return null;
        }
        final String user = settings.getUserName();
        final String pass = settings.getPassword();
        final String ip = settings.getRouterIP();
        RouterData rd = settings.getRouterData();
        //
        LiveHeaderInvoker ret = new LiveHeaderInvoker(this, script, user, pass, ip, getRouterName());
        if (rd != null) {
            if (StringUtils.equals(rd.getScript(), script)) {
                ret.setRouterData(rd);
            }
        }
        return ret;

    }

    protected String getRouterName() {

        final RouterData routerData = settings.getRouterData();
        if (routerData != null) {
            final String ret = routerData.getRouterName();
            if (StringUtils.isNotEmpty(ret)) {
                return ret;
            }

        }
        return "<unknown router>";
    }
}