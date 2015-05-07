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

package jd.plugins.hoster;

import java.io.IOException;

import jd.PluginWrapper;
import jd.config.Property;
import jd.http.Browser;
import jd.http.RandomUserAgent;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.parser.html.Form;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.utils.locale.JDL;

import org.appwork.utils.formatter.SizeFormatter;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "rusfolder.com", "rusfolder.ru", "ifolder.ru" }, urls = { "http://([a-z0-9\\.\\-]*?\\.)?((daoifolder|yapapka|rusfolder|ifolder)\\.(net|ru|com)|files\\.metalarea\\.org)/(files/)?\\d+", "IFOLDERISNOWRUSFOLDER", "IFOLDERISNOWRUSFOLDER" }, flags = { 0, 0, 0 })
public class IfolderRu extends PluginForHost {

    private String       ua          = RandomUserAgent.generate();

    private final String passWarning = ">Владелец файла установил пароль для скачивания\\.<";
    private final String PWTEXT      = "Введите пароль:<br";

    private final String CAPTEXT     = "/random/images/";

    /**
     * sets primary domain to be used throughout JDownloader!
     *
     * @author raztoki
     */
    private final String primaryHost = "rusfolder.com";

    public IfolderRu(PluginWrapper wrapper) {
        super(wrapper);
    }

    public void correctDownloadLink(DownloadLink link) {
        String url = new Regex(link.getDownloadURL(), "https?://.*?(/.+)").getMatch(0);
        if (url != null) {
            url = url.replaceAll("/files/", "/");
        }
        link.setUrlDownload("http://" + primaryHost + url);
    }

    @Override
    public String getAGBLink() {
        return ("http://" + primaryHost + "/agreement");
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return 10;
    }

    @Override
    public String rewriteHost(String host) {
        if (!"rusfolder.com".equals(getHost())) {
            if (host == null || "rusfolder.ru".equals(host) || "ifolder.ru".equals(host)) {
                return "rusfolder.com";
            }
        }
        return super.rewriteHost(host);
    }

