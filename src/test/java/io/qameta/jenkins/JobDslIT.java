package io.qameta.jenkins;

import hudson.model.Descriptor;
import hudson.model.FreeStyleProject;
import hudson.tasks.Publisher;
import hudson.util.DescribableList;
import io.qameta.jenkins.config.ReportBuildPolicy;
import javaposse.jobdsl.plugin.ExecuteDslScripts;
import javaposse.jobdsl.plugin.LookupStrategy;
import javaposse.jobdsl.plugin.RemovedJobAction;
import javaposse.jobdsl.plugin.RemovedViewAction;
import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Artem Eroshenko <eroshenkoam@yandex-team.ru>
 */
public class JobDslIT {

    public static final String JOB_NAME = "allure";
    public static final String SCRIPT_NAME = "allure.groovy";

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void shouldCreateJobWithDsl() throws Exception {
        buildJob(SCRIPT_NAME);

        assertThat(jenkins.getInstance().getJobNames(), hasItem(is(JOB_NAME)));
        FreeStyleProject generated = jenkins.getInstance()
                .getItemByFullName(JOB_NAME, FreeStyleProject.class);

        DescribableList<Publisher, Descriptor<Publisher>> publisher = generated.getPublishersList();

        assertThat("Should add step", publisher, hasSize(1));
        assertThat("Should contains complex report publisher",
                publisher.get(0), instanceOf(AllureReportPublisher.class));
        AllureReportPublisher allureReportPublisher = (AllureReportPublisher) publisher.get(0);

        assertThat(allureReportPublisher.getConfig().getResultsPaths(), hasItems(
                hasProperty("path", equalTo("target/first-results")),
                hasProperty("path", equalTo("target/second-results"))
        ));

        assertThat(allureReportPublisher.getConfig().getProperties(), hasSize(1));
        assertThat(allureReportPublisher.getConfig().getProperties().get(0).getKey(), equalTo("key"));
        assertThat(allureReportPublisher.getConfig().getProperties().get(0).getValue(), equalTo("value"));

        assertThat(allureReportPublisher.getConfig().getReportBuildPolicy(), equalTo(ReportBuildPolicy.UNSTABLE));
        assertThat(allureReportPublisher.getConfig().getIncludeProperties(), equalTo(Boolean.TRUE));


    }

    private FreeStyleProject buildJob(String script) throws Exception {
        FreeStyleProject job = jenkins.createFreeStyleProject();
        job.getBuildersList().add(
                new ExecuteDslScripts(
                        new ExecuteDslScripts.ScriptLocation(
                                null, null,
                                IOUtils.toString(this
                                        .getClass().getClassLoader().getResourceAsStream(script))
                        ),
                        false,
                        RemovedJobAction.DELETE,
                        RemovedViewAction.DELETE,
                        LookupStrategy.JENKINS_ROOT
                )
        );

        jenkins.buildAndAssertSuccess(job);
        return job;
    }
}
