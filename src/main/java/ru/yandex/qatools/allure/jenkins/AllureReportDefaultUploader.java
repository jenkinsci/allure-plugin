package ru.yandex.qatools.allure.jenkins;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

import java.io.IOException;

import static ru.yandex.qatools.allure.jenkins.AllureReportPlugin.getMasterReportFilePath;

/**
 * Created by lawyard on 19.09.16.
 */


public class AllureReportDefaultUploader extends AllureReportUploader {

    @Override
    public String publish(FilePath reportDirectory, AbstractBuild<?, ?> build) throws IOException, InterruptedException {

        reportDirectory.copyRecursiveTo(getMasterReportFilePath(build));
        return Jenkins.getInstance().getRootUrl() + build.getUrl() + getUrlName();

    }

    private String getUrlName() {
        return AllureReportPlugin.URL_PATH;
    }

    @Extension
    public static class AllureReportDefaultUploaderDescriptor extends Descriptor<AllureReportUploader> {
        @Override
        public String getDisplayName() {
            return "Upload to Jenkins Master";
        }
    }

}
