package org.jdownloader.captcha.v2.challenge.recaptcha.v2;

import jd.controlling.captcha.SkipException;
import jd.controlling.downloadcontroller.SingleDownloadController;
import jd.controlling.linkcollector.LinkCollector;
import jd.controlling.linkcrawler.CrawledLink;
import jd.controlling.linkcrawler.LinkCrawler;
import jd.controlling.linkcrawler.LinkCrawlerThread;
import jd.http.Browser;
import jd.plugins.CaptchaException;
import jd.plugins.DecrypterException;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;

import org.appwork.utils.logging2.LogSource;
import org.jdownloader.captcha.blacklist.BlacklistEntry;
import org.jdownloader.captcha.blacklist.BlockAllCrawlerCaptchasEntry;
import org.jdownloader.captcha.blacklist.BlockCrawlerCaptchasByHost;
import org.jdownloader.captcha.blacklist.BlockCrawlerCaptchasByPackage;
import org.jdownloader.captcha.blacklist.CaptchaBlackList;
import org.jdownloader.captcha.v2.ChallengeResponseController;

public class CaptchaHelperCrawlerPluginRecaptchaV2 extends AbstractCaptchaHelperRecaptchaV2<PluginForDecrypt> {

    public CaptchaHelperCrawlerPluginRecaptchaV2(PluginForDecrypt plugin, Browser br, String siteKey) {
        super(plugin, br, siteKey);

    }

    public CaptchaHelperCrawlerPluginRecaptchaV2(PluginForDecrypt plugin, Browser br) {
        this(plugin, br, null);
    }

    public String getToken() throws PluginException, InterruptedException, DecrypterException {
        if (Thread.currentThread() instanceof SingleDownloadController) {
            logger.severe("PluginForDecrypt.getCaptchaCode inside SingleDownloadController!?");
        }
        String apiKey = siteKey;
        if (apiKey == null) {
            apiKey = getRecaptchaV2ApiKey();
            if (apiKey == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, "RecaptchaV2 API Key can not be found");
            }
        }
        final PluginForDecrypt plugin = getPlugin();
        final LinkCrawler currentCrawler = plugin.getCrawler();
        final CrawledLink currentOrigin = plugin.getCurrentLink().getOriginLink();
        RecaptchaV2Challenge c = new RecaptchaV2Challenge(apiKey, plugin);
        c.setTimeout(plugin.getCaptchaTimeout());
        plugin.invalidateLastChallengeResponse();
        final BlacklistEntry blackListEntry = CaptchaBlackList.getInstance().matches(c);
        if (blackListEntry != null) {
            logger.warning("Cancel. Blacklist Matching");
            throw new CaptchaException(blackListEntry);
        }
        try {
            ChallengeResponseController.getInstance().handle(c);
            if (!c.isSolved()) {
                throw new DecrypterException(DecrypterException.CAPTCHA);
            }
            if (!c.isCaptchaResponseValid()) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, "Captcha reponse value did not validate!");
            }
            return c.getResult().getValue();
        } catch (InterruptedException e) {
            LogSource.exception(logger, e);
            throw e;
        } catch (SkipException e) {
            LogSource.exception(logger, e);
            switch (e.getSkipRequest()) {
            case BLOCK_ALL_CAPTCHAS:
                CaptchaBlackList.getInstance().add(new BlockAllCrawlerCaptchasEntry(plugin.getCrawler()));
                break;
            case BLOCK_HOSTER:
                CaptchaBlackList.getInstance().add(new BlockCrawlerCaptchasByHost(plugin.getCrawler(), plugin.getHost()));
                break;
            case BLOCK_PACKAGE:
                CaptchaBlackList.getInstance().add(new BlockCrawlerCaptchasByPackage(plugin.getCrawler(), plugin.getCurrentLink()));
                break;
            case REFRESH:
                break;
            case STOP_CURRENT_ACTION:
                if (Thread.currentThread() instanceof LinkCrawlerThread) {
                    LinkCollector.getInstance().abort();
                    // Just to be sure
                    CaptchaBlackList.getInstance().add(new BlockAllCrawlerCaptchasEntry(plugin.getCrawler()));
                }
                break;
            default:
                break;
            }
            throw new CaptchaException(e.getSkipRequest());
        }
    }

}