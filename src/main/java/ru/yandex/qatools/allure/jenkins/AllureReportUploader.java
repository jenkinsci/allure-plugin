package ru.yandex.qatools.allure.jenkins;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import jenkins.model.Jenkins;

import java.io.IOException;


/**
 * Created by lawyard on 19.09.16.
 */

public abstract class AllureReportUploader implements ExtensionPoint {

    public abstract String publish(FilePath reportDirectory, AbstractBuild<?, ?> build) throws IOException, InterruptedException;

    public static ExtensionList<AllureReportUploader> all() {
        return Jenkins.getInstance().getExtensionList(AllureReportUploader.class);
    }

}
