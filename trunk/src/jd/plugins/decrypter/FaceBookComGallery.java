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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import jd.PluginWrapper;
import jd.config.SubConfiguration;
import jd.controlling.AccountController;
import jd.controlling.ProgressController;
import jd.gui.UserIO;
import jd.http.Browser;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.parser.html.HTMLParser;
import jd.plugins.Account;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterException;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;
import jd.plugins.PluginForHost;
import jd.plugins.hoster.DummyScriptEnginePlugin;
import jd.plugins.hoster.K2SApi.JSonUtils;
import jd.utils.JDUtilities;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "facebook.com" }, urls = { "https?://(www\\.)?(on\\.fb\\.me/[A-Za-z0-9]+\\+?|facebook\\.com/.+|l\\.facebook\\.com/l/[^/]+/.+)" }, flags = { 0 })
public class FaceBookComGallery extends PluginForDecrypt {

    public FaceBookComGallery(PluginWrapper wrapper) {
        super(wrapper);
    }

    /* must be static so all plugins share same lock */
    private static Object           LOCK                            = new Object();
    private static final String     FACEBOOKMAINPAGE                = "http://www.facebook.com";
    private int                     DIALOGRETURN                    = -1;

    private static final String     TYPE_FBSHORTLINK                = "http(s)?://(?:www\\.)?on\\.fb\\.me/[A-Za-z0-9]+\\+?";
    private static final String     TYPE_FB_REDIRECT_TO_EXTERN_SITE = "https?://l\\.facebook\\.com/l/[^/]+/.+";
    private static final String     TYPE_SINGLE_PHOTO               = "http(s)?://(?:www\\.)?facebook\\.com/photo\\.php\\?fbid=\\d+.*?";
    private static final String     TYPE_SINGLE_VIDEO_MANY_TYPES    = "https?://(?:www\\.)?facebook\\.com/(video/video|photo|video)\\.php\\?v=\\d+";
    private static final String     TYPE_SINGLE_VIDEO_EMBED         = "https?://(?:www\\.)?facebook\\.com/video/embed\\?video_id=\\d+";
    private static final String     TYPE_SINGLE_VIDEO_VIDEOS        = "https?://(?:www\\.)?facebook\\.com/.+/videos.*?/\\d+.*?";
    private static final String     TYPE_SET_LINK_PHOTO             = "http(s)?://.+/(media/set/\\?set=|media_set\\?set=)o?a[0-9\\.]+(\\&type=\\d+)?";
    private static final String     TYPE_SET_LINK_VIDEO             = "https?://.+(/media/set/\\?set=|media_set\\?set=)vb\\.\\d+.*?";
    private static final String     TYPE_ALBUMS_LINK                = "https?://(?:www\\.)?facebook\\.com/.+photos_albums";
    private static final String     TYPE_PHOTOS_OF_LINK             = "https?://(?:www\\.)?facebook\\.com/[A-Za-z0-9\\.]+/photos_of.*";
    private static final String     TYPE_PHOTOS_ALL_LINK            = "https?://(?:www\\.)?facebook\\.com/[A-Za-z0-9\\.]+/photos_all.*";
    private static final String     TYPE_PHOTOS_STREAM_LINK         = "https?://(?:www\\.)?facebook\\.com/[A-Za-z0-9\\.]+/photos_stream.*";
    private static final String     TYPE_PHOTOS_LINK                = "https?://(?:www\\.)?facebook\\.com/[A-Za-z0-9\\.]+/photos.*";
    private static final String     TYPE_GROUPS_PHOTOS              = "https?://(?:www\\.)?facebook\\.com/groups/\\d+/photos/";
    private static final String     TYPE_GROUPS_FILES               = "https?://(?:www\\.)?facebook\\.com/groups/\\d+/files/";
    private static final String     TYPE_PROFILE_PHOTOS             = "https?://(?:www\\.)?facebook\\.com/profile\\.php\\?id=\\d+\\&sk=photos\\&collection_token=[A-Z0-9%]+";
    private static final String     TYPE_NOTES                      = "https?://(?:www\\.)?facebook\\.com/(notes/|note\\.php\\?note_id=).+";
    private static final String     TYPE_MESSAGE                    = "httpss?://(?:www\\.)?facebook\\.com/messages/.+";

    private static final int        MAX_LOOPS_GENERAL               = 150;
    private static final int        MAX_PICS_DEFAULT                = 5000;
    public static final String      REV                             = "1938577";

    private static String           MAINPAGE                        = "http://www.facebook.com";
    private static final String     CRYPTLINK                       = "facebookdecrypted.com/";
    private static final String     EXCEPTION_LINKOFFLINE           = "EXCEPTION_LINKOFFLINE";

    private static final String     CONTENTUNAVAILABLE              = ">Dieser Inhalt ist derzeit nicht verfügbar|>This content is currently unavailable<";
    private String                  PARAMETER                       = null;
    private boolean                 fastLinkcheckPictures           = jd.plugins.hoster.FaceBookComVideos.FASTLINKCHECK_PICTURES_DEFAULT;                                     ;
    private boolean                 logged_in                       = false;
    private ArrayList<DownloadLink> decryptedLinks                  = null;

    /*
     * Dear whoever is looking at this - this is a classic example of spaghetticode. If you like spaghettis, go ahead, and get you some
     * tomatoe sauce and eat it but if not, well have fun re-writing this from scratch ;) Wikipedia:
     * http://en.wikipedia.org/wiki/Spaghetti_code
     */
    @SuppressWarnings("deprecation")
    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        /* Code below = prevents Eclipse from freezing as it removes the log output of this thread! */
        // LogInterface logger = new LogInterface() {
        //
        // @Override
        // public void warning(String msg) {
        // }
        //
        // @Override
        // public void severe(String msg) {
        // }
        //
        // @Override
        // public void log(Throwable e) {
        // }
        //
        // @Override
        // public void info(String msg) {
        // }
        //
        // @Override
        // public void finest(String msg) {
        // }
        //
        // @Override
        // public void finer(String msg) {
        // }
        //
        // @Override
        // public void fine(String msg) {
        // }
        // };
        // this.setLogger(logger);
        // ((LinkCrawlerThread) Thread.currentThread()).setLogger(logger);
        br = new Browser();
        this.br.setAllowedResponseCodes(400);

