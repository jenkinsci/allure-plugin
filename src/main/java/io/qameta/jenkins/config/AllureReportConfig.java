package io.qameta.jenkins.config;

import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author eroshenkoam (Artem Eroshenko)
 * @author charlie (Dmitry Baev)
 */
public class AllureReportConfig implements Serializable {

    private final String jdk;

    private final String commandline;

    private final List<String> resultsPaths;

    private final List<PropertyConfig> properties;

    private final ReportBuildPolicy reportBuildPolicy;

    private final boolean includeProperties;

    @DataBoundConstructor
    public AllureReportConfig(String jdk, String commandline, List<PropertyConfig> properties,
                              ReportBuildPolicy reportBuildPolicy, Boolean includeProperties, List<String> resultsPaths) {
        this.jdk = jdk;
        this.properties = properties;
        this.commandline = commandline;
        this.resultsPaths = Objects.isNull(resultsPaths) ? Collections.emptyList() : resultsPaths;
        this.reportBuildPolicy = Objects.isNull(reportBuildPolicy) ? ReportBuildPolicy.ALWAYS : reportBuildPolicy;
        this.includeProperties = Objects.isNull(includeProperties) ? false : includeProperties;
    }

    @Nullable
    public String getJdk() {
        return jdk;
    }

    @Nullable
    public String getCommandline() {
        return commandline;
    }

    @Nonnull
    public List<String> getResultsPaths() {
        return resultsPaths;
    }

    @Nonnull
    public String getResultsPathsAsNewLineString() {
        return resultsPaths.stream().collect(Collectors.joining("\n"));
    }

    @Nullable
    public List<PropertyConfig> getProperties() {
        return properties;
    }

    @Nonnull
    public ReportBuildPolicy getReportBuildPolicy() {
        return reportBuildPolicy;
    }

    public boolean getIncludeProperties() {
        return includeProperties;
    }

    public static AllureReportConfig newInstance(List<String> paths) {
        return new AllureReportConfig(
                null,
                null,
                new ArrayList<>(),
                ReportBuildPolicy.ALWAYS,
                true,
                paths
        );
    }

}
