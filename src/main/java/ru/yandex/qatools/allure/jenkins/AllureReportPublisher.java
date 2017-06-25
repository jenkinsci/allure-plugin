package ru.yandex.qatools.allure.jenkins;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.matrix.MatrixAggregatable;
import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixRun;
import hudson.model.BuildListener;
import hudson.model.JDK;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Recorder;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import ru.yandex.qatools.allure.jenkins.callables.AddExecutorInfo;
import ru.yandex.qatools.allure.jenkins.callables.AddTestRunInfo;
import ru.yandex.qatools.allure.jenkins.config.AllureReportConfig;
import ru.yandex.qatools.allure.jenkins.config.PropertyConfig;
import ru.yandex.qatools.allure.jenkins.config.ReportBuildPolicy;
import ru.yandex.qatools.allure.jenkins.config.ResultsConfig;
import ru.yandex.qatools.allure.jenkins.exception.AllurePluginException;
import ru.yandex.qatools.allure.jenkins.tools.AllureCommandlineInstallation;
import ru.yandex.qatools.allure.jenkins.utils.BuildUtils;
import ru.yandex.qatools.allure.jenkins.utils.FilePathUtils;
import ru.yandex.qatools.allure.jenkins.utils.TrueZipArchiver;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.lang.String.format;
import static ru.yandex.qatools.allure.jenkins.utils.ZipUtils.listEntries;

/**
 * User: eroshenkoam.
 * Date: 10/8/13, 6:20 PM
 * <p/>
 * {@link AllureReportPublisherDescriptor}
 */
@SuppressWarnings({"unchecked", "ClassDataAbstractionCoupling", "ClassFanOutComplexity"})
public class AllureReportPublisher extends Recorder implements SimpleBuildStep, Serializable, MatrixAggregatable {

    private static final String ALLURE_PREFIX = "allure";
    private static final String ALLURE_SUFFIX = "results";
    private static final String REPORT_ARCHIVE_NAME = "allure-report.zip";

    private AllureReportConfig config;

    private String jdk;

    private String commandline;

    private List<PropertyConfig> properties = new ArrayList<>();

    private List<ResultsConfig> results;

    private ReportBuildPolicy reportBuildPolicy;

    private Boolean includeProperties;

    @DataBoundConstructor
    public AllureReportPublisher(@Nonnull List<ResultsConfig> results) {
        this.results = results;
    }

    public List<ResultsConfig> getResults() {
        if (this.results == null && this.config != null) {
            this.results = this.config.getResults();
        }
        return results;
    }

    @DataBoundSetter
    public void setConfig(final AllureReportConfig config) {
        this.config = config;
    }

    @DataBoundSetter
    public void setJdk(final String jdk) {
        this.jdk = jdk;
    }

    public String getJdk() {
        if (this.jdk == null && this.config != null) {
            this.jdk = this.config.getJdk();
        }
        return this.jdk;
    }

    @DataBoundSetter
    public void setCommandline(String commandline) {
        this.commandline = commandline;
    }

    public String getCommandline() {
        if (this.commandline == null && this.config != null) {
            this.commandline = this.config.getCommandline();
        }
        return this.commandline;
    }

    @DataBoundSetter
    public void setProperties(List<PropertyConfig> properties) {
        this.properties = properties;
    }

    public List<PropertyConfig> getProperties() {
        if (this.config != null) {
            return config.getProperties();
        }
        return this.properties;
    }

    @DataBoundSetter
    public void setReportBuildPolicy(ReportBuildPolicy reportBuildPolicy) {
        this.reportBuildPolicy = reportBuildPolicy;
    }

    public ReportBuildPolicy getReportBuildPolicy() {
        if (this.reportBuildPolicy == null && this.config != null) {
            this.reportBuildPolicy = this.config.getReportBuildPolicy();
        }
        return reportBuildPolicy != null ? reportBuildPolicy : ReportBuildPolicy.ALWAYS;
    }

    @DataBoundSetter
    public void setIncludeProperties(Boolean includeProperties) {
        this.includeProperties = includeProperties;
    }

    public Boolean getIncludeProperties() {
        if (this.includeProperties == null && this.config != null) {
            this.includeProperties = this.config.getIncludeProperties();
        }
        return this.includeProperties != null ? includeProperties : Boolean.TRUE;
    }

    @Nonnull
    public AllureReportConfig getConfig() {
        return config;
    }

    @Override
    @Nonnull
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    @Nonnull
    public AllureReportPublisherDescriptor getDescriptor() {
        return (AllureReportPublisherDescriptor) super.getDescriptor();
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher,
                        @Nonnull TaskListener listener) throws InterruptedException, IOException {
        final List<FilePath> results = new ArrayList<>();

        final EnvVars buildEnvVars = BuildUtils.getBuildEnvVars(run, listener);
        for (final ResultsConfig resultsConfig : getResults()) {
            results.add(workspace.child(buildEnvVars.expand(resultsConfig.getPath())));
        }
        prepareResults(results, run, listener);
        generateReport(results, run, workspace, launcher, listener);
        copyResultsToParentIfNeeded(results, run, listener);
    }

