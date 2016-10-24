package io.qameta.jenkins;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.ProminentProjectAction;
import org.kohsuke.stapler.StaplerProxy;

/**
 * {@link Action} that shows link to the allure report on the project page
 *
 * @author pupssman
 */
public class AllureReportProjectAction implements ProminentProjectAction, StaplerProxy {
    private final AbstractProject<?, ?> project;

    public AllureReportProjectAction(AbstractProject<?, ?> project) {
        this.project = project;
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
        AbstractBuild<?, ?> build = project.getLastBuild();
        return build != null ? build.getAction(AllureReportBuildBadgeAction.class) : null;
    }
}
