package ru.yandex.qatools.allure.jenkins.tools;

import hudson.CopyOnWrite;
import hudson.EnvVars;
import hudson.Extension;
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

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

/**
 * @author Artem Eroshenko <eroshenkoam@yandex-team.ru>
 */
public class AllureCommandlineInstallation extends ToolInstallation
        implements EnvironmentSpecific<AllureCommandlineInstallation>, NodeSpecific<AllureCommandlineInstallation> {

    @DataBoundConstructor
    public AllureCommandlineInstallation(String name, String home, List<? extends ToolProperty<?>> properties) {
        super(Util.fixEmptyAndTrim(name), Util.fixEmptyAndTrim(home), properties);
    }

    public String getClassPath(Launcher launcher) throws IOException, InterruptedException {
        return launcher.getChannel().call(new MasterToSlaveCallable<String, IOException>() {
            @Override
            public String call() throws IOException {
                Path home = getHomeFile();
                return String.format("%s/*:%s",
                        home.resolve("lib").toAbsolutePath(),
                        home.resolve("conf").toAbsolutePath()
                );
            }
        });
    }

    private Path getHomeFile() {
        String home = Util.replaceMacro(getHome(), EnvVars.masterEnvVars);
        return home == null ? null : Paths.get(home);
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