package ru.yandex.qatools.allure.jenkins;

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
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Recorder;
import org.kohsuke.stapler.DataBoundConstructor;
import ru.yandex.qatools.allure.jenkins.config.AllureReportConfig;
import ru.yandex.qatools.allure.jenkins.utils.FilePathUtils;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static ru.yandex.qatools.allure.jenkins.utils.FilePathUtils.copyRecursiveTo;
import static ru.yandex.qatools.allure.jenkins.utils.FilePathUtils.deleteRecursive;

/**
 * User: eroshenkoam
 * Date: 10/8/13, 6:20 PM
 * <p/>
 * {@link AllureReportPublisherDescriptor}
 */
@SuppressWarnings("unchecked")
public class AllureReportPublisher extends Recorder implements Serializable, MatrixAggregatable {

    private static final long serialVersionUID = 1L;

    private static final String ALLURE_PREFIX = "allure";

    private final AllureReportConfig config;

    @DataBoundConstructor
    public AllureReportPublisher(AllureReportConfig config) {
        this.config = config;
    }

    public AllureReportConfig getConfig() {
        return config == null ? AllureReportConfig.newInstance() : config;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public Collection<? extends Action> getProjectActions(AbstractProject<?, ?> project) {
        return Collections.singletonList(new AllureProjectAction(project));
    }

    @Override
    public AllureReportPublisherDescriptor getDescriptor() {
        return (AllureReportPublisherDescriptor) super.getDescriptor();
    }

    private void handleException(BuildListener listener, Exception e) {
        e.printStackTrace(listener.error("Failed to generate Allure Report")); //NOSONAR
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {
        try {
            List<FilePath> resultsPaths = FilePathUtils.stringsToFilePaths(config.getResultsPaths(), build.getWorkspace());

            createGeneratorBuilder()
                    .workspace(build.getWorkspace())
                    .launcher(launcher)
                    .listener(listener)
                    .run(build)
                    .createGenerator(resultsPaths)
                    .generateReport();

        /*
        Its chunk of code copies raw data to matrix createGenerator allure dir in order to generate aggregated report.

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
                FilePath aggregationResults = getAggregationResultDirectory(parentBuild);
                listener.getLogger().printf("copy matrix createGenerator results to directory [%s]%n", aggregationResults);
                for (FilePath resultsPath : resultsPaths) {
                    copyRecursiveTo(resultsPath, aggregationResults, parentBuild, listener.getLogger());
                }
            }

            return true;
        } catch (Exception e) {
            handleException(listener, e);
            return false;
        }
    }

    @Override
    public MatrixAggregator createAggregator(MatrixBuild build, Launcher launcher, BuildListener listener) {
        return new MatrixAggregatorImpl(build, launcher, listener);
    }

    private ReportGenerator.Builder createGeneratorBuilder() {
        return ReportGenerator.builder().config(getConfig());
    }

    private FilePath getAggregationResultDirectory(AbstractBuild<?, ?> build) {
        String curBuildNumber = Integer.toString(build.getNumber());
        return build.getWorkspace().child(ALLURE_PREFIX + curBuildNumber);
    }

    private class MatrixAggregatorImpl extends MatrixAggregator {
        private MatrixAggregatorImpl(MatrixBuild build, Launcher launcher, BuildListener listener) {
            super(build, launcher, listener);
        }

        @Override
        public boolean endBuild() throws InterruptedException, IOException {
            try {
                FilePath results = getAggregationResultDirectory(build);

                generateReport(results);

                deleteRecursive(results, listener.getLogger());

                return true;
            } catch (Exception e) {
                handleException(listener, e);
                return false;
            }
        }

        private void generateReport(FilePath results) throws AllureReportGenerationException {
            createGeneratorBuilder()
                    .workspace(build.getWorkspace())
                    .launcher(launcher)
                    .listener(listener)
                    .run(build)
                    .createGenerator(Collections.singletonList(results))
                    .generateReport();
        }
    }
}
