package org.jdownloader.statistics;

import java.awt.Dialog.ModalityType;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import jd.SecondLevelLaunch;
import jd.controlling.AccountController;
import jd.controlling.AccountControllerEvent;
import jd.controlling.AccountControllerListener;
import jd.controlling.downloadcontroller.AccountCache;
import jd.controlling.downloadcontroller.AccountCache.CachedAccount;
import jd.controlling.downloadcontroller.DownloadController;
import jd.controlling.downloadcontroller.DownloadLinkCandidate;
import jd.controlling.downloadcontroller.DownloadLinkCandidateResult;
import jd.controlling.downloadcontroller.DownloadLinkCandidateResult.RESULT;
import jd.controlling.downloadcontroller.DownloadSession;
import jd.controlling.downloadcontroller.DownloadWatchDog;
import jd.controlling.downloadcontroller.DownloadWatchDogProperty;
import jd.controlling.downloadcontroller.SingleDownloadController;
import jd.controlling.downloadcontroller.event.DownloadWatchdogListener;
import jd.gui.swing.jdgui.DirectFeedback;
import jd.gui.swing.jdgui.DownloadFeedBack;
import jd.http.Browser;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.plugins.Account;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

import org.appwork.exceptions.WTFException;
import org.appwork.storage.JSonMapperException;
import org.appwork.storage.JSonStorage;
import org.appwork.storage.Storable;
import org.appwork.storage.StorageException;
import org.appwork.storage.TypeRef;
import org.appwork.storage.config.JsonConfig;
import org.appwork.storage.config.ValidationException;
import org.appwork.storage.config.events.GenericConfigEventListener;
import org.appwork.storage.config.handler.KeyHandler;
import org.appwork.uio.CloseReason;
import org.appwork.uio.InputDialogInterface;
import org.appwork.uio.UIOManager;
import org.appwork.utils.Application;
import org.appwork.utils.Files;
import org.appwork.utils.Hash;
import org.appwork.utils.IO;
import org.appwork.utils.Regex;
import org.appwork.utils.StringUtils;
import org.appwork.utils.logging2.LogSource;
import org.appwork.utils.logging2.sendlogs.LogFolder;
import org.appwork.utils.net.httpconnection.HTTPProxy;
import org.appwork.utils.os.CrossSystem;
import org.appwork.utils.swing.dialog.ConfirmDialog;
import org.appwork.utils.swing.dialog.Dialog;
import org.appwork.utils.swing.dialog.DialogCanceledException;
import org.appwork.utils.swing.dialog.DialogClosedException;
import org.appwork.utils.swing.dialog.InputDialog;
import org.appwork.utils.swing.dialog.ProgressDialog;
import org.appwork.utils.swing.dialog.ProgressDialog.ProgressGetter;
import org.appwork.utils.swing.dialog.ProgressInterface;
import org.appwork.utils.zip.ZipIOException;
import org.appwork.utils.zip.ZipIOWriter;
import org.jdownloader.actions.AppAction;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.images.AbstractIcon;
import org.jdownloader.jdserv.JDServUtils;
import org.jdownloader.jdserv.stats.StatsManagerConfigV2;
import org.jdownloader.logging.LogController;
import org.jdownloader.myjdownloader.client.json.AbstractJsonData;
import org.jdownloader.plugins.PluginTaskID;
import org.jdownloader.plugins.tasks.PluginSubTask;
import org.jdownloader.settings.advanced.AdvancedConfigEntry;
import org.jdownloader.settings.advanced.AdvancedConfigManager;

public class StatsManager implements GenericConfigEventListener<Object>, DownloadWatchdogListener, Runnable {
    private static final long         RANGE_48HOUR_B     = 50 * 60 * 60 * 1000l;
    private static final long         RANGE_48HOUR_A     = 0;
    private static final long         RANGE_1YEAR_B      = ((365) + 1) * 24 * 60 * 60 * 1000l;
    private static final long         RANGE_2YEAR_B      = ((2 * 365) + 1) * 24 * 60 * 60 * 1000l;
    private static final long         RANGE_6MONTH_B     = ((6 * 31) + 1) * 24 * 60 * 60 * 1000l;
    private static final long         RANGE_1MONTH_B     = 32 * 24 * 60 * 60 * 1000l;
    private static final long         RANGE_3MONTH_B     = ((3 * 31) + 1) * 24 * 60 * 60 * 1000l;
    private static final long         RANGE_2YEAR_A      = RANGE_1YEAR_B;

    private static final long         RANGE_1YEAR_A      = RANGE_6MONTH_B;

    private static final long         RANGE_6MONTH_A     = RANGE_3MONTH_B;

    private static final long         RANGE_1MONTH_A     = RANGE_48HOUR_B;

    private static final long         RANGE_3MONTH_A     = RANGE_1MONTH_B;

    private static final StatsManager INSTANCE           = new StatsManager();

    private static final boolean      DISABLED           = false;

    public static final int           STACKTRACE_VERSION = 1;

    /**
     * get the only existing instance of StatsManager. This is a singleton
     * 
     * @return
     */
    public static StatsManager I() {
        return StatsManager.INSTANCE;
    }

    private StatsManagerConfigV2           config;

    private LogSource                      logger;
    private ArrayList<StatsLogInterface>   list;
    private Thread                         thread;

    private HashMap<String, AtomicInteger> counterMap;

    private long                           sessionStart;

    private File                           reducerFile;

    private HashMap<String, Integer>       reducerRandomMap;

    private void log(StatsLogInterface dl) {
        if (isEnabled()) {
            if (Math.random() > 0.25d && !(dl instanceof AbstractTrackEntry)) {
                return;
            }
            synchronized (list) {
                if (list.size() > 20) {
                    list.clear();
                }
                list.add(dl);
                list.notifyAll();
            }
        }

    }

