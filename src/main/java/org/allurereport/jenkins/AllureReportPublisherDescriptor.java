/*
 *  Copyright 2016-2023 Qameta Software OÜ
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.allurereport.jenkins;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.AutoCompletionCandidates;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.tools.InstallSourceProperty;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolProperty;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.allurereport.jenkins.config.PropertyConfig;
import org.allurereport.jenkins.config.ReportBuildPolicy;
import org.allurereport.jenkins.config.ResultPolicy;
import org.allurereport.jenkins.tools.Allure3Installation;
import org.allurereport.jenkins.tools.AllureCommandlineDirectInstaller;
import org.allurereport.jenkins.tools.AllureCommandlineInstallation;
import org.allurereport.jenkins.tools.AllureVersionService;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Extension
@Symbol("allure")
public class AllureReportPublisherDescriptor extends BuildStepDescriptor<Publisher> {

    private static final String PROPERTIES = "properties";
    private static final String NEWLINE = "\n";
    private static final int SINGLE_INSTALLATION = 1;
    private final Object quickSetupLock = new Object();
    private List<PropertyConfig> properties;

    public AllureReportPublisherDescriptor() {
        super(AllureReportPublisher.class);
        load();
    }
    @SuppressWarnings("unused")
    public ResultPolicy[] getResultPolicies() {
        return ResultPolicy.values();
    }

    public List<PropertyConfig> getProperties() {
        if (this.properties == null) {
            this.properties = new ArrayList<>();
        }
        return this.properties;
    }

    public void setProperties(final List<PropertyConfig> properties) {
        this.properties = properties;
    }

    @Override
    @NonNull
    public String getDisplayName() {
        return Messages.AllureReportPublisher_DisplayName();
    }

    @Override
    public boolean isApplicable(final Class<? extends AbstractProject> jobType) {
        return true;
    }

    @SuppressWarnings("unused")
    public ReportBuildPolicy[] getReportBuildPolicies() {
        return ReportBuildPolicy.values();
    }

    @SuppressWarnings("unused")
    @NonNull
    public AutoCompletionCandidates doAutoCompletePropertyKey() {
        final AutoCompletionCandidates candidates = new AutoCompletionCandidates();
        candidates.add("allure.issues.tracker.pattern");
        candidates.add("allure.tests.management.pattern");
        return candidates;
    }

    @Override
    public boolean configure(final StaplerRequest req,
        final JSONObject json) throws FormException {
        try {
            if (json.has(PROPERTIES)) {
                final String jsonProperties = JSONObject.fromObject(json).get(PROPERTIES).toString();
                final ObjectMapper mapper = new ObjectMapper()
                    .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);

                final List<PropertyConfig> properties = mapper.readValue(jsonProperties,
                    new TypeReference<List<PropertyConfig>>() { });
                setProperties(properties);
                save();
            }
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    @NonNull
    public List<AllureCommandlineInstallation> getCommandlineInstallations() {
        return Optional.of(Jenkins.get())
            .map(j -> j.getDescriptorByType(AllureCommandlineInstallation.DescriptorImpl.class))
            .map(ToolDescriptor::getInstallations)
            .map(Arrays::asList).orElse(Collections.emptyList());
    }

    public AllureCommandlineInstallation getCommandlineInstallation(final String name) {
        if (StringUtils.isBlank(name)) {
            return null;
        }

        final List<AllureCommandlineInstallation> installations = getCommandlineInstallations();
        if (CollectionUtils.isEmpty(installations)) {
            return null;
        }

        return installations.stream()
            .filter(installation -> installation.getName().equals(name))
            .findFirst()
            .orElse(null);
    }

    public AllureCommandlineInstallation getDefaultCommandlineInstallation() {
        final List<AllureCommandlineInstallation> installations = getCommandlineInstallations();
        if (installations.size() == SINGLE_INSTALLATION) {
            return installations.get(0);
        }
        return null;
    }

    /**
     * Get the Allure 3 installation.
     * For Allure 3, we use a single installation that expects 'allure' to be in PATH.
     *
     * @return the Allure 3 installation, or null if not configured
     */
    public Allure3Installation getAllure3Installation() {
        return Optional.of(Jenkins.get())
            .map(j -> j.getDescriptorByType(Allure3Installation.DescriptorImpl.class))
            .map(descriptor -> descriptor.getInstallations())
            .filter(installations -> installations.length > 0)
            .map(installations -> installations[0])
            .orElse(new Allure3Installation("Allure 3", "", Collections.emptyList()));
    }

    /**
     * Get available Allure versions for the UI dropdown.
     *
     * @return array of version strings
     */
    @SuppressWarnings("unused")
    public String[] getAllureVersions() {
        return new String[]{"2", "3"};
    }

    @RequirePOST
    @Restricted(NoExternalUse.class)
    public FormValidation doQuickSetup(@QueryParameter final String version) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        synchronized (quickSetupLock) {
            try {
                final AllureCommandlineInstallation.DescriptorImpl descriptor =
                    Jenkins.get().getDescriptorByType(AllureCommandlineInstallation.DescriptorImpl.class);

                if (descriptor == null) {
                    return FormValidation.error("Allure CLI descriptor not found");
                }

                final AllureCommandlineInstallation[] existing = descriptor.getInstallations();
                if (existing != null && existing.length > 0) {
                    final String existingNames = Arrays.stream(existing)
                        .map(AllureCommandlineInstallation::getName)
                        .collect(Collectors.joining(", "));
                    return FormValidation.ok(
                        "Allure CLI already configured: "
                        + existingNames);
                }

                final String targetVersion = determineVersion(version);

                final AllureCommandlineDirectInstaller installer =
                    new AllureCommandlineDirectInstaller(targetVersion);

                final InstallSourceProperty installSource =
                    new InstallSourceProperty(Collections.singletonList(installer));

                final List<ToolProperty<?>> properties = new ArrayList<>();
                properties.add(installSource);

                final AllureCommandlineInstallation installation =
                    new AllureCommandlineInstallation(
                        "Allure " + targetVersion,
                        "",
                        properties
                    );

                try {
                    descriptor.setInstallations(installation);
                    descriptor.save();
                } catch (Exception e) {
                    return FormValidation.error(
                            "Failed to save configuration: " + e.getMessage()
                                    + NEWLINE + "Please try again or configure manually in Global Tool Configuration.");
                }

                return FormValidation.ok(
                    "✓ Successfully configured Allure CLI " + targetVersion
                    + NEWLINE + "Installation will be downloaded automatically on first use."
                    + NEWLINE + "You can manage installations in: Manage Jenkins - Tools - Allure Commandline");

            } catch (Exception e) {
                return FormValidation.error(
                    "Failed to setup Allure CLI: " + e.getMessage()
                    + NEWLINE + "Please configure manually in Global Tool Configuration.");
            }
        }
    }

    @SuppressWarnings("PMD.AvoidLiteralsInIfCondition")
    private String determineVersion(final String requestedVersion) {
        final String latestKeyword = "latest";
        if (latestKeyword.equals(requestedVersion) || StringUtils.isBlank(requestedVersion)) {
            try {
                return AllureVersionService.getLatestStableVersion();
            } catch (Exception e) {
                return AllureVersionService.FALLBACK_VERSION;
            }
        }
        return requestedVersion;
    }
}
