package ru.yandex.qatools.allure.jenkins.config;

/**
 * eroshenkoam
 * 19/11/14
 */
public class AllureGlobalConfig {

    private String resultsPatternDefault;

    private String reportVersionDefault;

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

    public static AllureGlobalConfig newInstance(String resultsPatternDefault, String reportVersionDefault) {
        AllureGlobalConfig allureGlobalConfig = new AllureGlobalConfig();
        allureGlobalConfig.setResultsPatternDefault(resultsPatternDefault);
        allureGlobalConfig.setReportVersionDefault(reportVersionDefault);
        return allureGlobalConfig;
    }
}