    /**
     * Create a new instance of StatsManager. This is a singleton class. Access the only existing instance by using {@link #link()}.
     */
    private StatsManager() {
        list = new ArrayList<StatsLogInterface>();
        logger = LogController.getInstance().getLogger(StatsManager.class.getName());

        counterMap = new HashMap<String, AtomicInteger>();
        config = JsonConfig.create(StatsManagerConfigV2.class);
        reducerFile = Application.getResource("cfg/reducer.json");
        if (reducerFile.exists()) {
            try {
                reducerRandomMap = JSonStorage.restoreFromString(IO.readFileToString(reducerFile), TypeRef.HASHMAP_INTEGER);
            } catch (Throwable e) {
                logger.log(e);
            }
        }
        if (reducerRandomMap == null) {
            reducerRandomMap = new HashMap<String, Integer>();
        }

        DownloadWatchDog.getInstance().getEventSender().addListener(this);
        config._getStorageHandler().getKeyHandler("enabled").getEventSender().addListener(this);
        thread = new Thread(this);
        thread.setDaemon(true);
        thread.setName("StatsSender");
        thread.start();
        sessionStart = System.currentTimeMillis();

        trackR();

        SecondLevelLaunch.INIT_COMPLETE.executeWhenReached(new Runnable() {

            @Override
            public void run() {
                AccountController.getInstance().getEventSender().addListener(new AccountControllerListener() {

                    @Override
                    public synchronized void onAccountControllerEvent(AccountControllerEvent event) {
                        try {

                            if (event.getType() == AccountControllerEvent.Types.ADDED || (event.getType() == AccountControllerEvent.Types.ACCOUNT_CHECKED && event.getAccount().getBooleanProperty("fireStatsCall"))) {
                                final Account account = event.getAccount();
                                account.removeProperty("fireStatsCall");
                                if (account.getLongProperty("added", 0) <= 0) {
                                    account.setProperty("added", System.currentTimeMillis());
                                }
                                if (account != null && account.isValid()) {
                                    long lastValid = account.getLastValidTimestamp();
                                    if (lastValid <= 0 && event.getType() == AccountControllerEvent.Types.ADDED) {
                                        // the account may not be checked yet. send stats later
                                        account.setProperty("fireStatsCall", true);
                                        return;
                                    }
                                    final AccountInfo info = account.getAccountInfo();
                                    String type = "Unknown";
                                    long estimatedBuytime = account.getLongProperty("added", 0);
                                    final long validUntilTimeStamp;
                                    final long expireInMs;
                                    if (info != null) {
                                        validUntilTimeStamp = info.getValidUntil();
                                        expireInMs = validUntilTimeStamp - System.currentTimeMillis();
                                        if ("uploaded.to".equals(account.getHoster())) {
                                            if (validUntilTimeStamp > estimatedBuytime) {
                                                if (expireInMs > 0) {
                                                    if (expireInMs <= RANGE_48HOUR_B && expireInMs > RANGE_48HOUR_A) {
                                                        // 48hours
                                                        type = "48hours";
                                                        estimatedBuytime = Math.min(estimatedBuytime, validUntilTimeStamp - (48 * 60 * 60 * 1000l));
                                                    } else if (expireInMs <= RANGE_1MONTH_B && expireInMs > RANGE_1MONTH_A) {
                                                        // 1month
                                                        type = "1month";
                                                        estimatedBuytime = Math.min(estimatedBuytime, validUntilTimeStamp - ((1 * 30) * 24 * 60 * 60 * 1000l));
                                                    } else if (expireInMs <= RANGE_3MONTH_B && expireInMs > RANGE_3MONTH_A) {
                                                        // 3month
                                                        type = "3months";
                                                        estimatedBuytime = Math.min(estimatedBuytime, validUntilTimeStamp - ((3 * 30) * 24 * 60 * 60 * 1000l));
                                                    } else if (expireInMs <= RANGE_6MONTH_B && expireInMs > RANGE_6MONTH_A) {
                                                        // 6month
                                                        type = "6months";
                                                        estimatedBuytime = Math.min(estimatedBuytime, validUntilTimeStamp - ((6 * 30) * 24 * 60 * 60 * 1000l));
                                                    } else if (expireInMs <= RANGE_1YEAR_B && expireInMs > RANGE_1YEAR_A) {
                                                        // 1year
                                                        type = "1year";
                                                        estimatedBuytime = Math.min(estimatedBuytime, validUntilTimeStamp - ((365) * 24 * 60 * 60 * 1000l));
                                                    } else if (expireInMs <= RANGE_2YEAR_B && expireInMs > RANGE_2YEAR_A) {
                                                        // 2year
                                                        type = "2years";
                                                        estimatedBuytime = Math.min(estimatedBuytime, validUntilTimeStamp - ((2 * 365) * 24 * 60 * 60 * 1000l));
                                                    }
                                                }
                                            }
                                        } else if ("rapidgator.net".equals(account.getHoster())) {
                                            if (validUntilTimeStamp > estimatedBuytime) {
                                                if (expireInMs > 0) {
                                                    if (expireInMs < RANGE_1MONTH_B && expireInMs > RANGE_1MONTH_A) {
                                                        // 1month
                                                        type = "1month";
                                                        estimatedBuytime = Math.min(estimatedBuytime, validUntilTimeStamp - ((1 * 31) * 24 * 60 * 60 * 1000l));
                                                    } else if (expireInMs < RANGE_3MONTH_B && expireInMs > RANGE_3MONTH_A) {
                                                        // 3month
                                                        type = "3months";
                                                        estimatedBuytime = Math.min(estimatedBuytime, validUntilTimeStamp - ((3 * 30) * 24 * 60 * 60 * 1000l));
                                                    }
                                                }
                                            }
                                        }
                                        final String id;
                                        final HashMap<String, String> infos;
                                        if (validUntilTimeStamp > 0) {
                                            if (expireInMs > 0) {
                                                infos = new HashMap<String, String>();

                                                infos.put("ms", Long.toString(expireInMs));
                                                id = "premium/valid/" + account.getHoster() + "/" + account.getType() + "/until";
                                            } else {
                                                infos = null;
                                                id = "premium/valid/" + account.getHoster() + "/" + account.getType() + "/expired";
                                            }
                                        } else {
                                            infos = null;
                                            id = "premium/valid/" + account.getHoster() + "/" + account.getType() + "/unlimited";
                                        }
                                        StatsManager.I().track(id, infos);
                                    } else {
                                        validUntilTimeStamp = -1;
                                        expireInMs = -1;
                                        final String id = "premium/valid/" + account.getHoster() + "/" + account.getType() + "/unknown";
                                        StatsManager.I().track(id, null);
                                    }
                                    final String domain = account.getHoster();
                                    final File file = Application.getResource("cfg/clicked/" + domain + ".json");
                                    if (file.exists()) {
                                        ArrayList<ClickedAffLinkStorable> list = null;
                                        try {
                                            list = JSonStorage.restoreFromString(IO.readFileToString(file), new TypeRef<ArrayList<ClickedAffLinkStorable>>() {
                                            });
                                        } catch (Throwable e) {
                                            logger.log(e);
                                        } finally {
                                            file.delete();
                                        }
                                        if (list == null || list.size() == 0) {
                                            return;
                                        }
                                        final ClickedAffLinkStorable st = list.get(list.size() - 1);
                                        if (st != null) {
                                            final long timeDiff = estimatedBuytime - st.getTime();
                                            final HashMap<String, String> infos = new HashMap<String, String>();
                                            infos.put("clicksource", st.getSource() + "");
                                            infos.put("ms", Long.toString(timeDiff));
                                            Map<String, Object> properties = account.getProperties();
                                            if (info != null) {
                                                if (validUntilTimeStamp > 0) {
                                                    if (expireInMs > 0) {
                                                        infos.put("until", Long.toString(expireInMs));
                                                    } else {
                                                        infos.put("until", "0");
                                                    }
                                                } else {
                                                    infos.put("until", "-1");
                                                }
                                            }
                                            StatsManager.I().track("premium/addedAfter/" + account.getHoster() + "/" + account.getType() + "/" + type, infos);
                                        }
                                    }
                                }
                            }
                        } catch (Throwable e) {
                            logger.log(e);
                        }
                    }
                });
            }
        });
    }

    public void trackR() {
        final int reducer = 1000;
        String path2 = "ping";
        if (reducer > 1) {
            path2 += "_in" + reducer;
            synchronized (reducerRandomMap) {
                Integer randomValue = reducerRandomMap.get(path2);
                if (randomValue != null) {
                    if (randomValue.intValue() != 0) {
                        return;
                    }
                }
            }
        }
        final String path = path2;
        new Thread("Pinger") {
            private int i;
            {
                setDaemon(true);
            }

            public void run() {
                if (reducer > 1) {
                    synchronized (reducerRandomMap) {
                        Integer randomValue = reducerRandomMap.get(path);
                        if (randomValue == null) {
                            Random random = new Random(System.currentTimeMillis());
                            randomValue = random.nextInt(reducer);
                            reducerRandomMap.put(path, randomValue.intValue());
                            try {
                                IO.secureWrite(reducerFile, JSonStorage.serializeToJson(reducerRandomMap).getBytes("UTF-8"));
                            } catch (Throwable e) {
                                logger.log(e);
                            }
                        }
                        if (randomValue.intValue() != 0) {
                            return;
                        }
                    }
                }
                while (true) {
                    this.i = (int) System.currentTimeMillis();
                    log(new AbstractTrackEntry() {

                        @Override
                        public void send(Browser br) {
                            try {

                                final HashMap<String, String> cvar = new HashMap<String, String>();
                                try {
                                    cvar.put("_id", System.getProperty(new String(new byte[] { (byte) 117, (byte) 105, (byte) 100 }, new String(new byte[] { 85, 84, 70, 45, 56 }, "UTF-8"))));
                                } catch (UnsupportedEncodingException e1) {
                                    e1.printStackTrace();
                                }
                                cvar.put("d", (System.currentTimeMillis() - i) + "");
                                cvar.put("source", "jd2");
                                cvar.put("os", CrossSystem.getOS().name());

                                URLConnectionAdapter con = new Browser().openGetConnection("http://stats.appwork.org/jcgi/event/track?" + Encoding.urlEncode(path) + "&" + Encoding.urlEncode(JSonStorage.serializeToJson(cvar)));
                                con.disconnect();
                            } catch (Throwable e) {
                                e.printStackTrace();
                            }

                        }
                    });
                    i++;
                    try {
                        Thread.sleep(10 * 60 * 1000l);
                    } catch (InterruptedException e) {
                        return;
                    }

                }

            };
        }.start();

    }

