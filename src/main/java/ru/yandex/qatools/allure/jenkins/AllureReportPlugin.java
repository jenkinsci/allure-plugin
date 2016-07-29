package ru.yandex.qatools.allure.jenkins;

import hudson.FilePath;
import hudson.Plugin;
import hudson.PluginWrapper;
import hudson.model.AbstractBuild;
import jenkins.model.Jenkins;

import java.io.File;

/**
 * User: eroshenkoam
 * Date: 10/9/13, 8:29 PM
 */
public class AllureReportPlugin extends Plugin {

    public static final String URL_PATH = "allure";

    public static final String REPORT_PATH = "allure-report";

    public static final String DEFAULT_RESULTS_PATTERN = "allure-results";

    public static final String DEFAULT_URL_PATTERN = "%s";

    public static final String DEFAULT_ISSUE_TRACKER_PATTERN = DEFAULT_URL_PATTERN;

    public static final String DEFAULT_TMS_PATTERN = DEFAULT_URL_PATTERN;

    public static FilePath getMasterReportFilePath(AbstractBuild<?, ?> build) {
        File file = getReportBuildDirectory(build);
        return file == null ? null : new FilePath(file);
    }

    @SuppressWarnings("deprecation")
    public static File getReportBuildDirectory(AbstractBuild<?, ?> build) {
        return build == null ? null : new File(build.getRootDir(), REPORT_PATH);
    }

    public static String getTitle() {
        return Messages.AllureReportPlugin_Title();
    }

    public static String getIconFilename() {
        PluginWrapper wrapper = Jenkins.getInstance().getPluginManager().getPlugin(AllureReportPlugin.class);
        return wrapper == null ? "" : String.format("/plugin/%s/img/icon.png", wrapper.getShortName());
    }


}
