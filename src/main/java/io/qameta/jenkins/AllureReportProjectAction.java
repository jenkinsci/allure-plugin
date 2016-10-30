package io.qameta.jenkins;

import hudson.model.Action;
import hudson.model.Job;
import hudson.model.ProminentProjectAction;
import hudson.model.Run;
import org.kohsuke.stapler.StaplerProxy;

import java.util.Objects;

/**
 * {@link Action} that shows link to the allure report on the project page
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
        return "/plugin/allure/icon.png";
    }

    @Override
    public String getUrlName() {
        return "allure";
    }

    @Override
    public Object getTarget() {
        Run<?, ?> last = job.getLastCompletedBuild();
        if (Objects.nonNull(last)) {
            return last.getAction(AllureReportBuildBadgeAction.class);
        }
        return null;
    }
}
