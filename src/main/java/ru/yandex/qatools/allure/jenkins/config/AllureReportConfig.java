package ru.yandex.qatools.allure.jenkins.config;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * eroshenkoam.
 * 30/07/14
 */
public class AllureReportConfig implements Serializable {

    private String jdk;

    private String commandline;

    /**
     * @deprecated Please, someone write why is this deprecated?
     */
    @Deprecated
    private String resultsPattern;

    private List<PropertyConfig> properties = new ArrayList<>();

    private List<ResultsConfig> results;

    private ReportBuildPolicy reportBuildPolicy = ReportBuildPolicy.ALWAYS;

    private Boolean includeProperties = Boolean.FALSE;

    @DataBoundConstructor
    public AllureReportConfig(List<ResultsConfig> results) {
        this.results = results == null ? Collections.<ResultsConfig>emptyList() : results;
    }

    @DataBoundSetter
    public void setJdk(final String jdk) {
        this.jdk = jdk;
    }

    public String getJdk() {
        return jdk;
    }

    @DataBoundSetter
    public void setCommandline(String commandline) {
        this.commandline = commandline;
    }

    public String getCommandline() {
        return commandline;
    }

    @Nonnull
    @SuppressWarnings("deprecation")
    public List<ResultsConfig> getResults() {
        if (StringUtils.isNotBlank(this.resultsPattern)) {
            this.results = convertPaths(this.resultsPattern);
            this.resultsPattern = null;
        }
        return results;
    }

    public List<PropertyConfig> getProperties() {
        return this.properties;
    }

    @DataBoundSetter
    public void setProperties(List<PropertyConfig> properties) {
        this.properties = properties;
    }

    public ReportBuildPolicy getReportBuildPolicy() {
        return this.reportBuildPolicy;
    }

    @DataBoundSetter
    public void setReportBuildPolicy(ReportBuildPolicy reportBuildPolicy) {
        this.reportBuildPolicy = reportBuildPolicy;
    }

    @DataBoundSetter
    public void setIncludeProperties(Boolean includeProperties) {
        this.includeProperties = includeProperties;
    }

    public boolean getIncludeProperties() {
        return includeProperties;
    }

    public static AllureReportConfig newInstance(List<String> results) {

        return newInstance(null, null, results.toArray(new String[]{}));
    }

    public static AllureReportConfig newInstance(String jdk, String commandline, String... paths) {
        return newInstance(jdk, commandline, Arrays.asList(paths));
    }

    private static AllureReportConfig newInstance(String jdk, String commandline, List<String> paths) {
        final List<ResultsConfig> results = convertPaths(paths);
        final AllureReportConfig config = new AllureReportConfig(results);
        config.setJdk(jdk);
        config.setCommandline(commandline);
        config.setIncludeProperties(true);
        return config;
    }

    private static List<ResultsConfig> convertPaths(String paths) {
        return convertPaths(Arrays.asList(paths.split("\\n")));
    }

    private static List<ResultsConfig> convertPaths(List<String> paths) {
        final List<ResultsConfig> results = new ArrayList<>();
        for (String path : paths) {
            results.add(new ResultsConfig(path));
        }
        return results;
    }
}
