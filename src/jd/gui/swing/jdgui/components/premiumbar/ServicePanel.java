//    jDownloader - Downloadmanager
//    Copyright (C) 2009  JD-Team support@jdownloader.org
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://www.gnu.org/licenses/>.

package jd.gui.swing.jdgui.components.premiumbar;

import java.awt.HeadlessException;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import jd.SecondLevelLaunch;
import jd.controlling.AccountController;
import jd.controlling.AccountControllerEvent;
import jd.controlling.AccountControllerListener;
import jd.gui.swing.dialog.AddAccountDialog;
import jd.gui.swing.jdgui.JDGui;
import jd.gui.swing.jdgui.views.settings.ConfigurationView;
import jd.gui.swing.jdgui.views.settings.panels.accountmanager.AccountManagerSettings;
import jd.plugins.Account;
import jd.plugins.AccountInfo;
import jd.plugins.PluginForHost;
import net.miginfocom.swing.MigLayout;

import org.appwork.scheduler.DelayedRunnable;
import org.appwork.storage.config.JsonConfig;
import org.appwork.storage.config.ValidationException;
import org.appwork.storage.config.events.GenericConfigEventListener;
import org.appwork.storage.config.handler.KeyHandler;
import org.appwork.swing.components.ExtButton;
import org.appwork.utils.Application;
import org.appwork.utils.StringUtils;
import org.appwork.utils.formatter.TimeFormatter;
import org.appwork.utils.swing.EDTHelper;
import org.jdownloader.DomainInfo;
import org.jdownloader.actions.AppAction;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.images.AbstractIcon;
import org.jdownloader.logging.LogController;
import org.jdownloader.plugins.controller.host.HostPluginController;
import org.jdownloader.plugins.controller.host.LazyHostPlugin;
import org.jdownloader.settings.GraphicalUserInterfaceSettings;
import org.jdownloader.settings.GraphicalUserInterfaceSettings.PremiumStatusBarDisplay;
import org.jdownloader.settings.staticreferences.CFG_GUI;

public class ServicePanel extends JPanel implements MouseListener, AccountTooltipOwner {

    private static final long                                serialVersionUID = 7290466989514173719L;

    private DelayedRunnable                                  redrawTimer;

    private final CopyOnWriteArrayList<ServicePanelExtender> extender;
    private static ServicePanel                              INSTANCE         = new ServicePanel();
    static {
        if (Application.isHeadless()) {
            throw new HeadlessException();
        }
    }
    private AtomicBoolean                                    redrawing        = new AtomicBoolean(false);

    public static ServicePanel getInstance() {
        return INSTANCE;
    }

    public void addExtender(ServicePanelExtender ex) {
        if (!Application.isHeadless() && extender.addIfAbsent(ex)) {
            redrawTimer.delayedrun();
        }
    }

    public void requestUpdate(boolean immediately) {
        if (immediately) {
            redrawTimer.delayedrun();
        } else {
            redraw();
        }
    }

    public void removeExtender(ServicePanelExtender ex) {
        if (extender.remove(ex)) {
            redrawTimer.delayedrun();
        }
    }

    private ServicePanel() {
        super(new MigLayout("ins 0 2 0", "0[]0[]0[]0[]0", "0[]0"));

        extender = new CopyOnWriteArrayList<ServicePanelExtender>();
        this.setOpaque(false);
        final ScheduledExecutorService scheduler = DelayedRunnable.getNewScheduledExecutorService();

        redrawTimer = new DelayedRunnable(scheduler, 1000, 5000) {

            @Override
            public String getID() {
                return "PremiumStatusRedraw";
            }

            @Override
            public void delayedrun() {

                redraw();
            }

        };
        redraw();

        CFG_GUI.PREMIUM_STATUS_BAR_DISPLAY.getEventSender().addListener(new GenericConfigEventListener<Enum>() {

            @Override
            public void onConfigValidatorError(KeyHandler<Enum> keyHandler, Enum invalidValue, ValidationException validateException) {
            }

            @Override
            public void onConfigValueModified(KeyHandler<Enum> keyHandler, Enum newValue) {
                redraw();
            }
        });
        org.jdownloader.settings.staticreferences.CFG_GENERAL.USE_AVAILABLE_ACCOUNTS.getEventSender().addListener(new GenericConfigEventListener<Boolean>() {

            public void onConfigValueModified(KeyHandler<Boolean> keyHandler, Boolean newValue) {
                redraw();
            }

            public void onConfigValidatorError(KeyHandler<Boolean> keyHandler, Boolean invalidValue, ValidationException validateException) {
            }
        });
        SecondLevelLaunch.ACCOUNTLIST_LOADED.executeWhenReached(new Runnable() {

            public void run() {
                new Thread() {

                    @Override
                    public void run() {
                        redrawTimer.run();
                        AccountController.getInstance().getEventSender().addListener(new AccountControllerListener() {

                            public void onAccountControllerEvent(AccountControllerEvent event) {
                                if (org.jdownloader.settings.staticreferences.CFG_GENERAL.USE_AVAILABLE_ACCOUNTS.isEnabled()) {
                                    redrawTimer.run();
                                }
                            }
                        });
                        redraw();
                    }
                }.start();
            }
        });
    }

