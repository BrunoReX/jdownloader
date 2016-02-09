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

package org.jdownloader.captcha.v2.solver.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.net.MalformedURLException;
import java.util.HashSet;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JSeparator;

import org.appwork.exceptions.WTFException;
import org.appwork.swing.MigPanel;
import org.appwork.swing.components.CheckBoxIcon;
import org.appwork.utils.images.IconIO;
import org.appwork.utils.swing.SwingUtils;
import org.appwork.utils.swing.dialog.Dialog;
import org.jdownloader.DomainInfo;
import org.jdownloader.captcha.v2.challenge.recaptcha.v2.AbstractRecaptcha2FallbackChallenge;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.updatev2.gui.LAFOptions;

import jd.gui.swing.dialog.AbstractImageCaptchaDialog;
import jd.gui.swing.dialog.DialogType;
import net.miginfocom.swing.MigLayout;

/**
 * This Dialog is used to display a Inputdialog for the captchas
 */
public class RecaptchaChooseFrom3x3Dialog extends AbstractImageCaptchaDialog {

    private HashSet<Integer>                    selected;
    private AbstractRecaptcha2FallbackChallenge challenge;

    // public RecaptchaChooseFrom3x3Dialog(final int flag, DialogType type, final DomainInfo DomainInfo, final Image image, final String
    // explain) {
    // this(flag, type, DomainInfo, new Image[] { image }, explain);
    // }

    public RecaptchaChooseFrom3x3Dialog(int flag, DialogType type, DomainInfo domainInfo, AbstractRecaptcha2FallbackChallenge challenge) {
        super(flag | Dialog.STYLE_HIDE_ICON, _GUI.T.gui_captchaWindow_askForInput(domainInfo.getTld()), type, domainInfo, null, (Image[]) null);

        this.challenge = challenge;
        BufferedImage img;
        try {
            img = IconIO.getImage(challenge.getImageFile().toURI().toURL(), false);

            images = new Image[] { img };
        } catch (MalformedURLException e) {
            throw new WTFException(e);
        }

    }

    @Override
    protected void addBeforeImage(MigPanel field) {
        super.addBeforeImage(field);
        String key = challenge.getHighlightedExplain();

        field.setLayout(new MigLayout("ins 0,wrap 1", "[grow,fill]", "[][][grow,fill]"));

        Icon explainIcon = challenge.getExplainIcon(challenge.getExplain());
        String ex = challenge.getExplain().replaceAll("<.*?>", "").replace(key, "<b>" + key + "</b>");
        if (challenge.getChooseAtLeast() > 0) {
            ex += "<br>" + _GUI.T.RECAPTCHA_2_Dialog_help(challenge.getChooseAtLeast());
        }
        if (challenge.getType() != null && challenge.getType().startsWith("TileSelection")) {
            ex += "<br>" + _GUI.T.RECAPTCHA_2_Dialog_help_tile();
        }
        if (explainIcon != null) {
            field.add(SwingUtils.setOpaque(new JLabel("<html>" + ex + "</html>", explainIcon, JLabel.LEFT), false), "gapleft 5,gaptop 5");
        } else {
            field.add(SwingUtils.setOpaque(new JLabel("<html>" + ex + "</html>"), false), "gapleft 5,gaptop 5");

        }

        field.add(new JSeparator(JSeparator.HORIZONTAL));
    }

    private Color col = (LAFOptions.getInstance().getColorForPanelBackground());

    private int ceil(double d) {
        return (int) Math.ceil(d);
    }

