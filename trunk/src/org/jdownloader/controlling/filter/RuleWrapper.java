package org.jdownloader.controlling.filter;

import java.util.regex.Pattern;

import jd.controlling.linkcollector.LinkCollectingJob;
import jd.controlling.linkcollector.LinkOriginDetails;
import jd.controlling.linkcrawler.CrawledLink;
import jd.controlling.linkcrawler.LinkCrawler;
import jd.plugins.DownloadLink;
import jd.plugins.LinkInfo;

import org.appwork.utils.StringUtils;
import org.jdownloader.myjdownloader.client.json.AvailableLinkState;

public class RuleWrapper<T extends FilterRule> {

    protected CompiledRegexFilter      fileNameRule;
    protected boolean                  requiresLinkcheck = false;
    private CompiledPluginStatusFilter pluginStatusFilter;
    private BooleanFilter              alwaysFilter;
    private CompiledOriginFilter       originFilter;
    private CompiledRegexFilter        packageNameRule;
    private CompiledConditionFilter    conditionFilter;

    public CompiledPluginStatusFilter getPluginStatusFilter() {
        return pluginStatusFilter;
    }

    public RuleWrapper(T rule2) {
        this.rule = rule2;

        if (rule.getPluginStatusFilter().isEnabled()) {
            pluginStatusFilter = new CompiledPluginStatusFilter(rule.getPluginStatusFilter());
            requiresHoster = true;
        }

        if (rule.getOnlineStatusFilter().isEnabled()) {
            onlineStatusFilter = new CompiledOnlineStatusFiler(rule.getOnlineStatusFilter());
            requiresLinkcheck = true;
        }

        if (rule.getFilenameFilter().isEnabled()) {
            fileNameRule = new CompiledRegexFilter(rule.getFilenameFilter());
            requiresLinkcheck = true;
        }
        if (rule.getPackagenameFilter().isEnabled()) {
            packageNameRule = new CompiledRegexFilter(rule.getPackagenameFilter());
            // requiresLinkcheck = true;
        }
        if (rule.getFilesizeFilter().isEnabled()) {
            filesizeRule = new CompiledFilesizeFilter(rule.getFilesizeFilter());
            requiresLinkcheck = true;
        }
        if (rule.getFiletypeFilter().isEnabled()) {
            filetypeFilter = new CompiledFiletypeFilter(rule.getFiletypeFilter());
            requiresLinkcheck = true;
        }

        if (rule.getHosterURLFilter().isEnabled()) {
            hosterRule = new CompiledRegexFilter(rule.getHosterURLFilter());
            requiresHoster = true;
        }
        if (rule.getSourceURLFilter().isEnabled()) {
            sourceRule = new CompiledRegexFilter(rule.getSourceURLFilter());
        }

        if (rule.getOriginFilter().isEnabled()) {
            originFilter = new CompiledOriginFilter(rule.getOriginFilter());
        }

        if (rule.getConditionFilter().isEnabled()) {
            conditionFilter = new CompiledConditionFilter(rule.getConditionFilter());
        }
        if (rule.getMatchAlwaysFilter().isEnabled()) {
            alwaysFilter = rule.getMatchAlwaysFilter();
            // overwrites all others
            requiresHoster = false;
            requiresLinkcheck = false;
        }
    }

    public CompiledConditionFilter getConditionFilter() {
        return conditionFilter;
    }

    public CompiledOriginFilter getOriginFilter() {
        return originFilter;
    }

    public BooleanFilter getAlwaysFilter() {
        return alwaysFilter;
    }

    public CompiledRegexFilter getFileNameRule() {
        return fileNameRule;
    }

    public CompiledRegexFilter getPackageNameRule() {
        return packageNameRule;
    }

    public boolean isRequiresLinkcheck() {
        return requiresLinkcheck;
    }

    public boolean isRequiresHoster() {
        return requiresHoster;
    }

    public CompiledRegexFilter getHosterRule() {
        return hosterRule;
    }

    public CompiledRegexFilter getSourceRule() {
        return sourceRule;
    }

    public CompiledFilesizeFilter getFilesizeRule() {
        return filesizeRule;
    }

    public CompiledFiletypeFilter getFiletypeFilter() {
        return filetypeFilter;
    }

    protected boolean                   requiresHoster = false;
    protected CompiledRegexFilter       hosterRule;
    protected CompiledRegexFilter       sourceRule;
    protected CompiledFilesizeFilter    filesizeRule;
    protected CompiledFiletypeFilter    filetypeFilter;
    protected T                         rule;
    protected CompiledOnlineStatusFiler onlineStatusFilter;

    public T getRule() {
        return rule;
    }

    public CompiledOnlineStatusFiler getOnlineStatusFilter() {
        return onlineStatusFilter;
    }

    public static Pattern createPattern(String regex, boolean useRegex) {
        if (useRegex) {
            return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        } else {
            String[] parts = regex.split("\\*+");
            StringBuilder sb = new StringBuilder();
            if (regex.startsWith("*")) {
                sb.append("(.*)");
            }
            int actualParts = 0;
            for (int i = 0; i < parts.length; i++) {

                if (parts[i].length() != 0) {
                    if (actualParts > 0) {
                        sb.append("(.*?)");
                    }
                    sb.append(Pattern.quote(parts[i]));
                    actualParts++;
                }
            }
            if (sb.length() == 0) {
                sb.append("(.*?)");
            } else {
                if (regex.endsWith("*")) {
                    sb.append("(.*)");
                }

            }
            return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        }
    }

