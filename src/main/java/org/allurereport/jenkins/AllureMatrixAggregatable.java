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
package org.allurereport.jenkins;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.matrix.MatrixAggregatable;
import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixBuild;
import hudson.model.BuildListener;
import hudson.tasks.Publisher;
import org.allurereport.jenkins.utils.BuildUtils;
import org.allurereport.jenkins.utils.FilePathUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Extension(optional = true)
public class AllureMatrixAggregatable implements MatrixAggregatable {

    private static final String ALLURE_PREFIX = "allure";
    private static final String ALLURE_SUFFIX = "results";

    @Override
    public MatrixAggregator createAggregator(final MatrixBuild build,
        final Launcher launcher,
        final BuildListener listener) {
        final AllureReportPublisher publisher = findPublisher(build);
        if (publisher == null || publisher.isDisabled()) {
            return null;
        }

        final FilePath workspace = build.getWorkspace();
        if (workspace == null) {
            return null;
        }

        return new MatrixAggregator(build, launcher, listener) {
            @Override
            public boolean endBuild() throws InterruptedException, IOException {
                final List<FilePath> resultsPaths = new ArrayList<>();
                for (FilePath directory : workspace.listDirectories()) {
                    if (directory.getName().startsWith(ALLURE_PREFIX) && directory.getName().contains(ALLURE_SUFFIX)) {
                        resultsPaths.add(directory);
                    }
                }

                final EnvVars buildEnvVars = BuildUtils.getBuildEnvVars(build, listener);
                publisher.generateReport(resultsPaths, build, workspace, buildEnvVars, launcher, listener);
                for (FilePath resultsPath : resultsPaths) {
                    FilePathUtils.deleteRecursive(resultsPath, listener.getLogger());
                }
                return true;
            }
        };
    }

    private AllureReportPublisher findPublisher(final MatrixBuild build) {
        for (Publisher publisher : build.getProject().getPublishersList()) {
            if (publisher instanceof AllureReportPublisher) {
                return (AllureReportPublisher) publisher;
            }
        }
        return null;
    }
}
