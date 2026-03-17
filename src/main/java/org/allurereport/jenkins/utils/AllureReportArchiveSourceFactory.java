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
package org.allurereport.jenkins.utils;

import hudson.FilePath;
import hudson.model.Run;

/**
 * Factory that creates the appropriate {@link AllureReportArchiveSource} for a given build.
 *
 * <p>Resolution order for {@link #forRun(Run)}:
 * <ol>
 *   <li>Local file: {@code <artifactsDir>/allure-report.zip} — wrapped in
 *       {@link LocalFileArchiveSource}.</li>
 *   <li>Artifact manager: delegates to {@link ArtifactManagerArchiveSource} which reads
 *       via {@link jenkins.model.ArtifactManager} / {@link jenkins.util.VirtualFile}.</li>
 * </ol>
 *
 * <p>Callers should always use a try-with-resources block:
 * <pre>{@code
 * try (AllureReportArchiveSource src = AllureReportArchiveSourceFactory.forRun(run)) {
 *     if (src.exists()) { ... }
 * }
 * }</pre>
 */
public final class AllureReportArchiveSourceFactory {

    public static final String ALLURE_REPORT_ZIP = "allure-report.zip";

    private AllureReportArchiveSourceFactory() {
    }

    @SuppressWarnings("PMD.CloseResource")
    public static AllureReportArchiveSource forRun(final Run<?, ?> run) {
        final FilePath localPath = new FilePath(run.getArtifactsDir()).child(ALLURE_REPORT_ZIP);
        final AllureReportArchiveSource local = new LocalFileArchiveSource(localPath);
        final AllureReportArchiveSource remote = new ArtifactManagerArchiveSource(run);
        return new FallbackArchiveSource(local, remote);
    }

    public static AllureReportArchiveSource forLocalFile(final FilePath archivePath) {
        return new LocalFileArchiveSource(archivePath);
    }
}
