package org.jdownloader.plugins.controller.container;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import jd.plugins.PluginsC;

import org.appwork.utils.Regex;

public class ContainerPluginController {

    private static final ContainerPluginController INSTANCE = new ContainerPluginController();

    public static ContainerPluginController getInstance() {
        return ContainerPluginController.INSTANCE;
    }

    private List<PluginsC> list;

    private ContainerPluginController() {
        list = null;
    }

    public void init() {
        final List<PluginsC> plugins = new ArrayList<PluginsC>();
        try {
            plugins.add(new org.jdownloader.container.JD1Import());
        } catch (final Throwable e) {
            org.appwork.utils.logging2.extmanager.LoggerFactory.getDefaultLogger().log(e);
        }
        try {
            plugins.add(new org.jdownloader.container.NZB());
        } catch (final Throwable e) {
            org.appwork.utils.logging2.extmanager.LoggerFactory.getDefaultLogger().log(e);
        }
        try {
            plugins.add(new org.jdownloader.container.AMZ());
        } catch (final Throwable e) {
            org.appwork.utils.logging2.extmanager.LoggerFactory.getDefaultLogger().log(e);
        }
        try {
            plugins.add(new org.jdownloader.container.JD2Import());
        } catch (final Throwable e) {
            org.appwork.utils.logging2.extmanager.LoggerFactory.getDefaultLogger().log(e);
        }
        try {
            plugins.add(new org.jdownloader.container.C());
        } catch (final Throwable e) {
            org.appwork.utils.logging2.extmanager.LoggerFactory.getDefaultLogger().log(e);
        }
        try {
            plugins.add(new org.jdownloader.container.D());
        } catch (final Throwable e) {
            org.appwork.utils.logging2.extmanager.LoggerFactory.getDefaultLogger().log(e);
        }
        try {
            plugins.add(new org.jdownloader.container.MetaLink());
        } catch (final Throwable e) {
            org.appwork.utils.logging2.extmanager.LoggerFactory.getDefaultLogger().log(e);
        }
        try {
            plugins.add(new org.jdownloader.container.R());
        } catch (final Throwable e) {
            org.appwork.utils.logging2.extmanager.LoggerFactory.getDefaultLogger().log(e);
        }
        try {
            plugins.add(new org.jdownloader.container.SFT());
        } catch (final Throwable e) {
            org.appwork.utils.logging2.extmanager.LoggerFactory.getDefaultLogger().log(e);
        }
        list = Collections.unmodifiableList(plugins);
    }

    public List<PluginsC> list() {
        lazyInit();
        return list;
    }

    public void setList(List<PluginsC> list) {
        if (list == null) {
            return;
        }
        this.list = list;
    }

    private void lazyInit() {
        if (list != null) {
            return;
        }
        synchronized (this) {
            if (list != null) {
                return;
            }
            init();
        }
    }

    public PluginsC get(String displayName) {
        lazyInit();
        for (PluginsC p : list) {
            if (p.getName().equalsIgnoreCase(displayName)) {
                return p;
            }
        }
        return null;
    }

    public Pattern getContainerExtensions(final String filter) {
        lazyInit();
        final StringBuilder sb = new StringBuilder(".*(");
        for (final PluginsC act : list) {
            if (filter != null && !new Regex(act.getName(), filter).matches()) {
                continue;
            }
            final String regex = new Regex(act.getSupportedLinks().pattern(), "file:/\\.\\+(.+?)\\$").getMatch(0);
            if (regex != null) {
                if (sb.length() > 3) {
                    sb.append("|");
                }
                sb.append(regex);
            }
        }
        sb.append(")$");
        return Pattern.compile(sb.toString());
    }
}