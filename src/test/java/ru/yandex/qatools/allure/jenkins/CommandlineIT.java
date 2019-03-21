package ru.yandex.qatools.allure.jenkins;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.JDK;
import hudson.util.StreamTaskListener;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import ru.yandex.qatools.allure.jenkins.testdata.TestUtils;
import ru.yandex.qatools.allure.jenkins.tools.AllureCommandlineInstallation;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author charlie (Dmitry Baev).
 */
public class CommandlineIT {

    @ClassRule
    public static JenkinsRule jRule = new JenkinsRule();

    @ClassRule
    public static TemporaryFolder folder = new TemporaryFolder();

    private static ReportBuilder builder;

    @BeforeClass
    public static void setUp() throws Exception {
        EnvVars envVars = new EnvVars();
        JDK jdk = TestUtils.getJdk(jRule);
        jdk.buildEnvVars(envVars);
        AllureCommandlineInstallation allure = TestUtils.getAllureCommandline(jRule, folder);
        StreamTaskListener listener = new StreamTaskListener(System.out, StandardCharsets.UTF_8);
        Launcher launcher = new Launcher.LocalLauncher(listener);
        FilePath workspace = new FilePath(folder.newFolder());
        workspace.mkdirs();
        builder = new ReportBuilder(launcher, listener, workspace, envVars, allure);
    }

    @Test
    public void shouldGenerateReport() throws Exception {
        FilePath results = new FilePath(folder.newFolder("some with spaces in path (and even more x8)"));
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("sample-testsuite.xml")) {
            results.child("sample-testsuite.xml").copyFrom(is);
        }
        FilePath report = new FilePath(folder.getRoot()).child("some folder with (x22) spaces");
        int exitCode = builder.build(Collections.singletonList(results), report);
        assertThat(exitCode).as("Should exit with code 0").isEqualTo(0);
    }
}
