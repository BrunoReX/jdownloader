//    jDownloader - Downloadmanager
//    Copyright (C) 2008  JD-Team support@jdownloader.org
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

package jd.plugins;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import jd.config.Property;
import jd.nutils.NaturalOrderComparator;

import org.appwork.utils.formatter.SizeFormatter;
import org.jdownloader.logging.LogController;
import org.jdownloader.plugins.controller.host.HostPluginController;
import org.jdownloader.plugins.controller.host.LazyHostPlugin;
import org.jdownloader.plugins.controller.host.PluginFinder;

public class AccountInfo extends Property {

    private static final long serialVersionUID       = 1825140346023286206L;

    private long              account_validUntil     = -1;

    private long              account_trafficLeft    = -1;
    private long              account_trafficMax     = -1;

    private long              account_filesNum       = -1;
    private long              account_premiumPoints  = -1;
    private long              account_accountBalance = -1;
    private long              account_usedSpace      = -1;

    private String            account_status;
    private long              account_createTime     = 0;
    /**
     * indicator that host, account has special traffic handling, do not temp disable if traffic =0
     */
    private boolean           specialTraffic         = false;

    public long getCreateTime() {
        return account_createTime;
    }

    public void setSpecialTraffic(final boolean b) {
        specialTraffic = b;
    }

    public boolean isSpecialTraffic() {
        return specialTraffic;
    }

    public void setCreateTime(final long createTime) {
        this.account_createTime = createTime;
    }

    /**
     * Gibt zurück wieviel (in Cent) Geld gerade auf diesem Account ist
     *
     * @return
     */
    public long getAccountBalance() {
        return account_accountBalance;
    }

    /**
     * Gibt zurück wieviele Files auf dem Account hochgeladen sind
     *
     * @return
     */
    public long getFilesNum() {
        return account_filesNum;
    }

    /**
     * Gibt an wieviele PremiumPunkte der Account hat
     *
     * @return
     */
    public long getPremiumPoints() {
        return account_premiumPoints;
    }

    public String getStatus() {
        return account_status;
    }

    /**
     * Gibt an wieviel Traffic noch frei ist (in bytes)
     *
     * @return
     */
    public long getTrafficLeft() {
        return Math.max(0, account_trafficLeft);
    }

    public long getTrafficMax() {
        return Math.max(getTrafficLeft(), account_trafficMax);
    }

    /**
     * Gibt zurück wieviel Platz (bytes) die Oploads auf diesem Account belegen
     *
     * @return
     */
    public long getUsedSpace() {
        return account_usedSpace;
    }

    /**
     * Gibt einen Timestamp zurück zu dem der Account auslaufen wird bzw. ausgelaufen ist.(-1 für Nie)
     *
     * @return
     */
    public long getValidUntil() {
        return account_validUntil;
    }

    /**
     * Gibt zurück ob der Account abgelaufen ist
     *
     * @return
     */
    public boolean isExpired() {
        final long validUntil = getValidUntil();
        if (validUntil < 0) {
            return false;
        }
        if (validUntil == 0) {
            return true;
        }
        return validUntil < System.currentTimeMillis();
    }

    public void setAccountBalance(final long parseInt) {
        this.account_accountBalance = Math.max(0, parseInt);
    }

    public void setAccountBalance(final String string) {
        this.setAccountBalance((long) (Double.parseDouble(string) * 100));
    }

    public void setExpired(final boolean b) {
        if (b) {
            setValidUntil(0);
        } else {
            setValidUntil(-1);
        }
    }

    public void setFilesNum(final long parseInt) {
        this.account_filesNum = Math.max(0, parseInt);
    }

    public void setPremiumPoints(final long parseInt) {
        this.account_premiumPoints = Math.max(0, parseInt);
    }

    public void setPremiumPoints(final String string) {
        this.setPremiumPoints(Integer.parseInt(string.trim()));
    }

    public void setStatus(final String string) {
        this.account_status = string;
    }

