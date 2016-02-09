package org.jdownloader.captcha.v2.solver.captchabrotherhood;

import java.awt.event.ActionEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.Icon;

import org.appwork.storage.config.JsonConfig;
import org.appwork.swing.components.tooltips.ExtTooltip;
import org.appwork.utils.Application;
import org.appwork.utils.os.CrossSystem;
import org.jdownloader.actions.AppAction;
import org.jdownloader.captcha.v2.ChallengeSolverConfig;
import org.jdownloader.captcha.v2.solver.cheapcaptcha.CheapCaptchaSolverService;
import org.jdownloader.captcha.v2.solver.dbc.DeathByCaptchaSolver;
import org.jdownloader.captcha.v2.solver.dbc.DeathByCaptchaSolverService;
import org.jdownloader.captcha.v2.solver.endcaptcha.EndCaptchaSolverService;
import org.jdownloader.captcha.v2.solver.imagetyperz.ImageTyperzSolverService;
import org.jdownloader.captcha.v2.solver.jac.JacSolverService;
import org.jdownloader.captcha.v2.solver.service.AbstractSolverService;
import org.jdownloader.captcha.v2.solver.solver9kw.NineKwSolverService;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.images.AbstractIcon;
import org.jdownloader.images.NewTheme;
import org.jdownloader.settings.advanced.AdvancedConfigManager;
import org.jdownloader.settings.staticreferences.CFG_CAPTCHABROTHERHOOD;
import org.jdownloader.settings.staticreferences.CFG_CBH;

import jd.gui.swing.jdgui.components.premiumbar.ServiceCollection;
import jd.gui.swing.jdgui.components.premiumbar.ServicePanel;
import jd.gui.swing.jdgui.components.premiumbar.ServicePanelExtender;
import jd.gui.swing.jdgui.views.settings.components.Checkbox;
import jd.gui.swing.jdgui.views.settings.components.PasswordInput;
import jd.gui.swing.jdgui.views.settings.components.SettingsButton;
import jd.gui.swing.jdgui.views.settings.components.TextInput;
import jd.gui.swing.jdgui.views.settings.panels.anticaptcha.AbstractCaptchaSolverConfigPanel;

public class CBSolverService extends AbstractSolverService implements ServicePanelExtender {
    public static final String         ID = "cb";
    private CaptchaBrotherHoodSettings config;
    private CBSolver                   solver;

    public CBSolverService() {
        config = JsonConfig.create(CaptchaBrotherHoodSettings.class);
        AdvancedConfigManager.getInstance().register(config);

        if (!Application.isHeadless()) {
            ServicePanel.getInstance().addExtender(this);
            initServicePanel(CFG_CBH.USER, CFG_CBH.PASS, CFG_CBH.ENABLED);
        }
    }

    @Override
    public String getID() {
        return ID;
    }

    @Override
    public void extendServicePabel(List<ServiceCollection<?>> services) {
        if (getSolver().validateLogins()) {
            services.add(new ServiceCollection<DeathByCaptchaSolver>() {

                @Override
                public Icon getIcon() {
                    return NewTheme.I().getIcon(IconKey.ICON_LOGO_CBH, 16);
                }

                @Override
                public boolean isEnabled() {
                    return config.isEnabled();
                }

                @Override
                protected long getLastActiveTimestamp() {
                    return System.currentTimeMillis();
                }

                @Override
                protected String getName() {
                    return "captchabrotherhood.com";
                }

                @Override
                public ExtTooltip createTooltip(ServicePanel owner) {
                    return new ServicePanelCBHTooltip(owner, CBSolverService.this);
                }

            });
        }
    }

    @Override
    public Icon getIcon(int size) {
        return NewTheme.I().getIcon(IconKey.ICON_LOGO_CBH, size);
    }

    @Override
    public String getType() {
        return _GUI.T.CBSolver_getName_();
    }

    @Override
    public AbstractCaptchaSolverConfigPanel getConfigPanel() {
        AbstractCaptchaSolverConfigPanel ret = new AbstractCaptchaSolverConfigPanel() {

            private TextInput     userName;
            private PasswordInput passWord;

            @Override
            public String getPanelID() {
                return "CES_" + getTitle();
            }

            {
                addHeader(getTitle(), new AbstractIcon(IconKey.ICON_LOGO_CBH, 32));
                addDescription(_GUI.T.AntiCaptchaConfigPanel_onShow_description_ces());

                add(new SettingsButton(new AppAction() {
                    {
                        setName(_GUI.T.lit_open_website());

                    }

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        CrossSystem.openURL("http://www.captchabrotherhood.com/");

                    }
                }), "gapleft 37,spanx,pushx,growx");

                userName = new TextInput(CFG_CAPTCHABROTHERHOOD.USER);
                passWord = new PasswordInput(CFG_CAPTCHABROTHERHOOD.PASS);

                this.addHeader(_GUI.T.MyJDownloaderSettingsPanel_MyJDownloaderSettingsPanel_logins_(), NewTheme.I().getIcon(IconKey.ICON_LOGINS, 32));
                // addPair(_GUI.T.MyJDownloaderSettingsPanel_MyJDownloaderSettingsPanel_enabled(), null, checkBox);
                this.addDescriptionPlain(_GUI.T.captchabrotherhoodService_createPanel_logins_());

                addPair(_GUI.T.captchabrotherhoodService_createPanel_enabled(), null, new Checkbox(CFG_CAPTCHABROTHERHOOD.ENABLED, userName, passWord));
                addPair(_GUI.T.captchabrotherhoodService_createPanel_username(), null, userName);
                addPair(_GUI.T.captchabrotherhoodService_createPanel_password(), null, passWord);
                addBlackWhiteList(CFG_CAPTCHABROTHERHOOD.CFG);

            }

            @Override
            public void save() {

            }

            @Override
            public void updateContents() {
            }

            @Override
            public Icon getIcon() {
                return CBSolverService.this.getIcon(32);
            }

            @Override
            public String getTitle() {
                return "captchabrotherhood.com";
            }

        };
        return ret;
    }

    @Override
    public boolean hasConfigPanel() {
        return true;
    }

    @Override
    public String getName() {
        return _GUI.T.CBSolver_gettypeName_();
    }

    @Override
    public ChallengeSolverConfig getConfig() {
        return config;
    }

    @Override
    public Map<String, Integer> getWaitForOthersDefaultMap() {
        HashMap<String, Integer> ret = new HashMap<String, Integer>();
        // ret.put(Captcha9kwSolverClick.ID, 60000);
        // ret.put(DialogClickCaptchaSolver.ID, 60000);
        // ret.put(DialogBasicCaptchaSolver.ID, 60000);
        // ret.put(CaptchaAPISolver.ID, 60000);
        ret.put(JacSolverService.ID, 30000);
        // ret.put(Captcha9kwSolver.ID, 60000);
        // ret.put(CaptchaMyJDSolverService.ID, 60000);
        ret.put(NineKwSolverService.ID, 60000);
        ret.put(DeathByCaptchaSolverService.ID, 60000);
        ret.put(ImageTyperzSolverService.ID, 60000);
        ret.put(CheapCaptchaSolverService.ID, 60000);
        ret.put(EndCaptchaSolverService.ID, 60000);
        return ret;
    }

    public void setSolver(CBSolver cbSolver) {
        this.solver = cbSolver;
    }

    public CBSolver getSolver() {
        return solver;
    }

}
