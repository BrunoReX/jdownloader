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
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import java.util.HashMap;
import java.util.regex.Pattern;

import jd.config.ConfigContainer;
import jd.config.ConfigEntry;
import jd.config.Configuration;
import jd.plugins.DownloadLink;
import jd.plugins.Plugin;
import jd.plugins.PluginForHost;
import jd.plugins.PluginStep;
import jd.plugins.RequestInfo;
import jd.plugins.download.RAFDownload;
import jd.utils.JDLocale;
import jd.utils.JDUtilities;

public class Uploadedto extends PluginForHost {
    //uploaded.to/file/40gtfe 
    //uploaded.to/?id=5tr1m8
    static private final Pattern PAT_SUPPORTED = Pattern.compile("http://.*?uploaded\\.to/(file/|\\?id\\=)[a-zA-Z0-9]{6}.*", Pattern.CASE_INSENSITIVE);

    static private final String     HOST                         = "uploaded.to";

    static private final String     PLUGIN_NAME                  = HOST;

    static private final String     PLUGIN_VERSION               = "0.1.1";

    static private final String     PLUGIN_ID                    = PLUGIN_NAME + "-" + PLUGIN_VERSION;

    static private final String     CODER                        = "JD-Team";

    static private final String     AGB_LINK                     = "http://uploaded.to/agb";

    // Simplepattern
    
    static private final String 	DOWNLOAD_LIMIT_REACHED		 = "Free-Traffic ist aufgebraucht";
    
    static private final String     DOWNLOAD_URL                 = "<form name=\"download_form\" onsubmit=\"startDownload();\" method=\"post\" action=\"°\">";

    static private final String     DOWNLOAD_URL_WITHOUT_CAPTCHA = "<form name=\"download_form\" method=\"post\" action=\"°\">";

    static private final String     DOWNLOAD_URL_PREMIUM         = "<form name=\"download_form\" method=\"post\" action=\"°\">";

    private static final String     FILE_INFO                    = "Dateiname:°</td><td><b>°</b></td></tr>°<tr><td style=\"padding-left:4px;\">Dateityp:°</td><td>°</td></tr>°<tr><td style=\"padding-left:4px;\">Dateig°</td><td>°</td>";

    static private final String     FILE_NOT_FOUND               = "Datei existiert nicht";

    static private final String     TRAFFIC_EXCEEDED             = "Ihr Premium-Traffic ist aufgebraucht";

    private static final Pattern    CAPTCHA_FLE                  = Pattern.compile("<img name=\"img_captcha\" src=\"(.*?)\" border=0 />");

    private static final Pattern    CAPTCHA_TEXTFLD              = Pattern.compile("<input type=\"text\" id=\".*?\" name=\"(.*?)\" onkeyup=\"cap\\(\\)\\;\" size=3 />");

    private HashMap<String, String> postParameter                = new HashMap<String, String>();

    private static String           lastPassword                 = null;

    private String                  captchaAddress;

    private String                  postTarget;

    private String                  finalURL;

    private boolean                 useCaptchaVersion;

    private String                  cookie;

    public Uploadedto() {
        steps.add(new PluginStep(PluginStep.STEP_WAIT_TIME, null));
        steps.add(new PluginStep(PluginStep.STEP_GET_CAPTCHA_FILE, null));
        steps.add(new PluginStep(PluginStep.STEP_DOWNLOAD, null));
        setConfigElements();
    }

    @Override
    public String getCoder() {
        return CODER;
    }

    @Override
    public String getPluginName() {
        return HOST;
    }

    @Override
    public Pattern getSupportedLinks() {
        return PAT_SUPPORTED;
    }

    @Override
    public String getHost() {
        return HOST;
    }

    @Override
    public String getVersion() {
        return PLUGIN_VERSION;
    }

    @Override
    public String getPluginID() {
        return PLUGIN_ID;
    }

