package ru.yandex.qatools.allure.jenkins;

import hudson.FilePath;
import hudson.model.*;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;

/**
 * {@link Action} that server allure report from archive directory on master of a given build.
 *
 * @author pupssman
 */
public class AllureBuildAction implements BuildBadgeAction {

    private final AbstractBuild<?, ?> build;
    private final String reportUrl;

    public AllureBuildAction(AbstractBuild<?, ?> build, String reportUrl) {
        this.build = build;
        this.reportUrl = reportUrl;
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
        if (getReportUrl().contains(Jenkins.getInstance().getRootUrl())) {
            return AllureReportPlugin.URL_PATH;
        }
        else {
            return getReportUrl();
        }
    }

    @SuppressWarnings("unused")
    public String getBuildUrl() {
        return build.getUrl();
    }

    public String getReportUrl() {
        return reportUrl;
    }

    @SuppressWarnings("unused")
    public DirectoryBrowserSupport doDynamic(StaplerRequest req, StaplerResponse rsp)
            throws IOException, ServletException, InterruptedException {
        AbstractProject<?, ?> project = build.getProject();
        FilePath systemDirectory = new FilePath(AllureReportPlugin.getReportBuildDirectory(build));
        return new DirectoryBrowserSupport(this, systemDirectory, project.getDisplayName(), null, false);
    }

}
