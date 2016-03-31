package jd.plugins.components;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import org.appwork.storage.JSonStorage;
import org.appwork.utils.Application;
import org.appwork.utils.IO;
import org.appwork.utils.swing.dialog.Dialog;
import org.appwork.utils.swing.dialog.DialogCanceledException;
import org.appwork.utils.swing.dialog.DialogClosedException;
import org.jdownloader.controlling.ffmpeg.FFprobe;
import org.jdownloader.controlling.ffmpeg.json.Stream;
import org.jdownloader.controlling.ffmpeg.json.StreamInfo;
import org.jdownloader.plugins.components.youtube.YoutubeClipData;
import org.jdownloader.plugins.components.youtube.YoutubeITAG;
import org.jdownloader.plugins.components.youtube.YoutubeVariant;

import jd.http.Browser;
import jd.http.QueryInfo;
import jd.http.URLConnectionAdapter;
import jd.parser.Regex;

public class ItagHelper {
    public static void main(String[] args) throws DialogClosedException, DialogCanceledException {
        Application.setApplication(".jd_home");
        List<YoutubeITAG> dashVideos = new ArrayList<YoutubeITAG>();
        List<YoutubeITAG> dashAudios = new ArrayList<YoutubeITAG>();
        List<YoutubeITAG> nonDash = new ArrayList<YoutubeITAG>();
        loop: for (YoutubeITAG tag : YoutubeITAG.values()) {
            switch (tag) {
            case SUBTITLE:
            case IMAGE_HQ:
            case IMAGE_LQ:
            case IMAGE_MAX:
            case IMAGE_MQ:
                continue loop;
            }

            if (tag.getCodecAudio() == null) {
                dashVideos.add(tag);
            } else if (tag.getCodecVideo() == null) {
                dashAudios.add(tag);
            } else {
                nonDash.add(tag);
            }
        }
        StringBuilder sb = new StringBuilder();
        HashSet<String> dupes = new HashSet<String>();
        audioLoop: for (YoutubeITAG dashAudio : dashAudios) {
            for (YoutubeVariant v : YoutubeVariant.values()) {
                if (v.getiTagAudio() == dashAudio && v.getiTagVideo() == null) {
                    break audioLoop;
                }
            }

            String bitrate = new Regex(dashAudio.getQualityAudio(), "(\\d+)k").getMatch(0);

            String audioCodec = dashAudio.getCodecAudio();
            // String audioCodec = new Regex(dashAudio.name(), "(opus|aac)").getMatch(0);
            String name = "DASH_AUDIO_" + audioCodec + "_" + bitrate + "KBIT";
            // AAC_128(null, YoutubeVariantInterface.VariantGroup.AUDIO, YoutubeVariantInterface.DownloadType.DASH_AUDIO, "aac", null,
            // YoutubeITAG.DASH_AUDIO_128K_AAC, null, null, null) {
            // @Override
            // public String _getName() {
            // return _GUI.T.YoutubeVariant_name_AAC_128();
            // }
            //
            // @Override
            // public String getQualityExtension() {
            // return _GUI.T.YoutubeVariant_filenametag_AAC_128();
            // }
            // },
            name = name.toUpperCase(Locale.ENGLISH);
            String orgname = name;
            int i = 2;
            while (!dupes.add(name)) {
                name = orgname + "_" + (i++);
            }
            sb.append(name).append("(");
            sb.append("null");
            sb.append(", ");
            sb.append("YoutubeVariantInterface.VariantGroup.AUDIO");
            sb.append(", ");
            sb.append("YoutubeVariantInterface.DownloadType.DASH_AUDIO");
            sb.append(", ");
            sb.append("\"").append(getExtension(dashAudio).toString().toLowerCase(Locale.ENGLISH)).append("\"");
            sb.append(", ");
            sb.append("null");
            sb.append(", ");
            sb.append("YoutubeITAG." + dashAudio);
            sb.append(", ");
            sb.append("null");
            sb.append(", ");
            sb.append("null");
            sb.append(", ");
            sb.append("null");
            sb.append(")");
            sb.append("{\r\n");
            sb.append("@Override\r\n");
            sb.append("public String _getName() {\r\n");
            sb.append("    return _GUI.T.YoutubeVariant_name_generic_audio(\"" + bitrate + "kbit\",\"" + getExtension(dashAudio) + "\");\r\n");
            sb.append("}\r\n\r\n");
            sb.append("@Override\r\n");
            sb.append("public String getQualityExtension() {\r\n");
            sb.append("   return _GUI.T.YoutubeVariant_nametag_generic_audio(\"" + bitrate + "kbit\",\"" + getExtension(dashAudio) + "\");\r\n");
            sb.append("}\r\n");
            sb.append("},\r\n");
        }

        videoLoop: for (YoutubeITAG dashVideo : dashVideos) {
            audioLoop: for (YoutubeITAG dashAudio : dashAudios) {
                for (YoutubeVariant v : YoutubeVariant.values()) {
                    if (v.getiTagAudio() == dashAudio && v.getiTagVideo() == dashVideo) {
                        break audioLoop;
                    }
                }
                // unknown variant
                // DASH_VP9_2160P_30FPS_OPUS_64KBIT("_VP9_2160P_30FPS", YoutubeVariantInterface.VariantGroup.VIDEO,
                // YoutubeVariantInterface.DownloadType.DASH_VIDEO, "webm", YoutubeITAG.DASH_VIDEO_ITAG313_VP9_2160P_30FPS,
                // YoutubeITAG.DASH_AUDIO_OPUS_64KBIT, null, null, null) {
                // @Override
                // public String _getName() {
                //
                // return _GUI.T.YoutubeVariant_name_generic_video("720p","WebM");
                // }
                //
                // @Override
                // public String getQualityExtension() {
                // return _GUI.T.YoutubeVariant_name_generic_video("720p","WebM");
                // }
                // },
                String fps = new Regex(dashVideo.name(), "(\\d+fps)").getMatch(0);
                if (fps == null) {
                    fps = "30FPS";
                }
                String resolution = new Regex(dashVideo.getQualityVideo(), "(\\d+)p").getMatch(0);
                if (resolution == null) {
                    resolution = "" + (int) dashVideo.getQualityRating();
                }
                String bitrate = new Regex(dashAudio.getQualityAudio(), "(\\d+)k").getMatch(0);
                String videoCodec = dashVideo.getCodecVideo();
                // String videoCodec = new Regex(dashVideo.name(), "(h264|vp9)").getMatch(0);
                String audioCodec = dashAudio.getCodecAudio();
                if (!validCombination(videoCodec, audioCodec)) {
                    continue;
                }
                // String audioCodec = new Regex(dashAudio.name(), "(opus|aac)").getMatch(0);
                String name = "DASH_" + videoCodec + "_" + resolution + "P_" + fps + "_" + audioCodec + "_" + bitrate + "KBIT";
                name = name.toUpperCase(Locale.ENGLISH);
                String orgname = name;
                int i = 2;
                while (!dupes.add(name)) {
                    name = orgname + "_" + (i++);
                }
                sb.append(name).append("(");
                sb.append("\"").append(resolution + "P_" + getExtension(dashVideo)).append("\"");
                sb.append(", ");
                sb.append("YoutubeVariantInterface.VariantGroup.VIDEO");
                sb.append(", ");
                sb.append("YoutubeVariantInterface.DownloadType.DASH_VIDEO");
                sb.append(", ");
                sb.append("\"").append(getExtension(dashVideo).toString().toLowerCase(Locale.ENGLISH)).append("\"");
                sb.append(", ");
                sb.append("YoutubeITAG." + dashVideo);
                sb.append(", ");
                sb.append("YoutubeITAG." + dashAudio);
                sb.append(", ");
                sb.append("null");
                sb.append(", ");
                sb.append("null");
                sb.append(", ");
                sb.append("null");
                sb.append(")");
                sb.append("{\r\n");
                sb.append("@Override\r\n");
                sb.append("public String _getName() {\r\n");
                sb.append("    return _GUI.T.YoutubeVariant_name_generic_video(\"" + resolution + "p\",\"" + getExtension(dashVideo) + "\");\r\n");
                sb.append("}\r\n\r\n");
                sb.append("@Override\r\n");
                sb.append("public String getQualityExtension() {\r\n");
                sb.append("   return _GUI.T.YoutubeVariant_name_generic_video(\"" + resolution + "p\",\"" + getExtension(dashVideo) + "\");\r\n");
                sb.append("}\r\n");
                sb.append("},\r\n");

            }
        }

        videoLoop: for (YoutubeITAG dashVideo : nonDash) {

            for (YoutubeVariant v : YoutubeVariant.values()) {
                if (v.getiTagVideo() == dashVideo) {
                    continue videoLoop;
                }
            }
            // MP4_360("MP4_360", YoutubeVariantInterface.VariantGroup.VIDEO, YoutubeVariantInterface.DownloadType.VIDEO, "mp4",
            // YoutubeITAG.MP4_VIDEO_360P_H264_AUDIO_AAC, null, null, null, null) {
            // @Override
            // public String _getName() {
            // return _GUI.T.YoutubeVariant_name_MP4_360();
            // }
            //
            // @Override
            // public String getQualityExtension() {
            // return _GUI.T.YoutubeVariant_filenametag_MP4_360();
            // }
            // },
            String fps = new Regex(dashVideo.name(), "(\\d+fps)").getMatch(0);
            if (fps == null) {
                fps = "30FPS";
            }
            String resolution = new Regex(dashVideo.getQualityVideo(), "(\\d+)p").getMatch(0);
            if (resolution == null) {
                resolution = "" + (int) dashVideo.getQualityRating();
            }
            String bitrate = new Regex(dashVideo.getQualityAudio(), "(\\d+)k").getMatch(0);
            String videoCodec = dashVideo.getCodecVideo();
            // String videoCodec = new Regex(dashVideo.name(), "(h264|vp9)").getMatch(0);
            String audioCodec = dashVideo.getCodecAudio();
            if (!validCombination(videoCodec, audioCodec)) {
                continue;
            }
            // String audioCodec = new Regex(dashAudio.name(), "(opus|aac)").getMatch(0);
            String name = "" + videoCodec + "_" + resolution + "P_" + fps + "_" + audioCodec + "_" + bitrate + "KBIT";
            name = name.toUpperCase(Locale.ENGLISH);
            String orgname = name;
            int i = 2;
            while (!dupes.add(name)) {
                name = orgname + "_" + (i++);
            }
            sb.append(name).append("(");
            sb.append("\"").append(resolution + "P_" + getExtension(dashVideo)).append("\"");
            sb.append(", ");
            sb.append("YoutubeVariantInterface.VariantGroup.VIDEO");
            sb.append(", ");
            sb.append("YoutubeVariantInterface.DownloadType.VIDEO");
            sb.append(", ");
            sb.append("\"").append(getExtension(dashVideo).toString().toLowerCase(Locale.ENGLISH)).append("\"");
            sb.append(", ");
            sb.append("YoutubeITAG." + dashVideo);
            sb.append(", ");
            sb.append("null");
            sb.append(", ");
            sb.append("null");
            sb.append(", ");
            sb.append("null");
            sb.append(", ");
            sb.append("null");
            sb.append(")");
            sb.append("{\r\n");
            sb.append("@Override\r\n");
            sb.append("public String _getName() {\r\n");
            sb.append("    return _GUI.T.YoutubeVariant_name_generic_video(\"" + resolution + "p\",\"" + getExtension(dashVideo) + "\");\r\n");
            sb.append("}\r\n\r\n");
            sb.append("@Override\r\n");
            sb.append("public String getQualityExtension() {\r\n");
            sb.append("   return _GUI.T.YoutubeVariant_name_generic_video(\"" + resolution + "p\",\"" + getExtension(dashVideo) + "\");\r\n");
            sb.append("}\r\n");
            sb.append("},\r\n");

        }

        if (sb.length() > 0) {
            System.out.println("Missing Variant Found:");
            System.out.println("New Youtube Variant Found! Add to " + YoutubeVariant.class);

            System.out.println(sb.toString());

            try {
                Dialog.getInstance().showInputDialog(0, "New Youtube Variant Found! Add to " + YoutubeVariant.class, sb.toString());
            } catch (DialogClosedException e) {
                e.printStackTrace();
            } catch (DialogCanceledException e) {
                e.printStackTrace();
            }
        }

    }

