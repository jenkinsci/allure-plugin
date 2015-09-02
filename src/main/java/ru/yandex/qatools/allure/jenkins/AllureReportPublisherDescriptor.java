package ru.yandex.qatools.allure.jenkins;

import com.google.common.base.Objects;
import com.google.common.base.Strings;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;

import jenkins.model.Jenkins;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import ru.yandex.qatools.allure.jenkins.config.AllureGlobalConfig;
import ru.yandex.qatools.allure.jenkins.config.ReportBuildPolicy;
import ru.yandex.qatools.allure.jenkins.tools.AllureCommandlineInstallation;

import java.util.Arrays;
import java.util.List;

/**
 * User: eroshenkoam
 * Date: 10/9/13, 7:49 PM
 */
@Extension
public class AllureReportPublisherDescriptor extends BuildStepDescriptor<Publisher> {

    private AllureGlobalConfig config;

    @Deprecated
    @SuppressWarnings("unused")
    private String reportVersionDefault;

    @Deprecated
    @SuppressWarnings("unused")
    private String resultsPatternDefault;

    public AllureReportPublisherDescriptor() {
        super(AllureReportPublisher.class);
        load();
    }

    @Override
    public String getDisplayName() {
        return Messages.AllureReportPublisher_DisplayName();
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
    public String getIssuesTrackerPatternDefault() {
        return Objects.firstNonNull(getConfig().getIssuesTrackerPatternDefault(),
                AllureReportPlugin.DEFAULT_ISSUE_TRACKER_PATTERN);
    }

    public void setIssuesTrackerPatternDefault(String issuesTrackerPatternDefault) {
        getConfig().setIssuesTrackerPatternDefault(issuesTrackerPatternDefault);
    }

    @SuppressWarnings("unused")
    public String getTmsPatternDefault() {
        return Objects.firstNonNull(getConfig().getTmsPatternDefault(),
                AllureReportPlugin.DEFAULT_TMS_PATTERN);
    }

    public void setTmsPatternDefault(String tmsPatternDefault) {
        getConfig().setTmsPatternDefault(tmsPatternDefault);
    }

    @SuppressWarnings("unused")
    public FormValidation doResultsPattern(@QueryParameter String resultsPattern) {
        return Strings.isNullOrEmpty(resultsPattern) ?
                FormValidation.error(Messages.AllureReportPublisher_EmptyResultsError()) : FormValidation.ok();
    }

    @Override
    public boolean configure(StaplerRequest req, net.sf.json.JSONObject json) throws FormException {
        setConfig((AllureGlobalConfig) json.toBean(AllureGlobalConfig.class));
        String issuesTrackerPatternDefaultValue = json.getString("issuesTrackerPatternDefault");
        if (!Strings.isNullOrEmpty(issuesTrackerPatternDefaultValue)) {
            setIssuesTrackerPatternDefault(issuesTrackerPatternDefaultValue);
        }
        String tmsPatternDefaultValue = json.getString("tmsPatternDefault");
        if (!Strings.isNullOrEmpty(tmsPatternDefaultValue)) {
            setTmsPatternDefault(tmsPatternDefaultValue);
        }

        save();
        return true;
    }

    public List<AllureCommandlineInstallation> getCommandlineInstallations() {
        return Arrays.asList(Jenkins.getInstance().getDescriptorByType(
                AllureCommandlineInstallation.DescriptorImpl.class).getInstallations());
    }

    public AllureCommandlineInstallation getCommandlineInstallation(String name) {
        List<AllureCommandlineInstallation> installations = getCommandlineInstallations();

        for (AllureCommandlineInstallation installation : installations) {
            if (installation.getName().equals(name)) {
                return installation;
            }
        }

        // If no installation match then take the first one
        if (!installations.isEmpty()) {
            return installations.get(0);
        }

        return null;

    }

}
