//jDownloader - Downloadmanager
//Copyright (C) 2015  JD-Team support@jdownloader.org
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

package jd.plugins.decrypter;

import java.util.ArrayList;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.parser.html.Form;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.components.PluginJSonUtils;
import jd.plugins.components.SiteType.SiteTemplate;

import org.jdownloader.captcha.v2.challenge.recaptcha.v2.CaptchaHelperCrawlerPluginRecaptchaV2;
import org.jdownloader.plugins.components.antiDDoSForDecrypt;

/**
 *
 * Eventually variant of OuoIo, see also fas.li and unskip.me
 *
 * @author pspzockerscene
 *
 */
@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "catly.us", "paylinks.xyz" }, urls = { "https?://(?:www\\.)?catly\\.us/[A-Za-z0-9]{4,}", "https?://(?:www\\.)?paylinks\\.xyz/[A-Za-z0-9]{4,}" })
public class CatlyUs extends antiDDoSForDecrypt {

    public CatlyUs(PluginWrapper wrapper) {
        super(wrapper);
    }

    private String parameter = null;
    private String fuid      = null;
    private String slink     = null;

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        set(param.toString());
        if (slink != null) {
            decryptedLinks.add(createDownloadlink(Encoding.urlDecode(slink, false)));
            return decryptedLinks;
        } else if (fuid == null && slink == null) {
            // fuid is just a URL owner identifier! slink value is needed, without it you can't get the end URL!
            decryptedLinks.add(createOfflinelink(parameter));
            return decryptedLinks;
        }
        parameter = "http://" + Browser.getHost(parameter) + "/" + this.fuid;
        getPage(parameter);
        if (br.getHttpConnection().getResponseCode() == 403 || br.getHttpConnection().getResponseCode() == 404 || !this.br.getURL().contains(fuid)) {
            decryptedLinks.add(this.createOfflinelink(parameter));
            return decryptedLinks;
        }
        // form
        Form form = br.getForm(0);
        if (form != null) {
            if (form.containsHTML("g-recaptcha")) {
                final String recaptchaV2Response = new CaptchaHelperCrawlerPluginRecaptchaV2(this, br).getToken();
                form.put("g-recaptcha-response", Encoding.urlEncode(recaptchaV2Response));
            }
            br.submitForm(form);
        }
        form = this.br.getForm(0);
        if (form != null) {
            /* Usually POST to "[...]/links/go" */
            this.br.getHeaders().put("Accept", "application/json, text/javascript, */*; q=0.01");
            this.br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
            this.br.submitForm(form);
        }
        /* E.g. {"status":"success","message":"Go without Earn because anonymous user","url":"http...."} */
        final String finallink = getFinalLink();
        if (finallink == null || !finallink.startsWith("http")) {
            return null;
        }
        decryptedLinks.add(createDownloadlink(finallink));

        return decryptedLinks;
    }

    private String getFinalLink() {
        return PluginJSonUtils.getJsonValue(this.br, "url");
    }

    private void set(final String downloadLink) {
        parameter = downloadLink;
        fuid = new Regex(parameter, ".+/([A-Za-z0-9]{4,})$").getMatch(0);
        slink = new Regex(parameter, "/s/[A-Za-z0-9]{4,}\\?s=((?:http|ftp).+)").getMatch(0);
    }

    public boolean hasCaptcha(DownloadLink link, jd.plugins.Account acc) {
        set(link.getDownloadURL());
        if (slink != null) {
            return false;
        }
        return true;
    }

    public boolean hasAutoCaptcha() {
        return false;
    }

    @Override
    public SiteTemplate siteTemplateType() {
        return SiteTemplate.OuoIoCryptor;
    }

}
