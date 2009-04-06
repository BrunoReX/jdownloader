//    jDownloader - Downloadmanager
//    Copyright (C) 2008  JD-Team support@jdownloader.org
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

package jd.gui.skins.simple.config;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;
import java.util.Locale;

import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import jd.config.ConfigContainer;
import jd.config.ConfigEntry;
import jd.config.ConfigGroup;
import jd.config.Configuration;
import jd.config.SubConfiguration;
import jd.config.ConfigEntry.PropertyType;
import jd.gui.JDLookAndFeelManager;
import jd.gui.skins.simple.GuiRunnable;
import jd.gui.skins.simple.SimpleGUI;
import jd.gui.skins.simple.SimpleGuiConstants;
import jd.gui.skins.simple.components.JLinkButton;
import jd.nutils.OSDetector;
import jd.utils.JDLocale;
import jd.utils.JDSounds;
import jd.utils.JDTheme;
import jd.utils.JDUtilities;
import edu.stanford.ejalbert.BrowserLauncher;
import edu.stanford.ejalbert.exception.BrowserLaunchingInitializingException;
import edu.stanford.ejalbert.exception.UnsupportedOperatingSystemException;

public class ConfigPanelGUI extends ConfigPanel {

    private static final long serialVersionUID = 5474787504978441198L;

    private ConfigEntriesPanel cep;

    private SubConfiguration subConfig;

    public ConfigPanelGUI(Configuration configuration) {
        super();
        subConfig = JDUtilities.getSubConfig(SimpleGuiConstants.GUICONFIGNAME);
        initPanel();

        load();
    }

    @Override
    public void initPanel() {
        ConfigContainer container = new ConfigContainer(this);

        ConfigEntry ce;
        /*LANGUAGE*/
        ConfigGroup langGroup = new ConfigGroup(JDLocale.L("gui.config.gui.language", "Sprache"),JDTheme.II("gui.splash.languages",24,24));

        ConfigContainer look = new ConfigContainer(this, JDLocale.L("gui.config.gui.look.tab", "Anzeige & Bedienung"));
        container.addEntry(new ConfigEntry(ConfigContainer.TYPE_CONTAINER, look));
        look.addEntry(new ConfigEntry(ConfigContainer.TYPE_LABEL, JDLocale.LF("gui.config.gui.languageFileInfo", "Current Language File: %s from %s in version %s", JDUtilities.getSubConfig(JDLocale.CONFIG).getStringProperty(JDLocale.LOCALE_ID, Locale.getDefault().toString()), JDLocale.getTranslater(), JDLocale.getVersion())).setGroup(langGroup));

        look.addEntry(ce = new ConfigEntry(ConfigContainer.TYPE_COMBOBOX, JDUtilities.getSubConfig(JDLocale.CONFIG), JDLocale.LOCALE_ID, JDLocale.getLocaleIDs().toArray(new String[] {}), "").setGroup(langGroup));
        ce.setDefaultValue(Locale.getDefault());
        ce.setPropertyType(PropertyType.NEEDS_RESTART);
/*LOOK*/
        ConfigGroup lookGroup = new ConfigGroup(JDLocale.L("gui.config.gui.view", "Look"),JDTheme.II("gui.images.config.gui",24,24));

        look.addEntry(ce = new ConfigEntry(ConfigContainer.TYPE_COMBOBOX, subConfig, SimpleGuiConstants.PARAM_THEME, JDTheme.getThemeIDs().toArray(new String[] {}), JDLocale.L("gui.config.gui.theme", "Theme")).setGroup(lookGroup));
        ce.setDefaultValue("default");
        ce.setPropertyType(PropertyType.NEEDS_RESTART);
        look.addEntry(ce = new ConfigEntry(ConfigContainer.TYPE_COMBOBOX, subConfig, JDSounds.PARAM_CURRENTTHEME, JDSounds.getSoundIDs().toArray(new String[] {}), JDLocale.L("gui.config.gui.soundTheme", "Soundtheme")).setGroup(lookGroup));
        ce.setDefaultValue("noSounds");
        ce.setPropertyType(PropertyType.NEEDS_RESTART);
        look.addEntry(ce = new ConfigEntry(ConfigContainer.TYPE_COMBOBOX, subConfig, JDLookAndFeelManager.PARAM_PLAF, JDLookAndFeelManager.getInstalledLookAndFeels(), JDLocale.L("gui.config.gui.plaf", "Style(benötigt JD-Neustart)")).setGroup(lookGroup));
        ce.setDefaultValue(JDLookAndFeelManager.getPlaf());
        /*FEEL*/
        ConfigGroup feel = new ConfigGroup(JDLocale.L("gui.config.gui.feel", "Feel"),JDTheme.II("gui.images.configuration",24,24));

        final ConfigEntry plaf = ce;
        look.addEntry(ce = new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, subConfig, SimpleGuiConstants.PARAM_DCLICKPACKAGE, JDLocale.L("gui.config.gui.doubeclick", "Double click to expand/collapse Packages")).setGroup(feel));
        ce.setDefaultValue(false);

