package io.qameta.jenkins;

import com.google.common.base.Strings;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.AutoCompletionCandidates;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import io.qameta.jenkins.config.AllureGlobalConfig;
import io.qameta.jenkins.config.ReportBuildPolicy;
import io.qameta.jenkins.tools.AllureCommandlineInstallation;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * @author eroshenkoam (Artem Eroshenko)
 */
@Extension
@Symbol("allure")
public class AllureReportPublisherDescriptor extends BuildStepDescriptor<Publisher> {

    private AllureGlobalConfig config;

    public AllureReportPublisherDescriptor() {
        super(AllureReportPublisher.class);
        load();
    }

    public AllureGlobalConfig getConfig() {
        if (config == null) {
            config = AllureGlobalConfig.newInstance();
        }
        return config;
    }

    public void setConfig(AllureGlobalConfig config) {
        this.config = config;
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
    @Nonnull
    public ReportBuildPolicy[] getReportBuildPolicies() {
        return ReportBuildPolicy.values();
    }

    @Override
    public boolean configure(StaplerRequest req, net.sf.json.JSONObject json) throws FormException {
        req.bindJSON(config, json);
        save();
        return true;
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
        return Arrays.asList(Jenkins.getInstance().getDescriptorByType(
                AllureCommandlineInstallation.DescriptorImpl.class
        ).getInstallations());
    }

    @Nonnull
    public Optional<AllureCommandlineInstallation> getCommandlineInstallation(@Nullable String name) {
        List<AllureCommandlineInstallation> installations = getCommandlineInstallations();
        Optional<AllureCommandlineInstallation> any = installations.stream()
                .filter(i -> i.getName().equals(name))
                .findAny();
        if (any.isPresent()) {
            return any;
        }
        if (!installations.isEmpty()) {
            return Optional.of(installations.iterator().next());
        }
        return Optional.empty();
    }

}
