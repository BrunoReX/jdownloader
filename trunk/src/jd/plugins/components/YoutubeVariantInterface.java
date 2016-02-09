package jd.plugins.components;

import java.io.File;
import java.util.List;

import jd.plugins.DownloadLink;
import jd.plugins.PluginForHost;

import org.appwork.storage.config.annotations.LabelInterface;
import org.jdownloader.controlling.linkcrawler.LinkVariant;
import org.jdownloader.gui.translate._GUI;

public interface YoutubeVariantInterface extends LinkVariant {

    public static enum DownloadType {
        DASH_AUDIO,
        DASH_VIDEO,

        VIDEO,
        /**
         * Static videos have a static url in YT_STATIC_URL
         */
        IMAGE,
        SUBTITLES,
        DESCRIPTION,

    }

    public static enum VariantGroup implements LabelInterface {
        AUDIO {

            @Override
            public String getLabel() {
                return _GUI.T.YoutubeVariantInterface_getLabel_audio();
            }
        },
        VIDEO {
            @Override
            public String getLabel() {
                return _GUI.T.YoutubeVariantInterface_getLabel_video();
            }
        },
        VIDEO_3D {
            @Override
            public String getLabel() {
                return _GUI.T.YoutubeVariantInterface_getLabel_video3d();
            }
        },
        IMAGE {
            @Override
            public String getLabel() {
                return _GUI.T.YoutubeVariantInterface_getLabel_image();
            }
        },
        SUBTITLES {
            @Override
            public String getLabel() {
                return _GUI.T.YoutubeVariantInterface_getLabel_subtitles();
            }
        },
        DESCRIPTION {
            @Override
            public String getLabel() {
                return _GUI.T.YoutubeVariantInterface_getLabel_description();
            }
        };
    }

    String getFileExtension();

    String getMediaTypeID();

    YoutubeITAG getiTagVideo();

    YoutubeITAG getiTagAudio();

    YoutubeITAG getiTagData();

    double getQualityRating();

    String getTypeId();

    DownloadType getType();

    VariantGroup getGroup();

    void convert(DownloadLink downloadLink, PluginForHost plugin) throws Exception;

    String getQualityExtension();

    String modifyFileName(String formattedFilename, DownloadLink link);

    boolean hasConverter(DownloadLink downloadLink);

    List<File> listProcessFiles(DownloadLink link);

}