    public void redraw() {
        if (SecondLevelLaunch.ACCOUNTLIST_LOADED.isReached()) {
            if (redrawing.compareAndSet(false, true)) {
                try {
                    final List<ServiceCollection<?>> services = groupServices(CFG_GUI.CFG.getPremiumStatusBarDisplay(), true, null, null);
                    new EDTHelper<Object>() {
                        @Override
                        public Object edtRun() {
                            try {
                                try {
                                    removeAll();
                                    // Math.min(, JsonConfig.create(GeneralSettings.class).getMaxPremiumIcons());
                                    StringBuilder sb = new StringBuilder();
                                    sb.append("2");
                                    for (int i = 0; i < services.size(); i++) {
                                        sb.append("[22!]0");
                                    }
                                    boolean hasValidAccount = false;
                                    for (ServiceCollection<?> s : services) {
                                        if (s instanceof AccountServiceCollection) {
                                            hasValidAccount = true;
                                            break;
                                        }
                                    }

                                    if (!hasValidAccount) {
                                        sb.append("[]0");
                                    }
                                    setLayout(new MigLayout("ins 0 2 0 0", sb.toString(), "[22!]"));
                                    for (ServiceCollection<?> s : services) {

                                        final JComponent c = s.createIconComponent(ServicePanel.this);
                                        if (c != null) {
                                            add(c, "gapleft 0,gapright 0");
                                        }

                                    }
                                    if (!hasValidAccount && CFG_GUI.CFG.isStatusBarAddPremiumButtonVisible()) {
                                        ExtButton addPremium = new ExtButton(new AppAction() {
                                            {
                                                setName(_GUI.T.StatusBarImpl_add_premium());
                                            }

                                            @Override
                                            public void actionPerformed(ActionEvent e) {
                                                JsonConfig.create(GraphicalUserInterfaceSettings.class).setConfigViewVisible(true);
                                                JDGui.getInstance().setContent(ConfigurationView.getInstance(), true);
                                                ConfigurationView.getInstance().setSelectedSubPanel(AccountManagerSettings.class);
                                                SwingUtilities.invokeLater(new Runnable() {

                                                    @Override
                                                    public void run() {
                                                        AddAccountDialog.showDialog(null, null);
                                                    }
                                                });

                                            }
                                        });
                                        addPremium.setRolloverEffectEnabled(true);
                                        addPremium.setHorizontalAlignment(SwingConstants.LEFT);
                                        addPremium.setIcon(new AbstractIcon(IconKey.ICON_ADD, 18));
                                        // addPremium.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                                        add(addPremium, "height 20!,gapright 10!");

                                    }
                                    revalidate();
                                    repaint();
                                } catch (final Throwable e) {
                                    org.appwork.utils.logging2.extmanager.LoggerFactory.getDefaultLogger().log(e);
                                }
                                invalidate();
                            } finally {
                                redrawing.compareAndSet(true, false);
                            }
                            return null;
                        }
                    }.start();
                } catch (final Throwable e) {
                    redrawing.compareAndSet(true, false);
                }
            }
        }
    }

