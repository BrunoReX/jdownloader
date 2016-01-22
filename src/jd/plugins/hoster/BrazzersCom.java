//jDownloader - Downloadmanager
//Copyright (C) 2009  JD-Team support@jdownloader.org
//
//This program is free software: you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation, either version 3 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program.  If not, see <http://www.gnu.org/licenses/>.

package jd.plugins.hoster;

import java.io.IOException;

import jd.PluginWrapper;
import jd.controlling.AccountController;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.Account;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;

import org.appwork.utils.formatter.SizeFormatter;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "brazzers.com" }, urls = { "https?://(?:www\\.)?brazzers\\.com/scenes/view/id/\\d+(?:/[a-z0-9\\-]+/)?" }, flags = { 0 })
public class BrazzersCom extends antiDDoSForHost {

    public BrazzersCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public String getAGBLink() {
        return "http://brazzerssupport.com/terms-of-service/";
    }

    /* Connection stuff */
    private static final boolean FREE_RESUME       = true;
    private static final int     FREE_MAXCHUNKS    = 0;
    private static final int     FREE_MAXDOWNLOADS = 20;

    // private static final boolean ACCOUNT_FREE_RESUME = true;
    // private static final int ACCOUNT_FREE_MAXCHUNKS = 0;
    // private static final int ACCOUNT_FREE_MAXDOWNLOADS = 20;
    // private static final boolean ACCOUNT_PREMIUM_RESUME = true;
    // private static final int ACCOUNT_PREMIUM_MAXCHUNKS = 0;
    // private static final int ACCOUNT_PREMIUM_MAXDOWNLOADS = 20;
    //
    // /* don't touch the following! */
    // private static AtomicInteger maxPrem = new AtomicInteger(1);

    /**
     * So far this plugin has no account support which means the plugin itself cannot download anything but the download via MOCH will work
     * fine.
     */
    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws IOException, PluginException {
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        br.getPage(link.getDownloadURL());
        /* Offline will usually return 404 */
        if (br.getHttpConnection().getResponseCode() == 404) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        final String fid = new Regex(link.getDownloadURL(), "/id/(\\d+)").getMatch(0);
        final String url_name = new Regex(link.getDownloadURL(), "/id/\\d+/(.+)/$").getMatch(0);
        String filename = br.getRegex("<h1 itemprop=\"name\">([^<>\"]+)<span").getMatch(0);
        /* Two fallbacks in case we do not get any filename via html code */
        if (inValidate(filename)) {
            filename = url_name;
        }
        if (inValidate(filename)) {
            filename = fid;
        }
        final Account aa = AccountController.getInstance().getValidAccount(this);
        final boolean moch_download_possible = AccountController.getInstance().hasMultiHostAccounts(this.getHost());
        long filesize_max = -1;
        long filesize_temp = -1;
        final String[] filesizes = br.getRegex("\\[(\\d{1,5}(?:\\.\\d{1,2})? (?:GiB|MB))\\]").getColumn(0);
        for (final String filesize_temp_str : filesizes) {
            filesize_temp = SizeFormatter.getSize(filesize_temp_str);
            if (filesize_temp > filesize_max) {
                filesize_max = filesize_temp;
            }
        }
        if (filename == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        filename = Encoding.htmlDecode(filename).trim();
        filename = encodeUnicode(filename);
        /* Do NOT set final filename here yet! */
        link.setName(filename + ".mp4");
        if (filesize_max > -1) {
            if (aa != null) {
                /* Original brazzers account available --> Set highest filesize found --> Best Quality possible */
                link.setDownloadSize(filesize_max);
            } else if (moch_download_possible) {
                /* Multihosters usually return a medium quality - about 1 / 4 the size of the best possible! */
                link.setDownloadSize((long) (filesize_max * 0.25));
            } else {
                link.setDownloadSize(filesize_max);
            }
        }
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        requestFileInformation(downloadLink);
        doFree(downloadLink, FREE_RESUME, FREE_MAXCHUNKS, "free_directlink");
    }

    private void doFree(final DownloadLink downloadLink, final boolean resumable, final int maxchunks, final String directlinkproperty) throws Exception, PluginException {
        /* Premiumonly */
        throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
    }

    @Override
    public boolean canHandle(DownloadLink downloadLink, Account account) {
        return account != null;
    }

    /**
     * Validates string to series of conditions, null, whitespace, or "". This saves effort factor within if/for/while statements
     *
     * @param s
     *            Imported String to match against.
     * @return <b>true</b> on valid rule match. <b>false</b> on invalid rule match.
     * @author raztoki
     */
    protected boolean inValidate(final String s) {
        if (s == null || s.matches("\\s+") || s.equals("")) {
            return true;
        } else {
            return false;
        }
    }

    /** Avoid chars which are not allowed in filenames under certain OS' */
    private static String encodeUnicode(final String input) {
        String output = input;
        output = output.replace(":", ";");
        output = output.replace("|", "¦");
        output = output.replace("<", "[");
        output = output.replace(">", "]");
        output = output.replace("/", "⁄");
        output = output.replace("\\", "∖");
        output = output.replace("*", "#");
        output = output.replace("?", "¿");
        output = output.replace("!", "¡");
        output = output.replace("\"", "'");
        return output;
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return FREE_MAXDOWNLOADS;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

}