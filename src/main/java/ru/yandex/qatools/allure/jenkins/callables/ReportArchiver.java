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
import jenkins.security.Roles;
import org.jenkinsci.remoting.RoleChecker;
import ru.yandex.qatools.allure.jenkins.utils.TrueZipArchiver;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;

/**
 * Archiver callable that creates a zip archive on the agent node.
 */
public class ReportArchiver implements FilePath.FileCallable<Void> {

    private static final long serialVersionUID = 1L;

    private final String reportPathName;
    private final String archiveName;

    public ReportArchiver(final String reportPathName, final String archiveName) {
        this.reportPathName = reportPathName;
        this.archiveName = archiveName;
    }

    @Override
    public Void invoke(final File baseDir,
        final VirtualChannel channel) throws IOException, InterruptedException {
        final File zipFile = new File(baseDir, archiveName);

        if (zipFile.exists() && !zipFile.delete()) {
            throw new IOException("Failed to delete existing archive: " + zipFile);
        }

        final File reportDir = new File(baseDir, reportPathName);

        try (OutputStream os = Files.newOutputStream(zipFile.toPath())) {
            new FilePath(reportDir.getParentFile())
                .archive(TrueZipArchiver.FACTORY, os, reportDir.getName() + "/**");
        }
        return null;
    }

    @Override
    public void checkRoles(final RoleChecker checker) {
        checker.check(this, Roles.SLAVE);
    }
}
