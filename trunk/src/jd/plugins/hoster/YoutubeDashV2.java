package jd.plugins.hoster;

import java.awt.Color;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

import jd.PluginWrapper;
import jd.config.ConfigContainer;
import jd.config.Property;
import jd.config.SubConfiguration;
import jd.controlling.downloadcontroller.DiskSpaceReservation;
import jd.controlling.downloadcontroller.ExceptionRunnable;
import jd.controlling.downloadcontroller.FileIsLockedException;
import jd.controlling.downloadcontroller.SingleDownloadController;
import jd.controlling.linkchecker.LinkChecker;
import jd.controlling.linkcollector.LinkCollector;
import jd.controlling.linkcrawler.CheckableLink;
import jd.controlling.linkcrawler.CrawledLink;
import jd.http.Request;
import jd.http.URLConnectionAdapter;
import jd.http.requests.GetRequest;
import jd.http.requests.HeadRequest;
import jd.parser.Regex;
import jd.plugins.Account;
import jd.plugins.AccountInfo;
import jd.plugins.BrowserAdapter;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.DownloadLinkDatabindingInterface;
import jd.plugins.FilePackage;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginConfigPanelNG;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.PluginProgress;
import jd.plugins.components.YoutubeClipData;
import jd.plugins.components.YoutubeCustomConvertVariant;
import jd.plugins.components.YoutubeCustomVariantStorable;
import jd.plugins.components.YoutubeITAG;
import jd.plugins.components.YoutubeStreamData;
import jd.plugins.components.YoutubeSubtitleInfo;
import jd.plugins.components.YoutubeVariant;
import jd.plugins.components.YoutubeVariantInterface;
import jd.plugins.components.YoutubeVariantInterface.VariantGroup;
import jd.plugins.decrypter.YoutubeHelper;
import jd.plugins.download.DownloadInterface;
import jd.plugins.download.DownloadLinkDownloadable;
import jd.plugins.download.Downloadable;
import jd.plugins.download.HashResult;
import jd.utils.locale.JDL;

import org.appwork.exceptions.WTFException;
import org.appwork.storage.JSonStorage;
import org.appwork.storage.TypeRef;
import org.appwork.storage.config.ConfigInterface;
import org.appwork.storage.config.JsonConfig;
import org.appwork.storage.config.annotations.AboutConfig;
import org.appwork.storage.config.annotations.DefaultBooleanValue;
import org.appwork.storage.config.annotations.DefaultEnumValue;
import org.appwork.storage.config.annotations.DefaultIntValue;
import org.appwork.storage.config.annotations.DefaultStringValue;
import org.appwork.storage.config.annotations.DescriptionForConfigEntry;
import org.appwork.storage.config.annotations.LabelInterface;
import org.appwork.storage.config.annotations.RequiresRestart;
import org.appwork.swing.action.BasicAction;
import org.appwork.swing.components.JScrollMenu;
import org.appwork.txtresource.TranslationFactory;
import org.appwork.utils.Files;
import org.appwork.utils.IO;
import org.appwork.utils.StringUtils;
import org.appwork.utils.logging2.LogSource;
import org.appwork.utils.net.httpconnection.HTTPProxy;
import org.appwork.utils.net.httpconnection.HTTPProxyStorable;
import org.appwork.utils.swing.EDTRunner;
import org.jdownloader.DomainInfo;
import org.jdownloader.controlling.DefaultDownloadLinkViewImpl;
import org.jdownloader.controlling.DownloadLinkView;
import org.jdownloader.controlling.ffmpeg.FFMpegProgress;
import org.jdownloader.controlling.ffmpeg.FFmpeg;
import org.jdownloader.controlling.linkcrawler.LinkVariant;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.gui.views.SelectionInfo.PluginView;
import org.jdownloader.images.AbstractIcon;
import org.jdownloader.images.BadgeIcon;
import org.jdownloader.plugins.DownloadPluginProgress;
import org.jdownloader.plugins.SkipReason;
import org.jdownloader.plugins.SkipReasonException;
import org.jdownloader.plugins.config.PluginJsonConfig;
import org.jdownloader.settings.GeneralSettings;
import org.jdownloader.settings.staticreferences.CFG_GUI;
import org.jdownloader.translate._JDT;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "youtube.com" }, urls = { "youtubev2://.+" }, flags = { 2 })
public class YoutubeDashV2 extends PluginForHost {

    private static final String    DASH_AUDIO_FINISHED = "DASH_AUDIO_FINISHED";

    private static final String    DASH_VIDEO_FINISHED = "DASH_VIDEO_FINISHED";

    private static final String    DASH_AUDIO_LOADED   = "DASH_AUDIO_LOADED";

    private static final String    DASH_VIDEO_LOADED   = "DASH_VIDEO_LOADED";

    private final String           DASH_AUDIO_CHUNKS   = "DASH_AUDIO_CHUNKS";

    private final String           DASH_VIDEO_CHUNKS   = "DASH_VIDEO_CHUNKS";

    private YoutubeConfig          cfg;
    private YoutubeHelper          cachedHelper;
    private YoutubeDashConfigPanel configPanel;

    /**
     * Maybe useful in the future - this guy has a lot of knowledge and makes good scripts:
     * https://github.com/bromix/plugin.video.youtube/tree/master/resources/lib
     */
    @Override
    public String getAGBLink() {
        return "http://youtube.com/t/terms";
    }

