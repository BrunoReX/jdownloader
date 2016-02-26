package org.jdownloader.premium;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.ActionEvent;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.appwork.exceptions.WTFException;
import org.appwork.swing.MigPanel;
import org.appwork.swing.components.ExtButton;
import org.appwork.utils.swing.SwingUtils;
import org.appwork.utils.swing.dialog.AbstractDialog;
import org.appwork.utils.swing.dialog.DialogNoAnswerException;
import org.jdownloader.DomainInfo;
import org.jdownloader.gui.InputChangedCallbackInterface;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.images.NewTheme;
import org.jdownloader.plugins.accounts.AccountBuilderInterface;
import org.jdownloader.plugins.controller.PluginClassLoader;
import org.jdownloader.plugins.controller.PluginClassLoader.PluginClassLoaderChild;
import org.jdownloader.plugins.controller.UpdateRequiredClassNotFoundException;
import org.jdownloader.plugins.controller.host.HostPluginController;

import jd.gui.swing.dialog.AddAccountDialog;
import jd.gui.swing.dialog.InputOKButtonAdapter;
import jd.plugins.Account;
import jd.plugins.PluginForHost;

public class BuyAndAddPremiumAccount extends AbstractDialog<Boolean> implements BuyAndAddPremiumDialogInterface, InputChangedCallbackInterface {

    private DomainInfo                   info;

    private String                       id;

    private AccountBuilderInterface      accountBuilderUI;

    private final PluginClassLoaderChild cl;

    public BuyAndAddPremiumAccount(DomainInfo info, String id) {
        super(0, _GUI.T.BuyAndAddPremiumAccount_BuyAndAddPremiumAccount_title_(), null, null, null);
        this.info = info;
        this.id = id;
        cl = PluginClassLoader.getInstance().getChild();
    }

    @Override
    protected Boolean createReturnValue() {
        return null;
    }

    public void actionPerformed(final ActionEvent e) {
        if (e.getSource() == this.okButton) {
            org.appwork.utils.logging2.extmanager.LoggerFactory.getDefaultLogger().fine("Answer: Button<OK:" + this.okButton.getText() + ">");

            Account ac = accountBuilderUI.getAccount();
            ac.setHoster(info.getTld());
            try {
                if (!AddAccountDialog.addAccount(ac)) {
                    return;
                } else {
                    this.dispose();
                }
            } catch (DialogNoAnswerException e2) {
                this.dispose();
            }
            this.setReturnmask(true);
        } else if (e.getSource() == this.cancelButton) {
            org.appwork.utils.logging2.extmanager.LoggerFactory.getDefaultLogger().fine("Answer: Button<CANCEL:" + this.cancelButton.getText() + ">");
            this.setReturnmask(false);
        }
        this.dispose();
    }

    protected void layoutDialog() {

        final Image back = NewTheme.I().hasIcon("fav/footer." + info.getTld()) ? NewTheme.I().getImage("fav/footer." + info.getTld(), -1) : null;
        super.layoutDialog();
        getDialog().setContentPane(new JPanel() {
            /**
             *
             */
            private static final long serialVersionUID = 1L;

            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (back != null) {
                    final Graphics2D g2 = (Graphics2D) g;

                    double faktor = Math.max((double) back.getWidth(null) / getWidth(), (double) back.getHeight(null) / getHeight());
                    int width = Math.max((int) (back.getWidth(null) / faktor), 1);
                    int height = Math.max((int) (back.getHeight(null) / faktor), 1);
                    g2.drawImage(back, 0, getHeight() - height, width, getHeight(), 0, 0, back.getWidth(null), back.getHeight(null), getBackground(), null);
                }
            }
        });
    }

    @Override
    public JComponent layoutDialogContent() {

        final MigPanel ret = new MigPanel("ins 0,wrap 1", "[grow,fill]", "[]");
        final Image logo = NewTheme.I().hasIcon("fav/large." + info.getTld()) ? NewTheme.I().getImage("fav/large." + info.getTld(), -1) : null;

        if (logo != null) {
            JLabel ico = new JLabel(new ImageIcon(logo));
            ret.add(ico);
        }

        ret.add(header(_GUI.T.BuyAndAddPremiumAccount_layoutDialogContent_get()), "gapleft 15,pushx,growx");
        ExtButton bt = new ExtButton(new OpenURLAction(info, id == null ? "BuyAndAddDialog" : id));
        ret.add(bt, "gapleft 27");
        ret.add(header(_GUI.T.BuyAndAddPremiumAccount_layoutDialogContent_enter()), "gapleft 15,pushx,growx");
        PluginForHost plg;
        try {
            plg = HostPluginController.getInstance().get(info.getTld()).newInstance(cl);
        } catch (UpdateRequiredClassNotFoundException e) {
            throw new WTFException(e);
        }
        accountBuilderUI = plg.getAccountFactory(this);
        ret.add(accountBuilderUI.getComponent(), "gapleft 27");
        onChangedInput(null);
        return ret;
    }

    private JComponent header(String buyAndAddPremiumAccount_layoutDialogContent_get) {
        JLabel ret = SwingUtils.toBold(new JLabel(buyAndAddPremiumAccount_layoutDialogContent_get));
        ret.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ret.getForeground()));
        return ret;
    }

    @Override
    public void onChangedInput(Object component) {
        InputOKButtonAdapter.register(this, accountBuilderUI);
    }
}