    protected void collectMostImportantAdvancedOptions() {
        if ((checkReducer("advanced", 100) && !config.isAskedToContributeAdvancedSetup())) {
            if (!Application.isHeadless()) {
                new Thread() {
                    {
                        setDaemon(true);
                    }

                    public boolean equals(Object x, Object y) {
                        if (x instanceof Collection && ((Collection) x).size() == 0) {
                            x = null;
                        }
                        if (y instanceof Collection && ((Collection) y).size() == 0) {
                            y = null;
                        }
                        if (x instanceof List && ((List) x).size() == 0) {
                            x = null;
                        }
                        if (y instanceof List && ((List) y).size() == 0) {
                            y = null;
                        }
                        if (x instanceof Map && ((Map) x).size() == 0) {
                            x = null;
                        }
                        if (y instanceof Map && ((Map) y).size() == 0) {
                            y = null;
                        }
                        if (x == y) {
                            return true;
                        }
                        if (x == null && y != null) {
                            return false;
                        }
                        if (y == null && x != null) {
                            return false;
                        }

                        if (x instanceof Storable && y instanceof Storable) {
                            boolean ret = JSonStorage.serializeToJson(x).equals(JSonStorage.serializeToJson(y));
                            if (ret) {
                                return true;
                            } else {
                                return false;
                            }
                        }
                        if (x instanceof String && y instanceof String) {
                            if (StringUtils.isEmpty((String) x) && StringUtils.isEmpty((String) y)) {
                                return true;
                            }
                        }
                        boolean ret = x.equals(y);
                        if (ret) {
                            return true;
                        } else {
                            return false;
                        }
                    }

                    public void run() {
                        try {
                            Thread.sleep(2);

                            final HashSet<String> map = new HashSet<String>();
                            final ArrayList<String> list = new ArrayList<String>();
                            final StringBuilder sb = new StringBuilder();
                            HashSet<String> exclude = new HashSet<String>();
                            exclude.add("FFmpegSetup.dash2aaccommand");
                            exclude.add("FFmpegSetup.dash2m4acommand");
                            exclude.add("FFmpegSetup.dash2oggaudiocommand");
                            exclude.add("FFmpegSetup.demux2aaccommand");
                            exclude.add("FFmpegSetup.demux2m4acommand");
                            exclude.add("FFmpegSetup.demux2mp3command");
                            exclude.add("FFmpegSetup.demuxandconvert2ogg");
                            exclude.add("FFmpegSetup.demuxgenericcommand");
                            exclude.add("FFmpegSetup.muxtomp4command");
                            exclude.add("FFmpegSetup.muxtowebmcommand");
                            exclude.add("GeneralSettings.browsercommandline");
                            exclude.add("GeneralSettings.domainrules");
                            exclude.add("GraphicalUserInterfaceSettings.overviewpositions");
                            exclude.add("GraphicalUserInterfaceSettings.windowswindowmanageraltkeycombi");
                            exclude.add("InternetConnectionSettings.customproxylist");
                            exclude.add("LAFSettings.popupborderinsets");
                            exclude.add("LinkFilterSettings.filterlist");
                            exclude.add("LinkgrabberSettings.downloaddestinationhistory");
                            exclude.add("MyJDownloaderSettings.deviceconnectports");
                            exclude.add("PackagizerSettings.rulelist");
                            exclude.add("PackagizerSettings.tryjd1importenabled");
                            exclude.add("Reconnect.activepluginid");
                            exclude.add("StatsManagerV2.captchauploadpercentage");
                            exclude.add("GraphicalUserInterfaceSetting.specialdealoboomdialogvisibleonstartup");
                            exclude.add("GraphicalUserInterfaceSettings.windowswindowmanagerforegroundlocktimeout");
                            exclude.add("GraphicalUserInterfaceSettings.premiumexpirewarningmapv2");
                            exclude.add("MyJDownloaderSettings.latesterror");
                            exclude.add("LinkgrabberSettings.packagenamehistory");
                            exclude.add("MyJDownloaderSettings.devicename");
                            for (AdvancedConfigEntry value : AdvancedConfigManager.getInstance().list()) {
                                if (exclude.contains(value.getKey())) {
                                    continue;
                                }
                                if (!equals(value.getValue(), value.getDefault())) {
                                    if (map.add(value.getKey().replace(".", "_"))) {
                                        list.add(value.getKey().replace(".", "_"));
                                    }
                                }
                            }
                            if (map.size() == 0) {
                                return;
                            }
                            Collections.sort(list);
                            for (String l : list) {
                                if (sb.length() > 0) {
                                    sb.append("\r\n");
                                }
                                sb.append(l);

                            }
                            config.setAskedToContributeAdvancedSetup(true);
                            ConfirmDialog d = new ConfirmDialog(0, _GUI._.StatsManager_StatsManager_advanced_survey_title(), _GUI._.StatsManager_StatsManager_advanced_survey_msg(), null, _GUI._.StatsManager_StatsManager_advanced_survey_send(), _GUI._.lit_no()) {
                                @Override
                                public ModalityType getModalityType() {
                                    return ModalityType.MODELESS;
                                }
                            };
                            d.setLeftActions(new AppAction() {
                                {
                                    setName(_GUI._.StatsManager_StatsManager_advanced_survey_show());
                                }

                                @Override
                                public void actionPerformed(ActionEvent e) {
                                    final ConfirmDialog d = new ConfirmDialog(Dialog.STYLE_LARGE | UIOManager.BUTTONS_HIDE_CANCEL, _GUI._.StatsManager_StatsManager_advanced_survey_title_list(), sb.toString(), null, _GUI._.lit_close(), null) {
                                        @Override
                                        protected int getPreferredHeight() {
                                            return 750;
                                        }

                                        @Override
                                        public ModalityType getModalityType() {
                                            return ModalityType.MODELESS;
                                        }

                                        @Override
                                        protected int getPreferredWidth() {
                                            return super.getPreferredWidth();
                                        }
                                    };
                                    new Thread() {
                                        {
                                            setDaemon(true);
                                        }

                                        public void run() {
                                            UIOManager.I().show(null, d);

                                        }
                                    }.start();

                                }
                            });
                            UIOManager.I().show(null, d).throwCloseExceptions();
                            new Browser().postPageRaw("http://stats.appwork.org/jcgi/event/adv", JSonStorage.serializeToJson(list));

                        } catch (InterruptedException e1) {

                        } catch (DialogClosedException e1) {
                            e1.printStackTrace();
                        } catch (DialogCanceledException e1) {
                            e1.printStackTrace();
                        } catch (StorageException e1) {
                            e1.printStackTrace();
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        }
                    };
                }.start();
            }

        }
    }

    private boolean checkReducer(String path, int reducer) {
        synchronized (reducerRandomMap) {
            path += "_" + reducer;
            Integer randomValue = reducerRandomMap.get(path);
            if (randomValue == null) {
                Random random = new Random(System.currentTimeMillis());
                randomValue = random.nextInt(reducer);
                reducerRandomMap.put(path, randomValue.intValue());
                try {
                    IO.secureWrite(reducerFile, JSonStorage.serializeToJson(reducerRandomMap).getBytes("UTF-8"));
                } catch (Throwable e) {
                    logger.log(e);
                }

            }
            return randomValue != null && randomValue == 0;
        }
    }

    public long getSessionStart() {
        return sessionStart;
    }

