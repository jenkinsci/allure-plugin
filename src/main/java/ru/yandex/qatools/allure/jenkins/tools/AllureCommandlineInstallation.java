package ru.yandex.qatools.allure.jenkins.tools;

import hudson.CopyOnWrite;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Functions;
import hudson.Launcher;
import hudson.Util;
import hudson.model.EnvironmentSpecific;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolProperty;
import jenkins.security.MasterToSlaveCallable;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import ru.yandex.qatools.allure.jenkins.Messages;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

/**
 * @author Artem Eroshenko <eroshenkoam@yandex-team.ru>
 */
public class AllureCommandlineInstallation extends ToolInstallation
        implements EnvironmentSpecific<AllureCommandlineInstallation>, NodeSpecific<AllureCommandlineInstallation> {

    public static final String BIN_PATH = "bin";

    @DataBoundConstructor
    public AllureCommandlineInstallation(String name, String home, List<? extends ToolProperty<?>> properties) {
        super(Util.fixEmptyAndTrim(name), Util.fixEmptyAndTrim(home), properties);
    }

    public File getExecutable(Launcher launcher) throws InterruptedException, IOException { //NOSONAR
        return launcher.getChannel().call(new MasterToSlaveCallable<File, IOException>() {
            @Override
            public File call() throws IOException {
                Path executable = getExecutablePath();
                if (executable == null || Files.notExists(executable)) {
                    throw new IOException(String.format("Can not find allure bin at path '%s'", executable));
                }
                return executable.toFile();
            }
        });
    }


    private Path getHomePath() {
        String home = Util.replaceMacro(getHome(), EnvVars.masterEnvVars);
        return home == null ? null : Paths.get(home);
    }

    private Path getExecutablePath() {
        Path home = getHomePath();
        if (home == null) {
            return null;
        }
        return home.resolve(BIN_PATH).resolve(Functions.isWindows() ? "allure.bat" : "allure");
    }

    @Override
    public AllureCommandlineInstallation forEnvironment(EnvVars environment) {
        return new AllureCommandlineInstallation(getName(), environment.expand(getHome()), getProperties().toList());
    }

    @Override
    public AllureCommandlineInstallation forNode(Node node, TaskListener log) throws IOException, InterruptedException {
        return new AllureCommandlineInstallation(getName(), translateFor(node, log), getProperties().toList());
    }

    @Extension
    public static class DescriptorImpl extends ToolDescriptor<AllureCommandlineInstallation> {
        @CopyOnWrite
        private volatile AllureCommandlineInstallation[] installations = new AllureCommandlineInstallation[0];

        public DescriptorImpl() {
            load();
        }

        @Override
        public String getDisplayName() {
            return Messages.AllureCommandlineInstallation_DisplayName();
        }

        @Override
        public List<? extends ToolInstaller> getDefaultInstallers() {
            return Collections.singletonList(new AllureCommandlineInstaller(null));
        }

        @Override
        public AllureCommandlineInstallation newInstance(StaplerRequest req, JSONObject formData) {
            return (AllureCommandlineInstallation) req.bindJSON(clazz, formData);
        }

        public void setInstallations(AllureCommandlineInstallation... antInstallations) {
            this.installations = antInstallations;
            save();
        }

        @Override
        public AllureCommandlineInstallation[] getInstallations() {
            return installations;
        }
    }

}