    private static boolean validCombination(String videoCodec, String audioCodec) {
        if ("vp9".equalsIgnoreCase(videoCodec)) {
            if ("Opus".equalsIgnoreCase(audioCodec)) {
                return true;
            }
            if ("Vorbis".equalsIgnoreCase(audioCodec)) {
                return true;
            }
            if ("aac".equalsIgnoreCase(audioCodec)) {
                return false;
            }
        }
        if ("h264".equalsIgnoreCase(videoCodec)) {
            if ("Opus".equalsIgnoreCase(audioCodec)) {
                return false;
            }
            if ("Vorbis".equalsIgnoreCase(audioCodec)) {
                return false;
            }
            if ("aac".equalsIgnoreCase(audioCodec)) {
                return true;
            }
        }
        return false;
    }

    private static Object getExtension(YoutubeITAG dashVideo) {
        if ("vp9".equalsIgnoreCase(dashVideo.getCodecVideo())) {
            return "WebM";
        }
        if ("H264".equalsIgnoreCase(dashVideo.getCodecVideo())) {
            return "Mp4";
        }
        if ("Opus".equalsIgnoreCase(dashVideo.getCodecAudio())) {
            return "Ogg";
        }
        return null;
    }

    private QueryInfo               query;
    private String                  url;
    private String                  itag;
    private Browser                 br;
    private StreamInfo              streamInfo;
    private HashMap<Object, String> mapping;
    private YoutubeClipData         vid;
    private File                    file;

