package org.jdownloader.gui.views.components.packagetable;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import javax.swing.JRootPane;
import javax.swing.border.Border;
import javax.swing.plaf.ButtonUI;
import javax.swing.plaf.synth.SynthButtonUI;
import javax.swing.plaf.synth.SynthContext;
import javax.swing.plaf.synth.SynthLookAndFeel;
import javax.swing.plaf.synth.SynthPainter;

import jd.controlling.packagecontroller.AbstractPackageChildrenNode;
import jd.controlling.packagecontroller.AbstractPackageNode;

import org.appwork.scheduler.DelayedRunnable;
import org.appwork.storage.config.JsonConfig;
import org.appwork.swing.components.ExtTextField;
import org.appwork.utils.Application;
import org.appwork.utils.NullsafeAtomicReference;
import org.jdownloader.actions.AppAction;
import org.jdownloader.controlling.filter.LinkgrabberFilterRuleWrapper;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.views.components.SearchCatInterface;
import org.jdownloader.images.NewTheme;
import org.jdownloader.settings.GeneralSettings;
import org.jdownloader.updatev2.gui.LAFOptions;

public class SearchField<SearchCat extends SearchCatInterface, PackageType extends AbstractPackageNode<ChildType, PackageType>, ChildType extends AbstractPackageChildrenNode<PackageType>> extends ExtTextField implements MouseMotionListener, MouseListener {
    /**
     *
     */
    private static final long                                                                  serialVersionUID        = -8079363840549073686L;
    private static final int                                                                   SIZE                    = 20;
    private Image                                                                              img;
    private DelayedRunnable                                                                    delayedFilter;
    private PackageControllerTable<PackageType, ChildType>                                     table2Filter;

    private volatile JLabel                                                                    label;
    private int                                                                                labelWidth;
    private Color                                                                              bgColor;
    private volatile SearchCat[]                                                               searchCategories;
    private Image                                                                              popIcon;
    private int                                                                                iconGap                 = 38;
    private Border                                                                             orgBorder;
    private Image                                                                              close;

    private int                                                                                closeXPos               = -1;
    private boolean                                                                            mouseoverClose          = false;
    private volatile boolean                                                                   closeEnabled            = false;
    private NullsafeAtomicReference<PackageControllerTableModelFilter<PackageType, ChildType>> appliedFilter           = new NullsafeAtomicReference<PackageControllerTableModelFilter<PackageType, ChildType>>(null);
    private AppAction                                                                          focusAction;
    private JButton                                                                            button;
    private boolean                                                                            synthButtonUIAccessable = Application.getJavaVersion() >= Application.JAVA17;

    public boolean isEmpty() {
        return appliedFilter.get() == null;
    }

    public SearchField(final PackageControllerTable<PackageType, ChildType> table2Filter, SearchCat defCategory) {
        super();
        button = new JButton();
        this.table2Filter = table2Filter;
        img = NewTheme.I().getImage(IconKey.ICON_SEARCH, SIZE);
        close = NewTheme.I().getImage(IconKey.ICON_CLOSE, -1);

        LAFOptions lafo = LAFOptions.getInstance();
        bgColor = (lafo.getColorForPanelHeaderBackground());

        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        popIcon = NewTheme.I().getImage(IconKey.ICON_POPUPSMALL, -1);
        delayedFilter = new DelayedRunnable(150l, 2000l) {

            @Override
            public String getID() {
                return "SearchField" + table2Filter.getModel().getModelID();
            }

            @Override
            public void delayedrun() {
                updateFilter();
            }

        };
        orgBorder = getBorder();
        setBorder(BorderFactory.createCompoundBorder(orgBorder, BorderFactory.createEmptyBorder(0, 28, 0, 18)));
        addMouseMotionListener(this);
        addMouseListener(this);
        // setSelectedCategory(defCategory);,
        focusAction = new AppAction() {

            @Override
            public void actionPerformed(ActionEvent e) {
                requestFocus();
                selectAll();
            }
        };
    }

    public AppAction getFocusAction() {
        return focusAction;
    }

    public Image getPopIcon() {
        return popIcon;
    }

    public void setPopIcon(Image popIcon) {
        this.popIcon = popIcon;
    }

    @Override
    public void onChanged() {
        final String text = getText();
        closeEnabled = text != null && text.length() > 0;
        delayedFilter.run();
    }

    protected final boolean isSynthButtonUIAvailable(ButtonUI buttonUI) {
        if (synthButtonUIAccessable) {
            return buttonUI instanceof SynthButtonUI;
        } else {
            return false;
        }
    }