    /**
     * this setter does not set the config flag. Can be used to disable the logger for THIS session.
     * 
     * @param b
     */
    public void setEnabled(boolean b) {
        config.setEnabled(b);
    }

    public boolean isEnabled() {
        String dev = System.getProperty("statsmanager");
        if (dev != null && dev.equalsIgnoreCase("true")) {
            return true;
        }
        if (!Application.isJared(StatsManager.class)) {
            return false;
        }
        return config.isEnabled() /* && */;

    }

    @Override
    public void onConfigValidatorError(KeyHandler<Object> keyHandler, Object invalidValue, ValidationException validateException) {
    }

    @Override
    public void onConfigValueModified(KeyHandler<Object> keyHandler, Object newValue) {

    }

    @Override
    public void onDownloadWatchdogDataUpdate() {
    }

    @Override
    public void onDownloadWatchdogStateIsIdle() {
    }

    @Override
    public void onDownloadWatchdogStateIsPause() {
    }

    @Override
    public void onDownloadWatchdogStateIsRunning() {
    }

    private void createAndUploadLog(PostAction action, boolean silent) {

        final File[] logs = Application.getResource("logs").listFiles();

        LogFolder latestLog = null;
        LogFolder currentLog = null;

        if (logs != null) {
            for (final File f : logs) {
                final String timestampString = new Regex(f.getName(), "(\\d+)_\\d\\d\\.\\d\\d").getMatch(0);
                if (timestampString != null) {
                    final long timestamp = Long.parseLong(timestampString);
                    LogFolder lf;
                    lf = new LogFolder(f, timestamp);
                    if (LogController.getInstance().getInitTime() == timestamp) {
                        /*
                         * this is our current logfolder, flush it before we can upload it
                         */

                        SimpleDateFormat df = new SimpleDateFormat("dd.MM.yy HH.mm.ss", Locale.GERMANY);
                        // return .format(date);
                        lf.setNeedsFlush(true);
                        currentLog = lf;
                        final File zip = Application.getTempResource("logs/logPackage_" + System.currentTimeMillis() + ".zip");
                        zip.delete();
                        zip.getParentFile().mkdirs();
                        ZipIOWriter writer = null;

                        final String name = lf.getFolder().getName() + "-" + df.format(new Date(lf.getCreated())) + " to " + df.format(new Date(lf.getLastModified()));
                        final File folder = Application.getTempResource("logs/" + name);
                        try {
                            try {
                                LogController.getInstance().flushSinks(true, false);
                                writer = new ZipIOWriter(zip) {
                                    @Override
                                    public void addFile(final File addFile, final boolean compress, final String fullPath) throws FileNotFoundException, ZipIOException, IOException {
                                        if (addFile.getName().endsWith(".lck") || addFile.isFile() && addFile.length() == 0) {
                                            return;
                                        }
                                        if (Thread.currentThread().isInterrupted()) {
                                            throw new WTFException("INterrupted");
                                        }
                                        super.addFile(addFile, compress, fullPath);
                                    }
                                };

                                if (folder.exists()) {
                                    Files.deleteRecursiv(folder);
                                }
                                IO.copyFolderRecursive(lf.getFolder(), folder, true);
                                writer.addDirectory(folder, true, null);

                            } finally {
                                try {
                                    writer.close();
                                } catch (final Throwable e) {
                                }
                            }

                            if (Thread.currentThread().isInterrupted()) {
                                throw new WTFException("INterrupted");
                            }

                            String id = JDServUtils.upload(IO.readFile(zip), "ErrorID: " + action.getData(), null);

                            zip.delete();
                            if (zip.length() > 1024 * 1024 * 10) {
                                throw new Exception("Filesize: " + zip.length());
                            }
                            sendLogDetails(new LogDetails(id, action.getData()));
                            if (!silent) {
                                UIOManager.I().showMessageDialog(_GUI._.StatsManager_createAndUploadLog_thanks_(action.getData()));
                            }

                        } catch (Exception e) {
                            logger.log(e);

                        }
                        return;
                    }

                }
            }
        }

    }

    @Override
    public void onDownloadWatchdogStateIsStopped() {
    }

    @Override
    public void onDownloadWatchdogStateIsStopping() {
    }

    @Override
    public void onDownloadControllerStart(SingleDownloadController downloadController, DownloadLinkCandidate candidate) {
    }

    private ConcurrentHashMap<String, ErrorDetails> errors                = new ConcurrentHashMap<String, ErrorDetails>(10, 0.9f, 1);
    private HashSet<String>                         requestedErrorDetails = new HashSet<String>();
    private HashSet<String>                         requestedLogs         = new HashSet<String>();

