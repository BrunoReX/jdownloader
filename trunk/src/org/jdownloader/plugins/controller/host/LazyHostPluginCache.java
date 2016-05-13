package org.jdownloader.plugins.controller.host;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicLong;

import org.appwork.utils.IO;
import org.appwork.utils.awfc.AWFCUtils;
import org.appwork.utils.net.CountingInputStream;
import org.jdownloader.plugins.controller.LazyPluginClass;
import org.jdownloader.plugins.controller.host.LazyHostPlugin.FEATURE;

public class LazyHostPluginCache {

    private static final long CACHEVERSION = 13052016001l + LazyHostPlugin.FEATURE.CACHEVERSION;

    private static ByteArrayOutputStream readFile(File file) throws IOException {
        final ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream(32767) {
            @Override
            public synchronized byte[] toByteArray() {
                /* avoid creating new byteArray */
                return buf;
            }
        };
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            IO.readStream((int) file.length(), fis, byteBuffer, true);
            return byteBuffer;
        } finally {
            if (fis != null) {
                fis.close();
            }
        }
    }

    public static List<LazyHostPlugin> read(File file, final AtomicLong lastModification) throws IOException {
        final ArrayList<LazyHostPlugin> ret = new ArrayList<LazyHostPlugin>(4096);
        if (file.exists()) {
            final ByteArrayOutputStream byteBuffer = readFile(file);
            final CountingInputStream bis = new CountingInputStream(new ByteArrayInputStream(byteBuffer.toByteArray(), 0, byteBuffer.size()));
            try {
                final AWFCUtils is = new AWFCUtils(bis);
                if (CACHEVERSION != is.readLong()) {
                    throw new IOException("Outdated CacheVersion");
                }
                final long lastModified = is.readLong();
                final int lazyPluginClassSize = is.readShort();
                final byte[] sha256 = new byte[32];
                final byte[] stringBuffer = new byte[32767];
                for (int lazyPluginClassIndex = 0; lazyPluginClassIndex < lazyPluginClassSize; lazyPluginClassIndex++) {
                    final LazyPluginClass lazyPluginClass = new LazyPluginClass(is.readString(stringBuffer), is.ensureRead(32, sha256), is.readLong(), (int) is.readLong(), is.readLong());
                    final int lazyHostPluginSize = is.readShort();
                    for (int lazyHostPluginIndex = 0; lazyHostPluginIndex < lazyHostPluginSize; lazyHostPluginIndex++) {
                        final LazyHostPlugin lazyHostPlugin = new LazyHostPlugin(lazyPluginClass, is.readString(stringBuffer), is.readString(stringBuffer), null, null);
                        lazyHostPlugin.setPluginUsage(is.readLongOptimized());
                        final int flags = is.ensureRead();
                        lazyHostPlugin.setPremium((flags & (1 << 0)) != 0);
                        lazyHostPlugin.setHasConfig((flags & (1 << 1)) != 0);
                        lazyHostPlugin.setSitesSupported((flags & (1 << 3)) != 0);
                        if ((flags & (1 << 5)) != 0) {
                            lazyHostPlugin.setPremiumUrl(is.readString(stringBuffer));
                        }
                        if ((flags & (1 << 6)) != 0) {
                            lazyHostPlugin.setHasRewrite(true);
                        }
                        if ((flags & (1 << 7)) != 0) {
                            lazyHostPlugin.setHasAllowHandle(true);
                        }
                        if ((flags & (1 << 4)) != 0) {
                            lazyHostPlugin.setConfigInterface(is.readString(stringBuffer));
                        }
                        if ((flags & (1 << 2)) != 0) {
                            final ArrayList<FEATURE> features = new ArrayList<FEATURE>(FEATURE.values().length);
                            for (final FEATURE feature : FEATURE.values()) {
                                if (is.readBoolean()) {
                                    features.add(feature);
                                }
                            }
                            lazyHostPlugin.setFeatures(features.toArray(new FEATURE[0]));
                        }
                        ret.add(lazyHostPlugin);
                    }
                }
                if (lastModification != null) {
                    lastModification.set(lastModified);
                }
            } catch (final IOException e) {
                throw new IOException(e.getMessage() + " (" + bis.transferedBytes() + "/" + bis.available() + ")", e);
            }
        }
        return ret;
    }

    public static void write(List<LazyHostPlugin> lazyPlugins, File file, final AtomicLong lastModification) throws IOException {
        final HashMap<LazyPluginClass, List<LazyHostPlugin>> lazyPluginsMap = new HashMap<LazyPluginClass, List<LazyHostPlugin>>();
        if (lazyPlugins != null) {
            for (LazyHostPlugin lazyPlugin : lazyPlugins) {
                List<LazyHostPlugin> lazyPluginClasses = lazyPluginsMap.get(lazyPlugin.getLazyPluginClass());
                if (lazyPluginClasses == null) {
                    lazyPluginClasses = new ArrayList<LazyHostPlugin>();
                    lazyPluginsMap.put(lazyPlugin.getLazyPluginClass(), lazyPluginClasses);
                }
                lazyPluginClasses.add(lazyPlugin);
            }
        }
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            final BufferedOutputStream bos = new BufferedOutputStream(fos, 32767);
            final AWFCUtils os = new AWFCUtils(bos);
            os.writeLong(CACHEVERSION);
            final long lastModified = lastModification != null ? lastModification.get() : 0;
            os.writeLong(lastModified);
            os.writeShort(lazyPluginsMap.size());
            for (final Entry<LazyPluginClass, List<LazyHostPlugin>> lazyPluginMapEntry : lazyPluginsMap.entrySet()) {
                final LazyPluginClass lazyPluginClass = lazyPluginMapEntry.getKey();
                os.writeString(lazyPluginClass.getClassName());
                bos.write(lazyPluginClass.getSha256());
                os.writeLong(lazyPluginClass.getLastModified());
                os.writeLong(Math.max(0, lazyPluginClass.getInterfaceVersion()));
                os.writeLong(Math.max(0, lazyPluginClass.getRevision()));
                /* plugins */
                final List<LazyHostPlugin> plugins = lazyPluginMapEntry.getValue();
                os.writeShort(plugins.size());
                for (final LazyHostPlugin plugin : plugins) {
                    os.writeString(plugin.getPatternSource());
                    os.writeString(plugin.getDisplayName());
                    os.writeLongOptimized(plugin.getPluginUsage());
                    byte flags = 0;
                    if (plugin.isPremium()) {
                        flags |= (1 << 0);
                    }
                    if (plugin.isHasConfig()) {
                        flags |= (1 << 1);
                    }
                    final FEATURE[] features = plugin.getFeatures();
                    if (features != null && features.length > 0) {
                        flags |= (1 << 2);
                    }
                    if (plugin.isSitesSupported()) {
                        flags |= (1 << 3);
                    }
                    if (plugin.isHasConfig() && plugin.getConfigInterface() != null) {
                        flags |= (1 << 4);
                    }
                    if (plugin.isPremium() && plugin.getPremiumUrl() != null) {
                        flags |= (1 << 5);
                    }
                    if (plugin.isHasRewrite()) {
                        flags |= (1 << 6);
                    }
                    if (plugin.isHasAllowHandle()) {
                        flags |= (1 << 7);
                    }
                    bos.write(flags);
                    if (plugin.isPremium() && plugin.getPremiumUrl() != null) {
                        os.writeString(plugin.getPremiumUrl());
                    }
                    if (plugin.isHasConfig() && plugin.getConfigInterface() != null) {
                        os.writeString(plugin.getConfigInterface());
                    }
                    if (features != null && features.length > 0) {
                        for (final FEATURE feature : FEATURE.values()) {
                            os.writeBoolean(feature.isSet(features));
                        }
                    }
                }
            }
            bos.flush();
            bos.close();
            fos.close();
            fos = null;
        } finally {
            if (fos != null) {
                fos.close();
            }
        }
    }
}