    public void setTrafficLeft(long size) {
        this.account_trafficLeft = Math.max(0, size);
    }

    public void setUnlimitedTraffic() {
        account_trafficLeft = -1;
    }

    public boolean isUnlimitedTraffic() {
        return account_trafficLeft == -1;
    }

    public void setTrafficLeft(final String freeTraffic) {
        this.setTrafficLeft(SizeFormatter.getSize(freeTraffic, true, true));
    }

    /**
     * @since JD2
     * @param trafficMax
     */
    public void setTrafficMax(final String trafficMax) {
        this.setTrafficMax(SizeFormatter.getSize(trafficMax, true, true));
    }

    public void setTrafficMax(final long trafficMax) {
        this.account_trafficMax = Math.max(0, trafficMax);
    }

    public void setUsedSpace(final long size) {
        this.account_usedSpace = Math.max(0, size);
    }

    public void setUsedSpace(final String string) {
        this.setUsedSpace(SizeFormatter.getSize(string, true, true));
    }

    /**
     * -1 für Niemals ablaufen
     *
     * @param validUntil
     */
    public void setValidUntil(final long validUntil) {
        this.account_validUntil = validUntil;
    }

    private long validPremiumUntil = 0l;

    public long getValidPremiumUntil() {
        return validPremiumUntil;
    }

    /**
     * @param validPremiumUntil
     * @since JD2
     */
    public void setValidPremiumUntil(long validPremiumUntil) {
        this.validPremiumUntil = validPremiumUntil;
    }

    /**
     * Removes forbidden hosts, adds host corrections, de-dupes, and then sets AccountInfo property 'multiHostSupport'
     *
     * @author raztoki
     * @param multiHostPlugin
     *            TODO
     * @since JD2
     * */

    public void setMultiHostSupport(final PluginForHost multiHostPlugin, final List<String> multiHostSupport) {
        setMultiHostSupport(multiHostPlugin, multiHostSupport, new PluginFinder());
    }

    public void setMultiHostSupport(final PluginForHost multiHostPlugin, final List<String> multiHostSupport, final PluginFinder pluginFinder) {
        if (multiHostSupport != null && multiHostSupport.size() > 0) {
            Collections.sort(multiHostSupport, new NaturalOrderComparator());
            final LinkedHashSet<String> supportedHostsSet = new LinkedHashSet<String>();
            for (String host : multiHostSupport) {
                if (host != null) {
                    host = host.toLowerCase(Locale.ENGLISH);
                    final String assignedHost;
                    if (pluginFinder == null) {
                        assignedHost = host;
                    } else {
                        assignedHost = pluginFinder.assignHost(host);
                    }
                    final LazyHostPlugin lazyPlugin = HostPluginController.getInstance().get(assignedHost);
                    if (lazyPlugin != null && !lazyPlugin.getClassName().endsWith("r.Offline") && !supportedHostsSet.contains(lazyPlugin.getHost())) {
                        try {
                            if (!lazyPlugin.isHasAllowHandle()) {
                                supportedHostsSet.add(lazyPlugin.getHost());
                            } else {
                                final DownloadLink link = new DownloadLink(null, "", lazyPlugin.getHost(), "", false);
                                if (lazyPlugin.getPrototype(null).allowHandle(link, multiHostPlugin)) {
                                    supportedHostsSet.add(lazyPlugin.getHost());
                                }
                            }
                        } catch (final Throwable e) {
                            LogController.CL().log(e);
                        }
                    }
                }
            }
            if (supportedHostsSet.size() > 0) {
                this.setProperty("multiHostSupport", new CopyOnWriteArrayList<String>(supportedHostsSet));
                return;
            }
        }
        this.setProperty("multiHostSupport", Property.NULL);
    }

    public List<String> getMultiHostSupport() {
        final Object ret = getProperty("multiHostSupport", null);
        if (ret != null && ret instanceof List) {
            final List<String> list = (List<String>) ret;
            if (list.size() > 0) {
                return list;
            }
        }
        return null;
    }
}
