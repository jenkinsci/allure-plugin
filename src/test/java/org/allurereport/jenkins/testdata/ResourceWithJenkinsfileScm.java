/*
 *  Copyright 2016-2023 Qameta Software OÜ
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.allurereport.jenkins.testdata;

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

    public ResourceWithJenkinsfileScm(final String contentFileResource,
                                      final String contentDest,
                                      final String jenkinsFileResource,
                                      final String jenkinsFileDest) {
        this.contentFileResource = contentFileResource;
        this.jenkinsFileResource = jenkinsFileResource;
        this.contentDest = contentDest;
        this.jenkinsFileDest = jenkinsFileDest;
    }


    @Override
    public void checkout(final Run<?, ?> build,
                         final Launcher launcher,
                         final FilePath workspace,
                         final TaskListener listener,
                         final File changelogFile,
                         final SCMRevisionState baseline) throws IOException, InterruptedException {
        copyResourceToWorkspace(workspace, contentFileResource, contentDest);
        copyResourceToWorkspace(workspace, jenkinsFileResource, jenkinsFileDest);
    }

    private void copyResourceToWorkspace(final FilePath workspace,
                                         final String source,
                                         final String dest) throws IOException, InterruptedException {
        final FilePath child = workspace.child(dest);
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(source);
             OutputStream os = child.write()) {
            IOUtils.copy(is, os);
        }
    }
}
