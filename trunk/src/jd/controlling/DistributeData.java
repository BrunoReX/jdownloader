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

package jd.controlling;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import jd.controlling.linkcrawler.CrawledLink;
import jd.controlling.linkcrawler.LinkCrawler;
import jd.plugins.DownloadLink;

import org.jdownloader.controlling.PasswordUtils;

/**
 * Diese Klasse läuft in einem Thread und verteilt den Inhalt der Zwischenablage an (unter Umständen auch mehrere) Plugins Die gefundenen
 * Treffer werden ausgeschnitten.
 *
 * @author astaldo
 */
public class DistributeData {

    /**
     * Die zu verteilenden Daten
     */
    private final String data;

    /**
     * Erstellt einen neuen Thread mit dem Text, der verteilt werden soll. Die übergebenen Daten werden durch einen URLDecoder geschickt.
     *
     * @param data
     *            Daten, die verteilt werden sollen
     */
    @Deprecated
    public DistributeData(final String data) {
        this.data = data;
    }

    /* keep for comp. issues in other projects */
    @Deprecated
    public DistributeData setFilterNormalHTTP(final boolean b) {
        return this;
    }

    @Deprecated
    public static boolean hasPluginFor(final String tmp, final boolean filterNormalHTTP) {
        return true;
    }

    @Deprecated
    public ArrayList<DownloadLink> findLinks() {
        final Set<String> pws = PasswordUtils.getPasswords(data);
        final LinkCrawler lc = LinkCrawler.newInstance();
        lc.crawl(data);
        lc.waitForCrawling();
        ArrayList<DownloadLink> ret = new ArrayList<DownloadLink>(lc.getCrawledLinks().size());
        for (final CrawledLink link : lc.getCrawledLinks()) {
            DownloadLink dl = link.getDownloadLink();
            if (dl == null) {
                final String url = link.getURL();
                if (url != null) {
                    dl = new DownloadLink(null, null, null, url, true);
                }
            }
            if (dl != null && pws != null && pws.size() > 0) {
                List<String> oldList = dl.getSourcePluginPasswordList();
                if (oldList != null && oldList.size() > 0) {
                    final ArrayList<String> newList = new ArrayList<String>(oldList);
                    newList.removeAll(pws);
                    newList.addAll(pws);
                    dl.setSourcePluginPasswordList(newList);
                } else {
                    dl.setSourcePluginPasswordList(new ArrayList<String>(pws));
                }
                ret.add(dl);
            }
        }
        return ret;
    }

}