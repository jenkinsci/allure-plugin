package ru.yandex.qatools.allure.jenkins.config;

import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import static org.apache.commons.lang.StringUtils.isNotBlank;

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
        this.includeProperties = includeProperties;
        this.resultsPattern = resultsPattern;
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

    public List<String> getResultsPaths() {
        return Arrays.asList(resultsPattern.split("\\n"));
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

    public static AllureReportConfig newInstance(String resultsMask) {
        return new AllureReportConfig(null, null, resultsMask, ReportBuildPolicy.ALWAYS, true);
    }
}
