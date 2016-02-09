package jd.controlling.linkcollector;

import jd.controlling.downloadcontroller.DownloadController;
import jd.controlling.linkcrawler.CrawledLink;

import org.jdownloader.translate._JDT;

public enum VariousCrawledLinkFlags implements MatchesInterface<CrawledLink> {
    DOWNLOAD_LIST_DUPE(_JDT.T.DOWNLOAD_LIST_DUPE()) {
        public boolean matches(CrawledLink link) {
            return link != null && DownloadController.getInstance().hasDownloadLinkByID(link.getLinkID());
        }

    };

    private final String translation;

    private VariousCrawledLinkFlags(String translation) {
        this.translation = translation;
    }

    public String getTranslation() {
        return translation;
    }

}
