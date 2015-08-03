package ru.yandex.qatools.allure.jenkins.utils;

import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.SettingsBuildingException;
import ru.yandex.qatools.allure.report.AllureReportBuilder;
import ru.yandex.qatools.clay.Aether;
import ru.yandex.qatools.clay.maven.settings.FluentSettingsBuilder;

import java.io.File;
import java.io.IOException;

import static ru.yandex.qatools.clay.Aether.MAVEN_CENTRAL_URL;
import static ru.yandex.qatools.clay.Aether.aether;
import static ru.yandex.qatools.clay.maven.settings.FluentProfileBuilder.newProfile;
import static ru.yandex.qatools.clay.maven.settings.FluentRepositoryBuilder.newRepository;
import static ru.yandex.qatools.clay.maven.settings.FluentSettingsBuilder.loadSettings;

/**
 * eroshenkoam
 * 7/16/14
 */
public class ReportGenerator implements FilePath.FileCallable<FilePath> {

    public static final String RESULTS_PATH = "results";

    public static final String REPORT_PATH = "report";

    private String reportVersion;

    private Proxy proxy;

    public ReportGenerator(String reportVersion, Proxy proxy) {
        this.reportVersion = reportVersion;
        this.proxy = proxy;
    }

    @Override
    public FilePath invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
        File resultsDirectory = new File(f, RESULTS_PATH);
        File reportDirectory = new File(f, REPORT_PATH);
        try {
            Aether aether = createAether(proxy);
            AllureReportBuilder allureReportBuilder = new AllureReportBuilder(reportVersion, reportDirectory, aether);
            allureReportBuilder.processResults(resultsDirectory);
            allureReportBuilder.unpackFace();
        } catch (Exception e) {
            throw new IOException(e);
        }

        return new FilePath(reportDirectory);
    }

    public Aether createAether(Proxy proxy) throws IOException, SettingsBuildingException {
        FluentSettingsBuilder settingsBuilder = loadSettings()
                .withActiveProfile(
                        newProfile()
                                .withId("allure")
                                .withRepository(newRepository().withUrl(MAVEN_CENTRAL_URL))
                );

        Settings settings = settingsBuilder.build();
        if (settings.getActiveProxy() == null && proxy.isActive()) {
            settings.addProxy(proxy);
        }

        return aether(settingsBuilder.build());
    }
}
