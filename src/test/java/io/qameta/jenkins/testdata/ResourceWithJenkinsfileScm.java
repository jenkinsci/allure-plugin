package io.qameta.jenkins.testdata;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.NullSCM;
import hudson.scm.SCMRevisionState;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author charlie (Dmitry Baev).
 */
public class ResourceWithJenkinsfileScm extends NullSCM {

    private final String contentFileResource;

    private final String jenkinsFileResource;

    private final String contentDest;

    private final String jenkinsFileDest;

    public ResourceWithJenkinsfileScm(String contentFileResource, String contentDest, String jenkinsFileResource,
                                      String jenkinsFileDest) {
        this.contentFileResource = contentFileResource;
        this.jenkinsFileResource = jenkinsFileResource;
        this.contentDest = contentDest;
        this.jenkinsFileDest = jenkinsFileDest;
    }


    @Override
    public void checkout(Run<?, ?> build, Launcher launcher, FilePath workspace, TaskListener listener,
                         File changelogFile, SCMRevisionState baseline) throws IOException, InterruptedException {
        copyResourceToWorkspace(workspace, contentFileResource, contentDest);
        copyResourceToWorkspace(workspace, jenkinsFileResource, jenkinsFileDest);
    }

    private void copyResourceToWorkspace(FilePath workspace, String source, String dest) throws IOException, InterruptedException {
        FilePath child = workspace.child(dest);
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(source);
             OutputStream os = child.write()) {
            IOUtils.copy(is, os);
        }
    }
}
