package org.jdownloader.gui.donate;

import java.awt.Dimension;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.appwork.storage.JSonStorage;
import org.appwork.storage.StorageException;
import org.appwork.storage.TypeRef;
import org.appwork.swing.MigPanel;
import org.appwork.swing.action.BasicAction;
import org.appwork.uio.UIOManager;
import org.appwork.utils.Application;
import org.appwork.utils.Exceptions;
import org.appwork.utils.IO;
import org.appwork.utils.Regex;
import org.appwork.utils.encoding.URLEncode;
import org.appwork.utils.logging2.LogSource;
import org.appwork.utils.os.CrossSystem;
import org.appwork.utils.swing.EDTRunner;
import org.appwork.utils.swing.dialog.AbstractDialog;
import org.appwork.utils.swing.dialog.Dialog;
import org.appwork.utils.swing.dialog.DialogCanceledException;
import org.appwork.utils.swing.dialog.ProgressDialog;
import org.appwork.utils.swing.dialog.ProgressDialog.ProgressGetter;
import org.jdownloader.donate.DonationManager;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.mainmenu.DonateAction;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.images.AbstractIcon;
import org.jdownloader.logging.LogController;
import org.jdownloader.statistics.StatsManager;

import jd.http.Browser;

public class DonationDialog extends AbstractDialog<Object> {

    private LogSource                             logger;
    // private String symbol;
    // private String currencyCode;
    // private JFormattedTextField input;
    // private Border defaultBorder;
    // private JLabel recurringLabel;
    // private JCheckBox recurring;
    // private PseudoMultiCombo<CategoryPriority> catSel;
    // private ExtTextArea note;
    // protected long prioritySum;
    protected DonationDetails                     details;
    private Browser                               br;
    protected String                              transactionID;
    private JTabbedPane                           tabbed;
    protected ProviderPanel                       currentPanel;
    private JLabel                                more;
    private MigPanel                              p;
    private ArrayList<ArrayList<PaymentProvider>> mergedList;

    public DonationDialog(DonationDetails details2) {
        super(0, _GUI.T.DonationDialog_DonationDialog_title_(), null, _GUI.T.DonationDialog_ok(), null);
        logger = LogController.getInstance().getLogger("DonationDialog");
        this.details = details2;
        br = new Browser();
    }

    @Override
    protected Object createReturnValue() {
        return null;
    }

    @Override
    protected int getPreferredHeight() {
        return 500;
    }

    @Override
    protected int getPreferredWidth() {
        return 780;
    }

