package ru.yandex.qatools.allure.jenkins.config;

import com.google.common.collect.ImmutableSet;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import ru.yandex.qatools.allure.jenkins.steps.AllureStepExecution;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * eroshenkoam
 * 30/07/14
 */
public class AllureReportConfig extends Step implements Serializable {

    private String jdk;

    private String commandline;

    /**
     * @deprecated
     */
    @Deprecated
    private String resultsPattern;

    private List<PropertyConfig> properties;

    private List<ResultsConfig> results;

    private ReportBuildPolicy reportBuildPolicy;

    private Boolean includeProperties;

    @DataBoundConstructor
    public AllureReportConfig(String jdk, String commandline, List<PropertyConfig> properties,
                              ReportBuildPolicy reportBuildPolicy, Boolean includeProperties, List<ResultsConfig> results) {
        this.jdk = jdk;
        this.commandline = commandline;

        this.results = results == null ? Collections.<ResultsConfig>emptyList() : results;
        this.properties = properties == null ? Collections.<PropertyConfig>emptyList() : properties;
        this.includeProperties = includeProperties == null ? Boolean.FALSE : includeProperties;
        this.reportBuildPolicy = reportBuildPolicy == null ? ReportBuildPolicy.ALWAYS : reportBuildPolicy;
    }

    public String getJdk() {
        return jdk;
    }

    public void setJdk(String jdk) {
        this.jdk = jdk;
    }

    public String getCommandline() {
        return commandline;
    }

    public void setCommandline(String commandline) {
        this.commandline = commandline;
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

    public static AllureReportConfig newInstance(List<String> paths) {
        return newInstance(null, null, paths.toArray(new String[]{}));
    }

    public static AllureReportConfig newInstance(String jdk, String commandline, String... paths) {
        return newInstance(jdk, commandline, Arrays.asList(paths));
    }

    private static AllureReportConfig newInstance(String jdk, String commandline, List<String> paths) {
        List<ResultsConfig> results = convertPaths(paths);
        return new AllureReportConfig(jdk, commandline, new ArrayList<PropertyConfig>(),
                ReportBuildPolicy.ALWAYS, true, results);
    }

    private static List<ResultsConfig> convertPaths(String paths) {
        return convertPaths(Arrays.asList(paths.split("\\n")));
    }

    private static List<ResultsConfig> convertPaths(List<String> paths) {
        List<ResultsConfig> results = new ArrayList<>();
        for (String path : paths) {
            results.add(new ResultsConfig(path));
        }
        return results;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new AllureStepExecution(context, this);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "withAllure";
        }


        @Override
        public String getDisplayName() {
            return "Provide Allure reporting";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return false;
        }

        @Override
        public Set<Class<?>> getRequiredContext() {
            return ImmutableSet.of(TaskListener.class, FilePath.class, Launcher.class, EnvVars.class, Run.class);
        }
    }
}
