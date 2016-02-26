//    jDownloader - Downloadmanager
//    Copyright (C) 2009  JD-Team support@jdownloader.org
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

package jd.plugins.decrypter;

import java.awt.Dialog.ModalityType;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.appwork.exceptions.WTFException;
import org.appwork.storage.JSonStorage;
import org.appwork.txtresource.TranslationFactory;
import org.appwork.uio.ConfirmDialogInterface;
import org.appwork.uio.UIOManager;
import org.appwork.utils.Application;
import org.appwork.utils.StringUtils;
import org.appwork.utils.swing.dialog.ConfirmDialog;
import org.appwork.utils.swing.dialog.DialogCanceledException;
import org.appwork.utils.swing.dialog.DialogClosedException;
import org.jdownloader.plugins.config.PluginJsonConfig;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.http.Browser.BrowserException;
import jd.nutils.encoding.Encoding;
import jd.nutils.encoding.HTMLEntities;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginForDecrypt;
import jd.plugins.components.YoutubeClipData;
import jd.plugins.components.YoutubeITAG;
import jd.plugins.components.YoutubeStreamData;
import jd.plugins.components.YoutubeSubtitleInfo;
import jd.plugins.components.YoutubeVariant;
import jd.plugins.components.YoutubeVariantInterface;
import jd.plugins.components.youtube.VideoResolution;
import jd.plugins.hoster.YoutubeDashV2.YoutubeConfig;
import jd.plugins.hoster.YoutubeDashV2.YoutubeConfig.GroupLogic;
import jd.plugins.hoster.YoutubeDashV2.YoutubeConfig.IfUrlisAPlaylistAction;
import jd.plugins.hoster.YoutubeDashV2.YoutubeConfig.IfUrlisAVideoAndPlaylistAction;
import jd.utils.JDUtilities;
import jd.utils.locale.JDL;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "youtube.com", "youtube.com", "youtube.com" }, urls = { "https?://([a-z]+\\.)?yt\\.not\\.allowed/.+", "https?://([a-z]+\\.)?youtube\\.com/(embed/|.*?watch.*?v(%3D|=)|view_play_list\\?p=|playlist\\?(p|list)=|.*?g/c/|.*?grid/user/|v/|user/|channel/|course\\?list=)[A-Za-z0-9\\-_]+(.*?page=\\d+)?(.*?list=[A-Za-z0-9\\-_]+)?(\\&variant=[a-z\\_0-9]+)?|watch_videos\\?.*?video_ids=.+", "https?://youtube\\.googleapis\\.com/(v/|user/|channel/)[A-Za-z0-9\\-_]+(\\?variant=[a-z_0.9]+)?" }, flags = { 0, 0, 0 })
public class TbCmV2 extends PluginForDecrypt {

    private static final int DDOS_WAIT_MAX        = Application.isJared(null) ? 1000 : 10;

    private static final int DDOS_INCREASE_FACTOR = 15;

    public TbCmV2(PluginWrapper wrapper) {
        super(wrapper);
    };

    /**
     * Returns host from provided String.
     */
    static String getBase() {
        // YoutubeConfig cfg = PluginJsonConfig.get(YoutubeConfig.class);
        // boolean prefers = cfg.isPreferHttpsEnabled();
        //
        // if (prefers) {
        return "https://www.youtube.com";
        // } else {
        // return "http://www.youtube.com";
        // }

    }

    /**
     * Returns a ListID from provided String.
     */
    private String getListIDByUrls(String originUrl) {
        // String list = null;
        // http://www.youtube.com/user/wirypodge#grid/user/41F2A8E7EBF86D7F
        // list = new Regex(originUrl, "(g/c/|grid/user/)([A-Za-z0-9\\-_]+)").getMatch(1);
        // /user/
        // http://www.youtube.com/user/Gronkh
        // if (list == null) list = new Regex(originUrl, "/user/([A-Za-z0-9\\-_]+)").getMatch(0);
        // play && course
        // http://www.youtube.com/playlist?list=PL375B54C39ED612FC

        return new Regex(originUrl, "list=([A-Za-z0-9\\-_]+)").getMatch(0);
    }

    private String getVideoIDByUrl(String URL) {
        String vuid = new Regex(URL, "v=([A-Za-z0-9\\-_]+)").getMatch(0);
        if (vuid == null) {
            vuid = new Regex(URL, "v/([A-Za-z0-9\\-_]+)").getMatch(0);
            if (vuid == null) {
                vuid = new Regex(URL, "embed/(?!videoseries\\?)([A-Za-z0-9\\-_]+)").getMatch(0);
            }
        }
        return vuid;
    }

    private HashSet<String> dupeCheckSet;

    private YoutubeConfig   cfg;
    private YoutubeHelper   cachedHelper;

    public static class VariantInfo implements Comparable<VariantInfo> {

        protected final YoutubeVariantInterface variant;
        private final YoutubeStreamData         audioStream;
        private final YoutubeStreamData         videoStream;
        public String                           special = "";
        private final YoutubeStreamData         data;

        public YoutubeStreamData getData() {
            return data;
        }

        @Override
        public String toString() {
            return getIdentifier();
        }

        public String getIdentifier() {
            return variant._getUniqueId();
        }

        public VariantInfo(YoutubeVariantInterface v, YoutubeStreamData audio, YoutubeStreamData video, YoutubeStreamData data) {
            this.variant = v;
            this.audioStream = audio;
            this.videoStream = video;
            this.data = data;
        }

        public YoutubeVariantInterface getVariant() {
            return variant;
        }

        public void fillExtraProperties(DownloadLink thislink, List<VariantInfo> alternatives) {
        }

        @Override
        public int compareTo(VariantInfo o) {
            return new Double(o.variant.getQualityRating()).compareTo(new Double(variant.getQualityRating()));
        }

    }

    private static Object DIALOGLOCK = new Object();

    private String        videoID;
    private String        watch_videos;
    private String        playlistID;
    private String        channelID;
    private String        userID;