    private Method synthLookAndFeelUpdateMethod = null;

    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        Composite comp = g2.getComposite();
        super.paintComponent(g);
        if (label != null) {
            if (isSynthButtonUIAvailable(button.getUI())) {
                try {
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
                    button.setOpaque(false);
                    button.setBackground(null);
                    button.setSize(labelWidth + 5 + iconGap + 8 - 1, getHeight());
                    SynthContext context = ((SynthButtonUI) button.getUI()).getContext(button);
                    if (synthLookAndFeelUpdateMethod == null) {
                        synthLookAndFeelUpdateMethod = SynthLookAndFeel.class.getDeclaredMethod("update", new Class[] { SynthContext.class, Graphics.class });
                        synthLookAndFeelUpdateMethod.setAccessible(true);
                    }
                    synthLookAndFeelUpdateMethod.invoke(null, new Object[] { context, g2 });
                    SynthPainter painter = context.getStyle().getPainter(context);
                    g2.setClip(0, 0, labelWidth + 5 + iconGap + 8 + 1, getHeight());
                    painter.paintButtonBackground(context, g2, 0, 0, labelWidth + 5 + iconGap + 8 - 1 + 4, getHeight());
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
                    g2.setClip(null);
                    g2.setColor(getBackground().darker().darker());
                    g2.drawLine(labelWidth + 5 + iconGap + 8, 1, labelWidth + iconGap + 5 + 8, getHeight() - 2);
                } catch (Throwable e) {
                    e.printStackTrace();
                    synthButtonUIAccessable = false;
                } finally {
                    g2.setComposite(comp);
                }
            } else {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
                g2.setColor(bgColor);
                g2.fillRect(1, 1, labelWidth + 5 + iconGap + 8 - 1, getHeight() - 1);
                g2.setColor(getBackground().darker());
                g2.drawLine(labelWidth + 5 + iconGap + 8, 1, labelWidth + iconGap + 5 + 8, getHeight() - 1);
                g2.setComposite(comp);
            }
            final SearchCat cat = getSelectedCategory();
            if (cat != null) {
                cat.getIcon().paintIcon(this, g2, iconGap - 24, 3);
            }
            g2.translate(iconGap + 1, 0);
            label.getUI().paint(g2, label);
            g2.drawImage(popIcon, labelWidth + 3, (getHeight() - popIcon.getHeight(null)) / 2, null);
            // label.paintComponents(g2);
            g2.translate(-iconGap - 1, 0);
        } else {
            if (isSynthButtonUIAvailable(button.getUI())) {
                try {
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
                    button.setOpaque(false);
                    button.setBackground(null);
                    button.setSize(labelWidth + 5 + iconGap + 8 - 1, getHeight());
                    SynthContext context = ((SynthButtonUI) button.getUI()).getContext(button);
                    if (synthLookAndFeelUpdateMethod == null) {
                        synthLookAndFeelUpdateMethod = SynthLookAndFeel.class.getDeclaredMethod("update", new Class[] { SynthContext.class, Graphics.class });
                        synthLookAndFeelUpdateMethod.setAccessible(true);
                    }
                    synthLookAndFeelUpdateMethod.invoke(null, new Object[] { context, g2 });
                    SynthPainter painter = context.getStyle().getPainter(context);
                    g2.setClip(0, 0, 26, getHeight());
                    painter.paintButtonBackground(context, g2, 0, 0, 26 + 3, getHeight());
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
                    g2.setClip(null);
                    g2.setColor(getBackground().darker().darker());
                    g2.drawLine(26, 1, 26, getHeight() - 1);
                } catch (Throwable e) {
                    e.printStackTrace();
                    synthButtonUIAccessable = false;
                } finally {
                    g2.setComposite(comp);
                }
            } else {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
                g2.setColor(bgColor);
                g2.fillRect(0, 0, 26, getHeight());
                g2.setColor(getBackground().darker());
                g2.drawLine(26, 1, 26, getHeight() - 1);
                g2.setComposite(comp);
            }
            g2.drawImage(img, 3, 3, 3 + SIZE, 3 + SIZE, 0, 0, SIZE, SIZE, null);
        }
        if (closeEnabled) {
            closeXPos = getWidth() - close.getWidth(null) - (getHeight() - close.getHeight(null)) / 2;
            g2.drawImage(close, closeXPos, (getHeight() - close.getHeight(null)) / 2, close.getWidth(null), close.getHeight(null), null);
        }
        // g2.dispose();
    }

    protected final boolean isFullMatchPattern(Pattern pattern) {
        final String stringPattern = pattern.pattern();
        return stringPattern.charAt(0) == '^' && stringPattern.charAt(stringPattern.length() - 1) == '$';
    }

    protected final boolean isMatching(List<Pattern> pattern, String string) {
        if (string != null) {
            for (final Pattern filterPattern : pattern) {
                // if (isFullMatchPattern(filterPattern)) {
                // if (filterPattern.matcher(string).matches()) {
                // return true;
                // }
                // } else
                if (filterPattern.matcher(string).find()) {
                    return true;
                }
            }
        }
        return false;
    }

    private void updateFilter() {
        final String filterRegex = this.getText();
        final boolean enabled = filterRegex != null && filterRegex.length() > 0;
        PackageControllerTableModelFilter<PackageType, ChildType> newFilter = null;
        if (enabled) {
            final List<Pattern> list = new ArrayList<Pattern>();
            try {
                if (JsonConfig.create(GeneralSettings.class).isFilterRegex()) {
                    list.add(LinkgrabberFilterRuleWrapper.createPattern(filterRegex, true));
                } else {
                    String[] filters = filterRegex.split("\\|");
                    for (String filter : filters) {
                        list.add(LinkgrabberFilterRuleWrapper.createPattern(filter, false));
                    }
                }
                newFilter = getFilter(list, getSelectedCategory());
            } catch (final Throwable e) {
                e.printStackTrace();
            }
        }
        final PackageControllerTableModelFilter<PackageType, ChildType> oldFilter = appliedFilter.getAndSet(newFilter);
        if (oldFilter != null) {
            table2Filter.getModel().removeFilter(oldFilter);
        }
        if (newFilter != null) {
            table2Filter.getModel().addFilter(newFilter);
        }
        table2Filter.getModel().recreateModel(true);
    }

    protected PackageControllerTableModelFilter<PackageType, ChildType> getFilter(List<Pattern> pattern, SearchCat searchCat) {
        return null;
    }

    public void mouseDragged(MouseEvent e) {
    }

    public void mouseMoved(MouseEvent e) {
        updateCursor(e);
    }

    private void updateCursor(MouseEvent e) {
        if (!hasFocus()) {
            return;
        }
        if (label != null && e.getX() < labelWidth + 5 + iconGap + 8) {
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            setCaretColor(getBackground());
            focusLost(null);
            mouseoverClose = false;
        } else if (closeXPos > 0 && e.getX() > closeXPos) {
            mouseoverClose = true;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setCaretColor(getBackground());
            focusLost(null);
        } else {
            setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
            setCaretColor(null);
            focusGained(null);
            mouseoverClose = false;
        }
    }

    public void focusGained(final FocusEvent arg0) {
        if (arg0 != null && arg0.getOppositeComponent() instanceof JRootPane) {
            return;
        }
        super.focusGained(arg0);
    }

    public void mouseClicked(MouseEvent e) {
        if (mouseoverClose && closeEnabled) {
            onResetPerformed();
        } else if (label != null && e.getX() < labelWidth + 5 + iconGap + 8) {
            onCategoryPopup();
        }
    }

    private void onResetPerformed() {
        setText(null);
        onChanged();
    }

    private void onCategoryPopup() {

        JPopupMenu popup = new JPopupMenu();
        final SearchCat selectedCat = getSelectedCategory();
        for (final SearchCat sc : searchCategories) {

            popup.add(new AppAction() {
                private final SearchCat category = sc;

                {
                    setName(sc.getLabel());
                    setSmallIcon(sc.getIcon());
                }

                public void actionPerformed(ActionEvent e) {
                    if (category != selectedCat) {
                        setSelectedCategory(category);
                        focusLost(null);
                    }
                }
            });
        }
        Insets insets = LAFOptions.getInstance().getExtension().customizePopupBorderInsets();
        Dimension pref = popup.getPreferredSize();
        // pref.width = positionComp.getWidth() + ((Component)
        // e.getSource()).getWidth() + insets[1] + insets[3];
        popup.setPreferredSize(new Dimension(labelWidth + 5 + iconGap + 8 + insets.left + insets.left + insets.right, (int) pref.getHeight()));

        popup.show(this, -insets.left, -popup.getPreferredSize().height + insets.bottom);
    }

    public void mousePressed(MouseEvent e) {
    }

    public void mouseReleased(MouseEvent e) {
    }

    public void mouseEntered(MouseEvent e) {
        updateCursor(e);
    }

    public void mouseExited(MouseEvent e) {
        mouseoverClose = false;
    }

    protected final AtomicReference<SearchCat> selectedCategory = new AtomicReference<SearchCat>(null);

    public SearchCat getSelectedCategory() {
        return selectedCategory.get();
    }

    public void setSelectedCategory(SearchCat selectedCategory) {
        if (selectedCategory == null) {
            org.appwork.utils.logging2.extmanager.LoggerFactory.getDefaultLogger().log(new NullPointerException("selectedCategory null"));
        }
        final SearchCat old = this.selectedCategory.getAndSet(selectedCategory);
        if (old != selectedCategory) {
            onChanged();
        }
        if (label != null) {
            label.setText(selectedCategory.getLabel());
            setHelpText(selectedCategory.getHelpText());
        }
    }

    public void setCategories(SearchCat[] searchCategories) {
        this.searchCategories = searchCategories;
        label = new JLabel() {
            public boolean isShowing() {

                return true;
            }

            public boolean isVisible() {
                return true;
            }
        };

        final SearchCat preSel = getSelectedCategory();
        boolean found = false;

        for (SearchCat sc : searchCategories) {
            if (sc == preSel) {
                found = true;
            }
            label.setText(sc.getLabel());
            labelWidth = Math.max(label.getPreferredSize().width, labelWidth);
        }

        if (!found) {
            final SearchCat old = this.selectedCategory.getAndSet(searchCategories[0]);
            if (old != searchCategories[0]) {
                onChanged();
            }
        }
        label.setSize(labelWidth, 24);
        // label.setEnabled(false);
        setBorder(BorderFactory.createCompoundBorder(orgBorder, BorderFactory.createEmptyBorder(0, labelWidth + 14 + iconGap, 0, 18)));
        final SearchCat sel = getSelectedCategory();
        label.setText(sel.getLabel());
        setHelpText(sel.getHelpText());
    }

}
