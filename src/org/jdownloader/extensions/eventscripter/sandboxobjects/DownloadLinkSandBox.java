package org.jdownloader.extensions.eventscripter.sandboxobjects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.WeakHashMap;

import jd.controlling.downloadcontroller.DownloadWatchDog;
import jd.controlling.downloadcontroller.SingleDownloadController;
import jd.plugins.DownloadLink;
import jd.plugins.PluginProgress;

import org.appwork.exceptions.WTFException;
import org.appwork.storage.Storable;
import org.appwork.utils.reflection.Clazz;
import org.jdownloader.api.downloads.v2.DownloadLinkAPIStorableV2;
import org.jdownloader.api.downloads.v2.LinkQueryStorable;
import org.jdownloader.extensions.eventscripter.ScriptAPI;
import org.jdownloader.extensions.extraction.Archive;
import org.jdownloader.extensions.extraction.ExtractionStatus;
import org.jdownloader.extensions.extraction.bindings.downloadlink.DownloadLinkArchiveFactory;
import org.jdownloader.extensions.extraction.contextmenu.downloadlist.ArchiveValidator;
import org.jdownloader.plugins.ConditionalSkipReason;
import org.jdownloader.plugins.DownloadPluginProgress;
import org.jdownloader.plugins.FinalLinkState;
import org.jdownloader.plugins.SkipReason;
import org.jdownloader.plugins.TimeOutCondition;

@ScriptAPI(description = "The context download list link")
public class DownloadLinkSandBox {

    private final DownloadLink                                              downloadLink;
    private final DownloadLinkAPIStorableV2                                 storable;

    private final static WeakHashMap<DownloadLink, HashMap<String, Object>> SESSIONPROPERTIES = new WeakHashMap<DownloadLink, HashMap<String, Object>>();

    public DownloadLinkSandBox(DownloadLink downloadLink) {
        this.downloadLink = downloadLink;
        storable = org.jdownloader.api.downloads.v2.DownloadsAPIV2Impl.toStorable(LinkQueryStorable.FULL, downloadLink, this);
    }

    public DownloadLinkSandBox() {
        downloadLink = null;
        storable = new DownloadLinkAPIStorableV2();
    }

    /**
     * returns how long the downloadlink is in progress
     *
     * @return
     */
    public long getDownloadTime() {
        if (downloadLink != null) {
            long time = downloadLink.getView().getDownloadTime();
            final PluginProgress progress = downloadLink.getPluginProgress();
            if (progress instanceof DownloadPluginProgress) {
                time = time + ((DownloadPluginProgress) progress).getDuration();
            }
            return time;
        }
        return -1;
    }

    public void abort() {
        final List<DownloadLink> abort = new ArrayList<DownloadLink>();
        abort.add(downloadLink);
        DownloadWatchDog.getInstance().abort(abort);
    }

    public Object getProperty(String key) {
        if (downloadLink == null) {
            return null;
        }
        return downloadLink.getProperty(key);
    }

    public Object getSessionProperty(final String key) {
        final DownloadLink link = downloadLink;
        if (link != null) {
            synchronized (SESSIONPROPERTIES) {
                final HashMap<String, Object> properties = SESSIONPROPERTIES.get(link);
                if (properties != null) {
                    return properties.get(key);
                }
            }
        }
        return null;
    }

    public void setSessionProperty(final String key, final Object value) {
        if (value != null) {
            if (!Clazz.isPrimitive(value.getClass()) && !(value instanceof Storable)) {
                throw new WTFException("Type " + value.getClass().getSimpleName() + " is not supported");
            }
        }
        final DownloadLink link = downloadLink;
        if (link != null) {
            synchronized (SESSIONPROPERTIES) {
                HashMap<String, Object> properties = SESSIONPROPERTIES.get(link);
                if (properties == null) {
                    properties = new HashMap<String, Object>();
                    SESSIONPROPERTIES.put(link, properties);
                }
                properties.put(key, value);
            }
        }
    }

    public String getUUID() {
        if (downloadLink == null) {
            return null;
        }
        return downloadLink.getUniqueID().toString();
    }

    public void setProperty(String key, Object value) {
        if (downloadLink == null) {
            return;
        }
        if (value != null) {
            if (!Clazz.isPrimitive(value.getClass()) && !(value instanceof Storable)) {
                throw new WTFException("Type " + value.getClass().getSimpleName() + " is not supported");
            }
        }
        downloadLink.setProperty(key, value);
    }

    public long getDownloadSessionDuration() {
        if (downloadLink != null) {
            final SingleDownloadController controller = downloadLink.getDownloadLinkController();
            if (controller != null) {
                return System.currentTimeMillis() - controller.getStartTimestamp();
            }
        }
        return -1;
    }

