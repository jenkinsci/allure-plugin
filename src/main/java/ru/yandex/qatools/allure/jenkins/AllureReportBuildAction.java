package ru.yandex.qatools.allure.jenkins;

import hudson.FilePath;
import hudson.model.Action;
import hudson.model.BuildBadgeAction;
import hudson.model.DirectoryBrowserSupport;
import hudson.model.Run;
import hudson.util.ChartUtil;
import hudson.util.DataSetBuilder;
import hudson.util.Graph;
import jenkins.model.lazy.LazyBuildMixIn;
import org.jfree.chart.JFreeChart;
import org.jfree.data.category.CategoryDataset;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import ru.yandex.qatools.allure.jenkins.utils.BuildSummary;
import ru.yandex.qatools.allure.jenkins.utils.ChartUtils;
import ru.yandex.qatools.allure.jenkins.utils.FilePathUtils;

import javax.servlet.ServletException;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * {@link Action} that serves allure report from archive directory on master of a given build.
 *
 * @author pupssman
 */
public class AllureReportBuildAction implements BuildBadgeAction{

    private Run<?, ?> run;
    private WeakReference<BuildSummary> buildSummary;

    AllureReportBuildAction(final BuildSummary buildSummary) {
        this.buildSummary = new WeakReference<>(buildSummary);
    }

    public void doGraph(final StaplerRequest req, final StaplerResponse rsp) throws IOException {
        final CategoryDataset data = buildDataSet();

        new Graph(-1, 600, 300) {
            protected JFreeChart createGraph() {
                return ChartUtils.createChart(req, data);
            }
        }.doPng(req, rsp);
    }

    public void doGraphMap(final StaplerRequest req, final StaplerResponse rsp) throws IOException {
        final CategoryDataset data = buildDataSet();

        new Graph(-1, 600, 300) {
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
            this.buildSummary = new WeakReference<>(FilePathUtils.extractSummary(run));
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
    private AllureReportBuildAction getPreviousResult(final boolean eager) {
        Run<?, ?> b = run;
        Set<Integer> loadedBuilds;
        if (!eager && run.getParent() instanceof LazyBuildMixIn.LazyLoadingJob) {
            loadedBuilds = ((LazyBuildMixIn.LazyLoadingJob<?, ?>) run.getParent()).getLazyBuildMixIn()._getRuns().getLoadedBuilds().keySet();
        } else {
            loadedBuilds = null;
        }
        while (true) {
            b = loadedBuilds == null || loadedBuilds.contains(b.number - /* assuming there are no gaps */1) ? b.getPreviousBuild() : null;
            if (b == null)
                return null;
            AllureReportBuildAction r = b.getAction(AllureReportBuildAction.class);
            if (r != null) {
                if (r == this) {
                    throw new IllegalStateException(this + " was attached to both " + b + " and " + run);
                }
                if (r.run.number != b.number) {
                    throw new IllegalStateException(r + " was attached to both " + b + " and " + r.run);
                }
                return r;
            }
        }
    }

    private CategoryDataset buildDataSet() {
        DataSetBuilder<String, ChartUtil.NumberOnlyBuildLabel> dsb = new DataSetBuilder<>();

        for (AllureReportBuildAction a = this; a != null; a = a.getPreviousResult()) {
            final ChartUtil.NumberOnlyBuildLabel columnKey = new ChartUtil.NumberOnlyBuildLabel(a.run);
            dsb.add(a.getFailedCount(), "failed", columnKey);
            dsb.add(a.getBrokenCount(), "broken", columnKey);
            dsb.add(a.getPassedCount(), "passed", columnKey);
            dsb.add(a.getSkipCount(), "skipped", columnKey);
            dsb.add(a.getUnknownCount(), "unknown", columnKey);
        }
        return dsb.build();
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
