package ru.yandex.qatools.allure.jenkins;

import com.google.common.base.Strings;
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
import hudson.remoting.Callable;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Recorder;
import org.apache.maven.settings.Proxy;
import org.kohsuke.stapler.DataBoundConstructor;
import ru.yandex.qatools.allure.jenkins.config.AllureReportConfig;
import ru.yandex.qatools.allure.jenkins.config.ReportBuildPolicy;
import ru.yandex.qatools.allure.jenkins.config.ReportVersionPolicy;
import ru.yandex.qatools.allure.jenkins.utils.PrintStreamWrapper;
import ru.yandex.qatools.allure.jenkins.utils.PropertiesSaver;
import ru.yandex.qatools.allure.jenkins.utils.ProxyBuilder;
import ru.yandex.qatools.allure.jenkins.utils.ReportGenerator;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static ru.yandex.qatools.allure.jenkins.AllureReportPlugin.DEFAULT_URL_PATTERN;
import static ru.yandex.qatools.allure.jenkins.AllureReportPlugin.getReportBuildDirectory;
import static ru.yandex.qatools.allure.jenkins.utils.GlobDirectoryFinder.findDirectoriesByGlob;

/**
 * User: eroshenkoam
 * Date: 10/8/13, 6:20 PM
 * <p/>
 * {@link AllureReportPublisherDescriptor}
 */
@SuppressWarnings("unchecked")
public class AllureReportPublisher extends Recorder implements Serializable, MatrixAggregatable {

    private static final long serialVersionUID = 1L;

    private final AllureReportConfig config;
    private final String REPORT_DIR_PREFIX = "allure";

    /**
     * @deprecated since 1.3.1
     */
    @Deprecated
    private String resultsMask;

    /**
     * @deprecated since 1.1
     */
    @Deprecated
    private String resultsPath;

    @Deprecated
    private boolean alwaysGenerate;

    @DataBoundConstructor
    public AllureReportPublisher(AllureReportConfig config) {
        this.config = config;
    }

    public AllureReportConfig getConfig() {
        if (config != null) {
            return config;
        } else {
            String resultPattern = Strings.isNullOrEmpty(resultsPath) ? resultsMask : resultsPath;
            return AllureReportConfig.newInstance(resultPattern, alwaysGenerate);
        }
    }

    @Deprecated
    public String getResultsPath() {
        return resultsPath;
    }

    @Deprecated
    public String getResultsMask() {
        return resultsMask;
    }

