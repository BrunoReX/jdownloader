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

import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map.Entry;

import jd.PluginWrapper;
import jd.config.ConfigContainer;
import jd.config.ConfigEntry;
import jd.config.Property;
import jd.config.SubConfiguration;
import jd.http.Browser;
import jd.http.Cookie;
import jd.http.Cookies;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.utils.locale.JDL;

import org.jdownloader.controlling.ffmpeg.json.Stream;
import org.jdownloader.controlling.ffmpeg.json.StreamInfo;
import org.jdownloader.downloader.hls.HLSDownloader;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "twitch.tv" }, urls = { "http://twitchdecrypted\\.tv/\\d+" }, flags = { 2 })
public class JustinTv extends PluginForHost {

    public JustinTv(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("https://secure.twitch.tv/products/turbo_year/ticket");
        setConfigElements();
    }

    @Override
    public String getAGBLink() {
        return "http://www.twitch.tv/user/legal?page=terms_of_service";
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    private final String        FASTLINKCHECK             = "FASTLINKCHECK";
    private final String        NOCHUNKS                  = "NOCHUNKS";
    private final static String CUSTOM_DATE_2             = "CUSTOM_DATE_2";
    private final static String CUSTOM_FILENAME_3         = "CUSTOM_FILENAME_3";
    private final static String CUSTOM_FILENAME_4         = "CUSTOM_FILENAME_4";
    private final static String PARTNUMBERFORMAT          = "PARTNUMBERFORMAT";

    private static final int    ACCOUNT_FREE_MAXDOWNLOADS = 20;

    private String              dllink                    = null;

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink downloadLink) throws Exception {
        dllink = downloadLink.getStringProperty("plain_directlink", downloadLink.getStringProperty("m3u", null));
        if (downloadLink.getBooleanProperty("offline", false) || dllink == null) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        if (dllink.endsWith("m3u8")) {
            // whilst requestFileInformation isn't threaded, I'm calling it directly from decrypter as a setting method. We now want to
            // prevent more than one thread running, incase of issues from hoster
            synchronized (ctrlLock) {
                checkFFProbe(downloadLink, "File Checking a HLS Stream");
                if (downloadLink.getBooleanProperty("encrypted")) {
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Encrypted HLS is not supported");
                }

                br.getHeaders().put("Accept", "*/*");
                br.getHeaders().put("X-Requested-With", "ShockwaveFlash/18.0.0.194");
                br.getHeaders().put("Referer", downloadLink.getContentUrl());
                HLSDownloader downloader = new HLSDownloader(downloadLink, br, downloadLink.getStringProperty("m3u", null));
                StreamInfo streamInfo = downloader.getProbe();
                if (streamInfo == null) {
                    return AvailableStatus.FALSE;
                }

                String extension = ".m4a";

                for (Stream s : streamInfo.getStreams()) {
                    if ("video".equalsIgnoreCase(s.getCodec_type())) {
                        extension = ".mp4";
                        if (s.getHeight() > 0) {
                            downloadLink.setProperty("videoQuality", s.getHeight());
                        }
                        if (s.getCodec_name() != null) {
                            downloadLink.setProperty("videoCodec", s.getCodec_name());
                        }
                    } else if ("audio".equalsIgnoreCase(s.getCodec_type())) {
                        if (s.getBit_rate() != null) {
                            downloadLink.setProperty("audioBitrate", Integer.parseInt(s.getBit_rate()) / 1024);
                        }
                        if (s.getCodec_name() != null) {
                            downloadLink.setProperty("audioCodec", s.getCodec_name());
                        }
                    }
                }
                downloadLink.setProperty("extension", extension);

                downloadLink.setName(getFormattedFilename(downloadLink));
                return AvailableStatus.TRUE;
            }
        } else {
            this.setBrowserExclusive();
            URLConnectionAdapter con = null;
            final Browser br2 = br.cloneBrowser();
            // In case the link redirects to the finallink
            br2.setFollowRedirects(true);
            try {
                con = br2.openGetConnection(dllink);
                if (!con.getContentType().contains("html")) {
                    downloadLink.setDownloadSize(con.getLongContentLength());
                } else {
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                }
            } finally {
                try {
                    con.disconnect();
                } catch (Throwable e) {
                }
            }
            final String formattedFilename = getFormattedFilename(downloadLink);
            downloadLink.setFinalFileName(formattedFilename);
            return AvailableStatus.TRUE;
        }
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception {
        requestFileInformation(downloadLink);
        if (dllink != null && dllink.endsWith("m3u8")) {
            doHLS(downloadLink);
        } else {
            doFree(downloadLink);
        }
    }

    private final void doHLS(final DownloadLink downloadLink) throws Exception {
        checkFFmpeg(downloadLink, "Download a HLS Stream");
        if (downloadLink.getBooleanProperty("encrypted")) {

            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Encrypted HLS is not supported");
        }
        // requestFileInformation(downloadLink);
        dl = new HLSDownloader(downloadLink, br, dllink);
        dl.startDownload();
    }

    private void doFree(final DownloadLink downloadLink) throws Exception {
        int maxChunks = 0;
        if (downloadLink.getBooleanProperty(NOCHUNKS, false)) {
            maxChunks = 1;
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, true, maxChunks);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            if (br.containsHTML(">416 Requested Range Not Satisfiable<")) {
                /* unknown error, we disable multiple chunks */
                if (downloadLink.getBooleanProperty(NOCHUNKS, false) == false) {
                    downloadLink.setProperty(NOCHUNKS, Boolean.valueOf(true));
                    throw new PluginException(LinkStatus.ERROR_RETRY);
                }
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error", 5 * 60 * 1000l);
            }
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if (!this.dl.startDownload()) {
            try {
                if (dl.externalDownloadStop()) {
                    return;
                }
            } catch (final Throwable e) {
            }
            /* unknown error, we disable multiple chunks */
            if (downloadLink.getBooleanProperty(NOCHUNKS, false) == false) {
                downloadLink.setProperty(NOCHUNKS, Boolean.valueOf(true));
                throw new PluginException(LinkStatus.ERROR_RETRY);
            }
        }
    }

    @SuppressWarnings("deprecation")
    public static String getFormattedFilename(final DownloadLink downloadLink) throws ParseException {
        String videoName = downloadLink.getStringProperty("plainfilename", null);

        final SubConfiguration cfg = SubConfiguration.getConfig("twitch.tv");
        String formattedFilename = downloadLink.getStringProperty("m3u", null) != null ? cfg.getStringProperty(CUSTOM_FILENAME_4, defaultCustomFilenameHls) : cfg.getStringProperty(CUSTOM_FILENAME_3, defaultCustomFilenameWeb);
        if (formattedFilename == null || formattedFilename.equals("")) {
            formattedFilename = downloadLink.getStringProperty("m3u", null) != null ? defaultCustomFilenameHls : defaultCustomFilenameWeb;
        }
        if (!formattedFilename.contains("*videoname") || !formattedFilename.contains("*ext*")) {
            formattedFilename = downloadLink.getStringProperty("m3u", null) != null ? defaultCustomFilenameHls : defaultCustomFilenameWeb;
        }
        String partnumberformat = cfg.getStringProperty(PARTNUMBERFORMAT);
        if (partnumberformat == null || partnumberformat.equals("")) {
            partnumberformat = "00";
        }

        final DecimalFormat df = new DecimalFormat(partnumberformat);
        final String date = downloadLink.getStringProperty("originaldate", null);
        final String channelName = downloadLink.getStringProperty("channel", null);
        final int partNumber = downloadLink.getIntegerProperty("partnumber", -1);
        final String quality = downloadLink.getStringProperty("quality", "");
        final int videoQuality = downloadLink.getIntegerProperty("videoQuality", -1);
        final String videoCodec = downloadLink.getStringProperty("videoCodec", "");
        final int audioBitrate = downloadLink.getIntegerProperty("audioBitrate", -1);
        final String audioCodec = downloadLink.getStringProperty("audioCodec", "");
        final String extension = downloadLink.getStringProperty("extension", ".flv");

        String formattedDate = null;
        if (date != null) {
            final String userDefinedDateFormat = cfg.getStringProperty(CUSTOM_DATE_2, "dd.MM.yyyy_HH-mm-ss");
            final String[] dateStuff = date.split("T");
            final String input = dateStuff[0] + ":" + dateStuff[1].replace("Z", "GMT");
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd:HH:mm:ssZ");
            Date dateStr = formatter.parse(input);
            formattedDate = formatter.format(dateStr);
            Date theDate = formatter.parse(formattedDate);

            formatter = new SimpleDateFormat(userDefinedDateFormat);
            formattedDate = formatter.format(theDate);
        }
        // parse for user characters outside of wild card, only used as separators.
        final String p = new Regex(formattedFilename, "\\*?(([^\\*]*)?\\*partnumber\\*([^\\*]*)?)").getMatch(0);
        if (partNumber == -1) {
            if (p != null) {
                formattedFilename = formattedFilename.replace(p, "");
            } else {
                formattedFilename = formattedFilename.replace("*partnumber*", "");
            }
        } else {
            formattedFilename = formattedFilename.replace("*partnumber*", df.format(partNumber));
        }

        formattedFilename = formattedFilename.replace("*quality*", quality);
        formattedFilename = formattedFilename.replace("*channelname*", channelName);
        formattedFilename = formattedFilename.replace("*videoQuality*", videoQuality == -1 ? "" : videoQuality + "p");
        formattedFilename = formattedFilename.replace("*videoCodec*", videoCodec);
        formattedFilename = formattedFilename.replace("*audioBitrate*", audioBitrate == -1 ? "" : audioBitrate + "kbits");
        formattedFilename = formattedFilename.replace("*audioCodec*", audioCodec);
        if (formattedDate != null) {
            formattedFilename = formattedFilename.replace("*date*", formattedDate);
        } else {
            formattedFilename = formattedFilename.replace("*date*", "");
        }
        formattedFilename = formattedFilename.replace("*ext*", extension);
        // Insert filename at the end to prevent errors with tags
        formattedFilename = formattedFilename.replace("*videoname*", videoName);

        return formattedFilename;
    }

    /* NO OVERRIDE!! We need to stay 0.9*compatible */
    public boolean allowHandle(final DownloadLink downloadLink, final PluginForHost plugin) {
        return downloadLink.getHost().equalsIgnoreCase(plugin.getHost());
    }

    private static final String MAINPAGE    = "http://twitch.tv";
    private static Object       accountLock = new Object();
    private static Object       ctrlLock    = new Object();

    @SuppressWarnings("unchecked")
    public static void login(final Browser br, final Account account, final boolean force) throws Exception {
        synchronized (accountLock) {
            try {
                // Load cookies
                br.setCookiesExclusive(true);
                final Object ret = account.getProperty("cookies", null);
                boolean acmatch = Encoding.urlEncode(account.getUser()).equals(account.getStringProperty("name", Encoding.urlEncode(account.getUser())));
                if (acmatch) {
                    acmatch = Encoding.urlEncode(account.getPass()).equals(account.getStringProperty("pass", Encoding.urlEncode(account.getPass())));
                }
                if (acmatch && ret != null && ret instanceof HashMap<?, ?> && !force) {
                    final HashMap<String, String> cookies = (HashMap<String, String>) ret;
                    if (account.isValid()) {
                        for (final Entry<String, String> cookieEntry : cookies.entrySet()) {
                            final String key = cookieEntry.getKey();
                            final String value = cookieEntry.getValue();
                            br.setCookie(MAINPAGE, key, value);
                        }
                        return;
                    }
                }
                br.setFollowRedirects(true);
                br.getPage("http://www.twitch.tv/user/login_popup?follow=");
                final String auth_token = br.getRegex("name=\"authenticity_token\" type=\"hidden\" value=\"([^<>\"]*?)\"").getMatch(0);
                if (auth_token == null) {
                    if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nPlugin defekt, bitte den JDownloader Support kontaktieren!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nPlugin broken, please contact the JDownloader Support!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                br.postPage("https://secure.twitch.tv/user/login", "utf8=%E2%9C%93&authenticity_token=" + Encoding.urlEncode(auth_token) + "&redirect_on_login=&embed_form=false&mp_source_action=login-button&follow=&login=" + Encoding.urlEncode(account.getUser()) + "&password=" + Encoding.urlEncode(account.getPass()));
                if (br.getCookie(MAINPAGE, "persistent") == null) {
                    if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername oder ungültiges Passwort!\r\nSchnellhilfe: \r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen?\r\nFalls dein Passwort Sonderzeichen enthält, ändere es und versuche es erneut!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nQuick help:\r\nYou're sure that the username and password you entered are correct?\r\nIf your password contains special characters, change it (remove them) and try again!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                // Save cookies
                final HashMap<String, String> cookies = new HashMap<String, String>();
                final Cookies add = br.getCookies(MAINPAGE);
                for (final Cookie c : add.getCookies()) {
                    cookies.put(c.getKey(), c.getValue());
                }
                account.setProperty("name", Encoding.urlEncode(account.getUser()));
                account.setProperty("pass", Encoding.urlEncode(account.getPass()));
                account.setProperty("cookies", cookies);
            } catch (final PluginException e) {
                account.setProperty("cookies", Property.NULL);
                throw e;
            }
        }
    }

    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        final AccountInfo ai = new AccountInfo();
        try {
            login(this.br, account, true);
        } catch (PluginException e) {
            account.setValid(false);
            throw e;
        }
        ai.setUnlimitedTraffic();
        try {
            account.setType(AccountType.FREE);
            account.setMaxSimultanDownloads(ACCOUNT_FREE_MAXDOWNLOADS);
        } catch (final Throwable e) {
            /* not available in old Stable 0.9.581 */
        }
        ai.setStatus("Free Account");
        account.setValid(true);
        return ai;
    }

    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception {
        requestFileInformation(link);
        if (dllink != null && dllink.endsWith("m3u8")) {
            doHLS(link);
        } else {
            doFree(link);
        }
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        return ACCOUNT_FREE_MAXDOWNLOADS;
    }

    @Override
    public String getDescription() {
        return "JDownloader's twitch.tv plugin helps downloading videoclips. JDownloader provides settings for the filenames.";
    }

    private final static String defaultCustomFilenameWeb = "*partnumber**videoname*_*quality**ext*";
    private final static String defaultCustomFilenameHls = "*partnumber* - *videoname* -*videoQuality*_*videoCodec*-*audioBitrate*_*audioCodec**ext*";

    private void setConfigElements() {
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), FASTLINKCHECK, JDL.L("plugins.hoster.justintv.fastlinkcheck", "Activate fast linkcheck (filesize won't be shown in linkgrabber)?")).setDefaultValue(false));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_LABEL, "Customize the filename properties"));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_TEXTFIELD, getPluginConfig(), CUSTOM_DATE_2, JDL.L("plugins.hoster.justintv.customdate", "Define how the date should look:")).setDefaultValue("dd.MM.yyyy_hh-mm-ss"));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_TEXTFIELD, getPluginConfig(), PARTNUMBERFORMAT, JDL.L("plugins.hoster.justintv.custompartnumber", "Define how the partnumbers should look:")).setDefaultValue("00"));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_TEXTFIELD, getPluginConfig(), CUSTOM_FILENAME_3, JDL.L("plugins.hoster.justintv.customfilename1", "Define how standard filenames should look:")).setDefaultValue(defaultCustomFilenameWeb));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_TEXTFIELD, getPluginConfig(), CUSTOM_FILENAME_4, JDL.L("plugins.hoster.justintv.customfilename2", "Define how vod /v/ filenames should look:")).setDefaultValue(defaultCustomFilenameHls));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        final StringBuilder sb = new StringBuilder();
        sb.append("Explanation of the available tags: (shared)\r\n");
        sb.append("*channelname* = name of the channel/uploader\r\n");
        sb.append("*date* = date when the video was posted - appears in the user-defined format above\r\n");
        sb.append("*videoname* = name of the video without extension\r\n");
        sb.append("*partnumber* = number of the part of the video - if there is only 1 part, it's 1\r\n");
        sb.append("*ext* = the extension of the file, in this case usually '.flv'\r\n");
        sb.append("\r\nThis tag is only used for standard downloads\r\n");
        sb.append("*quality* = the quality of the file, e.g. '720p'. (used for older formats, not present new /v/ videos)\r\n");
        sb.append("\r\nThese following tags are only used for HLS /v/ urls\r\n");
        sb.append("*videoQuality* = the frame size/quality, e.g. '720p'\r\n");
        sb.append("*videoCodec* = video codec used, e.g. 'h264'\r\n");
        sb.append("*audioBitrate* = audio bitrate, e.g. '128kbits'\r\n");
        sb.append("*audioCodec* = audio encoding type, e.g. 'aac'");
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_LABEL, sb.toString()));
        // best shite for hls
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_LABEL, "Quality selection, this is for HLS /v/ links only"));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), "q1080p", JDL.L("plugins.hoster.justintv.check1080p", "Grab 1080?")).setDefaultValue(true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), "q720p", JDL.L("plugins.hoster.justintv.check720p", "Grab 720p?")).setDefaultValue(true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), "q480p", JDL.L("plugins.hoster.justintv.check480p", "Grab 480p?")).setDefaultValue(true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), "q360p", JDL.L("plugins.hoster.justintv.check360p", "Grab 360p?")).setDefaultValue(true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), "q240p", JDL.L("plugins.hoster.justintv.check240p", "Grab 240p?")).setDefaultValue(true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), "useBest", JDL.L("plugins.hoster.justintv.usebest", "Only grab Best video within selection above?, Else will return available videos within your selected above")).setDefaultValue(true));

    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

    @Override
    public void resetPluginGlobals() {
    }
}