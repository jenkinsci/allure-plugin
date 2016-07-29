package ru.yandex.qatools.allure.jenkins.config;

import com.google.common.base.Joiner;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;
import java.util.ArrayList;
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

    private List<PropertyConfig> properties;

    private ReportBuildPolicy reportBuildPolicy;

    private Boolean includeProperties;

    @DataBoundConstructor
    public AllureReportConfig(String jdk, String commandline, String resultsPattern, List<PropertyConfig> properties,
                              ReportBuildPolicy reportBuildPolicy, Boolean includeProperties) {
        this.jdk = jdk;
        this.properties = properties;
        this.commandline = commandline;
        this.resultsPattern = resultsPattern;
        this.reportBuildPolicy = reportBuildPolicy;
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

    public List<String> getResultsPaths() {
        return Arrays.asList(resultsPattern.split("\\n"));
    }

    public List<PropertyConfig> getProperties() {
        if (properties == null) {
            properties = new ArrayList<>();
        }
        return properties;
    }

    public void setProperties(List<PropertyConfig> properties) {
        this.properties = properties;
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

    public static AllureReportConfig newInstance() {
        return new AllureReportConfig(null, null, null, new ArrayList<PropertyConfig>(), ReportBuildPolicy.ALWAYS, true);
    }

    public static AllureReportConfig newInstance(String paths) {
        return new AllureReportConfig(null, null, paths, new ArrayList<PropertyConfig>(), ReportBuildPolicy.ALWAYS, true);
    }

    public static AllureReportConfig newInstance(List<String> paths) {
        return new AllureReportConfig(null, null, Joiner.on("\n").join(paths),
                new ArrayList<PropertyConfig>(), ReportBuildPolicy.ALWAYS, true);
    }

}
