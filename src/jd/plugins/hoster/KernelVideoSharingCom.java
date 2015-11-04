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
import java.util.regex.Pattern;

import jd.PluginWrapper;
import jd.http.Browser.BrowserException;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.components.SiteType.SiteTemplate;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "kernel-video-sharing.com", "alotporn.com", "alphaporno.com", "updatetube.com", "thenewporn.com", "pinkrod.com", "hotshame.com", "tubewolf.com", "voyeurhit.com", "yourlust.com", "pornicom.com", "pervclips.com", "wankoz.com", "tubecup.com", "pornalized.com", "myxvids.com", "hellporno.com", "h2porn.com", "befuck.com", "gayfall.com", "finevids.xxx", "freepornvs.com", "hclips.com", "mylust.com", "pornfun.com", "pornoid.com", "pornwhite.com", "sheshaft.com", "tryboobs.com", "tubepornclassic.com", "vikiporn.com", "fetishshrine.com", "katestube.com", "sleazyneasy.com", "yeswegays.com", "wetplace.com", "xbabe.com", "xfig.net", "hdzog.com", "sex3.com", "egbo.com", "bravoteens.com", "yoxhub.com", "xxxymovies.com", "bravotube.net", "upornia.com", "xcafe.com" }, urls = {
        "http://(?:www\\.)?kvs\\-demo\\.com/videos/[a-z0-9\\-]+/", "http://(?:www\\.)?alotporn\\.com/(?:\\d+/[A-Za-z0-9\\-_]+/|(?:embed\\.php\\?id=|embed/)\\d+)|https?://m\\.alotporn\\.com/\\d+/[a-z0-9\\-]+/", "http://(?:www\\.)?alphaporno\\.com/videos/[a-z0-9\\-]+/", "http://(?:www\\.)?updatetube\\.com/videos/\\d+/[a-z0-9\\-]+/", "http://(?:www\\.)?thenewporn\\.com/videos/\\d+/[a-z0-9\\-]+/", "http://(?:www\\.)?pinkrod\\.com/videos/\\d+/[a-z0-9\\-]+/", "http://(?:www\\.)?hotshame\\.com/videos/\\d+/[a-z0-9\\-]+/", "http://(?:www\\.)?tubewolf\\.com/movies/[a-z0-9\\-]+", "http://(?:www\\.)?voyeurhit\\.com/videos/[a-z0-9\\-]+", "http://(?:www\\.)?yourlust\\.com/videos/[a-z0-9\\-]+\\.html", "http://(?:www\\.)?pornicom\\.com/videos/\\d+/[a-z0-9\\-]+/", "http://(?:www\\.)?pervclips\\.com/tube/videos/[^<>\"/]+/", "http://(?:www\\.|m\\.)?wankoz\\.com/videos/\\d+/[a-z0-9\\-_]+/",
        "http://(?:www\\.)?tubecup\\.com/videos/\\d+/[a-z0-9\\-_]+/", "http://(?:www\\.)?pornalized\\.com/videos/\\d+/[a-z0-9\\-]+/", "http://(?:www\\.)?myxvids\\.com/(videos/\\d+/[a-z0-9\\-_]+/|embed/\\d+)", "http://(?:www\\.)?hellporno\\.com/videos/[a-z0-9\\-]+/", "http://(?:www\\.)?h2porn\\.com/videos/[a-z0-9\\-]+/", "http://(?:www\\.)?befuck\\.com/videos/\\d+/[a-z0-9\\-]+/", "http://(?:www\\.)?gayfall\\.com/videos/[a-z0-9\\-]+/", "http://(?:www\\.)?finevids\\.xxx/videos/\\d+/[a-z0-9\\-]+", "http://(?:www\\.)?freepornvs\\.com/videos/\\d+/[a-z0-9\\-]+/", "http://(?:www\\.)?hclips\\.com/videos/[a-z0-9\\-]+", "http://(?:www\\.)?mylust\\.com/videos/\\d+/[a-z0-9\\-]+/", "http://(?:www\\.)?pornfun\\.com/videos/\\d+/[a-z0-9\\-]+/", "http://(?:www\\.)?pornoid\\.com/videos/\\d+/[a-z0-9\\-]+/", "http://(?:www\\.)?pornwhite\\.com/videos/\\d+/[a-z0-9\\-]+/",
        "http://(?:www\\.)?sheshaft\\.com/videos/\\d+/[a-z0-9\\-]+/", "http://(?:www\\.)?tryboobs\\.com/videos/\\d+/[a-z0-9\\-]+/", "http://(?:www\\.)?tubepornclassic\\.com/videos/\\d+/[a-z0-9\\-]+/", "http://(?:www\\.)?vikiporn\\.com/videos/\\d+/[a-z0-9\\-]+/", "http://(?:www\\.)?fetishshrine\\.com/videos/\\d+/[a-z0-9\\-]+/", "http://(?:www\\.)?katestube\\.com/videos/\\d+/[a-z0-9\\-]+/", "http://(?:www\\.)?sleazyneasy\\.com/videos/\\d+/[a-z0-9\\-]+/", "http://(?:www\\.)?yeswegays\\.com/videos/\\d+/[a-z0-9\\-]+/", "http://(?:www\\.)?wetplace\\.com/videos/\\d+/[a-z0-9\\-]+/", "http://(www\\.)?xbabe\\.com/videos/[a-z0-9\\-]+/", "http://(?:www\\.)?xfig\\.net/videos/\\d+/[a-z0-9\\-]+/", "http://(?:www\\.)?hdzog\\.com/videos/\\d+/[a-z0-9\\-]+/", "http://(www\\.)?sex3\\.com/\\d+/", "http://(?:www\\.)?egbo\\.com/video/\\d+/?", "http://(?:www\\.)?bravoteens\\.com/videos/\\d+/[a-z0-9\\-]+/",
        "http://(?:www\\.)?yoxhub\\.com/videos/\\d+/[a-z0-9\\-]+/", "http://(?:www\\.)?xxxymovies\\.com/videos/\\d+/[a-z0-9\\-]+/", "http://(?:www\\.)?bravotube\\.net/videos/[a-z0-9\\-]+", "http://(?:www\\.)?upornia\\.com/videos/\\d+/[a-z0-9\\-]+/", "http://xcafe\\.com/\\d+/" }, flags = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 })
