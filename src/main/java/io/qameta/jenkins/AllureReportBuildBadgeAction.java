package io.qameta.jenkins;

import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildBadgeAction;
import hudson.model.DirectoryBrowserSupport;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;

/**
 * {@link Action} that serves allure report from archive directory on master of a given build.
 *
 * @author pupssman
 */
public class AllureReportBuildBadgeAction implements BuildBadgeAction {

    private final AbstractBuild<?, ?> build;

    public AllureReportBuildBadgeAction(AbstractBuild<?, ?> build) {
        this.build = build;
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

    @SuppressWarnings("unused")
    public String getBuildUrl() {
        return build.getUrl();
    }

    @SuppressWarnings("unused")
    public DirectoryBrowserSupport doDynamic(StaplerRequest req, StaplerResponse rsp)
            throws IOException, ServletException, InterruptedException { //NOSONAR
        AbstractProject<?, ?> project = build.getProject();
        FilePath systemDirectory = new FilePath(new File(build.getRootDir(), "allure-report"));
        return new DirectoryBrowserSupport(this, systemDirectory, project.getDisplayName(), null, false);
    }

}
