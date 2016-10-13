package ru.yandex.qatools.allure.jenkins;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.io.PrintStream;

import static ru.yandex.qatools.allure.jenkins.AllureReportPlugin.getMasterReportFilePath;

/**
 * Created by lawyard on 19.09.16.
 */


@Extension
public class AllureReportDefaultUploader extends AllureReportUploader {

    @DataBoundConstructor
    public AllureReportDefaultUploader() {}

    @Override
    public String publish(FilePath reportDirectory, AbstractBuild<?, ?> build, PrintStream logger) throws IOException, InterruptedException {

        reportDirectory.copyRecursiveTo(getMasterReportFilePath(build));
        logger.println(String.format("Report successfully saved to `%s`", getMasterReportFilePath(build)));
        return Jenkins.getInstance().getRootUrl() + build.getUrl() + getAllureUrlName();

    }

    private String getAllureUrlName() {
        return AllureReportPlugin.URL_PATH;
    }

    @Extension
    public static class AllureReportDefaultUploaderDescriptor extends Descriptor<AllureReportUploader> {
        /**
         *
         * @return Uploader Name
         */
        @Override
        public String getDisplayName() {
            return "Jenkins Master";
        }
    }

}
