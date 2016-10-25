package io.qameta.jenkins;

import hudson.FilePath;
import hudson.model.Action;
import hudson.model.BuildBadgeAction;
import hudson.model.DirectoryBrowserSupport;
import hudson.model.Run;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;

/**
 * {@link Action} that serves allure report from archive directory on master of a given build.
 *
 * @author pupssman
 */
public class AllureReportBuildBadgeAction implements BuildBadgeAction {

    private final Run<?, ?> run;

    public AllureReportBuildBadgeAction(@Nonnull Run<?, ?> run) {
        this.run = run;
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
        return run.getUrl();
    }

    @SuppressWarnings("unused")
    public DirectoryBrowserSupport doDynamic(StaplerRequest req, StaplerResponse rsp) //NOSONAR
            throws IOException, ServletException, InterruptedException {
        FilePath allureReport = new FilePath(new File(run.getRootDir(), "allure-report"));
        return new DirectoryBrowserSupport(this, allureReport, run.getFullDisplayName(), null, false);
    }

}
