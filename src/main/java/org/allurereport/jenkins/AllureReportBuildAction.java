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
package org.allurereport.jenkins;

import hudson.FilePath;
import hudson.Util;
import hudson.model.Action;
import hudson.model.BuildBadgeAction;
import hudson.model.Job;
import hudson.model.Run;
import hudson.util.ChartUtil;
import hudson.util.DataSetBuilder;
import hudson.util.Graph;
import jenkins.model.RunAction2;
import jenkins.model.lazy.LazyBuildMixIn;
import jenkins.tasks.SimpleBuildStep;
import org.allurereport.jenkins.utils.AllureReportArchiveSource;
import org.allurereport.jenkins.utils.AllureReportArchiveSourceFactory;
import org.allurereport.jenkins.utils.BuildSummary;
import org.allurereport.jenkins.utils.ChartUtils;
import org.allurereport.jenkins.utils.FilePathUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.data.category.CategoryDataset;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import static java.lang.String.format;

/**
 * {@link Action} that serves allure report from archive directory on master of a given build.
 */
@SuppressWarnings({"ClassDataAbstractionCoupling", "PMD.GodClass"})
public class AllureReportBuildAction implements BuildBadgeAction, RunAction2, SimpleBuildStep.LastBuildAction {

    private static final String ALLURE_REPORT = "allure-report";
    private static final String CACHE_CONTROL = "Cache-Control";
    private static final String CACHE_CONTROL_NO_CACHE = "no-cache, no-store, must-revalidate";
    private static final String CACHE_CONTROL_POST_CHECK = "post-check=0, pre-check=0";
    private static final String HEADER_PRAGMA = "Pragma";
    private static final String HEADER_PRAGMA_NO_CACHE = "no-cache";
    private static final String HEADER_EXPIRES = "Expires";
    private static final String HASH_404 = "#404";
    private static final String WAS_ATTACHED_TO_BOTH = "%s was attached to both %s and %s";
    private static final String HEADER_CONTENT_SECURITY_POLICY = "Content-Security-Policy";
    private static final String HEADER_X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    private static final String HEADER_NOSNIFF = "nosniff";
    private static final String SLASH = "/";
    private static final String INDEX_HTML = "index.html";
    private static final String PATH_TRAVERSAL = "..";
    private static final String ILLEGAL_PATH = "Illegal path";

    private Run<?, ?> run;

    private transient WeakReference<BuildSummary> buildSummary;
    private final boolean allure3;
    private String reportPath;

    AllureReportBuildAction(final BuildSummary buildSummary, final boolean allure3) {
        this.buildSummary = new WeakReference<>(buildSummary);
        this.reportPath = ALLURE_REPORT;
        this.allure3 = allure3;
    }

    private String getReportPath() {
        return this.reportPath == null ? ALLURE_REPORT : this.reportPath;
    }

