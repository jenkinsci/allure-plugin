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
package org.allurereport.jenkins.callables;

import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;

import org.allurereport.jenkins.utils.TrueZipArchiver;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 * Creates Allure report archive on the build agent.
 */
public class AllureReportArchive extends MasterToSlaveFileCallable<Void> {

    private static final long serialVersionUID = 1L;

    public static final String REPORT_DIRECTORY_NOT_FOUND = "Allure report directory not found: ";

    private final String reportDirectoryPath;
    private final String archiveFileName;

    public AllureReportArchive(final String reportDirectoryPath, final String archiveFileName) {

        this.reportDirectoryPath = reportDirectoryPath;
        this.archiveFileName = archiveFileName;
    }

    @Override
    public Void invoke(final File workspaceDirectory, final VirtualChannel channel)
        throws IOException, InterruptedException {

        final FilePath workspace = new FilePath(workspaceDirectory);
        final FilePath reportDirectory = workspace.child(reportDirectoryPath);

        if (!reportDirectory.exists()) {
            throw new IOException(REPORT_DIRECTORY_NOT_FOUND + reportDirectory.getRemote());
        }

        final FilePath archiveFilePath = workspace.child(archiveFileName);
        if (archiveFilePath.exists()) {
            archiveFilePath.delete();
        }

        final FilePath reportParentDirectory = reportDirectory.getParent();

        try (OutputStream outputStream = archiveFilePath.write()) {
            Objects.requireNonNull(reportParentDirectory)
                .archive(TrueZipArchiver.FACTORY, outputStream, reportDirectory.getName() + "/**");
        }

        return null;
    }
}
