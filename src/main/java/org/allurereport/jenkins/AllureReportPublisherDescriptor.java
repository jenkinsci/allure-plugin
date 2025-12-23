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
import hudson.tools.ToolDescriptor;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.collections.CollectionUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.StaplerRequest;
import org.allurereport.jenkins.config.PropertyConfig;
import org.allurereport.jenkins.config.ReportBuildPolicy;
import org.allurereport.jenkins.config.ResultPolicy;
import org.allurereport.jenkins.tools.AllureCommandlineInstallation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * User: eroshenkoam.
 * Date: 10/9/13, 7:49 PM
 */
@Extension
@Symbol("allure")
public class AllureReportPublisherDescriptor extends BuildStepDescriptor<Publisher> {

    private static final String PROPERTIES = "properties";
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

    @SuppressWarnings("ReturnCount")
    public AllureCommandlineInstallation getCommandlineInstallation(final String name) {

        final List<AllureCommandlineInstallation> installations = getCommandlineInstallations();
        if (CollectionUtils.isEmpty(installations)) {
            return null;
        }

        return installations.stream()
            .filter(installation -> installation.getName().equals(name))
            // If no installation match then take the first one
            .findFirst().orElse(installations.get(0));
    }
}
