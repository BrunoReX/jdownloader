//    jDownloader - Downloadmanager
//    Copyright (C) 2008  JD-Team jdownloader@freenet.de
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

package jd.plugins.host;

import java.io.File;
import java.net.URI;
import java.util.regex.Pattern;

import jd.config.Configuration;
import jd.http.Browser;
import jd.http.Encoding;
import jd.http.GetRequest;
import jd.http.HTTPConnection;
import jd.http.PostRequest;
import jd.parser.Form;
import jd.parser.Regex;
import jd.plugins.Account;
import jd.plugins.DownloadLink;
import jd.plugins.LinkStatus;
import jd.plugins.Plugin;
import jd.plugins.PluginForHost;
import jd.plugins.download.RAFDownload;
import jd.utils.JDLocale;
import jd.utils.JDUtilities;

public class RapidShareDe extends PluginForHost {
    private static final String HOST = "rapidshare.de";

    static private final Pattern patternSupported = Pattern.compile("http://[\\w\\.]*?rapidshare\\.de/files/[\\d]{3,9}/.*", Pattern.CASE_INSENSITIVE);

    private File captchaFile;

    private String code = null;

    private Form form;

    private long waittime = 0;

    //

    public RapidShareDe() {
        super();
        // steps.add(new PluginStep(PluginStep.STEP_WAIT_TIME, null));
        // steps.add(new PluginStep(PluginStep.STEP_PENDING, null));
        // steps.add(new PluginStep(PluginStep.STEP_GET_CAPTCHA_FILE, null));

        // steps.add(new PluginStep(PluginStep.STEP_DOWNLOAD, null));

        setConfigElements();
        this.enablePremium();
    }

    @Override
    public boolean doBotCheck(File file) {
        return false;
    } // kein BotCheck

    @Override
    public void handleFree(DownloadLink downloadLink) throws Exception {
        LinkStatus linkStatus = downloadLink.getLinkStatus();
        // switch (step.getStep()) {
        // case PluginStep.STEP_WAIT_TIME:
        Browser.clearCookies(HOST);
br.setFollowRedirects(false);
        Form[] forms = br.getForms(downloadLink.getDownloadURL());
        if (forms.length < 2) {
            // step.setStatus(PluginStep.STATUS_ERROR);
            logger.severe("konnte den Download nicht finden");
            linkStatus.addStatus(LinkStatus.ERROR_FILE_NOT_FOUND);
            return;
        }
        form = forms[1];
        form.remove("dl.start");
        form.put("dl.start", "Free");
        br.submitForm(form);

        // case PluginStep.STEP_PENDING:
        // if (aborted) {
        // logger.warning("Plugin abgebrochen");
        // linkStatus.addStatus(LinkStatus.TODO);
        // //step.setStatus(PluginStep.STATUS_TODO);
        // return;
        // }
        try {
            waittime = Long.parseLong(new Regex(br, "<script>var.*?\\= ([\\d]+)").getFirstMatch()) * 1000;
        } catch (Exception e) {
            try {
                waittime = Long.parseLong(new Regex(br, "\\(Oder warte ([\\d]+) Minuten\\)").getFirstMatch()) * 60000;
                linkStatus.addStatus(LinkStatus.ERROR_IP_BLOCKED);
                linkStatus.setValue(waittime);
            } catch (Exception es) {
                // step.setStatus(PluginStep.STATUS_ERROR);
                logger.severe("kann wartezeit nicht setzen");
                linkStatus.addStatus(LinkStatus.ERROR_RETRY);
                return;
            }
        }

        // case PluginStep.STEP_GET_CAPTCHA_FILE:
        String ticketCode = Encoding.htmlDecode(new Regex(br, "unescape\\(\\'(.*?)\\'\\)").getFirstMatch());

        form = br.getForm(0);
        captchaFile = Plugin.getLocalCaptchaFile(this, ".png");
        String captchaAdress = new Regex(ticketCode, "<img src=\"(.*?)\">").getFirstMatch();
        logger.info("CaptchaAdress:" + captchaAdress);
        boolean fileDownloaded = br.downloadFile(captchaFile, captchaAdress);
        if (!fileDownloaded || !captchaFile.exists() || captchaFile.length() == 0) {
            logger.severe("Captcha not found");
            linkStatus.addStatus(LinkStatus.ERROR_CAPTCHA);// step.setParameter("Captcha
            // ImageIO
            // Error");
            // step.setStatus(PluginStep.STATUS_ERROR);
            return;
        }
        try {
            code = Plugin.getCaptchaCode(captchaFile, this);
        } catch (Exception e) {

        }
        if (code == null || code == "") {
            logger.severe("Bot erkannt");
            linkStatus.addStatus(LinkStatus.ERROR_IP_BLOCKED);
            linkStatus.setValue(60000);
            // step.setStatus(PluginStep.STATUS_ERROR);
            JDUtilities.appendInfoToFilename(this, captchaFile, "_NULL", false);
            return;
        }
        form.put("captcha", code);
        // step.setStatus(PluginStep.STATUS_SKIP);

        // case PluginStep.STEP_DOWNLOAD:
        // if (aborted) {
        // logger.warning("Plugin abgebrochen");
        // linkStatus.addStatus(LinkStatus.TODO);
        // //step.setStatus(PluginStep.STATUS_TODO);
        // return;
        // }
        HTTPConnection urlConnection = br.openFormConnection(form);
        dl = new RAFDownload(this, downloadLink, urlConnection);

        dl.startDownload();
    }