    protected void paintIconComponent(Graphics g, int width, int height, int xOffset, int yOffset, BufferedImage scaled) {
        if (bounds == null) {
            return;
        }

        double columnWidth = bounds.getWidth() / challenge.getSplitWidth();
        double rowHeight = bounds.getHeight() / challenge.getSplitHeight();
        double imgColumnWidth = images[0].getWidth(null) / challenge.getSplitWidth();
        double imgRowHeight = images[0].getHeight(null) / challenge.getSplitHeight();

        g.setColor(col);

        int splitterWidth = 4;
        ((Graphics2D) g).setStroke(new BasicStroke(splitterWidth));
        for (int yslot = 0; yslot < challenge.getSplitHeight() - 1; yslot++) {
            int y = ceil((1 + yslot) * rowHeight);

            g.drawLine(bounds.x, bounds.y + y, bounds.x + bounds.width, bounds.y + y);

        }
        for (int xslot = 0; xslot < challenge.getSplitWidth() - 1; xslot++) {
            int x = ceil((1 + xslot) * columnWidth);

            g.drawLine(bounds.x + x, bounds.y, bounds.x + x, bounds.y + bounds.height);
        }
        ((Graphics2D) g).setStroke(new BasicStroke(3));
        for (int yslot = 0; yslot < challenge.getSplitHeight(); yslot++) {
            for (int xslot = 0; xslot < challenge.getSplitWidth(); xslot++) {
                int x = (int) ((1 + xslot) * columnWidth);
                int y = (int) ((1 + yslot) * rowHeight);
                int num = xslot + yslot * challenge.getSplitWidth();

                // Color color = Color.RED;
                if (selected.contains(num)) {
                    Rectangle rect = new Rectangle(ceil(bounds.x + xslot * columnWidth), ceil(bounds.y + yslot * rowHeight), ceil(columnWidth), ceil(rowHeight));
                    Rectangle src = new Rectangle(ceil(xslot * imgColumnWidth), ceil(yslot * imgRowHeight), ceil(imgColumnWidth), ceil(imgRowHeight));

                    int strokeWidth = 10;
                    g.setColor(col);
                    g.fillRect(rect.x, rect.y, rect.width, rect.height);
                    g.setColor(Color.GREEN.brighter());
                    int x1 = rect.x + strokeWidth / 2 + splitterWidth;
                    int y1 = rect.y + strokeWidth / 2 + splitterWidth;
                    int x2 = rect.x + rect.width - strokeWidth;
                    int y2 = rect.y + rect.height - strokeWidth;
                    g.drawImage(images[0], x1, y1, x2, y2, src.x, src.y, src.x + src.width, src.y + src.height, null);
                    g.drawRect(x1, y1, x2 - x1, y2 - y1);
                    CheckBoxIcon.TRUE.paintIcon(iconPanel, g, xOffset + x - CheckBoxIcon.TRUE.getIconWidth() - 5, yOffset + y - CheckBoxIcon.TRUE.getIconHeight() - 5);

                } else {
                    CheckBoxIcon.FALSE.paintIcon(iconPanel, g, xOffset + x - CheckBoxIcon.TRUE.getIconWidth() - 5, yOffset + y - CheckBoxIcon.TRUE.getIconHeight() - 5);
                }
            }
        }

    }

    @Override
    public JComponent layoutDialogContent() {
        JComponent ret = super.layoutDialogContent();
        this.selected = new HashSet<Integer>();
        iconPanel.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        iconPanel.setToolTipText(getHelpText());
        iconPanel.addMouseListener(new MouseListener() {

            @Override
            public void mouseReleased(MouseEvent e) {

                int x = (e.getX() - bounds.x) / (bounds.width / challenge.getSplitWidth());
                int y = (e.getY() - bounds.y) / (bounds.height / challenge.getSplitHeight());
                int num = x + y * challenge.getSplitWidth();
                System.out.println("pressed " + num);
                if (!selected.remove(num)) {
                    selected.add(num);

                }
                iconPanel.repaint();

            }

            @Override
            public void mousePressed(MouseEvent e) {
            }

            @Override
            public void mouseExited(MouseEvent e) {
            }

            @Override
            public void mouseEntered(MouseEvent e) {
            }

            @Override
            public void mouseClicked(MouseEvent e) {
            }
        });
        return ret;
    }

    @Override
    protected JComponent createInputComponent() {
        return null;
    }

    public String getResult() {
        StringBuilder sb = new StringBuilder();

        for (Integer s : selected) {
            if (sb.length() > 0) {
                sb.append(",");

            }
            sb.append(s + 1);
        }
        return sb.toString();
    }

}