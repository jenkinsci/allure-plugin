/*
 *  Copyright 2016-2023 Qameta Software OÜ
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package ru.yandex.qatools.allure.jenkins;

import hudson.FilePath;
import hudson.Util;
import hudson.model.Action;
import hudson.model.BuildBadgeAction;
import hudson.model.DirectoryBrowserSupport;
import hudson.model.Job;
import hudson.model.Run;
import hudson.util.ChartUtil;
import hudson.util.DataSetBuilder;
import hudson.util.Graph;
import jenkins.model.RunAction2;
import jenkins.model.lazy.LazyBuildMixIn;
import jenkins.tasks.SimpleBuildStep;
import org.jfree.chart.JFreeChart;
import org.jfree.data.category.CategoryDataset;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import ru.yandex.qatools.allure.jenkins.utils.BuildSummary;
import ru.yandex.qatools.allure.jenkins.utils.ChartUtils;
import ru.yandex.qatools.allure.jenkins.utils.FilePathUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.lang.String.format;

/**
 * {@link Action} that serves allure report from archive directory on master of a given build.
 *
 * @author pupssman
 */
@SuppressWarnings({"ClassDataAbstractionCoupling"})
public class AllureReportBuildAction implements BuildBadgeAction, RunAction2, SimpleBuildStep.LastBuildAction {

    private static final String ALLURE_REPORT = "allure-report";
    private static final String CACHE_CONTROL = "Cache-Control";
    private static final String WAS_ATTACHED_TO_BOTH = "%s was attached to both %s and %s";
    private static final String SLASH = "/";
    private Run<?, ?> run;

    private transient WeakReference<BuildSummary> buildSummary;

    private String reportPath;

    AllureReportBuildAction(final BuildSummary buildSummary) {
        this.buildSummary = new WeakReference<>(buildSummary);
        this.reportPath = ALLURE_REPORT;
    }

    private String getReportPath() {
        return this.reportPath == null ? ALLURE_REPORT : this.reportPath;
    }

    public void setReportPath(final FilePath reportPath) {
        this.reportPath = reportPath.getName();
    }

    public void doGraph(final StaplerRequest req, final StaplerResponse rsp) throws IOException {
        final CategoryDataset data = buildDataSet();

        new Graph(-1, 600, 300) {
            @Override
            protected JFreeChart createGraph() {
                return ChartUtils.createChart(req, data);
            }
        }.doPng(req, rsp);
    }

    public void doGraphMap(final StaplerRequest req, final StaplerResponse rsp) throws IOException {
        final CategoryDataset data = buildDataSet();

        new Graph(-1, 600, 300) {
            @Override
            protected JFreeChart createGraph() {
                return ChartUtils.createChart(req, data);
            }
        }.doMap(req, rsp);
    }

    public boolean hasSummaryLink() {
        return this.buildSummary != null;
    }

    public BuildSummary getBuildSummary() {
        if (this.buildSummary == null || this.buildSummary.get() == null) {
            this.buildSummary = new WeakReference<>(FilePathUtils.extractSummary(run, this.getReportPath()));
        }
        final BuildSummary data = this.buildSummary.get();
        return data != null ? data : new BuildSummary();
    }

    public long getFailedCount() {
        return getBuildSummary().getFailedCount();
    }

    public long getPassedCount() {
        return getBuildSummary().getPassedCount();
    }

    public long getSkipCount() {
        return getBuildSummary().getSkipCount();
    }

    public long getBrokenCount() {
        return getBuildSummary().getBrokenCount();
    }

    public long getUnknownCount() {
        return getBuildSummary().getUnknownCount();
    }

    public long getTotalCount() {
        return getFailedCount() + getBrokenCount() + getPassedCount()
            + getSkipCount() + getUnknownCount();
    }