    @Override
    public void handleFree(DownloadLink downloadLink) throws Exception {
        requestFileInformation(downloadLink);
        String dllink = checkDirectLink(downloadLink, "directlink");
        if (dllink == null) {
            /* too many traffic but can we download download with ad? */
            final boolean withad = br.containsHTML(">Вы можете получить этот файл, если посетите сайт наших рекламодателей");
            if (br.containsHTML("На данный момент иностранный трафик у этого файла превышает российский") && !withad) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "At the moment foreign traffic of this file is larger than Russia's");
            }
            final String raidlink = br.getRegex("src: \\'(http://[a-z0-9]+\\.rusfolder\\.com/download_raid/\\d+\\?check=[^<>\"]*?)\\'").getMatch(0);
            if (raidlink != null) {
                br.cloneBrowser().getPage(raidlink);
            }
            br.setFollowRedirects(true);
            br.setDebug(true);
            String passCode = null;
            // prevents captcha if user doesn't set dl password here....
            if (br.containsHTML(passWarning)) {
                passCode = downloadLink.getStringProperty("pass", null);
                if ("".equals(passCode) || passCode == null) {
                    passCode = getUserInput(null, downloadLink);
                }
                // can not have a blank password, no point doing captcha when password isn't possible.
                if ("".equals(passCode) || passCode == null) {
                    throw new PluginException(LinkStatus.ERROR_FATAL, "Password required to download this file");
                }
            }
            String domain = null;
            String watchAd = null;
            domain = br.getRegex("(https?://ints\\..*?\\.[a-z]{2,3})/ints/").getMatch(0);
            watchAd = br.getRegex("http://ints\\..*?\\.[a-z]{2,3}/ints/\\?([^<>\"\\']+)(\"|\\')").getMatch(0);
            if (domain == null || watchAd == null) {
                /* TODO: Check if this step is really always needed! */
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            watchAd = domain + "/ints/?".concat(watchAd).replace("';", "");
            downloadLink.getLinkStatus().setStatusText(JDL.L("plugins.hoster.ifolderru.errors.ticketwait", "Waiting for ticket"));
            br.getPage(watchAd);
            final String watchAdComplete = br.getRegex("location\\.href = \\'(http[^<>\"]*?)\\';").getMatch(0);
            if (watchAdComplete != null) {
                final String adfly = new Regex(watchAdComplete, "(http://adf\\.ly/\\d+/?)").getMatch(0);
                watchAd = new Regex(watchAdComplete, "(http://ints\\.rusfolder\\.com/.+)").getMatch(0);
                if (adfly == null || watchAd == null) {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                br.getHeaders().put("Referer", watchAdComplete);
            } else {
                watchAd = br.getRegex("href=(http://ints\\.rusfolder.[a-z0-9]+/ints/[^<>\"]*?)>").getMatch(0);
            }
            if (watchAd != null) {
                br.getPage(watchAd);
                watchAd = br.getRegex("\"f_top\" src=\"(.*?)\"").getMatch(0);
                // If they take the waittime out this part is optional
                if (watchAd != null) {
                    logger.info("Third watchad available --> Waittime active");
                    br.getPage(watchAd);
                    /* Tickettime */
                    String ticketTimeS = br.getRegex("delay = (\\d+)").getMatch(0);
                    if (ticketTimeS == null) {
                        logger.warning("ticketTimeS equals null");
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                    int ticketTime = Integer.parseInt(ticketTimeS) * 1000;
                    this.sleep(ticketTime + 1, downloadLink);
                    /* this response comes without valid http header */
                    br.getPage(watchAd);
                }
            } else {
                logger.warning("second watchad equals null");
            }
            if (!br.containsHTML(CAPTEXT)) {
                logger.warning("Browser doesn't contain the captcha-text");
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            for (int retry = 1; retry <= 5; retry++) {
                Form captchaForm = br.getFormbyProperty("name", "form1");
                String captchaurl = "/random/images/?session=";
                String ints_session = br.getRegex("tag\\.value = \"(.*?)\"").getMatch(0);
                if (captchaForm == null || captchaurl == null) {
                    logger.warning("captchaForm or captchaurl or ints_session equals null, stopping...");
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                captchaurl += captchaForm.getInputField("session").getValue();
                captchaForm.put("ints_session", ints_session);
                captchaForm.remove("adverigo_captcha");
                captchaForm.setAction(this.br.getURL());
                /* Captcha */
                // try {
                // // jd2
                // org.jdownloader.captcha.v2.solver.jac.JACSolver.getInstance().setMethodTrustThreshold(this, "ifolder.ru", 70);
                // } catch (Throwable e) {
                //
                // }
                String captchaCode = getCaptchaCode("ifolder.ru", captchaurl, downloadLink);
                captchaForm.put("confirmed_number", captchaCode);
                /* this hoster checks content encoding */
                captchaForm.setEncoding("application/x-www-form-urlencoded");
                String specialParam = br.getRegex("var s=[\t\n\r ]*?\\'([^<>\"]*?)\\';").getMatch(0);
                final String paramsubstring = br.getRegex("s\\.substring\\((\\d+)\\)").getMatch(0);
                if (paramsubstring != null) {
                    specialParam = specialParam.substring(Integer.parseInt(paramsubstring));
                }
                String specialValue = br.getRegex("s\\.substring\\(\\d+\\)\\+\"\\' value=\\'([a-z0-9]+)\\\'>").getMatch(0);
                if (specialValue == null) {
                    specialValue = "1";
                }
                if (specialParam != null) {
                    captchaForm.put(specialParam, specialValue);
                } else {
                    logger.info("Specialstuff is null, this could cause trouble...");
                }
                captchaForm.put("adverigo-input", "");
                try {
                    br.submitForm(captchaForm);
                } catch (Exception e) {
                    e.printStackTrace();
                    br.submitForm(captchaForm);
                }
                if (!br.containsHTML(CAPTEXT)) {
                    break;
                }
            }
            if (br.containsHTML(CAPTEXT)) {
                throw new PluginException(LinkStatus.ERROR_CAPTCHA);
            }
            /* Is the file password protected ? */
            if (br.containsHTML(PWTEXT)) {
                final int repeat = 3;
                for (int passwordRetry = 0; passwordRetry <= repeat; passwordRetry++) {
                    logger.info("This file is password protected");
                    final String session = br.getRegex("name=\"session\" value=\"(.*?)\"").getMatch(0);
                    final String fileID = getFUID(downloadLink);
                    if (session == null || fileID == null) {
                        logger.warning("The string 'session' or 'fileID' equals null, throwing exception...");
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                    String postData = "session=" + session + "&file_id=" + fileID + "&action=1&pswd=" + Encoding.urlEncode(passCode);
                    br.postPage(br.getURL(), postData);
                    if (!br.containsHTML(PWTEXT)) {
                        break;
                    } else if (passwordRetry + 1 != repeat) {
                        logger.info("DownloadPW wrong!");
                        passCode = getUserInput("Wrong Password, Please enter in another!", downloadLink);
                        // can not have a blank password, no point doing captcha when password isn't possible.
                        if ("".equals(passCode) || passCode == null) {
                            throw new PluginException(LinkStatus.ERROR_FATAL, "Password required to download this file");
                        }
                        continue;
                    } else {
                        downloadLink.setProperty("pass", Property.NULL);
                        logger.warning("DownloadPW wrong!");
                        throw new PluginException(LinkStatus.ERROR_RETRY);
                    }
                }
                // set working password
                downloadLink.setProperty("pass", passCode);
            }
            dllink = br.getRegex("id=\"download_file_href\".*?href=\"(.*?)\"").getMatch(0);
            if (dllink == null) {
                logger.warning("directLink equals null");
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            if (passCode != null) {
                downloadLink.setProperty("pass", passCode);
            }
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, true, 0);
        if (dl.getConnection().getContentType().contains("html")) {
            if (dl.getConnection().getResponseCode() == 404) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 404", 30 * 60 * 1000l);
            }
            br.followConnection();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        downloadLink.setProperty("directlink", dllink);
        dl.startDownload();
    }

    private String getFUID(DownloadLink downloadLink) {
        final String fuid = new Regex(downloadLink.getDownloadURL(), "/(\\d+)$").getMatch(0);
        return fuid;
    }

    private String checkDirectLink(final DownloadLink downloadLink, final String property) {
        String dllink = downloadLink.getStringProperty(property);
        if (dllink != null) {
            try {
                final Browser br2 = br.cloneBrowser();
                br2.setFollowRedirects(true);
                URLConnectionAdapter con = br2.openGetConnection(dllink);
                if (con.getContentType().contains("html") || con.getLongContentLength() == -1) {
                    downloadLink.setProperty(property, Property.NULL);
                    dllink = null;
                }
                con.disconnect();
            } catch (final Exception e) {
                downloadLink.setProperty(property, Property.NULL);
                dllink = null;
            }
        }
        return dllink;
    }

    private void prepareBrowser(Browser br) {
        if (br == null) {
            return;
        }
        br.getHeaders().put("User-Agent", ua);
        br.getHeaders().put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        br.getHeaders().put("Accept-Language", "en-us,de;q=0.7,en;q=0.3");
        br.setReadTimeout(3 * 60 * 1000);
    }

    @Override
    public AvailableStatus requestFileInformation(DownloadLink downloadLink) throws PluginException, IOException, InterruptedException {
        this.setBrowserExclusive();
        prepareBrowser(br);
        br.getPage(downloadLink.getDownloadURL());
        if (br.getRedirectLocation() != null) {
            String fileID = new Regex(downloadLink.getDownloadURL(), "https?://.*?/(.+)").getMatch(0);
            if (!br.getRedirectLocation().contains(fileID)) {
                logger.warning("The redirect location doesn't contain the fileID, stopping...");
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            logger.info("Setting new downloadUrl...");
            downloadLink.setUrlDownload(br.getRedirectLocation());
            br.getPage(downloadLink.getDownloadURL());
        }
        if (br.containsHTML("<p>Файл номер <b>\\d+</b> удален !!!</p>") || br.containsHTML("<p>Файл номер <b>\\d+</b> не найден !!!</p>")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }

        String filename = br.getRegex("Название:.*?<b>(.*?)</b>").getMatch(0);
        String filesize = br.getRegex("Размер:.*?<b>(.*?)</b>").getMatch(0);
        if (filename == null || filesize == null) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        if (filename.contains("..")) {
            /* because of server problems check for final filename here */
            downloadLink.setName(filename);
        } else {
            downloadLink.setFinalFileName(filename);
        }
        downloadLink.setDownloadSize(SizeFormatter.getSize(filesize.replace("Мб", "Mb").replace("кб", "Kb").replace("Гб", "Gb")));
        return AvailableStatus.TRUE;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

    /* NO OVERRIDE!! We need to stay 0.9*compatible */
    public boolean hasCaptcha(DownloadLink link, jd.plugins.Account acc) {
        return true;
    }
}