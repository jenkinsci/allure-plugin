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

/**
 * User: eroshenkoam.
 * Date: 10/9/13, 8:29 PM
 */
public class AllureReportPlugin extends Plugin {

    public static final String URL_PATH = "allure";

    public static final String REPORT_PATH = "allure-report";

    public static final String DEFAULT_RESULTS_PATTERN = "allure-results";

    public static final String DEFAULT_URL_PATTERN = "%s";

    public static final String DEFAULT_ISSUE_TRACKER_PATTERN = DEFAULT_URL_PATTERN;

    public static final String DEFAULT_TMS_PATTERN = DEFAULT_URL_PATTERN;

    public static FilePath getMasterReportFilePath(final AbstractBuild<?, ?> build) {
        final File file = getReportBuildDirectory(build);
        return file == null ? null : new FilePath(file);
    }

    @SuppressWarnings("deprecation")
    public static File getReportBuildDirectory(final AbstractBuild<?, ?> build) {
        return build == null ? null : new File(build.getRootDir(), REPORT_PATH);
    }

    public static String getTitle() {
        return Messages.AllureReportPlugin_Title();
    }

    public static String getIconFilename() {
        final PluginWrapper wrapper = Jenkins.get().getPluginManager().getPlugin(AllureReportPlugin.class);
        return wrapper == null ? "" : String.format("/plugin/%s/img/icon.png", wrapper.getShortName());
    }


}