    public ItagHelper(YoutubeClipData vid, Browser br, QueryInfo query, String url) {
        this.query = query;
        this.url = url;
        this.itag = query.get("itag");
        this.br = br;
        this.vid = vid;
        mapping = new HashMap<Object, String>();
        mapping.put("mp42", "MP4");
        mapping.put("H.264 / AVC / MPEG-4 AVC / MPEG-4 part 10", "H264");
        mapping.put("AAC (Advanced Audio Coding)", "AAC");
        mapping.put("h264", "H264");
        mapping.put("aac", "AAC");
        mapping.put("Google VP9", "VP9");
        mapping.put("Google VP8", "VP9");
        mapping.put("H.263 / H.263-1996, H.263+ / H.263-1998 / H.263 version 2", "H263");

        mapping.put("mp3", "MP3");
        mapping.put("Opus", "Opus");
        mapping.put("MP3 (MPEG audio layer 3)", "MP3");
        mapping.put("flv", "FLV");
        mapping.put("mpeg4", "Mpeg-4 Visual");
        mapping.put("MPEG-4 part 2", "Mpeg-4 Visual");

        mapping.put("3gp6", "3GP");
        mapping.put("FLV (Flash Video)", "FLV");
        mapping.put("FLV / Sorenson Spark / Sorenson H.263 (Flash Video)", "Sorenson H263");
        mapping.put("AMR-NB (Adaptive Multi-Rate NarrowBand)", "AMRNB");
    }

