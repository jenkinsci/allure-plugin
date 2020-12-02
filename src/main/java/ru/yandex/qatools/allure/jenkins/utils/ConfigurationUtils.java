package ru.yandex.qatools.allure.jenkins.utils;

import jenkins.model.Jenkins;

/**
 * @author eliasnogueira eliasnogueira
 */
public class ConfigurationUtils {

    private static final String ALLURE_INITIAL_FILENAME = "allure-report";
    private static final String ZIP = ".zip";

    private ConfigurationUtils() {}

    /**
     * Return the filename with job name
     * @return filename with job name as suffix
     */
    public static String getFileName() {
        String jobName = Jenkins.getActiveInstance().getJobNames().iterator().next();

        return jobName == null ? ALLURE_INITIAL_FILENAME + ZIP : ALLURE_INITIAL_FILENAME + "-" + jobName + ZIP;
    }

}