    // @Override
    // public URLConnection getURLConnection() {
    // // XXX: ???
    // return null;
    // }
    public PluginStep doStep(PluginStep step, DownloadLink parameter) {
        if (JDUtilities.getConfiguration().getBooleanProperty(Configuration.PARAM_USE_GLOBAL_PREMIUM, true)&&getProperties().getBooleanProperty(PROPERTY_USE_PREMIUM, false)) {

            return doPremiumStep(step, parameter);
        }
        // http://uploaded.to/file/6t2rrq
        // http://uploaded.to/?id=6t2rrq
        // http://uploaded.to/file/6t2rrq/blabla.rar
        // Url correction
        correctURL(parameter);
        RequestInfo requestInfo;
        try {
            DownloadLink downloadLink = (DownloadLink) parameter;
            switch (step.getStep()) {
                case PluginStep.STEP_WAIT_TIME:

                    requestInfo = getRequest(new URL(downloadLink.getDownloadURL()), "lang=de", null, true);
///?view=error_traffic_exceeded_free
                    if(requestInfo.containsHTML(DOWNLOAD_LIMIT_REACHED)||(requestInfo.getLocation()!=null &&requestInfo.getLocation().indexOf("traffic_exceeded")>=0)){
                        
                        int waitTime = 10 * 60 * 1000;
                        downloadLink.setStatus(DownloadLink.STATUS_ERROR_DOWNLOAD_LIMIT);
                        step.setStatus(PluginStep.STATUS_ERROR);
                        logger.info("Traffic Limit reached....");
                        step.setParameter((long) waitTime);
                        return step;
                    }
                    // Datei geloescht?
                    if (requestInfo.getHtmlCode().contains(FILE_NOT_FOUND)) {
                        logger.severe("download not found");
                        downloadLink.setStatus(DownloadLink.STATUS_ERROR_FILE_NOT_FOUND);
                        step.setStatus(PluginStep.STATUS_ERROR);
                        return step;
                    }

                    // 3 Versuche
                    String pass = null;
                    if (requestInfo.containsHTML("file_key")) {
                        logger.info("File is Password protected (1)");
                        if (lastPassword != null) {
                            logger.info("Try last pw: " + lastPassword);
                            pass = lastPassword;
                            requestInfo = postRequest(new URL(downloadLink.getDownloadURL()), "lang=de", null, null, "lang=de&file_key=" + pass, false);

                        }
                        else {
                            pass = JDUtilities.getController().getUiInterface().showUserInputDialog("Password?");
                            logger.info("Password: " + pass);
                            requestInfo = postRequest(new URL(downloadLink.getDownloadURL()), "lang=de", null, null, "lang=de&file_key=" + pass, false);
                        }

                    }
                    if (requestInfo.containsHTML("file_key")) {
                        logger.info("File is Password protected (2)");
                        pass = JDUtilities.getController().getUiInterface().showUserInputDialog("Password?");
                        logger.info("Password: " + pass);
                        requestInfo = postRequest(new URL(downloadLink.getDownloadURL()), "lang=de", null, null, "lang=de&file_key=" + pass, false);

                    }
                    if (requestInfo.containsHTML("file_key")) {
                        logger.info("File is Password protected (3)");
                        pass = JDUtilities.getController().getUiInterface().showUserInputDialog("Password?");
                        logger.info("Password: " + pass);
                        requestInfo = postRequest(new URL(downloadLink.getDownloadURL()), "lang=de", null, null, "lang=de&file_key=" + pass, false);

                    }
                    if (requestInfo.containsHTML("file_key")) {
                        logger.severe("Wrong password entered");

                        downloadLink.setStatus(DownloadLink.STATUS_ERROR_PLUGIN_SPECIFIC);
                        step.setParameter("Wrong Password");
                        step.setStatus(PluginStep.STATUS_ERROR);
                        return step;

                    }
                    if (pass != null) {
                        lastPassword = pass;
                    }

                    // logger.info(requestInfo.getHtmlCode());
                    this.captchaAddress = "http://" + requestInfo.getConnection().getRequestProperty("host") + "/" + getFirstMatch(requestInfo.getHtmlCode(), CAPTCHA_FLE, 1);

                    this.postTarget = getFirstMatch(requestInfo.getHtmlCode(), CAPTCHA_TEXTFLD, 1);
                    String url = getSimpleMatch(requestInfo.getHtmlCode(), DOWNLOAD_URL, 0);
                    if (url == null) {
                        this.useCaptchaVersion = false;
                        // Captcha deaktiviert
                        // 

                        url = getSimpleMatch(requestInfo.getHtmlCode(), DOWNLOAD_URL_WITHOUT_CAPTCHA, 0);
                        logger.finer("Use Captcha free Plugin: " + url);
                        requestInfo = postRequest(new URL(url), "lang=de", null, null, null, false);
                      ///?view=error_traffic_exceeded_free
                        if(requestInfo.containsHTML(DOWNLOAD_LIMIT_REACHED)||(requestInfo.getLocation()!=null &&requestInfo.getLocation().indexOf("traffic_exceeded")>=0)){
                            
                            int waitTime = 10 * 60 * 1000;
                            downloadLink.setStatus(DownloadLink.STATUS_ERROR_DOWNLOAD_LIMIT);
                            step.setStatus(PluginStep.STATUS_ERROR);
                            logger.info("Traffic Limit reached....");
                            step.setParameter((long) waitTime);
                            return step;
                        }
                        if (requestInfo.getConnection().getHeaderField("Location") != null) {
                            this.finalURL = "http://" + requestInfo.getConnection().getRequestProperty("host") + requestInfo.getConnection().getHeaderField("Location");
                            return step;
                        }
                        step.setStatus(PluginStep.STATUS_ERROR);
                        downloadLink.setStatus(DownloadLink.STATUS_ERROR_UNKNOWN);
                    }
                    else {
                        useCaptchaVersion = true;
                        logger.finer("Use Captcha Plugin");
                        requestInfo = postRequest(new URL(url), "lang=de", null, null, null, false);
                      ///?view=error_traffic_exceeded_free
                        if(requestInfo.containsHTML(DOWNLOAD_LIMIT_REACHED)||(requestInfo.getLocation()!=null &&requestInfo.getLocation().indexOf("traffic_exceeded")>=0)){
                            
                            int waitTime = 10 * 60 * 1000;
                            downloadLink.setStatus(DownloadLink.STATUS_ERROR_DOWNLOAD_LIMIT);
                            step.setStatus(PluginStep.STATUS_ERROR);
                            logger.info("Traffic Limit reached....");
                            step.setParameter((long) waitTime);
                            return step;
                        }
                        
                        if (requestInfo.getConnection().getHeaderField("Location") != null && requestInfo.getConnection().getHeaderField("Location").indexOf("error") > 0) {
                            logger.severe("Unbekannter fehler.. retry in 20 sekunden");
                            step.setStatus(PluginStep.STATUS_ERROR);
                            downloadLink.setStatus(DownloadLink.STATUS_ERROR_UNKNOWN_RETRY);
                            step.setParameter(20000l);
                            return step;
                        }
                        if (requestInfo.getConnection().getHeaderField("Location") != null) {
                            this.finalURL = "http://" + requestInfo.getConnection().getRequestProperty("host") + requestInfo.getConnection().getHeaderField("Location");
                            return step;
                        }
                        step.setStatus(PluginStep.STATUS_ERROR);
                        downloadLink.setStatus(DownloadLink.STATUS_ERROR_UNKNOWN);
                    }
                    return step;
                case PluginStep.STEP_GET_CAPTCHA_FILE:
                    if (useCaptchaVersion) {
                        File file = this.getLocalCaptchaFile(this);
                        if (!JDUtilities.download(file, captchaAddress) || !file.exists()) {
                            logger.severe("Captcha Download fehlgeschlagen: " + captchaAddress);
                            step.setParameter(null);
                            step.setStatus(PluginStep.STATUS_ERROR);
                            downloadLink.setStatus(DownloadLink.STATUS_ERROR_CAPTCHA_IMAGEERROR);
                            return step;
                        }
                        else {
                            step.setParameter(file);
                            step.setStatus(PluginStep.STATUS_USER_INPUT);
                        }
                        break;
                    }
                    else {
                        step.setStatus(PluginStep.STATUS_SKIP);
                        downloadLink.setStatusText("No Captcha");
                        return step;

                    }
                case PluginStep.STEP_DOWNLOAD:
                    if (useCaptchaVersion) {
                        this.finalURL = finalURL + (String) steps.get(1).getParameter();
                        logger.info("dl " + finalURL);
                        postParameter.put(postTarget, (String) steps.get(1).getParameter());
                        requestInfo = getRequestWithoutHtmlCode(new URL(finalURL), "lang=de", null, false);
                      ///?view=error_traffic_exceeded_free
                        if(requestInfo.containsHTML(DOWNLOAD_LIMIT_REACHED)||(requestInfo.getLocation()!=null &&requestInfo.getLocation().indexOf("traffic_exceeded")>=0)){
                            
                            int waitTime = 10 * 60 * 1000;
                            downloadLink.setStatus(DownloadLink.STATUS_ERROR_DOWNLOAD_LIMIT);
                            step.setStatus(PluginStep.STATUS_ERROR);
                            logger.info("Traffic Limit reached....");
                            step.setParameter((long) waitTime);
                            return step;
                        }
                        if (requestInfo.getConnection().getHeaderField("Location") != null && requestInfo.getConnection().getHeaderField("Location").indexOf("error-captcha") > 0) {
                            step.setStatus(PluginStep.STATUS_ERROR);
                            logger.severe("captcha Falsch");
                            downloadLink.setStatus(DownloadLink.STATUS_ERROR_CAPTCHA_WRONG);

                            return step;
                        }

                        if (requestInfo.getConnection().getHeaderField("Location") != null && requestInfo.getConnection().getHeaderField("Location").indexOf("error") > 0) {
                            step.setStatus(PluginStep.STATUS_ERROR);
                            logger.severe("Fehler 1 Errorpage wird angezeigt " + requestInfo.getConnection().getHeaderField("Location"));

                            downloadLink.setStatus(DownloadLink.STATUS_ERROR_UNKNOWN_RETRY);
                            step.setParameter(20000l);
                            return step;
                        }
                        int length = requestInfo.getConnection().getContentLength();
                        downloadLink.setDownloadMax(length);
                        logger.info("Filenam1e: " + getFileNameFormHeader(requestInfo.getConnection()));
                      
                        if (getFileNameFormHeader(requestInfo.getConnection()) == null || getFileNameFormHeader(requestInfo.getConnection()).indexOf("?") >= 0) {
                            step.setStatus(PluginStep.STATUS_ERROR);
                            logger.severe("Fehler 2 Dateiname kann nicht ermittelt werden");
                            downloadLink.setStatus(DownloadLink.STATUS_ERROR_UNKNOWN_RETRY);
                            step.setParameter(20000l);
                            return step;
                        }
                        downloadLink.setName(getFileNameFormHeader(requestInfo.getConnection()));
                        if (!hasEnoughHDSpace(downloadLink)) {
                            downloadLink.setStatus(DownloadLink.STATUS_ERROR_NO_FREE_SPACE);
                            step.setStatus(PluginStep.STATUS_ERROR);
                            return step;
                        }
                       dl = new RAFDownload(this, downloadLink, requestInfo.getConnection());

                        dl.startDownload();
                        return step;
                    }
                    else {
                        this.finalURL = finalURL + "";
                        logger.info("dl " + finalURL);

                        requestInfo = getRequestWithoutHtmlCode(new URL(finalURL), "lang=de", null, false);
                       
                        if (requestInfo.getConnection().getHeaderField("Location") != null && requestInfo.getConnection().getHeaderField("Location").indexOf("error") > 0) {
                            step.setStatus(PluginStep.STATUS_ERROR);
                            logger.severe("Fehler 1 Errorpage wird angezeigt " + requestInfo.getConnection().getHeaderField("Location"));

                            downloadLink.setStatus(DownloadLink.STATUS_ERROR_UNKNOWN_RETRY);
                            step.setParameter(20000l);
                            return step;
                        }
                        int length = requestInfo.getConnection().getContentLength();
                        downloadLink.setDownloadMax(length);
                        
                        int w=0;
                        while(requestInfo.getHeaders().size()<2){
                            w++;
                            downloadLink.setStatusText("Warte auf Verbindung...");
                            try {
                                Thread.sleep(1000);
                            }
                            catch (InterruptedException e) {}
                            if(w>30){
                                logger.severe("ERROR!!!");
                                break;
                            }
                        }
                        logger.info("Filename: " + getFileNameFormHeader(requestInfo.getConnection()));
                        
                        logger.info("Headers: "+requestInfo.getHeaders().size());
                        logger.info("Connection: "+requestInfo.getConnection());
                        logger.info("Code: \r\n"+requestInfo.getHtmlCode());
                        
                        if (getFileNameFormHeader(requestInfo.getConnection()) == null || getFileNameFormHeader(requestInfo.getConnection()).indexOf("?") >= 0) {
                            step.setStatus(PluginStep.STATUS_ERROR);
                            logger.severe("Fehler 2 Dateiname kann nicht ermittelt werden");
                            downloadLink.setStatus(DownloadLink.STATUS_ERROR_UNKNOWN_RETRY);
                            step.setParameter(20000l);
                            return step;
                        }
                        downloadLink.setName(getFileNameFormHeader(requestInfo.getConnection()));
                        if (!hasEnoughHDSpace(downloadLink)) {
                            downloadLink.setStatus(DownloadLink.STATUS_ERROR_NO_FREE_SPACE);
                            step.setStatus(PluginStep.STATUS_ERROR);
                            return step;
                        }
                       dl = new RAFDownload(this, downloadLink, requestInfo.getConnection());

                        dl.startDownload();
                
                        return step;
                    }
            }
            return step;
        }
        catch (IOException e) {
            e.printStackTrace();
            step.setStatus(PluginStep.STATUS_ERROR);
            logger.severe("Unbekannter Fehler. siehe Exception");
            parameter.setStatus(DownloadLink.STATUS_ERROR_UNKNOWN);
            step.setParameter(20000l);
            return step;
        }
    }