    @Override
    public Class<? extends ConfigInterface> getConfigInterface() {
        return YoutubeConfig.class;
    }

    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        final AccountInfo ai = new AccountInfo();
        try {
            YoutubeHelper helper = getCachedHelper();
            helper.setupProxy();
            helper.login(account, true, true);

        } catch (final PluginException e) {
            account.setValid(false);
            return ai;
        }
        //
        ai.setStatus(JDL.L("plugins.hoster.youtube.accountok", "Account is OK."));
        ai.setValidUntil(-1);
        account.setValid(true);
        return ai;
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return 20;
    }

    public static final class DashDownloadPluginProgress extends DownloadPluginProgress {
        private final long           totalSize;
        private final PluginProgress progress;
        private final long           chunkOffset;

        public DashDownloadPluginProgress(Downloadable downloadable, DownloadInterface downloadInterface, Color color, long totalSize, PluginProgress progress, long chunkOffset) {
            super(downloadable, downloadInterface, color);
            this.totalSize = totalSize;
            this.progress = progress;
            this.chunkOffset = chunkOffset;
        }

        @Override
        public long getCurrent() {
            final long ret = chunkOffset + ((DownloadInterface) progress.getProgressSource()).getTotalLinkBytesLoadedLive();
            return ret;
        }

        @Override
        public long getTotal() {
            return totalSize;
        }

        public long getDuration() {
            return System.currentTimeMillis() - startTimeStamp;
        }

        public long getSpeed() {
            return ((DownloadInterface) progress.getProgressSource()).getManagedConnetionHandler().getSpeed();
        }
    }

    public static interface YoutubeConfig extends ConfigInterface {
        @DefaultBooleanValue(false)
        @AboutConfig
        boolean isAndroidSupportEnabled();

        void setAndroidSupportEnabled(boolean b);

        @DefaultBooleanValue(true)
        @AboutConfig
        boolean isCreateBestVideoVariantLinkEnabled();

        void setCreateBestVideoVariantLinkEnabled(boolean b);

        @DefaultBooleanValue(true)
        @DescriptionForConfigEntry("If enabled, JD will not suggest 1400p and 2160p videos as 'Best' stream and download the 1080p stream instead.")
        @AboutConfig
        boolean isBestVideoVariant1080pLimitEnabled();

        void setBestVideoVariant1080pLimitEnabled(boolean b);

        @DefaultBooleanValue(false)
        @AboutConfig
        boolean isCustomChunkValueEnabled();

        void setCustomChunkValueEnabled(boolean b);

        @DefaultBooleanValue(true)
        @DescriptionForConfigEntry("Disable this if you do not want to use the new DASH Format. This will disable AUDIO only Downloads, and High Quality Video Downloads")
        @AboutConfig
        boolean isExternMultimediaToolUsageEnabled();

        void setExternMultimediaToolUsageEnabled(boolean b);

        @DefaultBooleanValue(true)
        @AboutConfig
        boolean isCreateBestAudioVariantLinkEnabled();

        void setCreateBestAudioVariantLinkEnabled(boolean b);

        @DefaultBooleanValue(true)
        @AboutConfig
        boolean isCreateBestImageVariantLinkEnabled();

        void setCreateBestImageVariantLinkEnabled(boolean b);

        @DefaultBooleanValue(false)
        @AboutConfig
        boolean isFastLinkCheckEnabled();

        void setFastLinkCheckEnabled(boolean b);

        @DefaultBooleanValue(true)
        @AboutConfig
        boolean isCreateBest3DVariantLinkEnabled();

        void setCreateBest3DVariantLinkEnabled(boolean b);

        @DefaultBooleanValue(true)
        @AboutConfig
        boolean isCreateBestSubtitleVariantLinkEnabled();

        void setCreateBestSubtitleVariantLinkEnabled(boolean b);

        @DefaultBooleanValue(false)
        @DescriptionForConfigEntry("sets the CUSTOM 'download from' field to: yourProtocolPreference + \"://www.youtube.com/watch?v=\" + videoID. Useful for when you don't want courselist / playlist / variant information polluting URL.")
        @AboutConfig
        boolean isSetCustomUrlEnabled();

        void setSetCustomUrlEnabled(boolean b);

        @DefaultBooleanValue(false)
        @AboutConfig
        boolean isProxyEnabled();

        void setProxyEnabled(boolean b);

        @AboutConfig
        HTTPProxyStorable getProxy();

        void setProxy(HTTPProxyStorable address);

        public static enum IfUrlisAVideoAndPlaylistAction implements LabelInterface {

            ASK {
                @Override
                public String getLabel() {
                    return _JDT._.YoutubeDash_IfUrlisAVideoAndPlaylistAction_ASK();
                }
            },
            VIDEO_ONLY {
                @Override
                public String getLabel() {
                    return _JDT._.YoutubeDash_IfUrlisAVideoAndPlaylistAction_VIDEO_ONLY();
                }
            },
            PLAYLIST_ONLY {
                @Override
                public String getLabel() {
                    return _JDT._.YoutubeDash_IfUrlisAVideoAndPlaylistAction_PLAYLIST_ONLY();
                }
            },
            NOTHING {
                @Override
                public String getLabel() {
                    return _JDT._.YoutubeDash_IfUrlisAVideoAndPlaylistAction_NOTHING();
                }
            },
            ;

        }

        public static enum IfUrlisAPlaylistAction implements LabelInterface {
            ASK {
                @Override
                public String getLabel() {
                    return _JDT._.YoutubeDash_IfUrlisAPlaylistAction_ASK();
                }
            },
            PROCESS {
                @Override
                public String getLabel() {
                    return _JDT._.YoutubeDash_IfUrlisAPlaylistAction_PROCESS();
                }
            },
            NOTHING {
                @Override
                public String getLabel() {
                    return _JDT._.YoutubeDash_IfUrlisAPlaylistAction_NOTHING();
                }
            };
        }

        @AboutConfig
        String[] getBlacklistedVariants();

        void setBlacklistedVariants(String[] variants);

        @AboutConfig
        String[] getExtraVariants();

        void setExtraVariants(String[] variants);

        @AboutConfig
        @DefaultEnumValue("ASK")
        IfUrlisAVideoAndPlaylistAction getLinkIsVideoAndPlaylistUrlAction();

        void setLinkIsVideoAndPlaylistUrlAction(IfUrlisAVideoAndPlaylistAction action);

        @AboutConfig
        @DefaultEnumValue("ASK")
        IfUrlisAPlaylistAction getLinkIsPlaylistUrlAction();

        void setLinkIsPlaylistUrlAction(IfUrlisAPlaylistAction action);

        // @DefaultBooleanValue(false)
        // @AboutConfig
        // boolean isPreferHttpsEnabled();
        //
        // void setPreferHttpsEnabled(boolean b);

        public static enum GroupLogic implements LabelInterface {
            BY_MEDIA_TYPE {
                @Override
                public String getLabel() {
                    return _JDT._.YoutubeDash_GroupLogic_BY_MEDIA_TYPE();
                }
            },
            BY_FILE_TYPE {
                @Override
                public String getLabel() {
                    return _JDT._.YoutubeDash_GroupLogic_BY_FILE_TYPE();
                }
            },
            NO_GROUP {
                @Override
                public String getLabel() {
                    return _JDT._.YoutubeDash_GroupLogic_NO_GROUP();
                }
            }
        }

        @AboutConfig
        @DefaultEnumValue("BY_MEDIA_TYPE")
        GroupLogic getGroupLogic();

        void setGroupLogic(GroupLogic group);

        // @DefaultBooleanValue(true)
        // @AboutConfig
        // boolean isBestGroupVariantEnabled();
        //
        // void setBestGroupVariantEnabled(boolean b);

        @AboutConfig
        @DefaultStringValue("*videoname* (*quality*).*ext*")
        String getFilenamePattern();

        void setFilenamePattern(String name);

        @AboutConfig
        String getSubtitleFilenamePattern();

        void setSubtitleFilenamePattern(String name);

        @AboutConfig
        String getAudioFilenamePattern();

        void setAudioFilenamePattern(String name);

        @AboutConfig
        @DefaultIntValue(15)
        int getChunksCount();

        void setChunksCount(int count);

        @AboutConfig
        String[] getPreferedSubtitleLanguages();

        void setPreferedSubtitleLanguages(String[] lngKeys);

        @AboutConfig
        ArrayList<String> getSubtitleWhiteList();

        void setSubtitleWhiteList(ArrayList<String> list);

        @AboutConfig
        ArrayList<String> getExtraSubtitles();

        void setExtraSubtitles(ArrayList<String> list);

        @AboutConfig
        String getVideoFilenamePattern();

        void setVideoFilenamePattern(String name);

        @AboutConfig
        String getImageFilenamePattern();

        void setImageFilenamePattern(String name);

        @AboutConfig
        @DefaultBooleanValue(true)
        boolean isSubtitlesEnabled();

        void setSubtitlesEnabled(boolean b);

        @AboutConfig
        @DefaultBooleanValue(false)
        boolean isDescriptionTextEnabled();

        void setDescriptionTextEnabled(boolean b);

        @AboutConfig
        ArrayList<YoutubeCustomVariantStorable> getCustomVariants();

        void setCustomVariants(ArrayList<YoutubeCustomVariantStorable> list);

        @AboutConfig
        @DefaultStringValue("*videoname*")
        String getPackagePattern();

        void setPackagePattern(String pattern);

        @AboutConfig
        @DefaultBooleanValue(true)
        void setSubtitleCopyforEachVideoVariant(boolean b);

        boolean isSubtitleCopyforEachVideoVariant();

        @AboutConfig
        @DefaultIntValue(5)
        @RequiresRestart("A JDownloader Restart is Required")
        @DescriptionForConfigEntry("Increase or decrease this value to modify the 'best video/audio/image available' - sorting")
        int getRating60Fps();

        void setRating60Fps(int rating);

        @AboutConfig
        @DefaultIntValue(60)
        @RequiresRestart("A JDownloader Restart is Required")
        @DescriptionForConfigEntry("Increase or decrease this value to modify the 'best video/audio/image available' - sorting")
        int getRatingContainerMP4();

        void setRatingContainerMP4(int rating);

        @AboutConfig
        @DefaultIntValue(50)
        @RequiresRestart("A JDownloader Restart is Required")
        @DescriptionForConfigEntry("Increase or decrease this value to modify the 'best video/audio/image available' - sorting")
        int getRatingContainerWEBM();

        void setRatingContainerWEBM(int rating);

        @AboutConfig
        @DefaultIntValue(5)
        @RequiresRestart("A JDownloader Restart is Required")
        @DescriptionForConfigEntry("Increase or decrease this value to modify the 'best video/audio/image available' - sorting")
        int getRatingContainerM4A();

        void setRatingContainerM4A(int rating);

        @AboutConfig
        @DefaultIntValue(4)
        @RequiresRestart("A JDownloader Restart is Required")
        @DescriptionForConfigEntry("Increase or decrease this value to modify the 'best video/audio/image available' - sorting")
        int getRatingContainerAAC();

        void setRatingContainerAAC(int rating);

        @AboutConfig
        @DefaultIntValue(2)
        @RequiresRestart("A JDownloader Restart is Required")
        @DescriptionForConfigEntry("Increase or decrease this value to modify the 'best video/audio/image available' - sorting")
        int getRatingContainerMP3();

        void setRatingContainerMP3(int rating);
    }

    @Override
    public ConfigContainer getConfig() {
        throw new WTFException("Not implemented");
    }

    @Override
    public SubConfiguration getPluginConfig() {
        throw new WTFException("Not implemented");
    }

    public YoutubeDashV2(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("http://www.youtube.com/login?next=/index");

    }

    @Override
    public PluginConfigPanelNG createConfigPanel() {
        YoutubeDashConfigPanel panel = this.configPanel;
        if (panel == null) {
            panel = this.configPanel = new YoutubeDashConfigPanel(getDescription());
        }
        return panel;
    }

    public class UrlCollection {
        private String data;

        public UrlCollection(String videoUrl, String audioUrl, String dataUrl) {
            this.audio = audioUrl;
            this.video = videoUrl;
            this.data = dataUrl;
        }

        String video;
        String audio;
    }

    public String getMirrorID(DownloadLink link) {
        return "Youtube:" + link.getStringProperty(YoutubeHelper.YT_VARIANT) + link.getName() + "_" + link.getView().getBytesTotal();
    }

    private boolean isDownloading = false;

    @Override
    public AvailableStatus requestFileInformation(DownloadLink downloadLink) throws Exception {
        cfg = PluginJsonConfig.get(YoutubeConfig.class);

        YoutubeProperties data = downloadLink.bindData(YoutubeProperties.class);
        if (!downloadLink.getDownloadURL().startsWith("youtubev2://")) {
            convertOldLink(downloadLink);
        }

        if (cfg.isFastLinkCheckEnabled() && !LinkChecker.isForcedLinkCheck(downloadLink)) {

            return AvailableStatus.UNCHECKED;

        }

        YoutubeHelper helper = getCachedHelper();

        YoutubeVariantInterface variant = getVariant(downloadLink);

        {
            // update linkid
            final String linkid = downloadLink.getLinkID();
            if (linkid != null && !isDownloading) {
                switch (variant.getType()) {
                case SUBTITLES: {
                    if (!("youtubev2://" + YoutubeVariant.SUBTITLES + "/" + downloadLink.getStringProperty(YoutubeHelper.YT_ID)).equals(linkid)) {
                        // update it
                        downloadLink.setLinkID("youtubev2://" + YoutubeVariant.SUBTITLES + "/" + downloadLink.getStringProperty(YoutubeHelper.YT_ID));
                        return AvailableStatus.TRUE;
                    }
                    break;
                }
                case VIDEO:
                case DASH_AUDIO:
                case DASH_VIDEO: {
                    if (!("youtubev2://" + variant._getUniqueId() + "/" + downloadLink.getStringProperty(YoutubeHelper.YT_ID)).equals(linkid)) {
                        // update it
                        downloadLink.setLinkID("youtubev2://" + variant._getUniqueId() + "/" + downloadLink.getStringProperty(YoutubeHelper.YT_ID));
                        return AvailableStatus.TRUE;
                    }
                    break;
                }
                case IMAGE:
                    break;
                case DESCRIPTION:
                    downloadLink.setDownloadSize(downloadLink.getStringProperty(YoutubeHelper.YT_DESCRIPTION).getBytes("UTF-8").length);
                    return AvailableStatus.TRUE;
                default:
                    break;
                }
            }
        }
        URLConnectionAdapter con;
        long totalSize = -1;
        // youtube uses redirects - maybe for load balancing
        br.setFollowRedirects(true);
        switch (variant.getType()) {
        case SUBTITLES:
            for (int i = 0; i < 2; i++) {
                UrlCollection urls = getUrlPair(downloadLink);
                if (StringUtils.isEmpty(urls.data)) {
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                }
                String encoding = br.getHeaders().get("Accept-Encoding");
                try {
                    br.getHeaders().put("Accept-Encoding", "");
                    con = br.openGetConnection(urls.data);
                    con.disconnect();
                } finally {
                    br.getHeaders().put("Accept-Encoding", encoding);
                }
                if (!con.getContentType().startsWith("text/xml") || con.getResponseCode() != 200) {
                    if (i == 0) {
                        resetStreamUrls(downloadLink);
                        continue;
                    }
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                }
                totalSize = con.getLongContentLength();
                break;
            }

            break;
        case DESCRIPTION:
            downloadLink.setDownloadSize(downloadLink.getStringProperty(YoutubeHelper.YT_DESCRIPTION).getBytes("UTF-8").length);
            return AvailableStatus.TRUE;
        case IMAGE:
            for (int i = 0; i < 2; i++) {
                UrlCollection urls = getUrlPair(downloadLink);
                if (StringUtils.isEmpty(urls.data)) {
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                }
                con = br.openGetConnection(urls.data);
                con.disconnect();
                if (!con.getContentType().startsWith("image/jpeg") || con.getResponseCode() != 200) {
                    if (i == 0) {
                        resetStreamUrls(downloadLink);
                        continue;
                    }
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                }
                totalSize = con.getLongContentLength();
                break;
            }
            break;

        default:
            br.setFollowRedirects(true);

            HashSet<LinkVariant> checkedAlternatives = new HashSet<LinkVariant>();
            YoutubeVariantInterface orgVariant = getVariant(downloadLink);
            test: while (true) {

                checkedAlternatives.add(getVariant(downloadLink));
                try {

                    for (int i = 0; i < 2; i++) {
                        totalSize = 0;
                        UrlCollection urls = getUrlPair(downloadLink);

                        if (StringUtils.isNotEmpty(urls.video)) {
                            String url = urls.video;
                            br.openRequestConnection(new HeadRequest(url)).disconnect();
                            con = br.getHttpConnection();
                            if (con.getResponseCode() == 200) {
                                downloadLink.setProperty(YoutubeHelper.YT_STREAMURL_VIDEO, br.getRequest().getUrl());
                                totalSize += con.getLongContentLength();
                                data.setDashVideoSize(con.getLongContentLength());

                            } else {

                                if (i == 0) {
                                    resetStreamUrls(downloadLink);
                                    continue;
                                }
                                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);

                            }
                        }

                        if (StringUtils.isNotEmpty(urls.audio)) {
                            String url = urls.audio;
                            br.openRequestConnection(new HeadRequest(url)).disconnect();
                            con = br.getHttpConnection();
                            if (con.getResponseCode() == 200) {
                                downloadLink.setProperty(YoutubeHelper.YT_STREAMURL_AUDIO, br.getRequest().getUrl());
                                totalSize += con.getLongContentLength();
                                data.setDashAudioSize(con.getLongContentLength());

                            } else {

                                if (i == 0) {
                                    resetStreamUrls(downloadLink);
                                    continue;
                                }
                                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                            }
                        }
                        break test;
                    }

                } catch (PluginException e) {

                    if (e.getErrorMessage() != null) {

                        if (StringUtils.equalsIgnoreCase(e.getErrorMessage(), "This video is private")) {
                            throw e;
                        }

                        if (e.getErrorMessage().contains(_JDT._.CountryIPBlockException_createCandidateResult())) {
                            throw e;
                        }
                    }
                    List<LinkVariant> alternatives = getVariantsByLink(downloadLink);
                    if (alternatives != null) {
                        for (LinkVariant v : alternatives) {

                            if (!checkedAlternatives.contains(v)) {
                                LinkCollector.getInstance().setActiveVariantForLink(downloadLink, v);

                                continue test;

                            }
                        }
                    }
                    LinkCollector.getInstance().setActiveVariantForLink(downloadLink, orgVariant);

                    throw e;
                }

            }

        }

        // HTTP/1.1 403 Forbidden
        // helper.login(false, false);

        // // we should cache this information:

        downloadLink.setFinalFileName(helper.createFilename(downloadLink));
        String oldLinkName = downloadLink.getStringProperty("name", null);
        if (StringUtils.isNotEmpty(oldLinkName)) {
            // old link?

            downloadLink.setFinalFileName(oldLinkName);
        }

        downloadLink.setInternalTmpFilenameAppend(null);
        YoutubeVariantInterface v = getVariant(downloadLink);
        if (v.hasConverer(downloadLink)) {
            downloadLink.setInternalTmpFilenameAppend(".tmp");
        }

        if (totalSize > 0) {
            downloadLink.setVerifiedFileSize(totalSize);
        }

        return AvailableStatus.TRUE;
    }

    private void convertOldLink(DownloadLink link) throws PluginException {
        try {

            link.setProperty(YoutubeHelper.YT_ID, link.getStringProperty("ytID", null));
            if (link.getBooleanProperty("DASH", false)) {
                String video = link.getStringProperty("DASH_VIDEO");
                String audio = link.getStringProperty("DASH_AUDIO");
                YoutubeITAG videoTag = StringUtils.isEmpty(video) ? null : YoutubeITAG.get(Integer.parseInt(video), -1, -1, -1, "", 0);
                YoutubeITAG audioTag = StringUtils.isEmpty(audio) ? null : YoutubeITAG.get(Integer.parseInt(audio), -1, -1, -1, "", 0);
                YoutubeVariant variant = null;
                for (YoutubeVariant v : YoutubeVariant.values()) {
                    if (v.getiTagAudio() == audioTag && v.getiTagVideo() == videoTag) {
                        variant = v;
                        break;
                    }
                }
                if (variant == null) {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, "Old Link. Please readd the Link");
                }
                LinkCollector.getInstance().setActiveVariantForLink(link, variant);
            } else {
                String url = link.getDownloadURL();

                YoutubeVariantInterface variant = null;
                String[] matches = new Regex(url, "http://img.youtube.com/vi/(.+)/(.+)\\.jpg").getRow(0);
                if (matches != null) {
                    link.setProperty(YoutubeHelper.YT_ID, matches[0]);
                    if (matches[1].equals("default")) {
                        variant = YoutubeVariant.IMAGE_LQ;
                    } else if (matches[1].equals("mqdefault")) {

                        variant = YoutubeVariant.IMAGE_MQ;

                    } else if (matches[1].equals("hqdefault")) {

                        variant = YoutubeVariant.IMAGE_HQ;

                    } else if (matches[1].equals("maxresdefault")) {

                        variant = YoutubeVariant.IMAGE_MAX;
                    }
                }
                if (variant == null) {
                    LinkedHashMap<String, String> params = Request.parseQuery(url);
                    if (url.contains("/api/timedtext")) {
                        link.setProperty(YoutubeHelper.YT_ID, params.get("v"));
                        variant = new SubtitleVariant(params.get("lang"));
                    } else {
                        String itag = params.get("itag");
                        YoutubeITAG videoTag = YoutubeITAG.get(Integer.parseInt(itag), -1, -1, -1, "", 0);

                        for (YoutubeVariant v : YoutubeVariant.values()) {
                            if (v.getiTagAudio() == null && v.getiTagVideo() == videoTag) {
                                variant = v;
                                break;
                            }
                        }
                    }
                }
                if (variant == null) {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, "Old Link. Please readd the Link");
                }
                LinkCollector.getInstance().setActiveVariantForLink(link, variant);
            }

        } catch (Exception e) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, "Old Link. Please readd the Link");
        }

    }

    private UrlCollection getUrlPair(DownloadLink downloadLink) throws Exception {
        String videoUrl = downloadLink.getStringProperty(YoutubeHelper.YT_STREAMURL_VIDEO);
        String audioUrl = downloadLink.getStringProperty(YoutubeHelper.YT_STREAMURL_AUDIO);
        String dataUrl = downloadLink.getStringProperty(YoutubeHelper.YT_STREAMURL_DATA);
        YoutubeVariantInterface variant = getVariant(downloadLink);
        if (StringUtils.isEmpty(dataUrl) && variant.getiTagData() != null) {
            updateUrls(downloadLink);
            videoUrl = downloadLink.getStringProperty(YoutubeHelper.YT_STREAMURL_VIDEO);
            audioUrl = downloadLink.getStringProperty(YoutubeHelper.YT_STREAMURL_AUDIO);
            dataUrl = downloadLink.getStringProperty(YoutubeHelper.YT_STREAMURL_DATA);
        }
        if (StringUtils.isEmpty(videoUrl) && variant.getiTagVideo() != null) {
            updateUrls(downloadLink);
            videoUrl = downloadLink.getStringProperty(YoutubeHelper.YT_STREAMURL_VIDEO);
            audioUrl = downloadLink.getStringProperty(YoutubeHelper.YT_STREAMURL_AUDIO);
            dataUrl = downloadLink.getStringProperty(YoutubeHelper.YT_STREAMURL_DATA);
        }
        if (StringUtils.isEmpty(audioUrl) && variant.getiTagAudio() != null) {
            updateUrls(downloadLink);
            videoUrl = downloadLink.getStringProperty(YoutubeHelper.YT_STREAMURL_VIDEO);
            audioUrl = downloadLink.getStringProperty(YoutubeHelper.YT_STREAMURL_AUDIO);
            dataUrl = downloadLink.getStringProperty(YoutubeHelper.YT_STREAMURL_DATA);
        }
        return new UrlCollection(videoUrl, audioUrl, dataUrl);
    }

    protected YoutubeVariantInterface getVariant(DownloadLink downloadLink) throws PluginException {

        String var = downloadLink.getStringProperty(YoutubeHelper.YT_VARIANT);
        YoutubeVariantInterface ret = getCachedHelper().getVariantById(var);
        if (ret == null) {
            getLogger().warning("Invalid Variant: " + var);
            throw new PluginException(LinkStatus.ERROR_FATAL, "INVALID VARIANT: " + downloadLink.getStringProperty(YoutubeHelper.YT_VARIANT));

        }
        return ret;

    }

    private void updateUrls(DownloadLink downloadLink) throws Exception {
        YoutubeVariantInterface variant = getVariant(downloadLink);
        String videoID = downloadLink.getStringProperty(YoutubeHelper.YT_ID);
        YoutubeClipData vid = new YoutubeClipData(videoID);
        YoutubeHelper helper = getCachedHelper();
        Map<YoutubeITAG, YoutubeStreamData> info = helper.loadVideo(vid);
        if (info == null) {
            // if (StringUtils.equalsIgnoreCase(vid.error, "This video is unavailable.") || StringUtils.equalsIgnoreCase(vid.error,/*
            // * 15.12.2014
            // */"This video is not available.")) {
            // throw new CountryIPBlockException();
            // }
            // if (StringUtils.equalsIgnoreCase(vid.error, "This video is unavailable.") || StringUtils.equalsIgnoreCase(vid.error,/*
            // * 15.12.2014
            // */"This video is not available.")) {
            // throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE,
            // _JDT._.CountryIPBlockException_createCandidateResult(), 1 * 24 * 60 * 60 * 100l);
            // }
            if (StringUtils.equalsIgnoreCase(vid.error, "This video is unavailable.") || StringUtils.equalsIgnoreCase(vid.error,/*
             * 15.12.2014
             */"This video is not available.")) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, _JDT._.CountryIPBlockException_createCandidateResult());
            }
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, vid.error);
        }

        // write properties in old links and update properties in all others
        downloadLink.setProperty(YoutubeHelper.YT_TITLE, vid.title);
        downloadLink.setProperty(YoutubeHelper.YT_ID, vid.videoID);
        downloadLink.setProperty(YoutubeHelper.YT_AGE_GATE, vid.ageCheck);
        downloadLink.setProperty(YoutubeHelper.YT_CHANNEL, vid.channel);
        downloadLink.setProperty(YoutubeHelper.YT_USER, vid.user);
        downloadLink.setProperty(YoutubeHelper.YT_DATE, vid.date);
        downloadLink.setProperty(YoutubeHelper.YT_LENGTH_SECONDS, vid.length);

        if (variant.getGroup() == YoutubeVariantInterface.VariantGroup.SUBTITLES) {

            String code = downloadLink.getStringProperty(YoutubeHelper.YT_SUBTITLE_CODE, null);

            for (YoutubeSubtitleInfo si : helper.loadSubtitles(vid)) {

                if (si.getLang().equals(code)) {

                    downloadLink.setProperty(YoutubeHelper.YT_STREAMURL_DATA, si._getUrl(vid.videoID));
                    break;
                }

            }
            return;
        }

        if (variant.getiTagData() != null) {
            YoutubeStreamData data = info.get(variant.getiTagData());
            if (data == null) {
                getLogger().info(info + "");
                throw new PluginException(LinkStatus.ERROR_FATAL, "Variant not found: " + variant + "(Itag missing: " + variant.getiTagData() + ")");
            }
            downloadLink.setProperty(YoutubeHelper.YT_STREAMURL_DATA, data.getUrl());
        }
        if (variant.getiTagAudio() != null) {
            YoutubeStreamData audioStream = info.get(variant.getiTagAudio());
            if (audioStream == null) {
                getLogger().info(info + "");
                throw new PluginException(LinkStatus.ERROR_FATAL, "Variant not found: " + variant + "(Itag missing: " + variant.getiTagAudio() + ")");
            }
            downloadLink.setProperty(YoutubeHelper.YT_STREAMURL_AUDIO, audioStream.getUrl());
        }

        if (variant.getiTagVideo() != null) {
            YoutubeStreamData videoStream = info.get(variant.getiTagVideo());
            if (videoStream == null) {
                getLogger().info(info + "");
                throw new PluginException(LinkStatus.ERROR_FATAL, "Variant not found: " + variant + "(Itag missing: " + variant.getiTagVideo() + ")");
            }
            downloadLink.setProperty(YoutubeHelper.YT_STREAMURL_VIDEO, videoStream.getUrl());
        }

    }

    public static interface YoutubeProperties extends DownloadLinkDatabindingInterface {
        public static final String DASH_VIDEO_SIZE = "DASH_VIDEO_SIZE";
        public static final String DASH_AUDIO_SIZE = "DASH_AUDIO_SIZE";

        @Key(DASH_VIDEO_SIZE)
        long getDashVideoSize();

        @Key(DASH_VIDEO_SIZE)
        void setDashVideoSize(long longContentLength);

        @Key(DASH_AUDIO_SIZE)
        long getDashAudioSize();

        @Key(DASH_AUDIO_SIZE)
        void setDashAudioSize(long longContentLength);

        @Key(DASH_VIDEO_FINISHED)
        void setDashVideoFinished(boolean b);

        @Key(DASH_AUDIO_FINISHED)
        void setDashAudioFinished(boolean b);

        @Key(DASH_VIDEO_FINISHED)
        boolean isDashVideoFinished();

        @Key(DASH_AUDIO_FINISHED)
        boolean isDashAudioFinished();

        @Key(DASH_VIDEO_LOADED)
        long getDashVideoBytesLoaded();

        @Key(DASH_AUDIO_LOADED)
        long getDashAudioBytesLoaded();

        @Key(DASH_VIDEO_LOADED)
        void setDashVideoBytesLoaded(long bytesLoaded);

        @Key(DASH_AUDIO_LOADED)
        void setDashAudioBytesLoaded(long bytesLoaded);

    }

    private Boolean downloadDashStream(final DownloadLink downloadLink, final YoutubeProperties data, final boolean isVideoStream) throws Exception {
        final long totalSize = downloadLink.getDownloadSize();

        UrlCollection urls = getUrlPair(downloadLink);
        GetRequest request = null;
        final String dashName;
        final String dashChunksProperty;

        // final String dashLoadedProperty;
        // final String dashFinishedProperty;
        final long chunkOffset;
        if (isVideoStream) {
            request = new GetRequest(urls.video);
            dashName = getDashVideoFileName(downloadLink);
            dashChunksProperty = DASH_VIDEO_CHUNKS;
            chunkOffset = 0;
        } else {
            request = new GetRequest(urls.audio);
            dashName = getDashAudioFileName(downloadLink);
            dashChunksProperty = DASH_AUDIO_CHUNKS;
            final YoutubeVariantInterface variant = getVariant(downloadLink);
            if (variant.getType() == YoutubeVariantInterface.DownloadType.DASH_AUDIO) {
                chunkOffset = 0;
            } else {
                chunkOffset = data.getDashVideoSize();
            }
        }
        {
            // scope just to highlight the jd2 code
            // Do not copy to jd09 plugins.. this is jd2 code!
            List<HTTPProxy> possibleProxies = br.getProxy().getProxiesByUrl(request.getUrl());
            request.setProxy((possibleProxies == null || possibleProxies.size() == 0) ? null : possibleProxies.get(0));
        }
        final String dashPath = new File(downloadLink.getDownloadDirectory(), dashName).getAbsolutePath();
        final DownloadLink dashLink = new DownloadLink(this, dashName, getHost(), request.getUrl(), true);
        dashLink.setLivePlugin(this);
        final LinkStatus videoLinkStatus = new LinkStatus(dashLink);
        Downloadable dashDownloadable = new DownloadLinkDownloadable(dashLink) {

            volatile long[] chunkProgress = null;
            {
                Object ret = downloadLink.getProperty(dashChunksProperty, null);
                if (ret != null && ret instanceof long[]) {
                    chunkProgress = (long[]) ret;
                } else {
                    if (ret != null && ret instanceof List) {
                        /* restored json-object */
                        List<Object> list = ((List<Object>) ret);
                        long[] ret2 = new long[list.size()];
                        for (int i = 0; i < ret2.length; i++) {
                            ret2[i] = Long.valueOf(list.get(0).toString());
                        }
                        chunkProgress = ret2;
                    }
                }
            }

            @Override
            public void setResumeable(boolean value) {
                downloadLink.setResumeable(value);
            }

            public long[] getChunksProgress() {
                return chunkProgress;
            }

            public void setChunksProgress(long[] ls) {
                chunkProgress = ls;
                if (ls == null) {
                    downloadLink.setProperty(dashChunksProperty, Property.NULL);
                } else {
                    downloadLink.setProperty(dashChunksProperty, ls);
                }
            }

            @Override
            public boolean isResumable() {
                return true;
            }

            @Override
            public void addDownloadTime(long ms) {
                downloadLink.addDownloadTime(ms);
            }

            @Override
            public void setHashResult(HashResult result) {
            }

            @Override
            public String getFinalFileOutput() {
                return dashPath;
            }

            @Override
            public void lockFiles(File... files) throws FileIsLockedException {
                /**
                 * do nothing, handleDownload does all the locking
                 */
            }

            @Override
            public void unlockFiles(File... files) {
                /**
                 * do nothing, handleDownload does all the locking
                 */
            }

            @Override
            public void waitForNextConnectionAllowed() throws InterruptedException {
                YoutubeDashV2.this.waitForNextConnectionAllowed(downloadLink);
            }

            @Override
            public String getFileOutput() {
                return dashPath;
            }

            @Override
            public int getLinkStatus() {
                return videoLinkStatus.getStatus();
            }

            @Override
            public long getVerifiedFileSize() {
                long ret = -1l;
                if (isVideoStream) {
                    ret = data.getDashVideoSize();
                } else {
                    ret = data.getDashAudioSize();
                }
                if (ret <= 0) {
                    ret = -1;
                }
                return ret;
            }

            @Override
            public long getKnownDownloadSize() {
                if (isVideoStream) {
                    return data.getDashVideoSize();
                } else {
                    return data.getDashAudioSize();
                }
            }

            @Override
            public void setDownloadTotalBytes(long l) {
            }

            @Override
            public void setLinkStatus(int finished) {
                if (isVideoStream) {
                    data.setDashVideoFinished(LinkStatus.FINISHED == finished);
                } else {
                    data.setDashAudioFinished(LinkStatus.FINISHED == finished);
                }
            }

            @Override
            public void setVerifiedFileSize(long length) {
                if (isVideoStream) {
                    if (length >= 0) {
                        data.setDashVideoSize(length);
                    } else {
                        data.setDashVideoSize(-1);
                    }
                } else {
                    if (length >= 0) {
                        data.setDashAudioSize(length);

                    } else {
                        data.setDashAudioSize(-1);
                    }
                }

            }

            @Override
            public String getFinalFileName() {
                return dashName;
            }

            @Override
            public void setFinalFileName(String newfinalFileName) {
            }

            @Override
            public long getDownloadTotalBytes() {
                if (isVideoStream) {
                    return data.getDashVideoBytesLoaded();
                } else {
                    return data.getDashAudioBytesLoaded();
                }

            }

            @Override
            public void setDownloadBytesLoaded(long bytes) {
                if (isVideoStream) {
                    if (bytes < 0) {
                        data.setDashVideoBytesLoaded(0);
                    } else {
                        data.setDashVideoBytesLoaded(bytes);
                    }
                } else {
                    if (bytes < 0) {
                        data.setDashAudioBytesLoaded(0);
                    } else {
                        data.setDashAudioBytesLoaded(bytes);
                    }
                }

                downloadLink.setDownloadCurrent(chunkOffset + bytes);
            }

            @Override
            public boolean isHashCheckEnabled() {
                return false;
            }

            @Override
            public SingleDownloadController getDownloadLinkController() {
                return downloadLink.getDownloadLinkController();
            }

            final HashMap<PluginProgress, PluginProgress> pluginProgressMap = new HashMap<PluginProgress, PluginProgress>();

            @Override
            public void addPluginProgress(final PluginProgress progress) {
                final PluginProgress mapped;
                synchronized (pluginProgressMap) {
                    if (pluginProgressMap.containsKey(progress)) {
                        mapped = pluginProgressMap.get(progress);
                    } else if (progress != null && progress instanceof DownloadPluginProgress) {
                        mapped = new DashDownloadPluginProgress(this, (DownloadInterface) progress.getProgressSource(), progress.getColor(), totalSize, progress, chunkOffset);
                        pluginProgressMap.put(progress, mapped);
                    } else {
                        mapped = progress;
                    }
                }
                downloadLink.addPluginProgress(mapped);
            }

            @Override
            public boolean removePluginProgress(PluginProgress remove) {
                final PluginProgress mapped;
                synchronized (pluginProgressMap) {
                    if (pluginProgressMap.containsKey(remove)) {
                        mapped = pluginProgressMap.remove(remove);
                    } else {
                        mapped = remove;
                    }
                }
                return downloadLink.removePluginProgress(mapped);
            }

        };
        dl = BrowserAdapter.openDownload(br, dashDownloadable, request, true, getChunksPerStream());
        if (!this.dl.getConnection().isContentDisposition() && !this.dl.getConnection().getContentType().startsWith("video") && !this.dl.getConnection().getContentType().startsWith("audio") && !this.dl.getConnection().getContentType().startsWith("application")) {
            if (dl.getConnection().getResponseCode() == 500) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, _GUI._.hoster_servererror("Youtube"), 5 * 60 * 1000l);
            }
            br.followConnection();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        boolean ret = dl.startDownload();
        if (dl.externalDownloadStop()) {
            return null;
        }
        return ret;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception {
        handlePremium(downloadLink, null);

    }

    public static void main(String[] args) {
        for (YoutubeVariant v : YoutubeVariant.values()) {

            // @Default(lngs = { "en" }, values = { "240p MP4-Video" })
            // String YoutubeVariant_name_MP4_DASH_240_AAC128();
            System.out.println("@Default(lngs = { \"en\" }, values = { \"" + v.getQualityExtension() + "\" })");
            ;
            System.out.println("String YoutubeVariant_qualityExtension_" + v.name() + "();");
        }
    }

    private int getChunksPerStream() {
        YoutubeConfig cfg = PluginJsonConfig.get(YoutubeConfig.class);
        if (!cfg.isCustomChunkValueEnabled()) {
            return 0;
        }
        int maxChunks = cfg.getChunksCount();
        if (maxChunks <= 0) {
            maxChunks = 0;
        }
        return maxChunks;
    }

    public boolean hasConfig() {
        return true;
    }

    public void handleDash(final DownloadLink downloadLink, final YoutubeProperties data, Account account) throws Exception {

        DownloadLinkView oldView = null;
        DefaultDownloadLinkViewImpl newView = null;
        try {
            newView = new DefaultDownloadLinkViewImpl() {
                @Override
                public long getBytesLoaded() {
                    if (data.isDashVideoFinished()) {
                        return super.getBytesLoaded() + data.getDashVideoSize();
                    } else {
                        return super.getBytesLoaded();
                    }

                }
            };
            oldView = downloadLink.setView(newView);
            FFmpeg ffmpeg = new FFmpeg();

            // debug

            requestFileInformation(downloadLink);

            final SingleDownloadController dlc = downloadLink.getDownloadLinkController();
            final List<File> locks = new ArrayList<File>();
            locks.addAll(listProcessFiles(downloadLink));
            try {
                new DownloadLinkDownloadable(downloadLink).checkIfWeCanWrite(new ExceptionRunnable() {

                    @Override
                    public void run() throws Exception {
                        try {
                            for (File lock : locks) {
                                logger.info("Lock " + lock);
                                dlc.lockFile(lock);
                            }
                        } catch (FileIsLockedException e) {
                            for (File lock : locks) {
                                dlc.unlockFile(lock);
                            }
                            throw e;
                        }

                    }
                }, null);

                final String videoStreamPath = getVideoStreamPath(downloadLink);
                if (videoStreamPath != null && new File(videoStreamPath).exists()) {
                    data.setDashVideoFinished(true);

                }
                YoutubeVariantInterface variant = getVariant(downloadLink);
                boolean loadVideo = !data.isDashVideoFinished();
                if (videoStreamPath == null || variant.getType() == YoutubeVariantInterface.DownloadType.DASH_AUDIO) {
                    /* Skip video if just audio should be downloaded */
                    loadVideo = false;
                } else {
                    loadVideo |= !(new File(videoStreamPath).exists() && new File(videoStreamPath).length() > 0);
                }

                if (loadVideo) {
                    /* videoStream not finished yet, resume/download it */
                    Boolean ret = downloadDashStream(downloadLink, data, true);
                    if (ret == null) {
                        return;
                    }
                    if (!ret) {
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                }
                /* videoStream is finished */
                final String audioStreamPath = getAudioStreamPath(downloadLink);
                if (audioStreamPath != null && new File(audioStreamPath).exists()) {
                    data.setDashAudioFinished(true);
                }
                boolean loadAudio = !data.isDashAudioFinished();
                loadAudio |= !(new File(audioStreamPath).exists() && new File(audioStreamPath).length() > 0);
                if (loadAudio) {
                    /* audioStream not finished yet, resume/download it */
                    Boolean ret = downloadDashStream(downloadLink, data, false);
                    if (ret == null) {
                        return;
                    }
                    if (!ret) {
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                }
                if (new File(audioStreamPath).exists() && !new File(downloadLink.getFileOutput()).exists()) {
                    /* audioStream also finished */
                    /* Do we need an exception here? If a Video is downloaded it is always finished before the audio part. TheCrap */

                    if (videoStreamPath != null && new File(videoStreamPath).exists()) {
                        final FFMpegProgress progress = new FFMpegProgress();
                        progress.setProgressSource(this);
                        try {
                            downloadLink.addPluginProgress(progress);
                            String codec = variant.getiTagVideo().getCodecVideo();
                            if (codec.equalsIgnoreCase("vp9") || codec.equalsIgnoreCase("vp8") || variant.toString().startsWith("WEBM")) {
                                if (ffmpeg.muxToWebm(progress, downloadLink.getFileOutput(), videoStreamPath, audioStreamPath)) {
                                    downloadLink.getLinkStatus().setStatus(LinkStatus.FINISHED);
                                    new File(videoStreamPath).delete();
                                    new File(audioStreamPath).delete();
                                } else {
                                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, _GUI._.YoutubeDash_handleFree_error_());

                                }
                            } else {
                                if (ffmpeg.muxToMp4(progress, downloadLink.getFileOutput(), videoStreamPath, audioStreamPath)) {
                                    downloadLink.getLinkStatus().setStatus(LinkStatus.FINISHED);
                                    new File(videoStreamPath).delete();
                                    new File(audioStreamPath).delete();
                                } else {
                                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, _GUI._.YoutubeDash_handleFree_error_());

                                }
                            }
                        } finally {
                            downloadLink.removePluginProgress(progress);
                        }
                    } else {

                        if (variant instanceof YoutubeVariant || variant instanceof YoutubeCustomConvertVariant) {
                            YoutubeVariant ytVariant = (variant instanceof YoutubeCustomConvertVariant) ? ((YoutubeCustomConvertVariant) variant).getSource() : (YoutubeVariant) variant;

                            if (ytVariant.getFileExtension().toLowerCase(Locale.ENGLISH).equals("aac")) {

                                final FFMpegProgress progress = new FFMpegProgress();
                                progress.setProgressSource(this);
                                try {
                                    downloadLink.addPluginProgress(progress);

                                    if (ffmpeg.generateAac(progress, downloadLink.getFileOutput(), audioStreamPath)) {
                                        downloadLink.getLinkStatus().setStatus(LinkStatus.FINISHED);
                                        new File(audioStreamPath).delete();
                                    } else {
                                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, _GUI._.YoutubeDash_handleFree_error_());

                                    }
                                } finally {
                                    downloadLink.removePluginProgress(progress);
                                }
                            } else if (ytVariant.getFileExtension().toLowerCase(Locale.ENGLISH).equals("m4a")) {

                                final FFMpegProgress progress = new FFMpegProgress();
                                progress.setProgressSource(this);
                                try {
                                    downloadLink.addPluginProgress(progress);

                                    if (ffmpeg.generateM4a(progress, downloadLink.getFileOutput(), audioStreamPath)) {
                                        downloadLink.getLinkStatus().setStatus(LinkStatus.FINISHED);
                                        new File(audioStreamPath).delete();
                                    } else {
                                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, _GUI._.YoutubeDash_handleFree_error_());

                                    }
                                } finally {
                                    downloadLink.removePluginProgress(progress);
                                }

                            } else if (ytVariant.getFileExtension().toLowerCase(Locale.ENGLISH).equals("ogg")) {

                                final FFMpegProgress progress = new FFMpegProgress();
                                progress.setProgressSource(this);
                                try {
                                    downloadLink.addPluginProgress(progress);

                                    if (ffmpeg.generateOggAudio(progress, downloadLink.getFileOutput(), audioStreamPath)) {
                                        downloadLink.getLinkStatus().setStatus(LinkStatus.FINISHED);
                                        new File(audioStreamPath).delete();
                                    } else {
                                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, _GUI._.YoutubeDash_handleFree_error_());

                                    }
                                } finally {
                                    downloadLink.removePluginProgress(progress);
                                }

                            } else {
                                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                            }

                        } else {
                            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                        }

                    }
                }
            } catch (final FileIsLockedException e) {
                throw new PluginException(LinkStatus.ERROR_ALREADYEXISTS);
            } finally {

                for (File lock : locks) {
                    dlc.unlockFile(lock);
                }

            }
        } finally {
            if (oldView != null) {
                downloadLink.setView(oldView);
            }
        }
    }

    @Override
    public void handlePremium(DownloadLink downloadLink, Account account) throws Exception {
        isDownloading = true;
        YoutubeProperties data = downloadLink.bindData(YoutubeProperties.class);
        cfg = PluginJsonConfig.get(YoutubeConfig.class);

        if (!downloadLink.getDownloadURL().startsWith("youtubev2://")) {
            convertOldLink(downloadLink);
        }
        YoutubeHelper helper = getCachedHelper();
        YoutubeVariantInterface variant = getVariant(downloadLink);

        if (account != null) {
            helper.login(account, false, false);
        }
        // if (!Application.isJared(null)) throw new RuntimeException("Shit happened");
        boolean resume = true;
        switch (variant.getType()) {
        case DESCRIPTION:
            downloadDescription(downloadLink);
            return;
        case IMAGE:
            this.setBrowserExclusive();

            this.requestFileInformation(downloadLink);
            this.br.setDebug(true);

            this.dl = jd.plugins.BrowserAdapter.openDownload(this.br, downloadLink, getUrlPair(downloadLink).data, resume, 1);
            if (!this.dl.getConnection().isContentDisposition() && !this.dl.getConnection().getContentType().startsWith("image/")) {
                if (dl.getConnection().getResponseCode() == 500) {
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, _GUI._.hoster_servererror("Youtube"), 5 * 60 * 1000l);
                }

                this.dl.getConnection().disconnect();
                throw new PluginException(LinkStatus.ERROR_RETRY);

            }
            if (!this.dl.startDownload()) {
                throw new PluginException(LinkStatus.ERROR_RETRY);
            }
            break;
        case SUBTITLES:

            this.setBrowserExclusive();

            this.requestFileInformation(downloadLink);
            this.br.setDebug(true);

            this.dl = jd.plugins.BrowserAdapter.openDownload(this.br, downloadLink, getUrlPair(downloadLink).data, resume, 1);
            if (!this.dl.getConnection().isContentDisposition() && !this.dl.getConnection().getContentType().startsWith("text/xml")) {
                if (dl.getConnection().getResponseCode() == 500) {
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, _GUI._.hoster_servererror("Youtube"), 5 * 60 * 1000l);
                }

                this.dl.getConnection().disconnect();
                throw new PluginException(LinkStatus.ERROR_RETRY);

            }
            if (!this.dl.startDownload()) {
                throw new PluginException(LinkStatus.ERROR_RETRY);
            }

            break;
        case VIDEO:

            if (variant instanceof YoutubeVariant) {
                if (((YoutubeVariant) variant).name().contains("DEMUX") || ((YoutubeVariant) variant).name().contains("MP3")) {
                    checkFFmpeg(downloadLink, _GUI._.YoutubeDash_handleDownload_youtube_demux());
                }
            }

            this.setBrowserExclusive();
            //

            this.requestFileInformation(downloadLink);
            this.br.setDebug(true);

            this.dl = jd.plugins.BrowserAdapter.openDownload(this.br, downloadLink, getUrlPair(downloadLink).video, resume, getChunksPerStream());
            if (!this.dl.getConnection().isContentDisposition() && !this.dl.getConnection().getContentType().startsWith("video") && !this.dl.getConnection().getContentType().startsWith("application")) {
                if (dl.getConnection().getResponseCode() == 500) {
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, _GUI._.hoster_servererror("Youtube"), 5 * 60 * 1000l);
                }

                this.dl.getConnection().disconnect();
                throw new PluginException(LinkStatus.ERROR_RETRY);

            }
            if (!this.dl.startDownload()) {
                throw new PluginException(LinkStatus.ERROR_RETRY);
            }
            break;
        case DASH_AUDIO:
        case DASH_VIDEO:
            checkFFmpeg(downloadLink, _GUI._.YoutubeDash_handleDownload_youtube_dash());
            handleDash(downloadLink, data, null);
            break;

        }
        if (variant.hasConverer(downloadLink)) {
            long lastMod = new File(downloadLink.getFileOutput()).lastModified();

            variant.convert(downloadLink, this);

            try {

                if (lastMod > 0 && JsonConfig.create(GeneralSettings.class).isUseOriginalLastModified()) {
                    new File(downloadLink.getFileOutput()).setLastModified(lastMod);
                }
            } catch (final Throwable e) {
                LogSource.exception(logger, e);
            }
        }

        switch (variant.getType()) {
        case SUBTITLES:
            // rename subtitles to match the videos.
            // this code
            if (cfg.isSubtitleCopyforEachVideoVariant()) {
                FilePackage pkg = downloadLink.getParentNode();
                boolean readL2 = pkg.getModifyLock().readLock();
                File finalFile = new File(downloadLink.getFileOutput(false, false));
                boolean copied = false;
                try {
                    String myID = downloadLink.getStringProperty(YoutubeHelper.YT_ID, null);
                    for (DownloadLink child : pkg.getChildren()) {
                        try {
                            if (myID.equals(child.getStringProperty(YoutubeHelper.YT_ID, null))) {
                                LinkVariant v = getActiveVariantByLink(child);
                                if (v instanceof YoutubeVariant) {
                                    switch (((YoutubeVariant) v).getGroup()) {
                                    case VIDEO:
                                    case VIDEO_3D:
                                        String ext = Files.getExtension(child.getFinalFileName());
                                        if (StringUtils.isNotEmpty(ext)) {
                                            String base = child.getFinalFileName().substring(0, child.getFinalFileName().length() - ext.length() - 1);

                                            String code = downloadLink.getStringProperty(YoutubeHelper.YT_SUBTITLE_CODE, "");
                                            Locale locale = TranslationFactory.stringToLocale(code);

                                            File newFile;
                                            IO.copyFile(finalFile, newFile = new File(finalFile.getParentFile(), base + "." + locale.getDisplayLanguage() + ".srt"));

                                            try {

                                                if (JsonConfig.create(GeneralSettings.class).isUseOriginalLastModified()) {
                                                    newFile.setLastModified(finalFile.lastModified());
                                                }
                                            } catch (final Throwable e) {
                                                LogSource.exception(logger, e);
                                            }

                                            downloadLink.setFinalFileName(newFile.getName());
                                            copied = true;
                                        }
                                        break;
                                    default:
                                        break;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            getLogger().log(e);
                        }

                    }
                    if (copied) {
                        finalFile.delete();
                    }
                } finally {
                    pkg.getModifyLock().readUnlock(readL2);
                }
            }

            break;
        }

    }

    private void downloadDescription(DownloadLink downloadLink) throws Exception {
        final String description = downloadLink.getStringProperty(YoutubeHelper.YT_DESCRIPTION, null);
        if (description == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        final byte[] bytes = description.getBytes("UTF-8");
        final File outputFile = new File(downloadLink.getFileOutput());
        final DiskSpaceReservation reservation = new DiskSpaceReservation() {

            @Override
            public long getSize() {
                return Math.max(0, bytes.length - outputFile.length());
            }

            @Override
            public File getDestination() {
                return outputFile;
            }
        };
        final DownloadLinkDownloadable downloadable = new DownloadLinkDownloadable(downloadLink);
        if (!downloadable.checkIfWeCanWrite(new ExceptionRunnable() {

            @Override
            public void run() throws Exception {
                downloadable.checkAndReserve(reservation);
                try {
                    downloadable.lockFiles(outputFile);
                } catch (FileIsLockedException e) {
                    downloadable.unlockFiles(outputFile);
                    throw new PluginException(LinkStatus.ERROR_ALREADYEXISTS);
                }
            }
        }, null)) {
            throw new SkipReasonException(SkipReason.INVALID_DESTINATION);
        }
        try {
            final long size = bytes.length;
            IO.writeToFile(outputFile, bytes);
            downloadable.setDownloadTotalBytes(size);
            downloadable.setDownloadBytesLoaded(size);
            downloadable.setLinkStatus(LinkStatus.FINISHED);
        } finally {
            downloadable.unlockFiles(outputFile);
        }
    }

    @Override
    public boolean isProxyRotationEnabledForLinkChecker() {
        return super.isProxyRotationEnabledForLinkChecker();
    }

    @Override
    public void resetDownloadlink(DownloadLink downloadLink) {
        resetStreamUrls(downloadLink);
        YoutubeProperties data = downloadLink.bindData(YoutubeProperties.class);
        data.setDashAudioBytesLoaded(0);
        data.setDashAudioFinished(false);
        data.setDashAudioSize(-1);
        data.setDashVideoBytesLoaded(0);
        data.setDashVideoFinished(false);
        data.setDashVideoSize(-1);
        downloadLink.setProperty(DASH_VIDEO_CHUNKS, Property.NULL);
        downloadLink.setProperty(DASH_AUDIO_CHUNKS, Property.NULL);

    }

    @Override
    public void resetPluginGlobals() {
    }

    @Override
    public String getDescription() {
        return "JDownloader's YouTube Plugin helps downloading videoclips from youtube.com. YouTube provides different video formats and qualities. JDownloader is able to extract audio after download, and save it as mp3 file. \r\n - Hear your favourite YouTube Clips on your MP3 Player.";
    }

    protected FilePair[] listFilePairsToMove(DownloadLink link, String currentDirectory, String currentName, String newDirectory, String newName) {
        List<FilePair> ret = new ArrayList<PluginForHost.FilePair>();
        ret.add(new FilePair(new File(new File(currentDirectory), currentName + ".part"), new File(new File(newDirectory), newName + ".part")));
        ret.add(new FilePair(new File(new File(currentDirectory), currentName), new File(new File(newDirectory), newName)));
        try {
            YoutubeVariantInterface variant = getVariant(link);
            if (variant != null) {
                for (File f : variant.listProcessFiles(link)) {
                    FilePair fp = new FilePair(new File(new File(currentDirectory), f.getName()), new File(new File(newDirectory), newName + f.getName().substring(currentName.length())));
                    ret.add(fp);
                    fp = new FilePair(new File(new File(currentDirectory), f.getName() + ".part"), new File(new File(newDirectory), newName + f.getName().substring(currentName.length()) + ".part"));
                    ret.add(fp);
                }

                switch (variant.getType()) {
                case DASH_AUDIO:
                case DASH_VIDEO:
                    String vs = getVideoStreamPath(link);
                    String as = getAudioStreamPath(link);
                    if (StringUtils.isNotEmpty(vs)) {
                        // aac only does not have video streams
                        // ret.add(new File(vs));
                        // ret.add(new File(vs + ".part"));

                        ret.add(new FilePair(new File(new File(currentDirectory), new File(vs).getName() + ".part"), new File(new File(newDirectory), new File(vs).getName() + ".part")));
                        ret.add(new FilePair(new File(new File(currentDirectory), new File(vs).getName()), new File(new File(newDirectory), new File(vs).getName())));

                    }
                    if (StringUtils.isNotEmpty(as)) {
                        ret.add(new FilePair(new File(new File(currentDirectory), new File(as).getName() + ".part"), new File(new File(newDirectory), new File(as).getName() + ".part")));
                        ret.add(new FilePair(new File(new File(currentDirectory), new File(as).getName()), new File(new File(newDirectory), new File(as).getName())));
                    }

                    break;

                default:

                }
            }
        } catch (PluginException e) {
            e.printStackTrace();
        }

        return ret.toArray(new FilePair[] {});
    }

    @Override
    public List<File> listProcessFiles(DownloadLink link) {
        List<File> ret = super.listProcessFiles(link);

        try {
            YoutubeVariantInterface variant = getVariant(link);
            if (variant == null) {
                return ret;
            }
            ret.addAll(variant.listProcessFiles(link));
            switch (variant.getType()) {
            case DASH_AUDIO:
            case DASH_VIDEO:
                String vs = getVideoStreamPath(link);
                String as = getAudioStreamPath(link);
                if (StringUtils.isNotEmpty(vs)) {
                    // aac only does not have video streams
                    ret.add(new File(vs));
                    ret.add(new File(vs + ".part"));
                }
                if (StringUtils.isNotEmpty(as)) {
                    ret.add(new File(as));
                    ret.add(new File(as + ".part"));
                }

                break;

            default:

            }
        } catch (PluginException e) {
            e.printStackTrace();
        }

        return ret;
    }

    private YoutubeHelper getCachedHelper() {
        YoutubeHelper ret = cachedHelper;
        if (ret == null || ret.getBr() != this.br) {
            ret = new YoutubeHelper(br, PluginJsonConfig.get(YoutubeConfig.class), getLogger());

        }
        ret.setupProxy();
        return ret;
    }

    public String getAudioStreamPath(DownloadLink link) throws PluginException {
        String audioFilenName = getDashAudioFileName(link);
        if (StringUtils.isEmpty(audioFilenName)) {
            return null;
        }
        return new File(link.getDownloadDirectory(), audioFilenName).getAbsolutePath();
    }

    public String getDashAudioFileName(DownloadLink link) throws PluginException {
        YoutubeVariantInterface var = getVariant(link);
        switch (var.getType()) {
        case DASH_AUDIO:
        case DASH_VIDEO:
            break;
        default:
            return null;
        }
        // add both - audio and videoid to the path. else we might get conflicts if we download 2 qualities with the same audiostream
        return link.getStringProperty(YoutubeHelper.YT_ID, null) + "_" + var.getiTagVideo() + "_" + var.getiTagAudio() + ".dashAudio";
    }

    public String getVideoStreamPath(DownloadLink link) throws PluginException {
        String videoFileName = getDashVideoFileName(link);
        if (StringUtils.isEmpty(videoFileName)) {
            return null;
        }
        return new File(link.getDownloadDirectory(), videoFileName).getAbsolutePath();
    }

    public String getDashVideoFileName(DownloadLink link) throws PluginException {
        YoutubeVariantInterface var = getVariant(link);
        switch (var.getType()) {
        case DASH_AUDIO:
        case DASH_VIDEO:
            break;
        default:
            return null;
        }
        // add both - audio and videoid to the path. else we might get conflicts if we download 2 qualities with the same audiostream
        return link.getStringProperty(YoutubeHelper.YT_ID, null) + "_" + var.getiTagVideo() + "_" + var.getiTagAudio() + ".dashVideo";
    }

    public class SubtitleVariant implements YoutubeVariantInterface {

        private Locale locale;
        private String code;

        public SubtitleVariant(String code) {
            this.code = code;
            locale = TranslationFactory.stringToLocale(code);
        }

        @Override
        public String _getTooltipDescription() {
            return _getExtendedName();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || !(obj instanceof SubtitleVariant)) {
                return false;
            }
            return code.equals(((SubtitleVariant) obj).code);
        }

        @Override
        public int hashCode() {
            return code.hashCode();
        }

        public String _getName() {
            return _GUI._.YoutubeDash_getName_subtitles_(locale.getDisplayName());
        }

        public Icon _getIcon() {
            return null;
        }

        @Override
        public String getFileExtension() {
            return "srt";
        }

        @Override
        public String _getUniqueId() {
            return code;
        }

        @Override
        public String getMediaTypeID() {
            return VariantGroup.SUBTITLES.name();
        }

        @Override
        public YoutubeITAG getiTagVideo() {
            return null;
        }

        @Override
        public YoutubeITAG getiTagAudio() {
            return null;
        }

        @Override
        public YoutubeITAG getiTagData() {
            return null;
        }

        @Override
        public double getQualityRating() {
            return 0;
        }

        @Override
        public String getTypeId() {
            return code;
        }

        @Override
        public DownloadType getType() {
            return DownloadType.SUBTITLES;
        }

        @Override
        public VariantGroup getGroup() {
            return VariantGroup.SUBTITLES;
        }

        @Override
        public void convert(DownloadLink downloadLink, PluginForHost plugin) {
        }

        @Override
        public String getQualityExtension() {
            return code;
        }

        @Override
        public String modifyFileName(String formattedFilename, DownloadLink link) {
            return formattedFilename;
        }

        @Override
        public boolean hasConverer(DownloadLink downloadLink) {
            return false;
        }

        @Override
        public List<File> listProcessFiles(DownloadLink link) {
            return null;
        }

        @Override
        public String _getExtendedName() {
            return _GUI._.YoutubeDash_getName_subtitles_(locale.getDisplayName());
        }

    }

    @Override
    public LinkVariant getActiveVariantByLink(DownloadLink downloadLink) {
        Object variant = downloadLink.getTempProperties().getProperty(YoutubeHelper.YT_VARIANT, null);

        if (variant != null && variant instanceof SubtitleVariant) {
            //
            return (LinkVariant) variant;
        }
        if (variant != null && variant instanceof YoutubeVariantInterface) {
            //
            return (YoutubeVariantInterface) variant;
        }
        String name = downloadLink.getStringProperty(YoutubeHelper.YT_VARIANT, null);
        try {
            if (name != null) {

                YoutubeVariantInterface v = getCachedHelper().getVariantById(name);
                if (v == YoutubeVariant.SUBTITLES) {

                    SubtitleVariant var = new SubtitleVariant(downloadLink.getStringProperty(YoutubeHelper.YT_SUBTITLE_CODE));
                    downloadLink.getTempProperties().setProperty(YoutubeHelper.YT_VARIANT, var);
                    return var;
                } else {
                    downloadLink.getTempProperties().setProperty(YoutubeHelper.YT_VARIANT, v);
                    downloadLink.getTempProperties().setProperty(YoutubeHelper.YT_VARIANT, v);
                    return v;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            getLogger().log(e);
        }
        return null;
    }

    @Override
    public void setActiveVariantByLink(DownloadLink downloadLink, LinkVariant variant) {
        YoutubeConfig cfg = PluginJsonConfig.get(YoutubeConfig.class);

        if (variant == null) {
            return;
        }
        if (variant instanceof SubtitleVariant) {

            // reset Streams urls. we need new ones

            resetStreamUrls(downloadLink);
            downloadLink.setDownloadSize(-1);
            downloadLink.setVerifiedFileSize(-1);
            downloadLink.getTempProperties().setProperty(YoutubeHelper.YT_VARIANT, Property.NULL);
            downloadLink.setProperty(YoutubeHelper.YT_VARIANT, YoutubeVariant.SUBTITLES.name());
            downloadLink.setProperty(YoutubeHelper.YT_EXT, YoutubeVariant.SUBTITLES.getFileExtension());
            downloadLink.setProperty(YoutubeHelper.YT_SUBTITLE_CODE, ((SubtitleVariant) variant).code);
            String filename;
            downloadLink.setFinalFileName(filename = getCachedHelper().createFilename(downloadLink));
            downloadLink.setPluginPatternMatcher("youtubev2://" + YoutubeVariant.SUBTITLES + "/" + downloadLink.getStringProperty(YoutubeHelper.YT_ID) + "/");

            // if (prefers) {
            downloadLink.setContentUrl("https://www.youtube.com" + "/watch?v=" + downloadLink.getStringProperty(YoutubeHelper.YT_ID) + "&variant=" + variant);
            // } else {
            // downloadLink.setContentUrl("http://www.youtube.com" + "/watch?v=" + downloadLink.getStringProperty(YoutubeHelper.YT_ID) +
            // "&variant=" + variant);
            //
            // }

            downloadLink.setLinkID("youtubev2://" + YoutubeVariant.SUBTITLES + "/" + downloadLink.getStringProperty(YoutubeHelper.YT_ID));

        } else if (variant instanceof YoutubeVariantInterface) {
            YoutubeVariantInterface v = (YoutubeVariantInterface) variant;
            // reset Streams urls. we need new ones

            resetStreamUrls(downloadLink);
            downloadLink.setDownloadSize(-1);
            downloadLink.setVerifiedFileSize(-1);
            downloadLink.getTempProperties().setProperty(YoutubeHelper.YT_VARIANT, Property.NULL);
            downloadLink.setProperty(YoutubeHelper.YT_VARIANT, v._getUniqueId());
            downloadLink.setProperty(YoutubeHelper.YT_EXT, v.getFileExtension());
            String filename;
            downloadLink.setFinalFileName(filename = getCachedHelper().createFilename(downloadLink));
            downloadLink.setPluginPatternMatcher("youtubev2://" + v._getUniqueId() + "/" + downloadLink.getStringProperty(YoutubeHelper.YT_ID) + "/");
            // if (prefers) {
            downloadLink.setContentUrl("https://www.youtube.com" + "/watch?v=" + downloadLink.getStringProperty(YoutubeHelper.YT_ID) + "&variant=" + variant);
            // } else {
            // downloadLink.setContentUrl("http://www.youtube.com" + "/watch?v=" + downloadLink.getStringProperty(YoutubeHelper.YT_ID) +
            // "&variant=" + variant);
            //
            // }

            downloadLink.setLinkID("youtubev2://" + v._getUniqueId() + "/" + downloadLink.getStringProperty(YoutubeHelper.YT_ID));

        }
        if (downloadLink.getStringProperty(YoutubeHelper.YT_TITLE, null) == null) {
            // old link?
            String oldLinkName = downloadLink.getStringProperty("name", null);
            downloadLink.setFinalFileName(oldLinkName);
        }

    }

    protected void resetStreamUrls(DownloadLink downloadLink) {
        downloadLink.setProperty(YoutubeHelper.YT_STREAMURL_AUDIO, Property.NULL);
        downloadLink.setProperty(YoutubeHelper.YT_STREAMURL_VIDEO, Property.NULL);
        downloadLink.setProperty(YoutubeHelper.YT_STREAMURL_DATA, Property.NULL);
    }

    public boolean hasVariantToChooseFrom(DownloadLink downloadLink) {
        return downloadLink.hasProperty(YoutubeHelper.YT_VARIANTS);
    }

    @Override
    public List<LinkVariant> getVariantsByLink(DownloadLink downloadLink) {
        if (hasVariantToChooseFrom(downloadLink) == false) {
            return null;
        }

        Object ret = downloadLink.getTempProperties().getProperty(YoutubeHelper.YT_VARIANTS, null);
        if (ret != null) {
            return (List<LinkVariant>) ret;
        }
        String lngCodes = downloadLink.getStringProperty(YoutubeHelper.YT_SUBTITLE_CODE_LIST, null);
        if (StringUtils.isNotEmpty(lngCodes)) {

            // subtitles variants
            List<LinkVariant> ret2 = new ArrayList<LinkVariant>();
            for (String code : JSonStorage.restoreFromString(lngCodes, new TypeRef<ArrayList<String>>() {
            })) {
                ret2.add(new SubtitleVariant(code));
            }
            Collections.sort(ret2, new Comparator<LinkVariant>() {

                @Override
                public int compare(LinkVariant o1, LinkVariant o2) {
                    return o1._getName().compareToIgnoreCase(o2._getName());

                }

            });
            downloadLink.getTempProperties().setProperty(YoutubeHelper.YT_VARIANTS, ret2);
            return ret2;
        }
        String idsString = downloadLink.getStringProperty(YoutubeHelper.YT_VARIANTS, "[]");
        ArrayList<String> ids = JSonStorage.restoreFromString(idsString, new TypeRef<ArrayList<String>>() {
        });
        List<LinkVariant> ret2 = new ArrayList<LinkVariant>();
        for (String id : ids) {
            try {
                ret2.add(getCachedHelper().getVariantById(id));
            } catch (Exception e) {
                getLogger().log(e);
            }
        }
        downloadLink.getTempProperties().setProperty(YoutubeHelper.YT_VARIANTS, ret2);
        return ret2;
    }

    @Override
    public void extendLinkgrabberContextMenu(final JComponent parent, final PluginView<CrawledLink> pv, Collection<PluginView<CrawledLink>> allPvs) {
        final JMenu setVariants = new JScrollMenu(_GUI._.YoutubeDashV2_extendLinkgrabberContextMenu_context_menu());
        setVariants.setIcon(DomainInfo.getInstance(getHost()).getFavIcon());
        setVariants.setEnabled(false);

        final JMenu addVariants = new JScrollMenu(_GUI._.YoutubeDashV2_extendLinkgrabberContextMenu_context_menu_add());

        addVariants.setIcon(new BadgeIcon(DomainInfo.getInstance(getHost()).getFavIcon(), new AbstractIcon(IconKey.ICON_ADD, 16), 4, 4));
        addVariants.setEnabled(false);
        new Thread("Collect Variants") {
            public void run() {
                HashMap<String, YoutubeVariantInterface> map = new HashMap<String, YoutubeVariantInterface>();
                final HashMap<String, ArrayList<YoutubeVariantInterface>> list = new HashMap<String, ArrayList<YoutubeVariantInterface>>();
                for (CrawledLink cl : pv.getChildren()) {
                    List<LinkVariant> v = getVariantsByLink(cl.getDownloadLink());
                    if (v != null) {
                        for (LinkVariant lv : v) {
                            if (lv instanceof YoutubeVariantInterface) {
                                if (map.put(((YoutubeVariantInterface) lv).getTypeId(), (YoutubeVariantInterface) lv) == null) {
                                    ArrayList<YoutubeVariantInterface> l = list.get(((YoutubeVariantInterface) lv).getGroup().name());
                                    if (l == null) {
                                        l = new ArrayList<YoutubeVariantInterface>();
                                        list.put(((YoutubeVariantInterface) lv).getGroup().name(), l);
                                    }
                                    l.add((YoutubeVariantInterface) lv);
                                }
                            }
                        }
                    }
                }

                new EDTRunner() {

                    @Override
                    protected void runInEDT() {

                        add(setVariants, addVariants, pv, list, VariantGroup.VIDEO);
                        add(setVariants, addVariants, pv, list, VariantGroup.AUDIO);

                        add(setVariants, addVariants, pv, list, VariantGroup.IMAGE);

                        add(setVariants, addVariants, pv, list, VariantGroup.VIDEO_3D);
                        add(setVariants, addVariants, pv, list, VariantGroup.SUBTITLES);

                    }

                    private void add(JMenu setVariants, JMenu addVariants, final PluginView<CrawledLink> pv, HashMap<String, ArrayList<YoutubeVariantInterface>> map, final VariantGroup group) {
                        ArrayList<YoutubeVariantInterface> list = map.get(group.name());
                        if (list == null || list.size() == 0) {
                            return;
                        }
                        final Comparator<YoutubeVariantInterface> comp;
                        Collections.sort(list, comp = new Comparator<YoutubeVariantInterface>() {

                            @Override
                            public int compare(YoutubeVariantInterface o1, YoutubeVariantInterface o2) {
                                return new Double(o2.getQualityRating()).compareTo(new Double(o1.getQualityRating()));
                            }
                        });
                        setVariants.setEnabled(true);
                        addVariants.setEnabled(true);

                        addToAddMenu(addVariants, pv, map, group, list, comp);
                        addToSetMenu(setVariants, pv, map, group, list, comp);

                    }

                    protected void addToAddMenu(JMenu addSubmenu, final PluginView<CrawledLink> pv, HashMap<String, ArrayList<YoutubeVariantInterface>> map, final VariantGroup group, ArrayList<YoutubeVariantInterface> list, final Comparator<YoutubeVariantInterface> comp) {
                        JMenu groupMenu = new JScrollMenu(group.getLabel());
                        if (map.size() == 1) {
                            groupMenu = addSubmenu;
                        } else {
                            addSubmenu.add(groupMenu);
                        }
                        groupMenu.add(new JMenuItem(new BasicAction() {
                            {
                                setName(_GUI._.YoutubeDashV2_add_best(group.getLabel()));
                            }

                            @Override
                            public void actionPerformed(ActionEvent e) {
                                java.util.List<CheckableLink> checkableLinks = new ArrayList<CheckableLink>(1);

                                for (CrawledLink cl : pv.getChildren()) {
                                    List<YoutubeVariantInterface> variants = new ArrayList<YoutubeVariantInterface>();
                                    for (LinkVariant v : getVariantsByLink(cl.getDownloadLink())) {
                                        if (v instanceof YoutubeVariantInterface) {
                                            variants.add((YoutubeVariantInterface) v);
                                        }
                                    }
                                    Collections.sort(variants, comp);
                                    for (YoutubeVariantInterface variant : variants) {
                                        if (variant.getGroup() == group) {
                                            CrawledLink newLink = LinkCollector.getInstance().addAdditional(cl, variant);

                                            if (newLink != null) {
                                                checkableLinks.add(newLink);
                                            } else {
                                                Toolkit.getDefaultToolkit().beep();
                                            }
                                            break;
                                        }

                                    }

                                }

                                LinkChecker<CheckableLink> linkChecker = new LinkChecker<CheckableLink>(true);
                                linkChecker.check(checkableLinks);
                            }

                        }));

                        for (final YoutubeVariantInterface v : list) {
                            groupMenu.add(new JMenuItem(new BasicAction() {
                                {

                                    setName(CFG_GUI.EXTENDED_VARIANT_NAMES_ENABLED.isEnabled() ? v._getExtendedName() : v._getName());

                                }

                                //
                                @Override
                                public void actionPerformed(ActionEvent e) {
                                    java.util.List<CheckableLink> checkableLinks = new ArrayList<CheckableLink>(1);

                                    for (CrawledLink cl : pv.getChildren()) {
                                        for (LinkVariant variants : getVariantsByLink(cl.getDownloadLink())) {
                                            if (variants instanceof YoutubeVariantInterface) {
                                                if (((YoutubeVariantInterface) variants).getTypeId().equals(v.getTypeId())) {
                                                    CrawledLink newLink = LinkCollector.getInstance().addAdditional(cl, v);

                                                    if (newLink != null) {
                                                        checkableLinks.add(newLink);
                                                    } else {
                                                        Toolkit.getDefaultToolkit().beep();
                                                    }
                                                }
                                            }
                                        }

                                    }

                                    LinkChecker<CheckableLink> linkChecker = new LinkChecker<CheckableLink>(true);
                                    linkChecker.check(checkableLinks);
                                }

                            }));

                        }

                        groupMenu.add(new JMenuItem(new BasicAction() {
                            {
                                setName(_GUI._.YoutubeDashV2_add_worst(group.getLabel()));
                            }

                            @Override
                            public void actionPerformed(ActionEvent e) {
                                java.util.List<CheckableLink> checkableLinks = new ArrayList<CheckableLink>(1);

                                for (CrawledLink cl : pv.getChildren()) {
                                    List<YoutubeVariantInterface> variants = new ArrayList<YoutubeVariantInterface>();
                                    for (LinkVariant v : getVariantsByLink(cl.getDownloadLink())) {
                                        if (v instanceof YoutubeVariantInterface) {
                                            variants.add((YoutubeVariantInterface) v);
                                        }
                                    }
                                    Collections.sort(variants, comp);
                                    for (int i = variants.size() - 1; i >= 0; i--) {
                                        YoutubeVariantInterface variant = variants.get(i);
                                        if (variant.getGroup() == group) {
                                            CrawledLink newLink = LinkCollector.getInstance().addAdditional(cl, variant);

                                            if (newLink != null) {
                                                checkableLinks.add(newLink);
                                            } else {
                                                Toolkit.getDefaultToolkit().beep();
                                            }
                                            break;
                                        }

                                    }

                                }

                                LinkChecker<CheckableLink> linkChecker = new LinkChecker<CheckableLink>(true);
                                linkChecker.check(checkableLinks);
                            }

                        }));
                    }

                    protected void addToSetMenu(JMenu setVariants, final PluginView<CrawledLink> pv, HashMap<String, ArrayList<YoutubeVariantInterface>> map, final VariantGroup group, ArrayList<YoutubeVariantInterface> list, final Comparator<YoutubeVariantInterface> comp) {
                        JMenu groupMenu = new JScrollMenu(group.getLabel());
                        if (map.size() == 1) {
                            groupMenu = setVariants;
                        } else {
                            setVariants.add(groupMenu);
                        }
                        groupMenu.add(new JMenuItem(new BasicAction() {
                            {
                                setName(_GUI._.YoutubeDashV2_add_best(group.getLabel()));
                            }

                            @Override
                            public void actionPerformed(ActionEvent e) {
                                java.util.List<CheckableLink> checkableLinks = new ArrayList<CheckableLink>(1);

                                for (CrawledLink cl : pv.getChildren()) {
                                    List<YoutubeVariantInterface> variants = new ArrayList<YoutubeVariantInterface>();
                                    for (LinkVariant v : getVariantsByLink(cl.getDownloadLink())) {
                                        if (v instanceof YoutubeVariantInterface) {
                                            variants.add((YoutubeVariantInterface) v);
                                        }
                                    }
                                    Collections.sort(variants, comp);
                                    for (YoutubeVariantInterface variant : variants) {
                                        if (variant.getGroup() == group) {
                                            LinkCollector.getInstance().setActiveVariantForLink(cl.getDownloadLink(), variant);

                                            checkableLinks.add(cl);
                                            break;
                                        }

                                    }

                                }

                                LinkChecker<CheckableLink> linkChecker = new LinkChecker<CheckableLink>(true);
                                linkChecker.check(checkableLinks);
                            }

                        }));
                        for (final YoutubeVariantInterface v : list) {
                            groupMenu.add(new JMenuItem(new BasicAction() {
                                {
                                    setName(CFG_GUI.EXTENDED_VARIANT_NAMES_ENABLED.isEnabled() ? v._getExtendedName() : v._getName());
                                }

                                @Override
                                public void actionPerformed(ActionEvent e) {
                                    java.util.List<CheckableLink> checkableLinks = new ArrayList<CheckableLink>(1);

                                    for (CrawledLink cl : pv.getChildren()) {
                                        for (LinkVariant variants : getVariantsByLink(cl.getDownloadLink())) {
                                            if (variants instanceof YoutubeVariantInterface) {
                                                if (((YoutubeVariantInterface) variants).getTypeId().equals(v.getTypeId())) {
                                                    LinkCollector.getInstance().setActiveVariantForLink(cl.getDownloadLink(), v);
                                                    checkableLinks.add(cl);
                                                }
                                            }
                                        }

                                    }

                                    LinkChecker<CheckableLink> linkChecker = new LinkChecker<CheckableLink>(true);
                                    linkChecker.check(checkableLinks);
                                }

                            }));

                        }

                        groupMenu.add(new JMenuItem(new BasicAction() {
                            {
                                setName(_GUI._.YoutubeDashV2_add_worst(group.getLabel()));
                            }

                            @Override
                            public void actionPerformed(ActionEvent e) {
                                java.util.List<CheckableLink> checkableLinks = new ArrayList<CheckableLink>(1);

                                for (CrawledLink cl : pv.getChildren()) {
                                    List<YoutubeVariantInterface> variants = new ArrayList<YoutubeVariantInterface>();
                                    for (LinkVariant v : getVariantsByLink(cl.getDownloadLink())) {
                                        if (v instanceof YoutubeVariantInterface) {
                                            variants.add((YoutubeVariantInterface) v);
                                        }
                                    }
                                    Collections.sort(variants, comp);
                                    for (int i = variants.size() - 1; i >= 0; i--) {
                                        YoutubeVariantInterface variant = variants.get(i);
                                        if (variant.getGroup() == group) {
                                            LinkCollector.getInstance().setActiveVariantForLink(cl.getDownloadLink(), variant);

                                            checkableLinks.add(cl);
                                            break;
                                        }

                                    }

                                }

                                LinkChecker<CheckableLink> linkChecker = new LinkChecker<CheckableLink>(true);
                                linkChecker.check(checkableLinks);
                            }

                        }));
                    }
                };

            };
        }.start();

        parent.add(setVariants);
        parent.add(addVariants);
    }

    /* NO OVERRIDE!! We need to stay 0.9*compatible */
    public boolean allowHandle(final DownloadLink downloadLink, final PluginForHost plugin) {
        return downloadLink.getHost().equalsIgnoreCase(plugin.getHost());
    }

    protected void setConfigElements() {
    }

    @Override
    public void reset() {
    }
}
