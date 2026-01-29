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

import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.allurereport.jenkins.config.ResultsConfig;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Allure 3 configuration.
 */
public class Allure3ConfigTest {

    @ClassRule
    public static JenkinsRule jRule = new JenkinsRule();

    @Test
    public void shouldDefaultToAllure2() {
        final AllureReportPublisher publisher = new AllureReportPublisher(
                Collections.singletonList(new ResultsConfig("allure-results"))
        );

        assertThat(publisher.getAllureVersion()).isEqualTo("2");
        assertThat(publisher.isAllure3()).isFalse();
    }

    @Test
    public void shouldSetAllure3Version() {
        final AllureReportPublisher publisher = new AllureReportPublisher(
                Collections.singletonList(new ResultsConfig("allure-results"))
        );
        publisher.setAllureVersion("3");

        assertThat(publisher.getAllureVersion()).isEqualTo("3");
        assertThat(publisher.isAllure3()).isTrue();
    }

    @Test
    public void shouldSetAllure2Version() {
        final AllureReportPublisher publisher = new AllureReportPublisher(
                Collections.singletonList(new ResultsConfig("allure-results"))
        );
        publisher.setAllureVersion("2");

        assertThat(publisher.getAllureVersion()).isEqualTo("2");
        assertThat(publisher.isAllure3()).isFalse();
    }

    @Test
    public void descriptorShouldReturnAllure3Installation() {
        final AllureReportPublisherDescriptor descriptor = jRule.jenkins
                .getDescriptorByType(AllureReportPublisherDescriptor.class);

        // Should return a default installation even if none configured
        assertThat(descriptor.getAllure3Installation()).isNotNull();
    }

    @Test
    public void descriptorShouldReturnAllureVersions() {
        final AllureReportPublisherDescriptor descriptor = jRule.jenkins
                .getDescriptorByType(AllureReportPublisherDescriptor.class);

        final String[] versions = descriptor.getAllureVersions();
        assertThat(versions).containsExactly("2", "3");
    }
}
