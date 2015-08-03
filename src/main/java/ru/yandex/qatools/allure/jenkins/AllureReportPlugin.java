package ru.yandex.qatools.allure.jenkins;

import java.io.File;

import hudson.Plugin;
import hudson.PluginWrapper;
import hudson.model.AbstractBuild;
import jenkins.model.Jenkins;

/**
 * User: eroshenkoam
 * Date: 10/9/13, 8:29 PM
 */
public class AllureReportPlugin extends Plugin {

    public static final String TITLE = "Allure Report";

    public static final String DESCRIPTION = "Allure Report Generation";

    public static final String URL_PATH = "allure";

    public static final String REPORT_PATH = "allure-report";

    /**
     * @deprecated since 1.0
    */
    @Deprecated
    public static final String REPORT_PATH_OLD = "allure-reports";

    public static final String DEFAULT_RESULTS_PATTERN = "**/allure-results";

    public static final String DEFAULT_REPORT_VERSION = "1.3.9";

    public static final String DEFAULT_URL_PATTERN = "%s";

    public static final String DEFAULT_ISSUE_TRACKER_PATTERN = DEFAULT_URL_PATTERN;

    public static final String DEFAULT_TMS_PATTERN = DEFAULT_URL_PATTERN;

    public static File getReportBuildDirectory(AbstractBuild<?, ?> build) {
        if (build != null) {
            File oldReportPath = new File(build.getRootDir(), REPORT_PATH_OLD);
            return oldReportPath.exists() ? oldReportPath : new File(build.getRootDir(), REPORT_PATH);
        } else {
            return null;
        }
    }

    public static String getTitle() {
        return TITLE;
    }

    public static String getIconFilename() {
        PluginWrapper wrapper = Jenkins.getInstance().getPluginManager().getPlugin(AllureReportPlugin.class);
        return String.format("/plugin/%s/img/icon.png", wrapper.getShortName());
    }


}
