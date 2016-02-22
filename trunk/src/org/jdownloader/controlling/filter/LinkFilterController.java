package org.jdownloader.controlling.filter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import jd.controlling.TaskQueue;
import jd.controlling.linkcrawler.CrawledLink;
import jd.controlling.linkcrawler.LinkCrawlerFilter;

import org.appwork.exceptions.WTFException;
import org.appwork.shutdown.ShutdownController;
import org.appwork.shutdown.ShutdownEvent;
import org.appwork.shutdown.ShutdownRequest;
import org.appwork.storage.JSonStorage;
import org.appwork.storage.TypeRef;
import org.appwork.storage.config.JsonConfig;
import org.appwork.storage.config.ValidationException;
import org.appwork.storage.config.events.GenericConfigEventListener;
import org.appwork.storage.config.handler.KeyHandler;
import org.appwork.utils.event.predefined.changeevent.ChangeEvent;
import org.appwork.utils.event.predefined.changeevent.ChangeEventSender;
import org.appwork.utils.event.queue.QueueAction;

public class LinkFilterController implements LinkCrawlerFilter {
    private static final LinkFilterController INSTANCE = new LinkFilterController(false);

    /**
     * get the only existing instance of LinkFilterController. This is a singleton
     *
     * @return
     */
    public static LinkFilterController getInstance() {
        return LinkFilterController.INSTANCE;
    }

    public static LinkFilterController createEmptyTestInstance() {
        return new LinkFilterController(true);
    }

    private volatile ArrayList<LinkgrabberFilterRule>             filter;
    private final LinkFilterSettings                              config;
    private volatile java.util.List<LinkgrabberFilterRuleWrapper> denyFileFilter;

    public java.util.List<LinkgrabberFilterRuleWrapper> getAcceptFileFilter() {
        return acceptFileFilter;
    }

    public java.util.List<LinkgrabberFilterRuleWrapper> getAcceptUrlFilter() {
        return acceptUrlFilter;
    }

    private volatile java.util.List<LinkgrabberFilterRuleWrapper> acceptFileFilter;
    private volatile java.util.List<LinkgrabberFilterRuleWrapper> denyUrlFilter;
    private volatile java.util.List<LinkgrabberFilterRuleWrapper> acceptUrlFilter;
    private final KeyHandler<Object>                              filterListHandler;

    private final ChangeEventSender                               eventSender;
    private final GenericConfigEventListener<Object>              eventHandler;
    private final boolean                                         testInstance;

    /**
     * Create a new instance of LinkFilterController. This is a singleton class. Access the only existing instance by using
     * {@link #getInstance()}.
     */
    public LinkFilterController(boolean testInstance) {
        eventSender = new ChangeEventSender();
        this.testInstance = testInstance;
        if (isTestInstance() == false) {
            config = JsonConfig.create(LinkFilterSettings.class);
            filterListHandler = config._getStorageHandler().getKeyHandler("FilterList");
            filter = readConfig();
            filterListHandler.getEventSender().addListener(eventHandler = new GenericConfigEventListener<Object>() {

                @Override
                public void onConfigValueModified(KeyHandler<Object> keyHandler, Object newValue) {
                    filter = readConfig();
                    TaskQueue.getQueue().add(new QueueAction<Void, RuntimeException>() {

                        @Override
                        protected Void run() throws RuntimeException {
                            updateInternal();
                            return null;
                        }

                    });
                }

                @Override
                public void onConfigValidatorError(KeyHandler<Object> keyHandler, Object invalidValue, ValidationException validateException) {
                }
            });
            ShutdownController.getInstance().addShutdownEvent(new ShutdownEvent() {

                @Override
                public void onShutdown(final ShutdownRequest shutdownRequest) {
                    save(filter);
                }

                @Override
                public long getMaxDuration() {
                    return 0;
                }

                @Override
                public String toString() {
                    return "save filters...";
                }
            });
            updateInternal();
        } else {
            this.config = null;
            filterListHandler = null;
            eventHandler = null;
            filter = new ArrayList<LinkgrabberFilterRule>();
        }
    }

    public ChangeEventSender getEventSender() {
        return eventSender;
    }

