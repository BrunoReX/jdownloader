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

package jd.plugins.decrypter;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.http.Browser.BrowserException;
import jd.nutils.encoding.Encoding;
import jd.nutils.encoding.HTMLEntities;
import jd.parser.Regex;
import jd.parser.html.Form;
import jd.parser.html.InputField;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterException;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.Plugin;
import jd.plugins.components.SiteType.SiteTemplate;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3,

names = { "nifteam.info", "swzz.xyz", "animeforce.org", "link.achanime.net", "sipkur.net", "xxxporn88.com", "otrkeyfinder.com", "solarmovie.is", "xiaomengku.com", "anyt.ml", "fastgo.cu.cc", "hide-my.link", "linkdecode.com", "lienscash.com", "is.gd", "djurl.com", "umhq.net", "madlink.sk", "lnx.lu", "searchonzippy.eu", "sharmota.com", "komp3.net", "hflix.in", "hnzoom.com", "basemp3.ru", "protetorbr.com", "lezlezlez.com", "dwz.cn", "guardlink.org", "q32.ru", "icefilms.info", "adfoc.us", "damasgate.com", "freeonsmash.com", "lnk.co", "myurl.in", "filep.info", "grou.ps", "eskimotube.com", "4p5.com", "href.hu", "migre.me", "altervista.org", "agaleradodownload.com", "songspk.info", "deurl.me", "muzgruz.ru", "zero10.net", "chip.de", "nbanews.us", "1tool.biz", "file4ever.us", "zero10.net", "official.fm", "academicearth.org", "tm-exchange.com", "adiarimore.com", "mafia.to", "view.stern.de",
        "warcraft.ingame.de", "mixconnect.com", "twiturm.com", "ebooksdownloadfree.com", "freebooksearcher.info", "mp3.wp.pl", "gantrack.com" },

urls = { "http://nifteam\\.info/link\\.php\\?file=.+", "https?://(?:www\\.)?swzz\\.xyz/link/[a-zA-Z0-9]{5}/", "http://(?:www\\.)?animeforce\\.org/ds(?:\\d+)?\\.php\\?file=.+", "https?://link\\.achanime\\.net/(short/\\?id=[a-f0-9]{32}|link/\\?[^/]+)", "http://sipkur\\.net/[a-z0-9\\-_]+\\.html", "http://xxxporn88\\.com/video/[a-z0-9\\-_]+\\.[a-z0-9]+\\.html", "https?://otrkeyfinder\\.com/de/go\\-to\\-mirror\\?otrkey=.+", "https?://cinema\\.solarmovie\\.is/link/play/\\d+/?", "https?://(?:www\\.)?xiaomengku\\.com/files\\?id=\\d+", "http://anyt\\.ml/[A-Za-z0-9]+", "https?://(www\\.)?fastgo\\.cu\\.cc/[a-zA-Z0-9]{6}", "http://(www\\.)?hide\\-my\\.link/\\d+", "http://(www\\.)?(linkdecode|fastdecode)\\.com/\\?[a-zA-Z0-9_/\\+\\=\\-%]+", "http://(www\\.)?lienscash\\.com/l/[a-z0-9]+", "http://(www\\.)?is\\.gd/[a-zA-Z0-9]+", "http://djurl\\.com/[A-Za-z0-9]+",
        "http://(www\\.)?umhq\\.net/[A-Z0-9]+/rdf\\.php\\?link=[a-z0-9]+", "http://(www\\.)?(madlink\\.sk|m\\-l\\.sk)/[a-z0-9]+", "http://(www\\.)?(lnx\\.lu|z\\.gs|url\\.fm)/[a-z0-9]+", "http://(www\\.)?searchonzippy\\.eu/out\\.php\\?link=\\d+", "http://(www\\.)?sharmota\\.com/movies/\\d+/\\d+", "http://(www\\.)?komp3\\.net/download/mp3/\\d+/[^<>\"]+\\.html", "http://(www\\.)?hflix\\.in/[A-Za-z0-9]+", "http://(www\\.)?hnzoom\\.com/(\\?[A-Za-z0-9]{20}|folder/[a-zA-Z0-9\\-]{11})", "http://(www\\.)?basemp3\\.ru/music\\-view\\-\\d+\\.html", "http://(www\\.)?protetorbr\\.com/d\\?id=\\d+", "http://(www\\.)?lezlezlez\\.com/mediaswf\\.php\\?type=vid\\&name=[^<>\"/]+\\.flv", "http://(www\\.)?dwz\\.cn/[A-Za-z0-9]+", "http://(www\\.)?guardlink\\.org/[A-Za-z0-9]+", "http://q32\\.ru/\\d+/c/[A-Za-z0-9\\-_]+", "https?://(www\\.)?icefilms\\.info/ip\\.php\\?v=\\d+\\&?",
        "http://(www\\.)?adfoc\\.us/(serve/\\?id=[a-z0-9]+|(?!serve|privacy|terms)[a-z0-9]+)", "http://(www\\.)?damasgate\\.com/redirector\\.php\\?url=.+", "http://(www\\.)?freeonsmash\\.com/redir/[A-Za-z0-9\\=\\+\\/\\.\\-]+", "http://(www\\.)?lnk\\.co/[A-Za-z0-9]+", "http://(www\\.)?protect\\.myurl\\.in/[A-Za-z0-9]+", "http://(www\\.)?filep\\.info/(\\?url=|/)\\d+", "http://(www\\.)?grou\\.ps/[a-z0-9]+/videos/\\d+", "http://(www\\.)?eskimotube\\.com/\\d+\\-.*?\\.html", "http://(www\\.)?4p5\\.com/[a-z0-9]+", "http://href\\.hu/x/[a-zA-Z0-9\\.]+", "http://[\\w\\.]*?migre\\.me/[a-z0-9A-Z]+", "http://[\\w\\.]*?altervista\\.org/\\?i=[0-9a-zA-Z]+", "http://[\\w\\.]*?agaleradodownload\\.com/download.*?\\?.*?//:ptth", "http://[\\w\\.]*?(link\\.songs\\.pk/(popsong|song1|bhangra)\\.php\\?songid=|songspk\\.info/ghazals/download/ghazals\\.php\\?id=)[0-9]+", "http://[\\w\\.]*?deurl\\.me/[0-9A-Z]+",
        "http://[\\w\\.]*?muzgruz\\.ru/music/download/\\d+", "http://[\\w\\.]*?zero10\\.net/\\d+", "http://[\\w\\.]*?chip\\.de/c1_videos/.*?-Video_\\d+\\.html", "http://[\\w\\.]*?nbanews\\.us/\\d+", "http://(www\\.)?1tool\\.biz/\\d+", "http://[\\w\\.]*?file4ever\\.us/\\d+", "http://[\\w\\.]*?zero10\\.net/\\d+", "http://(www\\.)?official\\.fm/track(s)?/[A-Za-z0-9]+", "http://[\\w\\.]*?academicearth\\.org/lectures/.{2,}", "http://[\\w\\.]*?tm-exchange\\.com/(get\\.aspx\\?action=trackgbx|\\?action=trackshow)\\&id=\\d+", "http://[\\w\\.]*?adiarimore\\.com/miralink/[a-z0-9]+", "http://[\\w\\.]*?mafia\\.to/download-[a-z0-9]+\\.cfm", "http://(www\\.)?view\\.stern\\.de/de/(picture|original)/.*?-\\d+\\.html", "http://(www\\.)?warcraft\\.ingame\\.de/downloads/\\?file=\\d+", "http://(www\\.)?mixconnect\\.com/listen/.*?-mid\\d+", "http://(www\\.)?twiturm\\.com/[a-z0-9]+",
        "http://(www\\.)?ebooksdownloadfree\\.com/.*?/.*?\\.html", "http://(www\\.)?freebooksearcher\\.info/downloadbook\\.php\\?id=\\d+", "http://[\\w\\.]*?mp3\\.wp\\.pl/(?!ftp)(p/strefa/artysta/\\d+,utwor,\\d+\\.html|\\?tg=[A-Za-z0-9=]+)", "http://(www\\.)?gantrack\\.com/t/l/\\d+/[A-Za-z0-9]+" },