    public List<ServiceCollection<?>> groupServices(PremiumStatusBarDisplay premiumStatusBarDisplay, boolean extend, final String hostFilter, Account account) {
        final List<Account> accounts = AccountController.getInstance().list();
        final HashMap<String, AccountServiceCollection> servicesMap = new HashMap<String, AccountServiceCollection>();
        final List<ServiceCollection<?>> services = new ArrayList<ServiceCollection<?>>();
        try {
            Collections.sort(accounts, new Comparator<Account>() {
                public int compare(boolean x, boolean y) {
                    return (x == y) ? 0 : (x ? 1 : -1);
                }

                @Override
                public int compare(Account o1, Account o2) {
                    return compare(o1.isMultiHost(), o2.isMultiHost());
                }

            });
        } catch (final Throwable e) {
            LogController.CL(true).log(e);
        }
        if (account != null) {
            accounts.remove(account);
            accounts.add(0, account);
        }
        for (final Account acc : accounts) {
            if (acc.getLastValidTimestamp() < 0 && acc.getError() != null) {
                continue;
            }
            final long expireTimeMS = CFG_GUI.CFG.getPremiumStatusBarDisabledAccountExpire() * 7 * 24 * 60 * 60 * 1000l;
            final long timeSinceValidLogin = System.currentTimeMillis() - acc.getLastValidTimestamp();
            final boolean debug = false;
            if (debug) {
                if (acc.getLastValidTimestamp() == -1) {
                    System.out.println(acc.getUser() + " NO LAST LOGIN");
                } else if (timeSinceValidLogin > expireTimeMS) {
                    System.out.println(acc.getHoster() + " @ " + acc.getUser() + " last login: " + TimeFormatter.formatMilliSeconds(timeSinceValidLogin, 0));
                }
            }
            if (acc.getLastValidTimestamp() == -1 || (!acc.isEnabled() && timeSinceValidLogin > expireTimeMS)) {
                continue;
            }

            final PluginForHost plugin = acc.getPlugin();
            if (plugin != null) {
                final DomainInfo domainInfo = DomainInfo.getInstance(plugin.getHost());
                final String domainTld = domainInfo.getTld();
                domainInfo.getFavIcon();
                switch (premiumStatusBarDisplay) {
                case DONT_GROUP:
                    if (hostFilter == null || StringUtils.equals(hostFilter, domainTld)) {
                        final AccountServiceCollection asc = new AccountServiceCollection(domainInfo);
                        asc.add(acc);
                        services.add(asc);
                    }
                    break;
                case GROUP_BY_ACCOUNT_TYPE:
                case GROUP_BY_SUPPORTED_ACCOUNTS:
                case GROUP_BY_SUPPORTED_HOSTS:
                    if (hostFilter == null || StringUtils.equals(hostFilter, domainTld)) {
                        AccountServiceCollection asc = servicesMap.get(domainTld);
                        if (asc == null) {
                            asc = new AccountServiceCollection(domainInfo);
                            servicesMap.put(domainTld, asc);
                            services.add(asc);
                        }
                        asc.add(acc);
                    }
                    if (PremiumStatusBarDisplay.GROUP_BY_ACCOUNT_TYPE.equals(premiumStatusBarDisplay)) {
                        break;
                    }
                    final HashMap<String, DomainInfo> domainInfoCache = new HashMap<String, DomainInfo>();
                    final AccountInfo accountInfo = acc.getAccountInfo();
                    if (accountInfo != null) {
                        final List<String> supportedHosts = accountInfo.getMultiHostSupport();
                        if (supportedHosts != null) {
                            /*
                             * synchronized on list because plugins can change the list in runtime
                             */
                            for (final String supportedHost : supportedHosts) {
                                DomainInfo supportedHostDomainInfo = domainInfoCache.get(supportedHost);
                                if (supportedHostDomainInfo == null) {
                                    final LazyHostPlugin plg = HostPluginController.getInstance().get(supportedHost);
                                    if (plg != null) {
                                        supportedHostDomainInfo = DomainInfo.getInstance(plg.getHost());
                                        domainInfoCache.put(supportedHost, supportedHostDomainInfo);
                                    }
                                }
                                if (supportedHostDomainInfo != null && (hostFilter == null || StringUtils.equals(hostFilter, supportedHostDomainInfo.getTld()))) {
                                    AccountServiceCollection asc = servicesMap.get(supportedHostDomainInfo.getTld());
                                    if (asc == null) {
                                        asc = new AccountServiceCollection(supportedHostDomainInfo);
                                        servicesMap.put(supportedHostDomainInfo.getTld(), asc);
                                        services.add(asc);
                                    }
                                    asc.add(acc);
                                }
                            }
                        }
                    }
                    break;
                default:
                    break;
                }
            }

        }
        if (PremiumStatusBarDisplay.GROUP_BY_SUPPORTED_ACCOUNTS.equals(premiumStatusBarDisplay)) {
            for (ServiceCollection<?> serviceCollection : services) {
                ((AccountServiceCollection) serviceCollection).disableMulti();
            }
        }
        if (extend) {
            for (ServicePanelExtender bla : extender) {
                bla.extendServicePabel(services);
            }
        }
        Collections.sort(services);
        return services;
    }

    public void mouseClicked(MouseEvent e) {
    }

    public void mouseEntered(MouseEvent e) {
    }

    public void mouseExited(MouseEvent e) {
    }

    public void mousePressed(MouseEvent e) {
    }

    public void mouseReleased(MouseEvent e) {
    }

}