    public Object get(Object name) {
        String ret = mapping.get(name);
        if (ret != null) {
            return ret;
        }
        if (name instanceof Integer) {
            return name;
        }
        return name;
    }

    public void run() throws IOException {
        if (!Application.isJared(null)) {
            loadStreamInfo();

            try {
                System.out.println(JSonStorage.serializeToJson(query) + "\r\n" + JSonStorage.serializeToJson(streamInfo));
                String itagName = "";
                String itagID = itag;
                if (streamInfo.getStreams().size() < 2) {
                    // dash
                    String quality = "";
                    if ("VIDEO".equals(streamInfo.getStreams().get(0).getCodec_type().toUpperCase(Locale.ENGLISH))) {
                        Stream video = streamInfo.getStreams().get(0);
                        String res = getVideoResolution(video);
                        int fps = getFPS(video);
                        itagName = "DASH_VIDEO";

                        itagName += "_ITAG" + itagID;
                        itagName += "_" + get(video.getCodec_long_name());
                        itagName += "_" + getVideoResolution(video);
                        itagName += "P_" + get(fps) + "FPS";
                        // if (streamInfo.getStreams().get(0).getWidth() < streamInfo.getStreams().get(0).getHeight()) {
                        // itagName += "_PIVOT";
                        // }
                        quality = "YoutubeITAG.VIDEO_RESOLUTION_" + getVideoResolution(video) + "P + YoutubeITAG.VIDEO_CODEC_" + upper(get(video.getCodec_long_name()).toString());
                        itagName = upper(itagName);

                        notify(itagName + "(" + itagID + ",\"" + get(video.getCodec_long_name()) + "\",\"" + res + "p\",null,null," + quality + "),");
                        // DASH_WEBM_VIDEO_2160P_VP9(272, "VP9", "2160p", null, null, 2160.3),

                    } else {
                        Stream audio = streamInfo.getStreams().get(0);
                        int audioBitrate = getKBIT(audio);
                        itagName = "DASH_AUDIO";

                        itagName += "_" + get(audio.getCodec_long_name());
                        itagName += "_" + audioBitrate + "KBIT";
                        quality = " YoutubeITAG.AUDIO_CODEC_" + upper(get(audio.getCodec_long_name())) + "_" + get(audioBitrate);

                        itagName = upper(itagName);

                        notify(itagName + "(" + itagID + ",null,null,\"" + get(audio.getCodec_long_name()) + "\",\"" + get(audioBitrate) + "kbit\"," + quality + "),");

                    }

                } else {
                    Stream audio = null;
                    Stream video = null;

                    if (streamInfo.getStreams().get(0).getCodec_type().equalsIgnoreCase("video")) {
                        video = streamInfo.getStreams().get(0);
                        audio = streamInfo.getStreams().get(1);
                    } else {
                        video = streamInfo.getStreams().get(1);
                        audio = streamInfo.getStreams().get(2);
                    }
                    int fps = getFPS(video);
                    int audioBitrate = getKBIT(audio);
                    String res = getVideoResolution(video);
                    itagName = get(getContainerName()) + "";
                    ;
                    itagName += "_ITAG" + itagID;
                    itagName += "_" + get(video.getCodec_long_name());
                    itagName += "_" + getVideoResolution(video);
                    itagName += "P_" + get(fps) + "FPS";
                    // if (video.getWidth() < video.getHeight()) {
                    // itagName += "_PIVOT";
                    // }

                    itagName += "_" + get(audio.getCodec_long_name());
                    itagName += "_" + audioBitrate + "KBIT";
                    // MP4_VIDEO_720P_H264_AUDIO_AAC(22, "H264", "720p", "AAC", "192kbit", 720.4 + YoutubeITAG.AAC_192),
                    itagName = upper(itagName);

                    // YoutubeITAG.VIDEO_RESOLUTION_144P + YoutubeITAG.VIDEO_CODEC_H263 + YoutubeITAG.AUDIO_CODEC_AMRNB_12
                    String quality = "YoutubeITAG.VIDEO_RESOLUTION_" + getVideoResolution(video) + "P + YoutubeITAG.VIDEO_CODEC_" + upper(get(video.getCodec_long_name()).toString()) + " + YoutubeITAG.AUDIO_CODEC_" + upper(get(audio.getCodec_long_name())) + "_" + get(audioBitrate);
                    String not = itagName + "(" + itagID + ",\"" + get(video.getCodec_long_name()) + "\",\"" + res + "p\",\"" + get(audio.getCodec_long_name()) + "\",\"" + get(audioBitrate) + "kbit\",";
                    // VideoResolution.P_240, VideoContainer.FLV, VideoCodec.H263, AudioCodec.MP3, AudioBitrate.KBIT_64
                    not += "VideoResolution.P_" + getVideoResolution(video) + ", ";
                    not += "VideoContainer." + upper(getContainerName()) + ", ";
                    not += "VideoCodec." + upper(get(video.getCodec_long_name()).toString()) + ", ";
                    not += "AudioCodec." + upper(get(audio.getCodec_long_name())) + ", ";
                    not += "AudioBitrate.KBIT_" + audioBitrate;

                    not += "),";
                    notify(not);
                    // Dialog.getInstance().showInputDialog(Dialog.STYLE_LARGE, "New Youtube ITag Found!",
                    // JSonStorage.serializeToJson(query) + "\r\n" + JSonStorage.serializeToJson(streamInfo));

                }

            } catch (Throwable e) {
                e.printStackTrace();
            }
            System.out.println("StreamInfo");
        }
    }