    /**
     * Its chunk of code copies raw data to matrix build allure dir in order to generate aggregated report.
     * <p>
     * It is not possible to move this code to MatrixAggregator->endRun, because endRun executed according
     * its triggering queue (despite of the run can be completed so long ago), and by the beginning of
     * executing the slave can be off already (for ex. with jclouds plugin).
     * <p>
     * It is not possible to make a method like MatrixAggregator->simulatedEndRun and call its from here,
     * because AllureReportPublisher is singleton for job, and it can't store state objects to communicate
     * between perform and createAggregator, because for concurrent builds (Jenkins provides such feature)
     * state objects will be corrupted.
     */
    private void copyResultsToParentIfNeeded(@Nonnull List<FilePath> results, @Nonnull Run<?, ?> run,
                                             @Nonnull TaskListener listener) throws IOException, InterruptedException {
        if (run instanceof MatrixRun) {
            final MatrixBuild parentBuild = ((MatrixRun) run).getParentBuild();
            final FilePath workspace = parentBuild.getWorkspace();
            if (workspace == null) {
                listener.getLogger().format("Can not find workspace for parent build %s", parentBuild.getDisplayName());
                return;
            }
            final FilePath aggregationDir = workspace.createTempDir(ALLURE_PREFIX, ALLURE_SUFFIX);
            listener.getLogger().format("Copy matrix build results to directory [%s]", aggregationDir);
            for (FilePath resultsPath : results) {
                FilePathUtils.copyRecursiveTo(resultsPath, aggregationDir, parentBuild, listener.getLogger());
            }
        }
    }

    @Override
    public MatrixAggregator createAggregator(MatrixBuild build, Launcher launcher, BuildListener listener) {
        final FilePath workspace = build.getWorkspace();
        if (workspace == null) {
            return null;
        }
        return new MatrixAggregator(build, launcher, listener) {
            @Override
            public boolean endBuild() throws InterruptedException, IOException {
                final List<FilePath> resultsPaths = new ArrayList<>();
                for (FilePath directory : workspace.listDirectories()) {
                    if (directory.getName().startsWith(ALLURE_PREFIX) && directory.getName().endsWith(ALLURE_SUFFIX)) {
                        resultsPaths.add(directory);
                    }
                }
                generateReport(resultsPaths, build, workspace, launcher, listener);
                for (FilePath resultsPath : resultsPaths) {
                    FilePathUtils.deleteRecursive(resultsPath, listener.getLogger());
                }
                return true;
            }
        };
    }

    @SuppressWarnings("TrailingComment")
    private void generateReport(@Nonnull List<FilePath> resultsPaths, @Nonnull Run<?, ?> run,
                                @Nonnull FilePath workspace, @Nonnull Launcher launcher,
                                @Nonnull TaskListener listener) throws IOException, InterruptedException { //NOSONAR

        final ReportBuildPolicy reportBuildPolicy = getReportBuildPolicy();
        if (!reportBuildPolicy.isNeedToBuildReport(run)) {
            listener.getLogger().println(String.format("allure report generation reject by policy [%s]",
                    reportBuildPolicy.getTitle()));
            return;
        }

        final EnvVars buildEnvVars = BuildUtils.getBuildEnvVars(run, listener);
        setAllureProperties(buildEnvVars);
        configureJdk(launcher, listener, buildEnvVars);
        final AllureCommandlineInstallation commandline = getCommandline(launcher, listener, buildEnvVars);

        final FilePath reportPath = workspace.child("allure-report");
        try {
            final int exitCode = new ReportBuilder(launcher, listener, workspace, buildEnvVars, commandline)
                    .build(resultsPaths, reportPath);
            if (exitCode != 0) {
                throw new AllurePluginException("Can not generate Allure Report, exit code: " + exitCode);
            }
            listener.getLogger().println("Allure report was successfully generated.");
            saveAllureArtifact(run, workspace, reportPath, listener);
            run.addAction(new AllureReportBuildAction());
        } finally {
            FilePathUtils.deleteRecursive(reportPath, listener.getLogger());
        }
    }

    private void saveAllureArtifact(final Run<?, ?> run, final FilePath workspace, final FilePath reportPath,
                                    final TaskListener listener)
            throws IOException, InterruptedException {
        listener.getLogger().println("Creating artifact for the build.");
        final File artifactsDir = run.getArtifactsDir();
        artifactsDir.mkdirs();
        final File archive = new File(artifactsDir, REPORT_ARCHIVE_NAME);
        final File tempArchive = new File(archive.getAbsolutePath() + ".writing.zip");

        try (OutputStream os = new FileOutputStream(tempArchive)) {
            workspace.archive(TrueZipArchiver.FACTORY, os, reportPath.getName() + "/**");
        }
        tempArchive.renameTo(archive);
        listener.getLogger().println("Artifact was added to the build.");
    }

