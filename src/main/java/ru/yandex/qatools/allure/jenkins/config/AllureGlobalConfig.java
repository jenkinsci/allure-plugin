package ru.yandex.qatools.allure.jenkins.config;

import java.io.Serializable;

/**
 * eroshenkoam
 * 19/11/14
 */
public class AllureGlobalConfig implements Serializable {

    private String resultsPatternDefault;

    private String reportVersionDefault;

    private String issuesTrackerPatternDefault;

    private String tmsPatternDefault;

    public String getResultsPatternDefault() {
        return resultsPatternDefault;
    }

    public void setResultsPatternDefault(String resultsPatternDefault) {
        this.resultsPatternDefault = resultsPatternDefault;
    }

    public String getReportVersionDefault() {
        return reportVersionDefault;
    }

    public void setReportVersionDefault(String reportVersionDefault) {
        this.reportVersionDefault = reportVersionDefault;
    }

    public String getIssuesTrackerPatternDefault() {
        return issuesTrackerPatternDefault;
    }

    public void setIssuesTrackerPatternDefault(String issuesTrackerPatternDefault) {
        this.issuesTrackerPatternDefault = issuesTrackerPatternDefault;
    }

    public String getTmsPatternDefault() {
        return tmsPatternDefault;
    }

    public void setTmsPatternDefault(String tmsPatternDefault) {
        this.tmsPatternDefault = tmsPatternDefault;
    }

    public static AllureGlobalConfig newInstance(String resultsPatternDefault, String reportVersionDefault) {
        AllureGlobalConfig allureGlobalConfig = new AllureGlobalConfig();
        allureGlobalConfig.setResultsPatternDefault(resultsPatternDefault);
        allureGlobalConfig.setReportVersionDefault(reportVersionDefault);
        return allureGlobalConfig;
    }
}
