package jd.gui.swing.jdgui.views.settings.panels.proxy;

import java.awt.event.ActionEvent;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.appwork.storage.config.JsonConfig;
import org.appwork.utils.StringUtils;
import org.appwork.utils.event.queue.QueueAction;
import org.appwork.utils.net.httpconnection.HTTPProxy;
import org.appwork.utils.net.httpconnection.HTTPProxy.TYPE;
import org.appwork.utils.net.socketconnection.SocketConnection;
import org.appwork.utils.swing.EDTRunner;
import org.appwork.utils.swing.dialog.MessageDialogImpl;
import org.jdownloader.actions.AppAction;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.updatev2.InternetConnectionSettings;

import com.btr.proxy.search.ProxySearchStrategy;
import com.btr.proxy.search.browser.firefox.FirefoxProxySearchStrategy;
import com.btr.proxy.search.desktop.DesktopProxySearchStrategy;
import com.btr.proxy.search.env.EnvProxySearchStrategy;
import com.btr.proxy.search.java.JavaProxySearchStrategy;
import com.btr.proxy.selector.pac.PacProxySelector;
import com.btr.proxy.selector.pac.PacScriptParser;
import com.btr.proxy.selector.pac.PacScriptSource;
import com.btr.proxy.selector.pac.UrlPacScriptSource;
import com.btr.proxy.selector.whitelist.ProxyBypassListSelector;

import jd.controlling.TaskQueue;
import jd.controlling.proxy.AbstractProxySelectorImpl;
import jd.controlling.proxy.NoProxySelector;
import jd.controlling.proxy.PacProxySelectorImpl;
import jd.controlling.proxy.ProxyController;
import jd.controlling.proxy.SingleBasicProxySelectorImpl;
import jd.controlling.proxy.SingleDirectGatewaySelector;

public class ProxyAutoAction extends AppAction {

    public ProxyAutoAction() {
        super();
        setName(_GUI.T.ProxyAutoAction_actionPerformed_d_title());
        setIconKey(IconKey.ICON_PLUGIN);
        setEnabled(JsonConfig.create(InternetConnectionSettings.PATH, InternetConnectionSettings.class).isProxyVoleAutodetectionEnabled());
    }

    /**
     *
     */
    private static final long serialVersionUID = -197136045388327528L;

    public void actionPerformed(ActionEvent e) {
        TaskQueue.getQueue().add(new QueueAction<Void, RuntimeException>() {

            @Override
            protected Void run() throws RuntimeException {
                final ArrayList<ProxySearchStrategy> strategies = new ArrayList<ProxySearchStrategy>();
                try {
                    strategies.add(new DesktopProxySearchStrategy());
                    strategies.add(new FirefoxProxySearchStrategy());
                    strategies.add(new EnvProxySearchStrategy());
                    strategies.add(new JavaProxySearchStrategy());
                } catch (final Throwable e) {
                    e.printStackTrace();
                }
                final int pre = ProxyController.getInstance().getList().size();
                for (ProxySearchStrategy s : strategies) {
                    ProxySelector selector;
                    try {
                        selector = s.getProxySelector();
                        if (selector == null) {
                            continue;
                        }
                        if (selector instanceof ProxyBypassListSelector) {
                            Field field = ProxyBypassListSelector.class.getDeclaredField("delegate");
                            field.setAccessible(true);
                            selector = (ProxySelector) field.get(selector);
                        }
                        if (selector instanceof PacProxySelector) {
                            Field field = PacProxySelector.class.getDeclaredField("pacScriptParser");
                            field.setAccessible(true);
                            PacScriptParser source = (PacScriptParser) field.get(selector);
                            PacScriptSource pacSource = source.getScriptSource();
                            if (pacSource != null && pacSource instanceof UrlPacScriptSource) {
                                field = UrlPacScriptSource.class.getDeclaredField("scriptUrl");
                                field.setAccessible(true);
                                Object pacURL = field.get(pacSource);
                                if (StringUtils.isNotEmpty((String) pacURL)) {
                                    setProxy(new PacProxySelectorImpl((String) pacURL, null, null));
                                }
                            }
                        } else {
                            List<Proxy> proxies = selector.select(new URI("http://google.com"));
                            if (proxies != null) {
                                for (Proxy p : proxies) {
                                    HTTPProxy httpProxy = null;
                                    switch (p.type()) {
                                    case DIRECT:
                                        if (p.address() == null) {
                                            setProxy(new NoProxySelector());
                                        } else {
                                            httpProxy = new HTTPProxy(((InetSocketAddress) p.address()).getAddress());
                                            setProxy(new SingleDirectGatewaySelector(httpProxy));
                                        }
                                        break;
                                    case HTTP:
                                        if (p.address() != null) {
                                            httpProxy = new HTTPProxy(TYPE.HTTP, SocketConnection.getHostName(p.address()), ((InetSocketAddress) p.address()).getPort());
                                            setProxy(new SingleBasicProxySelectorImpl(httpProxy));
                                        }
                                        break;
                                    case SOCKS:
                                        if (p.address() != null) {
                                            httpProxy = new HTTPProxy(TYPE.SOCKS5, SocketConnection.getHostName(p.address()), ((InetSocketAddress) p.address()).getPort());
                                            setProxy(new SingleBasicProxySelectorImpl(httpProxy));
                                        }
                                        break;
                                    }
                                }
                            }
                        }
                        System.out.println(selector);
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }
                final int diff = ProxyController.getInstance().getList().size() - pre;
                if (diff >= 0) {
                    new EDTRunner() {
                        @Override
                        protected void runInEDT() {
                            new MessageDialogImpl(0, _GUI.T.ProxyAutoAction_run_added_proxies_(diff)).show();
                        }
                    };
                }
                return null;
            }
        });

    }

    protected void setProxy(final AbstractProxySelectorImpl proxy) {
        proxy.setEnabled(true);
        ProxyController.getInstance().setProxy(proxy);
    }

}