    @Override
    public void onDownloadControllerStopped(SingleDownloadController downloadController, DownloadLinkCandidate candidate, DownloadLinkCandidateResult result) {
        try {
            // HashResult hashResult = downloadController.getHashResult();
            // long startedAt = downloadController.getStartTimestamp();
            DownloadLink link = downloadController.getDownloadLink();

            DownloadLogEntry dl = new DownloadLogEntry();
            Throwable th = null;
            if (result.getResult() != null) {
                switch (result.getResult()) {
                case ACCOUNT_ERROR:
                case ACCOUNT_INVALID:
                    dl.setResult(DownloadResult.ACCOUNT_INVALID);
                    break;
                case ACCOUNT_REQUIRED:
                    dl.setResult(DownloadResult.ACCOUNT_REQUIRED);
                    break;
                case ACCOUNT_UNAVAILABLE:
                    dl.setResult(DownloadResult.ACCOUNT_UNAVAILABLE);
                    break;
                case CAPTCHA:
                    dl.setResult(DownloadResult.CAPTCHA);
                    break;
                case CONDITIONAL_SKIPPED:
                    dl.setResult(DownloadResult.CONDITIONAL_SKIPPED);
                    break;
                case CONNECTION_ISSUES:
                    dl.setResult(DownloadResult.CONNECTION_ISSUES);
                    break;
                case CONNECTION_TEMP_UNAVAILABLE:
                    dl.setResult(DownloadResult.CONNECTION_UNAVAILABLE);
                    break;
                case FAILED:
                    dl.setResult(DownloadResult.FAILED);
                    break;

                case FAILED_INCOMPLETE:
                    dl.setResult(DownloadResult.FAILED_INCOMPLETE);
                    th = result.getThrowable();
                    if (th != null) {

                        if (th instanceof PluginException) {

                            // String error = ((PluginException) th).getErrorMessage();
                            if (((PluginException) th).getValue() == LinkStatus.VALUE_NETWORK_IO_ERROR) {
                                dl.setResult(DownloadResult.CONNECTION_ISSUES);
                            } else if (((PluginException) th).getValue() == LinkStatus.VALUE_LOCAL_IO_ERROR) {
                                return;
                            }

                        }

                    }
                    break;
                case FATAL_ERROR:
                    dl.setResult(DownloadResult.FATAL_ERROR);
                    break;
                case FILE_UNAVAILABLE:
                    dl.setResult(DownloadResult.FILE_UNAVAILABLE);
                    break;
                case FINISHED:
                    dl.setResult(DownloadResult.FINISHED);
                    break;
                case FINISHED_EXISTS:
                    dl.setResult(DownloadResult.FINISHED_EXISTS);
                    break;
                case HOSTER_UNAVAILABLE:
                    dl.setResult(DownloadResult.HOSTER_UNAVAILABLE);
                    th = result.getThrowable();
                    if (th != null) {
                        if (th instanceof PluginException) {
                            System.out.println(1);
                            String error = ((PluginException) th).getErrorMessage();
                            if (error != null && (error.contains("Reconnection") || error.contains("Waiting till new downloads can be started"))) {
                                dl.setResult(DownloadResult.IP_BLOCKED);
                            }

                        }
                    }
                    break;
                case IP_BLOCKED:
                    dl.setResult(DownloadResult.IP_BLOCKED);
                    break;
                case OFFLINE_TRUSTED:
                    dl.setResult(DownloadResult.OFFLINE_TRUSTED);
                    break;
                case PLUGIN_DEFECT:
                    dl.setResult(DownloadResult.PLUGIN_DEFECT);
                    break;
                case PROXY_UNAVAILABLE:
                    dl.setResult(DownloadResult.PROXY_UNAVAILABLE);
                    break;
                case SKIPPED:
                    dl.setResult(DownloadResult.SKIPPED);
                    break;

                case FAILED_EXISTS:
                case RETRY:
                case STOPPED:
                    // no reason to log them
                    return;
                default:
                    dl.setResult(DownloadResult.UNKNOWN);
                }
            } else {
                dl.setResult(DownloadResult.UNKNOWN);
            }
            // long downloadTime = link.getView().getDownloadTime();
            List<PluginSubTask> tasks = downloadController.getTasks();
            PluginSubTask plugintask = tasks.get(0);
            PluginSubTask downloadTask = null;
            long userIO = 0l;
            long captcha = 0l;
            long waittime = 0l;
            for (int i = 1; i < tasks.size(); i++) {
                PluginSubTask task = tasks.get(i);
                if (downloadTask == null) {
                    switch (task.getId()) {
                    case CAPTCHA:
                        captcha += task.getRuntime();
                        break;
                    case USERIO:
                        userIO += task.getRuntime();
                        break;
                    case WAIT:
                        waittime += task.getRuntime();
                        break;

                    }
                }
                if (task.getId() == PluginTaskID.DOWNLOAD) {
                    downloadTask = task;
                    break;
                }
            }
            if (downloadTask == null) {
                // download stopped or failed, before the downloadtask
            }
            long pluginRuntime = downloadTask != null ? (downloadTask.getStartTime() - plugintask.getStartTime()) : plugintask.getRuntime();

            HTTPProxy usedProxy = downloadController.getUsedProxy();
            CachedAccount account = candidate.getCachedAccount();
            boolean aborted = downloadController.isAborting();
            // long duration = link.getView().getDownloadTime();

            long sizeChange = Math.max(0, link.getView().getBytesLoaded() - downloadController.getSizeBefore());
            long duration = downloadTask != null ? downloadTask.getRuntime() : 0;
            long speed = duration <= 0 ? 0 : (sizeChange * 1000) / duration;

            pluginRuntime -= userIO;
            pluginRuntime -= captcha;

            switch (result.getResult()) {
            case ACCOUNT_INVALID:
            case ACCOUNT_REQUIRED:
            case ACCOUNT_UNAVAILABLE:
            case CAPTCHA:
            case CONDITIONAL_SKIPPED:
            case CONNECTION_ISSUES:
            case CONNECTION_TEMP_UNAVAILABLE:
            case FAILED:
            case FAILED_EXISTS:
            case FAILED_INCOMPLETE:
            case FATAL_ERROR:
            case FILE_UNAVAILABLE:

            case FINISHED_EXISTS:
            case HOSTER_UNAVAILABLE:
            case IP_BLOCKED:
            case OFFLINE_TRUSTED:
            case PLUGIN_DEFECT:
            case PROXY_UNAVAILABLE:
            case RETRY:
            case SKIPPED:
            case STOPPED:

                break;
            case FINISHED:

                if (downloadTask != null) {
                    // we did at least download somthing
                }

            }
            //

            // dl.set

            dl.setBuildTime(readBuildTime());

            dl.setResume(downloadController.isResumed());
            dl.setCanceled(aborted);
            dl.setHost(Candidate.replace(link.getHost()));
            dl.setCandidate(Candidate.create(account));

            dl.setCaptchaRuntime(captcha);
            dl.setFilesize(Math.max(0, link.getView().getBytesTotal()));
            dl.setPluginRuntime(pluginRuntime);
            dl.setProxy(usedProxy != null && !usedProxy.isDirect() && !usedProxy.isNone());

            dl.setSpeed(speed);
            dl.setWaittime(waittime);

            dl.setOs(CrossSystem.getOSFamily().name());
            dl.setUtcOffset(TimeZone.getDefault().getOffset(System.currentTimeMillis()));
            final String errorID = result.getErrorID();
            String stacktrace = errorID;
            if (stacktrace != null) {
                stacktrace = "IDV" + STACKTRACE_VERSION + ":\r\n" + dl.getCandidate().getPlugin() + "-" + dl.getCandidate().getType() + "\r\n" + account.getPlugin().getClass().getName() + "\r\n" + cleanErrorID(stacktrace);
            }

            dl.setErrorID(errorID == null ? null : Hash.getMD5(stacktrace));
            dl.setTimestamp(System.currentTimeMillis());
            dl.setSessionStart(sessionStart);
            // this linkid is only unique for you. it is not globaly unique, thus it cannot be mapped to the actual url or anything like
            // this.
            dl.setLinkID(link.getUniqueID().getID());
            String id = dl.getCandidate().getRevision() + "_" + dl.getErrorID() + "_" + dl.getCandidate().getPlugin() + "_" + dl.getCandidate().getType();
            AtomicInteger errorCounter = counterMap.get(id);
            if (errorCounter == null) {
                counterMap.put(id, errorCounter = new AtomicInteger());
            }

            //
            dl.setCounter(errorCounter.incrementAndGet());
            ;

            if (dl.getErrorID() != null) {
                ErrorDetails error = errors.get(dl.getErrorID());

                if (error == null) {

                    ErrorDetails error2 = errors.putIfAbsent(dl.getErrorID(), error = new ErrorDetails(dl.getErrorID()));
                    error.setStacktrace(stacktrace);

                    error.setBuildTime(dl.getBuildTime());

                    if (error2 != null) {
                        error = error2;
                    }
                }
            }
            logger.info("Tracker Package: \r\n" + JSonStorage.serializeToJson(dl));
            if (dl.getErrorID() != null) {
                logger.info("Error Details: \r\n" + JSonStorage.serializeToJson(errors.get(dl.getErrorID())));
            }

            if (result.getLastPluginHost() != null && !StringUtils.equals(dl.getCandidate().getPlugin(), result.getLastPluginHost())) {
                // the error did not happen in the plugin
                logger.info("Do not track. " + result.getLastPluginHost() + "!=" + dl.getCandidate().getPlugin());
                // return;
            }
            // DownloadInterface instance = link.getDownloadLinkController().getDownloadInstance();

            log(dl);
        } catch (Throwable e) {
            logger.log(e);
        }

    }

    public static String cleanErrorID(String errorID) {
        if (errorID == null) {
            return null;
        }
        if (errorID.contains("java.lang.NumberFormatException")) {
            errorID = Pattern.compile("java.lang.NumberFormatException: For input string: \".*?\"\\s*[\r\n]{1,}", Pattern.DOTALL).matcher(errorID).replaceAll("java.lang.NumberFormatException: For input string: \"@See Log\"\r\n");

        }
        return errorID;
    }

    public static enum ActionID {

        REQUEST_LOG,
        REQUEST_ERROR_DETAILS,
        REQUEST_MESSAGE;
    }

    public static enum PushResponseCode {
        OK,
        FAILED,
        KILL;
    }

    public static class PostAction extends AbstractJsonData implements Storable {
        public PostAction(/* storable */) {

        }

        public PostAction(ActionID id, String data) {
            this.id = id;
            this.data = data;
        }

        private String   data = null;
        private ActionID id   = null;

        public String getData() {
            return data;
        }

        public void setData(String data) {
            this.data = data;
        }

        public ActionID getId() {
            return id;
        }

        public void setId(ActionID id) {
            this.id = id;
        }

    }

    public static class Response extends AbstractJsonData implements Storable {
        public Response(PushResponseCode code) {
            this.code = code;
        }

        public PushResponseCode getCode() {
            return code;
        }

        public void setCode(PushResponseCode code) {
            this.code = code;
        }

        public Response(/* storable */) {

        }

        private PostAction[] actions = null;

        public PostAction[] getActions() {
            return actions;
        }