    public String getBuildNumber() {
        return run.getId();
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

    public AllureReportBuildAction getPreviousResult() {
        return getPreviousResult(true);
    }

    //copied from junit-plugin
    @SuppressWarnings("PMD.CognitiveComplexity")
    private AllureReportBuildAction getPreviousResult(final boolean eager) {
        Run<?, ?> b = run;
        final Set<Integer> loadedBuilds;
        if (!eager && run.getParent() instanceof LazyBuildMixIn.LazyLoadingJob) {
            loadedBuilds = ((LazyBuildMixIn.LazyLoadingJob<?, ?>)
                run.getParent()).getLazyBuildMixIn()._getRuns().getLoadedBuilds().keySet();
        } else {
            loadedBuilds = null;
        }
        while (true) {
            b = loadedBuilds == null
                || loadedBuilds.contains(b.number - /* assuming there are no gaps */1)
                ? b.getPreviousBuild() : null;
            if (b == null) {
                return null;
            }
            final AllureReportBuildAction r = b.getAction(AllureReportBuildAction.class);
            if (r != null) {
                if (r.equals(this)) {
                    throw new IllegalStateException(format(WAS_ATTACHED_TO_BOTH, this, b, run));
                }
                if (r.run.number != b.number) {
                    throw new IllegalStateException(format(WAS_ATTACHED_TO_BOTH, r, b, r.run));
                }
                return r;
            }
        }
    }

    //copied from junit-plugin
    @Override
    public Collection<? extends Action> getProjectActions() {
        final Job<?, ?> job = run.getParent();
        if (/* getAction(Class) and getAllActions() produces a StackOverflowError */
            !Util.filter(job.getActions(), AllureReportProjectAction.class).isEmpty()) {
            // JENKINS-26077: someone like XUnitPublisher already added one
            return Collections.emptySet();
        }
        return Collections.singleton(new AllureReportProjectAction(job));
    }

    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private CategoryDataset buildDataSet() {
        final DataSetBuilder<String, ChartUtil.NumberOnlyBuildLabel> dsb = new DataSetBuilder<>();

        for (AllureReportBuildAction a = this; a != null; a = a.getPreviousResult()) {
            final ChartUtil.NumberOnlyBuildLabel columnKey = new ChartUtil.NumberOnlyBuildLabel(a.run);
            dsb.add(a.getFailedCount(), "a_failed", columnKey);
            dsb.add(a.getBrokenCount(), "b_broken", columnKey);
            dsb.add(a.getPassedCount(), "c_passed", columnKey);
            dsb.add(a.getSkipCount(), "d_skipped", columnKey);
            dsb.add(a.getUnknownCount(), "e_unknown", columnKey);
        }
        return dsb.build();
    }

    @SuppressWarnings("unused")
    public String getBuildUrl() {
        return run.getUrl();
    }

    @Override
    public void onAttached(final Run<?, ?> attachedRun) {
        this.run = attachedRun;
    }

    @Override
    public void onLoad(final Run<?, ?> loadedRun) {
        this.run = loadedRun;
    }

    @SuppressWarnings("unused")
    public Object doDynamic(final StaplerRequest request, final StaplerResponse response)
        throws IOException, InterruptedException {

        final FilePath runRootDir = new FilePath(run.getRootDir());
        final String reportDirName = getReportPath();

        final FilePath reportDirectoryUnderBuild = runRootDir.child(reportDirName);
        if (reportDirectoryUnderBuild.exists()) {
            return new DirectoryReportBrowser(reportDirectoryUnderBuild);
        }

        final FilePath archivedZip = runRootDir.child("archive/allure-report.zip");
        if (archivedZip.exists()) {
            final ArchiveReportBrowser browser = new ArchiveReportBrowser(archivedZip);
            browser.setReportPath(reportDirName);
            return browser;
        }

        response.sendError(
            HttpServletResponse.SC_NOT_FOUND,
            "Allure report not found. Neither directory '" + reportDirectoryUnderBuild.getRemote()
                + "' nor archive '" + archivedZip.getRemote() + "' exists."
        );
        return null;
    }

    private static final class DirectoryReportBrowser implements HttpResponse {

        private final FilePath baseDirectory;

        DirectoryReportBrowser(final FilePath baseDirectory) {
            this.baseDirectory = baseDirectory;
        }

        @Override
        public void generateResponse(final StaplerRequest request,
            final StaplerResponse response,
            final Object node) throws IOException, ServletException {

            response.setHeader("Content-Security-Policy", "");
            response.setHeader("X-Content-Type-Options", "nosniff");

            String relativePath = request.getRestOfPath();
            if (relativePath == null || relativePath.isEmpty() || SLASH.equals(relativePath)) {
                relativePath = "index.html";
            } else if (relativePath.startsWith(SLASH)) {
                relativePath = relativePath.substring(1);
            }

            if (relativePath.contains("..")) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Illegal path");
                return;
            }

            final FilePath fileToServe = baseDirectory.child(relativePath);

            try {
                if (!fileToServe.exists()) {
                    response.sendRedirect2(request.getRequestURI().replaceAll("/+$", SLASH));
                    return;
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while checking report file existence", interrupted);
            }

            try (InputStream inputStream = fileToServe.read()) {
                response.serveFile(
                    request,
                    inputStream,
                    -1L, -1L, -1L,
                    fileToServe.getName()
                );
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while reading report file", interrupted);
            }
        }
    }

    /**
     * {@link DirectoryBrowserSupport} a modified browser support class that serves from an archive.
     */
    private static class ArchiveReportBrowser implements HttpResponse {

        private final FilePath archive;

        private String reportPath;

        ArchiveReportBrowser(final FilePath archive) {
            this.archive = archive;
            this.reportPath = ALLURE_REPORT;
        }

        public void setReportPath(final String reportPath) {
            this.reportPath = reportPath;
        }

        @Override
        public void generateResponse(final StaplerRequest req,
            final StaplerResponse rsp,
            final Object node)
            throws IOException, ServletException {
            rsp.setHeader(CACHE_CONTROL, "no-cache, no-store, must-revalidate");
            rsp.addHeader(CACHE_CONTROL, "post-check=0, pre-check=0");
            rsp.setHeader("Pragma", "no-cache");
            rsp.setDateHeader("Expires", 0);

            final String path = req.getRestOfPath().isEmpty() ? "/index.html" : req.getRestOfPath();
            try (ZipFile allureReport = new ZipFile(archive.getRemote())) {
                final ZipEntry entry = allureReport.getEntry(this.reportPath + path);
                if (entry != null) {
                    rsp.serveFile(req, allureReport.getInputStream(entry), -1L, -1L, -1L, entry.getName());
                } else {
                    rsp.sendRedirect("/index.html#404");
                }
            }
        }
    }
}