    public void actionPerformed(final ActionEvent e) {

        if (e.getSource() == this.okButton) {
            org.appwork.utils.logging2.extmanager.LoggerFactory.getDefaultLogger().fine("Answer: Button<OK:" + this.okButton.getText() + ">");
            this.setReturnmask(true);
            final double amt = getProviderPanel().getAmount();
            if (amt <= 0) {
                return;
            }

            final String[] list = getProviderPanel().getSelectedCategories();
            final boolean recurringValue = getProviderPanel().isRecurring();
            final String noteText = getProviderPanel().getNote();
            final String cCode = getProviderPanel().getCurrencyCode();
            final String provider = getProviderPanel().getProvider();
            final HashMap<String, String> custom = new HashMap<String, String>();
            custom.put("source", "button");
            custom.put("os", CrossSystem.getOS().name());
            final AtomicBoolean close = new AtomicBoolean(true);
            final ProgressDialog d = new ProgressDialog(new ProgressGetter() {

                private long started;

                @Override
                public String getLabelString() {
                    return null;
                }

                @Override
                public int getProgress() {
                    return -1;

                }

                @Override
                public String getString() {
                    return _GUI.T.DonationDialog_layoutDialogContent_please_wait();
                }

                @Override
                public void run() throws Exception {
                    try {
                        // createDonation&String1&String2&double1&boolean1&HashMap1&String[]1&String3
                        String json = br.getPage(DonateAction.SERVER + "payment/createDonation?" + toQuery(provider, cCode, amt, recurringValue, custom, list, noteText));
                        transactionID = JSonStorage.restoreFromString(json, TypeRef.STRING);
                        StatsManager.I().track("/donation/button/redirect");
                        CrossSystem.openURLOrShowMessage(DonateAction.SERVER + "payment/donationRedirect?" + toQuery(transactionID));
                        started = System.currentTimeMillis();
                        while (true) {
                            try {
                                if (System.currentTimeMillis() - started > 10 * 60 * 1000l) {
                                    StatsManager.I().track("/donation/button/timeout/600");
                                    DonateFeedback.reportFailed(new Exception("Timeout 600"), "TransactionID:" + transactionID, true);
                                    Dialog.getInstance().showMessageDialog(_GUI.T.DonationDialog_run_failed());
                                    close.set(false);
                                    return;
                                }
                                final String url = DonateAction.SERVER + "payment/getStatus?" + toQuery(transactionID);
                                final String jsonStatus = br.getPage(url);
                                TransactionStatus enu = JSonStorage.restoreFromString(jsonStatus, new TypeRef<TransactionStatus>() {
                                });
                                switch (enu) {
                                case DONE:
                                    StatsManager.I().track("/donation/button/success");
                                    DonationManager.getInstance().autoHide();
                                    writeTransactionFile(amt, list, noteText, cCode, provider);
                                    Dialog.getInstance().showMessageDialog(_GUI.T.DonationDialog_run_thanks_());
                                    close.set(true);
                                    return;
                                case FAILED:
                                    StatsManager.I().track("/donation/button/failed");
                                    DonateFeedback.reportFailed(null, "TransactionID:" + transactionID, true);
                                    Dialog.getInstance().showMessageDialog(_GUI.T.DonationDialog_run_failed());
                                    close.set(false);
                                    return;
                                case CANCELED:
                                    StatsManager.I().track("/donation/button/canceled");
                                    DonateFeedback.reportCanceled("TransactionID:" + transactionID);
                                    Dialog.getInstance().showMessageDialog(_GUI.T.DonationDialog_run_cancel());
                                    close.set(false);
                                    return;
                                case PENDING:
                                case UNKNOWN:
                                }
                            } catch (Throwable e) {
                                logger.log(e);
                            }
                            Thread.sleep(1000);
                        }
                    } catch (InterruptedException e) {
                        StatsManager.I().track("/donation/button/exception/InterruptedException");
                        // startFallbackWaiter(list, noteText, amt, provider, cCode);
                        close.set(true);
                    } catch (Throwable e) {
                        try {
                            if (e.getCause() != null) {
                                e = e.getCause();
                            }
                            final StringBuilder sb = new StringBuilder();
                            final String[] lines = Regex.getLines(Exceptions.getStackTrace(e));
                            for (String line : lines) {
                                if (sb.length() > 0) {
                                    sb.insert(0, "/");
                                }
                                if (line.startsWith("at")) {
                                    sb.insert(0, URLEncode.encodeRFC2396(line.replaceAll("(^.*)(\\.[a-zA-Z0-9]+\\()", "$2").replaceAll("(at | |\\(|\\)|\\$|\\.|:)", "")));
                                } else {
                                    sb.insert(0, URLEncode.encodeRFC2396(line.replaceAll("(at | |\\(|\\)|\\$|\\.|:)", "")));
                                }
                            }
                            StatsManager.I().track("/donation/button/exception/" + sb.toString());
                            StatsManager.I().track("/donation/button/exception/" + URLEncode.encodeRFC2396(e.getClass() + "/" + e.getMessage()));
                        } catch (Throwable e2) {
                            StatsManager.I().track("/donation/button/exception/" + URLEncode.encodeRFC2396(e2.getClass() + "/" + e2.getMessage()));
                        }
                        DonateFeedback.reportFailed(e, "TransactionID:" + transactionID, false);
                        logger.log(e);
                        custom.put("source", "buttonFallback");

                        new EDTRunner() {

                            @Override
                            protected void runInEDT() {
                                try {
                                    CrossSystem.openURLOrShowMessage(DonateAction.SERVER + "payment/fallbackDonation?" + toQuery(provider, cCode, amt, recurringValue, custom, list, noteText));
                                } catch (Throwable e1) {
                                    logger.log(e1);
                                }
                                // Util.showErrorMessage(getInstallerContext().getMessage("donate_failed"));
                            }
                        };
                    } finally {
                    }
                }
            }, UIOManager.BUTTONS_HIDE_OK, _GUI.T.DonationDialog_layoutDialogContent_please_wait_title(), _GUI.T.DonationDialog_layoutDialogContent_please_wait_progress_msg(), null, null, null);

            try {
                Dialog.getInstance().showDialog(d);
            } catch (DialogCanceledException e1) {

                close.set(false);

            } catch (final Throwable e1) {

            }
            if (!close.get()) {
                return;
            }

        } else if (e.getSource() == this.cancelButton) {
            org.appwork.utils.logging2.extmanager.LoggerFactory.getDefaultLogger().fine("Answer: Button<CANCEL:" + this.cancelButton.getText() + ">");
            this.setReturnmask(false);
        }

        this.dispose();
    }

