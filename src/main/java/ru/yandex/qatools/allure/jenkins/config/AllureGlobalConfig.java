package ru.yandex.qatools.allure.jenkins.config;

import ru.yandex.qatools.allure.jenkins.AllureReportDefaultUploader;
import ru.yandex.qatools.allure.jenkins.AllureReportUploader;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Artem Eroshenko eroshenkoam@yandex-team.ru
 */
public class AllureGlobalConfig {

    private List<PropertyConfig> properties;
    private AllureReportUploader uploader;

    public List<PropertyConfig> getProperties() {
        if (properties == null) {
            properties = new ArrayList<>();
        }
        return properties;
    }

    public void setProperties(List<PropertyConfig> properties) {
        this.properties = properties;
    }

    public AllureReportUploader getUploader() {
        return uploader == null ? new AllureReportDefaultUploader() : uploader;
    }

    public void setUploader(String uploader) {
        List<AllureReportUploader> availableUploaders = AllureReportUploader.all();
        for (AllureReportUploader availableUploader: availableUploaders) {
            if (availableUploader.getDescriptor().getDisplayName().equals(uploader)) {
                this.uploader = availableUploader;
                break;
            }
        }
    }

    public static AllureGlobalConfig newInstance() {
        return new AllureGlobalConfig();
    }
}