public class KernelVideoSharingCom extends PluginForHost {

    public KernelVideoSharingCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    /* DEV NOTES */
    /* Porn_plugin */
    // Version 1.0
    // Tags:
    // protocol: no https
    // other: URL to a live demo: http://www.kvs-demo.com/
    // other #2: Special websites that have their own plugins: alotporn.com
    // other #3: Plugins with "high security" removed 2015-07-02: BravoTubeNet, BravoTeensCom
    // other #3: h2porn.com: Added without security stuff 2015-11-03 REV 29387
    // TODO: Check if it is possible to get nice filenames for embed-urls as well
    /**
     * specifications that have to be met for hosts to be added here:
     *
     * -404 error response on file not found
     *
     * -Possible filename inside URL
     *
     * -No serverside downloadlimits
     *
     * -No account support
     *
     * -Final downloadlink that fits the RegExes
     *
     * -Website should NOT link to external sources (needs decrypter)
     *
     * */

    /* Extension which will be used if no correct extension is found */
    private static final String  default_Extension     = ".mp4";
    /* Connection stuff */
    private static final boolean free_resume           = true;
    private static final int     free_maxchunks        = 0;
    private static final int     free_maxdownloads     = -1;

    /* E.g. normal kernel-video-sharing.com video urls */
    private static final String  type_normal           = "^https?://.+/(videos/)?(?:\\d+/)?[a-z0-9\\-]+(/?|\\.html)$";
    private static final String  type_mobile           = "^https?://m\\.[^/]+/\\d+/[a-z0-9\\-]+/$";
    /* E.g. sex3.com, egbo.com */
    private static final String  type_only_numbers     = "^https?://[^/]+/(?:video/)?\\d+/$";
    /* E.g. myxvids.com */
    private static final String  type_embedded         = "^https?://(?:www\\.)?[^/]+/embed/\\d+/?$";

