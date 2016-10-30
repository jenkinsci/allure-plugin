package io.qameta.jenkins;

import hudson.FilePath;
import hudson.model.Action;
import hudson.model.BuildBadgeAction;
import hudson.model.DirectoryBrowserSupport;
import hudson.model.Job;
import hudson.model.Run;
import jenkins.model.RunAction2;
import jenkins.tasks.SimpleBuildStep;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

/**
 * {@link Action} that serves allure report from archive directory on master of a given build.
 *
 * @author pupssman
 */
public class AllureReportBuildBadgeAction implements BuildBadgeAction, RunAction2, SimpleBuildStep.LastBuildAction {

    private Run<?, ?> run;

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
    public void onAttached(Run<?, ?> r) {
        this.run = r;
    }

    @Override
    public void onLoad(Run<?, ?> r) {
        this.run = r;
    }

    @Override
    public Collection<? extends Action> getProjectActions() {
        Job<?, ?> job = run.getParent();
        return Collections.singleton(new AllureReportProjectAction(job));
    }

    @SuppressWarnings("unused")
    public String getBuildUrl() {
        return run.getUrl();
    }

    @SuppressWarnings("unused")
    public DirectoryBrowserSupport doDynamic(StaplerRequest req, StaplerResponse rsp) //NOSONAR
            throws IOException, ServletException, InterruptedException {
        FilePath allureReport = new FilePath(new File(run.getRootDir(), "allure-report"));
        return new DirectoryBrowserSupport(this, allureReport, run.getFullDisplayName(), null, false);
    }
}