    protected String toQuery(Object... objects) throws UnsupportedEncodingException, StorageException {
        StringBuilder sb = new StringBuilder();
        for (Object o : objects) {
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(URLEncode.encodeRFC2396(JSonStorage.serializeToJson(o)));

        }
        return sb.toString();
    }

    public ProviderPanel getProviderPanel() {
        return (ProviderPanel) tabbed.getSelectedComponent();
    }

    @Override
    public JComponent layoutDialogContent() {

        p = new MigPanel("ins 5", "0[]0", "[][grow,fill]");

        JLabel top = new JLabel("<html><b>" + _GUI.T.DonationDialog_layoutDialogContent_top_text() + "</b></html>");
        p.add(top, "spanx,pushx,growx");
        tabbed = new JTabbedPane();

        HashMap<String, ArrayList<PaymentProvider>> currencyMap = new HashMap<String, ArrayList<PaymentProvider>>();
        mergedList = new ArrayList<ArrayList<PaymentProvider>>();
        for (PaymentProvider provider : details.getPaymentProvider()) {
            ArrayList<PaymentProvider> list = currencyMap.get(provider.getId());
            if (list == null) {
                currencyMap.put(provider.getId(), list = new ArrayList<PaymentProvider>());
                mergedList.add(list);
            }
            list.add(provider);
        }
        int selectTab = 0;
        File iconFolder = Application.getResource("tmp/donateicons/");
        PaymentProvider defaultProvider = details.getPaymentProvider()[details.getDefaultProvider()];
        for (ArrayList<PaymentProvider> provider : mergedList) {
            Icon icon = null;
            StringBuilder label = new StringBuilder();
            File file = new File(iconFolder, provider.get(0).getLocaleName() + ".png");

            if (file.exists()) {

                try {
                    icon = new ImageIcon(ImageIO.read(file));

                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
            if (provider.contains(defaultProvider)) {
                selectTab = tabbed.getTabCount();
            }
            tabbed.addTab(label.toString(), icon, new ProviderPanel(details, provider));
        }
        tabbed.addTab(_GUI.T.DonationDialog_layoutDialogContent_more(), new AbstractIcon(IconKey.ICON_ADD, 20), more = new JLabel());
        p.add(tabbed, "pushx,growx,spanx,pushy,growy,spany");
        tabbed.addChangeListener(new ChangeListener() {

            @Override
            public void stateChanged(ChangeEvent e) {
                if (tabbed.getSelectedComponent() == more) {
                    if (currentPanel != null) {
                        tabbed.setSelectedComponent(currentPanel);
                    } else {
                        tabbed.setSelectedIndex(0);
                    }

                    popup();
                    return;
                }
                ProviderPanel newPanel = getProviderPanel();
                if (currentPanel != null && newPanel != currentPanel && newPanel != null) {
                    newPanel.setSelectedCategories(currentPanel.getSelectedCategories());
                    newPanel.setNote(currentPanel.getNote());
                    newPanel.setRecurring(currentPanel.isRecurring());
                }
                currentPanel = newPanel;
            }
        });
        tabbed.setSelectedIndex(selectTab);

        return p;
    }

    public class NotImplementedProvider extends BasicAction {

        private PayProvider provider;

        public NotImplementedProvider(PayProvider p) {
            this.provider = p;
            setName(p.getLabel());
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            // if (provider == PayProvider.OTHER) {
            // String name = "noanswer";
            // try {
            // name = Dialog.getInstance().showInputDialog("Which provider would you like to use?");
            // } catch (DialogClosedException e1) {
            // e1.printStackTrace();
            // } catch (DialogCanceledException e1) {
            // e1.printStackTrace();
            // }
            // StatsManager.I().track("/donation/button/provider/" + provider.name() + "/" + name);
            // } else {
            StatsManager.I().track("/donation/button/provider/" + provider.name());
            // }
            Dialog.getInstance().showMessageDialog(_GUI.T.DonationDialog_NotImplementedProvider_actionPerformed_());
        }
    }

    protected void popup() {
        JPopupMenu pu = new JPopupMenu();

        ArrayList<PayProvider> lst = new ArrayList<PayProvider>();
        for (PayProvider provider : PayProvider.values()) {
            lst.add(provider);
        }
        long seed = System.nanoTime();
        Collections.shuffle(lst, new Random(seed));

        for (PayProvider p : lst) {
            pu.add(new NotImplementedProvider(p));

        }

        Point point = MouseInfo.getPointerInfo().getLocation();
        SwingUtilities.convertPointFromScreen(point, p);
        Dimension pref = pu.getPreferredSize();
        // pref.width = positionComp.getWidth() + ((Component)
        // e.getSource()).getWidth() + insets[1] + insets[3];
        // pu.setPreferredSize(new Dimension(optionsgetWidth() + insets[1] + insets[3], (int) pref.getHeight()));

        pu.show(p, point.x, point.y);
    }

    public void startFallbackWaiter(final String[] list, final String noteText, final double amt, final String provider, final String cCode) {
        StatsManager.I().track("/donation/button/startfallbackwait");
        final String transactionID = this.transactionID;
        new Thread("Wait for Donation") {
            public void run() {
                long start = System.currentTimeMillis();
                while (true) {
                    try {
                        if (System.currentTimeMillis() - start > 30 * 60 * 1000l) {
                            StatsManager.I().track("/donation/button/timeout/1800");
                            DonateFeedback.reportFailed(new Exception("Timeout 1800"), "TransactionID:" + transactionID, true);
                            Dialog.getInstance().showMessageDialog(_GUI.T.DonationDialog_run_failed());
                            return;
                        }
                        final String url = DonateAction.SERVER + "payment/getStatus?" + toQuery(transactionID);
                        final String jsonStatus = br.getPage(url);
                        TransactionStatus enu = JSonStorage.restoreFromString(jsonStatus, new TypeRef<TransactionStatus>() {
                        });
                        switch (enu) {
                        case DONE:
                            StatsManager.I().track("/donation/button/success/fallbackwait");
                            writeTransactionFile(amt, list, noteText, cCode, provider);
                            DonationManager.getInstance().autoHide();
                            Dialog.getInstance().showMessageDialog(_GUI.T.DonationDialog_run_thanks_());
                            return;
                        case FAILED:
                            StatsManager.I().track("/donation/button/failed/fallbackwait");
                            DonateFeedback.reportFailed(null, "TransactionID:" + transactionID, true);
                            Dialog.getInstance().showMessageDialog(_GUI.T.DonationDialog_run_failed());
                            return;
                        case CANCELED:
                            StatsManager.I().track("/donation/button/canceled/fallbackwait");
                            DonateFeedback.reportCanceled("TransactionID:" + transactionID);
                            Dialog.getInstance().showMessageDialog(_GUI.T.DonationDialog_run_cancel());
                            return;
                        case PENDING:
                        case UNKNOWN:
                        }
                    } catch (Throwable e) {
                        logger.log(e);
                    }
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            };
        }.start();
    }

    /**
     * @param amt
     * @return
     * @throws UnsupportedEncodingException
     */
    public String enc(final Object amt) throws UnsupportedEncodingException {
        return URLEncode.encodeRFC2396(amt + "");
    }

    /**
     * @param amt
     * @param list
     * @param noteText
     * @param cCode
     * @param provider
     * @param cfg
     * @throws IOException
     */
    public void writeTransactionFile(final double amt, final String[] list, final String noteText, final String cCode, final String provider) throws IOException {
        File cfg = Application.getResource("cfg");
        cfg.mkdirs();
        File donation = null;
        int i = 0;
        do {
            donation = new File(cfg, "donation_" + (i++) + ".json");
        } while (donation.exists());
        Transaction transaction = new Transaction();
        transaction.setProvider(provider);
        transaction.setAmount(amt);
        transaction.setCategories(list);
        transaction.setCurrency(cCode);
        transaction.setNote(noteText);
        transaction.setTid(transactionID);
        transaction.setTime(System.currentTimeMillis());
        IO.writeStringToFile(donation, JSonStorage.serializeToJson(transaction));
    }

}
