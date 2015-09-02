package ru.yandex.qatools.allure.jenkins.config;

import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * eroshenkoam
 * 30/07/14
 */
public class AllureReportConfig implements Serializable {

    private final String jdk;

    private final String commandlineName;

    private final String resultsPattern;

    private final String reportVersionCustom;

    private final ReportBuildPolicy reportBuildPolicy;

    private final ReportVersionPolicy reportVersionPolicy;

    private final Boolean includeProperties;

    @DataBoundConstructor
    public AllureReportConfig(String jdk, String commandlineName, String resultsPattern, String reportVersionCustom,
                              ReportVersionPolicy reportVersionPolicy, ReportBuildPolicy reportBuildPolicy, Boolean includeProperties) {
        this.jdk = jdk;
        this.commandlineName = commandlineName;
        this.reportVersionPolicy = reportVersionPolicy;
        this.reportVersionCustom = reportVersionCustom;
        this.reportBuildPolicy = reportBuildPolicy;
        this.resultsPattern = resultsPattern;
        this.includeProperties = includeProperties;
    }

    public String getJdk() {
        return jdk;
    }

    public boolean hasJdk() {
        return isNotBlank(getJdk());
    }

    public String getCommandlineName() {
        return commandlineName;
    }

    public String getResultsPattern() {
        return resultsPattern;
    }

    public String getReportVersionCustom() {
        return reportVersionCustom;
    }

    public ReportVersionPolicy getReportVersionPolicy() {
        return reportVersionPolicy;
    }

    public ReportBuildPolicy getReportBuildPolicy() {
        return reportBuildPolicy;
    }

    public boolean getIncludeProperties() {
        return includeProperties == null || includeProperties;
    }

    public static AllureReportConfig newInstance(String resultsMask, boolean alwaysGenerate) {
        return new AllureReportConfig(null, null, resultsMask, null, ReportVersionPolicy.DEFAULT,
                alwaysGenerate ? ReportBuildPolicy.ALWAYS : ReportBuildPolicy.UNSTABLE, true);
    }
}
