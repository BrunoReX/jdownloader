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

import java.awt.Graphics;
import java.util.ArrayList;
import java.util.Vector;
import java.util.logging.Logger;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;

import jd.config.ConfigContainer;
import jd.config.ConfigEntry;
import jd.config.ConfigGroup;
import jd.config.SubConfiguration;
import jd.config.ConfigEntry.PropertyType;
import jd.gui.skins.simple.Factory;
import jd.gui.skins.simple.JTabbedPanel;
import jd.gui.skins.simple.SimpleGUI;
import net.miginfocom.swing.MigLayout;

public abstract class ConfigPanel extends JTabbedPanel {

    private static final long serialVersionUID = 3383448498625377495L;

    protected Vector<GUIConfigEntry> entries = new Vector<GUIConfigEntry>();

    protected Logger logger = jd.controlling.JDLogger.getLogger();
    private boolean tabbed = false;
    protected JPanel panel;

    private ConfigGroup currentGroup;

    protected boolean viewport = true;

    private JMenuBar menuBar;

    private int latestGroupID;

    public ConfigPanel() {
        this.setLayout(new MigLayout("ins 0 0 0 0", "[fill,grow]", "[fill,grow]"));
        panel = new JPanel();
        panel.setLayout(new MigLayout("ins 0 10 10 10,wrap 2", "[fill,grow 10]10[fill,grow]"));
    }

    @Override
    public boolean needsViewport() {
        return viewport;
    }

    public void setTabbed(boolean b) {
        tabbed = b;

    }

    @Override
    public void paint(Graphics g) {

        super.paint(g);
        if (SimpleGUI.CURRENTGUI != null) SimpleGUI.CURRENTGUI.setWaiting(false);
    }

    public void addGUIConfigEntry(GUIConfigEntry entry, JPanel panel) {

        // JDUtilities.addToGridBag(panel, entry, GridBagConstraints.RELATIVE,
        // GridBagConstraints.RELATIVE, GridBagConstraints.REMAINDER, 1, 1, 0,
        // insets, GridBagConstraints.HORIZONTAL, GridBagConstraints.NORTH);
        ConfigGroup group = entry.getConfigEntry().getGroup();

        if (group == null) {
            if (currentGroup != null) {
                panel.add(new JSeparator(), "spanx,gapbottom 15,gaptop 15");
            }
            if (entry.getDecoration() != null) {
                if ((entry.getConfigEntry().getType() == ConfigContainer.TYPE_TEXTAREA) || (entry.getConfigEntry().getType() == ConfigContainer.TYPE_LISTCONTROLLED)) {
                    panel.add(entry.getDecoration(), "spany " + entry.getInput().length + ",spanx, gapright " + getGapRight());

                } else {
                    panel.add(entry.getDecoration(), "spany " + entry.getInput().length + (entry.getInput().length == 0 ? ",spanx" : ""));
                }
            }

            for (JComponent c : entry.getInput()) {
                switch (entry.getConfigEntry().getType()) {
                case ConfigContainer.TYPE_TEXTAREA:
                case ConfigContainer.TYPE_LISTCONTROLLED:
                    panel.add(new JScrollPane(c), "spanx,gapright " + getGapRight() + ",growy,pushy");
                    // panel.add(new JScrollPane(c),
                    // "spanx,gapright 20,growy,pushy");
                    viewport = false;
                    break;
                case ConfigContainer.TYPE_PREMIUMPANEL:
                    this.setLayout(new MigLayout("ins 0", "[fill,grow]", "[fill,grow]"));
                    panel.setLayout(new MigLayout("ins 0,wrap 2", "[fill,grow 10]10[fill,grow]"));

                    // JScrollPane sp;
                    panel.add(c, "spanx,growy,pushy");
                    // panel.add(sp=new JScrollPane(c), "spanx,growy,pushy");
                    // sp.setBorder(null);
                    break;
                default:
                    panel.add(c, entry.getDecoration() == null ? "spanx,gapright " + getGapRight() : "gapright " + getGapRight());
                    break;
                }

            }
            entries.add(entry);
            currentGroup = null;
            return;
        } else {

            if (currentGroup != group) {

                panel.add(Factory.createHeader(group), "spanx");
              latestGroupID=  panel.getComponentCount();
                currentGroup = group;
            }
            if (entry.getDecoration() != null) {
                if (entry.getConfigEntry().getType() == ConfigContainer.TYPE_TEXTAREA) {
                    panel.add(entry.getDecoration(), "gapleft " + getGapLeft() + ",spany " + entry.getInput().length + ",spanx");

                } else {
                    panel.add(entry.getDecoration(), "gapleft " + getGapLeft() + ",spany " + entry.getInput().length + (entry.getInput().length == 0 ? ",spanx" : ""));
                }
            }

            for (JComponent c : entry.getInput()) {

                switch (entry.getConfigEntry().getType()) {
                case ConfigContainer.TYPE_BUTTON:
                    if (menuBar == null) {
                        menuBar = new JMenuBar();
                        panel.add(menuBar, "cell 0 "+latestGroupID+",spanx,growx,pushx");
                    }
                    JButton bt = (JButton) entry.getInput()[0];
                    JMenuItem mnuFile = new JMenuItem(bt.getName());
                    menuBar.add(mnuFile);
                    // panel.add(new JScrollPane(c),
                    // "spanx,gapleft 35,gapright 20");
                    // panel.add(new JScrollPane(c), "spanx,gapright " +
                    // getGapRight() + ",growy,pushy,gapleft " + getGapLeft());

                    break;

                case ConfigContainer.TYPE_TEXTAREA:
                    viewport = false;
                    // panel.add(new JScrollPane(c),
                    // "spanx,gapleft 35,gapright 20");
                    panel.add(new JScrollPane(c), "spanx,gapright " + getGapRight() + ",growy,pushy,gapleft " + getGapLeft());

                    break;
                case ConfigContainer.TYPE_PREMIUMPANEL:

                    this.setLayout(new MigLayout("ins 0", "[fill,grow]", "[fill,grow]"));
                    panel.setLayout(new MigLayout("ins 0,wrap 2", "[fill,grow 10]10[fill,grow]"));
                    JScrollPane sp;
                    panel.add(sp = new JScrollPane(c), "spanx");
                    sp.setBorder(null);
                    break;
                default:
                    panel.add(c, entry.getDecoration() == null ? "spanx,gapright " + this.getGapRight() + ",gapleft " + this.getGapLeft() : "gapright " + this.getGapRight());
                    break;
                }

            }
        }
        entries.add(entry);

    }