    @Deprecated
    public boolean getAlwaysGenerate() {
        return alwaysGenerate;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public Collection<? extends Action> getProjectActions(AbstractProject<?, ?> project) {
        return Arrays.asList(new AllureProjectAction(project));
    }

    public AllureReportPublisherDescriptor getDescriptor() {
        return (AllureReportPublisherDescriptor) super.getDescriptor();
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {

        PrintStreamWrapper logger = new PrintStreamWrapper(listener.getLogger());
        FilePath allureFilePath = build.getWorkspace().createTempDir(REPORT_DIR_PREFIX, null);

        logger.println("started");
        ReportBuildPolicy reportBuildPolicy = getConfig().getReportBuildPolicy();
        if (!reportBuildPolicy.isNeedToBuildReport(build)) {
            logger.println("report generation reject by policy [%s]", reportBuildPolicy.getTitle());
            return true;
        }

        String resultsPattern = getConfig().getResultsPattern();
        logger.println("find directories by mask [%s]", resultsPattern);
        List<FilePath> resultsFilePaths = build.getWorkspace().act(findDirectoriesByGlob(resultsPattern));
        logger.println("found allure result directories %s", Arrays.toString(resultsFilePaths.toArray()));


        if (resultsFilePaths.size() == 0) {
            logger.println("can't find allure results directories");
            return false;
        }

        FilePath resultsFilePath = allureFilePath.child(ReportGenerator.RESULTS_PATH);
        logger.println("copy founded directories in directory [%s]", resultsFilePath);
        for (FilePath filePath : resultsFilePaths) {
            filePath.copyRecursiveTo(resultsFilePath);
        }

        boolean includeProperties = getConfig().getIncludeProperties();
        if (includeProperties) {
            resultsFilePath.createTempFile("allure", "-environment.properties").
                    act(new PropertiesSaver(build.getBuildVariables(), "Build Properties"));
        }

        generateReport(build, allureFilePath, launcher, logger);
        publishReport(build, logger);

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

            MatrixBuild mb = ((MatrixRun) build).getParentBuild();
            FilePath tmpResultsDirectory = getAggregationDir(mb).child(ReportGenerator.RESULTS_PATH);

            logger.println("copy matrix build results to directory [%s]", tmpResultsDirectory);

            for (FilePath resultsDirectory : resultsFilePaths) {
                copyRecursiveTo(resultsDirectory, tmpResultsDirectory, mb, logger);
            }
        }

        deleteRecursive(allureFilePath, logger);
        logger.println("completed");

        return true;
    }

    public MatrixAggregator createAggregator(MatrixBuild build, Launcher launcher, BuildListener listener) {
        return new MatrixAggregator(build, launcher, listener) {

            @Override
            public boolean endBuild() throws InterruptedException, IOException {

                FilePath aggregatedAllureFilePath = getAggregationDir(build);
                FilePath tmpResultsDirectory = aggregatedAllureFilePath.child(ReportGenerator.RESULTS_PATH);

                PrintStreamWrapper logger = new PrintStreamWrapper(listener.getLogger());

                logger.println("started");
                ReportBuildPolicy reportBuildPolicy = getConfig().getReportBuildPolicy();
                if (!reportBuildPolicy.isNeedToBuildReport(build)) {
                    logger.println("project build reject by policy [%s]", reportBuildPolicy.getTitle());
                    return true;
                }

                if (tmpResultsDirectory.getUsableDiskSpace() == 0) {
                    logger.println("results directory [%s] is empty", tmpResultsDirectory);
                    return true;
                }

                generateReport(build, aggregatedAllureFilePath, launcher, logger);
                publishReport(build, logger);
                deleteRecursive(aggregatedAllureFilePath, logger);

                logger.println("completed");
                return true;
            }
        };
    }

    private FilePath getAggregationDir(AbstractBuild<?, ?> build) {
        String curBuildNumber = Integer.toString(build.getNumber());
        // child(...) works as get or create directory method
        return build.getWorkspace().child(REPORT_DIR_PREFIX + curBuildNumber);
    }

    private void copyRecursiveTo(FilePath from, FilePath to, AbstractBuild build, PrintStreamWrapper logger)
            throws IOException, InterruptedException {
        if (from.isRemote() && to.isRemote()) {
            FilePath tmpMasterFilePath = new FilePath(build.getRootDir()).createTempDir(REPORT_DIR_PREFIX, null);
            from.copyRecursiveTo(tmpMasterFilePath);
            tmpMasterFilePath.copyRecursiveTo(to);
            deleteRecursive(tmpMasterFilePath, logger);
        } else {
            from.copyRecursiveTo(to);
        }
    }

    private void generateReport(AbstractBuild<?, ?> build, FilePath allureFilePath, Launcher launcher, PrintStreamWrapper logger)
            throws IOException, InterruptedException {

        // configuring external links
        final String issuesTrackerPatternDefault = getDescriptor().getIssuesTrackerPatternDefault();
        final String tmsPatternDefault = getDescriptor().getTmsPatternDefault();

        // This code will be run on machine where project is being built (slave or master)
        Callable<String, IOException> task = new Callable<String, IOException>() {
            public String call() throws IOException {

                // Jenkins (non default) settings override Allure settings
                if (!DEFAULT_URL_PATTERN.equals(issuesTrackerPatternDefault)) {
                    System.setProperty("allure.issues.tracker.pattern", issuesTrackerPatternDefault);
                }

                // Jenkins (non default) settings override Allure settings
                if (!DEFAULT_URL_PATTERN.equals(tmsPatternDefault)) {
                    System.setProperty("allure.tests.management.pattern", tmsPatternDefault);
                }
                return "";
            }
        };
        launcher.getChannel().call(task);

        logger.println("generate report from directory [%s]", allureFilePath);
        FilePath reportFilePath = new FilePath(getReportBuildDirectory(build));
        String reportVersion = getConfig().getReportVersionPolicy().equals(ReportVersionPolicy.CUSTOM) ?
                getConfig().getReportVersionCustom() : getDescriptor().getReportVersionDefault();
        Proxy proxySettings = ProxyBuilder.loadHttpProxySettings();
        logger.println("proxy settings [active:'%s', host:'%s', port:'%s', username:'%s', password: '%s']",
                proxySettings.isActive(),
                proxySettings.getHost(),
                proxySettings.getPort(),
                proxySettings.getUsername(),
                proxySettings.getPassword() == null ? "" : "***"
        );
        allureFilePath.act(new ReportGenerator(reportVersion, proxySettings)).copyRecursiveTo(reportFilePath);
    }

    private void publishReport(AbstractBuild<?, ?> build, PrintStreamWrapper logger) {
        logger.println("attach report link to build and project");
        build.getActions().add(new AllureBuildAction(build));
    }

    private void deleteRecursive(FilePath filePath, PrintStreamWrapper logger) {
        try {
            filePath.deleteContents();
            filePath.deleteRecursive();
        } catch (IOException | InterruptedException e) {
            logger.println("Can't delete directory [%s]", filePath);
            e.printStackTrace(logger.getPrintStream());
        }
    }

}