    public ArrayList<DownloadLink> decryptIt(final CryptedLink param, final ProgressController progress) throws Exception {
        cfg = PluginJsonConfig.get(YoutubeConfig.class);
        String cryptedLink = param.getCryptedUrl();
        if (StringUtils.containsIgnoreCase(cryptedLink, "yt.not.allowed")) {
            final ArrayList<DownloadLink> ret = new ArrayList<DownloadLink>();
            if (cfg.isAndroidSupportEnabled()) {
                if (cryptedLink.matches("https?://[\\w\\.]*yt\\.not\\.allowed/[a-z_A-Z0-9\\-]+")) {
                    cryptedLink = cryptedLink.replaceFirst("yt\\.not\\.allowed", "youtu.be");
                } else {
                    cryptedLink = cryptedLink.replaceFirst("yt\\.not\\.allowed", "youtube.com");
                }
                final DownloadLink link = createDownloadlink(cryptedLink);
                link.setContainerUrl(cryptedLink);
                ret.add(link);
            }
            return ret;
        }
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>() {
            @Override
            public boolean add(DownloadLink e) {
                distribute(e);
                return super.add(e);
            }
        };
        dupeCheckSet = new HashSet<String>();
        br.setFollowRedirects(true);
        br.setCookiesExclusive(true);
        br.clearCookies("youtube.com");
        br.setCookie("http://youtube.com", "PREF", "hl=en-GB");

        String cleanedurl = Encoding.urlDecode(cryptedLink, false);
        cleanedurl = cleanedurl.replace("youtube.jd", "youtube.com");
        String requestedVariant = new Regex(cleanedurl, "\\&variant=([a-z\\_0-9]+)").getMatch(0);
        if (requestedVariant != null) {
            requestedVariant = Encoding.urlDecode(requestedVariant, false);
        }
        cleanedurl = cleanedurl.replace("\\&variant=[a-z\\_0-9]+", "");
        cleanedurl = cleanedurl.replace("/embed/", "/watch?v=");
        videoID = getVideoIDByUrl(cleanedurl);
        // for watch_videos, found within youtube.com music
        watch_videos = new Regex(cleanedurl, "video_ids=([a-zA-Z0-9\\-_,]+)").getMatch(0);
        if (watch_videos != null) {
            // first uid in array is the video the user copy url on.
            videoID = new Regex(watch_videos, "([a-zA-Z0-9\\-_]+)").getMatch(0);
        }
        playlistID = getListIDByUrls(cleanedurl);
        userID = new Regex(cleanedurl, "/user/([A-Za-z0-9\\-_]+)").getMatch(0);
        channelID = new Regex(cleanedurl, "/channel/([A-Za-z0-9\\-_]+)").getMatch(0);

        YoutubeHelper helper = getCachedHelper();

        helper.login(false, false);
        synchronized (DIALOGLOCK) {
            if (this.isAbort()) {
                logger.info("Thread Aborted!");
                return decryptedLinks;
            }
            {
                // Prevents accidental decrypting of entire Play-List or Channel-List or User-List.
                IfUrlisAPlaylistAction playListAction = cfg.getLinkIsPlaylistUrlAction();
                if ((StringUtils.isNotEmpty(playlistID) || StringUtils.isNotEmpty(channelID) || StringUtils.isNotEmpty(userID)) && StringUtils.isEmpty(videoID)) {

                    if (playListAction == IfUrlisAPlaylistAction.ASK) {
                        ConfirmDialog confirm = new ConfirmDialog(UIOManager.LOGIC_COUNTDOWN, cleanedurl, JDL.L("plugins.host.youtube.isplaylist.question.message", "This link is a Play-List or Channel-List or User-List. What would you like to do?"), null, JDL.L("plugins.host.youtube.isplaylist.question.onlyplaylist", "Process Playlist?"), JDL.L("plugins.host.youtube.isvideoandplaylist.question.nothing", "Do Nothing?")) {
                            @Override
                            public ModalityType getModalityType() {
                                return ModalityType.MODELESS;
                            }

                            @Override
                            public boolean isRemoteAPIEnabled() {
                                return true;
                            }
                        };
                        try {
                            UIOManager.I().show(ConfirmDialogInterface.class, confirm).throwCloseExceptions();
                            playListAction = IfUrlisAPlaylistAction.PROCESS;
                        } catch (DialogCanceledException e) {
                            playListAction = IfUrlisAPlaylistAction.NOTHING;
                        } catch (DialogClosedException e) {
                            playListAction = IfUrlisAPlaylistAction.NOTHING;
                        }

                    }
                    switch (playListAction) {
                    case PROCESS:
                        break;
                    case NOTHING:
                    default:
                        return decryptedLinks;
                    }
                }
            }
            {

                // Check if link contains a video and a playlist
                IfUrlisAVideoAndPlaylistAction PlaylistVideoAction = cfg.getLinkIsVideoAndPlaylistUrlAction();
                if ((StringUtils.isNotEmpty(playlistID) || StringUtils.isNotEmpty(watch_videos)) && StringUtils.isNotEmpty(videoID)) {

                    if (PlaylistVideoAction == IfUrlisAVideoAndPlaylistAction.ASK) {
                        ConfirmDialog confirm = new ConfirmDialog(UIOManager.LOGIC_COUNTDOWN, cleanedurl, JDL.L("plugins.host.youtube.isvideoandplaylist.question.message", "The Youtube link contains a video and a playlist. What do you want do download?"), null, JDL.L("plugins.host.youtube.isvideoandplaylist.question.onlyvideo", "Only video"), JDL.L("plugins.host.youtube.isvideoandplaylist.question.playlist", "Complete playlist")) {
                            @Override
                            public ModalityType getModalityType() {
                                return ModalityType.MODELESS;
                            }

                            @Override
                            public boolean isRemoteAPIEnabled() {
                                return true;
                            }
                        };
                        try {
                            UIOManager.I().show(ConfirmDialogInterface.class, confirm).throwCloseExceptions();
                            PlaylistVideoAction = IfUrlisAVideoAndPlaylistAction.VIDEO_ONLY;
                        } catch (DialogCanceledException e) {
                            PlaylistVideoAction = IfUrlisAVideoAndPlaylistAction.PLAYLIST_ONLY;
                        } catch (DialogClosedException e) {
                            PlaylistVideoAction = IfUrlisAVideoAndPlaylistAction.NOTHING;
                        }

                    }
                    switch (PlaylistVideoAction) {

                    case PLAYLIST_ONLY:
                        // videoID = null;
                        break;
                    case VIDEO_ONLY:
                        playlistID = null;
                        watch_videos = null;
                        break;
                    default:
                        return decryptedLinks;
                    }
                }
            }
        }
        ArrayList<YoutubeClipData> videoIdsToAdd = new ArrayList<YoutubeClipData>();
        try {
            boolean userWorkaround = false;
            boolean channelWorkaround = false;
            if (StringUtils.isNotEmpty(userID) && StringUtils.isEmpty(playlistID)) {

                // the user channel parser only parses 1050 videos. this workaround finds the user channel playlist and parses this playlist
                // instead
                br.getPage("https://www.youtube.com/user/" + userID + "/featured");

                playlistID = br.getRegex(">Uploads</span>.*?list=([A-Za-z0-9\\-_]+)\".+?play-all-icon-btn").getMatch(0);
                userWorkaround = StringUtils.isNotEmpty(playlistID);
            }
            if (StringUtils.isNotEmpty(channelID) && StringUtils.isEmpty(playlistID)) {

                // the user channel parser only parses 1050 videos. this workaround finds the user channel playlist and parses this playlist
                // instead
                br.getPage("https://www.youtube.com/channel/" + channelID);

                playlistID = br.getRegex("list=([A-Za-z0-9\\-_]+)\".+?play-all-icon-btn").getMatch(0);
                if (StringUtils.isEmpty(playlistID) && channelID.startsWith("UC")) {
                    // channel has no play all button.
                    // like https://www.youtube.com/channel/UCbmRs17gtQxFXQyvIo5k6Ag/feed
                    playlistID = "UU" + channelID.substring(2);
                }
                channelWorkaround = StringUtils.isNotEmpty(playlistID);
            }

            ArrayList<YoutubeClipData> playlist;
            videoIdsToAdd.addAll(playlist = parsePlaylist(playlistID));
            if (channelWorkaround && playlist.size() == 0) {

                // failed
                channelWorkaround = false;
            }
            if (userWorkaround && playlist.size() == 0) {
                // failed
                userWorkaround = false;
            }
            if (!channelWorkaround) {
                videoIdsToAdd.addAll(parseChannelgrid(channelID));
            }
            if (!userWorkaround) {
                videoIdsToAdd.addAll(parseUsergrid(userID));
            }
            // some unknown playlist type?
            if (videoIdsToAdd.size() == 0 && StringUtils.isNotEmpty(playlistID)) {
                videoIdsToAdd.addAll(parseGeneric(cleanedurl));
            }
            videoIdsToAdd.addAll(parseVideoIds(watch_videos));

            if (StringUtils.isNotEmpty(videoID) && dupeCheckSet.add(videoID)) {
                videoIdsToAdd.add(new jd.plugins.components.YoutubeClipData(videoID));
            }
            if (videoIdsToAdd.size() == 0) {
                videoIdsToAdd.addAll(parseGeneric(cleanedurl));
            }

        } catch (InterruptedException e) {
            return decryptedLinks;
        }

        // HashSet<YoutubeVariantInterface> blacklistedVariants = new HashSet<YoutubeVariantInterface>();
        HashSet<String> blacklistedStrings = new HashSet<String>();
        HashSet<String> extraStrings = new HashSet<String>();

        String[] blacklist = cfg.getBlacklistedVariants();
        if (blacklist != null) {

            for (String ytv : blacklist) {

                YoutubeVariantInterface v = helper.getVariantById(ytv);
                // if (v != null) {
                // blacklistedVariants.add(v);
                // }

                blacklistedStrings.add(ytv);
            }
        }
        String[] extra = cfg.getExtraVariants();
        if (extra != null) {
            for (String s : extra) {
                extraStrings.add(s);
            }
        }
        if (requestedVariant != null) {
            extraStrings.add(requestedVariant);
        }
        for (YoutubeClipData vid : videoIdsToAdd) {
            if (this.isAbort()) {
                throw new InterruptedException();
            }
            HashMap<String, List<VariantInfo>> groups = new HashMap<String, List<VariantInfo>>();
            HashMap<String, List<VariantInfo>> groupsExcluded = new HashMap<String, List<VariantInfo>>();
            HashMap<YoutubeVariantInterface, VariantInfo> allVariants = new HashMap<YoutubeVariantInterface, VariantInfo>();
            HashMap<String, VariantInfo> idMap = new HashMap<String, VariantInfo>();
            Map<YoutubeITAG, YoutubeStreamData> vc = null;
            try {
                vc = helper.loadVideo(vid);
            } catch (Exception e) {
                String emsg = null;
                try {
                    emsg = e.getMessage().toString();
                } catch (NullPointerException npe) {
                    // e.message can be null...
                }
                if (emsg != null && (emsg.contains(YoutubeHelper.PAID_VIDEO))) {
                    vid.error = emsg;
                } else {
                    throw e;
                }
            }
            if (vc == null || StringUtils.isNotEmpty(vid.error)) {
                decryptedLinks.add(createOfflinelink("http://youtube.com/watch?v=" + vid.videoID, "Error - " + vid.videoID + (vid.title != null ? " [" + vid.title + "]:" : "") + " " + vid.error, vid.error));
                if (vc == null) {
                    continue;
                }
            }

            YoutubeITAG bestVideoResolution = null;
            for (Entry<YoutubeITAG, YoutubeStreamData> es : vc.entrySet()) {
                if (es.getKey().getQualityVideo() != null) {
                    if (bestVideoResolution == null || bestVideoResolution.getQualityRating() < es.getKey().getQualityRating()) {
                        bestVideoResolution = es.getKey();
                    }
                }

            }
            vid.bestVideoItag = bestVideoResolution;
            if (!cfg.isExternMultimediaToolUsageEnabled()) {
                getLogger().info("isDashEnabledEnabled=false");
            }
            for (YoutubeVariantInterface v : helper.getVariants()) {

                if (!cfg.isExternMultimediaToolUsageEnabled() && v instanceof YoutubeVariant && ((YoutubeVariant) v).isVideoToolRequired()) {

                    continue;
                }
                // System.out.println("test for " + v);
                String groupID = getGroupID(v);

                YoutubeStreamData audio = null;
                YoutubeStreamData video = null;
                YoutubeStreamData data = null;
                boolean valid = v.getiTagVideo() != null || v.getiTagAudio() != null || v.getiTagData() != null;

                if (v.getiTagVideo() != null) {
                    video = vc.get(v.getiTagVideo());
                    if (video == null) {
                        valid = false;
                    }
                }
                if (v.getiTagAudio() != null) {
                    audio = vc.get(v.getiTagAudio());
                    if (audio == null) {
                        valid = false;
                    }
                }
                if (v.getiTagData() != null) {
                    data = vc.get(v.getiTagData());
                    if (data == null) {
                        valid = false;
                    }
                }

                if (valid) {
                    VariantInfo vi = new VariantInfo(v, audio, video, data);
                    if ((blacklistedStrings.contains(v.getTypeId()) || blacklistedStrings.contains(v._getUniqueId())) && !extraStrings.contains(v.getTypeId()) && !extraStrings.contains(v._getUniqueId())) {
                        logger.info("Variant blacklisted:" + v);
                        List<VariantInfo> list = groupsExcluded.get(groupID);
                        if (list == null) {
                            list = new ArrayList<TbCmV2.VariantInfo>();
                            groupsExcluded.put(groupID, list);
                        }
                        list.add(vi);

                    } else {
                        // if we have several variants with the same id, use the one with the highest rating.
                        // example: mp3 conversion can be done from a high and lower video. audio is the same. we should prefer the lq video
                        VariantInfo mapped = idMap.get(v.getTypeId());

                        if (mapped == null || v.getQualityRating() > mapped.variant.getQualityRating()) {
                            idMap.put(v.getTypeId(), vi);
                            // remove old mapping
                            if (mapped != null) {
                                getLogger().info("Removed Type Dupe: " + mapped);
                                String mappedGroupID = getGroupID(mapped.variant);
                                List<VariantInfo> list = groups.get(mappedGroupID);
                                if (list != null) {
                                    list.remove(mapped);
                                }

                                allVariants.remove(mapped.variant);
                            }

                        } else {
                            // we already have a better quality of this variant id
                            continue;
                        }

                        List<VariantInfo> list = groups.get(groupID);
                        if (list == null) {
                            list = new ArrayList<TbCmV2.VariantInfo>();
                            groups.put(groupID, list);
                        }

                        list.add(vi);
                    }
                    // System.out.println("Variant found " + v);

                    allVariants.put(v, vi);
                } else {

                    // System.out.println("Variant NOT found " + v);
                }

            }

            List<VariantInfo> allSubtitles = new ArrayList<VariantInfo>();
            if (cfg.isSubtitlesEnabled()) {
                ArrayList<YoutubeSubtitleInfo> subtitles = helper.loadSubtitles();

                ArrayList<String> whitelist = cfg.getSubtitleWhiteList();

                for (final YoutubeSubtitleInfo si : subtitles) {

                    try {

                        String groupID;
                        switch (cfg.getGroupLogic()) {
                        case BY_FILE_TYPE:
                            groupID = "srt";
                            break;
                        case NO_GROUP:
                            groupID = "srt-" + si.getLanguage();
                            break;
                        case BY_MEDIA_TYPE:
                            groupID = YoutubeVariantInterface.VariantGroup.SUBTITLES.name();
                            break;
                        default:
                            throw new WTFException("Unknown Grouping");
                        }
                        VariantInfo vi = new VariantInfo(YoutubeVariant.SUBTITLES, null, null, new YoutubeStreamData(vid, si._getUrl(vid.videoID), YoutubeITAG.SUBTITLE)) {

                            @Override
                            public void fillExtraProperties(DownloadLink thislink, List<VariantInfo> alternatives) {
                                thislink.setProperty(YoutubeHelper.YT_SUBTITLE_CODE, si._getIdentifier());
                                final ArrayList<String> lngCodes = new ArrayList<String>();
                                for (final VariantInfo si : alternatives) {
                                    lngCodes.add(si.getIdentifier());
                                }
                                thislink.setProperty(YoutubeHelper.YT_SUBTITLE_CODE_LIST, JSonStorage.serializeToJson(lngCodes));
                            }

                            @Override
                            public String getIdentifier() {
                                return si._getIdentifier();
                            }

                            @Override
                            public int compareTo(VariantInfo o) {
                                return this.variant._getName().compareToIgnoreCase(o.variant._getName());

                            }
                        };
                        if (whitelist != null) {
                            if (whitelist.contains(si.getLanguage())) {
                                List<VariantInfo> list = groups.get(groupID);
                                if (list == null) {
                                    list = new ArrayList<TbCmV2.VariantInfo>();
                                    groups.put(groupID, list);
                                }

                                list.add(vi);
                            } else {
                                List<VariantInfo> list = groupsExcluded.get(groupID);
                                if (list == null) {
                                    list = new ArrayList<TbCmV2.VariantInfo>();
                                    groupsExcluded.put(groupID, list);
                                }

                                list.add(vi);
                            }
                        } else {

                            List<VariantInfo> list = groups.get(groupID);
                            if (list == null) {
                                list = new ArrayList<TbCmV2.VariantInfo>();
                                groups.put(groupID, list);
                            }

                            list.add(vi);
                        }
                        allSubtitles.add(vi);
                    } catch (Exception e) {
                        getLogger().warning("New Subtitle Language: " + JSonStorage.serializeToJson(si));
                    }

                }
            }

            ArrayList<VariantInfo> descriptions = new ArrayList<VariantInfo>();
            if (cfg.isDescriptionTextEnabled()) {

                final String descText = vid.description;
                if (StringUtils.isNotEmpty(descText)) {
                    try {

                        String groupID;
                        switch (cfg.getGroupLogic()) {
                        case BY_FILE_TYPE:
                        case NO_GROUP:
                            groupID = "desc";
                            break;

                        case BY_MEDIA_TYPE:
                            groupID = YoutubeVariantInterface.VariantGroup.DESCRIPTION.name();
                            break;
                        default:
                            throw new WTFException("Unknown Grouping");
                        }
                        VariantInfo vi;
                        descriptions.add(vi = new VariantInfo(YoutubeVariant.DESCRIPTION, null, null, new YoutubeStreamData(vid, null, YoutubeITAG.DESCRIPTION)) {

                            @Override
                            public void fillExtraProperties(DownloadLink thislink, List<VariantInfo> alternatives) {
                                thislink.setProperty(YoutubeHelper.YT_DESCRIPTION, descText);

                            }

                            @Override
                            public int compareTo(VariantInfo o) {
                                return this.variant._getName().compareToIgnoreCase(o.variant._getName());

                            }
                        });

                        List<VariantInfo> list = groups.get(groupID);
                        if (list == null) {
                            list = new ArrayList<TbCmV2.VariantInfo>();
                            groups.put(groupID, list);
                        }

                        list.add(vi);

                    } catch (Exception e) {
                        getLogger().log(e);
                    }
                }
            }

            if (requestedVariant != null) {
                for (VariantInfo v : allVariants.values()) {
                    if (v.variant.getTypeId().equals(requestedVariant)) {
                        String groupID = getGroupID(v.variant);
                        List<VariantInfo> fromGroup = groups.get(groupID);
                        decryptedLinks.add(createLink(v, fromGroup));
                    }
                }
                for (VariantInfo vi : allSubtitles) {
                    if (vi.getIdentifier().equalsIgnoreCase(requestedVariant)) {
                        decryptedLinks.add(createLink(vi, allSubtitles));
                    }
                }
                for (VariantInfo vi : descriptions) {
                    if (vi.getIdentifier().equalsIgnoreCase(requestedVariant)) {
                        decryptedLinks.add(createLink(vi, descriptions));
                    }
                }
            } else {
                main: for (Entry<String, List<VariantInfo>> e : groups.entrySet()) {
                    if (e.getValue().size() == 0) {
                        continue;
                    }
                    Collections.sort(e.getValue());
                    if (e.getKey().equals(YoutubeVariantInterface.VariantGroup.DESCRIPTION.name()) || e.getKey().equalsIgnoreCase("txt")) {
                        for (VariantInfo vi : descriptions) {
                            decryptedLinks.add(createLink(vi, descriptions));
                        }
                    } else if (e.getKey().equals(YoutubeVariantInterface.VariantGroup.SUBTITLES.name()) || e.getKey().equalsIgnoreCase("srt")) {
                        if (cfg.isCreateBestSubtitleVariantLinkEnabled()) {
                            // special handling for subtitles
                            final String[] keys = cfg.getPreferedSubtitleLanguages();
                            // first try the users prefered list
                            if (keys != null) {
                                for (final String locale : keys) {
                                    try {
                                        for (VariantInfo vi : e.getValue()) {
                                            if (StringUtils.startsWithCaseInsensitive(vi.getIdentifier(), locale)) {
                                                decryptedLinks.add(createLink(vi, e.getValue()));
                                                continue main;
                                            }
                                        }
                                    } catch (Exception e1) {
                                        getLogger().log(e1);
                                    }
                                }
                            }
                            try {
                                // try to find the users locale
                                final String desiredLocale = TranslationFactory.getDesiredLanguage().toLowerCase(Locale.ENGLISH);
                                for (final VariantInfo vi : e.getValue()) {
                                    if (StringUtils.startsWithCaseInsensitive(vi.getIdentifier(), desiredLocale)) {
                                        decryptedLinks.add(createLink(vi, e.getValue()));
                                        continue main;
                                    }
                                }
                            } catch (Exception e1) {
                                // try english
                                getLogger().log(e1);
                                for (final VariantInfo vi : e.getValue()) {
                                    if (StringUtils.startsWithCaseInsensitive(vi.getIdentifier(), "en")) {
                                        decryptedLinks.add(createLink(vi, e.getValue()));
                                        continue main;
                                    }
                                }
                            }
                            // fallback: use the first
                            decryptedLinks.add(createLink(e.getValue().get(0), e.getValue()));
                        } else {
                            for (VariantInfo vi : e.getValue()) {
                                decryptedLinks.add(createLink(vi, e.getValue()));
                            }
                        }
                    } else {
                        if (cfg.getGroupLogic() != GroupLogic.NO_GROUP) {
                            switch (e.getValue().get(0).variant.getGroup()) {
                            case AUDIO:
                                if (cfg.isCreateBestAudioVariantLinkEnabled()) {
                                    decryptedLinks.add(createLink(e.getValue().get(0), e.getValue()));
                                }
                                break;
                            case IMAGE:
                                if (cfg.isCreateBestImageVariantLinkEnabled()) {
                                    decryptedLinks.add(createLink(e.getValue().get(0), e.getValue()));
                                }
                                break;
                            case VIDEO:
                                if (cfg.isCreateBestVideoVariantLinkEnabled()) {
                                    VariantInfo best = null;
                                    if (cfg.isBestVideoVariant1080pLimitEnabled()) {
                                        for (VariantInfo vv : e.getValue()) {
                                            try {
                                                if (vv.getVariant().getiTagVideo().getQualityRating() < VideoResolution.P_1440.getRating()) {
                                                    if (best == null) {
                                                        best = vv;
                                                    } else if (best.getVariant().getQualityRating() < vv.getVariant().getQualityRating()) {
                                                        best = vv;
                                                    }
                                                }
                                            } catch (Throwable ee) {
                                            }
                                        }
                                    }
                                    if (best != null) {
                                        decryptedLinks.add(createLink(best, e.getValue()));
                                    } else {
                                        decryptedLinks.add(createLink(e.getValue().get(0), e.getValue()));
                                    }
                                }
                                break;
                            case VIDEO_3D:
                                if (cfg.isCreateBest3DVariantLinkEnabled()) {
                                    decryptedLinks.add(createLink(e.getValue().get(0), e.getValue()));
                                }
                                break;
                            }
                        } else {
                            decryptedLinks.add(createLink(e.getValue().get(0), e.getValue()));
                        }
                    }
                }
                if (extra != null && extra.length > 0) {
                    main: for (VariantInfo v : allVariants.values()) {
                        for (String s : extra) {
                            if (v.variant.getTypeId().equals(s)) {
                                String groupID = getGroupID(v.variant);
                                List<VariantInfo> fromGroup = groups.get(groupID);
                                decryptedLinks.add(createLink(v, fromGroup));
                                continue main;
                            }
                        }
                    }
                }
                ArrayList<String> extraSubtitles = cfg.getExtraSubtitles();
                if (extraSubtitles != null) {
                    for (String v : extraSubtitles) {
                        if (v != null) {
                            for (VariantInfo vi : allSubtitles) {
                                if (vi.getIdentifier().equalsIgnoreCase(v)) {
                                    decryptedLinks.add(createLink(vi, allSubtitles));
                                }
                            }
                        }
                    }
                }
            }
        }
        for (DownloadLink dl : decryptedLinks) {
            dl.setContainerUrl(cryptedLink);
        }
        return decryptedLinks;
    }

