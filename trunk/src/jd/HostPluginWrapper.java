package jd;

import jd.plugins.PluginForHost;

public class HostPluginWrapper extends PluginWrapper {

    private static final String AGB_CHECKED = null;

    public HostPluginWrapper(String name, String host, String className, String patternSupported, int flags) {
        super(name, host, "jd.plugins.host." + className, patternSupported, flags);
    }

    public HostPluginWrapper(String host, String className, String patternSupported, int flags) {
        this(host, host, className, patternSupported, flags);
    }

    public HostPluginWrapper(String host, String className, String patternSupported) {
        this(host, host, className, patternSupported, 0);
    }

    public PluginForHost getPlugin() {
        return (PluginForHost) super.getPlugin();
    }

    public boolean isAGBChecked() {
        return getPluginConfig().getBooleanProperty(AGB_CHECKED, false);
    }

    public void setAGBChecked(Boolean value) {
        getPluginConfig().setProperty(AGB_CHECKED, value);
        getPluginConfig().save();
    }

}
