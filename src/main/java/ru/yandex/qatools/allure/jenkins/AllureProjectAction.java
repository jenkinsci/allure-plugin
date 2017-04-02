package ru.yandex.qatools.allure.jenkins;

import org.kohsuke.stapler.StaplerProxy;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.ProminentProjectAction;


/**
 * @deprecated
 * {@link Action} that shows link to the allure report on the project page
 *
 * @author pupssman
 */
@Deprecated
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
        return AllureReportPlugin.URL_PATH;
    }

    @Override
    public Object getTarget() {
        final AbstractBuild<?, ?> build = project.getLastBuild();
        return build == null ? null : build.getAction(AllureBuildAction.class);
    }
}