    private Collection<? extends YoutubeClipData> parseGeneric(String cryptedUrl) throws InterruptedException, IOException {
        ArrayList<YoutubeClipData> ret = new ArrayList<YoutubeClipData>();
        if (StringUtils.isNotEmpty(cryptedUrl)) {
            int page = 1;
            int counter = 1;
            while (true) {
                if (this.isAbort()) {
                    throw new InterruptedException();
                }

                // br.getHeaders().put("Cookie", "");
                br.getPage(cryptedUrl);
                checkErrors(br);

                String[] videos = br.getRegex("data\\-video\\-id=\"([^\"]+)").getColumn(0);
                if (videos != null) {
                    for (String id : videos) {

                        if (dupeCheckSet.add(id)) {
                            ret.add(new YoutubeClipData(id, counter++));
                        }
                    }
                }
                if (ret.size() == 0) {
                    videos = br.getRegex("href=\"(/watch\\?v=[A-Za-z0-9\\-_]+)\\&amp;list=[A-Z0-9]+").getColumn(0);
                    if (videos != null) {
                        for (String relativeUrl : videos) {
                            String id = getVideoIDByUrl(relativeUrl);
                            if (dupeCheckSet.add(id)) {
                                ret.add(new YoutubeClipData(id, counter++));
                            }
                        }
                    }
                }
                break;
                // Several Pages: http://www.youtube.com/playlist?list=FL9_5aq5ZbPm9X1QH0K6vOLQ
                // String nextPage = br.getRegex("<a href=\"/playlist\\?list=" + playlistID +
                // "\\&amp;page=(\\d+)\"[^\r\n]+>Next").getMatch(0);
                // if (nextPage != null) {
                // page = Integer.parseInt(nextPage);
                // // anti ddos
                // Thread.sleep(500);
                // } else {
                // break;
                // }
            }

        }
        return ret;
    }

