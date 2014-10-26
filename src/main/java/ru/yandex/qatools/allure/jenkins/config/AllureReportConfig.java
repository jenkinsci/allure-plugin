package ru.yandex.qatools.allure.jenkins.config;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * eroshenkoam
 * 30/07/14
 */
public class AllureReportConfig {

    private final String resultsPattern;

    private final String reportVersionCustom;

    private final ReportBuildPolicy reportBuildPolicy;

    private final ReportVersionPolicy reportVersionPolicy;

    private final Boolean includeProperties;

    private final Boolean agregateMatrix;

    @DataBoundConstructor
    public AllureReportConfig(String resultsPattern, String reportVersionCustom,
                              ReportVersionPolicy reportVersionPolicy, ReportBuildPolicy reportBuildPolicy,
                              Boolean includeProperties, Boolean agregateMatrix) {

        this.reportVersionPolicy = reportVersionPolicy;
        this.reportVersionCustom = reportVersionCustom;
        this.reportBuildPolicy = reportBuildPolicy;
        this.resultsPattern = resultsPattern;
        this.includeProperties = includeProperties;
        this.agregateMatrix = agregateMatrix;
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

    public boolean getAgregateMatrix() {
        return agregateMatrix == null || agregateMatrix;
    }

    public static AllureReportConfig newInstance(String resultsMask, boolean alwaysGenerate) {
        return new AllureReportConfig(resultsMask, null, ReportVersionPolicy.DEFAULT,
                alwaysGenerate ? ReportBuildPolicy.ALWAYS : ReportBuildPolicy.UNSTABLE, true, true);
    }
}
