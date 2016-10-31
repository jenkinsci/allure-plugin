package ru.yandex.qatools.allure.jenkins;

import hudson.FilePath;
import hudson.model.*;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * {@link Action} that server allure report from archive directory on master of a given createGenerator.
 *
 * @author pupssman
 */
public class AllureBuildAction implements BuildBadgeAction {

    private final Run<?, ?> build;

    public AllureBuildAction(Run<?, ?> build) {
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

    @SuppressWarnings("unused")
    public DirectoryBrowserSupport doDynamic(StaplerRequest req, StaplerResponse rsp) throws Exception { //NOSONAR
        Job<?, ?> project = build.getParent();
        FilePath systemDirectory = new FilePath(AllureReportPlugin.getReportBuildDirectory(build));
        return new DirectoryBrowserSupport(this, systemDirectory, project.getDisplayName(), null, false);
    }

}
