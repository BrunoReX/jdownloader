package jd.controlling.linkcollector;

import java.util.EventListener;

import jd.controlling.linkcrawler.CrawledLink;

public interface LinkCollectorListener extends EventListener {
    /**
     * abort requested
     *
     * @param event
     */
    void onLinkCollectorAbort(LinkCollectorEvent event);

    /**
     * new filtered stuff is available
     *
     * @param event
     */
    void onLinkCollectorFilteredLinksAvailable(LinkCollectorEvent event);

    /**
     * all filtered stuff is gone
     *
     * @param event
     */
    void onLinkCollectorFilteredLinksEmpty(LinkCollectorEvent event);

    /**
     * only refresh the content data
     *
     * @param event
     */
    void onLinkCollectorDataRefresh(LinkCollectorEvent event);

    /**
     * refresh content structure
     *
     * @param event
     */
    void onLinkCollectorStructureRefresh(LinkCollectorEvent event);

    /**
     * content got removed
     *
     * @param event
     */
    void onLinkCollectorContentRemoved(LinkCollectorEvent event);

    /**
     * content got added
     *
     * @param event
     */
    void onLinkCollectorContentAdded(LinkCollectorEvent event);

    /**
     * New link has been added and grouped.<br>
     * Parameter[0]: (CrawledLink) added Link
     */
    void onLinkCollectorLinkAdded(LinkCollectorEvent event, CrawledLink link);

    /**
     * Dupe link has been added.<br>
     * Parameter[0]: (CrawledLink) added Link
     */
    void onLinkCollectorDupeAdded(LinkCollectorEvent event, CrawledLink link);

    void onLinkCrawlerAdded(LinkCollectorCrawler crawler);

    void onLinkCrawlerStarted(LinkCollectorCrawler crawler);

    void onLinkCrawlerStopped(LinkCollectorCrawler crawler);

    void onLinkCrawlerFinished();

    void onLinkCrawlerNewJob(LinkCollectingJob job);

}