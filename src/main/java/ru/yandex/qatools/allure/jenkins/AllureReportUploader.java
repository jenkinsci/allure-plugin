package ru.yandex.qatools.allure.jenkins;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.AbstractDescribableImpl;

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
    public abstract String publish(FilePath reportDirectory, AbstractBuild<?, ?> build) throws IOException, InterruptedException, AllureUploadException;

    /**
     *
     * @return all registered AllureReportUploader extensions
     */
    public static ExtensionList<AllureReportUploader> all() {
        return ExtensionList.lookup(AllureReportUploader.class);
    }

    /**
     *  Common exception for allure uploaders
     *  Use it when catching exceptions different from IOException and InterruptedException
     */
    public class AllureUploadException extends Exception {

        public AllureUploadException() {}

        public AllureUploadException(String message) {
            super(message);
        }
    }

}