    protected String getGroupID(YoutubeVariantInterface v) {
        String groupID;
        switch (cfg.getGroupLogic()) {
        case BY_FILE_TYPE:
            groupID = v.getFileExtension();
            break;
        case NO_GROUP:
            groupID = v._getUniqueId();
            break;
        case BY_MEDIA_TYPE:
            groupID = v.getMediaTypeID();
            break;
        default:
            throw new WTFException("Unknown Grouping");
        }
        return groupID;
    }

    private DownloadLink createLink(VariantInfo variantInfo, List<VariantInfo> alternatives) {
        try {

            // if (!getCachedHelper().getConfig().isFastLinkCheckEnabled()) {
            // // sometimes streams are not available due to whatever. (for example internal server errors)
            // // let's just try the next alternative in this case
            // HashSet<VariantInfo> dupe = new HashSet<VariantInfo>();
            // int i = 0;
            // VariantInfo originalVariant = variantInfo;
            // while (!validate(variantInfo)) {
            // dupe.add(variantInfo);
            // variantInfo = null;
            // for (; i < alternatives.size(); i++) {
            // VariantInfo nextVariant = alternatives.get(i);
            // if (!dupe.contains(nextVariant)) {
            // variantInfo = nextVariant;
            // break;
            // }
            //
            // }
            // if (variantInfo == null) {
            // variantInfo = originalVariant;
            // break;
            // }
            // }
            // }
            YoutubeClipData clip = null;
            if (clip == null && variantInfo.videoStream != null) {
                clip = variantInfo.videoStream.getClip();

            }
            if (clip == null && variantInfo.audioStream != null) {
                clip = variantInfo.audioStream.getClip();

            }
            if (clip == null && variantInfo.data != null) {
                clip = variantInfo.data.getClip();

            }
            DownloadLink thislink;
            thislink = createDownloadlink("youtubev2://" + variantInfo.variant + "/" + clip.videoID + "/");

            // thislink.setAvailable(true);

            try {/* JD2 only */
                if (cfg.isSetCustomUrlEnabled()) {
                    thislink.setCustomURL(getBase() + "/watch?v=" + clip.videoID);
                }
                thislink.setContentUrl(getBase() + "/watch?v=" + clip.videoID + "&variant=" + Encoding.urlEncode(variantInfo.variant.getTypeId()));
            } catch (Throwable e) {/* Stable */
                thislink.setBrowserUrl(getBase() + "/watch?v=" + clip.videoID);
            }

            // thislink.setProperty(key, value)
            thislink.setProperty(YoutubeHelper.YT_EXT, variantInfo.variant.getFileExtension());

            thislink.setProperty(YoutubeHelper.YT_TITLE, clip.title);
            thislink.setProperty(YoutubeHelper.YT_PLAYLIST_INT, clip.playlistEntryNumber);
            thislink.setProperty(YoutubeHelper.YT_ID, clip.videoID);
            thislink.setProperty(YoutubeHelper.YT_AGE_GATE, clip.ageCheck);
            thislink.setProperty(YoutubeHelper.YT_CHANNEL, clip.channel);
            thislink.setProperty(YoutubeHelper.YT_USER, clip.user);
            thislink.setProperty(YoutubeHelper.YT_BEST_VIDEO, clip.bestVideoItag == null ? null : clip.bestVideoItag.name());
            thislink.setProperty(YoutubeHelper.YT_DATE, clip.date);
            thislink.setProperty(YoutubeHelper.YT_LENGTH_SECONDS, clip.length);
            thislink.setProperty(YoutubeHelper.YT_GOOGLE_PLUS_ID, clip.userGooglePlusID);
            thislink.setProperty(YoutubeHelper.YT_CHANNEL_ID, clip.channelID);
            thislink.setProperty(YoutubeHelper.YT_DURATION, clip.duration);
            thislink.setProperty(YoutubeHelper.YT_DATE_UPDATE, clip.dateUpdated);
            if (variantInfo.videoStream != null) {

                thislink.setProperty(YoutubeHelper.YT_STREAMURL_VIDEO, variantInfo.videoStream.getUrl());
                thislink.setProperty(YoutubeHelper.YT_STREAMURL_VIDEO_SEGMENTS, JSonStorage.serializeToJson(variantInfo.videoStream.getSegments()));

            }
            if (variantInfo.audioStream != null) {

                thislink.setProperty(YoutubeHelper.YT_STREAMURL_AUDIO, variantInfo.audioStream.getUrl());

                thislink.setProperty(YoutubeHelper.YT_STREAMURL_AUDIO_SEGMENTS, JSonStorage.serializeToJson(variantInfo.audioStream.getSegments()));

            }
            if (variantInfo.data != null) {

                thislink.setProperty(YoutubeHelper.YT_STREAMURL_DATA, variantInfo.data.getUrl());
            }
            ArrayList<String> variants = new ArrayList<String>();
            boolean has = false;
            if (alternatives != null) {

                for (VariantInfo vi : alternatives) {
                    // if (vi.variant != variantInfo.variant) {
                    variants.add(vi.variant._getUniqueId());
                    if (variantInfo.getIdentifier().equals(vi.getIdentifier())) {
                        has = true;
                    }
                    // }
                }

                if (!has) {
                    variants.add(0, variantInfo.variant._getUniqueId());
                }
            }

            thislink.setVariantSupport(variants.size() > 1);
            thislink.setProperty(YoutubeHelper.YT_VARIANTS, JSonStorage.serializeToJson(variants));
            thislink.setProperty(YoutubeHelper.YT_VARIANT, variantInfo.variant._getUniqueId());
            variantInfo.fillExtraProperties(thislink, alternatives);
            String filename;
            thislink.setFinalFileName(filename = getCachedHelper().createFilename(thislink));
            if (YoutubeVariant.SUBTITLES.equals(variantInfo.variant)) {
                thislink.setLinkID("youtubev2://" + variantInfo.variant + "/" + clip.videoID + "/" + variantInfo.getIdentifier());
            } else {
                thislink.setLinkID("youtubev2://" + variantInfo.variant + "/" + clip.videoID);
            }

            FilePackage fp = FilePackage.getInstance();
            YoutubeHelper helper = getCachedHelper();
            final String fpName = helper.replaceVariables(thislink, helper.getConfig().getPackagePattern());
            // req otherwise returned "" value = 'various', regardless of user settings for various!
            if (StringUtils.isNotEmpty(fpName)) {
                fp.setName(fpName);
                // let the packagizer merge several packages that have the same name
                fp.setProperty("ALLOW_MERGE", true);
                fp.add(thislink);
            }

            return thislink;
        } catch (Exception e) {
            getLogger().log(e);
            return null;
        }

    }

