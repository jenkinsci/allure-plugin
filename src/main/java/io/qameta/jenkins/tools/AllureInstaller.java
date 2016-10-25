package io.qameta.jenkins.tools;

import hudson.Extension;
import hudson.tools.DownloadFromUrlInstaller;
import hudson.tools.ToolInstallation;
import io.qameta.jenkins.Messages;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;

/**
 * @author Artem Eroshenko <eroshenkoam@yandex-team.ru>
 */
public class AllureInstaller extends DownloadFromUrlInstaller {

    @DataBoundConstructor
    public AllureInstaller(String id) {
        super(id);
    }

    @Extension
    @SuppressWarnings("unused")
    public static class DescriptorImpl extends DownloadFromUrlInstaller.DescriptorImpl<AllureInstaller> {

        @Override
        @Nonnull
        public String getDisplayName() {
            return Messages.AllureCommandlineInstaller_DisplayName();
        }

        @Override
        public boolean isApplicable(Class<? extends ToolInstallation> toolType) {
            return toolType == AllureInstallation.class;
        }
    }
}