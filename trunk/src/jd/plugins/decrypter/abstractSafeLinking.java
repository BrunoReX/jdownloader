package jd.plugins.decrypter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.http.URLConnectionAdapter;
import jd.nutils.JDHash;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.parser.html.Form;
import jd.parser.html.InputField;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterException;
import jd.plugins.DownloadLink;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;
import jd.plugins.PluginForHost;
import jd.utils.JDUtilities;

/**
 * abstract class to handle sites similar to safelinking type sites. <br />
 * Google "Secure your links with a captcha, a password and much more" to find such sites
 *
 * @author raztoki - abstract & improvements
 * @author bismarck - parts of the original
 * @author psp - parts of the original
 */
public abstract class abstractSafeLinking extends PluginForDecrypt {

    public abstractSafeLinking(PluginWrapper wrapper) {
        super(wrapper);
    }

    protected String                               parameter = null;
    protected String                               cType     = "notDetected";
    protected String                               uid       = null;

    protected final static AtomicReference<String> userAgent = new AtomicReference<String>(null);

    protected Browser prepBrowser(Browser br) {
        // browser stuff
        try {
            /* not available in old versions (before jd2) */
            br.setAllowedResponseCodes(new int[] { 500 });
        } catch (Throwable e) {
        }
        // we only want to load user-agent when specified
        if (useRUA()) {
            if (userAgent.get() == null) {
                /* we first have to load the plugin, before we can reference it */
                JDUtilities.getPluginForHost("mediafire.com");
                userAgent.set(jd.plugins.hoster.MediafireCom.stringUserAgent());
            }
            br.getHeaders().put("User-Agent", userAgent.get());
        }
        return br;
    }

