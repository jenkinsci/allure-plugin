package ru.yandex.qatools.allure.jenkins.tools;

import hudson.Extension;
import hudson.tools.DownloadFromUrlInstaller;
import hudson.tools.ToolInstallation;
import org.kohsuke.stapler.DataBoundConstructor;
import ru.yandex.qatools.allure.jenkins.Messages;

/**
 * @author Artem Eroshenko <eroshenkoam@yandex-team.ru>
 */
public class AllureCommandlineInstaller extends DownloadFromUrlInstaller {

    @DataBoundConstructor
    public AllureCommandlineInstaller(String id) {
        super(id);
    }

    @Extension
    public static final class DescriptorImpl extends DownloadFromUrlInstaller.DescriptorImpl<AllureCommandlineInstaller> {
        @Override
        public String getDisplayName() {
            return Messages.AllureCommandlineInstaller_DisplayName();
        }

        @Override
        public boolean isApplicable(Class<? extends ToolInstallation> toolType) {
            return toolType == AllureCommandlineInstallation.class;
        }
    }
}