        look.addEntry(new ConfigEntry(ConfigContainer.TYPE_SPINNER, subConfig, SimpleGuiConstants.PARAM_INPUTTIMEOUT, JDLocale.L("gui.config.gui.inputtimeout", "Timeout for InputWindows"), 0, 600).setDefaultValue(20).setGroup(feel));
        look.addEntry(ce = new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, subConfig, SimpleGuiConstants.PARAM_SHOW_SPLASH, JDLocale.L("gui.config.gui.showSplash", "Splashscreen beim starten zeigen")).setGroup(feel));
        ce.setDefaultValue(true);

        look.addEntry(ce = new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, subConfig, SimpleGuiConstants.PARAM_DISABLE_CONFIRM_DIALOGS, JDLocale.L("gui.config.gui.disabledialogs", "Bestätigungsdialoge abschalten")).setGroup(feel));
        ce.setDefaultValue(false);
        /*Speedmeter*/
        ConfigGroup speedmeter = new ConfigGroup(JDLocale.L("gui.config.gui.speedmeter", "Speedmeter"),JDTheme.II("gui.images.download",24,24));

        look.addEntry(ce = new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, subConfig, SimpleGuiConstants.PARAM_SHOW_SPEEDMETER, JDLocale.L("gui.config.gui.show_speed_graph", "Display speedmeter graph")).setGroup(speedmeter));
        ce.setDefaultValue(true);
        ce.setPropertyType(PropertyType.NEEDS_RESTART);
        ConfigEntry cond = ce;

        look.addEntry(ce = new ConfigEntry(ConfigContainer.TYPE_SPINNER, subConfig, SimpleGuiConstants.PARAM_SHOW_SPEEDMETER_WINDOWSIZE, JDLocale.L("gui.config.gui.show_speed_graph_window", "Speedmeter Time period (sec)"), 10, 60 * 60 * 12).setGroup(speedmeter));
        ce.setDefaultValue(60);
        ce.setEnabledCondidtion(cond, "==", true);

        // Links Tab
        ConfigContainer links = new ConfigContainer(this, JDLocale.L("gui.config.gui.container.tab", "Downloadlinks"));
        container.addEntry(new ConfigEntry(ConfigContainer.TYPE_CONTAINER, links));

        // links.addEntry(ce = new ConfigEntry(ConfigContainer.TYPE_CHECKBOX,
        // subConfig, LinkGrabber.PROPERTY_ONLINE_CHECK,
        // JDLocale.L("gui.config.gui.linkgrabber.onlinecheck",
        // "Linkgrabber:Linkstatus überprüfen(Verfügbarkeit)")));
        // ce.setDefaultValue(true);

        links.addEntry(ce = new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, JDUtilities.getConfiguration(), Configuration.PARAM_RELOADCONTAINER, JDLocale.L("gui.config.reloadContainer", "Heruntergeladene Container einlesen")));
        ce.setDefaultValue(true);

        links.addEntry(ce = new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, JDUtilities.getSubConfig("GUI"), Configuration.PARAM_SHOW_CONTAINER_ONLOAD_OVERVIEW, JDLocale.L("gui.config.showContainerOnLoadInfo", "Detailierte Containerinformationen beim Öffnen anzeigen")));
        ce.setDefaultValue(false);
        // ce.setInstantHelp(JDLocale.L(
        // "gui.config.showContainerOnLoadInfo.helpurl",
        // "http://jdownloader.org/wiki/index.php?title=Konfiguration_der_Benutzeroberfl%C3%A4che"
        // ));

        // Extended Tab
        ConfigContainer ext = new ConfigContainer(this, JDLocale.L("gui.config.gui.ext", "Advanced"));
        container.addEntry(new ConfigEntry(ConfigContainer.TYPE_CONTAINER, ext));

        ext.addEntry(ce = new ConfigEntry(ConfigContainer.TYPE_SPINNER, subConfig, SimpleGuiConstants.PARAM_NUM_PREMIUM_CONFIG_FIELDS, JDLocale.L("gui.config.gui.premiumconfigfilednum", "How many Premiumaccount fields should be displayed"), 1, 10));
        ce.setDefaultValue(5);
        ce.setPropertyType(PropertyType.NEEDS_RESTART);
        ext.addEntry(ce = new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, subConfig, "FILE_REGISTER", JDLocale.L("gui.config.gui.reg_protocols", "Link ccf/dlc/rsdf to JDownloader")));
        ce.setDefaultValue(true);
        ce.setPropertyType(PropertyType.NEEDS_RESTART);
        if (!OSDetector.isWindows()) ce.setEnabled(false);

        // Browser Tab
        Object[] browserArray = (Object[]) subConfig.getProperty(SimpleGuiConstants.PARAM_BROWSER_VARS, null);
        if (browserArray == null) {
            BrowserLauncher launcher;
            List<?> ar = null;
            try {
                launcher = new BrowserLauncher();
                ar = launcher.getBrowserList();
            } catch (BrowserLaunchingInitializingException e) {
                e.printStackTrace();
            } catch (UnsupportedOperatingSystemException e) {
                e.printStackTrace();
            }
            if (ar == null || ar.size() < 2) {
                browserArray = new Object[] { "JavaBrowser" };
            } else {
                browserArray = new Object[ar.size() + 1];
                for (int i = 0; i < browserArray.length - 1; i++) {
                    browserArray[i] = ar.get(i);
                }
                browserArray[browserArray.length - 1] = "JavaBrowser";
            }
            subConfig.setProperty(SimpleGuiConstants.PARAM_BROWSER_VARS, browserArray);
            subConfig.setProperty(SimpleGuiConstants.PARAM_BROWSER, browserArray[0]);
            subConfig.save();
        }

        ConfigContainer browser = new ConfigContainer(this, JDLocale.L("gui.config.gui.Browser", "Browser"));
        container.addEntry(new ConfigEntry(ConfigContainer.TYPE_CONTAINER, browser));

        browser.addEntry(ce = new ConfigEntry(ConfigContainer.TYPE_BUTTON, new ActionListener() {

            public void actionPerformed(ActionEvent arg0) {
                if (SimpleGUI.CURRENTGUI.showConfirmDialog(JDLocale.L("gui.config.gui.testbrowser.message", "JDownloader now tries to open http://jdownloader.org in your browser."))) {
                    try {
                        save();
                        JLinkButton.openURL("http://jdownloader.org");
                    } catch (Exception e) {
                        e.printStackTrace();
                        SimpleGUI.CURRENTGUI.showMessageDialog(JDLocale.LF("gui.config.gui.testbrowser.error", "Browser launcher failed: %s", e.getLocalizedMessage()));
                    }
                }

            }
        }, JDLocale.L("gui.config.gui.testbrowser", "Test browser")));

        ConfigEntry conditionEntry = new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, subConfig, SimpleGuiConstants.PARAM_CUSTOM_BROWSER_USE, JDLocale.L("gui.config.gui.use_custom_browser", "Use custom browser"));
        conditionEntry.setDefaultValue(false);

        browser.addEntry(ce = new ConfigEntry(ConfigContainer.TYPE_COMBOBOX, subConfig, SimpleGuiConstants.PARAM_BROWSER, browserArray, JDLocale.L("gui.config.gui.Browser", "Browser")));
        ce.setDefaultValue(browserArray[0]);
        ce.setEnabledCondidtion(conditionEntry, "==", false);

        browser.addEntry(conditionEntry);

        browser.addEntry(ce = new ConfigEntry(ConfigContainer.TYPE_TEXTFIELD, subConfig, SimpleGuiConstants.PARAM_CUSTOM_BROWSER, JDLocale.L("gui.config.gui.custom_browser", "Browserpath")));

        String parameter = null;
        String path = null;
        if (OSDetector.isWindows()) {

            if (new File("C:\\Program Files\\Mozilla Firefox\\firefox.exe").exists()) {
                parameter = "-new-tab\r\n%url";
                path = "C:\\Program Files\\Mozilla Firefox\\firefox.exe";
            } else if (new File("C:\\Programme\\Mozilla Firefox\\firefox.exe").exists()) {
                parameter = "-new-tab\r\n%url";
                path = "C:\\Programme\\Mozilla Firefox\\firefox.exe";
            } else if (new File("C:\\Program Files\\Internet Explorer\\iexplore.exe").exists()) {
                parameter = "%url";
                path = "C:\\Program Files\\Internet Explorer\\iexplore.exe";
            } else {
                parameter = "%url";
                path = "C:\\Programme\\Internet Explorer\\iexplore.exe";
            }

        } else if (OSDetector.isMac()) {

            if (new File("/Applications/Firefox.app").exists()) {
                parameter = "/Applications/Firefox.app\r\n-new-tab\r\n%url";
                path = "open";
            } else {
                parameter = "/Applications/Safari.app\r\n-new-tab\r\n%url";
                path = "open";
            }

        } else if (OSDetector.isLinux()) {

            // TODO: das ganze für linux

        }

        ce.setDefaultValue(path);
        ce.setEnabledCondidtion(conditionEntry, "==", true);

        browser.addEntry(ce = new ConfigEntry(ConfigContainer.TYPE_TEXTAREA, subConfig, SimpleGuiConstants.PARAM_CUSTOM_BROWSER_PARAM, JDLocale.L("gui.config.gui.custom_browser_param", "Parameter %url (one parameter per line)")));
        ce.setDefaultValue(parameter);
        ce.setEnabledCondidtion(conditionEntry, "==", true);

        this.add(cep = new ConfigEntriesPanel(container));
        ((JComboBox) ((GUIConfigEntry) plaf.getGuiListener()).getInput()[0]).addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                String plafName = ((JComboBox) e.getSource()).getSelectedItem().toString();
                final Object old = plaf.getPropertyInstance().getProperty(plaf.getPropertyName());

                plaf.getPropertyInstance().setProperty(plaf.getPropertyName(), plafName);
                new Thread() {
                    public void run() {

                        new GuiRunnable<Object>() {

                            public Object runSave() {
                                try {
                                    try {
                                        UIManager.setLookAndFeel(JDLookAndFeelManager.getPlaf().getClassName());
                                    } catch (Exception e1) {
                                        // TODO Auto-generated catch block
                                        e1.printStackTrace();
                                    }
                                    // SimpleGUI.CURRENTGUI.updateDecoration();
                                    SwingUtilities.updateComponentTreeUI(SimpleGUI.CURRENTGUI);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                return null;
                            }
                        }.waitForEDT();
                        plaf.getPropertyInstance().setProperty(plaf.getPropertyName(), old);
                    }
                }.start();
                // 

            }

        });
    }

    @Override
    public void load() {
        loadConfigEntries();
    }

    @Override
    public void save() {
        cep.save();
        subConfig.save();
        updateLAF();

    }

    private void updateLAF() {
        new Thread() {
            public void run() {

                new GuiRunnable<Object>() {

                    public Object runSave() {
                        try {
                            try {
                                UIManager.setLookAndFeel(JDLookAndFeelManager.getPlaf().getClassName());
                                // SimpleGUI.CURRENTGUI.updateDecoration();
                            } catch (Exception e1) {
                                // TODO Auto-generated catch block
                                e1.printStackTrace();
                            }
                            System.out.println("Set LAF " + JDLookAndFeelManager.getPlaf());
                            SwingUtilities.updateComponentTreeUI(SimpleGUI.CURRENTGUI);

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        return null;
                    }
                }.waitForEDT();
            }
        }.start();

    }

    public PropertyType hasChanges() {

        return PropertyType.getMax(super.hasChanges(), cep.hasChanges());
    }

}