        public void setActions(PostAction[] actions) {
            this.actions = actions;
        }

        private PushResponseCode code = PushResponseCode.OK;

    }

    @Override
    public void run() {
        while (true) {
            ArrayList<LogEntryWrapper> sendTo = new ArrayList<LogEntryWrapper>();
            ArrayList<AbstractLogEntry> sendRequest = new ArrayList<AbstractLogEntry>();
            ArrayList<AbstractTrackEntry> trackRequest = new ArrayList<AbstractTrackEntry>();
            Browser br = createBrowser();
            try {
                while (list.size() == 0) {
                    synchronized (list) {
                        if (list.size() == 0) {
                            list.wait(10 * 60 * 1000);

                        }
                    }
                }
                retry: while (true) {
                    try {
                        synchronized (list) {
                            for (StatsLogInterface l : list) {
                                if (l instanceof AbstractLogEntry) {
                                    sendRequest.add((AbstractLogEntry) l);
                                    sendTo.add(new LogEntryWrapper((AbstractLogEntry) l, LogEntryWrapper.VERSION));
                                } else if (l instanceof AbstractTrackEntry) {
                                    trackRequest.add((AbstractTrackEntry) l);
                                }
                            }

                            list.clear();
                        }
                        if (trackRequest.size() > 0) {
                            for (AbstractTrackEntry l : trackRequest) {
                                try {
                                    l.send(br);
                                } catch (Throwable e) {
                                    logger.log(e);
                                }

                            }
                        }
                        if (sendTo.size() > 0) {
                            Thread.sleep(1 * 60 * 1000l);
                            logger.info("Try to send: \r\n" + JSonStorage.serializeToJson(sendRequest));
                            if (!config.isEnabled()) {
                                return;
                            }
                            br.postPageRaw(getBase() + "stats/push", Encoding.urlEncode(JSonStorage.serializeToJson(new TimeWrapper(sendTo))));

                            // br.postPageRaw("http://localhost:8888/stats/push", JSonStorage.serializeToJson(sendTo));

                            Response response = JSonStorage.restoreFromString(br.getRequest().getHtmlCode(), new TypeRef<RIDWrapper<Response>>() {
                            }).getData();
                            switch (response.getCode()) {
                            case OK:
                                PostAction[] actions = response.getActions();
                                if (actions != null) {
                                    for (final PostAction action : actions) {
                                        if (action != null) {
                                            switch (action.getId()) {
                                            case REQUEST_MESSAGE:
                                                requestMessage(action);
                                                break;
                                            case REQUEST_ERROR_DETAILS:
                                                ErrorDetails error = errors.get(action.getData());
                                                if (error != null) {
                                                    sendErrorDetails(error);
                                                } else {
                                                    requestedErrorDetails.add(action.getData());
                                                }

                                                break;

                                            case REQUEST_LOG:
                                                if (!requestedLogs.add(action.getData())) {
                                                    break;
                                                }
                                                boolean found = false;
                                                if (action.getData() != null) {
                                                    for (AbstractLogEntry s : sendRequest) {
                                                        if (s instanceof DownloadLogEntry) {
                                                            if (StringUtils.equals(((DownloadLogEntry) s).getErrorID(), action.getData())) {
                                                                final DownloadLink downloadLink = DownloadController.getInstance().getLinkByID(((DownloadLogEntry) s).getLinkID());
                                                                if (downloadLink != null) {
                                                                    found = true;
                                                                    new Thread("Log Requestor") {
                                                                        @Override
                                                                        public void run() {
                                                                            if (config.isAlwaysAllowLogUploads()) {
                                                                                createAndUploadLog(action, true);
                                                                            } else {
                                                                                UploadSessionLogDialogInterface d = UIOManager.I().show(UploadSessionLogDialogInterface.class, new UploadSessionLogDialog(action.getData(), downloadLink));
                                                                                config.setAlwaysAllowLogUploads(d.isDontShowAgainSelected());
                                                                                if (d.getCloseReason() == CloseReason.OK) {
                                                                                    UIOManager.I().show(ProgressInterface.class, new ProgressDialog(new ProgressGetter() {

                                                                                        @Override
                                                                                        public void run() throws Exception {
                                                                                            createAndUploadLog(action, false);
                                                                                        }

                                                                                        @Override
                                                                                        public String getString() {
                                                                                            return null;
                                                                                        }

                                                                                        @Override
                                                                                        public int getProgress() {
                                                                                            return -1;
                                                                                        }

                                                                                        @Override
                                                                                        public String getLabelString() {
                                                                                            return null;
                                                                                        }
                                                                                    }, 0, _GUI._.StatsManager_run_upload_error_title(), _GUI._.StatsManager_run_upload_error_message(), new AbstractIcon(IconKey.ICON_UPLOAD, 32)) {
                                                                                        public java.awt.Dialog.ModalityType getModalityType() {
                                                                                            return ModalityType.MODELESS;
                                                                                        };
                                                                                    });
                                                                                }
                                                                            }
                                                                        }
                                                                    }.start();
                                                                }
                                                            }
                                                        }

                                                    }
                                                }
                                                if (!found) {
                                                    new Thread("Log Requestor") {
                                                        @Override
                                                        public void run() {
                                                            if (config.isAlwaysAllowLogUploads()) {
                                                                createAndUploadLog(action, true);
                                                            } else {
                                                                UploadGeneralSessionLogDialogInterface d = UIOManager.I().show(UploadGeneralSessionLogDialogInterface.class, new UploadGeneralSessionLogDialog());
                                                                if (d.getCloseReason() == CloseReason.OK) {
                                                                    UIOManager.I().show(ProgressInterface.class, new ProgressDialog(new ProgressGetter() {

                                                                        @Override
                                                                        public void run() throws Exception {
                                                                            createAndUploadLog(action, false);
                                                                        }

                                                                        @Override
                                                                        public String getString() {
                                                                            return null;
                                                                        }

                                                                        @Override
                                                                        public int getProgress() {
                                                                            return -1;
                                                                        }

                                                                        @Override
                                                                        public String getLabelString() {
                                                                            return null;
                                                                        }
                                                                    }, 0, _GUI._.StatsManager_run_upload_error_title(), _GUI._.StatsManager_run_upload_error_message(), new AbstractIcon(IconKey.ICON_UPLOAD, 32)) {
                                                                        public java.awt.Dialog.ModalityType getModalityType() {
                                                                            return ModalityType.MODELESS;
                                                                        };
                                                                    });
                                                                }
                                                            }
                                                        }
                                                    }.start();
                                                    // non-error related log request
                                                }
                                                // if (StringUtils.equals(getErrorID(), action.getData())) {
                                                // StatsManager.I().sendLogs(getErrorID(),);
                                                // }

                                                break;

                                            }

                                        }
                                    }
                                }
                                break retry;
                            case FAILED:
                                break retry;
                            case KILL:
                                return;
                            }

                        } else {
                            break retry;
                        }
                        System.out.println(1);
                    } catch (ConnectException e) {
                        logger.log(e);
                        logger.info("Wait and retry");
                        Thread.sleep(5 * 60 * 1000l);
                        // not sent. push back
                        // synchronized (list) {
                        // list.addAll(sendRequest);
                        // }
                    } catch (JSonMapperException e) {
                        logger.log(e);
                        logger.info("Wait and retry");
                        Thread.sleep(5 * 60 * 1000l);
                        // not sent. push back
                        // synchronized (list) {
                        // list.addAll(sendRequest);
                        // }
                    }
                }
            } catch (Exception e) {
                // failed. push back
                logger.log(e);
                // synchronized (list) {
                // list.addAll(sendRequest);
                // }
            }
        }
    }

