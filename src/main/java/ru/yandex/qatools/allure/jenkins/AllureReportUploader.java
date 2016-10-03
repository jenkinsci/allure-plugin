package ru.yandex.qatools.allure.jenkins;

import hudson.ExtensionPoint;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.AbstractDescribableImpl;
import jenkins.model.Jenkins;

import java.io.IOException;


/**
 * Created by lawyard on 19.09.16.
 */

public abstract class AllureReportUploader extends AbstractDescribableImpl<AllureReportUploader> implements ExtensionPoint {

    /**
     *
     * @param reportDirectory Directory with generated allure report
     * @param build current build instance
     * @return url with allure report location
     * @throws IOException
     * @throws InterruptedException
     */
    public abstract String publish(FilePath reportDirectory, AbstractBuild<?, ?> build) throws IOException, InterruptedException;

    /**
     *
     * @return Uploader short name
     */
    public abstract String getShortName();

    /**
     *
     * @return all registered AllureReportUploader extensions
     */
    public static ExtensionList<AllureReportUploader> all() {
        return Jenkins.getActiveInstance().getExtensionList(AllureReportUploader.class);
    }


}
