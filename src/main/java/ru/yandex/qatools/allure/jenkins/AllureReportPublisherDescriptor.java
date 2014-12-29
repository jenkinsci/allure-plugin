package ru.yandex.qatools.allure.jenkins;

import com.google.common.base.Objects;
import com.google.common.base.Strings;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;

import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import ru.yandex.qatools.allure.jenkins.config.AllureGlobalConfig;
import ru.yandex.qatools.allure.jenkins.config.ReportBuildPolicy;

/**
 * User: eroshenkoam
 * Date: 10/9/13, 7:49 PM
 */
@Extension
public class AllureReportPublisherDescriptor extends BuildStepDescriptor<Publisher> {

    private AllureGlobalConfig config;

    @Deprecated
    private String reportVersionDefault;

    @Deprecated
    private String resultsPatternDefault;

    public AllureReportPublisherDescriptor() {
        super(AllureReportPublisher.class);
        load();
    }

    @Override
    public String getDisplayName() {
        return AllureReportPlugin.DESCRIPTION;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public boolean isApplicable(Class<? extends AbstractProject> jobType) {
        return true;
    }

    @SuppressWarnings("unused")
    public ReportBuildPolicy[] getReportBuildPolicies() {
        return ReportBuildPolicy.values();
    }


    public AllureGlobalConfig getConfig() {
        if (config == null) {
            return AllureGlobalConfig.newInstance(resultsPatternDefault, reportVersionDefault);
        } else {
            return config;
        }
    }

    public void setConfig(AllureGlobalConfig config) {
        this.config = config;
    }

    @SuppressWarnings("unused")
    public String getResultsPatternDefault() {
        return Objects.firstNonNull(getConfig().getResultsPatternDefault(),
                AllureReportPlugin.DEFAULT_RESULTS_PATTERN);
    }

    @SuppressWarnings("unused")
    public String getReportVersionDefault() {
        return Objects.firstNonNull(getConfig().getReportVersionDefault(),
                AllureReportPlugin.DEFAULT_REPORT_VERSION);
    }

    @SuppressWarnings("unused")
    public FormValidation doResultsPattern(@QueryParameter String resultsPattern) {
        return Strings.isNullOrEmpty(resultsPattern) ?
                FormValidation.error("Results pattern can't be empty") : FormValidation.ok();
    }

    @Override
    public boolean configure(StaplerRequest req, net.sf.json.JSONObject json) throws FormException {
        setConfig((AllureGlobalConfig) json.toBean(AllureGlobalConfig.class));
        save();
        return true;
    }
}
