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

import hudson.model.Action;
import hudson.model.Job;
import hudson.model.ProminentProjectAction;
import hudson.model.Run;
import org.kohsuke.stapler.StaplerProxy;

/**
 * {@link Action} that shows link to the allure report on the project page.
 */
public class AllureReportProjectAction implements ProminentProjectAction, StaplerProxy {

    private final Job<?, ?> job;

    private final boolean allure3;

    /** Report directory basename ({@code allure-report}, {@code allure2}, …). */
    private final String reportPath;

    public AllureReportProjectAction(final Job<?, ?> job) {
        this(job, false, AllureReportPlugin.REPORT_PATH);
    }

    public AllureReportProjectAction(final Job<?, ?> job, final boolean allure3) {
        this(job, allure3, AllureReportPlugin.REPORT_PATH);
    }

    public AllureReportProjectAction(final Job<?, ?> job, final boolean allure3, final String reportPath) {
        this.job = job;
        this.allure3 = allure3;
        this.reportPath = reportPath == null || reportPath.isEmpty()
                ? AllureReportPlugin.REPORT_PATH
                : reportPath;
    }

    @Override
    public String getDisplayName() {
        return AllureReportPlugin.getTitle(allure3);
    }

    @Override
    public String getIconFileName() {
        // Prefer the publisher configuration — last build may be missing (new job) or A2-only.
        return AllureReportPlugin.getIconFilename(allure3);
    }

    @Override
    public String getUrlName() {
        return AllureReportPlugin.urlNameForReportDir(reportPath);
    }

    @Override
    public Object getTarget() {
        final Run<?, ?> last = job.getLastCompletedBuild();
        if (last == null) {
            return null;
        }
        final String url = getUrlName();
        for (final AllureReportBuildAction action : last.getActions(AllureReportBuildAction.class)) {
            if (url.equals(action.getUrlName())) {
                return action;
            }
        }
        return null;
    }

    public boolean isCanBuildGraph() {
        int dataPointsCount = 0;
        for (AllureReportBuildAction allureBuildAction = getLastAllureBuildAction();
             allureBuildAction != null && dataPointsCount < 2;
             allureBuildAction = allureBuildAction.getPreviousResult()) {
            dataPointsCount++;
        }
        return dataPointsCount >= 2;
    }

    //copied from junit-plugin
    public AllureReportBuildAction getLastAllureBuildAction() {
        final Run<?, ?> tb = job.getLastSuccessfulBuild();
        Run<?, ?> b = job.getLastBuild();
        final String url = getUrlName();
        while (b != null) {
            if (!b.isBuilding()) {
                for (final AllureReportBuildAction a : b.getActions(AllureReportBuildAction.class)) {
                    if (url.equals(a.getUrlName())) {
                        return a;
                    }
                }
            }
            if (b.equals(tb)) {
                // if even the last successful build didn't produce the test result,
                // that means we just don't have any tests configured.
                return null;
            }
            b = b.getPreviousBuild();
        }
        return null;
    }
}
