package ru.yandex.qatools.allure.jenkins;

import hudson.FilePath;
import hudson.model.*;

import java.io.IOException;

import javax.servlet.ServletException;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * @deprecated
 * {@link Action} that server allure report from archive directory on master of a given build.
 *
 * @author pupssman
 */
@Deprecated
public class AllureBuildAction implements BuildBadgeAction {

    private final AbstractBuild<?, ?> build;

    public AllureBuildAction(AbstractBuild<?, ?> build) {
        this.build = build;
    }

    @Override
    public String getDisplayName() {
        return AllureReportPlugin.getTitle();
    }

    @Override
    public String getIconFileName() {
        return AllureReportPlugin.getIconFilename();
    }

    @Override
    public String getUrlName() {
        return AllureReportPlugin.URL_PATH;
    }

    @SuppressWarnings("unused")
    public String getBuildUrl() {
        return build.getUrl();
    }

    @SuppressWarnings({"unused", "TrailingComment"})
    public DirectoryBrowserSupport doDynamic(StaplerRequest req, StaplerResponse rsp) //NOSONAR
            throws IOException, ServletException, InterruptedException {
        final AbstractProject<?, ?> project = build.getProject();
        final FilePath systemDirectory = new FilePath(AllureReportPlugin.getReportBuildDirectory(build));
        return new DirectoryBrowserSupport(this, systemDirectory, project.getDisplayName(), null, false);
    }

}
