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
import jd.http.Browser;
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

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "boyfriendtv.com", "ashemaletube.com", "pornoxo.com", "clipcake.com", "worldsex.com", "nudez.com", "porndoe.com", "bigcamtube.com", "xogogo.com", "bigass.ws" }, urls = { "http://(?:www\\.)?boyfriendtv\\.com/videos/\\d+/[a-z0-9\\-]+/", "http://(?:www\\.)?ashemaletube\\.com/videos/\\d+/[a-z0-9\\-]+/", "http://(?:www\\.)?pornoxo\\.com/videos/\\d+/[a-z0-9\\-]+/", "http://(?:www\\.)?clipcake\\.com/videos/\\d+/[a-z0-9\\-]+/", "http://(?:www\\.)?worldsex\\.com/videos/[a-z0-9\\-]+\\-\\d+\\.html", "http://(?:[a-z]{2}\\.)?nudez\\.com/video/[a-z0-9\\-]+\\-\\d+\\.html", "http://[a-z]{2}\\.porndoe\\.com/video/\\d+/[a-z0-9\\-]+", "http://(?:www\\.)?bigcamtube\\.com/videos/[a-z0-9\\-]+/", "http://(?:www\\.)?xogogo\\.com/videos/\\d+/[a-z0-9\\-]+\\.html", "http://(?:www\\.)?bigass\\.ws/videos/\\d+/[a-z0-9\\-]+\\.html" }, flags = {
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0 })
public class UnknownPornScript5 extends PluginForHost {

    public UnknownPornScript5(PluginWrapper wrapper) {
        super(wrapper);
    }

    /* DEV NOTES */
    /* Porn_plugin */
    /* V0.1 */
    // other: Should work for all (porn) sites that use the "jwplayer" with http URLs: http://www.jwplayer.com/

    /* Extension which will be used if no correct extension is found */

    private static final String  type_1                       = "^https?://(?:www\\.)?[^/]+/videos/\\d+/([a-z0-9\\-]+)/$";
    /* E.g. worldsex.com */
    private static final String  type_2                       = "^http://(?:www\\.)?[^/]+/videos/([a-z0-9\\-]+)\\-\\d+\\.html$";
    /* E.g. nudez.com */
    private static final String  type_3                       = "^http://[a-z]{2}\\.[^/]+/video/([a-z0-9\\-]+)\\-\\d+\\.html$";
    /* E.g. porndoe.com */
    private static final String  type_4                       = "^http://[a-z]{2}\\.[^/]+/video/\\d+/([a-z0-9\\-]+)$";
    /* E.g. bigcamtube.com */
    private static final String  type_5                       = "^http://(?:www\\.)?[^/]+/videos/([a-z0-9\\-]+)/$";
    /* E.g. xogogo.com, bigass.ws */
    private static final String  type_6                       = "^http://(?:www\\.)?[^/]+/videos/\\d+/([a-z0-9\\-]+)\\.html$";

    private static final String  type_allow_title_as_filename = "https?://(?:www\\.)?ah\\-me\\.com/videos/\\d+";

    private static final String  default_Extension            = ".mp4";
    /* Connection stuff */
    private static final boolean free_resume                  = true;
    private static final int     free_maxchunks               = 0;
    private static final int     free_maxdownloads            = -1;

    private String               DLLINK                       = null;