        decryptedLinks = new ArrayList<DownloadLink>();
        String parameter = param.toString().replace("#!/", "");
        PARAMETER = parameter;
        fastLinkcheckPictures = getPluginConfig().getBooleanProperty(jd.plugins.hoster.FaceBookComVideos.FASTLINKCHECK_PICTURES, jd.plugins.hoster.FaceBookComVideos.FASTLINKCHECK_PICTURES_DEFAULT);
        if (PARAMETER.matches(TYPE_SINGLE_VIDEO_MANY_TYPES) || PARAMETER.matches(TYPE_SINGLE_VIDEO_EMBED) || PARAMETER.matches(TYPE_SINGLE_VIDEO_VIDEOS) || PARAMETER.contains("/video.php?v=")) {
            String id;
            if (PARAMETER.matches(TYPE_SINGLE_VIDEO_VIDEOS)) {
                id = new Regex(PARAMETER, "/videos.*?/(\\d+)/?.*?$").getMatch(0);
            } else {
                id = new Regex(PARAMETER, "(?:v=|video_id=)(\\d+)").getMatch(0);
            }
            final DownloadLink fina = createDownloadlink("https://www.facebookdecrypted.com/video.php?v=" + id);
            decryptedLinks.add(fina);
            return decryptedLinks;
        } else if (PARAMETER.matches(TYPE_SINGLE_PHOTO)) {
            final String id = new Regex(PARAMETER, "fbid=(\\d+)").getMatch(0);
            final DownloadLink fina = createDownloadlink("https://www.facebookdecrypted.com/photo.php?fbid=" + id);
            decryptedLinks.add(fina);
            return decryptedLinks;
        } else if (PARAMETER.matches(TYPE_FB_REDIRECT_TO_EXTERN_SITE)) {
            final String external_url = "http://" + new Regex(PARAMETER, "facebook\\.com/l/[^/]+/(.+)").getMatch(0);
            final DownloadLink fina = createDownloadlink(external_url);
            decryptedLinks.add(fina);
            return decryptedLinks;
        }
        br.setFollowRedirects(false);
        try {
            if (parameter.matches(TYPE_FBSHORTLINK)) {
                br.getPage(parameter);
                final String finallink = br.getRedirectLocation();
                if (br.containsHTML(">Something\\'s wrong here")) {
                    logger.info("Link offline: " + parameter);
                    final DownloadLink offline = createDownloadlink("directhttp://" + parameter);
                    offline.setFinalFileName(new Regex(parameter, "facebook\\.com/(.+)").getMatch(0));
                    offline.setAvailable(false);
                    offline.setProperty("offline", true);
                    decryptedLinks.add(offline);
                    return decryptedLinks;
                }
                if (finallink == null) {
                    logger.warning("Decrypter broken for link: " + parameter);
                    return null;
                }
                decryptedLinks.add(createDownloadlink(finallink));
                return decryptedLinks;
            }
            synchronized (LOCK) {
                logged_in = login();
            }
            getpagefirsttime(PARAMETER);

            /* temporarily unavailable (or forever, or permission/rights needed) || empty album */
            if (br.containsHTML(">Dieser Inhalt ist derzeit nicht verfügbar</") || br.containsHTML("class=\"fbStarGridBlankContent\"")) {
                throw new DecrypterException(EXCEPTION_LINKOFFLINE);
            }

            if (PARAMETER.matches(TYPE_ALBUMS_LINK)) {
                decryptAlbums();
            } else if (PARAMETER.matches(TYPE_PHOTOS_OF_LINK)) {
                decryptPhotosOf();
            } else if (PARAMETER.matches(TYPE_PHOTOS_ALL_LINK)) {
                decryptPhotosAll();
            } else if (PARAMETER.matches(TYPE_PHOTOS_STREAM_LINK)) {
                decryptSets();
            } else if (PARAMETER.matches(TYPE_PHOTOS_LINK)) {
                // Old handling removed 05.12.13 in rev 23262
                decryptPicsGeneral(null);
            } else if (br.getURL().contains("profile.php?id=")) {
                decryptPicsProfile();
            } else if (PARAMETER.matches(TYPE_SET_LINK_PHOTO)) {
                decryptSetLinkPhoto();
            } else if (PARAMETER.matches(TYPE_SET_LINK_VIDEO)) {
                decryptSets();
            } else if (parameter.matches(TYPE_GROUPS_PHOTOS)) {
                decryptGroupsPhotos();
            } else if (parameter.matches(TYPE_NOTES)) {
                decryptNotes();
            } else if (parameter.matches(TYPE_MESSAGE)) {
                decryptMessagePhotos();
            } else {
                // Should never happen
                logger.info("Unsupported linktype: " + PARAMETER);
                // because facebook picks up so many false positives do not throw exception or add offline links.
                // throw new DecrypterException(EXCEPTION_LINKOFFLINE);
            }
        } catch (final DecrypterException e) {
            try {
                if (e.getMessage().equals(EXCEPTION_LINKOFFLINE)) {
                    final DownloadLink offline = createDownloadlink("directhttp://" + PARAMETER);
                    offline.setAvailable(false);
                    offline.setProperty("offline", true);
                    offline.setName(new Regex(PARAMETER, "facebook\\.com/(.+)").getMatch(0));
                    decryptedLinks.add(offline);
                    return decryptedLinks;
                }
            } catch (final Exception x) {
            }
            throw e;
        }
        if (decryptedLinks == null) {
            logger.warning("Decrypter broken for link: " + PARAMETER);
            return null;
        } else if (decryptedLinks.size() == 0) {
            logger.info("Link offline: " + PARAMETER);
            return decryptedLinks;
        }
        return decryptedLinks;
    }

    private void decryptAlbums() throws Exception {
        br.getRequest().setHtmlCode(br.toString().replace("\\", ""));
        String fpName = br.getRegex("<title id=\"pageTitle\">([^<>\"]*?)\\- Photos \\| Facebook</title>").getMatch(0);
        final String profileID = getProfileID();
        final String user = getUser(this.br);
        if (user == null) {
            logger.warning("Decrypter broken for link: " + PARAMETER);
            throw new DecrypterException("Decrypter broken for link: " + PARAMETER);
        }
        if (fpName == null) {
            fpName = "Facebook_video_albums_of_user_" + new Regex(PARAMETER, "facebook\\.com/(.*?)/photos_albums").getMatch(0);
        }
        fpName = Encoding.htmlDecode(fpName.trim());
        boolean dynamicLoadAlreadyDecrypted = false;

        for (int i = 1; i <= MAX_LOOPS_GENERAL; i++) {
            if (this.isAbort()) {
                logger.info("User aborted decryption");
                break;
            }
            int currentMaxPicCount = 18;

            String[] links;
            if (i > 1) {
                String cursor = this.br.getRegex("\\{\"__m\":\"__elem_559218ec_0_0\"\\},\"([^<>\"]*?)\"\\]").getMatch(0);
                if (cursor == null) {
                    cursor = this.br.getRegex("\"cursor\":\"([^<>\"]*?)\"").getMatch(0);
                }
                String collection_token = this.br.getRegex("\"pagelet_timeline_app_collection_([^<>\"/]*?)\"").getMatch(0);
                if (collection_token == null) {
                    /* E.g. from json */
                    collection_token = this.br.getRegex("\"collection_token\":\"([^<>\"]*?)\"").getMatch(0);
                }
                // String currentLastAlbumid = br.getRegex("\"last_album_id\":\"(\\d+)\"").getMatch(0);
                // if (currentLastAlbumid == null) {
                // currentLastAlbumid = br.getRegex("\"last_album_id\":(\\d+)").getMatch(0);
                // }
                // If we have exactly currentMaxPicCount pictures then we reload one
                // time and got all, 2nd time will then be 0 more links
                // -> Stop
                if (collection_token == null && dynamicLoadAlreadyDecrypted) {
                    break;
                } else if (collection_token == null) {
                    logger.warning("Decrypter maybe broken for link: " + PARAMETER);
                    logger.info("Returning already decrypted links anyways...");
                    break;
                }
                if (true) {
                    /* TODO: !!! */
                    break;
                }
                final String data = "{\"collection_token\":\"" + collection_token + "\",\"cursor\":\"" + cursor + "\",\"tab_key\":\"photos_albums\",\"profile_id\":" + profileID + ",\"overview\":false,\"ftid\":null,\"order\":null,\"sk\":\"photos\",\"importer_state\":null}";
                final String loadLink = MAINPAGE + "/ajax/pagelet/generic.php/TimelinePhotoAlbumsPagelet?data=" + Encoding.urlEncode(data) + "&__user=" + user + "&dyn=" + getDyn() + "&__a=1&__dyn=&__req=a&__adt=" + i;
                br.getPage(loadLink);
                br.getRequest().setHtmlCode(br.toString().replace("\\", ""));
                links = br.getRegex("class=\"photoTextTitle\" href=\"(https?://(www\\.)?facebook\\.com/media/set/\\?set=(?:a|vb)\\.[0-9\\.]+)\\&amp;type=\\d+\"").getColumn(0);
                currentMaxPicCount = 12;
                dynamicLoadAlreadyDecrypted = true;
            } else {
                links = br.getRegex("class=\"photoTextTitle\" href=\"(https?://(www\\.)?facebook\\.com/[^<>\"]*?)\"").getColumn(0);
            }
            if (links == null || links.length == 0) {
                logger.warning("Decrypter broken for the following link or account needed: " + PARAMETER);
                throw new DecrypterException("Decrypter broken for link: " + PARAMETER);
            }
            logger.info("Decrypting page " + i + " of ??");
            for (String link : links) {
                link = Encoding.htmlDecode(link);
                final DownloadLink dl = createDownloadlink(link);
                decryptedLinks.add(dl);
                distribute(dl);
            }
            // currentMaxPicCount = max number of links per segment
            if (links.length < currentMaxPicCount || profileID == null) {
                logger.info("Seems like we're done and decrypted all links, stopping at page: " + i);
                break;
            }
        }

        final FilePackage fp = FilePackage.getInstance();
        fp.setName(Encoding.htmlDecode(fpName.trim()));
        fp.addLinks(decryptedLinks);

    }

    private void decryptPhotosAll() throws Exception {
        decryptPicsGeneral("AllPhotosAppCollectionPagelet");
    }

    private void decryptPhotosOf() throws Exception {
        if (br.containsHTML(">Dieser Inhalt ist derzeit nicht verfügbar</")) {
            logger.info("The link is either offline or an account is needed to grab it: " + PARAMETER);
            throw new DecrypterException(EXCEPTION_LINKOFFLINE);
        }
        final String profileID = getProfileID();
        String fpName = br.getRegex("id=\"pageTitle\">([^<>\"]*?)</title>").getMatch(0);
        final String user = getUser(this.br);
        final String token = br.getRegex("\"tab_count\":\\d+,\"token\":\"([^<>\"]*?)\"").getMatch(0);
        if (token == null || user == null || profileID == null) {
            logger.warning("Decrypter broken for link: " + PARAMETER);
            throw new DecrypterException("Decrypter broken for link: " + PARAMETER);
        }
        if (fpName == null) {
            fpName = "Facebook_photos_of_of_user_" + user;
        }
        fpName = Encoding.htmlDecode(fpName.trim());
        final FilePackage fp = FilePackage.getInstance();
        fp.setName(fpName);
        boolean dynamicLoadAlreadyDecrypted = false;
        String lastfirstID = "";

        for (int i = 1; i <= MAX_LOOPS_GENERAL; i++) {
            int currentMaxPicCount = 20;

            String[] links;
            if (i > 1) {
                if (br.containsHTML("\"TimelineAppCollection\",\"setFullyLoaded\"")) {
                    break;
                }
                final String cursor = br.getRegex("\\[\"pagelet_timeline_app_collection_[^<>\"]*?\",\\{\"[^<>\"]*?\":\"[^<>\"]*?\"\\},\"([^<>\"]*?)\"").getMatch(0);
                // If we have exactly currentMaxPicCount pictures then we reload one
                // time and got all, 2nd time will then be 0 more links
                // -> Stop
                if (cursor == null && dynamicLoadAlreadyDecrypted) {
                    break;
                } else if (cursor == null) {
                    logger.warning("Decrypter maybe broken for link: " + PARAMETER);
                    logger.info("Returning already decrypted links anyways...");
                    break;
                }
                final String loadLink = MAINPAGE + "/ajax/pagelet/generic.php/TaggedPhotosAppCollectionPagelet?data=%7B%22collection_token%22%3A%22" + token + "%22%2C%22cursor%22%3A%22" + cursor + "%22%2C%22tab_key%22%3A%22photos_of%22%2C%22profile_id%22%3A" + profileID + "%2C%22overview%22%3Afalse%2C%22ftid%22%3Anull%2C%22order%22%3Anull%2C%22sk%22%3A%22photos%22%7D&__user=" + user + "&__a=1&__dyn=" + getDyn() + "&__adt=" + i;
                br.getPage(loadLink);
                links = br.getRegex("ajax\\\\/photos\\\\/hovercard\\.php\\?fbid=(\\d+)\\&").getColumn(0);
                currentMaxPicCount = 40;
                dynamicLoadAlreadyDecrypted = true;
            } else {
                links = br.getRegex("id=\"pic_(\\d+)\"").getColumn(0);
            }
            if (links == null || links.length == 0) {
                logger.warning("Decrypter broken for the following link or account needed: " + PARAMETER);
                throw new DecrypterException("Decrypter broken for link: " + PARAMETER);
            }
            boolean stop = false;
            logger.info("Decrypting page " + i + " of ??");
            for (final String picID : links) {
                // Another fail safe to prevent loops
                if (picID.equals(lastfirstID)) {
                    stop = true;
                    break;
                }
                final DownloadLink dl = createPicDownloadlink(picID);
                fp.add(dl);
                distribute(dl);
                decryptedLinks.add(dl);
            }
            // currentMaxPicCount = max number of links per segment
            if (links.length < currentMaxPicCount) {
                stop = true;
            }
            if (stop) {
                logger.info("Seems like we're done and decrypted all links, stopping at page: " + i);
                break;
            }
        }
        fp.addLinks(decryptedLinks);

    }

    private void decryptPicsProfile() throws Exception {
        String fpName = getPageTitle();
        final boolean profile_setlink = br.getURL().contains("&set=");
        boolean dynamicLoadAlreadyDecrypted = false;
        String type;
        String collection_token = null;
        String profileID;
        String setID;
        String additionalPostData = null;
        final String ajaxpipeToken = getajaxpipeToken();
        if (profile_setlink) {
            profileID = new Regex(br.getURL(), "profile\\.php\\?id=(\\d+)\\&").getMatch(0);
            collection_token = new Regex(br.getURL(), "collection_token=([^<>\"]*?)\\&").getMatch(0);
            type = new Regex(br.getURL(), "type=(\\d+)").getMatch(0);
            setID = new Regex(br.getURL(), "set=([a-z0-9\\.]+)").getMatch(0);
            additionalPostData = "%2C%22tab_key%22%3A%22photos%22%2C%22id%22%3A%22" + profileID + "%22%2C%22sk%22%3A%22photos%22%2C%22collection_token%22%3A%22" + Encoding.urlEncode(collection_token) + "%22%2C%22set%22%3A%22" + setID + "%22%2C%22type%22%3A%22" + type;
        } else {
            profileID = getProfileID();
        }
        final String user = getUser(this.br);
        final String totalPicCount = br.getRegex("data-medley-id=\"pagelet_timeline_medley_photos\">Photos<span class=\"_gs6\">(\\d+((,|\\.)\\d+)?)</span>").getMatch(0);
        if (user == null || profileID == null || ajaxpipeToken == null) {
            logger.warning("Decrypter broken for link: " + PARAMETER);
            return;
        }
        if (fpName == null) {
            fpName = "Facebook_profile_of_user_" + user;
        }
        fpName = Encoding.htmlDecode(fpName.trim());
        final FilePackage fp = FilePackage.getInstance();
        fp.setName(fpName);
        // Use this as default as an additional fail safe
        long totalPicsNum = MAX_PICS_DEFAULT;
        if (totalPicCount != null) {
            totalPicsNum = Long.parseLong(totalPicCount.replaceAll("(\\.|,)", ""));
        }
        int lastDecryptedPicsNum = 0;
        int decryptedPicsNum = 0;
        int timesNochange = 0;

        prepBrPhotoGrab();

        for (int i = 1; i <= MAX_LOOPS_GENERAL; i++) {
            try {
                if (this.isAbort()) {
                    logger.info("Decryption stopped at request " + i);
                    return;
                }
            } catch (final Throwable e) {
                // Not supported in old 0.9.581 Stable
            }

            int currentMaxPicCount = 20;

            String[] links;
            if (i > 1) {
                final String currentLastFbid = getLastFBID();
                // If we have exactly currentMaxPicCount pictures then we reload one
                // time and got all, 2nd time will then be 0 more links
                // -> Stop
                if (currentLastFbid == null && dynamicLoadAlreadyDecrypted) {
                    break;
                } else if (currentLastFbid == null || additionalPostData == null) {
                    logger.info("Cannot find more links, stopping decryption...");
                    break;
                }
                String loadLink = "https://www.facebook.com/ajax/pagelet/generic.php/TimelinePhotosAlbumPagelet?ajaxpipe=1&ajaxpipe_token=" + ajaxpipeToken + "&no_script_path=1&data=%7B%22scroll_load%22%3Atrue%2C%22last_fbid%22%3A" + currentLastFbid + "%2C%22fetch_size%22%3A32%2C%22profile_id%22%3A" + profileID + additionalPostData + "%22%2C%22overview%22%3Afalse%2C%22active_collection%22%3A69%2C%22collection_token%22%3A%22" + Encoding.urlEncode(collection_token) + "%22%2C%22cursor%22%3A0%2C%22tab_id%22%3A%22u_0_t%22%2C%22order%22%3Anull%2C%22importer_state%22%3Anull%7D&__user=" + user + "&__a=1&__dyn=7n8ahyngCBDBzpQ9UoGhk4BwzCxO4oKA8ABGfirWo8popyUWdDx24QqUgKm58y&__req=jsonp_" + i + "&__rev=" + REV + "&__adt=" + i;
                br.getPage(loadLink);
                links = br.getRegex("ajax\\\\/photos\\\\/hovercard\\.php\\?fbid=(\\d+)\\&").getColumn(0);
                currentMaxPicCount = 32;
                dynamicLoadAlreadyDecrypted = true;
            } else {
                if (profile_setlink) {
                    links = br.getRegex("hovercard\\.php\\?fbid=(\\d+)").getColumn(0);
                } else {
                    links = br.getRegex("class=\"photoTextTitle\" href=\"(https?://(www\\.)?facebook\\.com/media/set/\\?set=(a|vb)\\.[^<>\"]*?)\"").getColumn(0);
                    if (links == null || links.length == 0) {
                        links = br.getRegex("hovercard\\.php\\?fbid=(\\d+)").getColumn(0);
                    }
                }
            }
            if (links == null || links.length == 0) {
                logger.warning("Decryption done or decrypter broken: " + PARAMETER);
                return;
            }
            boolean stop = false;
            logger.info("Decrypting page " + i + " of ??");
            for (String entry : links) {
                entry = Encoding.htmlDecode(entry);
                final DownloadLink dl;
                if (entry.matches("\\d+")) {
                    dl = createPicDownloadlink(entry);
                } else {
                    dl = createDownloadlink(entry);
                }
                fp.add(dl);
                decryptedLinks.add(dl);
                decryptedPicsNum++;
            }
            // currentMaxPicCount = max number of links per segment
            if (links.length < currentMaxPicCount) {
                logger.info("facebook.com: Found less pics than a full page -> Continuing anyways");
            }
            logger.info("facebook.com: Decrypted " + decryptedPicsNum + " of " + totalPicsNum);
            if (timesNochange == 3) {
                logger.info("facebook.com: Three times no change -> Aborting decryption");
                stop = true;
            }
            if (decryptedPicsNum >= totalPicsNum) {
                logger.info("facebook.com: Decrypted all pictures -> Stopping");
                stop = true;
            }
            if (decryptedPicsNum == lastDecryptedPicsNum) {
                timesNochange++;
            } else {
                timesNochange = 0;
            }
            lastDecryptedPicsNum = decryptedPicsNum;
            if (stop) {
                logger.info("facebook.com: Seems like we're done and decrypted all links, stopping at page: " + i);
                break;
            }
        }
        fp.addLinks(decryptedLinks);
        logger.info("facebook.com: Decrypted " + decryptedPicsNum + " of " + totalPicsNum);
        if (decryptedPicsNum < totalPicsNum && br.containsHTML("\"TimelineAppCollection\",\"setFullyLoaded\"")) {
            logger.info("facebook.com: -> Even though it seems like we don't have all images, that's all ;)");
        }
    }

    private void decryptSetLinkPhoto() throws Exception {
        String type;
        String collection_token;
        String profileID;
        String setID;
        String activecollection = null;
        profileID = br.getRegex("\"profile_id\":(\\d+)").getMatch(0);
        if (profileID == null) {
            profileID = br.getRegex("follow_profile\\.php\\?profile_id=(\\d+)").getMatch(0);
        }
        if (profileID == null) {
            profileID = br.getRegex("\"profile_id\\\\\":(\\d+)").getMatch(0);
        }
        collection_token = br.getRegex("\\[\"pagelet_timeline_app_collection_([^<>\"]*?)\"\\]").getMatch(0);
        if (collection_token != null) {
            activecollection = new Regex(collection_token, ":(\\d+)$").getMatch(0);
        }
        type = "3";
        setID = new Regex(PARAMETER, "set=([a-z0-9\\.]+)").getMatch(0);
        String fpName = br.getRegex("id=\"pageTitle\">([^<>\"]*?)\\| Facebook</title>").getMatch(0);
        if (fpName == null) {
            fpName = br.getRegex("id=\"pageTitle\">([^<>\"]*?)</title>").getMatch(0);
        }
        if (fpName == null) {
            fpName = br.getRegex("class=\"fbPhotoAlbumTitle\">([^<>\"]*?)</h1>").getMatch(0);
        }
        final String ajaxpipeToken = getajaxpipeToken();
        final String user = getUser(this.br);
        String lastfirstID = "";
        if (ajaxpipeToken == null || user == null || type == null || setID == null) {
            logger.warning("Decrypter broken for link: " + PARAMETER);
            throw new DecrypterException("Decrypter broken for link: " + PARAMETER);
        }
        String data = null;
        if (collection_token != null) {
            data = "{\"scroll_load\":true,\"last_fbid\":JDL_LAST_FBID_JDL,\"fetch_size\":32,\"profile_id\":" + profileID + ",\"tab_key\":\"media_set\",\"set\":\"" + setID + "\",\"type\":\"" + type + "\",\"sk\":\"photos\",\"overview\":false,\"active_collection\":" + activecollection + ",\"collection_token\":\"" + collection_token + "\",\"cursor\":0,\"tab_id\":\"u_0_u\",\"order\":null,\"importer_state\":null}";
        } else {
            data = "{\"scroll_load\":true,\"last_fbid\":\"JDL_LAST_FBID_JDL\",\"fetch_size\":32,\"profile_id\":" + profileID + ",\"viewmode\":null,\"set\":\"" + setID + "\",\"type\":\"3\"}";
        }
        data = Encoding.urlEncode(data);
        if (fpName == null) {
            fpName = "Facebook_album_of_user_" + user;
        }
        fpName = Encoding.htmlDecode(fpName.trim()) + " - " + setID;
        final FilePackage fp = FilePackage.getInstance();
        fp.setName(fpName.trim());

        for (int i = 1; i <= MAX_LOOPS_GENERAL; i++) {
            int currentMaxPicCount = 28;
            String[] links;
            if (i > 1) {
                final String currentLastFbid = getLastFBID();
                if (currentLastFbid == null) {
                    logger.info("Cannot find more links, stopping decryption...");
                    break;
                }
                String loadLink = "https://www.facebook.com/ajax/pagelet/generic.php/TimelinePhotosAlbumPagelet?ajaxpipe=1&ajaxpipe_token=" + ajaxpipeToken + "&no_script_path=1&data=" + data.replace("JDL_LAST_FBID_JDL", currentLastFbid) + "&__user=" + user + "&__a=1&__dyn=7n8ahyngCBDBzpQ9UoGhk4BwzCxO4oKA8ABGfirWo8popyUWdDx24QqUgKm58y&__req=jsonp_" + i + "&__rev=" + REV + "&__adt=" + i;
                br.getPage(loadLink);
                links = br.getRegex("ajax\\\\/photos\\\\/hovercard\\.php\\?fbid=(\\d+)\\&").getColumn(0);
                currentMaxPicCount = 32;
            } else {
                links = br.getRegex("hovercard\\.php\\?fbid=(\\d+)").getColumn(0);
            }
            if (links == null || links.length == 0) {
                logger.warning("Decrypter broken for the following link or account needed: " + PARAMETER);
                throw new DecrypterException("Decrypter broken for link: " + PARAMETER);
            }
            boolean stop = false;
            logger.info("Decrypting page " + i + " of ??");
            for (final String picID : links) {
                // Another fail safe to prevent loops
                if (picID.equals(lastfirstID)) {
                    stop = true;
                }
                final DownloadLink dl = createPicDownloadlink(picID);
                fp.add(dl);
                try {
                    distribute(dl);
                } catch (final Throwable e) {
                    // Not supported in old 0.9.581 Stable
                }
                decryptedLinks.add(dl);
            }
            // currentMaxPicCount = max number of links per segment
            if (links.length < currentMaxPicCount || profileID == null) {
                stop = true;
            }
            if (stop) {
                logger.info("Seems like we're done and decrypted all links, stopping at page: " + i);
                break;
            }
        }
        fp.addLinks(decryptedLinks);
    }

    /* TODO: Improve this and clean this up! */
    private void decryptSets() throws Exception {
        if (br.containsHTML(">Dieser Inhalt ist derzeit nicht verfügbar</")) {
            logger.info("The link is either offline or an account is needed to grab it: " + PARAMETER);
            throw new DecrypterException(EXCEPTION_LINKOFFLINE);
        }
        String fpName = getPageTitle();
        final String url_username = new Regex(PARAMETER, "facebook\\.com/([^<>\"\\?/]+)").getMatch(0);
        final String rev = getRev(this.br);
        final String user = getUser(this.br);
        final String dyn = getDyn();
        final String ajaxpipe_token = getajaxpipeToken();
        final String profileID = getProfileID();
        final String totalPicCount = br.getRegex("data-medley-id=\"pagelet_timeline_medley_photos\">Photos<span class=\"_gs6\">(\\d+((,|\\.)\\d+)?)</span>").getMatch(0);
        final String setid = new Regex(this.PARAMETER, "/set/\\?set=([a-z0-9\\.]+)").getMatch(0);
        if (user == null || profileID == null || ajaxpipe_token == null || profileID == null) {
            logger.warning("Decrypter broken for link: " + PARAMETER);
            return;
        }
        if (fpName == null) {
            fpName = this.br.getRegex("<title id=\"pageTitle\">([^<>\"]*?)</title>").getMatch(0);
        }
        if (fpName == null) {
            fpName = "Facebook_photos_stream_of_user_" + user;
        }
        fpName = Encoding.htmlDecode(fpName.trim());
        final FilePackage fp = FilePackage.getInstance();
        fp.setName(fpName);

        boolean dynamicLoadAlreadyDecrypted = false;
        // Use this as default as an additional fail safe
        long totalPicsNum = MAX_PICS_DEFAULT;
        if (totalPicCount != null) {
            totalPicsNum = Long.parseLong(totalPicCount.replaceAll("(\\.|,)", ""));
        }
        int timesNochange = 0;

        prepBrPhotoGrab();

        for (int i = 1; i <= MAX_LOOPS_GENERAL; i++) {
            if (this.isAbort()) {
                logger.info("Decryption stopped at request " + i);
                return;
            }

            long linkscount_before = 0;
            long linkscount_after = 0;
            long addedlinks = 0;
            int currentMaxPicCount = 20;

            logger.info("Decrypting page " + i + " of ??");
            linkscount_before = this.decryptedLinks.size();

            if (i > 1) {
                final String currentLastFbid = getLastFBID();
                // If we have exactly currentMaxPicCount pictures then we reload one
                // time and got all, 2nd time will then be 0 more links
                // -> Stop
                if (currentLastFbid == null && dynamicLoadAlreadyDecrypted) {
                    break;
                } else if (currentLastFbid == null) {
                    logger.warning("Decrypter maybe broken for link: " + PARAMETER);
                    logger.info("Returning already decrypted links anyways...");
                    break;
                }
                final String urlload;
                final String data;
                if (this.PARAMETER.matches(TYPE_SET_LINK_VIDEO)) {
                    data = "{\"scroll_load\":true,\"last_fbid\":" + currentLastFbid + ",\"fetch_size\":32,\"viewmode\":null,\"profile_id\":" + profileID + ",\"set\":\"" + setid + "\",\"type\":2}";
                    urlload = "/ajax/pagelet/generic.php/TimelinePhotoSetPagelet?__pc=EXP1:DEFAULT&ajaxpipe=1&ajaxpipe_token=" + ajaxpipe_token + "&no_script_path=1&data=" + Encoding.urlEncode(data) + "&__user=" + user + "&__a=1&__dyn=" + dyn + "&__req=jsonp_" + i + "&__rev=" + rev + "&__adt=" + i;
                } else {
                    data = "{\"scroll_load\":true,\"last_fbid\":" + currentLastFbid + ",\"fetch_size\":32,\"profile_id\":" + profileID + ",\"tab\":\"photos_stream\",\"vanity\":\"" + url_username + "\",\"sk\":\"photos_stream\",\"tab_key\":\"photos_stream\",\"page\":" + profileID + ",\"is_medley_view\":true,\"pager_fired_on_init\":true}";
                    urlload = "/ajax/pagelet/generic.php/TimelinePhotosStreamPagelet?ajaxpipe=1&ajaxpipe_token=" + ajaxpipe_token + "&no_script_path=1&data=" + Encoding.urlEncode(data) + "&__user=" + user + "&__a=1&__dyn=" + dyn + "&__req=jsonp_" + i + "&__rev=" + rev + "&__adt=" + i;
                }
                // final String data = "{\"scroll_load\":true,\"last_fbid\":" + currentLastFbid + ",\"fetch_size\":32,\"profile_id\":" +
                // profileID + ",\"tab\":\"photos_stream\",\"ajaxpipe\":\"1\",\"ajaxpipe_token\":\"" + ajaxpipe_token +
                // "\",\"quickling\":{\"version\":\"" + rev + ";\"},\"__user\":\"" + user +
                // "\",\"__a\":\"1\",\"__dyn\":\"7nmajEyl35xKt2u6aEyx90BGUsx6bF3ozzkC-K26m6oKewWhEoyUnwPUS2O4K5fzEvFoy8ACxuFAdAw\",\"__req\":\"jsonp_"
                // + i + "\",\"__rev\":\"" + rev + "\",\"__adt\":\"7\",\"vanity\":\"" + url_username +
                // "\",\"sk\":\"photos_stream\",\"tab_key\":\"photos_stream\",\"page\":" + profileID + ",\"is_medley_view\":true}";
                br.getPage(urlload);
                br.getRequest().setHtmlCode(this.br.toString().replace("\\", ""));
                currentMaxPicCount = 40;
                dynamicLoadAlreadyDecrypted = true;
            }
            if (this.PARAMETER.matches(TYPE_SET_LINK_VIDEO)) {
                crawlVideos();
            } else {
                crawlImages();
            }
            linkscount_after = this.decryptedLinks.size();
            addedlinks = linkscount_after - linkscount_before;
            // currentMaxPicCount = max number of links per segment
            if (addedlinks < currentMaxPicCount) {
                logger.info("facebook.com: Found less pics than a full page -> Continuing anyways");
            }
            logger.info("facebook.com: Decrypted " + this.decryptedLinks.size() + " of " + totalPicsNum);
            if (timesNochange == 3) {
                logger.info("facebook.com: Three times no change -> Aborting decryption");
                break;
            }
            if (this.decryptedLinks.size() >= totalPicsNum) {
                logger.info("facebook.com: Decrypted all pictures -> Stopping");
                break;
            }
            if (this.decryptedLinks.size() == linkscount_before) {
                timesNochange++;
            } else {
                timesNochange = 0;
            }
        }
        fp.addLinks(decryptedLinks);
        if (this.decryptedLinks.size() < totalPicsNum && br.containsHTML("\"TimelineAppCollection\",\"setFullyLoaded\"")) {
            logger.info("facebook.com: -> Even though it seems like we don't have all images, that's all ;)");
        }
    }

    private void crawlImages() {
        String[] photoids = br.getRegex("ajax/photos/hovercard\\.php\\?fbid=(\\d+)\\&").getColumn(0);
        if (photoids == null || photoids.length == 0) {
            photoids = br.getRegex("id=\"pic_(\\d+)\"").getColumn(0);
        }
        for (final String picID : photoids) {
            final DownloadLink dl = createPicDownloadlink(picID);
            distribute(dl);
            decryptedLinks.add(dl);
        }
    }

    private void crawlVideos() {
        // String fpName = br.getRegex("<title id=\"pageTitle\">([^<>\"]*?)videos \\| Facebook</title>").getMatch(0);
        String[] links = br.getRegex("uiVideoLinkMedium\" href=\"https?://(?:www\\.)?facebook\\.com/(?:photo|video)\\.php\\?v=(\\d+)").getColumn(0);
        if (links == null || links.length == 0) {
            links = br.getRegex("ajaxify=\"(?:[^\"]+/videos/vb\\.\\d+/|[^\"]+/video\\.php\\?v=)(\\d+)").getColumn(0);
        }
        for (final String videoid : links) {
            final String videolink = "http://facebookdecrypted.com/video.php?v=" + videoid;
            final DownloadLink dl = createDownloadlink(videolink);
            dl.setContentUrl(videoid);
            dl.setLinkID(videoid);
            dl.setName(videoid + ".mp4");
            dl.setAvailable(true);
            decryptedLinks.add(dl);
        }
    }

    @SuppressWarnings("deprecation")
    private void decryptGroupsPhotos() throws Exception {
        String fpName = getPageTitle();
        final String rev = getRev(this.br);
        final String user = getUser(this.br);
        final String dyn = getDyn();
        final String totalPicCount = br.getRegex("data-medley-id=\"pagelet_timeline_medley_photos\">Photos<span class=\"_gs6\">(\\d+((,|\\.)\\d+)?)</span>").getMatch(0);
        final String ajaxpipe_token = getajaxpipeToken();
        final String controller = "GroupPhotosetPagelet";
        final String group_id = new Regex(PARAMETER, "groups/(\\d+)/").getMatch(0);
        if (user == null || ajaxpipe_token == null) {
            logger.warning("Decrypter broken for link: " + PARAMETER);
            return;
        }
        if (fpName == null) {
            fpName = "Facebook__groups_photos_of_user_" + user;
        }
        fpName = Encoding.htmlDecode(fpName.trim());
        final FilePackage fp = FilePackage.getInstance();
        fp.setName(fpName);
        boolean dynamicLoadAlreadyDecrypted = false;
        final ArrayList<String> allids = new ArrayList<String>();
        // Use this as default as an additional fail safe
        long totalPicsNum = MAX_PICS_DEFAULT;
        if (totalPicCount != null) {
            totalPicsNum = Long.parseLong(totalPicCount.replaceAll("(\\.|,)", ""));
        }
        int lastDecryptedPicsNum = 0;
        int decryptedPicsNum = 0;
        int timesNochange = 0;

        prepBrPhotoGrab();

        for (int i = 1; i <= MAX_LOOPS_GENERAL; i++) {
            try {
                if (this.isAbort()) {
                    logger.info("Decryption stopped at request " + i);
                    return;
                }
            } catch (final Throwable e) {
                // Not supported in old 0.9.581 Stable
            }

            int currentMaxPicCount = 20;

            String[] links;
            if (i > 1) {
                if (br.containsHTML("\"TimelineAppCollection\",\"setFullyLoaded\"")) {
                    logger.info("facebook.com: Server says the set is fully loaded -> Stopping");
                    break;
                }
                final String currentLastFbid = getLastFBID();
                // If we have exactly currentMaxPicCount pictures then we reload one
                // time and got all, 2nd time will then be 0 more links
                // -> Stop
                if (currentLastFbid == null && dynamicLoadAlreadyDecrypted) {
                    break;
                } else if (currentLastFbid == null) {
                    logger.warning("Decrypter maybe broken for link: " + PARAMETER);
                    logger.info("Returning already decrypted links anyways...");
                    break;
                }
                final String loadLink = MAINPAGE + "/ajax/pagelet/generic.php/" + controller + "?ajaxpipe=1&ajaxpipe_token=" + ajaxpipe_token + "&no_script_path=1&data=%7B%22scroll_load%22%3Atrue%2C%22last_fbid%22%3A" + currentLastFbid + "%2C%22fetch_size%22%3A120%2C%22group_id%22%3A" + group_id + "%7D&__user=" + user + "&__a=1&__dyn=" + dyn + "&__req=" + i + "&__rev=" + rev;
                getPage(loadLink);
                links = br.getRegex("\"(https?://(www\\.)?facebook\\.com/(photo\\.php\\?(fbid|v)=|media/set/\\?set=oa\\.)\\d+)").getColumn(0);
                currentMaxPicCount = 40;
                dynamicLoadAlreadyDecrypted = true;
            } else {
                links = br.getRegex("\"(https?://(www\\.)?facebook\\.com/(photo\\.php\\?(fbid|v)=|media/set/\\?set=oa\\.)\\d+)").getColumn(0);
            }
            if (links == null || links.length == 0) {
                logger.warning("Decryption done or decrypter broken: " + PARAMETER);
                return;
            }
            boolean stop = false;
            logger.info("Decrypting page " + i + " of ??");
            for (final String single_link : links) {
                final String current_id = new Regex(single_link, "(\\d+)$").getMatch(0);
                if (!allids.contains(current_id)) {
                    allids.add(current_id);
                    final DownloadLink dl = createDownloadlink(single_link.replace("facebook.com/", CRYPTLINK));
                    dl.setContentUrl(single_link);
                    if (!logged_in) {
                        dl.setProperty("nologin", true);
                    }
                    if (fastLinkcheckPictures) {
                        dl.setAvailable(true);
                    }
                    dl.setContentUrl(single_link);
                    // Set temp name, correct name will be set in hosterplugin later
                    dl.setName(current_id + ".jpg");
                    fp.add(dl);
                    distribute(dl);
                    decryptedLinks.add(dl);
                    decryptedPicsNum++;
                }
            }
            // currentMaxPicCount = max number of links per segment
            if (links.length < currentMaxPicCount) {
                logger.info("facebook.com: Found less pics than a full page -> Continuing anyways");
            }
            logger.info("facebook.com: Decrypted " + decryptedPicsNum + " of " + totalPicsNum);
            if (timesNochange == 3) {
                logger.info("facebook.com: Three times no change -> Aborting decryption");
                stop = true;
            }
            if (decryptedPicsNum >= totalPicsNum) {
                logger.info("facebook.com: Decrypted all pictures -> Stopping");
                stop = true;
            }
            if (decryptedPicsNum == lastDecryptedPicsNum) {
                timesNochange++;
            } else {
                timesNochange = 0;
            }
            lastDecryptedPicsNum = decryptedPicsNum;
            if (stop) {
                logger.info("facebook.com: Seems like we're done and decrypted all links, stopping at page: " + i);
                break;
            }
        }
        fp.addLinks(decryptedLinks);
        logger.info("facebook.com: Decrypted " + decryptedPicsNum + " of " + totalPicsNum);
        if (decryptedPicsNum < totalPicsNum && br.containsHTML("\"TimelineAppCollection\",\"setFullyLoaded\"")) {
            logger.info("facebook.com: -> Even though it seems like we don't have all images, that's all ;)");
        }

    }

    private void handlePagination() {

    }

    @SuppressWarnings("unchecked")
    private void decryptMessagePhotos() throws Exception {
        final String url_username = new Regex(this.PARAMETER, "/messages/(.+)").getMatch(0);
        if (!this.logged_in) {
            logger.info("Login required to decrypt photos of private messages");
            return;
        }
        this.getpagefirsttime(this.PARAMETER);
        final String thread_fbid = this.br.getRegex("" + url_username + "\",\"id\":\"fbid:(\\d+)\"").getMatch(0);
        final String user = getUser(this.br);
        if (thread_fbid == null || user == null) {
            /* Probably offline url */
            return;
        }
        final String postdata = "thread_id=" + thread_fbid + "&offset=0&limit=1000&__user=" + user + "&__a=1&__dyn=" + getDyn() + "&__req=19&fb_dtsg=" + getfb_dtsg() + "&ttstamp=" + System.currentTimeMillis() + "&__rev=" + getRev(this.br);
        /* First find all image-IDs */
        this.br.postPage("/ajax/messaging/attachments/sharedphotos.php", postdata);
        /* Secondly access each image individually to find its' final URL and download it */
        String json = this.br.getRegex("for \\(;;\\);(\\{.+)").getMatch(0);
        if (json == null) {
            this.decryptedLinks = null;
            return;
        }
        LinkedHashMap<String, Object> entries = (LinkedHashMap<String, Object>) jd.plugins.hoster.DummyScriptEnginePlugin.jsonToJavaObject(json);
        entries = (LinkedHashMap<String, Object>) DummyScriptEnginePlugin.walkJson(entries, "payload/imagesData");
        final Iterator<Entry<String, Object>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            final Entry<String, Object> entry = it.next();
            final String image_id = entry.getKey();
            final DownloadLink dl = createPicDownloadlink(image_id);
            dl.setProperty("is_private", true);
            dl.setProperty("thread_fbid", thread_fbid);
            this.decryptedLinks.add(dl);
        }
    }

    private void decryptNotes() throws Exception {
        final String html = br.getRegex("<div class=\"_4-u3 _5cla\">(.*?)class=\"commentable_item\"").getMatch(0);
        if (html == null) {
            throw new DecrypterException("Decrypter broken for link: " + PARAMETER);
        }
        final String[] urls = HTMLParser.getHttpLinks(html, null);
        for (final String url : urls) {
            this.decryptedLinks.add(this.createDownloadlink(url));
        }
    }

    // TODO: Use this everywhere as it should work universal
    private void decryptPicsGeneral(String controller) throws Exception {
        String fpName = getPageTitle();
        final String rev = getRev(this.br);
        final String user = getUser(this.br);
        final String dyn = getDyn();
        final String appcollection = br.getRegex("\"pagelet_timeline_app_collection_(\\d+:\\d+)(:\\d+)?\"").getMatch(0);
        final String profileID = getProfileID();
        final String totalPicCount = br.getRegex("data-medley-id=\"pagelet_timeline_medley_photos\">Photos<span class=\"_gs6\">(\\d+((,|\\.)\\d+)?)</span>").getMatch(0);
        if (controller == null) {
            controller = br.getRegex("\"photos\",\\[\\{\"controller\":\"([^<>\"]*?)\"").getMatch(0);
        }
        if (user == null || profileID == null || appcollection == null) {
            logger.warning("Decrypter broken for link: " + PARAMETER);
            return;
        }
        if (fpName == null) {
            fpName = "Facebook_photos_of_of_user_" + user;
        }
        fpName = Encoding.htmlDecode(fpName.trim());
        final FilePackage fp = FilePackage.getInstance();
        fp.setName(fpName);
        boolean dynamicLoadAlreadyDecrypted = false;
        final ArrayList<String> allids = new ArrayList<String>();
        // Use this as default as an additional fail safe
        long totalPicsNum = MAX_PICS_DEFAULT;
        if (totalPicCount != null) {
            totalPicsNum = Long.parseLong(totalPicCount.replaceAll("(\\.|,)", ""));
        }
        int lastDecryptedPicsNum = 0;
        int decryptedPicsNum = 0;
        int timesNochange = 0;

        prepBrPhotoGrab();

        for (int i = 1; i <= MAX_LOOPS_GENERAL; i++) {
            try {
                if (this.isAbort()) {
                    logger.info("Decryption stopped at request " + i);
                    return;
                }
            } catch (final Throwable e) {
                // Not supported in old 0.9.581 Stable
            }

            int currentMaxPicCount = 20;

            String[] links;
            if (i > 1) {
                if (br.containsHTML("\"TimelineAppCollection\",\"setFullyLoaded\"")) {
                    logger.info("facebook.com: Server says the set is fully loaded -> Stopping");
                    break;
                }
                final String cursor = br.getRegex("\\[\"pagelet_timeline_app_collection_[^<>\"]*?\",\\{\"[^<>\"]*?\":\"[^<>\"]*?\"\\},\"([^<>\"]*?)\"").getMatch(0);
                // If we have exactly currentMaxPicCount pictures then we reload one
                // time and got all, 2nd time will then be 0 more links
                // -> Stop
                if (cursor == null && dynamicLoadAlreadyDecrypted) {
                    break;
                } else if (cursor == null) {
                    logger.warning("Decrypter maybe broken for link: " + PARAMETER);
                    logger.info("Returning already decrypted links anyways...");
                    break;
                }
                final String loadLink = MAINPAGE + "/ajax/pagelet/generic.php/" + controller + "?data=%7B%22collection_token%22%3A%22" + Encoding.urlEncode(appcollection) + "%3A5%22%2C%22cursor%22%3A%22" + cursor + "%22%2C%22tab_key%22%3A%22photos_all%22%2C%22profile_id%22%3A" + profileID + "%2C%22overview%22%3Afalse%2C%22ftid%22%3Anull%2C%22order%22%3Anull%2C%22sk%22%3A%22photos%22%2C%22importer_state%22%3Anull%7D&__user=" + user + "&__a=1&__dyn=" + dyn + "&__req=" + i + "&__rev=" + rev;
                br.getPage(loadLink);
                links = br.getRegex("ajax\\\\/photos\\\\/hovercard\\.php\\?fbid=(\\d+)\\&").getColumn(0);
                currentMaxPicCount = 40;
                dynamicLoadAlreadyDecrypted = true;
            } else {
                links = br.getRegex("id=\"pic_(\\d+)\"").getColumn(0);
            }
            if (links == null || links.length == 0) {
                logger.warning("Decryption done or decrypter broken: " + PARAMETER);
                return;
            }
            boolean stop = false;
            logger.info("Decrypting page " + i + " of ??");
            for (final String picID : links) {
                if (!allids.contains(picID)) {
                    allids.add(picID);
                    final DownloadLink dl = createPicDownloadlink(picID);
                    fp.add(dl);
                    try {
                        distribute(dl);
                    } catch (final Throwable e) {
                        // Not supported in old 0.9.581 Stable
                    }
                    decryptedLinks.add(dl);
                    decryptedPicsNum++;
                }
            }
            // currentMaxPicCount = max number of links per segment
            if (links.length < currentMaxPicCount) {
                logger.info("facebook.com: Found less pics than a full page -> Continuing anyways");
            }
            logger.info("facebook.com: Decrypted " + decryptedPicsNum + " of " + totalPicsNum);
            if (timesNochange == 3) {
                logger.info("facebook.com: Three times no change -> Aborting decryption");
                stop = true;
            }
            if (decryptedPicsNum >= totalPicsNum) {
                logger.info("facebook.com: Decrypted all pictures -> Stopping");
                stop = true;
            }
            if (decryptedPicsNum == lastDecryptedPicsNum) {
                timesNochange++;
            } else {
                timesNochange = 0;
            }
            lastDecryptedPicsNum = decryptedPicsNum;
            if (stop) {
                logger.info("facebook.com: Seems like we're done and decrypted all links, stopping at page: " + i);
                break;
            }
        }
        fp.addLinks(decryptedLinks);
        logger.info("facebook.com: Decrypted " + decryptedPicsNum + " of " + totalPicsNum);
        if (decryptedPicsNum < totalPicsNum && br.containsHTML("\"TimelineAppCollection\",\"setFullyLoaded\"")) {
            logger.info("facebook.com: -> Even though it seems like we don't have all images, that's all ;)");
        }
    }

    private void getpagefirsttime(final String parameter) throws IOException {
        // Redirects from "http" to "https" can happen
        br.getHeaders().put("Accept-Language", "en-US,en;q=0.5");
        br.setCookie(FACEBOOKMAINPAGE, "locale", "en_GB");
        br.setFollowRedirects(true);
        br.getPage(parameter);
        String scriptRedirect = br.getRegex("<script>window\\.location\\.replace\\(\"(https?:[^<>\"]*?)\"\\);</script>").getMatch(0);
        if (scriptRedirect != null) {
            scriptRedirect = JSonUtils.unescape(scriptRedirect);
            br.getPage(scriptRedirect);
        }
        String normal_redirect = br.getRegex("<meta http\\-equiv=\"refresh\" content=\"0; URL=(/[^<>\"]*?)\"").getMatch(0);
        /* Do not access the noscript versions of links - this will cause out of date errors! */
        if (normal_redirect != null && !normal_redirect.contains("fb_noscript=1")) {
            normal_redirect = Encoding.htmlDecode(normal_redirect);
            normal_redirect = normal_redirect.replace("u00253A", "%3A");
            br.getPage("https://www.facebook.com" + normal_redirect);
        }
        if (br.getURL().contains("https://")) {
            MAINPAGE = "https://www.facebook.com";
        }
    }

    private DownloadLink createPicDownloadlink(final String picID) {
        final String final_link = "http://www." + CRYPTLINK + "photo.php?fbid=" + picID;
        final String real_link = "http://www.facebook.com/photo.php?fbid=" + picID;
        final DownloadLink dl = createDownloadlink(final_link);
        dl.setContentUrl(real_link);
        if (!logged_in) {
            dl.setProperty("nologin", true);
        }
        if (fastLinkcheckPictures) {
            dl.setAvailable(true);
        }
        // Set temp name, correct name will be set in hosterplugin later
        dl.setName(picID + ".jpg");
        return dl;
    }

    private String getProfileID() {
        String profileid = br.getRegex("data\\-gt=\"\\&#123;\\&quot;profile_owner\\&quot;:\\&quot;(\\d+)\\&quot;").getMatch(0);
        if (profileid == null) {
            profileid = br.getRegex("PageHeaderPageletController_(\\d+)\"").getMatch(0);
        }
        if (profileid == null) {
            profileid = br.getRegex("data\\-profileid=\"(\\d+)\"").getMatch(0);
        }
        return profileid;
    }

    private String getajaxpipeToken() {
        return br.getRegex("\"ajaxpipe_token\":\"([^<>\"]*?)\"").getMatch(0);
    }

    public static String getRev(final Browser br) {
        String rev = br.getRegex("\"revision\":(\\d+)").getMatch(0);
        if (rev == null) {
            rev = REV;
        }
        return rev;
    }

    private String getPageTitle() {
        return br.getRegex("id=\"pageTitle\">([^<>\"]*?)</title>").getMatch(0);
    }

    public static String getDyn() {
        return "7n8apij2qmumdDgDxyIJ3Ga58Ciq2W8GA8ABGeqheCu6po";
    }

    private String getLastFBID() {
        String currentLastFbid = br.getRegex("\"last_fbid\\\\\":\\\\\"(\\d+)\\\\\\\"").getMatch(0);
        if (currentLastFbid == null) {
            currentLastFbid = br.getRegex("\"last_fbid\\\\\":(\\d+)").getMatch(0);
        }
        if (currentLastFbid == null) {
            currentLastFbid = br.getRegex("\"last_fbid\":(\\d+)").getMatch(0);
        }
        if (currentLastFbid == null) {
            currentLastFbid = br.getRegex("\"last_fbid\":\"(\\d+)\"").getMatch(0);
        }
        return currentLastFbid;
    }

    private void prepBrPhotoGrab() {
        br.getHeaders().put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        br.getHeaders().put("Accept-Language", "de,en-us;q=0.7,en;q=0.3");
        br.getHeaders().put("Accept-Charset", null);
    }

    private void getPage(final String link) throws IOException {
        br.getPage(link);
        br.getRequest().setHtmlCode(br.toString().replace("\\", ""));
    }

    public static String getUser(final Browser br) {
        String user = br.getRegex("\"user\":\"(\\d+)\"").getMatch(0);
        if (user == null) {
            user = br.getRegex("detect_broken_proxy_cache\\(\"(\\d+)\", \"c_user\"\\)").getMatch(0);
        }
        // regex verified: 10.2.2014
        if (user == null) {
            user = br.getRegex("\\[(\\d+)\\,\"c_user\"").getMatch(0);
        }
        return user;
    }

    public static String getfb_dtsg() {
        return "fb_dtsg";
    }

    /**
     * JD2 CODE: DO NOIT USE OVERRIDE FÒR COMPATIBILITY REASONS!!!!!
     */
    public boolean isProxyRotationEnabledForLinkCrawler() {
        return false;
    }

    @SuppressWarnings("deprecation")
    private boolean login() throws Exception {
        /** Login stuff begin */
        final PluginForHost facebookPlugin = JDUtilities.getPluginForHost("facebook.com");
        Account aa = AccountController.getInstance().getValidAccount(facebookPlugin);
        boolean addAcc = false;
        if (aa == null) {
            SubConfiguration config = null;
            try {
                config = this.getPluginConfig();
                if (config.getBooleanProperty("infoShown", Boolean.FALSE) == false) {
                    if (config.getProperty("infoShown2") == null) {
                        showFreeDialog();
                    } else {
                        config = null;
                    }
                } else {
                    config = null;
                }
            } catch (final Throwable e) {
            } finally {
                if (config != null) {
                    config.setProperty("infoShown", Boolean.TRUE);
                    config.setProperty("infoShown2", "shown");
                    config.save();
                }
            }
            // User wants to use the account
            if (this.DIALOGRETURN == 0) {
                String username = UserIO.getInstance().requestInputDialog("Enter Loginname for facebook.com :");
                if (username == null) {
                    return false;
                }
                String password = UserIO.getInstance().requestInputDialog("Enter password for facebook.com :");
                if (password == null) {
                    return false;
                }
                aa = new Account(username, password);
                addAcc = true;
            }
        }
        if (aa != null) {
            try {
                ((jd.plugins.hoster.FaceBookComVideos) facebookPlugin).login(aa, this.br);
                // New account is valid, let's add it to the premium overview
                if (addAcc) {
                    AccountController.getInstance().addAccount(facebookPlugin, aa);
                }
                return true;
            } catch (final PluginException e) {

                aa.setValid(false);
                logger.info("Account seems to be invalid, returnung empty linklist!");
                return false;
            }
        }
        return false;
        /** Login stuff end */
    }

    /**
     * Wrapper<br/>
     * Tries to return value of key from JSon response, from default 'br' Browser.
     *
     * @author raztoki
     */
    private String getJson(final String key) {
        return jd.plugins.hoster.K2SApi.JSonUtils.getJson(br.toString(), key);
    }

    private void showFreeDialog() {
        try {
            SwingUtilities.invokeAndWait(new Runnable() {

                @Override
                public void run() {
                    try {
                        final String lng = System.getProperty("user.language");
                        String message = null;
                        String title = null;
                        if ("de".equalsIgnoreCase(lng)) {
                            title = "Facebook.com Gallerie/Photo Download";
                            message = "Du versucht gerade, eine Facebook Gallerie/Photo zu laden.\r\n";
                            message += "Für die meisten dieser Links wird ein gültiger Facebook Account benötigt!\r\n";
                            message += "Deinen Account kannst du in den Einstellungen als Premiumaccount hinzufügen.\r\n";
                            message += "Solltest du dies nicht tun, kann JDownloader nur Facebook Links laden, die keinen Account benötigen!\r\n";
                            message += "Willst du deinen Facebook Account jetzt hinzufügen?\r\n";
                        } else {
                            title = "Facebook.com gallery/photo download";
                            message = "You're trying to download a Facebook gallery/photo.\r\n";
                            message += "For most of these links, a valid Facebook account is needed!\r\n";
                            message += "You can add your account as a premium account in the settings.\r\n";
                            message += "Note that if you don't do that, JDownloader will only be able to download Facebook links which do not need a login.\r\n";
                            message += "Do you want to enter your Facebook account now?\r\n";
                        }
                        DIALOGRETURN = JOptionPane.showConfirmDialog(jd.gui.swing.jdgui.JDGui.getInstance().getMainFrame(), message, title, JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE, null);
                    } catch (Throwable e) {
                    }
                }
            });
        } catch (Throwable e) {
        }
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }

}