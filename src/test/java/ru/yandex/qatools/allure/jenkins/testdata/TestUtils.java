package ru.yandex.qatools.allure.jenkins.testdata;

import hudson.FilePath;
import hudson.model.JDK;
import hudson.scm.SCM;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SingleFileSCM;
import ru.yandex.qatools.allure.jenkins.AllureReportPublisher;
import ru.yandex.qatools.allure.jenkins.config.ResultsConfig;
import ru.yandex.qatools.allure.jenkins.tools.AllureCommandlineInstallation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * @author charlie (Dmitry Baev).
 */
public final class TestUtils {

    TestUtils() {
    }

    public static JDK getJdk(JenkinsRule jRule) {
        return jRule.jenkins.getJDKs().get(0);
    }

    public static AllureCommandlineInstallation getAllureCommandline(JenkinsRule jRule, TemporaryFolder folder) throws Exception {
        Path allureHome = folder.newFolder("some spaces in here").toPath();
        FilePath allure = jRule.jenkins.getRootPath().createTempFile("allure", "zip");
        //noinspection ConstantConditions
        allure.copyFrom(TestUtils.class.getClassLoader().getResource("allure-commandline.zip"));
        allure.unzip(new FilePath(allureHome.toFile()));

        AllureCommandlineInstallation installation = new AllureCommandlineInstallation(
                "Default", allureHome.toAbsolutePath().toString(), JenkinsRule.NO_PROPERTIES);
        jRule.jenkins.getDescriptorByType(AllureCommandlineInstallation.DescriptorImpl.class)
                .setInstallations(installation);
        return installation;
    }

    public static SCM getSimpleFileScm(String resourceName, String path) throws IOException {
        //noinspection ConstantConditions
        return new SingleFileSCM(path, TestUtils.class.getClassLoader().getResource(resourceName));
    }

    public static AllureReportPublisher createAllurePublisher(String jdk, String commandline,
                                                        String... resultsPaths) throws Exception {
        final AllureReportPublisher publisher = createAllurePublisherWithoutCommandline(jdk, resultsPaths);
        publisher.setCommandline(commandline);
        return publisher;
    }

    public static AllureReportPublisher createAllurePublisherWithoutCommandline(String jdk, String... resultsPaths)
            throws Exception {
        final List<ResultsConfig> results = ResultsConfig.convertPaths(Arrays.asList(resultsPaths));
        final AllureReportPublisher publisher = new AllureReportPublisher(results);
        publisher.setJdk(jdk);
        return publisher;
    }


}