    private static final String  type_special_alotporn = "http://(?:www\\.)?alotporn\\.com/(?:\\d+/[A-Za-z0-9\\-_]+/|(?:embed\\.php\\?id=|embed/)\\d+)|https?://m\\.alotporn\\.com/\\d+/[a-z0-9\\-]+/";

    private String               DLLINK                = null;

    @Override
    public String getAGBLink() {
        return "http://www.kvs-demo.com/terms.php";
    }

    @SuppressWarnings("deprecation")
    public void correctDownloadLink(final DownloadLink link) {
        if (link.getDownloadURL().matches(type_mobile)) {
            /* Correct mobile urls --> Normal URLs */
            final Regex info = new Regex(link.getDownloadURL(), "^https?://m\\.([^/]+/\\d+/[a-z0-9\\-]+/$)");
            final String linkpart = info.getMatch(0);
            link.setUrlDownload("http://www." + linkpart);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink downloadLink) throws IOException, PluginException {
        DLLINK = null;
        String filename = null;
        this.setBrowserExclusive();
        this.br.setFollowRedirects(true);
        this.br.getPage(downloadLink.getDownloadURL());
        final String host = downloadLink.getHost();
        if (br.containsHTML("KernelTeamVideoSharingSystem\\.js|KernelTeamImageRotator_")) {
            /* <script src="/js/KernelTeamImageRotator_3.8.1.jsx?v=3"></script> */
            /* <script type="text/javascript" src="http://www.hclips.com/js/KernelTeamVideoSharingSystem.js?v=3.8.1"></script> */
        }
        if (downloadLink.getDownloadURL().matches(type_special_alotporn)) {
            if (downloadLink.getDownloadURL().matches(type_embedded)) {
                /* Convert embed --> Normal */
                final String fid = new Regex(downloadLink.getDownloadURL(), "(\\d+)/?$").getMatch(0);
                this.br.getPage("http://www.alotporn.com/" + fid + "/" + System.currentTimeMillis() + "/");
            }
            filename = this.br.getRegex("<div class=\"headline\">[\t\n\r ]*?<h1>([^<>\"]*?)</h1>").getMatch(0);
            if (filename == null) {
                filename = regexStandardTitleWithHost(host);
            }
        } else if (downloadLink.getDownloadURL().matches(type_only_numbers)) {
            filename = br.getRegex("<title>([^<>\"]*?)</title>").getMatch(0);
        } else if (downloadLink.getDownloadURL().matches(type_embedded)) {
            filename = this.br.getRegex("<title>([^<>\"]*?) / Embed Player</title>").getMatch(0);
            if (filename == null) {
                filename = new Regex(downloadLink.getDownloadURL(), "(\\d+)/?$").getMatch(0);
            }
        } else {
            String filename_url = new Regex(downloadLink.getDownloadURL(), "(?:videos|movies)/(?:\\d+/)?([a-z0-9\\-]+)(?:/?|\\.html)$").getMatch(0);
            /* Make it look a bit better by using spaces instead of '-' which is always used inside their URLs. */
            filename_url = filename_url.replace("-", " ");

            /* Works e.g. for hdzog.com */
            filename = this.br.getRegex("var video_title[\t\n\r ]*?=[\t\n\r ]*?\"([^<>]*?)\";").getMatch(0);
            if (filename == null) {
                /* Newer KVS e.g. tubecup.com */
                filename = this.br.getRegex("title[\t\n\r ]*?:[\t\n\r ]*?\"([^<>\"]*?)\"").getMatch(0);
            }
            if (filename == null) {
                filename = this.br.getRegex("<h\\d+ class=\"album_title\">([^<>]*?)<").getMatch(0);
            }
            if (filename == null) {
                filename = this.br.getRegex("itemprop=\"name\">([^<>]*?)<").getMatch(0);
            }
            if (filename == null) {
                filename = this.br.getRegex("<meta property=\"og:title\" content=\"([^<>\"]*?)\"").getMatch(0);
            }
            if (filename == null) {
                /* Fails e.g. for alphaporno.com */
                filename = this.br.getRegex("<h\\d+ class=\"title\">([^<>\"]*?)<").getMatch(0);
            }
            if (filename == null) {
                /* Working e.g. for wankoz.com */
                filename = this.br.getRegex("<h\\d+ class=\"block_header\" id=\"desc_button\">([^<>\"]*?)</h\\d+>").getMatch(0);
            }
            if (filename == null) {
                /* Working e.g. for pervclips.com, pornicom.com */
                filename = this.br.getRegex("class=\"heading video\\-heading\">[\t\n\r ]+<h\\d+>([^<>\"]*?)</h\\d+>").getMatch(0);
            }
            if (filename == null) {
                /* Working e.g. for voyeurhit.com */
                filename = this.br.getRegex("<div class=\"info\\-player\">[\t\n\r ]+<h\\d+>([^<>\"]*?)</h\\d+>").getMatch(0);
            }
            // if (filename == null) {
            // /* This will e.g. fail for wankoz.com */
            // filename = this.br.getRegex("<h\\d+ class=\"block_header\">([^<>]*?)<").getMatch(0);
            // }
            // if (filename == null) {
            // /* This will e.g. fail for hdzog.com */
            // filename = this.br.getRegex("class=\"block\\-title\">[\t\n\r ]*?<h\\d+>([^<>]*?)<").getMatch(0);
            // }
            if (filename == null) {
                /* Many websites in general use this format - title plus their own hostname as ending. */
                filename = regexStandardTitleWithHost(host);
            }
            if (filename == null) {
                filename = filename_url;
            }

        }
        /* Offline links should also have nice filenames */
        filename = Encoding.htmlDecode(filename);
        filename = filename.trim();
        filename = encodeUnicode(filename);
        downloadLink.setName(filename);
        if (this.br.getHttpConnection().getResponseCode() == 404 || this.br.getURL().contains("/404.php")) {
            /* Definitly offline */
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        /*
         * Newer KVS versions also support html5 --> RegEx for that as this is a reliable source for our final downloadurl.They can contain
         * the old "video_url" as well but it will lead to 404 --> Prefer this way.
         *
         *
         * E.g. wankoz.com, pervclips.com, pornicom.com
         */
        DLLINK = this.br.getRegex("flashvars\\[\\'video_html5_url\\'\\]=\\'(http[^<>\"]*?)\\'").getMatch(0);
        if (DLLINK == null) {
            /* E.g. yourlust.com */
            DLLINK = this.br.getRegex("flashvars\\.video_html5_url = \"(http[^<>\"]*?)\"").getMatch(0);
        }
        if (DLLINK == null) {
            /* RegEx for "older" KVS versions */
            DLLINK = this.br.getRegex("video_url[\t\n\r ]*?:[\t\n\r ]*?\\'(http[^<>\"]*?)\\'").getMatch(0);
        }
        if (DLLINK == null && downloadLink.getDownloadURL().contains("xfig.net/")) {
            /* Small workaround - do not include the slash at the end. */
            DLLINK = this.br.getRegex("var videoFile=\"(http[^<>\"]*?)/\"").getMatch(0);
        }
        if (DLLINK == null) {
            DLLINK = this.br.getRegex("(http://[A-Za-z0-9\\.\\-]+/get_file/[^<>\"\\&]*?)(?:\\&|\\'|\")").getMatch(0);
        }
        if (DLLINK == null) {
            DLLINK = this.br.getRegex("\\'(?:file|video)\\'[\t\n\r ]*?:[\t\n\r ]*?\\'(http[^<>\"]*?)\\'").getMatch(0);
        }
        if (DLLINK == null) {
            DLLINK = this.br.getRegex("(?:file|url):[\t\n\r ]*?(?:\"|\\')(http[^<>\"]*?)(?:\"|\\')").getMatch(0);
        }
        if (DLLINK == null) {
            DLLINK = this.br.getRegex("<source src=\"(https?://[^<>\"]*?)\" type=(?:\"|\\')video/(?:mp4|flv)(?:\"|\\')").getMatch(0);
        }
        if (DLLINK == null) {
            DLLINK = this.br.getRegex("property=\"og:video\" content=\"(http[^<>\"]*?)\"").getMatch(0);
        }
        if (DLLINK == null) {
            if (!this.br.containsHTML("license_code:") && !this.br.containsHTML("kt_player_[0-9\\.]+\\.swfx?")) {
                /* No licence key present in html and/or no player --> No video --> Offline */
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        DLLINK = Encoding.htmlDecode(DLLINK);
        String ext = DLLINK.substring(DLLINK.lastIndexOf("."));
        /* Make sure that we get a correct extension */
        if (ext == null || !ext.matches("\\.[A-Za-z0-9]{3,5}")) {
            ext = default_Extension;
        }
        if (!filename.endsWith(ext)) {
            filename += ext;
        }
        downloadLink.setFinalFileName(filename);
        // In case the link redirects to the finallink
        this.br.setFollowRedirects(true);
        URLConnectionAdapter con = null;
        try {
            try {
                con = this.br.openHeadConnection(DLLINK);
                if (this.br.getHttpConnection().getResponseCode() == 404) {
                    /* Small workaround for buggy servers that redirect and fail if the Referer is wrong then. Examples: hdzog.com */
                    final String redirect_url = this.br.getHttpConnection().getRequest().getUrl();
                    con = this.br.openHeadConnection(redirect_url);
                }
            } catch (final BrowserException e) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            if (!con.getContentType().contains("html")) {
                downloadLink.setDownloadSize(con.getLongContentLength());
                final String redirect_url = this.br.getHttpConnection().getRequest().getUrl();
                if (redirect_url != null) {
                    DLLINK = redirect_url;
                    logger.info("DLLINK: " + DLLINK);
                }
                downloadLink.setProperty("directlink", DLLINK);
            }
            return AvailableStatus.TRUE;
        } finally {
            try {
                con.disconnect();
            } catch (final Throwable e) {
            }
        }
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception {
        requestFileInformation(downloadLink);
        if (this.br.getHttpConnection().getResponseCode() == 403) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 403", 60 * 60 * 1000l);
        } else if (this.br.getHttpConnection().getResponseCode() == 403) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 404", 60 * 60 * 1000l);
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, DLLINK, free_resume, free_maxchunks);
        if (dl.getConnection().getContentType().contains("html")) {
            if (dl.getConnection().getResponseCode() == 403) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 403", 60 * 60 * 1000l);
            } else if (dl.getConnection().getResponseCode() == 404) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 404", 60 * 60 * 1000l);
            }
            br.followConnection();
            try {
                dl.getConnection().disconnect();
            } catch (final Throwable e) {
            }
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    private String regexStandardTitleWithHost(final String host) {
        return this.br.getRegex(Pattern.compile("<title>([^<>\"]*?) \\- " + host + "</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL)).getMatch(0);
    }

    /* Avoid chars which are not allowed in filenames under certain OS' */
    private static String encodeUnicode(final String input) {
        String output = input;
        output = output.replace(":", ";");
        output = output.replace("|", "¦");
        output = output.replace("<", "[");
        output = output.replace(">", "]");
        output = output.replace("/", "/");
        output = output.replace("\\", "");
        output = output.replace("*", "#");
        output = output.replace("?", "¿");
        output = output.replace("!", "¡");
        output = output.replace("\"", "'");
        return output;
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return free_maxdownloads;
    }

    @Override
    public SiteTemplate siteTemplateType() {
        return SiteTemplate.KernelVideoSharing;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetPluginGlobals() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }
}
