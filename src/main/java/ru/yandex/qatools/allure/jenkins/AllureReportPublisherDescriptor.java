package ru.yandex.qatools.allure.jenkins;

import com.google.common.base.Strings;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.AutoCompletionCandidates;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.QueryParameter;
import ru.yandex.qatools.allure.jenkins.config.ReportBuildPolicy;
import ru.yandex.qatools.allure.jenkins.tools.AllureCommandlineInstallation;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.List;

/**
 * User: eroshenkoam
 * Date: 10/9/13, 7:49 PM
 */
@Extension
@Symbol("allure")
public class AllureReportPublisherDescriptor extends BuildStepDescriptor<Publisher> {


    public AllureReportPublisherDescriptor() {
        super(AllureReportPublisher.class);
        load();
    }

    @Override
    @Nonnull
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

    @SuppressWarnings("unused")
    @Nonnull
    public FormValidation doResultsPattern(@QueryParameter("results") String results) {
        if (Strings.isNullOrEmpty(results)) {
            return FormValidation.error(Messages.AllureReportPublisher_EmptyResultsError());
        }

        if (results.contains("**")) {
            return FormValidation.error(Messages.AllureReportPublisher_GlobSyntaxNotSupportedAnymore());
        }

        return FormValidation.ok();
    }

    @SuppressWarnings("unused")
    @Nonnull
    public AutoCompletionCandidates doAutoCompletePropertyKey() {
        AutoCompletionCandidates candidates = new AutoCompletionCandidates();
        candidates.add("allure.issues.tracker.pattern");
        candidates.add("allure.tests.management.pattern");
        return candidates;
    }

    @Nonnull
    public List<AllureCommandlineInstallation> getCommandlineInstallations() {
        return Arrays.asList(Jenkins.getInstance()
                .getDescriptorByType(AllureCommandlineInstallation.DescriptorImpl.class)
                .getInstallations());
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
