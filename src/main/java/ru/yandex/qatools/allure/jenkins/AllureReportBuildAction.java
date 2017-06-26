package ru.yandex.qatools.allure.jenkins;

import hudson.FilePath;
import hudson.model.Action;
import hudson.model.BuildBadgeAction;
import hudson.model.DirectoryBrowserSupport;
import hudson.model.Job;
import hudson.model.Run;
import jenkins.model.RunAction2;
import jenkins.tasks.SimpleBuildStep;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * {@link Action} that serves allure report from archive directory on master of a given build.
 *
 * @author pupssman
 */
public class AllureReportBuildAction implements BuildBadgeAction, RunAction2, SimpleBuildStep.LastBuildAction {

    private Run<?, ?> run;

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
    public void onAttached(Run<?, ?> r) {
        this.run = r;
    }

    @Override
    public void onLoad(Run<?, ?> r) {
        this.run = r;
    }

    @Override
    public Collection<? extends Action> getProjectActions() {
        final Job<?, ?> job = run.getParent();
        return Collections.singleton(new AllureReportProjectAction(job));
    }

    @SuppressWarnings("unused")
    public String getBuildUrl() {
        return run.getUrl();
    }

    @SuppressWarnings("unused")
    public ArchiveReportBrowser doDynamic(StaplerRequest req, StaplerResponse rsp)
            throws IOException, ServletException, InterruptedException {
        final FilePath archive = new FilePath(run.getRootDir()).child("archive/allure-report.zip");
        return new ArchiveReportBrowser(archive);
    }

    /**
     * {@link DirectoryBrowserSupport} a modified browser support class that serves from an archive.
     */
    private static class ArchiveReportBrowser implements HttpResponse {

        private final FilePath archive;

        ArchiveReportBrowser(FilePath archive) {
            this.archive = archive;
        }

        @Override
        public void generateResponse(final StaplerRequest req, final StaplerResponse rsp, final Object node)
                throws IOException, ServletException {
            rsp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            rsp.addHeader("Cache-Control", "post-check=0, pre-check=0");
            rsp.setHeader("Pragma", "no-cache");
            rsp.setDateHeader("Expires", 0);

            final String path = req.getRestOfPath().isEmpty() ? "/index.html" : req.getRestOfPath();
            try (ZipFile allureReport = new ZipFile(archive.getRemote())) {
                final ZipEntry entry = allureReport.getEntry("allure-report" + path);
                if (entry != null) {
                    rsp.serveFile(req, allureReport.getInputStream(entry), -1L, -1L, -1L, entry.getName());
                } else {
                    rsp.sendRedirect("/index.html#404");
                }
            }
        }
    }
}