    private static void notify(String string) {
        try {
            Dialog.getInstance().showInputDialog(0, "New Youtube ITAG. add it to " + YoutubeITAG.class, string);
        } catch (DialogClosedException e) {
            e.printStackTrace();
        } catch (DialogCanceledException e) {
            e.printStackTrace();
        }
    }

    public int getKBIT(Stream audio) {
        int bps = -1;
        try {
            bps = (Integer.parseInt(audio.getBit_rate()));
        } catch (Throwable e) {
            bps = (int) ((file.length() / Double.parseDouble(streamInfo.getFormat().getDuration())) * 8);
        }
        return bps / 1000;
    }

    public int getFPS(Stream video) {
        String[] d = new Regex(video.getAvg_frame_rate(), "(\\d+)/(\\d+)").getRow(0);
        return (int) Math.ceil(Integer.parseInt(d[0]) / (double) Integer.parseInt(d[1]));
    }

    public String getVideoResolution(Stream video) {
        if (true) {
            return video.getHeight() + "";
        }
        return Math.min(video.getHeight(), video.getWidth()) + "";

    }

    private String upper(Object string) {
        String str = string.toString();
        if (Character.isDigit(str.charAt(0))) {
            switch (str.charAt(0)) {
            case '0':
                str = "zero" + str.substring(1);
                break;
            case '1':
                str = "one" + str.substring(1);
                break;
            case '2':
                str = "two" + str.substring(1);
                break;
            case '3':
                str = "three" + str.substring(1);
                break;
            case '4':
                str = "fou_" + str.substring(1);
                break;
            case '5':
                str = "five" + str.substring(1);
                break;
            case '6':
                str = "six" + str.substring(1);
                break;
            case '7':
                str = "seven" + str.substring(1);
                break;
            case '8':
                str = "eight" + str.substring(1);
                break;
            case 9:
                str = "nine" + str.substring(1);
                break;
            }
        }
        return str.toUpperCase(Locale.ENGLISH).replaceAll("[\\s\\-]+", "_");
    }

    public String getContainerName() {

        String ret = streamInfo.getFormat().getTags().getMajor_brand();
        if (ret == null) {

            ret = streamInfo.getFormat().getFormat_long_name();
        }

        return get(ret) + "";
    }

    public void loadStreamInfo() throws IOException {
        final FFprobe ffprobe = new FFprobe();
        file = Application.getResource("tmp/ytdev/stream_" + itag + "_" + vid.videoID + ".dat");
        if (!file.exists()) {
            final URLConnectionAdapter con = br.openGetConnection(url);
            FileOutputStream fos = null;
            try {
                file.getParentFile().mkdirs();
                fos = new FileOutputStream(file);
                IO.readStreamToOutputStream(-1, con.getInputStream(), fos, true);
            } finally {
                try {
                    fos.close();
                } catch (final Throwable ignore) {
                }
                try {
                    if (con != null) {
                        con.disconnect();
                    }
                } catch (final Throwable ignore) {
                }
            }
        }
        streamInfo = ffprobe.getStreamInfo(file);
    }
}