    public void setReportPath(final FilePath reportPath) {
        this.reportPath = reportPath.getName();
    }
    public void setReportPath(final String reportPath) {
        this.reportPath = reportPath;
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
            this.buildSummary = new WeakReference<>(FilePathUtils.extractSummary(run, this.getReportPath(), allure3));
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

        final AllureReportArchiveSource archiveSource = AllureReportArchiveSourceFactory.forRun(run);
        if (archiveSource.exists()) {
            return new ArchiveReportBrowser(archiveSource, reportDirName, reportDirectoryUnderBuild.getRemote());
        }
        archiveSource.close();

        if (reportDirectoryUnderBuild.exists()) {
            return new DirectoryReportBrowser(reportDirectoryUnderBuild);
        }

        response.sendError(
                HttpServletResponse.SC_NOT_FOUND,
                "Allure report not found. Neither archive/artifact storage nor directory '"
                        + reportDirectoryUnderBuild.getRemote() + "' exists."
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

            response.setHeader(HEADER_CONTENT_SECURITY_POLICY, "");
            response.setHeader(HEADER_X_CONTENT_TYPE_OPTIONS, HEADER_NOSNIFF);

            final String rest = normalizeRestOfPath(request, response);
            if (rest == null) {
                return;
            }

            final FilePath fileToServe = resolveFileToServe(request, response, rest);
            if (fileToServe == null) {
                return;
            }

            serveFile(request, response, fileToServe);
        }

        private String normalizeRestOfPath(final StaplerRequest request,
                                           final StaplerResponse response) throws IOException {
            String rest = request.getRestOfPath();
            if (rest == null) {
                rest = "";
            }
            if (rest.isEmpty() || SLASH.equals(rest)) {
                rest = INDEX_HTML;
            } else if (rest.startsWith(SLASH)) {
                rest = rest.substring(1);
            }
            if (rest.contains(PATH_TRAVERSAL)) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, ILLEGAL_PATH);
                return null;
            }
            return rest;
        }

        private FilePath resolveFileToServe(final StaplerRequest request,
                                            final StaplerResponse response,
                                            final String rest) throws IOException {
            FilePath fileToServe = baseDirectory.child(rest);
            try {
                fileToServe = redirectOrIndexIfDirectory(request, response, fileToServe);
                if (fileToServe == null) {
                    return null;
                }
                if (!fileToServe.exists()) {
                    return redirectOr404(request, response);
                }
                return fileToServe;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while checking report file existence", interrupted);
            }
        }

        private FilePath redirectOrIndexIfDirectory(final StaplerRequest request,
                                                    final StaplerResponse response,
                                                    final FilePath fileToServe)
                throws IOException, InterruptedException {
            if (!fileToServe.exists() || !fileToServe.isDirectory()) {
                return fileToServe;
            }
            if (!request.getRequestURI().endsWith(SLASH)) {
                response.sendRedirect2(request.getRequestURI() + SLASH);
                return null;
            }
            return fileToServe.child(INDEX_HTML);
        }

        private FilePath redirectOr404(final StaplerRequest request,
                                       final StaplerResponse response) throws IOException {
            if (!request.getRequestURI().endsWith(SLASH)) {
                response.sendRedirect2(request.getRequestURI() + SLASH);
                return null;
            }
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Not found");
            return null;
        }

        private void serveFile(final StaplerRequest request,
                               final StaplerResponse response,
                               final FilePath fileToServe) throws IOException, ServletException {
            try (InputStream inputStream = fileToServe.read()) {
                response.serveFile(request, inputStream, -1L, -1L, -1L, fileToServe.getName());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while reading report file", interrupted);
            }
        }
    }

    /**
     * Browser that serves report files from an {@link AllureReportArchiveSource}
     * (local zip or remote artifact storage).
     */
    private static final class ArchiveReportBrowser implements HttpResponse {

        private final AllureReportArchiveSource source;
        private final String reportPath;
        private final String reportDirectoryPath;

        ArchiveReportBrowser(final AllureReportArchiveSource source,
                             final String reportPath,
                             final String reportDirectoryPath) {
            this.source = source;
            this.reportPath = reportPath;
            this.reportDirectoryPath = reportDirectoryPath;
        }

        @Override
        public void generateResponse(final StaplerRequest req,
                                     final StaplerResponse rsp,
                                     final Object node)
                throws IOException, ServletException {
            try (AllureReportArchiveSource s = this.source) {
                final AllureReportArchiveSource active = s.activeSource();
                if (active == null) {
                    rsp.sendError(
                            HttpServletResponse.SC_NOT_FOUND,
                            "Allure report not found. Checked: directory '" + reportDirectoryPath
                                    + "', and archive/artifact storage."
                    );
                    return;
                }

                rsp.setHeader(HEADER_CONTENT_SECURITY_POLICY, "");
                rsp.setHeader(HEADER_X_CONTENT_TYPE_OPTIONS, HEADER_NOSNIFF);
                rsp.setHeader(CACHE_CONTROL, CACHE_CONTROL_NO_CACHE);
                rsp.addHeader(CACHE_CONTROL, CACHE_CONTROL_POST_CHECK);
                rsp.setHeader(HEADER_PRAGMA, HEADER_PRAGMA_NO_CACHE);
                rsp.setDateHeader(HEADER_EXPIRES, 0);

                final String rest = normalizeRestOfPath(req, rsp);
                if (rest == null) {
                    return;
                }

                final String path = rest.isEmpty() ? SLASH + INDEX_HTML : rest;
                if (!path.endsWith(SLASH) && tryServeEntry(req, rsp, active, path)) {
                    return;
                }

                final String candidate = candidateIndexPath(path);
                if (tryServeEntry(req, rsp, active, candidate)) {
                    return;
                }

                rsp.sendRedirect2(baseUri(req) + INDEX_HTML + HASH_404);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while reading archive entry", interrupted);
            }
        }

        private String normalizeRestOfPath(final StaplerRequest req,
                                           final StaplerResponse rsp) throws IOException {
            String rest = req.getRestOfPath();
            if (rest == null) {
                rest = "";
            }
            if (!rest.isEmpty() && !rest.startsWith(SLASH)) {
                rest = SLASH + rest;
            }
            if (rest.contains(PATH_TRAVERSAL)) {
                rsp.sendError(HttpServletResponse.SC_BAD_REQUEST, ILLEGAL_PATH);
                return null;
            }
            return rest;
        }

        private boolean tryServeEntry(final StaplerRequest req,
                                      final StaplerResponse rsp,
                                      final AllureReportArchiveSource active,
                                      final String path)
                throws IOException, InterruptedException, ServletException {
            final String entryPath = reportPath + path;
            try (InputStream is = active.openEntry(entryPath)) {
                rsp.serveFile(req, is, -1L, -1L, -1L, fileName(path));
                return true;
            } catch (java.util.NoSuchElementException ignored) {
                return false;
            }
        }

        private String candidateIndexPath(final String path) {
            return path.endsWith(SLASH) ? path + INDEX_HTML : path + SLASH + INDEX_HTML;
        }

        private String fileName(final String path) {
            final int idx = path.lastIndexOf(SLASH);
            return idx >= 0 ? path.substring(idx + 1) : path;
        }

        private String baseUri(final StaplerRequest req) {
            final String requestUri = req.getRequestURI();
            final String restOfPath = req.getRestOfPath() == null ? "" : req.getRestOfPath();
            if (restOfPath.isEmpty() || !requestUri.endsWith(restOfPath)) {
                return ensureTrailingSlash(requestUri);
            }
            final String base = requestUri.substring(0, requestUri.length() - restOfPath.length());
            return ensureTrailingSlash(base);
        }

        private String ensureTrailingSlash(final String uri) {
            return uri.endsWith(SLASH) ? uri : (uri + SLASH);
        }
    }
}