    public long getDownloadDuration() {
        if (downloadLink != null) {
            final PluginProgress progress = downloadLink.getPluginProgress();
            if (progress instanceof DownloadPluginProgress) {
                return ((DownloadPluginProgress) progress).getDuration();
            }
        }
        return -1;
    }

    public void reset() {
        if (downloadLink == null) {
            return;
        }
        final ArrayList<DownloadLink> l = new ArrayList<DownloadLink>();
        l.add(downloadLink);
        DownloadWatchDog.getInstance().reset(l);
    }

    public long getEta() {
        if (downloadLink == null) {
            return -1l;
        }
        final PluginProgress progress = downloadLink.getPluginProgress();
        if (progress != null) {
            final long eta = progress.getETA();
            return eta * 1000l;
        }
        final ConditionalSkipReason conditionalSkipReason = downloadLink.getConditionalSkipReason();
        if (conditionalSkipReason != null && !conditionalSkipReason.isConditionReached()) {
            if (conditionalSkipReason instanceof TimeOutCondition) {
                long time = ((TimeOutCondition) conditionalSkipReason).getTimeOutLeft();
                return time * 1000l;
            }
        }
        return -1;
    }

    public ArchiveSandbox getArchive() {
        if (downloadLink == null || ArchiveValidator.EXTENSION == null) {
            return null;
        }
        final Archive archive = ArchiveValidator.EXTENSION.getArchiveByFactory(new DownloadLinkArchiveFactory(downloadLink));
        if (archive != null) {
            return new ArchiveSandbox(archive);
        }
        final ArrayList<Object> list = new ArrayList<Object>();
        list.add(downloadLink);
        final List<Archive> archives = ArchiveValidator.getArchivesFromPackageChildren(list);
        return (archives == null || archives.size() == 0) ? null : new ArchiveSandbox(archives.get(0));
    }

    public String getComment() {
        if (downloadLink == null) {
            return null;
        }
        return downloadLink.getComment();
    }

    public void setEnabled(boolean b) {
        if (downloadLink != null) {
            downloadLink.setEnabled(b);
        }
    }

    public String getDownloadPath() {
        if (downloadLink == null) {
            return "c:/I am a dummy folder/";
        }
        return downloadLink.getFileOutput();
    }

    @ScriptAPI(description = "Sets a new filename", parameters = { "new Name" })
    public void setName(String name) {
        if (downloadLink == null) {
            return;
        }
        DownloadWatchDog.getInstance().renameLink(downloadLink, name);

    }

    public String getUrl() {
        if (downloadLink == null) {
            return null;
        }
        return downloadLink.getView().getDisplayUrl();
    }

    public long getBytesLoaded() {
        if (downloadLink == null) {
            return -1;
        }
        return downloadLink.getView().getBytesLoaded();
    }

    public long getBytesTotal() {
        if (downloadLink == null) {
            return -1;
        }
        return downloadLink.getView().getBytesTotal();
    }

    public String getName() {
        if (downloadLink == null) {
            return "ExampleDownloadLink.rar";
        }
        return downloadLink.getName();
    }

    public FilePackageSandBox getPackage() {
        if (downloadLink == null) {
            return new FilePackageSandBox();
        }
        return new FilePackageSandBox(downloadLink.getParentNode());

    }

    public long getSpeed() {
        return storable.getSpeed();
    }

    public String getStatus() {
        return storable.getStatus();
    }

    public String getHost() {
        return storable.getHost();
    }

    public boolean isSkipped() {
        return storable.isSkipped();
    }

    public String getSkippedReason() {
        if (downloadLink != null) {
            final SkipReason skipped = downloadLink.getSkipReason();
            if (skipped != null) {
                return skipped.name();
            }
        }
        return null;
    }

    public String getFinalLinkStatus() {
        if (downloadLink != null) {
            final FinalLinkState state = downloadLink.getFinalLinkState();
            if (state != null) {
                return state.name();
            }
        }
        return null;
    }

    public void setSkipped(boolean b) {
        if (downloadLink == null) {
            return;
        }
        if (b) {
            if (!downloadLink.isSkipped()) {
                // keep skipreason if a reason is set
                downloadLink.setSkipReason(SkipReason.MANUAL);
            }
        } else {
            final List<DownloadLink> unSkip = new ArrayList<DownloadLink>();
            unSkip.add(downloadLink);
            DownloadWatchDog.getInstance().unSkip(unSkip);
        }
    }

    public boolean isRunning() {
        return storable.isRunning();
    }

    public boolean isEnabled() {
        return storable.isEnabled();
    }

    @Override
    public String toString() {
        return "DownloadLink Instance: " + getName();
    }

    public boolean isFinished() {
        return storable.isFinished();
    }

    public String getExtractionStatus() {
        if (downloadLink == null) {
            return null;
        }
        final ExtractionStatus ret = downloadLink.getExtractionStatus();
        return ret == null ? null : ret.name();
    }

}
