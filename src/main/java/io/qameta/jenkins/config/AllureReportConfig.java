package io.qameta.jenkins.config;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.ExportedBean;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @author eroshenkoam (Artem Eroshenko)
 * @author charlie (Dmitry Baev)
 */
@ExportedBean
public class AllureReportConfig implements Serializable {

    private final String jdk;

    private final String commandline;

    private final List<ResultsConfig> resultsPaths;

    private final List<PropertyConfig> properties;

    private final ReportBuildPolicy reportBuildPolicy;

    private final boolean includeProperties;

    @DataBoundConstructor
    public AllureReportConfig(String jdk, String commandline, List<PropertyConfig> properties,
                              ReportBuildPolicy reportBuildPolicy, Boolean includeProperties,
                              List<ResultsConfig> resultsPaths) {
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
    public List<ResultsConfig> getResultsPaths() {
        return resultsPaths;
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
}