    private ArrayList<LinkgrabberFilterRule> readConfig() {
        final ArrayList<LinkgrabberFilterRule> newList = new ArrayList<LinkgrabberFilterRule>();
        if (config != null) {
            ArrayList<LinkgrabberFilterRule> filter = config.getFilterList();
            if (filter == null) {
                filter = new ArrayList<LinkgrabberFilterRule>();
            }
            boolean dupesView = false;
            boolean offlineRule = false;
            boolean directHttpView = false;
            HashSet<String> dupefinder = new HashSet<String>();

            for (LinkgrabberFilterRule rule : filter) {
                LinkgrabberFilterRule clone = JSonStorage.restoreFromString(JSonStorage.serializeToJson(rule), new TypeRef<LinkgrabberFilterRule>() {
                });
                clone.setCreated(-1);
                if (!dupefinder.add(JSonStorage.serializeToJson(clone))) {
                    //
                    continue;
                }
                if (OfflineView.ID.equals(rule.getId())) {
                    OfflineView r;
                    newList.add(r = new OfflineView());
                    r.init();
                    r.setEnabled(rule.isEnabled());
                    offlineRule = true;
                    continue;

                }
                if (DirectHTTPView.ID.equals(rule.getId())) {
                    DirectHTTPView r;
                    newList.add(r = new DirectHTTPView());
                    r.init();
                    r.setEnabled(rule.isEnabled());
                    directHttpView = true;
                    continue;

                }
                if (DupesView.ID.equals(rule.getId())) {
                    DupesView r;
                    newList.add(r = new DupesView());
                    r.init();
                    r.setEnabled(rule.isEnabled());
                    dupesView = true;
                    continue;

                }
                newList.add(rule);
            }
            if (!directHttpView) {
                newList.add(new DirectHTTPView().init());
            }
            if (!offlineRule) {
                newList.add(new OfflineView().init());
            }
            if (!dupesView) {
                newList.add(new DupesView().init());
            }
        }
        return newList;
    }

    private void updateInternal() {
        // url filter only require the urls, and thus can be done
        // brefore
        // linkcheck
        ArrayList<LinkgrabberFilterRuleWrapper> newdenyUrlFilter = new ArrayList<LinkgrabberFilterRuleWrapper>();
        ArrayList<LinkgrabberFilterRuleWrapper> newacceptUrlFilter = new ArrayList<LinkgrabberFilterRuleWrapper>();

        // FIlefilters require the full file information available after
        // linkcheck
        ArrayList<LinkgrabberFilterRuleWrapper> newdenyFileFilter = new ArrayList<LinkgrabberFilterRuleWrapper>();
        ArrayList<LinkgrabberFilterRuleWrapper> newacceptFileFilter = new ArrayList<LinkgrabberFilterRuleWrapper>();

        for (LinkgrabberFilterRule lgr : filter) {
            if (lgr.isEnabled() && lgr.isValid()) {

                LinkgrabberFilterRuleWrapper compiled = lgr.compile();
                if (lgr.isAccept()) {
                    if (!compiled.isRequiresLinkcheck()) {
                        newacceptUrlFilter.add(compiled);
                    } else {
                        newacceptFileFilter.add(compiled);
                    }
                } else {
                    if (!compiled.isRequiresLinkcheck()) {
                        newdenyUrlFilter.add(compiled);
                    } else {
                        newdenyFileFilter.add(compiled);
                    }
                }
            }
        }

        newdenyUrlFilter.trimToSize();

        denyUrlFilter = newdenyUrlFilter;

        newacceptUrlFilter.trimToSize();

        acceptUrlFilter = newacceptUrlFilter;

        newdenyFileFilter.trimToSize();

        denyFileFilter = newdenyFileFilter;

        newacceptFileFilter.trimToSize();

        acceptFileFilter = newacceptFileFilter;
        getEventSender().fireEvent(new ChangeEvent(LinkFilterController.this));
    }

    public void update() {
        if (isTestInstance()) {
            updateInternal();
        } else {
            TaskQueue.getQueue().add(new QueueAction<Void, RuntimeException>() {

                @Override
                protected Void run() throws RuntimeException {
                    updateInternal();
                    return null;
                }

            });
        }
    }

    public boolean isTestInstance() {
        return testInstance;
    }

    public java.util.List<LinkgrabberFilterRule> list() {
        synchronized (this) {
            return new ArrayList<LinkgrabberFilterRule>(filter);
        }
    }

    public void addAll(java.util.List<LinkgrabberFilterRule> all) {
        if (all == null) {
            return;
        }
        synchronized (this) {
            filter.addAll(all);
            save(filter);
            update();
        }
    }

    private synchronized final void save(ArrayList<LinkgrabberFilterRule> filter) {
        if (config != null) {
            if (filterListHandler != null) {
                filterListHandler.getEventSender().removeListener(eventHandler);
                try {
                    config.setFilterList(filter);
                } finally {
                    if (!ShutdownController.getInstance().isShuttingDown()) {
                        filterListHandler.getEventSender().addListener(eventHandler);
                    }
                }
            } else {
                config.setFilterList(filter);
            }
        }
    }

    public void add(LinkgrabberFilterRule linkFilter) {
        if (linkFilter == null) {
            return;
        }
        synchronized (this) {
            filter.add(linkFilter);
            save(filter);
        }
        update();
    }

