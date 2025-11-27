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
package ru.yandex.qatools.allure.jenkins.callables;

import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;
import ru.yandex.qatools.allure.jenkins.utils.TrueZipArchiver;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 * Creates Allure report zip archive on the build agent.
 */
public class CreateAllureZipCallable extends MasterToSlaveFileCallable<Void> {

    private static final long serialVersionUID = 1L;

    public static final String REPORT_DIR_NOT_FOUND = "Allure report directory not found: ";

    private final String reportDirPath;
    private final String archiveName;

    public CreateAllureZipCallable(final String reportDirPath, final String archiveName) {
        this.reportDirPath = reportDirPath;
        this.archiveName = archiveName;
    }

    @Override
    public Void invoke(final File workspaceDir,
        final VirtualChannel channel) throws IOException, InterruptedException {
        final FilePath workspace = new FilePath(workspaceDir);
        final FilePath reportDir = workspace.child(reportDirPath);

        if (!reportDir.exists()) {
            throw new IOException(REPORT_DIR_NOT_FOUND + reportDir.getRemote());
        }

        final FilePath zipPath = workspace.child(archiveName);
        if (zipPath.exists()) {
            zipPath.delete();
        }

        final FilePath parent = reportDir.getParent();

        try (OutputStream os = zipPath.write()) {
            Objects.requireNonNull(parent)
                .archive(TrueZipArchiver.FACTORY, os, reportDir.getName() + "/**");
        }

        return null;
    }
}
