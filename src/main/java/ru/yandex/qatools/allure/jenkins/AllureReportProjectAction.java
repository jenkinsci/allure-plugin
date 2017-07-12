package ru.yandex.qatools.allure.jenkins;

import hudson.model.Action;
import hudson.model.Job;
import hudson.model.ProminentProjectAction;
import hudson.model.Run;
import org.kohsuke.stapler.StaplerProxy;

/**
 * {@link Action} that shows link to the allure report on the project page.
 *
 * @author pupssman
 */
public class AllureReportProjectAction implements ProminentProjectAction, StaplerProxy {

    private final Job<?, ?> job;

    public AllureReportProjectAction(Job<?, ?> job) {
        this.job = job;
    }

    @Override
    public String getDisplayName() {
        return Messages.AllureReportPlugin_Title();
    }

    @Override
    public String getIconFileName() {
        return AllureReportPlugin.getIconFilename();
    }

    @Override
    public String getUrlName() {
        return AllureReportPlugin.URL_PATH;
    }

    @Override
    public Object getTarget() {
        final Run<?, ?> last = job.getLastCompletedBuild();
        return last == null ? null : last.getAction(AllureReportBuildAction.class);
    }

    public boolean isCanBuildGraph() {
        int dataPointsCount = 0;
        AllureReportBuildAction allureBuildAction = getLastAllureBuildAction();
        while (dataPointsCount < 2) {
            if (allureBuildAction == null) {
                return false;
            }
            if (allureBuildAction.hasSummaryLink()) {
                dataPointsCount++;
            }
            allureBuildAction = allureBuildAction.getPreviousResult();
        }
        return true;
    }

    //copied from junit-plugin
    public AllureReportBuildAction getLastAllureBuildAction() {
        final Run<?, ?> tb = job.getLastSuccessfulBuild();
        Run<?, ?> b = job.getLastBuild();
        while (b != null) {
            final AllureReportBuildAction a = b.getAction(AllureReportBuildAction.class);
            if (a != null && (!b.isBuilding())) {
                return a;
            }
            if (b == tb) {
                // if even the last successful build didn't produce the test result,
                // that means we just don't have any tests configured.
                return null;
            }
            b = b.getPreviousBuild();
        }
        return null;
    }
}
