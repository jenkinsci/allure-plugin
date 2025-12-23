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

import hudson.model.Descriptor;
import hudson.model.FreeStyleProject;
import hudson.tasks.Publisher;
import hudson.util.DescribableList;
import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.allurereport.jenkins.config.PropertyConfig;
import org.allurereport.jenkins.config.ResultsConfig;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import javaposse.jobdsl.plugin.ExecuteDslScripts;
import javaposse.jobdsl.plugin.LookupStrategy;
import javaposse.jobdsl.plugin.RemovedJobAction;
import javaposse.jobdsl.plugin.RemovedViewAction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Artem Eroshenko
 */
public class JobDslIT {

    private static final String JOB_NAME = "allure";
    private static final String SCRIPT_NAME = "allure.groovy";

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void shouldCreateJobWithDsl() throws Exception {

        buildJob();

        assertThat(jenkins.getInstance().getJobNames()).contains(JOB_NAME);

        final FreeStyleProject generated = jenkins.getInstance()
                .getItemByFullName(JOB_NAME, FreeStyleProject.class);

        assertThat(generated).isNotNull();
        final DescribableList<Publisher, Descriptor<Publisher>> publisher = generated.getPublishersList();

        assertThat(publisher).as("Should add step").hasSize(1);
        assertThat(publisher.get(0)).as("Should contains complex report publisher")
                .isInstanceOf(AllureReportPublisher.class);
        final AllureReportPublisher allureReportPublisher = (AllureReportPublisher) publisher.get(0);

        assertThat(allureReportPublisher.getResults()).containsExactly(
                new ResultsConfig("target/first-results"),
                new ResultsConfig("target/second-results")
        );

        assertThat(allureReportPublisher.getProperties()).hasSize(1)
                .containsExactly(new PropertyConfig("key", "value"));

        assertThat(allureReportPublisher.getIncludeProperties()).isEqualTo(Boolean.TRUE);

        assertThat(allureReportPublisher.getConfigPath()).isEqualTo(null);
    }

    private void buildJob() throws Exception {

        final FreeStyleProject job = jenkins.createFreeStyleProject();
        final ExecuteDslScripts executeDslScripts = new ExecuteDslScripts();
        executeDslScripts.setScriptText(IOUtils.toString(Objects.requireNonNull(this
                .getClass().getClassLoader().getResourceAsStream(SCRIPT_NAME)),
                StandardCharsets.UTF_8
                ));
        executeDslScripts.setRemovedJobAction(RemovedJobAction.DELETE);
        executeDslScripts.setRemovedViewAction(RemovedViewAction.DELETE);
        executeDslScripts.setLookupStrategy(LookupStrategy.JENKINS_ROOT);
        job.getBuildersList().add(executeDslScripts);

        jenkins.buildAndAssertSuccess(job);
    }
}
