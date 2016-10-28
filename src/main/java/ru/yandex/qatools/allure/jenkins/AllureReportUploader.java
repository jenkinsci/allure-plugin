package ru.yandex.qatools.allure.jenkins;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.AbstractDescribableImpl;
import ru.yandex.qatools.allure.jenkins.exceptions.AllureUploadException;

import java.io.PrintStream;


/**
 * Created by lawyard on 19.09.16.
 */

public abstract class AllureReportUploader extends AbstractDescribableImpl<AllureReportUploader> implements ExtensionPoint {

    /**
     *
     * @param reportDirectory Directory with generated allure report
     * @param build current build instance
     * @param logger current listener logger to print to console output
     * @return url with allure report location
     * @throws AllureUploadException
     */
    public abstract String publish(FilePath reportDirectory, AbstractBuild<?, ?> build, PrintStream logger) throws AllureUploadException;

    /**
     *
     * @return all registered AllureReportUploader extensions
     */
    public static ExtensionList<AllureReportUploader> all() {
        return ExtensionList.lookup(AllureReportUploader.class);
    }

}