    private PluginStep doPremiumStep(PluginStep step, DownloadLink parameter) {
        correctURL(parameter);

        RequestInfo requestInfo;
        String user = getProperties().getStringProperty(PROPERTY_PREMIUM_USER);
        String pass = getProperties().getStringProperty(PROPERTY_PREMIUM_PASS);

        if (user == null || pass == null) {

            step.setStatus(PluginStep.STATUS_ERROR);
            logger.severe("Premiumfehler Logins are incorrect");
            parameter.setStatus(DownloadLink.STATUS_ERROR_PLUGIN_SPECIFIC);
            step.setParameter(JDLocale.L("plugins.host.premium.loginError", "Login Fehler") + ": "+user);
            getProperties().setProperty(PROPERTY_USE_PREMIUM, false);
            return step;

        }
        try {
            DownloadLink downloadLink = (DownloadLink) parameter;
            switch (step.getStep()) {
                // Wird als login verwendet
                case PluginStep.STEP_WAIT_TIME:
                    logger.info("login");
                    requestInfo = Plugin.postRequest(new URL("http://uploaded.to/login"), null, null, null, "email="+user+"&password="+pass, false);
                    
                    if (requestInfo.getCookie().indexOf("auth") < 0) {
                        step.setStatus(PluginStep.STATUS_ERROR);
                        parameter.setStatus(DownloadLink.STATUS_ERROR_PLUGIN_SPECIFIC);
                        step.setParameter("Login Error: "+user);
                        getProperties().setProperty(PROPERTY_USE_PREMIUM, false);
                        return step;
                    }
                    cookie = requestInfo.getCookie();

                    return step;
                case PluginStep.STEP_GET_CAPTCHA_FILE:
                    step.setStatus(PluginStep.STATUS_SKIP);
                    downloadLink.setStatusText("Premiumdownload");
                    step = nextStep(step);
                  
                case PluginStep.STEP_DOWNLOAD:

                    requestInfo = getRequest(new URL(downloadLink.getDownloadURL()), cookie, null, false);
                    // Datei geloescht?
                    if (requestInfo.getHtmlCode().contains(FILE_NOT_FOUND)) {
                        logger.severe("download not found");
                        downloadLink.setStatus(DownloadLink.STATUS_ERROR_FILE_NOT_FOUND);
                        step.setStatus(PluginStep.STATUS_ERROR);
                        return step;
                    }
                    // Traffic aufgebraucht?
                    if (requestInfo.getHtmlCode().contains(TRAFFIC_EXCEEDED)) {
                        logger.warning("Premium traffic exceeded (> 50 GiB in the last 10 days)");
                        downloadLink.setStatus(DownloadLink.STATUS_ERROR_PREMIUM);
                        step.setParameter("Premium overload (> 50 GiB)");
                        step.setStatus(PluginStep.STATUS_ERROR);
                        getProperties().setProperty(PROPERTY_USE_PREMIUM, false);
                        return step;
                    }

                    // 3 Versuche
                    String filepass = null;
                    if (requestInfo.containsHTML("file_key")) {
                        logger.info("File is Password protected1");
                        if (lastPassword != null) {
                            logger.info("Try last pw: " + lastPassword);
                            filepass = lastPassword;
                            requestInfo = postRequest(new URL(downloadLink.getDownloadURL()), cookie, null, null, "lang=de&file_key=" + filepass, false);

                        }
                        else {
                            filepass = JDUtilities.getController().getUiInterface().showUserInputDialog("Password?");
                            logger.info("Password: " + pass);
                            requestInfo = postRequest(new URL(downloadLink.getDownloadURL()), cookie, null, null, "lang=de&file_key=" + filepass, false);
                        }

                    }
                    if (requestInfo.containsHTML("file_key")) {
                        logger.info("File is Password protected (2)");
                        filepass = JDUtilities.getController().getUiInterface().showUserInputDialog("Password?");
                        logger.info("Password: " + pass);
                        requestInfo = postRequest(new URL(downloadLink.getDownloadURL()), cookie, null, null, "lang=de&file_key=" + filepass, false);

                    }
                    if (requestInfo.containsHTML("file_key")) {
                        logger.info("File is Password protected (3)");
                        filepass = JDUtilities.getController().getUiInterface().showUserInputDialog("Password?");
                        logger.info("Password: " + pass);
                        requestInfo = postRequest(new URL(downloadLink.getDownloadURL()), cookie, null, null, "lang=de&file_key=" + filepass, false);

                    }
                    if (requestInfo.containsHTML("file_key")) {
                        logger.severe("Wrong password entered");

                        downloadLink.setStatus(DownloadLink.STATUS_ERROR_PLUGIN_SPECIFIC);
                        step.setParameter("Wrong Password");
                        step.setStatus(PluginStep.STATUS_ERROR);
                        return step;

                    }
                    if (filepass != null) {
                        lastPassword = filepass;
                    }
                    String newURL = null;
                    if (requestInfo.getConnection().getHeaderField("Location") == null || requestInfo.getConnection().getHeaderField("Location").length() < 10) {
                        newURL = getSimpleMatch(requestInfo.getHtmlCode(), DOWNLOAD_URL_PREMIUM, 0);
                      
                        if (newURL == null) {
                            logger.severe("Indirekter Link konnte nicht gefunden werden");

                            downloadLink.setStatus(DownloadLink.STATUS_ERROR_PLUGIN_SPECIFIC);
                            step.setParameter("Indirect Link Error");
                            step.setStatus(PluginStep.STATUS_ERROR);
                            return step;
                        }

                        requestInfo = postRequest(new URL(newURL), cookie, null, null, null, false);

                        if (requestInfo.getConnection().getHeaderField("Location") == null || requestInfo.getConnection().getHeaderField("Location").length() < 10) {
                            if (getFileNameFormHeader(requestInfo.getConnection()) == null || getFileNameFormHeader(requestInfo.getConnection()).indexOf("?") >= 0) {
                                step.setStatus(PluginStep.STATUS_ERROR);
                                logger.severe("Endlink not found");
                                downloadLink.setStatus(DownloadLink.STATUS_ERROR_UNKNOWN);

                                return step;
                            }

                        }
                    }
                    else {
                        logger.info("Direct Downloads active");

                    }
                    String redirect = requestInfo.getConnection().getHeaderField("Location");
                    if (!redirect.startsWith("http://") && newURL != null) {

                        redirect = "http://" + new URL(newURL).getHost() + redirect;

                    }

                    requestInfo = getRequestWithoutHtmlCode(new URL(redirect), cookie, null, false);
                    int length = requestInfo.getConnection().getContentLength();
                    downloadLink.setDownloadMax(length);
                    logger.info("Filename: " + getFileNameFormHeader(requestInfo.getConnection()));
                    if (getFileNameFormHeader(requestInfo.getConnection()) == null || getFileNameFormHeader(requestInfo.getConnection()).indexOf("?") >= 0) {
                        step.setStatus(PluginStep.STATUS_ERROR);
                        logger.severe("Fehler 2 Dateiname kann nicht ermittelt werden");
                        downloadLink.setStatus(DownloadLink.STATUS_ERROR_UNKNOWN);

                        return step;
                    }
                    downloadLink.setName(getFileNameFormHeader(requestInfo.getConnection()));
                    if (!hasEnoughHDSpace(downloadLink)) {
                        downloadLink.setStatus(DownloadLink.STATUS_ERROR_NO_FREE_SPACE);
                        step.setStatus(PluginStep.STATUS_ERROR);
                        return step;
                    }
                   dl = new RAFDownload(this, downloadLink, requestInfo.getConnection());
                   dl.setChunkNum(JDUtilities.getSubConfig("DOWNLOAD").getIntegerProperty(Configuration.PARAM_DOWNLOAD_MAX_CHUNKS,3));
                    dl.setResume(true);
                    dl.startDownload();
                    return step;
            }
            return step;
        }
        catch (Exception e) {
            e.printStackTrace();
            step.setStatus(PluginStep.STATUS_ERROR);
            logger.severe("Unbekannter Fehler. siehe Exception");
            parameter.setStatus(DownloadLink.STATUS_ERROR_UNKNOWN);

            return step;
        }
    }

