package io.qameta.jenkins;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.matrix.MatrixAggregatable;
import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixRun;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.JDK;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Recorder;
import io.qameta.jenkins.callables.AddExecutorInfo;
import io.qameta.jenkins.callables.AddTestRunInfo;
import io.qameta.jenkins.config.AllureReportConfig;
import io.qameta.jenkins.config.ReportBuildPolicy;
import io.qameta.jenkins.config.ResultsConfig;
import io.qameta.jenkins.execption.AllurePluginException;
import io.qameta.jenkins.tools.AllureInstallation;
import io.qameta.jenkins.utils.BuildUtils;
import io.qameta.jenkins.utils.FilePathUtils;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.qameta.jenkins.utils.BuildUtils.getBuildEnvVars;

/**
 * User: eroshenkoam
 * Date: 10/8/13, 6:20 PM
 * <p>
 * {@link AllureReportPublisherDescriptor}
 */
@SuppressWarnings("unchecked")
public class AllureReportPublisher extends Recorder implements SimpleBuildStep, Serializable, MatrixAggregatable {

    public static final String ALLURE_PREFIX = "allure";
    public static final String ALLURE_SUFFIX = "report";

    private final AllureReportConfig config;

    @DataBoundConstructor
    public AllureReportPublisher(@Nonnull AllureReportConfig config) {
        this.config = config;
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
        List<FilePath> results = getConfig().getResultsPaths().stream()
                .map(ResultsConfig::getPath)
                .map(workspace::child)
                .collect(Collectors.toList());
        prepareResults(results, run);
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
            MatrixBuild parentBuild = ((MatrixRun) run).getParentBuild();
            FilePath workspace = parentBuild.getWorkspace();
            if (Objects.isNull(workspace)) {
                listener.getLogger().format("Can not find workspace for parent build %s", parentBuild.getDisplayName());
                return;
            }
            FilePath aggregationDir = workspace.createTempDir(ALLURE_PREFIX, ALLURE_SUFFIX);
            listener.getLogger().format("Copy matrix build results to directory [%s]", aggregationDir);
            for (FilePath resultsPath : results) {
                FilePathUtils.copyRecursiveTo(resultsPath, aggregationDir, parentBuild, listener.getLogger());
            }
        }
    }

    @Override
    public MatrixAggregator createAggregator(MatrixBuild build, Launcher launcher, BuildListener listener) {
        FilePath workspace = build.getWorkspace();
        if (Objects.isNull(workspace)) {
            return null;
        }
        return new MatrixAggregator(build, launcher, listener) {
            @Override
            public boolean endBuild() throws InterruptedException, IOException {
                List<FilePath> resultPaths = workspace.listDirectories().stream()
                        .filter(dir -> dir.getName().startsWith(ALLURE_PREFIX))
                        .filter(dir -> dir.getName().endsWith(ALLURE_SUFFIX))
                        .collect(Collectors.toList());
                generateReport(resultPaths, build, workspace, launcher, listener);
                resultPaths.forEach(path -> FilePathUtils.deleteRecursive(path, listener.getLogger()));
                return true;
            }
        };
    }

    private void generateReport(@Nonnull List<FilePath> resultsPaths, @Nonnull Run<?, ?> run,
                                @Nonnull FilePath workspace, @Nonnull Launcher launcher,
                                @Nonnull TaskListener listener) throws IOException, InterruptedException { //NOSONAR
        ReportBuildPolicy reportBuildPolicy = getConfig().getReportBuildPolicy();
        if (!reportBuildPolicy.isNeedToBuildReport(run)) {
            listener.getLogger().format(
                    "Allure report generation rejected by policy [%s]",
                    reportBuildPolicy.getTitle()
            );
            return;
        }

        EnvVars buildEnvVars = getBuildEnvVars(run, listener);
        configureJdk(buildEnvVars, listener);
        AllureInstallation commandline = getCommandline(listener, buildEnvVars);

        FilePath reportPath = workspace.createTempDir(ALLURE_PREFIX, "report");
        try {
            int exitCode = new ReportBuilder(launcher, listener, workspace, buildEnvVars, commandline)
                    .build(resultsPaths, reportPath);
            if (exitCode != 0) {
                throw new AllurePluginException("Can not generate Allure Report, exit code: " + exitCode);
            }
            reportPath.copyRecursiveTo(new FilePath(new File(run.getRootDir(), "allure-report")));
            run.addAction(new AllureReportBuildBadgeAction());
        } finally {
            FilePathUtils.deleteRecursive(reportPath, listener.getLogger());
        }
    }

    private AllureInstallation getCommandline(@Nonnull TaskListener listener, @Nonnull EnvVars buildEnvVars)
            throws IOException, InterruptedException {
        // discover commandline
        Optional<AllureInstallation> installation =
                getDescriptor().getCommandlineInstallation(config.getCommandline());

        if (!installation.isPresent()) {
            throw new AllurePluginException("Can not find any allure commandline installation.");
        }

        // configure commandline
        Optional<AllureInstallation> tool = BuildUtils.getBuildTool(installation.get(), buildEnvVars, listener);
        if (!tool.isPresent()) {
            throw new AllurePluginException("Can not find any allure commandline installation for given environment.");
        }
        return tool.get();
    }

    private void prepareResults(@Nonnull List<FilePath> resultsPaths, @Nonnull Run<?, ?> run)
            throws IOException, InterruptedException {
        copyHistory(resultsPaths, run);
        addTestRunInfo(resultsPaths, run);
        addExecutorInfo(resultsPaths, run);
    }

    private void addTestRunInfo(@Nonnull List<FilePath> resultsPaths, @Nonnull Run<?, ?> run)
            throws IOException, InterruptedException {
        long start = run.getStartTimeInMillis();
        long stop = run.getTimeInMillis();
        for (FilePath path : resultsPaths) {
            path.act(new AddTestRunInfo(run.getFullDisplayName(), start, stop));
        }
    }

    private void addExecutorInfo(@Nonnull List<FilePath> resultsPaths, @Nonnull Run<?, ?> run)
            throws IOException, InterruptedException {
        String rootUrl = Jenkins.getInstance().getRootUrl();
        String buildUrl = rootUrl + run.getUrl();
        String reportUrl = buildUrl + ALLURE_PREFIX;
        AddExecutorInfo callable = new AddExecutorInfo(rootUrl, run.getFullDisplayName(), buildUrl, reportUrl);
        for (FilePath path : resultsPaths) {
            path.act(callable);
        }
    }

    private void copyHistory(@Nonnull List<FilePath> resultsPaths, @Nonnull Run<?, ?> run)
            throws IOException, InterruptedException {
        Run<?, ?> previousRun = run.getPreviousCompletedBuild();
        if (Objects.isNull(previousRun)) {
            return;
        }

        FilePath history = new FilePath(new File(previousRun.getRootDir(), "allure-report/data/history.json"));
        if (history.exists()) {
            for (FilePath resultsPath : resultsPaths) {
                history.copyTo(new FilePath(resultsPath, "history.json"));
            }
        }
    }

    @Nullable
    private JDK getJdk() {
        return Optional.ofNullable(config.getJdk())
                .map(Jenkins.getInstance()::getJDK)
                .orElseGet(() -> Jenkins.getInstance().getJDK(null));
    }

    /**
     * Configure java environment variables such as JAVA_HOME.
     */
    private void configureJdk(EnvVars env, TaskListener listener) throws IOException, InterruptedException {
        JDK jdk = getJdk();
        if (Objects.isNull(jdk) || !jdk.getExists()) {
            return;
        }
        Optional<Node> node = Optional.ofNullable(Computer.currentComputer()).map(Computer::getNode);
        if (node.isPresent()) {
            jdk.forNode(node.get(), listener).buildEnvVars(env);
        }
        jdk.buildEnvVars(env);
    }
}