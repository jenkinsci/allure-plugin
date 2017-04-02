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
}