    public void remove(LinkgrabberFilterRule lf) {
        if (lf == null) {
            return;
        }
        synchronized (this) {
            filter.remove(lf);
            save(filter);
        }
        update();
    }

    public boolean dropByUrl(CrawledLink link) {
        if (link.getMatchingFilter() != null) {
            /*
             * links with set matching filtered are allowed, user wants to add them
             */
            return false;
        }
        if (isTestInstance() == false && !org.jdownloader.settings.staticreferences.CFG_LINKFILTER.LINK_FILTER_ENABLED.isEnabled()) {
            return false;
        }
        LinkgrabberFilterRule matchingFilter = null;
        final java.util.List<LinkgrabberFilterRuleWrapper> localdenyUrlFilter = denyUrlFilter;
        if (localdenyUrlFilter.size() > 0) {
            for (LinkgrabberFilterRuleWrapper lgr : localdenyUrlFilter) {
                try {
                    if (!lgr.checkHoster(link)) {
                        continue;
                    }
                    if (!lgr.checkPluginStatus(link)) {
                        continue;
                    }
                } catch (NoDownloadLinkException e) {
                    continue;
                }
                if (!isTestInstance()) {
                    if (!lgr.checkOrigin(link)) {
                        continue;
                    }
                    if (!lgr.checkConditions(link)) {
                        continue;
                    }
                }
                if (!lgr.checkSource(link)) {
                    continue;
                }
                if (!lgr.checkFileType(link)) {
                    continue;
                }
                matchingFilter = lgr.getRule();
                break;
            }
        }
        // no deny filter match. We can return here
        if (matchingFilter == null) {
            return false;
        } else {
            link.setMatchingFilter(matchingFilter);
            return true;
        }
    }

    public boolean dropByFileProperties(CrawledLink link) {
        if (link.getMatchingFilter() != null && link.getMatchingFilter() instanceof LinkgrabberFilterRule && !((LinkgrabberFilterRule) link.getMatchingFilter()).isAccept()) {
            /*
             * links with set matching filtered are allowed, user wants to add them
             */
            return false;
        }
        if (isTestInstance() == false && !org.jdownloader.settings.staticreferences.CFG_LINKFILTER.LINK_FILTER_ENABLED.isEnabled()) {
            return false;
        }
        if (link.getDownloadLink() == null) {
            throw new WTFException();
        }
        LinkgrabberFilterRule matchedFilter = null;
        final java.util.List<LinkgrabberFilterRuleWrapper> localdenyFileFilter = denyFileFilter;
        if (localdenyFileFilter.size() > 0) {
            for (LinkgrabberFilterRuleWrapper lgr : localdenyFileFilter) {
                try {
                    if (!lgr.checkHoster(link)) {
                        continue;
                    }
                    if (!lgr.checkPluginStatus(link)) {
                        continue;
                    }
                } catch (NoDownloadLinkException e) {
                    throw new WTFException();
                }
                if (!isTestInstance()) {
                    if (!lgr.checkOrigin(link)) {
                        continue;
                    }
                    if (!lgr.checkConditions(link)) {
                        continue;
                    }
                }
                if (!lgr.checkSource(link)) {
                    continue;
                }
                if (!lgr.checkOnlineStatus(link)) {
                    continue;
                }
                if (!lgr.checkFileName(link)) {
                    continue;
                }
                if (!lgr.checkPackageName(link)) {
                    continue;
                }
                if (!lgr.checkFileSize(link)) {
                    continue;
                }
                if (!lgr.checkFileType(link)) {
                    continue;
                }
                matchedFilter = lgr.getRule();
                break;
            }
        }
        if (matchedFilter == null) {
            return dropByUrl(link);
        } else {
            link.setMatchingFilter(matchedFilter);
            return true;
        }
    }

    public java.util.List<LinkgrabberFilterRule> listFilters() {
        synchronized (this) {
            final List<LinkgrabberFilterRule> lst = filter;
            final List<LinkgrabberFilterRule> ret = new ArrayList<LinkgrabberFilterRule>();
            for (final LinkgrabberFilterRule l : lst) {
                if (!l.isAccept()) {
                    ret.add(l);
                }
            }
            return ret;
        }
    }

    public java.util.List<LinkgrabberFilterRule> listExceptions() {
        synchronized (this) {
            final List<LinkgrabberFilterRule> lst = filter;
            final List<LinkgrabberFilterRule> ret = new ArrayList<LinkgrabberFilterRule>();
            for (final LinkgrabberFilterRule l : lst) {
                if (l.isAccept()) {
                    ret.add(l);
                }
            }
            return ret;
        }
    }
}
