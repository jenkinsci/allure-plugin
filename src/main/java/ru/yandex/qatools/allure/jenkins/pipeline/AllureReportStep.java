package ru.yandex.qatools.allure.jenkins.pipeline;

import com.google.inject.Inject;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import ru.yandex.qatools.allure.jenkins.ReportGenerator;
import ru.yandex.qatools.allure.jenkins.config.AllureReportConfig;
import ru.yandex.qatools.allure.jenkins.config.PropertyConfig;
import ru.yandex.qatools.allure.jenkins.utils.FilePathUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

@SuppressWarnings({"WeakerAccess", "unused"})
public class AllureReportStep extends AbstractStepImpl implements Serializable {
    private static final long serialVersionUID = 1L;

    private final List<String> paths;
    private String jdk;
    private String commandLine;
    private Map<String, String> properties = Collections.emptyMap();
    private Boolean includeProperties;

    @DataBoundConstructor
    public AllureReportStep(List<String> paths) {
        this.paths = paths;
    }

    @DataBoundSetter
    public void setJdk(String jdk) {
        this.jdk = jdk;
    }

    @DataBoundSetter
    public void setCommandLine(String commandLine) {
        this.commandLine = commandLine;
    }

    @DataBoundSetter
    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    @DataBoundSetter
    public void setIncludeProperties(Boolean includeProperties) {
        this.includeProperties = includeProperties;
    }

    private List<PropertyConfig> createPropertyConfigList() {
        List<PropertyConfig> propertiesList = new ArrayList<>(properties.size());
        for (Entry<String, String> property: properties.entrySet()) {
            PropertyConfig propertyConfig = new PropertyConfig(property.getKey(), property.getValue());
            propertiesList.add(propertyConfig);
        }
        return propertiesList;
    }

    AllureReportConfig toConfig() {
        AllureReportConfig config = AllureReportConfig.newInstance(paths);
        if (jdk != null) config.setJdk(jdk);
        if (commandLine != null) config.setCommandline(commandLine);
        if (includeProperties != null) config.setIncludeProperties(includeProperties);

        List<PropertyConfig> propertiesList = createPropertyConfigList();
        if (!propertiesList.isEmpty()) config.setProperties(propertiesList);

        return config;
    }

    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {
        public DescriptorImpl() {
            super(AllureReportStepExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "allureReport";
        }

        @Override
        public String getDisplayName() {
            return "Generates Allure report";
        }
    }

    public static final class AllureReportStepExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {
        private static final long serialVersionUID = 1;

        @Inject
        private AllureReportStep step;

        @StepContextParameter
        private transient TaskListener listener;

        @StepContextParameter
        private transient Run<?, ?> run;

        @StepContextParameter
        private transient Launcher launcher;

        @StepContextParameter
        private transient FilePath cwd;

        @Override
        protected Void run() throws Exception {
            try {
                List<FilePath> resultPaths = FilePathUtils.stringsToFilePaths(step.paths, cwd);

                ReportGenerator.builder()
                        .config(step.toConfig())
                        .workspace(cwd)
                        .launcher(launcher)
                        .listener(listener)
                        .run(run)
                        .createGenerator(resultPaths)
                        .generateReport();
            } catch (Exception e) {
                e.printStackTrace(listener.error("Failed to generate Allure Report"));
                throw e;
            }

            return null;
        }
    }
}
