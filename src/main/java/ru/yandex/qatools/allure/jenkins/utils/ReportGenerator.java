package ru.yandex.qatools.allure.jenkins.utils;

import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import org.apache.maven.settings.building.SettingsBuildingException;
import ru.yandex.qatools.allure.jenkins.config.ProxySettingsConfig;
import ru.yandex.qatools.allure.report.AllureReportBuilder;
import ru.yandex.qatools.clay.Aether;
import ru.yandex.qatools.clay.maven.settings.FluentSettingsBuilder;

import java.io.File;
import java.io.IOException;

import static ru.yandex.qatools.clay.Aether.MAVEN_CENTRAL_URL;
import static ru.yandex.qatools.clay.Aether.aether;
import static ru.yandex.qatools.clay.maven.settings.FluentProfileBuilder.newProfile;
import static ru.yandex.qatools.clay.maven.settings.FluentProxyBuilder.newProxy;
import static ru.yandex.qatools.clay.maven.settings.FluentRepositoryBuilder.newRepository;
import static ru.yandex.qatools.clay.maven.settings.FluentSettingsBuilder.loadSettings;

/**
 * eroshenkoam
 * 7/16/14
 */
public class ReportGenerator implements FilePath.FileCallable<FilePath> {

    public static final String SONATYPE_URL = "https://oss.sonatype.org/content/repositories/releases";

    public static final String RESULTS_PATH = "results";

    public static final String REPORT_PATH = "report";

    private ProxySettingsConfig proxySettings;

    private String reportVersion;

    public ReportGenerator(String reportVersion, ProxySettingsConfig proxySettings) {
        this.reportVersion = reportVersion;
        this.proxySettings = proxySettings;
    }

    @Override
    public FilePath invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
        File resultsDirectory = new File(f, RESULTS_PATH);
        File reportDirectory = new File(f, REPORT_PATH);
        try {
            Aether aether = createAether(proxySettings);
            AllureReportBuilder allureReportBuilder = new AllureReportBuilder(reportVersion, reportDirectory, aether);
            allureReportBuilder.processResults(resultsDirectory);
            allureReportBuilder.unpackFace();
        } catch (Exception e) {
            throw new IOException(e);
        }

        return new FilePath(reportDirectory);
    }

    public Aether createAether(ProxySettingsConfig proxySettings) throws IOException, SettingsBuildingException {
        FluentSettingsBuilder settingsBuilder = loadSettings()
                .withActiveProfile(
                        newProfile()
                                .withId("allure")
                                .withRepository(newRepository().withUrl(MAVEN_CENTRAL_URL))
                                .withRepository(newRepository().withUrl(SONATYPE_URL))
                );
        if (proxySettings.isActive()) {
            settingsBuilder.withProxy(
                    newProxy()
                            .withId("allure-http")
                            .withProtocol("http")
                            .withHost(proxySettings.getHost())
                            .withPort(proxySettings.getPort())
                            .withUsername(proxySettings.getUsername())
                            .withPassword(proxySettings.getPassword())
            ).withProxy(
                    newProxy()
                            .withId("allure-https")
                            .withProtocol("https")
                            .withHost(proxySettings.getHost())
                            .withPort(proxySettings.getPort())
                            .withUsername(proxySettings.getUsername())
                            .withPassword(proxySettings.getPassword())
            );
        }
        return aether(settingsBuilder.build());
    }
}