    /**
     * Korrigiert den Downloadlink in ein einheitliches Format
     * 
     * @param parameter
     */
    private void correctURL(DownloadLink parameter) {
        String link = parameter.getDownloadURL();
        link = link.replace("/?id=", "/file/");
        link = link.replace("?id=", "file/");
        String[] parts = link.split("\\/");
        String newLink = "";
        for (int t = 0; t < Math.min(parts.length, 5); t++)
            newLink += parts[t] + "/";

        parameter.setUrlDownload(newLink);

    }

    @Override
    public boolean doBotCheck(File file) {
        return false;
    }

    @Override
    public void reset() {
        this.finalURL = null;
        cookie = null;
    }

    public String getFileInformationString(DownloadLink downloadLink) {
        return downloadLink.getName() + " (" + JDUtilities.formatBytesToMB(downloadLink.getDownloadMax()) + ")";
    }

    @Override
    public boolean getFileInformation(DownloadLink downloadLink) {
        RequestInfo requestInfo;
        correctURL(downloadLink);
        try {
            requestInfo = getRequestWithoutHtmlCode(new URL(downloadLink.getDownloadURL()), null, null, false);
            if (requestInfo.getConnection().getHeaderField("Location") != null && requestInfo.getConnection().getHeaderField("Location").indexOf("error") > 0) {
                this.setStatusText("Error");
                return false;
            }
            else {
                if (requestInfo.getConnection().getHeaderField("Location") != null) {
                    requestInfo = getRequest(new URL("http://" + HOST + requestInfo.getConnection().getHeaderField("Location")), null, null, true);
                }
                else {
                    requestInfo = readFromURL(requestInfo.getConnection());
                }

                // Datei geloescht?
                if (requestInfo.getHtmlCode().contains(FILE_NOT_FOUND)) {
                    this.setStatusText("File Not Found");
                    return false;
                }

                String fileName = JDUtilities.htmlDecode(getSimpleMatch(requestInfo.getHtmlCode(), FILE_INFO, 1));
                String ext = getSimpleMatch(requestInfo.getHtmlCode(), FILE_INFO, 4);
                String fileSize = getSimpleMatch(requestInfo.getHtmlCode(), FILE_INFO, 7);
                downloadLink.setName(fileName.trim() + "" + ext.trim());
                if (fileSize != null) {
                    try {
                        int length = (int) (Double.parseDouble(fileSize.trim().split(" ")[0]));
                        if (fileSize.toLowerCase().indexOf("mb") > 0) {
                            length *= 1024 * 1024;
                        }
                        else if (fileSize.toLowerCase().indexOf("kb") > 0) {
                            length *= 1024;
                        }

                        downloadLink.setDownloadMax(length);
                    }
                    catch (Exception e) {
                    }
                }
            }
            return true;
        }
        catch (MalformedURLException e) {
        }
        catch (IOException e) {
        }
        return false;
    }