    @Override
    public void handlePremium(DownloadLink downloadLink, Account account) throws Exception {
        String user = account.getUser();
        String pass = account.getPass();
        Browser.clearCookies(HOST);
        br.setFollowRedirects(false);
        LinkStatus linkStatus = downloadLink.getLinkStatus();

        String formatPass = "";
        for (int i = 0; i < pass.length(); i++) {
            formatPass += "%" + Integer.toString(pass.charAt(i), 16);

        }

        String path = new URI(downloadLink.getDownloadURL()).getPath();
        PostRequest r = new PostRequest("http://rapidshare.de");
        r.setPostVariable("uri", Encoding.urlEncode(path));
        r.setPostVariable("dl.start", "PREMIUM");
        r.getCookies().put("user", user + "-" + formatPass);

        String page = r.load();
        String error = new Regex(page, "alert\\(\"(.*)\"\\)<\\/script>").getFirstMatch();
        if (error != null) {
            linkStatus.addStatus(LinkStatus.ERROR_FATAL);
            linkStatus.setErrorMessage(JDLocale.L("plugins.host.rapidshareDE.errors." + JDUtilities.getMD5(error), error));
            // step.setStatus(PluginStep.STATUS_ERROR);
            return;

        }
        String url = new Regex(page, "\\:<\\/b> <a href\\=\"([^\"].*)\">.*?.rapidshare.de").getFirstMatch();

        HTTPConnection urlConnection;
        GetRequest req = new GetRequest(url);
        r.getCookies().put("user", user + "-" + formatPass);
        req.connect();
        urlConnection = req.getHttpConnection();
        if (urlConnection.getHeaderField("content-disposition") == null) {

            page = req.read();

            // if ((error = this.findError(page)) != null) {
            // new File(downloadLink.getFileOutput()).delete();
            //
            // logger.warning(error);
            // //step.setStatus(PluginStep.STATUS_ERROR);
            // if (Regex.matches(error, PATTERN_MATCHER_PREMIUM_EXPIRED)) {
            // linkStatus.addStatus(LinkStatus.ERROR_PREMIUM);
            // //step.setParameter(premium);
            // downloadLink.getLinkStatus().setStatusText(error);
            // } else if (Regex.matches(error,
            // PATTERN_MATCHER_PREMIUM_LIMIT_REACHED)) {
            // linkStatus.addStatus(LinkStatus.ERROR_PREMIUM);
            // //step.setParameter(premium);
            // downloadLink.getLinkStatus().setStatusText(error);
            // } else {
            // linkStatus.addStatus(LinkStatus.ERROR_PLUGIN_SPECIFIC);
            // downloadLink.getLinkStatus().setStatusText(error);
            // //step.setParameter(error);
            // }
            //
            // return;
            // } else {
            // new File(downloadLink.getFileOutput()).delete();
            //
            // linkStatus.addStatus(LinkStatus.ERROR_RETRY);
            //
            // this.reportUnknownError(page, 6);
            //
            // //step.setStatus(PluginStep.STATUS_ERROR);
            // return;
            // }

        }

        dl = new RAFDownload(this, downloadLink, urlConnection);

        dl.setResume(true);
        dl.setChunkNum(JDUtilities.getSubConfig("DOWNLOAD").getIntegerProperty(Configuration.PARAM_DOWNLOAD_MAX_CHUNKS, 2));
        dl.startDownload();

    }

    @Override
    public String getAGBLink() {

        return "http://rapidshare.de/de/faq.html";
    }

    @Override
    public String getCoder() {
        return "JD-Team";
    }

    @Override
    public boolean getFileInformation(DownloadLink downloadLink) {
        
        Browser.clearCookies(HOST);
        br.setFollowRedirects(false);
        br.getPage(downloadLink.getDownloadURL());
        Form[] forms = br.getForms();
        if (forms.length < 2) { return false; }

        br.submitForm(forms[1]);
        try {
            String[][] regExp = new Regex(br, "<p>Du hast die Datei <b>(.*?)</b> \\(([\\d]+)").getMatches();
            downloadLink.setDownloadSize(Integer.parseInt(regExp[0][1]) * 1024);
            downloadLink.setName(regExp[0][0]);
            return true;
        } catch (Exception e) {
            // TODO: handle exception
        }
        return false;
    }

    @Override
    public String getHost() {
        return HOST;
    }

    @Override
    /*
     * public int getMaxSimultanDownloadNum() { if
     * (JDUtilities.getConfiguration().getBooleanProperty(Configuration.PARAM_USE_GLOBAL_PREMIUM,
     * true) && getProperties().getBooleanProperty(PROPERTY_USE_PREMIUM, false)) {
     * return 20; } else { return 1; } }
     * 
     * @Override
     */public String getPluginName() {
        return HOST;
    }

    @Override
    public Pattern getSupportedLinks() {
        return patternSupported;
    }

    @Override
    public String getVersion() {
        String ret = new Regex("$Revision$", "\\$Revision: ([\\d]*?) \\$").getFirstMatch();
        return ret == null ? "0.0" : ret;
    }

    @Override
    public void reset() {
        // TODO Automatisch erstellter Methoden-Stub
    }

    @Override
    public void resetPluginGlobals() {
        // TODO Automatisch erstellter Methoden-Stub
    }

    private void setConfigElements() {

    }
}