    private void requestMessage(final PostAction action) {
        new Thread("Log Requestor") {
            @Override
            public void run() {
                InputDialogInterface d = UIOManager.I().show(InputDialogInterface.class, new InputDialog(Dialog.STYLE_LARGE, _GUI._.StatsManager_run_requestMessage_title(), _GUI._.StatsManager_run_requestMessage_message(), null, null, _GUI._.lit_send(), null));
                if (d.getCloseReason() == CloseReason.OK) {
                    try {
                        sendMessage(d.getText(), action);
                    } catch (Exception e) {
                        logger.log(e);
                    }
                }
            }
        }.start();

    }

    public Browser createBrowser() {
        Browser br = new Browser();
        final int[] codes = new int[999];
        for (int i = 0; i < codes.length; i++) {
            codes[i] = i;
        }
        br.setAllowedResponseCodes(codes);
        return br;
    }

    private void sendLogDetails(LogDetails log) throws StorageException, IOException {
        Browser br = createBrowser();
        br.postPageRaw(getBase() + "stats/sendLog", Encoding.urlEncode(JSonStorage.serializeToJson(log)));

    }

    private void sendErrorDetails(ErrorDetails error) throws StorageException, IOException {
        Browser br = createBrowser();
        br.postPageRaw(getBase() + "stats/sendError", Encoding.urlEncode(JSonStorage.serializeToJson(error)));

    }

    private String getBase() {
        if (!Application.isJared(null) && false) {
            return "http://localhost:8888/";
        }
        if (!Application.isJared(null) && false) {
            return "http://192.168.2.250:81/thomas/fcgi/";
        }
        return "http://stats.appwork.org/jcgi/";
    }

    @Override
    public void onDownloadWatchDogPropertyChange(DownloadWatchDogProperty propertyChange) {
    }

    public void feedback(DirectFeedback feedback) {

        if (feedback instanceof DownloadFeedBack) {
            DownloadLink link = ((DownloadFeedBack) feedback).getDownloadLink();

            ArrayList<Candidate> possibleAccounts = new ArrayList<Candidate>();
            AccountCache accountCache = new DownloadSession().getAccountCache(link);
            HashSet<String> dupe = new HashSet<String>();
            for (CachedAccount s : accountCache) {
                Account acc = s.getAccount();
                if (acc != null && !acc.isEnabled()) {
                    continue;
                }
                Candidate candidate = Candidate.create(s);
                if (dupe.add(candidate.toID())) {
                    possibleAccounts.add(candidate);
                }
            }

            DownloadFeedbackLogEntry dl = new DownloadFeedbackLogEntry();
            dl.setHost(link.getHost());
            dl.setCandidates(possibleAccounts);
            dl.setFilesize(Math.max(0, link.getView().getBytesTotal()));

            dl.setBuildTime(readBuildTime());

            dl.setOs(CrossSystem.getOSFamily().name());
            dl.setUtcOffset(TimeZone.getDefault().getOffset(System.currentTimeMillis()));
            dl.setTimestamp(System.currentTimeMillis());

            dl.setSessionStart(sessionStart);
            // this linkid is only unique for you. it is not globaly unique, thus it cannot be mapped to the actual url or anything like
            // this.
            dl.setLinkID(link.getUniqueID().getID());
            String id = dl.getLinkID() + "";
            AtomicInteger errorCounter = counterMap.get(id);
            if (errorCounter == null) {
                counterMap.put(id, errorCounter = new AtomicInteger());
            }
            dl.setCounter(errorCounter.incrementAndGet());
            sendFeedback(dl);

            UIOManager.I().showMessageDialog(_GUI._.VoteFinderWindow_runInEDT_thankyou_2());
        }

    }

    public static long readBuildTime() {
        try {
            HashMap<String, Object> map = JSonStorage.restoreFromString(IO.readFileToString(Application.getResource("build.json")), TypeRef.HASHMAP);
            return readBuildTime(map);
        } catch (Throwable e) {
            return 0;
        }
    }

    public static long readBuildTime(HashMap<String, Object> map) {
        try {
            Object ret = map.get("buildTimestamp");
            if (ret instanceof Number) {
                return ((Number) ret).longValue();
            }

            return Long.parseLong(ret + "");
        } catch (Throwable e) {
            return 0;
        }
    }