    public boolean checkFileType(final CrawledLink link) {
        final CompiledFiletypeFilter filetypeFilter = getFiletypeFilter();
        if (filetypeFilter != null) {
            final LinkInfo linkInfo = link.getLinkInfo();
            return filetypeFilter.matches(linkInfo.getExtension().name(), linkInfo);
        }
        return true;
    }

    public boolean checkFileSize(final CrawledLink link) {
        final CompiledFilesizeFilter fileSizeRule = getFilesizeRule();
        if (fileSizeRule != null) {
            if (link.getLinkState() != AvailableLinkState.ONLINE) {
                return false;
            } else {
                return fileSizeRule.matches(link.getSize());
            }
        }
        return true;
    }

    public boolean checkPackageName(final CrawledLink link) {
        final CompiledRegexFilter packageNameRule = getPackageNameRule();
        if (packageNameRule != null) {
            String packagename = null;
            if (link != null) {
                if (link.getParentNode() != null) {
                    packagename = link.getParentNode().getName();
                }
                if (StringUtils.isEmpty(packagename) && link.getDesiredPackageInfo() != null) {
                    packagename = link.getDesiredPackageInfo().getName();
                }
            }
            if (StringUtils.isEmpty(packagename)) {
                return false;
            } else {
                return packageNameRule.matches(packagename);
            }
        }
        return true;
    }

    public boolean checkFileName(final CrawledLink link) {
        final CompiledRegexFilter fileNameRule = getFileNameRule();
        if (fileNameRule != null) {
            if (link.getLinkState() != AvailableLinkState.ONLINE) {
                return false;
            } else {
                return fileNameRule.matches(link.getName());
            }
        }
        return true;
    }

    public boolean checkHoster(final CrawledLink link) throws NoDownloadLinkException {
        final CompiledRegexFilter hosterRule = getHosterRule();
        if (hosterRule != null) {
            final DownloadLink dlLink = link.getDownloadLink();
            if (dlLink == null || link.gethPlugin() == null) {
                throw new NoDownloadLinkException();
            } else {
                final String host = dlLink.getHost();
                switch (hosterRule.getMatchType()) {
                case CONTAINS:
                case EQUALS:
                    return (host != null && hosterRule.matches(host)) || hosterRule.matches(dlLink.getContentUrlOrPatternMatcher());
                case CONTAINS_NOT:
                case EQUALS_NOT:
                    return (host == null || hosterRule.matches(host)) && hosterRule.matches(dlLink.getContentUrlOrPatternMatcher());
                }
            }
        }
        return true;
    }

    public boolean checkSource(CrawledLink link) {
        if (getSourceRule() != null) {
            String[] sources = link.getSourceUrls();
            int i = 1;
            String pattern = getSourceRule().getPattern().pattern();
            boolean indexed = pattern.matches("^\\-?\\d+\\\\\\. .+");
            boolean inverted = pattern.startsWith("-");
            if (sources == null || sources.length == 0) {
                /* the first link never has sourceURLs */
                sources = new String[2];
                sources[0] = LinkCrawler.cleanURL(link.getURL());
                LinkCollectingJob job = link.getSourceJob();
                if (job != null) {
                    sources[1] = LinkCrawler.cleanURL(job.getCustomSourceUrl());
                }
            }
            for (int j = inverted ? 0 : sources.length - 1; (inverted ? (j < sources.length) : (j >= 0)); j = (inverted ? (j + 1) : (j - 1))) {
                String url = sources[j];
                if (url == null) {
                    continue;
                }
                String toMatch = indexed ? (inverted ? "-" : "") + (i++) + ". " + url : url;
                if (getSourceRule().matches(toMatch)) {
                    return true;
                } else if (indexed) {
                    // for equals matchtypes, we need to ignore the index
                    switch (getSourceRule().getMatchType()) {
                    case EQUALS:
                    case EQUALS_NOT:
                        if (getSourceRule().matches(url)) {
                            return true;
                        }
                    default:
                        // nothing
                    }
                }
            }
            return false;
        }
        return true;
    }

    public boolean checkOnlineStatus(final CrawledLink link) {
        final AvailableLinkState linkState = link.getLinkState();
        if (AvailableLinkState.UNKNOWN == linkState) {
            return false;
        }
        final CompiledOnlineStatusFiler onlineStatusFilter = getOnlineStatusFilter();
        if (onlineStatusFilter != null) {
            return onlineStatusFilter.matches(linkState);
        }
        return true;
    }

    public boolean checkConditions(final CrawledLink link) {
        final CompiledConditionFilter conditionFiler = getConditionFilter();
        if (conditionFiler != null) {
            return conditionFiler.matches(link);
        }
        return true;
    }

    public boolean checkOrigin(final CrawledLink link) {
        final CompiledOriginFilter originFiler = getOriginFilter();
        if (originFiler != null) {
            final LinkOriginDetails origin = link.getOrigin();
            if (origin == null) {
                return false;
            } else {
                return originFiler.matches(origin.getOrigin());
            }
        }
        return true;
    }

    public boolean checkPluginStatus(final CrawledLink link) throws NoDownloadLinkException {
        final CompiledPluginStatusFilter pluginStatusFilter = getPluginStatusFilter();
        if (pluginStatusFilter != null) {
            if (link.getDownloadLink() == null || link.gethPlugin() == null) {
                throw new NoDownloadLinkException();
            } else {
                return pluginStatusFilter.matches(link);
            }
        }
        return true;
    }

    public String getName() {
        return rule.getName();
    }

    public boolean isEnabled() {
        return rule.isEnabled();
    }

}