    private YoutubeHelper getCachedHelper() {
        YoutubeHelper ret = cachedHelper;
        if (ret == null || ret.getBr() != this.br) {
            ret = new YoutubeHelper(br, PluginJsonConfig.get(YoutubeConfig.class), getLogger());

        }
        ret.setupProxy();
        return ret;
    }

    /**
     * Parse a playlist id and return all found video ids
     *
     * @param decryptedLinks
     * @param dupeCheckSet
     * @param base
     * @param playlistID
     * @param videoIdsToAdd
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    public ArrayList<YoutubeClipData> parsePlaylist(String playlistID) throws IOException, InterruptedException {
        // this returns the html5 player
        ArrayList<YoutubeClipData> ret = new ArrayList<YoutubeClipData>();

        if (StringUtils.isNotEmpty(playlistID)) {
            Browser pbr = new Browser();
            /* we first have to load the plugin, before we can reference it */
            JDUtilities.getPluginForHost("youtube.com");
            JDUtilities.getPluginForHost("mediafire.com");
            br.getHeaders().put("User-Agent", jd.plugins.hoster.MediafireCom.stringUserAgent());
            br.getHeaders().put("Accept-Charset", null);
            br.getPage(getBase() + "/playlist?list=" + playlistID);

            final String yt_page_cl = br.getRegex("'PAGE_CL': (\\d+)").getMatch(0);
            final String yt_page_ts = br.getRegex("'PAGE_BUILD_TIMESTAMP': \"(.*?)\"").getMatch(0);
            pbr = br.cloneBrowser();
            int counter = 1;
            int round = 0;
            while (true) {

                System.out.println(ret.size());
                if (this.isAbort()) {
                    throw new InterruptedException();
                }

                checkErrors(pbr);
                String[] videos = pbr.getRegex("href=(\"|')(/watch\\?v=[A-Za-z0-9\\-_]+.*?)\\1").getColumn(1);
                int before = dupeCheckSet.size();
                if (videos != null) {
                    for (String relativeUrl : videos) {
                        if (relativeUrl.contains("list=" + playlistID)) {
                            String id = getVideoIDByUrl(relativeUrl);
                            if (dupeCheckSet.add(id)) {
                                ret.add(new YoutubeClipData(id, counter++));
                            }
                        }
                    }
                }
                if (dupeCheckSet.size() == before) {
                    // no videos in the last round. we are probably done here
                    break;
                }
                // Several Pages: http://www.youtube.com/playlist?list=FL9_5aq5ZbPm9X1QH0K6vOLQ
                String jsonPage = pbr.getRegex("/browse_ajax\\?action_continuation=\\d+&amp;continuation=[a-zA-Z0-9%]+").getMatch(-1);
                String nextPage = pbr.getRegex("<a href=(\"|')(/playlist\\?list=" + playlistID + "\\&amp;page=\\d+)\\1[^\r\n]+>Next").getMatch(1);
                if (jsonPage != null) {
                    jsonPage = HTMLEntities.unhtmlentities(jsonPage);
                    pbr = br.cloneBrowser();
                    if (yt_page_cl != null) {
                        pbr.getHeaders().put("X-YouTube-Page-CL", yt_page_cl);
                    }
                    if (yt_page_ts != null) {
                        pbr.getHeaders().put("X-YouTube-Page-Timestamp", yt_page_ts);
                    }
                    // anti ddos
                    round = antiDdosSleep(round);
                    pbr.getPage(jsonPage);
                    String output = pbr.toString().replace("\\n", " ");
                    output = jd.nutils.encoding.Encoding.unescapeYoutube(output);
                    output = output.replaceAll("[ ]{2,}", "");
                    pbr.getRequest().setHtmlCode(output);
                } else if (nextPage != null) {
                    // OLD! doesn't always present. Depends on server playlist backend code.!
                    nextPage = HTMLEntities.unhtmlentities(nextPage);
                    round = antiDdosSleep(round);
                    pbr.getPage(nextPage);
                } else {
                    break;
                }
            }

        }
        return ret;
    }

    /**
     * @param round
     * @return
     * @throws InterruptedException
     */
    protected int antiDdosSleep(int round) throws InterruptedException {
        Thread.sleep((DDOS_WAIT_MAX * (Math.min(DDOS_INCREASE_FACTOR, round++))) / DDOS_INCREASE_FACTOR);
        return round;
    }

    public ArrayList<YoutubeClipData> parseChannelgrid(String channelID) throws IOException, InterruptedException {
        // http://www.youtube.com/user/Gronkh/videos
        // channel: http://www.youtube.com/channel/UCYJ61XIK64sp6ZFFS8sctxw
        Browser li = br.cloneBrowser();
        ArrayList<YoutubeClipData> ret = new ArrayList<YoutubeClipData>();
        int counter = 1;
        int round = 0;
        if (StringUtils.isNotEmpty(channelID)) {
            String pageUrl = null;
            while (true) {
                round++;
                if (this.isAbort()) {
                    throw new InterruptedException();
                }
                String content = null;
                if (pageUrl == null) {
                    // this returns the html5 player
                    br.getPage(getBase() + "/channel/" + channelID + "/videos?view=0");
                    checkErrors(br);
                    content = br.toString();
                } else {
                    li = br.cloneBrowser();
                    li.getPage(pageUrl);
                    checkErrors(li);
                    content = jd.nutils.encoding.Encoding.unescapeYoutube(li.toString());
                }

                String[] videos = new Regex(content, "href=\"(/watch\\?v=[A-Za-z0-9\\-_]+)").getColumn(0);
                if (videos != null) {
                    for (String relativeUrl : videos) {
                        String id = getVideoIDByUrl(relativeUrl);
                        if (dupeCheckSet.add(id)) {
                            ret.add(new YoutubeClipData(id, counter++));

                        }
                    }
                }
                // Several Pages: http://www.youtube.com/playlist?list=FL9_5aq5ZbPm9X1QH0K6vOLQ
                String nextPage = Encoding.htmlDecode(new Regex(content, "data-uix-load-more-href=\"(/[^<>\"]*?)\"").getMatch(0));
                System.out.println(ret.size());
                if (nextPage != null) {
                    pageUrl = getBase() + nextPage;
                    // anti ddos
                    round = antiDdosSleep(round);
                } else {
                    break;
                }
            }

        }
        return ret;
    }

    public ArrayList<YoutubeClipData> parseUsergrid(String userID) throws IOException, InterruptedException {
        // http://www.youtube.com/user/Gronkh/videos
        // channel: http://www.youtube.com/channel/UCYJ61XIK64sp6ZFFS8sctxw
        if (false && userID != null) {
            /** TEST CODE for 1050 playlist max size issue. below comment is incorrect, both grid and channelid return 1050. raztoki **/
            // this format only ever returns 1050 results, its a bug on youtube end. We can resolve this by finding the youtube id and let
            // parseChannelgrid(channelid) find the results.
            ArrayList<YoutubeClipData> ret = new ArrayList<YoutubeClipData>();
            Browser li = br.cloneBrowser();
            li.getPage(getBase() + "/user/" + userID + "/videos?view=0");
            this.channelID = li.getRegex("'CHANNEL_ID', \"(UC[^\"]+)\"").getMatch(0);
            if (StringUtils.isNotEmpty(this.channelID)) {
                return ret;
            }
        }

        Browser li = br.cloneBrowser();
        ArrayList<YoutubeClipData> ret = new ArrayList<YoutubeClipData>();
        int counter = 1;
        if (StringUtils.isNotEmpty(userID)) {
            String pageUrl = null;
            int round = 0;
            while (true) {
                if (this.isAbort()) {
                    throw new InterruptedException();
                }
                String content = null;
                if (pageUrl == null) {
                    // this returns the html5 player
                    br.getPage(getBase() + "/user/" + userID + "/videos?view=0");

                    checkErrors(br);
                    content = br.toString();
                } else {
                    try {
                        li = br.cloneBrowser();
                        li.getPage(pageUrl);
                    } catch (final BrowserException b) {
                        if (li.getHttpConnection() != null && li.getHttpConnection().getResponseCode() == 400) {
                            logger.warning("Youtube issue!");
                            return ret;
                        } else {
                            throw b;
                        }
                    }
                    checkErrors(li);
                    content = jd.nutils.encoding.Encoding.unescapeYoutube(li.toString());
                }

                String[] videos = new Regex(content, "href=\"(/watch\\?v=[A-Za-z0-9\\-_]+)").getColumn(0);
                if (videos != null) {
                    for (String relativeUrl : videos) {
                        String id = getVideoIDByUrl(relativeUrl);
                        if (dupeCheckSet.add(id)) {
                            ret.add(new YoutubeClipData(id, counter++));

                        }
                    }
                }
                // Several Pages: http://www.youtube.com/playlist?list=FL9_5aq5ZbPm9X1QH0K6vOLQ
                String nextPage = Encoding.htmlDecode(new Regex(content, "data-uix-load-more-href=\"(/[^<>\"]+)\"").getMatch(0));
                System.out.println(ret.size());
                if (nextPage != null) {
                    pageUrl = getBase() + nextPage;
                    round = antiDdosSleep(round);
                } else {
                    break;
                }
            }

        }
        return ret;
    }

    /**
     * parses 'video_ids=' array, primarily used with watch_videos link
     */
    public ArrayList<YoutubeClipData> parseVideoIds(String video_ids) throws IOException, InterruptedException {
        // /watch_videos?title=Trending&video_ids=0KSOMA3QBU0,uT3SBzmDxGk,X7Xf8DsTWgs,72WhEqeS6AQ,Qc9c12q3mrc,6l7J1i1OkKs,zeu2tI-tqvs,o3mP3mJDL2k,jYdaQJzcAcw&feature=c4-overview&type=0&more_url=
        ArrayList<YoutubeClipData> ret = new ArrayList<YoutubeClipData>();
        int counter = 1;
        if (StringUtils.isNotEmpty(video_ids)) {
            String[] videos = new Regex(video_ids, "([A-Za-z0-9\\-_]+)").getColumn(0);
            if (videos != null) {
                for (String vid : videos) {
                    if (dupeCheckSet.add(vid)) {
                        ret.add(new YoutubeClipData(vid, counter++));
                    }
                }
            }
        }
        return ret;
    }

    private void checkErrors(Browser br) throws InterruptedException {
        if (br.containsHTML(">404 Not Found<")) {
            throw new InterruptedException("404 Not Found");
        } else if (br.containsHTML("iframe style=\"display:block;border:0;\" src=\"/error")) {
            throw new InterruptedException("Unknown Error");
        } else if (br.containsHTML("<h2>\\s*This channel does not exist\\.\\s*</h2>")) {
            throw new InterruptedException("Channel does not exist.");
        }

    }

    @Override
    public void init() {
        Browser.setRequestIntervalLimitGlobal(this.getHost(), 100);
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }
}