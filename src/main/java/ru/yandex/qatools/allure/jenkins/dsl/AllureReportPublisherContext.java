package ru.yandex.qatools.allure.jenkins.dsl;

import javaposse.jobdsl.dsl.Context;
import ru.yandex.qatools.allure.jenkins.AllureReportDefaultUploader;
import ru.yandex.qatools.allure.jenkins.AllureReportUploader;
import ru.yandex.qatools.allure.jenkins.config.AllureReportConfig;
import ru.yandex.qatools.allure.jenkins.config.PropertyConfig;
import ru.yandex.qatools.allure.jenkins.config.ReportBuildPolicy;

import java.util.List;

/**
 * @author Marat Mavlutov <mavlyutov@yandex-team.ru>
 */
class AllureReportPublisherContext implements Context {

    public static final String FAILURE_POLICY = "FAILURE";

    private AllureReportConfig config;
    private AllureReportUploader uploader;

    public AllureReportPublisherContext(AllureReportConfig config, AllureReportUploader uploader) {
        this.config = config;
        this.uploader = uploader;
    }

    public AllureReportConfig getConfig() {
        return config;
    }

    public void buildFor(String buildPolicy) {
        String policy = buildPolicy.equals(FAILURE_POLICY) ? ReportBuildPolicy.UNSUCCESSFUL.getValue() : buildPolicy;
        getConfig().setReportBuildPolicy(ReportBuildPolicy.valueOf(policy));
    }

    public void jdk(String jdk) {
        this.getConfig().setJdk(jdk);
    }

    public void commandline(String commandline) {
        getConfig().setCommandline(commandline);
    }

    public void property(String key, String value) {
        getConfig().getProperties().add(new PropertyConfig(key, value));
    }

    public void includeProperties(boolean includeProperties) {
        getConfig().setIncludeProperties(includeProperties);
    }

    public AllureReportUploader getUploader() {
        return uploader == null ? new AllureReportDefaultUploader() : uploader;
    }

    public void uploader(String uploaderName) {
        AllureReportUploader selectedUploader = null;
        List<AllureReportUploader> availableUploaders = AllureReportUploader.all();
        for (AllureReportUploader availableUploader: availableUploaders) {
            if (availableUploader.getDescriptor().getDisplayName().equals(uploaderName)) {
                selectedUploader = availableUploader;
                break;
            }
        }
        if (selectedUploader == null) {
            throw new NullPointerException(String.format("There are no uploaders with name `%s`", uploaderName));
        }
        else {
            this.uploader = selectedUploader;
        }

    }

}