    private AllureCommandlineInstallation getCommandline(
            @Nonnull Launcher launcher, @Nonnull TaskListener listener, @Nonnull EnvVars env)
            throws IOException, InterruptedException {

        // discover commandline
        final AllureCommandlineInstallation installation =
                getDescriptor().getCommandlineInstallation(getCommandline());

        if (installation == null) {
            throw new AllurePluginException("Can not find any allure commandline installation.");
        }

        // configure commandline
        final AllureCommandlineInstallation tool = BuildUtils.setUpTool(installation, launcher, listener, env);
        if (tool == null) {
            throw new AllurePluginException("Can not find any allure commandline installation for given environment.");
        }
        return tool;
    }

    private void setAllureProperties(final EnvVars envVars) {
        final StringBuilder options = new StringBuilder();
        for (PropertyConfig property : getProperties()) {
            options.append(format("-D%s=%s ", property.getKey(), envVars.expand(property.getValue())));
        }
        envVars.put("ALLURE_OPTS", options.toString());
    }

    private void prepareResults(@Nonnull List<FilePath> resultsPaths, @Nonnull Run<?, ?> run,
                                @Nonnull TaskListener listener) throws IOException, InterruptedException {
        addHistory(resultsPaths, run, listener);
        addTestRunInfo(resultsPaths, run);
        addExecutorInfo(resultsPaths, run);
    }

    private void addTestRunInfo(@Nonnull List<FilePath> resultsPaths, @Nonnull Run<?, ?> run)
            throws IOException, InterruptedException {
        final long start = run.getStartTimeInMillis();
        final long stop = run.getTimeInMillis();
        for (FilePath path : resultsPaths) {
            path.act(new AddTestRunInfo(run.getFullDisplayName(), start, stop));
        }
    }

    private void addExecutorInfo(@Nonnull List<FilePath> resultsPaths, @Nonnull Run<?, ?> run)
            throws IOException, InterruptedException {
        final String rootUrl = Jenkins.getInstance().getRootUrl();
        final String buildUrl = rootUrl + run.getUrl();
        final String reportUrl = buildUrl + ALLURE_PREFIX;
        final String buildId = run.getId();
        final AddExecutorInfo callable = new AddExecutorInfo(rootUrl, run.getFullDisplayName(), buildUrl, reportUrl,
                buildId);
        for (FilePath path : resultsPaths) {
            path.act(callable);
        }
    }

    private void addHistory(List<FilePath> resultsPaths, @Nonnull Run<?, ?> run, @Nonnull TaskListener listener)
            throws IOException, InterruptedException {
        final FilePath previousReport = getPreviousReport(run);
        if (previousReport == null) {
            return;
        }
        try {
            copyHistoryToResultsPaths(previousReport, resultsPaths);
        } catch (Exception e) {
            listener.getLogger().println("Cannot find a history information about previous builds.");
            listener.getLogger().println(e);
        }
    }

    private void copyHistoryToResultsPaths(FilePath previousReport, List<FilePath> resultsPaths)
            throws IOException, InterruptedException {
        try (ZipFile archive = new ZipFile(previousReport.getRemote())) {
            for (FilePath resultsPath : resultsPaths) {
                copyHistoryToResultsPath(archive, resultsPath);
            }
        }
    }

    private void copyHistoryToResultsPath(ZipFile archive, FilePath resultsPath)
            throws IOException, InterruptedException {
        for (final ZipEntry historyEntry : listEntries(archive, "allure-report/history")) {
            final String historyFile = historyEntry.getName().replace("allure-report/", "");
            try (InputStream entryStream = archive.getInputStream(historyEntry)) {
                final FilePath historyCopy = resultsPath.child(historyFile);
                historyCopy.copyFrom(entryStream);
            }
        }
    }

    private FilePath getPreviousReport(Run<?, ?> run) throws IOException, InterruptedException {
        Run<?, ?> current = run;
        while (current != null) {
            final FilePath previousReport = new FilePath(current.getRootDir()).child("archive/allure-report.zip");
            if (previousReport.exists()) {
                return previousReport;
            }
            current = current.getPreviousCompletedBuild();
        }
        return null;
    }

    @Nullable
    private JDK getJdkInstallation() {
        return Jenkins.getInstance().getJDK(getJdk());
    }

    /**
     * Configure java environment variables such as JAVA_HOME.
     */
    private void configureJdk(Launcher launcher, TaskListener listener, EnvVars env)
            throws IOException, InterruptedException {
        final JDK jdk = BuildUtils.setUpTool(getJdkInstallation(), launcher, listener, env);
        if (jdk != null) {
            jdk.buildEnvVars(env);
        }
    }
}