    private String getGapLeft() {
        // TODO Auto-generated method stub
        return tabbed ? "35" : "35";
    }

    private String getGapRight() {
        return tabbed ? "16" : "20";
    }

    public void addGUIConfigEntry(GUIConfigEntry entry) {
        addGUIConfigEntry(entry, panel);
    }

    public abstract void initPanel();

    public abstract void load();

    public void loadConfigEntries() {
        for (GUIConfigEntry akt : entries) {
            if (akt.getConfigEntry().getPropertyInstance() != null && akt.getConfigEntry().getPropertyName() != null) {
                akt.setData(akt.getConfigEntry().getPropertyInstance().getProperty(akt.getConfigEntry().getPropertyName()));
            }
        }
    }

    public abstract void save();

    @Override
    public void onDisplay() {
        System.out.println("Display " + this);
        loadConfigEntries();
    }

    @Override
    public void onHide() {
        this.save();
    }

    public ConfigEntry.PropertyType hasChanges() {
        PropertyType ret = ConfigEntry.PropertyType.NONE;
        Object old;
        synchronized (entries) {
            for (GUIConfigEntry akt : entries) {
                if (akt.getConfigEntry().getPropertyInstance() != null && akt.getConfigEntry().getPropertyName() != null) {
                    if (akt.getConfigEntry().hasChanges()) {
                        ret = ret.getMax(PropertyType.NORMAL);
                    }
                    old = akt.getConfigEntry().getPropertyInstance().getProperty(akt.getConfigEntry().getPropertyName());
                    if (old == null && akt.getText() != null) {
                        ret = ret.getMax(akt.getConfigEntry().getPropertyType());
                        System.out.println(akt.getConfigEntry().getPropertyName() + "1: " + ret);
                        continue;
                    }
                    if (old == akt.getText()) {
                        System.out.println(akt.getConfigEntry().getPropertyName() + "2: " + ret);
                        continue;
                    }
                    if (!old.equals(akt.getText())) {
                        ret = ret.getMax(akt.getConfigEntry().getPropertyType());

                        System.out.println(akt.getConfigEntry().getPropertyName() + "3: " + ret);
                        continue;
                    }
                }

            }
        }
        return ret;
    }

    public void saveConfigEntries() {
        ArrayList<SubConfiguration> subs = new ArrayList<SubConfiguration>();
        for (GUIConfigEntry akt : entries) {
            if (akt.getConfigEntry().getPropertyInstance() instanceof SubConfiguration && subs.indexOf(akt.getConfigEntry().getPropertyInstance()) < 0) {
                subs.add((SubConfiguration) akt.getConfigEntry().getPropertyInstance());
            }
            if (akt.getConfigEntry().getPropertyInstance() != null && akt.getConfigEntry().getPropertyName() != null) {
                akt.getConfigEntry().getPropertyInstance().setProperty(akt.getConfigEntry().getPropertyName(), akt.getText());
            }
        }

        for (SubConfiguration subConfiguration : subs) {
            subConfiguration.save();
        }
    }

}
