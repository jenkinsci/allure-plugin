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
import ru.yandex.qatools.allure.jenkins.config.PropertyConfig;
import ru.yandex.qatools.allure.jenkins.config.ResultsConfig;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Artem Eroshenko <eroshenkoam@yandex-team.ru>
 */
public class JobDslIT {

    private static final String JOB_NAME = "allure";
    private static final String SCRIPT_NAME = "allure.groovy";

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void shouldCreateJobWithDsl() throws Exception {
        buildJob(SCRIPT_NAME);

        assertThat(jenkins.getInstance().getJobNames()).contains(JOB_NAME);

        FreeStyleProject generated = jenkins.getInstance()
                .getItemByFullName(JOB_NAME, FreeStyleProject.class);

        DescribableList<Publisher, Descriptor<Publisher>> publisher = generated.getPublishersList();

        assertThat(publisher).as("Should add step").hasSize(1);
        assertThat(publisher.get(0)).as("Should contains complex report publisher")
                .isInstanceOf(AllureReportPublisher.class);
        AllureReportPublisher allureReportPublisher = (AllureReportPublisher) publisher.get(0);

        assertThat(allureReportPublisher.getResults()).containsExactly(
                new ResultsConfig("target/first-results"),
                new ResultsConfig("target/second-results")
        );

        assertThat(allureReportPublisher.getProperties()).hasSize(1)
                .containsExactly(new PropertyConfig("key", "value"));

        assertThat(allureReportPublisher.getIncludeProperties()).isEqualTo(Boolean.TRUE);

        assertThat(allureReportPublisher.getConfigPath()).isEqualTo(null);
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
