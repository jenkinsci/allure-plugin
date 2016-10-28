package ru.yandex.qatools.allure.jenkins;

import org.kohsuke.stapler.StaplerProxy;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.ProminentProjectAction;


/**
 * {@link Action} that shows link to the allure report on the project page
 *
 * @author pupssman
 */
public class AllureProjectAction implements ProminentProjectAction, StaplerProxy {
    private final AbstractProject<?, ?> project;

    public AllureProjectAction(AbstractProject<?, ?> project) {
        this.project = project;
    }

    @Override
    public String getDisplayName() {
        return AllureReportPlugin.getTitle();
    }

    @Override
    public String getIconFileName() {
        return this.getTarget() != null ? AllureReportPlugin.getIconFilename() : null;
    }

    @Override
    public String getUrlName() {
        AllureBuildAction buildAction = project.getLastBuild().getAction(AllureBuildAction.class);
        return buildAction != null ? buildAction.getUrlName() : null;
    }

    @SuppressWarnings("unused")
    public String getBadgeUrl() {
        AllureBuildAction buildAction = project.getLastBuild().getAction(AllureBuildAction.class);
        return buildAction != null ? buildAction.getBadgeUrl() : null;
    }

    @Override
    public Object getTarget() {
        AbstractBuild<?, ?> build = project.getLastBuild();
        return build != null ? build.getAction(AllureBuildAction.class) : null;
    }

}
