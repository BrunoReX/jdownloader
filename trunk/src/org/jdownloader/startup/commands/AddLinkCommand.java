package org.jdownloader.startup.commands;

import java.io.File;
import java.util.Arrays;

import jd.controlling.linkcollector.LinkCollectingJob;
import jd.controlling.linkcollector.LinkCollector;
import jd.controlling.linkcollector.LinkOrigin;
import jd.controlling.linkcollector.LinkOriginDetails;

import org.appwork.utils.StringUtils;

public class AddLinkCommand extends AbstractStartupCommand {

    public AddLinkCommand() {
        super("add-links", "add-link", "a");
    }

    @Override
    public void run(String command, String... parameters) {
        logger.info("AddLinkCommand: " + Arrays.toString(parameters));
        for (final String parameter : parameters) {
            add(LinkOrigin.START_PARAMETER, parameter);
        }
    }

    public static boolean add(final LinkOrigin linkOrigin, final String parameter) {
        if (StringUtils.isNotEmpty(parameter)) {
            try {
                final LinkCollectingJob job;
                if (StringUtils.startsWithCaseInsensitive(parameter, "http")) {
                    job = new LinkCollectingJob(new LinkOriginDetails(linkOrigin, null), parameter);
                } else if (StringUtils.startsWithCaseInsensitive(parameter, "file:/")) {
                    job = new LinkCollectingJob(new LinkOriginDetails(linkOrigin, null), parameter);
                } else {
                    job = new LinkCollectingJob(new LinkOriginDetails(linkOrigin, null), new File(parameter).toURI().toString());
                }
                LinkCollector.getInstance().addCrawlerJob(job);
                return true;
            } catch (final Throwable e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    @Override
    public String getParameterHelp() {
        return "<Link1> <Link2> ... <Link*>";
    }

    @Override
    public String getDescription() {
        return "Add Links to the LinkGrabber";
    }

}
