package jd.plugins;

import java.util.HashMap;
import java.util.Map;

import jd.controlling.linkcrawler.LinkCrawler;
import jd.crypt.JDCrypt;
import jd.plugins.DownloadLink.AvailableStatus;

import org.appwork.storage.JSonStorage;
import org.appwork.storage.Storable;
import org.appwork.storage.TypeRef;
import org.appwork.utils.StringUtils;
import org.appwork.utils.encoding.Base64;
import org.jdownloader.controlling.UrlProtection;
import org.jdownloader.plugins.FinalLinkState;

public class DownloadLinkStorable implements Storable {

    private static final byte[]                       KEY      = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 };
    private static final String                       CRYPTED  = "CRYPTED:";
    public static final TypeRef<DownloadLinkStorable> TYPE_REF = new TypeRef<DownloadLinkStorable>() {
        public java.lang.reflect.Type getType() {
            return DownloadLinkStorable.class;
        };
    };
    private DownloadLink                              link;

    public AvailableStatus getAvailablestatus() {
        return link.getAvailableStatus();
    }

    public void setAvailablestatus(AvailableStatus availablestatus) {
        if (availablestatus != null) {
            link.setAvailableStatus(availablestatus);
        }
    }

    @SuppressWarnings("unused")
    private DownloadLinkStorable(/* Storable */) {
        this.link = new DownloadLink(null, null, null, null, false);
    }

    public DownloadLinkStorable(DownloadLink link) {
        this.link = link;
    }

    public long getUID() {
        return link.getUniqueID().getID();
    }

    /**
     * @since JD2
     */
    public void setUID(long id) {
        if (id != -1) {
            link.getUniqueID().setID(id);
        }
    }

    public String getName() {
        return link.getName();
    }

    public void setName(String name) {
        this.link.setNameUnsafe(name);
    }

    public Map<String, Object> getProperties() {
        if (crypt()) {
            return null;
        }
        final Map<String, Object> ret = link.getProperties();
        if (ret == null || ret.isEmpty()) {
            return null;
        }
        return ret;
    }

    public void setProperties(Map<String, Object> props) {
        if (props == null || props.isEmpty()) {
            return;
        }
        if (props instanceof HashMap) {
            link.setPropertiesUnsafe((HashMap<String, Object>) props);
        } else {
            this.link.setProperties(props);
        }
    }

    /**
     * keep for compatibility
     *
     * @return
     */
    @Deprecated
    public HashMap<String, String> getLinkStatus() {
        return null;
    }

    public void setFinalLinkState(String state) {
        if (state != null) {
            try {
                link.setFinalLinkStateUnsafe(FinalLinkState.valueOf(state));
            } catch (final Throwable e) {
                e.printStackTrace();
            }
        }
    }

    public String getFinalLinkState() {
        final FinalLinkState state = link.getFinalLinkState();
        if (state != null) {
            return state.name();
        }
        return null;
    }

    public void setLinkStatus(HashMap<String, String> status) {
        if (status != null) {
            try {
                final int linkStatus = Integer.parseInt(status.get("status"));
                if (linkStatus == LinkStatus.FINISHED || hasStatus(linkStatus, LinkStatus.FINISHED)) {
                    link.setFinalLinkState(FinalLinkState.FINISHED);
                } else if (linkStatus == LinkStatus.ERROR_FILE_NOT_FOUND || hasStatus(linkStatus, LinkStatus.ERROR_FILE_NOT_FOUND)) {
                    link.setFinalLinkState(FinalLinkState.OFFLINE);
                } else if (linkStatus == LinkStatus.ERROR_FATAL || hasStatus(linkStatus, LinkStatus.ERROR_FATAL)) {
                    link.setFinalLinkState(FinalLinkState.FAILED_FATAL);
                }
            } catch (final Throwable e) {
                org.appwork.utils.logging2.extmanager.LoggerFactory.getDefaultLogger().log(e);
            }
        }
    }

    private boolean hasStatus(final int is, final int expected) {
        return (is & expected) != 0;
    }

    public long getSize() {
        return link.getView().getBytesTotal();
    }

    public void setSize(long size) {
        link.setDownloadSize(size);
    }

    public long getCurrent() {
        return link.getView().getBytesLoaded();
    }

    public void setCurrent(long current) {
        link.setDownloadCurrent(current);
    }

    public String getURL() {
        final String url = link.getPluginPatternMatcher();
        if (StringUtils.isNotEmpty(url) && crypt()) {
            final byte[] crypted = JDCrypt.encrypt(url, KEY);
            return CRYPTED + Base64.encodeToString(crypted, false);
        } else {
            return url;
        }
    }

    public void setURL(String url) {
        if (StringUtils.isEmpty(url)) {
            link.setPluginPatternMatcherUnsafe(null);
        } else if (url.startsWith(CRYPTED)) {
            final byte[] bytes = Base64.decodeFast(url.substring(CRYPTED.length()));
            final String url2 = JDCrypt.decrypt(bytes, KEY);
            link.setPluginPatternMatcherUnsafe(url2);
        } else {
            link.setPluginPatternMatcherUnsafe(url);
        }
    }

    public String getHost() {
        return link.getHost();
    }

    public void setHost(String host) {
        link.setHost(host);
    }

    public void setBrowserURL(String url) {
        if (!StringUtils.isEmpty(url)) {
            /**
             * for conversion from own key to property system
             */
            link.setOriginUrl(url);
        }
    }

    public long[] getChunkProgress() {
        return link.getChunksProgress();
    }

    public void setChunkProgress(long[] p) {
        link.setChunksProgress(p);
    }

    public String getUrlProtection() {
        try {
            return link.getUrlProtection().name();
        } catch (Throwable e) {
            return UrlProtection.UNSET.name();
        }

    }

    public void setUrlProtection(String type) {
        try {
            link.setUrlProtection(UrlProtection.valueOf(type));
        } catch (Throwable e) {
            link.setUrlProtection(UrlProtection.UNSET);
        }
    }

    public boolean isEnabled() {
        return link.isEnabled();
    }

    public void setEnabled(boolean b) {
        link.setEnabled(b);
    }

    public long getCreated() {
        return link.getCreated();
    }

    public void setCreated(long time) {
        link.setCreated(time);
    }

    /* Do Not Serialize */
    public DownloadLink _getDownloadLink() {
        final DownloadLink lLink = link;
        if (lLink != null) {
            lLink.setContainerUrl(DownloadLink.deDuplicateString(LinkCrawler.cleanURL(lLink.getContainerUrl())));
            lLink.setReferrerUrl(DownloadLink.deDuplicateString(LinkCrawler.cleanURL(lLink.getReferrerUrl())));
            lLink.setOriginUrl(DownloadLink.deDuplicateString(LinkCrawler.cleanURL(lLink.getOriginUrl())));
            lLink.setContentUrl(DownloadLink.deDuplicateString(LinkCrawler.cleanURL(lLink.getContentUrl())));
        }
        return lLink;
    }

    private boolean crypt() {
        switch (link.getUrlProtection()) {
        case PROTECTED_CONTAINER:
        case PROTECTED_DECRYPTER:
            return true;
        default:
            return false;
        }
    }

    /**
     * @return the propertiesString
     */
    public String getPropertiesString() {
        if (crypt()) {
            final Map<String, Object> properties = link.getProperties();
            if (properties == null || properties.isEmpty()) {
                return null;
            }
            final byte[] crypted = JDCrypt.encrypt(JSonStorage.serializeToJson(properties), KEY);
            return CRYPTED + Base64.encodeToString(crypted, false);
        }
        return null;
    }

    /**
     * @param propertiesString
     *            the propertiesString to set
     */
    public void setPropertiesString(String propertiesString) {
        if (propertiesString != null && propertiesString.startsWith(CRYPTED)) {
            final byte[] bytes = Base64.decodeFast(propertiesString.substring(CRYPTED.length()));
            final Map<String, Object> properties = JSonStorage.restoreFromString(JDCrypt.decrypt(bytes, KEY), new TypeRef<org.jdownloader.myjdownloader.client.json.JsonMap>() {
            });
            setProperties(properties);
        }
    }
}
