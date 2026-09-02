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

import hudson.tools.InstallSourceProperty;
import hudson.tools.ToolProperty;
import org.allurereport.jenkins.config.ResultsConfig;
import org.allurereport.jenkins.tools.AllureCommandlineInstallation;
import org.allurereport.jenkins.tools.AllureManagedInstaller;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AllurePublisherConfigurationTest {

    private static final String TOOL_NAME = "Allure";
    private static final String ALLURE_3 = "3";

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Before
    public void configureUnifiedTool() throws Exception {
        final AllureManagedInstaller installer = new AllureManagedInstaller(
                AllureManagedInstaller.VERSION_POLICY_RECOMMENDED
        );
        final InstallSourceProperty source = new InstallSourceProperty(Collections.singletonList(installer));
        final List<ToolProperty<?>> properties = Collections.singletonList(source);
        final AllureCommandlineInstallation installation = new AllureCommandlineInstallation(
                TOOL_NAME,
                "",
                properties
        );
        jenkins.jenkins.getDescriptorByType(AllureCommandlineInstallation.DescriptorImpl.class)
                .setInstallations(installation);
    }

    @Test
    public void unifiedToolSelectionSurvivesPublisherRoundtrip() throws Exception {
        final AllureReportPublisher publisher = publisher();
        publisher.setCommandline(TOOL_NAME);

        final AllureReportPublisher roundtripped = jenkins.configRoundtrip(publisher);

        assertThat(roundtripped.getCommandline()).isEqualTo(TOOL_NAME);
        assertThat(roundtripped.isLegacyAllure3Configuration()).isFalse();
    }

    @Test
    public void releasedAllure3ConfigurationMigratesToLegacyPathSelection() throws Exception {
        final AllureReportPublisher publisher = publisher();
        publisher.setAllureVersion(ALLURE_3);

        final AllureReportPublisher roundtripped = jenkins.configRoundtrip(publisher);

        assertThat(roundtripped.isLegacyAllure3PathSelected()).isTrue();
        assertThat(roundtripped.isLegacyAllure3Configuration()).isTrue();
    }

    @Test
    public void releasedAllure3ConfigurationIgnoresStaleAllure2Selection() throws Exception {
        final AllureReportPublisher publisher = publisher();
        publisher.setAllureVersion(ALLURE_3);
        publisher.setCommandline(TOOL_NAME);

        final AllureReportPublisher roundtripped = jenkins.configRoundtrip(publisher);

        assertThat(roundtripped.isLegacyAllure3PathSelected()).isTrue();
    }

    private AllureReportPublisher publisher() {
        return new AllureReportPublisher(
                Collections.singletonList(new ResultsConfig("allure-results"))
        );
    }
}
