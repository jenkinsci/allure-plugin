package io.qameta.jenkins;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.matrix.MatrixAggregatable;
import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixRun;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.JDK;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Recorder;
import hudson.util.ArgumentListBuilder;
import io.qameta.jenkins.callables.AddExecutorInfo;
import io.qameta.jenkins.callables.AddTestRunInfo;
import io.qameta.jenkins.config.AllureReportConfig;
import io.qameta.jenkins.config.ReportBuildPolicy;
import io.qameta.jenkins.tools.AllureInstallation;
import io.qameta.jenkins.utils.BuildUtils;
import io.qameta.jenkins.utils.FilePathUtils;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
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
public class AllureReportPublisher extends Recorder implements Serializable, MatrixAggregatable {

    public static final String ALLURE_PREFIX = "allure";

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
    public Collection<? extends Action> getProjectActions(AbstractProject<?, ?> project) {
        return Collections.singletonList(new AllureReportProjectAction(project));
    }

    @Override
    @Nonnull
    public AllureReportPublisherDescriptor getDescriptor() {
        return (AllureReportPublisherDescriptor) super.getDescriptor();
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {

        List<FilePath> resultsPaths = getConfig().getResultsPaths().stream()
                .map(path -> build.getWorkspace().child(path))
                .collect(Collectors.toList());

        boolean result = generateReport(resultsPaths, build, launcher, listener);

        /*
        Its chunk of code copies raw data to matrix build allure dir in order to generate aggregated report.

        It is not possible to move this code to MatrixAggregator->endRun, because endRun executed according
        its triggering queue (despite of the run can be completed so long ago), and by the beginning of
        executing the slave can be off already (for ex. with jclouds plugin).

        It is not possible to make a method like MatrixAggregator->simulatedEndRun and call its from here,
        because AllureReportPublisher is singleton for job, and it can't store state objects to communicate
        between perform and createAggregator, because for concurrent builds (Jenkins provides such feature)
        state objects will be corrupted.
         */
        if (build instanceof MatrixRun) {
            MatrixBuild parentBuild = ((MatrixRun) build).getParentBuild();
            Optional<FilePath> optional = getAggregationResultDirectory(parentBuild);
            if (optional.isPresent()) {
                FilePath aggregationDir = optional.get();
                listener.getLogger().format("copy matrix build results to directory [%s]", aggregationDir);
                for (FilePath resultsPath : resultsPaths) {
                    FilePathUtils.copyRecursiveTo(resultsPath, aggregationDir, parentBuild, listener.getLogger());
                }
            }
        }
        return result;
    }

    @Override
    public MatrixAggregator createAggregator(MatrixBuild build, Launcher launcher, BuildListener listener) {
        return new MatrixAggregator(build, launcher, listener) {
            @Override
            public boolean endBuild() throws InterruptedException, IOException {
                Optional<FilePath> optional = getAggregationResultDirectory(build);
                if (!optional.isPresent()) {
                    return true;
                }
                FilePath results = optional.get();
                boolean result = generateReport(Collections.singletonList(results), build, launcher, listener);
                FilePathUtils.deleteRecursive(results, listener.getLogger());
                return result;
            }
        };
    }

    private boolean generateReport(List<FilePath> resultsPaths, AbstractBuild<?, ?> build, Launcher launcher,
                                   BuildListener listener) throws IOException, InterruptedException {
        ReportBuildPolicy reportBuildPolicy = getConfig().getReportBuildPolicy();
        if (!reportBuildPolicy.isNeedToBuildReport(build)) {
            listener.getLogger().format(
                    "Allure report generation rejected by policy [%s]",
                    reportBuildPolicy.getTitle()
            );
            return true;
        }

        EnvVars buildEnvVars = getBuildEnvVars(build, listener);
        // discover commandline
        Optional<AllureInstallation> installation =
                getDescriptor().getCommandlineInstallation(config.getCommandline());

        if (!installation.isPresent()) {
            launcher.getListener().getLogger().println("ERROR: Can not find any allure commandline installation.");
            return false;
        }

        copyHistory(resultsPaths, build, listener);
        addTestRunInfo(resultsPaths, build);
        addExecutorInfo(resultsPaths, build);

        FilePath workspace = build.getWorkspace();
        if (Objects.isNull(workspace)) {
            launcher.getListener().getLogger().println("ERROR: Can not find build workspace");
            return false;
        }

        FilePath tmpDirectory = workspace.createTempDir(FilePathUtils.ALLURE_PREFIX, null);
        FilePath reportDirectory = tmpDirectory.child("allure-report");

        // configure commandline
        Optional<AllureInstallation> tool = BuildUtils.getBuildTool(installation.get(), buildEnvVars, listener);
        if (!tool.isPresent()) {
            launcher.getListener().getLogger().println("ERROR: Can not find allure commandline " +
                    "installation for environment.");
            return false;
        }
        AllureInstallation commandline = tool.get();
        configureJdk(build.getProject(), listener, buildEnvVars);

        // configure arguments
        ArgumentListBuilder arguments = new ArgumentListBuilder();
        arguments.add(commandline.getExecutable(launcher));
        arguments.add("generate");
        for (FilePath resultsPath : resultsPaths) {
            arguments.addQuoted(resultsPath.getRemote());
        }
        arguments.add("-o").addQuoted(reportDirectory.getRemote());

        int exitCode;

        try {
            exitCode = launcher.launch().cmds(arguments).envs(buildEnvVars).stdout(listener)
                    .pwd(workspace).join();
            if (exitCode != 0) {
                return false;
            }
            // copy report on master
            reportDirectory.copyRecursiveTo(new FilePath(new File(build.getRootDir(), "allure-report")));
            // execute actions for report
            build.addAction(new AllureReportBuildBadgeAction(build));
        } catch (IOException e) { //NOSONAR
            listener.getLogger().println("Report generation failed");
            e.printStackTrace(listener.getLogger());  //NOSONAR
            return false;
        } finally {
            FilePathUtils.deleteRecursive(tmpDirectory, listener.getLogger());
        }
        return true;
    }

    private void addTestRunInfo(List<FilePath> resultsPaths, AbstractBuild<?, ?> build)
            throws IOException, InterruptedException {
        long start = build.getStartTimeInMillis();
        long stop = build.getTimeInMillis();
        for (FilePath path : resultsPaths) {
            path.act(new AddTestRunInfo(build.getFullDisplayName(), start, stop));
        }
    }

    private void addExecutorInfo(List<FilePath> resultsPaths, AbstractBuild<?, ?> build)
            throws IOException, InterruptedException {
        String rootUrl = Jenkins.getInstance().getRootUrl();
        String buildUrl = rootUrl + build.getUrl();
        String reportUrl = buildUrl + "allure";
        AddExecutorInfo callable = new AddExecutorInfo(rootUrl, build.getFullDisplayName(), buildUrl, reportUrl);
        for (FilePath path : resultsPaths) {
            path.act(callable);
        }
    }

    private void copyHistory(List<FilePath> resultsPaths, AbstractBuild<?, ?> build, BuildListener listener)
            throws IOException, InterruptedException {
        AbstractBuild<?, ?> previousBuild = build.getPreviousBuild();
        if (Objects.isNull(previousBuild)) {
            return;
        }

        FilePath history = new FilePath(new File(previousBuild.getRootDir(), "allure-report/data/history.json"));
        if (history.exists()) {
            resultsPaths.forEach(resultsPath -> {
                try {
                    history.copyTo(new FilePath(resultsPath, "history.json"));
                } catch (IOException | InterruptedException e) {
                    listener.getLogger().println("Can't copy history");
                }
            });
        } else {
            listener.getLogger().println("Can't find history file " + history);
        }
    }

    @Nullable
    private JDK getJdk(@Nonnull AbstractProject<?, ?> project) {
        return Optional.ofNullable(config.getJdk())
                .map(Jenkins.getInstance()::getJDK)
                .orElse(project.getJDK());
    }

    /**
     * Configure java environment variables such as JAVA_HOME.
     */
    private void configureJdk(AbstractProject<?, ?> project, TaskListener listener, EnvVars env)
            throws IOException, InterruptedException {
        JDK jdk = getJdk(project);
        if (Objects.isNull(jdk) || !jdk.getExists()) {
            return;
        }
        Optional<Node> node = Optional.ofNullable(Computer.currentComputer()).map(Computer::getNode);
        if (node.isPresent()) {
            jdk.forNode(node.get(), listener).buildEnvVars(env);
        }
        jdk.buildEnvVars(env);
    }

    private Optional<FilePath> getAggregationResultDirectory(AbstractBuild<?, ?> build) {
        String curBuildNumber = Integer.toString(build.getNumber());
        return Optional.of(build)
                .map(AbstractBuild::getWorkspace)
                .map(ws -> ws.child(ALLURE_PREFIX + curBuildNumber));
    }
}