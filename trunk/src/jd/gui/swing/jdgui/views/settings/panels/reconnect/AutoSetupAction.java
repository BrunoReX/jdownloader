package jd.gui.swing.jdgui.views.settings.panels.reconnect;

import java.awt.event.ActionEvent;
import java.util.ArrayList;

import jd.controlling.reconnect.ProcessCallBackAdapter;
import jd.controlling.reconnect.ReconnectConfig;
import jd.controlling.reconnect.ReconnectPluginController;
import jd.controlling.reconnect.ReconnectResult;
import jd.controlling.reconnect.RouterPlugin;
import jd.controlling.reconnect.RouterUtils;
import jd.controlling.reconnect.WizardUtils;
import jd.controlling.reconnect.ipcheck.IPController;
import jd.controlling.reconnect.pluginsinc.liveheader.LiveHeaderReconnectResult;
import jd.controlling.reconnect.pluginsinc.liveheader.ReconnectFindDialog;
import jd.controlling.reconnect.pluginsinc.liveheader.translate.T;

import org.appwork.storage.config.JsonConfig;
import org.appwork.swing.action.BasicAction;
import org.appwork.swing.components.tooltips.BasicTooltipFactory;
import org.appwork.uio.ConfirmDialogInterface;
import org.appwork.uio.UIOManager;
import org.appwork.utils.swing.dialog.ConfirmDialog;
import org.appwork.utils.swing.dialog.DialogCanceledException;
import org.appwork.utils.swing.dialog.DialogClosedException;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.images.AbstractIcon;
import org.jdownloader.images.NewTheme;
import org.jdownloader.translate._JDT;

public class AutoSetupAction extends BasicAction {

    /**
	 * 
	 */
    private static final long serialVersionUID = -1089268040145686928L;

    public AutoSetupAction() {
        putValue(NAME, _JDT.T.reconnectmanager_wizard());
        putValue(SMALL_ICON, new AbstractIcon(IconKey.ICON_WIZARD, 20));

        this.setTooltipFactory(new BasicTooltipFactory(getName(), _GUI.T.AutoSetupAction_tt(), new AbstractIcon(IconKey.ICON_WIZARD, 32)));

    }

    public void actionPerformed(ActionEvent e) {

        final ConfirmDialog confirm = new ConfirmDialog(0, _GUI.T.AutoSetupAction_actionPerformed_warn_title(), _GUI.T.AutoSetupAction_actionPerformed_warn_message(), new AbstractIcon(IconKey.ICON_WARNING, 32), _GUI.T.AutoSetupAction_actionPerformed_warn_message_continue(), null) {
            @Override
            protected int getPreferredWidth() {
                return 750;
            }

            @Override
            public boolean isRemoteAPIEnabled() {
                return false;
            }

        };

        try {

            UIOManager.I().show(ConfirmDialogInterface.class, confirm).throwCloseExceptions();
        } catch (Throwable e2) {
            e2.printStackTrace();
            return;

        }
        boolean pre = JsonConfig.create(ReconnectConfig.class).isIPCheckGloballyDisabled();
        try {
            if (pre) {
                if (!UIOManager.I().showConfirmDialog(0, _GUI.T.literally_warning(), T.T.ipcheck())) {
                    return;
                }

                JsonConfig.create(ReconnectConfig.class).setIPCheckGloballyDisabled(false);

            }

            ReconnectFindDialog d = new ReconnectFindDialog() {

                @Override
                public void run() throws InterruptedException {
                    setBarText(_GUI.T.LiveaheaderDetection_wait_for_online());
                    IPController.getInstance().waitUntilWeAreOnline();
                    setBarText(_GUI.T.LiveaheaderDetection_find_router());
                    RouterUtils.getAddress(false);
                    setBarText(_GUI.T.LiveaheaderDetection_network_setup_check());

                    if (WizardUtils.modemCheck()) {
                        return;
                    }
                    if (WizardUtils.vpnCheck()) {
                        return;
                    }
                    final java.util.List<ReconnectResult> scripts = ReconnectPluginController.getInstance().autoFind(new ProcessCallBackAdapter() {

                        public void setStatusString(Object caller, String string) {

                            setBarText(string);
                        }

                        public void setProgress(Object caller, int percent) {
                            setBarProgress(percent);
                        }

                        public void setStatus(Object caller, Object statusObject) {
                            if (caller instanceof RouterPlugin) {
                                setSubStatusHeader(_GUI.T.ReconnectDialog_layoutDialogContent_header(((RouterPlugin) caller).getName()));
                            }
                            if (statusObject instanceof ArrayList) {
                                try {
                                    @SuppressWarnings("unchecked")
                                    java.util.List<LiveHeaderReconnectResult> foundScripts = (java.util.List<LiveHeaderReconnectResult>) statusObject;
                                    if (foundScripts.size() > 0) {
                                        setInterruptEnabled(foundScripts);
                                    }
                                } catch (Throwable e) {

                                }
                            }
                        }
                    });

                    if (scripts != null && scripts.size() > 0) {

                        System.out.println("Scripts " + scripts);
                        ReconnectPluginController.getInstance().setActivePlugin(scripts.get(0).getInvoker().getPlugin());
                        scripts.get(0).getInvoker().getPlugin().setSetup(scripts.get(0));

                    } else {
                        UIOManager.I().showErrorMessage(T.T.AutoDetectAction_run_failed());

                    }

                }

            };

            UIOManager.I().show(null, d).throwCloseExceptions();
        } catch (DialogClosedException e1) {
            e1.printStackTrace();
        } catch (DialogCanceledException e1) {
            e1.printStackTrace();
        } finally {

            JsonConfig.create(ReconnectConfig.class).setIPCheckGloballyDisabled(pre);
            if (pre) {

                UIOManager.I().showMessageDialog(T.T.ipcheckreverted());
            }
        }
    }

}