    private void setConfigElements() {
        ConfigEntry cfg;
        config.addEntry(cfg = new ConfigEntry(ConfigContainer.TYPE_LABEL, JDLocale.L("plugins.host.premium.account", "Premium Account")));
        config.addEntry(cfg = new ConfigEntry(ConfigContainer.TYPE_TEXTFIELD, getProperties(), PROPERTY_PREMIUM_USER, JDLocale.L("plugins.host.premium.user", "Benutzer")));
        cfg.setDefaultValue("Kundennummer");
        config.addEntry(cfg = new ConfigEntry(ConfigContainer.TYPE_PASSWORDFIELD, getProperties(), PROPERTY_PREMIUM_PASS, JDLocale.L("plugins.host.premium.password", "Passwort")));
        cfg.setDefaultValue("Passwort");
        config.addEntry(cfg = new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getProperties(), PROPERTY_USE_PREMIUM, JDLocale.L("plugins.host.premium.useAccount", "Premium Account verwenden")));
        cfg.setDefaultValue(false);

    }

    @Override
    public int getMaxSimultanDownloadNum() {
        if (getProperties().getBooleanProperty(PROPERTY_USE_PREMIUM, false)) {

            return 20;
        }
        return 1;
    }

    @Override
    public void resetPluginGlobals() {
    }

    @Override
    public String getAGBLink() {
        return AGB_LINK;
    }
}