    @Override
    public String getAGBLink() {
        return "http://www.boyfriendtv.com/tos.html";
    }

    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink downloadLink) throws IOException, PluginException {
        DLLINK = null;
        final String host = downloadLink.getHost();
        final Browser br2 = new Browser();
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        br.getPage(downloadLink.getDownloadURL());
        if (br.getHttpConnection().getResponseCode() == 404) {
            /* E.g. responsecode 404: boyfriendtv.com */
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        } else if ((downloadLink.getDownloadURL().matches(type_1) || downloadLink.getDownloadURL().matches(type_6)) && !this.br.getURL().contains("/videos/")) {
            /* E.g. clipcake.com */
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        /* Now lets find the url_filename as a fallback in case we cannot find the filename inside the html code. */
        String url_filename = null;
        final String[] urlparts = new Regex(downloadLink.getDownloadURL(), "https?://[^/]+/[^/]+/(.+)").getMatch(0).split("/");
        String url_id = null;
        for (String urlpart : urlparts) {
            if (urlpart.matches("\\d+")) {
                url_id = urlpart;
            } else {
                url_filename = urlpart;
                break;
            }
        }
        if (url_filename == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        url_filename = url_filename.replace(".html", "");
        if (url_id == null) {
            /* In case we have an ID, it might be in the url_filename --> Find it */
            /* First check if we find it at the beginning. */
            url_id = new Regex(url_filename, "^(\\d+\\-).+").getMatch(0);
            if (url_id == null) {
                /* Secondly check if we find it at the end. */
                url_id = new Regex(url_filename, ".+(\\-\\d+)$").getMatch(0);
            }
        }
        if (url_id != null) {
            /* Remove url_id from url_filename */
            url_filename = url_filename.replace(url_id, "");
        }
        /* Make it look nicer! */
        url_filename = url_filename.replace("-", " ");

        String filename = regexStandardTitleWithHost(host);
        if (filename == null) {
            /* Works e.g. for: boyfriendtv.com, ashemaletube.com, pornoxo.com */
            filename = this.br.getRegex("<div id=\"maincolumn2\">[\t\n\r ]*?<h1>([^<>]*?)</h1>").getMatch(0);
        }
        if (filename == null && downloadLink.getDownloadURL().matches(type_allow_title_as_filename)) {
            filename = this.br.getRegex("<title>([^<>]*?)</title>").getMatch(0);
        }
        if (filename == null) {
            filename = url_filename;
        }
        filename = Encoding.htmlDecode(filename).trim();
        filename = encodeUnicode(filename);
        /* Offline urls should also get nice filenames! */
        downloadLink.setName(filename + default_Extension);
        /* Find the (js) source of our player - important! */
        String jwplayer_source = this.br.getRegex("jwplayer\\(\"[^<>\"]+\"\\)(.*?)</script>").getMatch(0);
        if (jwplayer_source != null) {
            /* Source #1 */
            DLLINK = new Regex(jwplayer_source, "(?:\\'|\")file(?:\\'|\"):[\t\n\r ]*?(?:\\'|\")(http[^<>\"\\']*?)(?:\\'|\")").getMatch(0);
            if (inValidateDLLINK()) {
                /* E.g. worldsex.com */
                DLLINK = new Regex(jwplayer_source, "file[\t\n\r ]*?:[\t\n\r ]*?(?:\\'|\")(http[^<>\"\\']*?)(?:\\'|\")").getMatch(0);
            }
            if (inValidateDLLINK()) {
                /* E.g. clipcake.com */
                DLLINK = br.getRegex("var videoFile=\"(http[^<>\"]*?)\";").getMatch(0);
            }
            if (inValidateDLLINK()) {
                /* E.g. porndoe.com */
                /* Check for multiple videoqualities --> Find highest quality */
                int maxquality = 0;
                String sources_source = new Regex(jwplayer_source, "sources:[\t\n\r ]*?\\[(.*?)\\]").getMatch(0);
                if (sources_source != null) {
                    sources_source = sources_source.replace("\\", "");
                    final String[] qualities = new Regex(sources_source, "(file: \".*?)\n").getColumn(0);
                    for (final String quality_info : qualities) {
                        final String p = new Regex(quality_info, "label:\"(\\d+)p").getMatch(0);
                        int pint = 0;
                        if (p != null) {
                            pint = Integer.parseInt(p);
                        }
                        if (pint > maxquality) {
                            maxquality = pint;
                            DLLINK = new Regex(quality_info, "file: \"(http[^<>\"]*?)\"").getMatch(0);
                        }
                    }
                }
            }
        } else {
            /* Source #2 */
            /* E.g. ah-me.com, sunporno.com */
            jwplayer_source = this.br.getRegex("<script type=\"text/javascript\" src=\"http[^<>\"]*?/lib/jwplayer[^<>\"]*?\\.js\"></script>[\t\n\r ]*?<script type=\"text/javascript\" src=\"http[^<>\"]*?/js/common\\.js\"></script>[\t\n\r ]*?<script type=\"text/javascript\">(.*?)</script>").getMatch(0);
            if (jwplayer_source != null) {
                DLLINK = new Regex(jwplayer_source, "<video src=\"(http[^<>\"]*?)\"").getMatch(0);
            }
        }
        if (jwplayer_source == null) {
            /*
             * No player found --> Chances are high that there is no playable content --> Video offline
             * 
             * This can also be seen as a "last chance offline" errorhandling for websites for which the above offline-errorhandling doesn't
             * work!
             */
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }

        String ext = default_Extension;
        if (!inValidateDLLINK()) {
            DLLINK = Encoding.htmlDecode(DLLINK);
            filename = filename.trim();
            ext = DLLINK.substring(DLLINK.lastIndexOf("."));
            if (ext == null || ext.length() > 5) {
                ext = default_Extension;
            }
            /* Set final filename! */
            downloadLink.setFinalFileName(filename + ext);
            URLConnectionAdapter con = null;
            br2.setFollowRedirects(true);
            try {
                con = br2.openHeadConnection(DLLINK);
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
        } else {
            downloadLink.setName(filename + ext);
        }
        return AvailableStatus.TRUE;
    }

    private boolean inValidateDLLINK() {
        if (DLLINK == null) {
            return true;
        } else if (DLLINK.endsWith(".vtt")) {
            /* We picked up the subtitle url instead of the video downloadurl! */
            return true;
        }
        return false;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception {
        requestFileInformation(downloadLink);
        if (inValidateDLLINK()) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
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

    private String regexStandardTitleWithHost(final String host) {
        final String[] hostparts = host.split("\\.");
        final String host_relevant_part = hostparts[0];
        String site_title = br.getRegex(Pattern.compile("<title>([^<>\"]*?) \\- " + host + "</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL)).getMatch(0);
        if (site_title == null) {
            site_title = br.getRegex(Pattern.compile("<title>([^<>\"]*?) at " + host + "</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL)).getMatch(0);
        }
        if (site_title == null) {
            site_title = br.getRegex(Pattern.compile("<title>([^<>\"]*?) at " + host_relevant_part + "</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL)).getMatch(0);
        }
        return site_title;
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return free_maxdownloads;
    }

    @Override
    public SiteTemplate siteTemplateType() {
        return null; // SiteTemplate.UnknownPornScript5;
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
