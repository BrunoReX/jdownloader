package org.jdownloader.captcha.v2.challenge.recaptcha.v2;

import java.util.ArrayList;

import org.appwork.utils.logging2.LogSource;
import org.jdownloader.captcha.blacklist.BlacklistEntry;
import org.jdownloader.captcha.blacklist.BlockAllCrawlerCaptchasEntry;
import org.jdownloader.captcha.blacklist.BlockCrawlerCaptchasByHost;
import org.jdownloader.captcha.blacklist.BlockCrawlerCaptchasByPackage;
import org.jdownloader.captcha.blacklist.CaptchaBlackList;
import org.jdownloader.captcha.v2.AbstractResponse;
import org.jdownloader.captcha.v2.ChallengeResponseController;
import org.jdownloader.captcha.v2.solverjob.SolverJob;

import jd.controlling.captcha.SkipException;
import jd.controlling.downloadcontroller.SingleDownloadController;
import jd.controlling.linkcollector.LinkCollector;
import jd.controlling.linkcrawler.LinkCrawlerThread;
import jd.http.Browser;
import jd.plugins.CaptchaException;
import jd.plugins.DecrypterException;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;

public class CaptchaHelperCrawlerPluginRecaptchaV2 extends AbstractCaptchaHelperRecaptchaV2<PluginForDecrypt> {

    public CaptchaHelperCrawlerPluginRecaptchaV2(final PluginForDecrypt plugin, final Browser br, final String siteKey, final String secureToken) {
        super(plugin, br, siteKey, secureToken);
    }

    public CaptchaHelperCrawlerPluginRecaptchaV2(final PluginForDecrypt plugin, final Browser br, final String siteKey) {
        this(plugin, br, siteKey, null);
    }

    public CaptchaHelperCrawlerPluginRecaptchaV2(final PluginForDecrypt plugin, final Browser br) {
        this(plugin, br, null);
    }

    public String getToken() throws PluginException, InterruptedException, DecrypterException {
        runDdosPrevention();
        if (Thread.currentThread() instanceof SingleDownloadController) {
            logger.severe("PluginForDecrypt.getCaptchaCode inside SingleDownloadController!?");
        }
        if (siteKey == null) {
            siteKey = getSiteKey();
            if (siteKey == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, "RecaptchaV2 API Key can not be found");
            }
        }
        if (secureToken == null) {
            secureToken = getSecureToken();
            // non fatal if secureToken is null.
        }
        final PluginForDecrypt plugin = getPlugin();
        final RecaptchaV2Challenge c = new RecaptchaV2Challenge(siteKey, secureToken, plugin, br, getSiteDomain(), getSiteUrl());
        c.setTimeout(plugin.getCaptchaTimeout());
        plugin.invalidateLastChallengeResponse();
        final BlacklistEntry<?> blackListEntry = CaptchaBlackList.getInstance().matches(c);
        if (blackListEntry != null) {
            logger.warning("Cancel. Blacklist Matching");
            throw new CaptchaException(blackListEntry);
        }
        ArrayList<SolverJob<String>> jobs = new ArrayList<SolverJob<String>>();
        try {

            jobs.add(ChallengeResponseController.getInstance().handle(c));
            AbstractRecaptcha2FallbackChallenge rcFallback = null;

            while (jobs.size() <= 10) {

                if (rcFallback == null && c.getResult() != null) {
                    for (AbstractResponse<String> r : c.getResult()) {
                        if (r.getChallenge() != null && r.getChallenge() instanceof AbstractRecaptcha2FallbackChallenge) {
                            rcFallback = (AbstractRecaptcha2FallbackChallenge) r.getChallenge();

                            break;

                        }
                    }
                }
                if (rcFallback != null && rcFallback.getToken() == null) {
                    // retry

                    try {
                        rcFallback.reload(jobs.size() + 1);
                    } catch (Throwable e) {
                        LogSource.exception(logger, e);
                        throw new DecrypterException(DecrypterException.CAPTCHA);
                    }
                    runDdosPrevention();
                    jobs.add(ChallengeResponseController.getInstance().handle(rcFallback));
                    if (rcFallback.getToken() != null) {
                        break;
                    }
                } else {
                    break;
                }
            }

            if (!c.isSolved()) {
                throw new DecrypterException(DecrypterException.CAPTCHA);
            }

            if (c.getResult() != null) {
                for (AbstractResponse<String> r : c.getResult()) {
                    if (r.getChallenge() instanceof AbstractRecaptcha2FallbackChallenge) {
                        String token = ((AbstractRecaptcha2FallbackChallenge) r.getChallenge()).getToken();
                        if (token == null) {
                            for (int i = 0; i < jobs.size(); i++) {

                                jobs.get(i).invalidate();

                            }
                        } else {
                            setCorrectAfter(jobs.size());

                            int validateTheLast = getRequiredCorrectAnswersGuess();
                            for (int i = 0; i < jobs.size(); i++) {

                                if (i >= jobs.size() - validateTheLast) {
                                    jobs.get(i).validate();
                                } else {
                                    jobs.get(i).invalidate();
                                }
                            }
                        }
                        return token;
                    }
                }
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
        } finally {
            c.cleanup();
        }
    }

}
