package io.qameta.jenkins.testdata;

import hudson.FilePath;
import hudson.model.Actionable;
import io.qameta.jenkins.AllureReportBuildBadgeAction;
import io.qameta.jenkins.ReportGenerateIT;
import io.qameta.jenkins.tools.AllureInstallation;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

import java.nio.file.Path;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;

/**
 * @author charlie (Dmitry Baev).
 */
public final class TestUtils {

    TestUtils() {
    }

    public static String getJdk(JenkinsRule jRule) {
        return jRule.jenkins.getJDKs().get(0).getName();
    }

    public static String getAllureCommandline(JenkinsRule jRule, TemporaryFolder folder) throws Exception {
        Path allureHome = folder.newFolder().toPath();
        FilePath allure = jRule.jenkins.getRootPath().createTempFile("allure", "zip");
        //noinspection ConstantConditions
        allure.copyFrom(ReportGenerateIT.class.getClassLoader().getResource("allure-commandline.zip"));
        allure.unzip(new FilePath(allureHome.toFile()));

        AllureInstallation installation = new AllureInstallation(
                "Default", allureHome.toAbsolutePath().toString(), JenkinsRule.NO_PROPERTIES);
        jRule.jenkins.getDescriptorByType(AllureInstallation.DescriptorImpl.class)
                .setInstallations(installation);
        return installation.getName();
    }

    public static void assertHasReport(Actionable actionable) {
        List<AllureReportBuildBadgeAction> actions = actionable.getActions(AllureReportBuildBadgeAction.class);
        assertThat(actions, hasSize(1));
    }
}
