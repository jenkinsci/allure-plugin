package ru.yandex.qatools.allure.jenkins;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.AutoCompletionCandidates;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import ru.yandex.qatools.allure.jenkins.config.PropertyConfig;
import ru.yandex.qatools.allure.jenkins.config.ReportBuildPolicy;
import ru.yandex.qatools.allure.jenkins.tools.AllureCommandlineInstallation;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * User: eroshenkoam.
 * Date: 10/9/13, 7:49 PM
 */
@Extension
@Symbol("allure")
public class AllureReportPublisherDescriptor extends BuildStepDescriptor<Publisher> {

    private List<PropertyConfig> properties;

    public AllureReportPublisherDescriptor() {
        super(AllureReportPublisher.class);
        load();
    }

    public List<PropertyConfig> getProperties() {
        if (this.properties == null) {
            this.properties = new ArrayList<>();
        }
        return this.properties;
    }

    public void setProperties(List<PropertyConfig> properties) {
        this.properties = properties;
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
    public AutoCompletionCandidates doAutoCompletePropertyKey() {
        final AutoCompletionCandidates candidates = new AutoCompletionCandidates();
        candidates.add("allure.issues.tracker.pattern");
        candidates.add("allure.tests.management.pattern");
        return candidates;
    }

    @Override
    public boolean configure(StaplerRequest req, net.sf.json.JSONObject json) throws FormException {
        try {
            if (json.has("properties")) {
                String jsonProperties = JSONObject.fromObject(json).get("properties").toString();
                final ObjectMapper mapper = new ObjectMapper()
                        .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);

                List<PropertyConfig> properties = mapper.readValue(jsonProperties,
                        new TypeReference<List<PropertyConfig>>() {});
                setProperties(properties);
                save();
            }
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    @Nonnull
    public List<AllureCommandlineInstallation> getCommandlineInstallations() {
        return Arrays.asList(Jenkins.getInstance()
                .getDescriptorByType(AllureCommandlineInstallation.DescriptorImpl.class)
                .getInstallations());
    }

    @SuppressWarnings("ReturnCount")
    public AllureCommandlineInstallation getCommandlineInstallation(String name) {
        final List<AllureCommandlineInstallation> installations = getCommandlineInstallations();

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
