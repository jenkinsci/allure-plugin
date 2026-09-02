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

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import org.allurereport.jenkins.config.ReportBuildPolicy;
import org.allurereport.jenkins.config.ResultsConfig;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Collections;

public class AllureReportPolicyIT {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void rejectedPolicyDoesNotRequireConfiguredCommandline() throws Exception {
        final FreeStyleProject project = jenkins.createFreeStyleProject();
        final AllureReportPublisher publisher = new AllureReportPublisher(
                Collections.singletonList(new ResultsConfig("allure-results"))
        );
        publisher.setReportBuildPolicy(ReportBuildPolicy.UNSUCCESSFUL);
        project.getPublishersList().add(publisher);

        final FreeStyleBuild build = jenkins.buildAndAssertSuccess(project);

        jenkins.assertLogContains("Allure report generation rejected by policy", build);
    }
}
