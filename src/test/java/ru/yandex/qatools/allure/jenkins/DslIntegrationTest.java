package ru.yandex.qatools.allure.jenkins;

import hudson.model.Descriptor;
import hudson.model.FreeStyleProject;
import hudson.tasks.Publisher;
import hudson.util.DescribableList;
import javaposse.jobdsl.plugin.ExecuteDslScripts;
import javaposse.jobdsl.plugin.LookupStrategy;
import javaposse.jobdsl.plugin.RemovedJobAction;
import javaposse.jobdsl.plugin.RemovedViewAction;
import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import ru.yandex.qatools.allure.jenkins.config.ReportBuildPolicy;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertThat;

/**
 * @author Artem Eroshenko <eroshenkoam@yandex-team.ru>
 */
public class DslIntegrationTest {

    public static final String JOB_NAME_IN_DSL_SCRIPT = "allure";
    public static final String CONDUCTOR_DSL_GROOVY = "allure.groovy";

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void shouldCreateJobWithExtendedDsl() throws Exception {
        FreeStyleProject job = jenkins.createFreeStyleProject();
        job.getBuildersList().add(
                new ExecuteDslScripts(
                        new ExecuteDslScripts.ScriptLocation(
                                null, null,
                                IOUtils.toString(this
                                        .getClass().getClassLoader().getResourceAsStream(CONDUCTOR_DSL_GROOVY))
                        ),
                        false,
                        RemovedJobAction.DELETE,
                        RemovedViewAction.DELETE,
                        LookupStrategy.JENKINS_ROOT
                )
        );

        jenkins.buildAndAssertSuccess(job);

        assertThat(jenkins.getInstance().getJobNames(), hasItem(is(JOB_NAME_IN_DSL_SCRIPT)));

        FreeStyleProject generated = jenkins.getInstance()
                .getItemByFullName(JOB_NAME_IN_DSL_SCRIPT, FreeStyleProject.class);

        DescribableList<Publisher, Descriptor<Publisher>> publisher = generated.getPublishersList();

        assertThat("Should add step", publisher, hasSize(1));
        assertThat("Should contains allure report publisher",
                publisher.get(0), instanceOf(AllureReportPublisher.class));
        AllureReportPublisher allureReportPublisher = (AllureReportPublisher) publisher.get(0);

        assertThat(allureReportPublisher.getConfig().getResultsPattern(), equalTo("target/allure-results"));
        assertThat(allureReportPublisher.getConfig().getReportBuildPolicy(), equalTo(ReportBuildPolicy.UNSTABLE));
        assertThat(allureReportPublisher.getConfig().getIncludeProperties(), equalTo(Boolean.TRUE));
    }
}