flags = { 0 })
public class DecrypterForRedirectServicesWithoutDirectRedirects extends antiDDoSForDecrypt {

    public DecrypterForRedirectServicesWithoutDirectRedirects(final PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    protected boolean useRUA() {
        return true;
    }

    @Override
    public ArrayList<DownloadLink> decryptIt(final CryptedLink param, final ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        String parameter = param.toString();
        br = new Browser();
        br.setFollowRedirects(false);
        br.setReadTimeout(60 * 1000);
        boolean dh = false;
        boolean offline = false;
        String finallink = null;
        String finalfilename = null;
        /* Some links don't have to be accessed (here) */
        try {
            if (!new Regex(parameter, "1tool\\.biz|file4ever\\.us|tm-exchange\\.com/|mixconnect\\.com/|dwz\\.cn/|icefilms\\.info/|linkdecode\\.com|fastdecode\\.com/|is\\.gd/|swzz\\.xyz/|animeforce\\.org/|nifteam\\.info/").matches()) {
                br.getPage(parameter);
            }
            if (parameter.contains("link.songs.pk/") || parameter.contains("songspk.info/ghazals/download/ghazals.php?id=")) {
                finallink = br.getRedirectLocation();
                dh = true;
            } else if (parameter.contains("deurl.me/")) {
                finallink = br.getRegex("<i><small>(http://.*?)</small></i>").getMatch(0);
                if (finallink == null) {
                    finallink = br.getRegex("- <a href=\"(http://.*?)\">Click here to visit this").getMatch(0);
                }
            } else if (parameter.contains("muzgruz.ru/music/")) {
                finallink = parameter;
                dh = true;
            } else if (parameter.contains("chip.de/c1_videos")) {
                finallink = br.getRegex("id=\"player\" href=\"(http://.*?\\.flv)\"").getMatch(0);
                if (finallink == null) {
                    finallink = br.getRegex("\"(http://video\\.chip\\.de/\\d+/.*?.flv)\"").getMatch(0);
                }
                dh = true;
            } else if (parameter.contains("nbanews.us/")) {
                br.setFollowRedirects(true);
                final String id = new Regex(parameter, "nbanews\\.us/(\\d+)").getMatch(0);
                br.getPage("http://www.nbanews.us/m1.php?id=" + id);
                finallink = br.getRegex("NewWindow\\('(.*?)'").getMatch(0);
            } else if (parameter.contains("1tool.biz/")) {
                final String id = new Regex(parameter, "1tool\\.biz/(\\d+)").getMatch(0);
                br.getPage("http://1tool.biz/2.php?id=" + id);
                finallink = br.getRegex("onclick=\"NewWindow\\('(.*?)','").getMatch(0);
            } else if (parameter.contains("agaleradodownload.com")) {
                final String url = new Regex(parameter, "download.*?\\?(.+)").getMatch(0);
                if (url != null) {
                    final StringBuilder sb = new StringBuilder("");
                    sb.append(url);
                    sb.reverse();
                    finallink = sb.toString();
                }
            } else if (parameter.contains("file4ever.us")) {
                final String damnID = new Regex(parameter, "/(\\d+)$").getMatch(0);
                br.getPage(parameter.replace(damnID, "") + "file.php?id=" + damnID);
                finallink = br.getRegex("<td width=\"70%\">[\t\n\r ]+<a href=\"(.*?)\"").getMatch(0);
            } else if (parameter.contains("zero10.net/")) {
                final String damnID = new Regex(parameter, "(\\d+)$").getMatch(0);
                br.postPage("http://zero10.net/link.php?id=" + damnID, "s=1");
                finallink = br.getRegex("onClick=.*?\\('(http:.*?)'").getMatch(0);
            } else if (parameter.contains("official.fm/")) {
                if (br.getRedirectLocation() != null) {
                    br.getPage(br.getRedirectLocation());
                }
                final String redirect = br.getRegex(">You are being <a href=\"(http://[^<>\"]*?)\">redirected").getMatch(0);
                if (redirect != null) {
                    br.getPage(redirect);
                }
                if (br.getHttpConnection().getResponseCode() == 404) {
                    logger.info("Link offline: " + parameter);
                    return decryptedLinks;
                }
                finalfilename = br.getRegex("<meta property=\"og:title\" content=\"(.*?)\"/>").getMatch(0);
                if (finalfilename == null) {
                    finalfilename = br.getRegex("<title>(.*?) on Official\\.fm</title>").getMatch(0);
                }
                if (finalfilename != null) {
                    finalfilename = Encoding.htmlDecode(finalfilename.trim()) + ".mp3";
                }
                finallink = br.getRegex("mp3_url\\&quot;:\\&quot;(http://cdn\\.official\\.fm/[^<>\"]*?)\\&quot;").getMatch(0);
                if (finallink == null) {
                    br.setFollowRedirects(true);
                    br.getPage(parameter + ".xspf?ll_header=yes");
                    finallink = br.getRegex("\"(http://cdn\\.official\\.fm/mp3s/\\d+/\\d+\\.mp3)").getMatch(0);
                }
                dh = true;
            } else if (parameter.contains("academicearth.org/")) {
                if (!(br.getRedirectLocation() != null && br.getRedirectLocation().contains("users/login"))) {
                    if (br.containsHTML(">Looks like the Internet may require a little disciplinary action")) {
                        offline = true;
                    }
                    finallink = br.getRegex("flashVars\\.flvURL = \"(.*?)\"").getMatch(0);
                    if (finallink == null) {
                        finallink = br.getRegex("<div><embed src=\"(.*?)\"").getMatch(0);
                    }
                    if (finallink != null) {
                        if (!finallink.contains("blip.tv") && !finallink.contains("youtube")) {
                            throw new DecrypterException("Found unsupported link in link: " + parameter);
                        }
                        if (finallink.contains("blip.tv/")) {
                            br.getPage(finallink);
                            finallink = br.getRedirectLocation();
                            dh = true;
                        }
                    }
                } else {
                    throw new DecrypterException("Login required to download link: " + parameter);
                }
            } else if (parameter.contains("tm-exchange.com/")) {
                finallink = "directhttp://" + parameter.replace("?action=trackshow", "get.aspx?action=trackgbx");
            } else if (parameter.contains("adiarimore.com/")) {
                finallink = br.getRegex("<iframe src=\"(.*?)\"").getMatch(0);
            } else if (parameter.contains("altervista.org") || parameter.contains("migre.me")) {
                finallink = br.getRedirectLocation();
            } else if (parameter.contains("mafia.to/download")) {
                br.getPage(parameter.replace("download-", "dl-"));
                finallink = br.getRedirectLocation();
            } else if (parameter.contains("view.stern.de/de/")) {
                br.setFollowRedirects(true);
                br.getPage(parameter.replace("/picture/", "/original/"));
                if (br.containsHTML("/erotikfilter/")) {
                    br.postPage(br.getURL(), "savefilter=1&referer=" + Encoding.urlEncode(parameter.replace("/original/", "/picture/")) + "%3Fr%3D1%26g%3Dall");
                    br.getPage(parameter.replace("/picture/", "/original/"));
                }
                finallink = br.getRegex("<div class=\"ImgBig\" style=\"width:\\d+px\">[\t\n\r ]+<img src=\"(http://.*?)\"").getMatch(0);
                if (finallink == null) {
                    finallink = br.getRegex("\"(http://view\\.stern\\.de/de/original/([a-z0-9]+/)?\\d+/.*?\\..{3,4})\"").getMatch(0);
                }
                dh = true;
            } else if (parameter.contains("warcraft.ingame.de/downloads/")) {
                finallink = br.getRegex("\"(http://warcraft\\.ingame\\.de/downloads/\\?rfid=\\d+)\"").getMatch(0);
                if (finallink == null) {
                    finallink = br.getRegex("class=\"download\"><a href=\"(http://.*?)\"").getMatch(0);
                }
                dh = true;
            } else if (parameter.contains("mixconnect.com/")) {
                final String fid = new Regex(parameter, "mixconnect\\.com/listen/.*?\\-mid(\\d+)").getMatch(0);
                try {
                    br.getPage("http://mixconnect.com/download/mixtape/id/" + fid);
                } catch (final BrowserException e) {
                    logger.info("Link offline: " + parameter);
                    return decryptedLinks;
                }
                if (!offline) {
                    finallink = br.getRedirectLocation();
                }
                if (finallink == null) {
                    br.getPage(parameter);
                    finallink = br.getRegex("mp3:\"(http://mixconnect\\.com/[^<>\"]*?)\"").getMatch(0);
                }
                if (br.containsHTML("The requested page does not exist")) {
                    logger.info("Link offline: " + parameter);
                    return decryptedLinks;
                }
            } else if (parameter.contains("twiturm.com/")) {
                finallink = br.getRegex("<div id=\"player\">[\r\t\n ]+<a href=\"(http://.*?)\">").getMatch(0);
                if (finallink == null) {
                    finallink = br.getRegex("\"(http://s\\d+\\.amazonaws\\.com/twiturm_prod/.*?)\"").getMatch(0);
                }
                dh = true;
                finalfilename = br.getRegex("<title>Twiturm\\.com - (.*?)</title>").getMatch(0);
                if (finalfilename != null) {
                    finalfilename += ".mp3";
                }
            } else if (parameter.contains("ebooksdownloadfree.com/")) {
                if (br.containsHTML("the page you requested is not located here<")) {
                    logger.info("Link offline: " + parameter);
                    return decryptedLinks;
                }
                finallink = br.getRegex("<strong>Link:</strong>\\&nbsp; </span><span class=\"linkcat\">[\t\n\r ]+<a style=\"font-size:16px\" href=\"(.*?)\"").getMatch(0);
                if (finallink == null) {
                    finallink = br.getRegex("\"(http://freebooksearcher\\.info/downloadbook\\.php\\?id=\\d+)\"").getMatch(0);
                }
            } else if (parameter.contains("freebooksearcher.info/")) {
                finallink = br.getRegex("<p><a href=\"(.*?)\"").getMatch(0);
            } else if (parameter.contains("mp3.wp.pl/")) {
                if (br.getRedirectLocation() != null) {
                    br.getPage(br.getRedirectLocation());
                }
                String ID = br.getRegex("name=\"mp3artist\" noresize src=\"http://mp3\\.wp\\.pl//p/strefa/artysta/\\d+,utwor,(\\d+)\\.html\"").getMatch(0);
                if (ID == null) {
                    ID = new Regex(parameter, "mp3\\.wp\\.pl/p/strefa/artysta/\\d+,utwor,(\\d+)\\.html").getMatch(0);
                }
                if (ID == null) {
                    return null;
                }
                br.getPage("http://mp3.wp.pl/i/sciagnij?id=" + ID + "&jakosc=hifi&streaming=0");
                finallink = br.getRedirectLocation();
                dh = true;
            } else if (parameter.contains("4p5.com/")) {
                finallink = br.getRegex("<iframe id=\"frame\" src=\"(.*?)\"").getMatch(0);
                if (finallink == null) {
                    finallink = br.getRegex("<div id=\"removeFrame\">\\&raquo; <a href=\"(.*?)\"").getMatch(0);
                }
            } else if (parameter.contains("eskimotube.com/")) {
                finalfilename = br.getRegex("<TITLE>EskimoTube\\.com \\- Streaming Videos of Unknown \\-([^<>\"]*?)\\- Pornstars And Centerfolds\\.</title>").getMatch(0);
                br.getPage("http://www.eskimotube.com/playlist-live.php?id=" + new Regex(parameter, "eskimotube\\.com/(\\d+)").getMatch(0));
                finallink = br.getRegex("<file>(http://[^<>\"]*?)</file>").getMatch(0);
                if (finalfilename == null) {
                    finalfilename = br.getRegex("<TITLE>EskimoTube\\.com - Streaming Videos of (.*?) \\- ").getMatch(0);
                }
                if (finalfilename != null && finallink != null) {
                    finalfilename += "." + finallink.substring(finallink.length() - 4, finallink.length());
                }
                dh = true;
            } else if (parameter.contains("grou.ps/")) {
                finallink = br.getRegex("name=\"movie\" value=\"(http://.*?)\"").getMatch(0);
                if (finallink == null) {
                    finallink = br.getRegex("<embed src=\"(http://.*?)\"").getMatch(0);
                }
                if (finallink == null) {
                    finallink = parameter.replace("grou.ps", "decryptedgrou.ps");
                }
            } else if (parameter.contains("filep.info/")) {
                parameter = parameter.replace("filep.info//", "filep.info/?url=");
                br.postPage(parameter, "abod=1");
                finallink = br.getRegex("onclick=\"NewWindow\\(\\'(.*?)\\'").getMatch(0);
            } else if (parameter.contains("protect.myurl.in/")) {
                finallink = br.getRegex("<iframe scrolling=\"(yes|no)\" src=\"(.*?)\"").getMatch(1);
            } else if (parameter.contains("lnk.co/")) {
                finallink = br.getRedirectLocation();
                if (finallink == null) {
                    finallink = br.getRegex("window\\.top\\.location = \\'srh\\.php\\?u=(http://[^<>\"]*?)\\'").getMatch(0);
                }
                if (finallink == null) {
                    finallink = br.getRegex("style=\\'pointer\\-events: none;\\' id=\\'dest\\' src=\"(http://[^<>\"]*?)\"").getMatch(0);
                }
                if (finallink == null) {
                    finallink = br.getRegex("linkurl.*?counter.*?linkurl' href=\"(http://[^<>\"]*?)\"").getMatch(0);
                }
            } else if (parameter.contains("freeonsmash.com/")) {
                if (finallink == null && br.getRedirectLocation() != null) {
                    finallink = br.getRedirectLocation();
                }
                if (finallink == null) {
                    finallink = br.getRegex("<meta http\\-equiv=\"Refresh\" content=\"\\d+; URL=(.*?)\"").getMatch(0);
                    if (finallink == null) {
                        finallink = br.getRegex("<input id=\\'bounce\\-page\\-url\\' type=\\'text\\' value=\\'(.*?)\\'").getMatch(0);
                    }
                }
            } else if (parameter.contains("damasgate.com/redirector")) {
                finallink = br.getRegex("align=\"center\"><a href=\"(.*?)\"").getMatch(0);
            } else if (parameter.contains("gantrack.com/")) {
                if (br.getRedirectLocation() != null) {
                    br.getPage(br.getRedirectLocation());
                    if (br.getRedirectLocation() != null) {
                        br.getPage(br.getRedirectLocation());
                    }
                }
                finallink = br.getRegex("http\\-equiv=\"refresh\" content=\"\\d+;URL=(.*?)\">").getMatch(0);
            } else if (parameter.contains("adfoc.us/")) {
                String id = new Regex(parameter, ".us/(.+)").getMatch(0);
                if ("forum".equalsIgnoreCase(id) || "support".equalsIgnoreCase(id) || "self".equalsIgnoreCase(id) || "user".equalsIgnoreCase(id) || "payout".equalsIgnoreCase(id) || "api".equalsIgnoreCase(id) || "js".equalsIgnoreCase(id) || "ajax".equalsIgnoreCase(id) || "faq".equalsIgnoreCase(id) || "1How".equalsIgnoreCase(id) || "tickets".equalsIgnoreCase(id) || "advertise".equalsIgnoreCase(id)) {
                    logger.info("Invalid link: " + parameter);
                    return decryptedLinks;
                }
                br.setFollowRedirects(true);
                br.getPage(parameter);
                br.setFollowRedirects(false);
                if (br.containsHTML(">403 Forbidden<")) {
                    logger.info("Link offline: " + parameter);
                    return decryptedLinks;
                } else if (br.containsHTML("No htmlCode read")) {
                    logger.info("Link offline (server error): " + parameter);
                    return decryptedLinks;
                } else if (br.getURL().equals("http://adfoc.us/")) {
                    logger.info("Link offline: " + parameter);
                    return decryptedLinks;
                }
                String click = br.getRegex("var click_url = \"(https?://[^\"]+)").getMatch(0);
                if (click != null && click.equals(parameter)) {
                    click = null;
                }
                if (click == null) {
                    click = br.getRegex("(http://adfoc\\.us/serve/click/\\?id=[a-z0-9]+\\&servehash=[a-z0-9]+\\&timestamp=\\d+)").getMatch(0);
                }
                if (click != null && click.contains("adfoc.us/") && !click.contains(id)) {
                    /* adfoc link leads to another adfoc link */
                    decryptedLinks.add(createDownloadlink(click));
                    return decryptedLinks;
                } else if (click != null && click.contains("adfoc.us/")) {
                    br.getHeaders().put("Referer", parameter);
                    br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
                    br.getPage(HTMLEntities.unhtmlentities(click));
                    if (br.getRedirectLocation() != null && !br.getRedirectLocation().matches("http://adfoc.us/")) {
                        finallink = br.getRedirectLocation();
                    }
                } else {
                    finallink = click;
                }
            } else if (parameter.contains("gabber.od.ua/")) {
                finallink = br.getRegex("Download link:<br><br><br><a href=\\'([^<>\"\\']+)\\'").getMatch(0);
            } else if (parameter.contains("icefilms.info/")) {
                // they use cloudflare!
                getPage(parameter);
                if (br.getRedirectLocation() != null) {
                    getPage(br.getRedirectLocation());
                }
                String nextUrl = br.getRegex("value=\'<iframe src=\"(http.*?)\"").getMatch(0);
                if (nextUrl == null) {
                    nextUrl = br.getRegex("<iframe id=\"videoframe\" src=\"(.*?)\"").getMatch(0);
                }
                if (nextUrl == null) {
                    nextUrl = br.getRegex("\"(/membersonly/components/com_iceplayer/video\\.php[^<>\"]*?)\"").getMatch(0);
                }
                if (nextUrl == null) {
                    return null;
                }
                getPage(nextUrl);
                if (br.containsHTML(">no sources<")) {
                    logger.info("Link offline: " + parameter);
                    return decryptedLinks;
                }
                final String sec = br.getRegex("f\\.lastChild\\.value=\"(\\w+)\"").getMatch(0);
                final String t = br.getRegex("&t=(\\d+)\"").getMatch(0);
                final String url = br.getRegex("\"POST\",\"(.*?)\"").getMatch(0);
                if (sec == null || t == null || url == null) {
                    return null;
                }
                String[] results = br.getRegex("(<a rel=\\d+.*?</a>)").getColumn(0);
                if (results == null || results.length == 0) {
                    return null;
                }
                final String ss = br.getRegex("var s\\s*=\\s*(\\d+)").getMatch(0);
                int s = ss != null ? (Integer.parseInt(ss) + 1) : 10000;
                final String mm = br.getRegex(",\\s*m=\\s*(\\d+)").getMatch(0);
                final int m = mm != null ? Integer.parseInt(mm) : 10000;
                for (String result : results) {
                    final String id = new Regex(result, "onclick=\'go\\((\\d+)").getMatch(0);
                    // continue when we can't find go value!
                    if (id == null) {
                        continue;
                    }
                    Browser br2 = br.cloneBrowser();
                    br2.getHeaders().put("Accept", "*/*");
                    postPage(br2, url.substring(0, url.indexOf("?")) + "?s=" + id + "&t=" + t, "&id=" + id + "&s=" + ++s + "&iqs=&url=&m=" + (m + 1 + new Random().nextInt(1000)) + "&cap= &sec=" + sec + "&t=" + t);
                    final String link = Encoding.htmlDecode(br2.getRegex("url=(.*?)$").getMatch(0));
                    // continues on link failure.. not all mirrors are live.
                    if (link != null) {
                        decryptedLinks.add(createDownloadlink(link));
                    }
                    // small pause to prevent high loads
                    Thread.sleep(500);
                }
                return decryptedLinks;
            } else if (parameter.contains("q32.ru/")) {
                final Form dlForm = br.getForm(0);
                if (dlForm != null) {
                    br.submitForm(dlForm);
                    finallink = br.getRegex("http\\-equiv=\"Refresh\" content=\"\\d+; URL=(.*?)\"").getMatch(0);
                }
            } else if (parameter.contains("guardlink.org/")) {
                finallink = br.getRegex("<iframe src=\"([^<>\"\\']+)\"").getMatch(0);
                if (finallink != null) {
                    finallink = Encoding.deepHtmlDecode(finallink).replace("&#114", "r");
                }
            } else if (parameter.contains("dwz.cn/")) {
                // It's a normal redirector but needs special handling, maybe
                // because of browser bug
                br.setFollowRedirects(true);
                br.getPage(parameter);
                if (!br.getURL().contains("dwz.cn/")) {
                    finallink = br.getURL();
                }
            } else if (parameter.contains("lezlezlez.com/")) {
                finallink = parameter.replace("lezlezlez.com/mediaswf.php?type=vid&name=", "lezlezlez.com/xmoov.php?file=vidz/") + "&start=true";
                dh = true;
                finalfilename = new Regex(parameter, "\\?type=vid\\&name=(.+)").getMatch(0);
            } else if (parameter.contains("protetorbr.com/")) {
                if (br.getRedirectLocation() != null) {
                    br.getPage(br.getRedirectLocation());
                }
                final Map<String, List<String>> rH = br.getRequest().getResponseHeaders();
                final Set<String> keys = rH.keySet();
                for (final String s : keys) {
                    if (!"refresh".equalsIgnoreCase(s)) {
                        continue;
                    }
                    for (final String ss : rH.get(s)) {
                        finallink = new Regex(ss, "URL=(.*?)$").getMatch(0);
                    }
                }
            } else if (parameter.contains("hnzoom.com/")) {
                if (parameter.matches(".+hnzoom\\.com/folder/[a-zA-Z0-9\\-]{11}")) {
                    String uid = new Regex(parameter, "/folder/([a-zA-Z0-9\\-]{11})").getMatch(0);
                    br.getPage(parameter);
                    String[] links = br.getRegex("href=\"([^\"]+/out/" + uid + "/\\d+)").getColumn(0);
                    if (links != null && links.length != 0) {
                        for (String link : links) {
                            Browser br2 = br.cloneBrowser();
                            br2.getPage(link);
                            // jd2 supports within refresh
                            String fl = br2.getRedirectLocation();
                            if (fl == null) {
                                fl = br2.getRegex("url=([^'\"\n\t]+);?").getMatch(0);
                            }
                            if (fl != null) {
                                decryptedLinks.add(createDownloadlink(fl));
                            }
                        }
                    }
                    if (links == null || links.length == 0 || decryptedLinks.isEmpty()) {
                        logger.warning("Possible plugin error! Please confirm link within your web browser. If broken please report to JDownloader Development Team : " + parameter);
                        return null;
                    }
                    return decryptedLinks;
                } else {
                    // 'protection service' to final mediafire direct link
                    br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
                    br.postPage(new Regex(parameter, "(https?://[^/]+)").getMatch(0) + "/get.php?do=getlink", "url=" + new Regex(parameter, "https?://[^/]+/\\?(.+)").getMatch(0) + "&pass=");
                    if (br.containsHTML("\"status\":2") && br.containsHTML("\"link\":null")) {
                        // requires password
                        for (int i = 0; i <= 3; i++) {
                            final String pass = Plugin.getUserInput("Password Required", param);
                            if (pass == null) {
                                return decryptedLinks;
                            } else {
                                br.postPage(new Regex(parameter, "(https?://[^/]+)").getMatch(0) + "/get.php?do=getlink", "url=" + new Regex(parameter, "https?://[^/]+/\\?(.+)").getMatch(0) + "&pass=" + pass);
                            }
                            if (br.containsHTML("\"status\":2") && br.containsHTML("\"link\":null")) {
                                continue;
                            } else {
                                break;
                            }
                        }
                    }
                    if (br.containsHTML("\"status\":4")) {
                        logger.info("Link offline: " + parameter);
                        return decryptedLinks;
                    }
                    finallink = br.getRegex("\"link\":\"(http:[^<>\"]*?)\"").getMatch(0);
                    if (finallink != null) {
                        finallink = finallink.replace("\\", "");
                        final String mediafireID = new Regex(finallink, "http://[^/]*?/[^/]*?/([^/]*?)/").getMatch(0);
                        if (mediafireID != null) {
                            decryptedLinks.add(createDownloadlink("http://www.mediafire.com/?" + mediafireID));
                            return decryptedLinks;
                        }
                    }
                }
            } else if (parameter.contains("basemp3.ru/")) {
                finallink = "http://basemp3.ru/download.php?id=" + new Regex(parameter, "(\\d+)\\.html$").getMatch(0);
                finalfilename = br.getRegex("<title>([^<>\"]*?) скачать бесплатно mp3").getMatch(0);
                if (finalfilename == null) {
                    finalfilename = br.getRegex("href=\"download\\.php\\?id=\\d+\" title=\"([^<>\"]*?) скачать бесплатно mp3\"").getMatch(0);
                }
                if (finalfilename != null) {
                    finalfilename = Encoding.htmlDecode(finalfilename.trim()) + ".mp3";
                }
                dh = true;
            } else if (parameter.contains("hflix.in/")) {
                finallink = br.getRedirectLocation();
                if (finallink == null) {
                    finallink = br.getRegex("<a id=\"yourls\\-once\" href=\"(http://[^<>\"]*?)\"").getMatch(0);
                }
            } else if (parameter.contains("komp3.net/")) {
                br.getPage(parameter.replace("/download/mp3/", "/download/mp3/da/"));
                finallink = br.getRedirectLocation();
            } else if (parameter.contains("sharmota.com/")) {
                br.setFollowRedirects(true);
                br.getPage(parameter);
                finalfilename = br.getRegex("<h3><b>(.*?)</b></h3>").getMatch(0);
                if (finalfilename != null) {
                    finalfilename = finalfilename.replaceAll("[\r\n]+", "") + ".flv";
                }
                finallink = "http://videos.sharmota.com/flv888/" + new Regex(parameter, "movies/\\d+/(\\d+)").getMatch(0) + ".flv";
            } else if (parameter.contains("searchonzippy.eu/")) {
                finallink = br.getRegex("setTimeout\\(\"document\\.location\\.href=\\'(http[^<>\"]*?)\\'\"").getMatch(0);
            } else if (parameter.contains("lnx.lu/") || parameter.contains("z.gs/") || parameter.contains("url.fm/")) {
                if (parameter.matches("http://(www\\.)?(lnx\\.lu|z\\.gs|url\\.fm)/(dmca|privacy|terms|advertising|contact|rates|faq)")) {
                    logger.info("Link invalid: " + parameter);
                    return decryptedLinks;
                }
                for (int i = 1; i <= 3; i++) {
                    if (br.getRedirectLocation() != null) {
                        if (br.getRedirectLocation().matches("http://(www\\.)?(lnx\\.lu|z\\.gs|url\\.fm)/.+")) {
                            br.getPage(br.getRedirectLocation());
                            continue;
                        } else {
                            finallink = br.getRedirectLocation();
                            break;
                        }
                    }
                    break;
                }
                if (finallink == null) {
                    if (br.containsHTML("No htmlCode read")) {
                        logger.info("Link offline: " + parameter);
                        return decryptedLinks;
                    }
                    if (br.containsHTML("id=\"shrinkfield\">")) {
                        logger.info("Link offline: " + parameter);
                        return decryptedLinks;
                    }
                    if (br.containsHTML("<title>Index of")) {
                        logger.info("Link offline: " + parameter);
                        return decryptedLinks;
                    }
                    finallink = br.getRegex("\"(/\\?click=[^<>\"/]*?)\"").getMatch(0);
                    if (finallink != null) {
                        br.getPage("http://lnx.lu" + finallink);
                        if (br.containsHTML("No htmlCode read")) {
                            logger.info("Link offline: " + parameter);
                            return decryptedLinks;
                        }
                        finallink = br.getRedirectLocation();
                    }
                }
            } else if (parameter.contains("madlink.sk/") || parameter.contains("m-l.sk/")) {
                br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
                br.postPage("http://madlink.sk/ajax/check_redirect.php", "link=" + new Regex(parameter, "([a-z0-9]+)$").getMatch(0));
                finallink = br.toString();
                if (finallink == null || finallink.length() > 500 || !finallink.startsWith("http")) {
                    finallink = null;
                }
            } else if (parameter.contains("umhq.net/")) {
                finallink = br.getRegex("class=\"dl\" href=\"(http[^<>\"]*?)\"").getMatch(0);
            } else if (parameter.contains("djurl.com/")) {
                finallink = br.getRedirectLocation();
                if (finallink == null) {
                    finallink = br.getRegex("var finalStr = \"(.*?)\";").getMatch(0);
                }
                if (finallink != null) {
                    finallink = Encoding.Base64Decode(finallink);
                } else {
                    finallink = br.getRegex("var finalLink = \"(.*?)\";").getMatch(0);
                    if (finallink == null) {
                        finallink = br.getRegex("<a href\\s*=\\s*('|\")([^\r\n]*/\\?r=.*?)\\1[^>]*>Close</a>").getMatch(1);
                    }
                }
                if (finallink == null && (br.containsHTML("<title>DJURL\\.COM \\- The DJ Link Shortener</title>") || br.getHttpConnection().getResponseCode() == 404)) {
                    offline = true;
                }
            } else if (parameter.contains("is.gd/")) {
                getPage(parameter);
                finallink = br.getRedirectLocation();
                if (finallink == null || finallink.contains("is.gd/")) {
                    finallink = br.getRegex("the destination shown: \\-<br /><a href=\"(http[^<>\"]*?)\"").getMatch(0);
                }
                if (br.containsHTML(">Sorry, we couldn't find the shortened URL you requested") || br.containsHTML(">Link Disabled<") || parameter.equals("http://is.gd") || parameter.equals("http://www.is.gd")) {
                    offline = true;
                }
            } else if (parameter.contains("lienscash.com/")) {
                br.getPage(parameter);
                finallink = br.getRegex("class=\"redirect\" id=\"(.*?)\"").getMatch(0);
                if (finallink == null) {
                    finallink = br.getRegex("id=\"redir_btn\" href=\"(https?://[^<>\"]*?)\"").getMatch(0);
                }
            } else if (parameter.contains("linkdecode.com/") || parameter.contains("fastdecode.com/")) {
                // linkdecode typically returns results to fastdecode. best to return back and run again incase it doesn't or ppl share
                // fastdecode links instead.
                parameter = Encoding.urlDecode(parameter, false);
                br.getPage(parameter);
                finallink = br.getRegex("class=\"Visit_Link\"[^\r\n]+onclick=\"window\\.open\\(('|\")(.*?)\\1").getMatch(1);
            } else if (parameter.contains("hide-my.link/")) {
                finallink = br.getRegex("search\\.substring\\(\\d+\\)\\) : \\'(http[^<>\"]*?)\\';").getMatch(0);
            } else if (parameter.contains("fastgo.cu.cc/")) {
                for (final Form f : br.getForms()) {
                    for (final InputField i : f.getInputFields()) {
                        if ("hidden".equalsIgnoreCase(i.getKey()) && "hidden".equalsIgnoreCase(i.getType()) && i.getValue() != null && i.getValue().matches("\\d+")) {
                            br.submitForm(f);
                            finallink = br.getRegex("window.location.replace\\(('|\")(.*?)\\1\\)").getMatch(1);
                            break;
                        }
                    }
                    if (!inValidate(finallink)) {
                        break;
                    }
                }
            } else if (parameter.contains("anyt.ml/")) {
                if (this.br.getHttpConnection().getResponseCode() == 404) {
                    offline = true;
                }
                finallink = getHttpMetaRefreshURL();
            } else if (parameter.contains("xiaomengku.com/")) {
                final String linkid = new Regex(parameter, "(\\d+)$").getMatch(0);
                this.br.getPage("http://www.xiaomengku.com/files/download?id=" + linkid);
                finallink = this.br.getRedirectLocation();
                if (finallink == null) {
                    offline = true;
                }
            } else if (parameter.contains("solarmovie.is/")) {
                this.br.getPage(parameter);
                if (this.br.getHttpConnection().getResponseCode() == 404) {
                    offline = true;
                }
                final String html = this.br.getRegex("<div class=\"thirdPartyEmbContainer\"(.*?)class=\"hidden\"").getMatch(0);
                if (html != null) {
                    finallink = new Regex(html, "(?:SRC|src)=\"(http[^<>\"]*?)\"").getMatch(0);
                }
            } else if (parameter.contains("otrkeyfinder.com/")) {
                finallink = br.getRegex("id=\"mirror\\-link\" href=\"(http[^<>\"]*?)\"").getMatch(0);
                if (finallink != null) {
                    finallink = Encoding.htmlDecode(finallink);
                } else {
                    offline = true;
                }
            } else if (parameter.contains("xxxporn88.com/")) {
                finallink = this.br.getRegex("loading_player\\(\\'([^<>\"]*?)\\'").getMatch(0);
                if (finallink != null) {
                    finallink = Encoding.Base64Decode(finallink);
                }
            } else if (parameter.contains("sipkur.net/")) {
                finallink = this.br.getRegex("onclick=\"window\\.open\\(\\'(http[^<>\"]*?)\\'").getMatch(0);
            } else if (parameter.contains("link.achanime.net/")) {
                finallink = this.br.getRedirectLocation();
                if (finallink == null) {
                    finallink = this.br.getRegex("id=\"theTarget\">\\d+</i> \\| (http[^<>\"]*?)</button>").getMatch(0);
                }
                if (finallink == null) {
                    finallink = this.br.getRegex("\\{window\\.location = \\'(http[^<>\"]*?)\\'").getMatch(0);
                }
            } else if (parameter.contains("animeforce.org/")) {
                // use cloudflare
                getPage(parameter);
                if (br.getRedirectLocation() != null) {
                    br.setFollowRedirects(true);
                    getPage(br.getRedirectLocation());
                }
                finallink = br.getRegex("href=\"(http[^<>\"]*?)\" target=\"_blank\">Download</a>").getMatch(0);
                if (finallink == null) {
                    if (br.containsHTML(">Il file che stai provando a scaricare non esiste,<br>oppure deve essere ancora caricato<|>o semplicemente hai cliccato/digitato un link sbagliato\\s*<")) {
                        return decryptedLinks;
                    }
                }
            } else if (parameter.contains("swzz.xyz/")) {
                // cloudflare
                getPage(parameter);
                if (br.containsHTML("<em>Questo Link Non è ancora attivo\\.\\.\\.riprova tra qualche istante!<em>")) {
                    logger.warning("Retry in a few minutes: " + parameter);
                    return decryptedLinks;
                }
                // within packed
                final String js = br.getRegex("eval\\((function\\(p,a,c,k,e,d\\)[^\r\n]+\\))\\)").getMatch(0);
                final ScriptEngineManager manager = jd.plugins.hoster.DummyScriptEnginePlugin.getScriptEngineManager(null);
                final ScriptEngine engine = manager.getEngineByName("javascript");
                String result = null;
                try {
                    engine.eval("var res = " + js + ";");
                    result = (String) engine.get("res");
                } catch (final Exception e) {
                    e.printStackTrace();
                }
                finallink = new Regex(result, "var link\\s*=\\s*(\"|')(.*?)\\1").getMatch(1);
            } else if (parameter.contains("nifteam.info/")) {
                dh = true;
                finallink = "http://download.nifteam.info/Download/Anime/" + new Regex(parameter, "\\?file=(.+)").getMatch(0);
            }
        } catch (final SocketTimeoutException e) {
            logger.info("Link offline (timeout): " + parameter);
            return decryptedLinks;
        } catch (final BrowserException e) {
            logger.info("BrowserException occured - timeout or server error: " + parameter);
            return decryptedLinks;
        }
        if ("http://adfoc.us/".equals(br.getRedirectLocation())) {
            offline = true;
        }
        if (offline) {
            logger.info("Link offline: " + parameter);
            if (decryptedLinks.isEmpty()) {
                decryptedLinks.add(this.createOfflinelink(parameter));
            }
            return decryptedLinks;
        }
        if (finallink == null) {
            logger.info("DecrypterForRedirectServicesWithoutDirectRedirects says \"Out of date\" for link: " + parameter);
            return null;
        }
        if (dh) {
            finallink = "directhttp://" + finallink;
        }
        final DownloadLink dl = createDownloadlink(finallink);
        if (finalfilename != null) {
            dl.setFinalFileName(finalfilename);
        }
        decryptedLinks.add(dl);

        return decryptedLinks;
    }

    private String getHttpMetaRefreshURL() {
        return this.br.getRegex("http\\-equiv=\"refresh\" content=\"\\d+; url=(http[^<>\"]*?)\"").getMatch(0);
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }

    @Override
    public String[] siteSupportedNames() {
        if ("songspk.info".equals(this.getHost())) {
            return new String[] { "link.songs.pk", "songspk.info" };
        }
        return super.siteSupportedNames();
    }

    @Override
    public SiteTemplate siteTemplateType() {
        return null; // DecrypterForRedirectServicesWithoutDirectRedirects
    }

}