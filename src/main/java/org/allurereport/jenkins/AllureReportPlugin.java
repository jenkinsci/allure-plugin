/*
 *  Copyright 2016-2023 Qameta Software OÜ
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.allurereport.jenkins;

import hudson.FilePath;
import hudson.Plugin;
import hudson.PluginWrapper;
import hudson.model.AbstractBuild;
import jenkins.model.Jenkins;

import java.io.File;

public class AllureReportPlugin extends Plugin {

    public static final String URL_PATH = "allure";

    public static final String REPORT_PATH = "allure-report";

    public static final String DEFAULT_RESULTS_PATTERN = "allure-results";

    public static final String DEFAULT_URL_PATTERN = "%s";

    public static final String DEFAULT_ISSUE_TRACKER_PATTERN = DEFAULT_URL_PATTERN;

    public static final String DEFAULT_TMS_PATTERN = DEFAULT_URL_PATTERN;

    private static final String PLUGIN_IMG_PATH = "/plugin/%s/img/%s";

    public static FilePath getMasterReportFilePath(final AbstractBuild<?, ?> build) {
        final File file = getReportBuildDirectory(build);
        return file == null ? null : new FilePath(file);
    }

    @SuppressWarnings("deprecation")
    public static File getReportBuildDirectory(final AbstractBuild<?, ?> build) {
        return build == null ? null : new File(build.getRootDir(), REPORT_PATH);
    }

    /**
     * Jenkins action URL for a report directory name.
     * <p>
     * Classic default: disk folder {@code allure-report} → URL {@code /allure}.
     * Dual / custom: set Report path explicitly (e.g. {@code allure2}, {@code allure3})
     * — that basename becomes the URL slug so two publishers can coexist.
     */
    public static String urlNameForReportDir(final String reportDir) {
        if (reportDir == null || reportDir.isEmpty()) {
            return URL_PATH;
        }
        String name = reportDir;
        final int slash = Math.max(reportDir.lastIndexOf('/'), reportDir.lastIndexOf('\\'));
        if (slash >= 0 && slash < reportDir.length() - 1) {
            name = reportDir.substring(slash + 1);
        }
        if (REPORT_PATH.equals(name) || URL_PATH.equals(name)) {
            return URL_PATH;
        }
        return name;
    }

    public static String getTitle() {
        return Messages.AllureReportPlugin_Title();
    }

    public static String getTitle(final boolean allure3) {
        return allure3 ? Messages.AllureReportPlugin_TitleAllure3() : Messages.AllureReportPlugin_TitleAllure2();
    }

    public static String getIconFilename() {
        return getIconFilename(false);
    }

    /**
     * Sidebar / summary icon for the report action.
     * Tight viewBox (full-bleed mark) — same visual weight as Allure TestOps.
     *
     * @param allure3 {@code true} → Allure 3 mark; {@code false} → Allure 2 (also default {@code icon.png})
     */
    public static String getIconFilename(final boolean allure3) {
        final PluginWrapper wrapper = Jenkins.get().getPluginManager().getPlugin(AllureReportPlugin.class);
        if (wrapper == null) {
            return "";
        }
        final String file = allure3 ? "icon-allure3.svg" : "icon-allure2.svg";
        return String.format(PLUGIN_IMG_PATH, wrapper.getShortName(), file);
    }

    /**
     * Build-history badge icon (16×16). Same tight marks as {@link #getIconFilename(boolean)}.
     */
    public static String getBadgeIconFilename(final boolean allure3) {
        return getIconFilename(allure3);
    }

    public static String getBadgeIconFilename() {
        return getBadgeIconFilename(false);
    }


}
