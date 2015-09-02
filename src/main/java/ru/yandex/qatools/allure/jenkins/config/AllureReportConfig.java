package ru.yandex.qatools.allure.jenkins.config;

import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * eroshenkoam
 * 30/07/14
 */
public class AllureReportConfig implements Serializable {

    private String jdk;

    private String commandline;

    private String resultsPattern;

    private ReportBuildPolicy reportBuildPolicy;

    private Boolean includeProperties;

    @DataBoundConstructor
    public AllureReportConfig(String jdk, String commandline, String resultsPattern,
                              ReportBuildPolicy reportBuildPolicy, Boolean includeProperties) {
        this.jdk = jdk;
        this.commandline = commandline;
        this.reportBuildPolicy = reportBuildPolicy;
        this.resultsPattern = resultsPattern;
        this.includeProperties = includeProperties;
    }

    public String getJdk() {
        return jdk;
    }

    public void setJdk(String jdk) {
        this.jdk = jdk;
    }

    public boolean hasJdk() {
        return isNotBlank(getJdk());
    }


    public String getCommandline() {
        return commandline;
    }

    public void setCommandline(String commandline) {
        this.commandline = commandline;
    }

    public String getResultsPattern() {
        return resultsPattern;
    }

    public void setResultsPattern(String resultsPattern) {
        this.resultsPattern = resultsPattern;
    }

    public ReportBuildPolicy getReportBuildPolicy() {
        return reportBuildPolicy;
    }

    public void setReportBuildPolicy(ReportBuildPolicy reportBuildPolicy) {
        this.reportBuildPolicy = reportBuildPolicy;
    }

    public boolean getIncludeProperties() {
        return includeProperties == null || includeProperties;
    }

    public void setIncludeProperties(Boolean includeProperties) {
        this.includeProperties = includeProperties;
    }

    public static AllureReportConfig newInstance(String resultsMask, boolean alwaysGenerate) {
        return new AllureReportConfig(null, null, resultsMask,
                alwaysGenerate ? ReportBuildPolicy.ALWAYS : ReportBuildPolicy.UNSTABLE, true);
    }
}
