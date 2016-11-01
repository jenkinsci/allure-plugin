package io.qameta.jenkins;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.JDK;
import hudson.util.StreamTaskListener;
import io.qameta.jenkins.tools.AllureInstallation;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static io.qameta.jenkins.testdata.TestUtils.getAllureCommandline;
import static io.qameta.jenkins.testdata.TestUtils.getJdk;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

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
        JDK jdk = getJdk(jRule);
        jdk.buildEnvVars(envVars);
        AllureInstallation allure = getAllureCommandline(jRule, folder);
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
        FilePath report = new FilePath(folder.newFolder("some folder with (x22) spaces"));
        int exitCode = builder.build(Collections.singletonList(results), report);
        assertThat("Should exit with code 0", exitCode, is(0));
    }
}