    @Override
    public ArrayList<DownloadLink> decryptIt(final CryptedLink param, final ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        // link correction
        parameter = correctLink(param.toString());
        prepBrowser(br);
        // setuid
        uid = getUID(parameter);
        br.getPage(parameter);
        if (isOffline()) {
            decryptedLinks.add(createOfflinelink(parameter));
            return decryptedLinks;
        }
        // shortlink i assume
        if (parameter.matches(regexLinkShort())) {
            String newparameter = br.getRedirectLocation();
            if (newparameter == null) {
                logger.warning("Decrypter broken for link: " + parameter);
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            /* redirect goes to outside link or back own shortlink */
            if (!newparameter.matches(regexLinkD() + "|" + regexLinkP())) {
                decryptedLinks.add(createDownloadlink(newparameter));
                return decryptedLinks;
            }
            parameter = correctLink(newparameter);
            br.getPage(parameter);
        }
        if (br.getRedirectLocation() != null && br.getRedirectLocation().matches("^.+" + regexSupportedDomains() + "/404$")) {
            logger.info("Link offline: " + parameter);
            return decryptedLinks;
        }
        if (br.containsHTML(">This link does not exist")) {
            logger.info("Link offline: " + parameter);
            return decryptedLinks;
        }
        br.setFollowRedirects(false);
        if (parameter.matches(regexLinkD())) {
            decryptedLinks.add(decryptSingleLink(br));
        } else {
            handleCaptcha(param);
            decryptedLinks.addAll(decryptMultipleLinks(param));
        }
        if (decryptedLinks.isEmpty()) {
            logger.warning("Decrypter out of date for link: " + parameter);
            return null;
        }
        return decryptedLinks;
    }

    protected String getUID(final String parameter) {
        return new Regex(parameter, "/(?:p|d)/([a-z0-9]+)$").getMatch(0);
    }

    protected boolean isOffline() {
        return (br.getHttpConnection() != null && br.getHttpConnection().getResponseCode() == 404) || br.containsHTML(regexOffline());
    }

    private String regexOffline() {
        return ">404 Page/File not found<|>The page/file you've requested has been moved";
    }

    /**
     * Setter for when the site supports HTTPS
     *
     * @return
     */
    protected abstract boolean supportsHTTPS();

    /**
     * Setter for when the site enforces HTTPS
     *
     * @return
     */
    protected abstract boolean enforcesHTTPS();

    /**
     * Setter to use random User-Agent
     *
     * @return
     */
    protected abstract boolean useRUA();

    protected String regexSupportedDomains() {
        return Pattern.quote(this.getHost());
    }

    /**
     * doesn't always use primary host.
     *
     * @return
     */
    protected String regexLinkShort() {
        return "https?://[^/]*" + getShortHost() + "/[a-zA-Z0-9]+";
    }

    protected String getShortHost() {
        return "";
    }

    protected String regexLinkP() {
        return "https?://[^/]*" + regexSupportedDomains() + "/p/[a-z0-9]+";
    }

    protected String regexLinkD() {
        return "https?://[^/]*" + regexSupportedDomains() + "/d/[a-z0-9]+";
    }

    protected String correctLink(final String string) {
        final String s = string.replaceFirst("^https?://", enforcesHTTPS() && supportsHTTPS() ? "https://" : "http://");
        return s;
    }

    public int getCaptchaTypeNumber() {
        if ("solvemedia".equals(cType)) {
            return 1;
        } else if ("recaptcha".equals(cType)) {
            return 2;
        } else if ("basic".equals(cType)) {
            return 3;
        } else if ("threeD".equals(cType)) {
            return 4;
        } else if ("fancy".equals(cType)) {
            return 5;
        } else if ("qaptcha".equals(cType)) {
            return 6;
        } else if ("simple".equals(cType)) {
            return 7;
        } else if ("dotty".equals(cType)) {
            return 8;
        } else if ("cool".equals(cType)) {
            return 9;
        } else if ("standard".equals(cType)) {
            return 10;
        } else if ("cats".equals(cType)) {
            return 11;
        } else if ("simplecaptcha".equals(cType)) {
            return 12;
        } else {
            // Not detected or other case
            return 0;
        }
    }

    protected String regexCaptchaSolveMedia() {
        return "api(-secure)?\\.solvemedia\\.com/(papi)?";
    }

    protected String regexCaptchaRecaptcha() {
        return "(api\\.recaptcha\\.net|google\\.com/recaptcha/api/)";
    }

    protected String regexCaptchaBasic() {
        return "(https?://[^/]*" + regexSupportedDomains() + "/includes/captcha_factory/securimage/securimage_(show\\.php\\?hash=[a-z0-9]+|register\\.php\\?hash=[^\"]+sid=[a-z0-9]{32}))";
    }

    protected String regexCaptchaThreeD() {
        return "\"(https?://[^/]*" + regexSupportedDomains() + "/includes/captcha_factory/3dcaptcha/3DCaptcha\\.php)\"";
    }

    protected String regexCaptchaFancy() {
        return "class=\"captcha_image ajax-fc-container\"";
    }

    protected String regexCaptchaQaptcha() {
        return "class=\"protected-captcha\"><div id=\"QapTcha\"";
    }

    protected String regexCaptchaSimplecaptcha() {
        return "\"https?://[^/]*" + regexSupportedDomains() + "/simplecaptcha/captcha\\.php\"";
    }

    protected String regexCaptchaCatAndDog() {
        return "\"https?://[^/]*" + regexSupportedDomains() + "/includes/captcha_factory/catsdogs/catdogcaptcha\\.php\\?";
    }

    protected void handleCaptcha(final CryptedLink param) throws Exception {
        br.setFollowRedirects(true);
        LinkedHashMap<String, String> captchaRegex = new LinkedHashMap<String, String>();
        captchaRegex.put("solvemedia", regexCaptchaSolveMedia());
        captchaRegex.put("recaptcha", regexCaptchaRecaptcha());
        captchaRegex.put("basic", regexCaptchaBasic());
        captchaRegex.put("threeD", regexCaptchaThreeD());
        captchaRegex.put("fancy", regexCaptchaFancy());
        captchaRegex.put("qaptcha", regexCaptchaQaptcha());
        captchaRegex.put("simplecaptcha", regexCaptchaSimplecaptcha());
        captchaRegex.put("cats", regexCaptchaCatAndDog());

        /* search for protected form */
        Form protectedForm = formProtected();
        if (protectedForm != null) {
            String psw = null;
            boolean password = formInputFieldContainsProperties(protectedForm, formPasswordInputProperties());
            prepareCaptchaAdress(protectedForm.getHtmlCode(), captchaRegex);

            final int repeat = 5;
            for (int i = 0; i < repeat; i++) {
                while (protectedForm.hasInputFieldByName("%5C")) {
                    protectedForm.remove("%5C");
                }
                if (password) {
                    if ((i <= 2 && isCaptchaSkipable()) || (i <= 1 && !isCaptchaSkipable())) {
                        // only use this twice, once for auto solvemedia, and second time with manual captcha!
                        psw = param.getDecrypterPassword();
                    }
                    if (psw == null || "".equals(psw)) {
                        psw = getUserInput(parameter, param);
                        if (psw == null || "".equals(psw)) {
                            throw new DecrypterException(DecrypterException.PASSWORD);
                        }
                    }
                    protectedForm.put(formPasswordInputKeyName(), Encoding.urlEncode(psw));
                }

                Browser captchaBr = null;
                if (!"notDetected".equals(cType)) {
                    captchaBr = br.cloneBrowser();
                }

                switch (getCaptchaTypeNumber()) {
                case 1:
                    if (i == 0 && isCaptchaSkipable()) {
                        long wait = 0;
                        while (wait < 3000) {
                            wait = 1272 * new Random().nextInt(6);
                        }
                        Thread.sleep(wait);
                        protectedForm.put("captchatype", "Simple");
                        protectedForm.put("used_captcha", "SolveMedia");
                        protectedForm.put("adcopy_challenge", "null");
                        protectedForm.put("adcopy_response", "");
                        break;
                    } else {
                        PluginForDecrypt solveplug = JDUtilities.getPluginForDecrypt("linkcrypt.ws");
                        jd.plugins.decrypter.LnkCrptWs.SolveMedia sm = ((jd.plugins.decrypter.LnkCrptWs) solveplug).getSolveMedia(br);
                        File cf = sm.downloadCaptcha(getLocalCaptchaFile());
                        String code = getCaptchaCode(cf, param);
                        String chid = sm.getChallenge(code);
                        protectedForm.put("solvemedia_response=", Encoding.urlEncode(code));
                        protectedForm.put("adcopy_challenge", Encoding.urlEncode(chid));
                        protectedForm.put("adcopy_response", Encoding.urlEncode(code));
                        break;
                    }
                case 2: {
                    PluginForHost recplug = JDUtilities.getPluginForHost("DirectHTTP");
                    jd.plugins.hoster.DirectHTTP.Recaptcha rc = ((jd.plugins.hoster.DirectHTTP) recplug).getReCaptcha(br);
                    rc.parse();
                    rc.load();
                    File cfRe = rc.downloadCaptcha(getLocalCaptchaFile());
                    final String code = getCaptchaCode(cfRe, param);
                    /* Sometimes that field already exists containing the value "manuel_challenge" */
                    protectedForm.remove("recaptcha_response_field");
                    protectedForm.put("recaptcha_challenge_field", rc.getChallenge());
                    protectedForm.put("recaptcha_response_field", Encoding.urlEncode(code));
                    break;
                }
                case 3: {
                    final String code = getCaptchaCode(br.getRegex(captchaRegex.get("basic")).getMatch(0), param);
                    protectedForm.put("securimage_response_field", Encoding.urlEncode(code));
                    break;
                }
                case 4: {
                    final String code = getCaptchaCode(br.getRegex(captchaRegex.get("threeD")).getMatch(0), param);
                    protectedForm.put("3dcaptcha_response_field", Encoding.urlEncode(code));
                    break;
                }
                case 5: {
                    captchaBr.getHeaders().put("X-Requested-With", "XMLHttpRequest");
                    captchaBr.getPage("/includes/captcha_factory/fancycaptcha.php?hash=" + uid);
                    protectedForm.put("fancy-captcha", captchaBr.toString().trim());
                    break;
                }
                case 6: {
                    captchaBr.getHeaders().put("X-Requested-With", "XMLHttpRequest");
                    captchaBr.postPage("/includes/captcha_factory/Qaptcha.jquery.php?hash=" + uid, "action=qaptcha");
                    if (!captchaBr.containsHTML("\"error\":false")) {
                        logger.warning("Decrypter broken for link: " + parameter + "\n");
                        logger.warning("Qaptcha handling broken");
                        if (password) {
                            throw new DecrypterException(DecrypterException.PASSWORD);
                        }
                    }
                    protectedForm.put("iQapTcha", "");
                    break;
                }
                case 0:
                case 7:
                case 8:
                case 9:
                case 10:
                case 11: {
                    // unsupported types
                    // short wait to prevent hammering
                    Thread.sleep(2500);
                    // maybe also good to clear cookies?
                    br.getPage(br.getURL());
                    protectedForm = formProtected();
                    prepareCaptchaAdress(protectedForm.getHtmlCode(), captchaRegex);
                    continue;
                }
                case 12: {
                    final String result = getCaptchaCode("/simplecaptcha/captcha.php", param);
                    protectedForm.put("captchatype", "Simple");
                    protectedForm.put("norobot", Encoding.urlEncode(result));
                    break;
                }
                }
                if (captchaRegex.containsKey(cType) || password) {
                    br.submitForm(protectedForm);
                    if (br.getHttpConnection().getResponseCode() == 500) {
                        logger.warning("500 Internal Server Error. Link: " + parameter);
                        continue;
                    }
                    if (password) {
                        protectedForm = formProtected();
                        password = formInputFieldContainsProperties(protectedForm, formPasswordInputProperties());
                        if (password) {
                            if (i + 1 > repeat) {
                                throw new DecrypterException(DecrypterException.PASSWORD);
                            }
                            prepareCaptchaAdress(protectedForm.getHtmlCode(), captchaRegex);
                            continue;
                        }
                        break;
                    }
                    protectedForm = formProtected();
                    /*
                     * I doubt that the following is required, (confirmationCheck() ||
                     * br.containsHTML("<strong>Prove you are human</strong>")). it shouldn't be, but if the form is present even after
                     * successful captcha&|password step, we will need to reinstate. -raz 20150510
                     */
                    if (protectedForm != null) {
                        if (i + 1 > repeat) {
                            throw new DecrypterException(DecrypterException.CAPTCHA);
                        }
                        prepareCaptchaAdress(protectedForm.getHtmlCode(), captchaRegex);
                        continue;
                    }
                }
                break;
            }
        }
    }

    protected boolean isCaptchaSkipable() {
        return false;
    }

    protected boolean confirmationCheck() {
        return false;
    }

    protected String[][] formPasswordInputProperties() {
        return new String[][] { { "type", "password" }, { "name", formPasswordInputKeyName() } };
    }

    protected String formPasswordInputKeyName() {
        return "link-password";
    }

    /**
     * returns true when inputfield has property matching.
     *
     * @author raztoki
     * @param form
     * @param inputProperties
     * @return
     */
    protected final boolean formInputFieldContainsProperties(final Form form, final String[][] inputProperties) {
        if (form == null || inputProperties == null) {
            return false;
        }
        for (final InputField i : form.getInputFields()) {
            ArrayList<Boolean> v = new ArrayList<Boolean>(inputProperties.length);
            int d = -1;
            for (final String[] fpip : inputProperties) {
                d++;
                v.add(d, null);
                if ("type".equalsIgnoreCase(fpip[0]) && fpip[1].equalsIgnoreCase(i.getType())) {
                    v.add(d, Boolean.TRUE);
                } else if ("name".equalsIgnoreCase(fpip[0]) && fpip[1].equalsIgnoreCase(i.getKey())) {
                    v.add(d, Boolean.TRUE);
                } else if ("value".equalsIgnoreCase(fpip[0]) && fpip[1].equalsIgnoreCase(i.getValue())) {
                    v.add(d, Boolean.TRUE);
                } else {
                    final String property = i.getProperty(fpip[0], null);
                    if (fpip[1].equalsIgnoreCase(property)) {
                        v.add(d, Boolean.TRUE);
                    }
                }
            }
            Boolean isMapAllTrue = null;
            for (int x = 0; x != inputProperties.length; x++) {
                final Boolean b = v.get(x);
                if (Boolean.FALSE.equals(b) || b == null) {
                    isMapAllTrue = Boolean.FALSE;
                    break;
                } else if (Boolean.TRUE.equals(b)) {
                    isMapAllTrue = Boolean.TRUE;
                }
            }
            if (Boolean.TRUE.equals(isMapAllTrue)) {
                return isMapAllTrue;
            }
        }
        return false;
    }

    protected Form formProtected() {
        final Form f = br.getFormbyProperty("id", "protected-form");
        return f;
    }

    private void prepareCaptchaAdress(String captcha, LinkedHashMap<String, String> captchaRegex) {
        br.getRequest().setHtmlCode(captcha);

        // nullify cType, this is so feedback is correct on retries etc.
        cType = "notDetected";

        for (Entry<String, String> next : captchaRegex.entrySet()) {
            if (br.containsHTML(next.getValue())) {
                cType = next.getKey();
                break;
            }
        }

        logger.info("notDetected".equals(cType) ? "Captcha not detected." : "Detected captcha type \"" + cType + "\".");

        /* detect javascript */
        String javaScript = null;
        for (String js : br.getRegex("<script type=\"text/javascript\">(.*?)</script>").getColumn(0)) {
            if (!new Regex(js, captchaRegex.get(cType)).matches()) {
                continue;
            }
            javaScript = js;
        }
        if (javaScript == null) {
            return;
        }

        /* execute javascript */
        Object result = new Object();
        final ScriptEngineManager manager = jd.plugins.hoster.DummyScriptEnginePlugin.getScriptEngineManager(this);
        final ScriptEngine engine = manager.getEngineByName("javascript");
        try {
            /*
             * creating pseudo functions: document.location.protocol + document.write(value)
             */
            engine.eval("var document = { loc : function() { var newObj = new Object(); function protocol() { return \"" + new Regex(br.getURL(), "https?://").getMatch(-1) + "\"; } newObj.protocol = protocol(); return newObj; }, write : function(a) { return a; }}");
            engine.eval("document.location = document.loc();");
            result = engine.eval(javaScript);
        } catch (Throwable e) {
            return;
        }
        String res = result != null ? result.toString() : null;
        br.getRequest().setHtmlCode(res);
    }

    private ArrayList<DownloadLink> loadcontainer(String format, CryptedLink param) throws IOException, PluginException {
        ArrayList<DownloadLink> links = new ArrayList<DownloadLink>();
        String containerLink = br.getRegex("\"(https?://[^/]*" + regexSupportedDomains() + "/c/[a-z0-9]+" + format + ")").getMatch(0);
        if (containerLink == null) {
            logger.warning("Contailerlink for link " + param.toString() + " for format " + format + " could not be found.");
            return links;
        }
        Browser brc = br.cloneBrowser();
        final String test = Encoding.htmlDecode(containerLink);
        File file = null;
        URLConnectionAdapter con = null;
        try {
            con = brc.openGetConnection(test);
            if (con.getResponseCode() == 200) {
                try {
                    /* does not exist in 09581 */
                    file = org.appwork.utils.Application.getResource("tmp/generalsafelinking/" + JDHash.getSHA1(test) + format);
                } catch (Throwable e) {
                    file = JDUtilities.getResourceFile("tmp/generalsafelinking/" + JDHash.getSHA1(test) + format);
                }
                if (file == null) {
                    return links;
                }
                file.getParentFile().mkdirs();
                file.deleteOnExit();
                brc.downloadConnection(file, con);
                if (file != null && file.exists() && file.length() > 100) {
                    links.addAll(JDUtilities.getController().getContainerLinks(file));
                }
            }
        } catch (final Throwable t) {
            t.printStackTrace();
        } finally {
            try {
                con.disconnect();
            } catch (Throwable e) {
            }
            if (file.exists()) {
                file.delete();
            }
        }
        return links;
    }

    protected ArrayList<DownloadLink> decryptMultipleLinks(final CryptedLink param) throws IOException, PluginException {
        ArrayList<String> cryptedLinks = new ArrayList<String>();
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();

        // TODO: Add handling for offline links/containers

        /* Container handling (if no containers found, use webprotection) */
        if (br.containsHTML("\\.dlc")) {
            decryptedLinks.addAll(loadcontainer(".dlc", param));
            if (!decryptedLinks.isEmpty()) {
                return decryptedLinks;
            }
        }

        if (br.containsHTML("\\.rsdf")) {
            decryptedLinks.addAll(loadcontainer(".rsdf", param));
            if (!decryptedLinks.isEmpty()) {
                return decryptedLinks;
            }
        }

        if (br.containsHTML("\\.ccf")) {
            decryptedLinks.addAll(loadcontainer(".ccf", param));
            if (!decryptedLinks.isEmpty()) {
                return decryptedLinks;
            }
        }

        // TODO: don't think this is needed! confirm -raztoki, not required by keeplinks or safemylink
        /* Webprotection decryption */
        if (br.getRedirectLocation() != null && br.getRedirectLocation().equals(parameter)) {
            br.getPage(parameter);
        }

        for (String[] s : br.getRegex(regexLinks()).getMatches()) {
            for (String[] ss : new Regex(s[0], "<a href=\"(.*?)\"").getMatches()) {
                for (String sss : ss) {
                    if (parameter.equals(sss)) {
                        continue;
                    }
                    cryptedLinks.add(sss);
                }
            }
        }

        for (final String link : cryptedLinks) {
            DownloadLink dl = null;
            if (link.matches(".*" + regexSupportedDomains() + "/d/.+")) {
                Browser br2 = br.cloneBrowser();
                br2.setFollowRedirects(false);
                br2.getPage(link);
                dl = decryptSingleLink(br2);
                if (dl == null) {
                    continue;
                }
            } else {
                dl = createDownloadlink(link);
            }
            decryptedLinks.add(dl);
        }
        return decryptedLinks;
    }

    /**
     * links to external source, and /d/ links. Single link will have different regex than list.
     *
     *
     * @return
     */
    protected String regexLinks() {
        return "<div class=\"links-container result-form\">(.*?)</div>";
    }

    protected String regexSingleLinkLocation() {
        return "location=\"(https?://[^\"]+)";
    }

    protected DownloadLink decryptSingleLink(final Browser br) {
        String finallink = br.getRedirectLocation();
        if (finallink == null) {
            finallink = br.getRegex(regexSingleLinkLocation()).getMatch(0);
        }
        if (finallink == null) {
            logger.warning("Sever issues? continuing...");
            logger.warning("Please confirm via browser, and report any bugs to JDownloader Developement Team. :" + parameter);
        }
        // prevent loop
        if (!parameter.equals(finallink)) {
            return createDownloadlink(finallink);
        } else {
            // Shouldn't happen
            return null;
        }
    }

}