    private void sendFeedback(AbstractFeedbackLogEntry dl) {
        Browser br = createBrowser();
        ArrayList<LogEntryWrapper> sendTo = new ArrayList<LogEntryWrapper>();
        sendTo.add(new LogEntryWrapper(dl, LogEntryWrapper.VERSION));
        try {
            String feedbackjson = JSonStorage.serializeToJson(new TimeWrapper(sendTo));

            br.postPageRaw(getBase() + "stats/push", Encoding.urlEncode(feedbackjson));

            // br.postPageRaw("http://localhost:8888/stats/push", JSonStorage.serializeToJson(sendTo));

            Response response = JSonStorage.restoreFromString(br.getRequest().getHtmlCode(), new TypeRef<RIDWrapper<Response>>() {
            }).getData();
            switch (response.getCode()) {
            case FAILED:
                break;
            case KILL:
                break;

            case OK:

                PostAction[] actions = response.getActions();
                if (actions != null) {
                    for (final PostAction action : actions) {
                        try {
                            if (action != null) {
                                switch (action.getId()) {
                                case REQUEST_MESSAGE:
                                    requestMessage(action);
                                    break;
                                case REQUEST_ERROR_DETAILS:
                                    break;

                                case REQUEST_LOG:

                                    // new Thread("Log Requestor") {
                                    // @Override
                                    // public void run() {
                                    // UploadGeneralSessionLogDialogInterface d =
                                    // UIOManager.I().show(UploadGeneralSessionLogDialogInterface.class, new
                                    // UploadGeneralSessionLogDialog());
                                    // if (d.getCloseReason() == CloseReason.OK) {
                                    // UIOManager.I().show(ProgressInterface.class, new ProgressDialog(new ProgressGetter() {
                                    //
                                    // @Override
                                    // public void run() throws Exception {
                                    // createAndUploadLog(action, false);
                                    // }
                                    //
                                    // @Override
                                    // public String getString() {
                                    // return null;
                                    // }
                                    //
                                    // @Override
                                    // public int getProgress() {
                                    // return -1;
                                    // }
                                    //
                                    // @Override
                                    // public String getLabelString() {
                                    // return null;
                                    // }
                                    // }, 0, _GUI._.StatsManager_run_upload_error_title(), _GUI._.StatsManager_run_upload_error_message(),
                                    // new AbstractIcon(IconKey.ICON_UPLOAD, 32)) {
                                    // public java.awt.Dialog.ModalityType getModalityType() {
                                    // return ModalityType.MODELESS;
                                    // };
                                    // });
                                    // }
                                    // }
                                    // }.start();
                                    // non-error related log request
                                }
                                // if (StringUtils.equals(getErrorID(), action.getData())) {
                                // StatsManager.I().sendLogs(getErrorID(),);
                                // }

                            }
                        } catch (Exception e) {
                            logger.log(e);

                        }
                    }
                }

            }

        } catch (StorageException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void sendMessage(String text, PostAction action) throws StorageException, IOException {

        Browser br = createBrowser();
        br.postPageRaw(getBase() + "stats/sendMessage", Encoding.urlEncode(JSonStorage.serializeToJson(new MessageData(text, action.getData()))));

    }

    public void track(final int reducer, final String id, final Map<String, String> infos) {
        final String path;
        if (reducer > 1) {
            path = id + "_in" + reducer;
            synchronized (reducerRandomMap) {
                final Integer randomValue = reducerRandomMap.get(id);
                if (randomValue != null) {
                    if (randomValue.intValue() != 0) {
                        return;
                    }
                }
            }
        } else {
            path = id;
        }
        log(new AbstractTrackEntry() {

            @Override
            public void send(Browser br) {
                try {
                    if (reducer > 1) {
                        synchronized (reducerRandomMap) {
                            Integer randomValue = reducerRandomMap.get(path);
                            if (randomValue == null) {
                                final Random random = new Random(System.currentTimeMillis());
                                randomValue = random.nextInt(reducer);
                                reducerRandomMap.put(path, randomValue.intValue());
                                try {
                                    IO.secureWrite(reducerFile, JSonStorage.serializeToJson(reducerRandomMap).getBytes("UTF-8"));
                                } catch (Throwable e) {
                                    logger.log(e);
                                }
                            }
                            if (randomValue.intValue() != 0) {
                                return;
                            }
                        }
                    }
                    final HashMap<String, String> cvar = new HashMap<String, String>();
                    try {
                        cvar.put("_id", System.getProperty(new String(new byte[] { (byte) 117, (byte) 105, (byte) 100 }, new String(new byte[] { 85, 84, 70, 45, 56 }, "UTF-8"))));
                    } catch (Throwable e1) {
                        e1.printStackTrace();
                    }
                    cvar.put("source", "jd2");
                    cvar.put("os", CrossSystem.getOS().name());
                    if (infos != null) {
                        cvar.putAll(infos);
                    }
                    final Browser browser = new Browser();
                    try {
                        final URLConnectionAdapter con = browser.openGetConnection("http://stats.appwork.org/jcgi/event/track?" + Encoding.urlEncode(path) + "&" + Encoding.urlEncode(JSonStorage.serializeToJson(cvar)));
                        con.disconnect();
                    } finally {
                        try {
                            browser.getHttpConnection().disconnect();
                        } catch (final Throwable e) {
                        }
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }

            }
        });
    }

    /**
     * use the reducer if you want to limit the tracker. 1000 means that only one out of 1000 calls will be accepted
     * 
     * @param reducer
     * @param path
     */
    public void track(final int reducer, String path2) {
        track(reducer, path2, null);
    }

    public void track(final String path) {
        track(1, path, null);
    }

    public void track(final String path, final Map<String, String> infos) {
        track(1, path, infos);
    }

    public void logDownloadException(DownloadLink link, PluginForHost plugin, Throwable e) {
        try {
            DownloadLinkCandidateResult result = new DownloadLinkCandidateResult(RESULT.PLUGIN_DEFECT, e, plugin.getHost());
            DownloadLogEntry dl = new DownloadLogEntry();

            SingleDownloadController downloadController = link.getDownloadLinkController();
            dl.setResult(DownloadResult.PLUGIN_DEFECT);

            HTTPProxy usedProxy = downloadController.getUsedProxy();
            boolean aborted = downloadController.isAborting();
            // long duration = link.getView().getDownloadTime();

            long sizeChange = Math.max(0, link.getView().getBytesLoaded() - downloadController.getSizeBefore());

            dl.setBuildTime(StatsManager.readBuildTime());

            dl.setResume(downloadController.isResumed());
            dl.setCanceled(aborted);
            dl.setHost(Candidate.replace(link.getHost()));
            dl.setCandidate(Candidate.create(new CachedAccount(plugin.getHost(), null, plugin)));

            dl.setCaptchaRuntime(0);
            dl.setFilesize(Math.max(0, link.getView().getBytesTotal()));
            dl.setPluginRuntime(1000);
            dl.setProxy(usedProxy != null && !usedProxy.isDirect() && !usedProxy.isNone());
            dl.setSpeed(1000);
            dl.setWaittime(1000);
            dl.setOs(CrossSystem.getOSFamily().name());
            dl.setUtcOffset(TimeZone.getDefault().getOffset(System.currentTimeMillis()));
            final String errorID = result.getErrorID();
            String stacktrace = errorID;
            if (stacktrace != null) {
                stacktrace = "IDV" + StatsManager.STACKTRACE_VERSION + ":\r\n" + dl.getCandidate().getPlugin() + "-" + dl.getCandidate().getType() + "\r\n" + getClass().getName() + "\r\n" + StatsManager.cleanErrorID(stacktrace);
            }

            dl.setErrorID(errorID == null ? null : Hash.getMD5(stacktrace));
            dl.setTimestamp(System.currentTimeMillis());
            dl.setSessionStart(StatsManager.I().getSessionStart());
            // this linkid is only unique for you. it is not globaly unique, thus it cannot be mapped to the actual url or anything like
            // this.
            dl.setLinkID(link.getUniqueID().getID());
            String id = dl.getCandidate().getRevision() + "_" + dl.getErrorID() + "_" + dl.getCandidate().getPlugin() + "_" + dl.getCandidate().getType();
            AtomicInteger errorCounter = counterMap.get(id);
            if (errorCounter == null) {
                counterMap.put(id, errorCounter = new AtomicInteger());
            }

            //
            dl.setCounter(errorCounter.incrementAndGet());
            ;

            if (dl.getErrorID() != null) {
                ErrorDetails error = errors.get(dl.getErrorID());

                if (error == null) {

                    ErrorDetails error2 = errors.putIfAbsent(dl.getErrorID(), error = new ErrorDetails(dl.getErrorID()));
                    error.setStacktrace(stacktrace);

                    error.setBuildTime(dl.getBuildTime());

                    if (error2 != null) {
                        error = error2;
                    }
                }
            }
            logger.info("Tracker Package: \r\n" + JSonStorage.serializeToJson(dl));
            if (dl.getErrorID() != null) {
                logger.info("Error Details: \r\n" + JSonStorage.serializeToJson(errors.get(dl.getErrorID())));
            }

            if (result.getLastPluginHost() != null && !StringUtils.equals(dl.getCandidate().getPlugin(), result.getLastPluginHost())) {
                // the error did not happen in the plugin
                logger.info("Do not track. " + result.getLastPluginHost() + "!=" + dl.getCandidate().getPlugin());
                // return;
            }
            //
            log(dl);
        } catch (Throwable e1) {
            logger.log(e1);
        }
    }

    public static void main(String[] args) throws MalformedURLException {
        System.out.println(new URL("uploaded.to").getHost());
    }

    public void openAfflink(final String url, final String source, final boolean direct) {
        try {
            if (url.startsWith("https://www.oboom.com/ref/C0ACB0?ref_token=")) {
                StatsManager.I().track("buypremium/" + source + "/https://www.oboom.com/ref/C0ACB0?ref_token=...");

            } else if (url.startsWith("http://update3.jdownloader.org/jdserv/RedirectInterface/ul")) {
                StatsManager.I().track("buypremium/" + source + "/http://update3.jdownloader.org/jdserv/RedirectInterface/ul...");
            } else {
                StatsManager.I().track("buypremium/" + source + "/" + url);
            }

            String domain = url;
            if (!StringUtils.startsWithCaseInsensitive(domain, "http")) {
                domain = "http://" + domain;
            }
            try {
                domain = new URL(url).getHost();
            } catch (Throwable e) {
            }
            // do mappings here.
            if (domain.equalsIgnoreCase("uploaded.to") || url.contains("RedirectInterface/ul") || domain.equalsIgnoreCase("ul.to") || domain.equalsIgnoreCase("ul.net") || domain.equalsIgnoreCase("uploaded.net")) {
                domain = "uploaded.to";
            }
            final File file = Application.getResource("cfg/clicked/" + domain + ".json");
            file.getParentFile().mkdirs();
            ArrayList<ClickedAffLinkStorable> list = null;
            if (file.exists()) {
                try {
                    list = JSonStorage.restoreFromString(IO.readFileToString(file), new TypeRef<ArrayList<ClickedAffLinkStorable>>() {
                    });
                    // TODO CLeanup
                } catch (Throwable e) {
                    logger.log(e);
                }
            }
            if (list == null) {
                list = new ArrayList<ClickedAffLinkStorable>();
            }
            // there is no reason to keep older clicks right now.
            list.clear();
            list.add(new ClickedAffLinkStorable(url, source));
            try {
                IO.secureWrite(file, JSonStorage.serializeToJson(list).getBytes("UTF-8"));
            } catch (Throwable e) {
                logger.log(e);
                file.delete();
            }
        } finally {
            if (direct) {
                CrossSystem.openURLOrShowMessage(url);
            } else {
                CrossSystem.openURLOrShowMessage(AccountController.createFullBuyPremiumUrl(url, source));
            }
        }
    }
}
