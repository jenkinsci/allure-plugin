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

import org.allurereport.jenkins.tools.AllureCommandlineInstallation;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.assertj.core.api.Assertions.assertThat;

public class AllureReportPublisherDescriptorTest {

    private static final String FIRST = "First";
    private static final String SECOND = "Second";
    private static final String DEFAULT = "Default";

    @Rule
    public JenkinsRule jRule = new JenkinsRule();

    @Test
    public void getCommandlineInstallationReturnsNamedInstallation() {
        final AllureReportPublisherDescriptor descriptor = descriptor();
        final AllureCommandlineInstallation first = installation(FIRST);
        final AllureCommandlineInstallation second = installation(SECOND);
        jRule.jenkins.getDescriptorByType(AllureCommandlineInstallation.DescriptorImpl.class)
                .setInstallations(first, second);

        assertThat(descriptor.getCommandlineInstallation(SECOND)).isEqualTo(second);
    }

    @Test
    public void getCommandlineInstallationReturnsNullForBlankAndUnknownNames() {
        final AllureReportPublisherDescriptor descriptor = descriptor();
        jRule.jenkins.getDescriptorByType(AllureCommandlineInstallation.DescriptorImpl.class)
                .setInstallations(installation(DEFAULT));

        assertThat(descriptor.getCommandlineInstallation("")).isNull();
        assertThat(descriptor.getCommandlineInstallation("missing")).isNull();
    }

    @Test
    public void getDefaultCommandlineInstallationReturnsOnlyInstallation() {
        final AllureReportPublisherDescriptor descriptor = descriptor();
        final AllureCommandlineInstallation installation = installation(DEFAULT);
        jRule.jenkins.getDescriptorByType(AllureCommandlineInstallation.DescriptorImpl.class)
                .setInstallations(installation);

        assertThat(descriptor.getDefaultCommandlineInstallation()).isEqualTo(installation);
    }

    @Test
    public void getDefaultCommandlineInstallationReturnsNullWhenMultipleInstallationsExist() {
        final AllureReportPublisherDescriptor descriptor = descriptor();
        jRule.jenkins.getDescriptorByType(AllureCommandlineInstallation.DescriptorImpl.class)
                .setInstallations(installation(FIRST), installation(SECOND));

        assertThat(descriptor.getDefaultCommandlineInstallation()).isNull();
    }

    private AllureReportPublisherDescriptor descriptor() {
        return jRule.jenkins.getDescriptorByType(AllureReportPublisherDescriptor.class);
    }

    private AllureCommandlineInstallation installation(final String name) {
        return new AllureCommandlineInstallation(name, "/tmp/" + name, JenkinsRule.NO_PROPERTIES);
    }
}
