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
package org.allurereport.jenkins.tools;

import hudson.FilePath;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstaller;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.allurereport.jenkins.tools.AllureVersionService.getJenkinsProxy;

/**
 * Installer that downloads Allure CLI directly from Maven Central (or a configured mirror).
 *
 * <p>This installer replaces the legacy {@link AllureCommandlineInstaller} which relied on
 * {@code DownloadFromUrlInstaller} and required an installables list. Instead, it constructs
 * the download URL directly from the version string and downloads the zip from Maven Central.
 */
public class AllureCommandlineDirectInstaller extends ToolInstaller {

    private static final Logger LOGGER =
            Logger.getLogger(AllureCommandlineDirectInstaller.class.getName());

    public static final String DEFAULT_BASE_URL = "https://repo1.maven.org/maven2";

    private static final String PATH_SEP = "/";
    private static final String DASH = "-";
    private static final String ZIP_EXT = ".zip";
    private static final String GROUP_PATH = "io/qameta/allure";
    private static final String ARTIFACT_ID = "allure-commandline";
    private static final String ALLURE_BIN = "allure";
    private static final String ALLURE_BAT = "allure.bat";
    private static final String BIN_DIR = "bin";
    private static final String BIN_ALLURE = BIN_DIR + PATH_SEP + ALLURE_BIN;
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 60_000;

    private static final Pattern WINDOWS_DRIVE_ABS =
            Pattern.compile("^[A-Za-z]:[\\\\/].*");

    static final Pattern SEMVER_PATTERN =
            Pattern.compile("^\\d+\\.\\d+\\.\\d+(-[a-zA-Z0-9.]+)?(\\+[a-zA-Z0-9.]+)?$");

    private final String version;
    private String baseUrl;

    @DataBoundConstructor
    public AllureCommandlineDirectInstaller(final String version) {
        super(null);
        this.version = version;
    }

    public String getVersion() {
        return version;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    @DataBoundSetter
    public void setBaseUrl(final String baseUrl) {
        this.baseUrl = baseUrl;
    }

    String effectiveBaseUrl() {
        return StringUtils.isBlank(baseUrl) ? DEFAULT_BASE_URL : baseUrl.replaceAll("/+$", "");
    }

    String buildDownloadUrl(final String ver) {
        return effectiveBaseUrl()
                + PATH_SEP + GROUP_PATH
                + PATH_SEP + ARTIFACT_ID
                + PATH_SEP + ver
                + PATH_SEP + ARTIFACT_ID + DASH + ver + ZIP_EXT;
    }

    @Override
    public FilePath performInstallation(
            final ToolInstallation tool,
            final Node node,
            final TaskListener log) throws IOException, InterruptedException {

        final FilePath installDir = preferredLocation(tool, node);

        if (isCachedInstallation(installDir, log)) {
            return installDir;
        }

        final String ver = StringUtils.isBlank(version) ? AllureVersionService.FALLBACK_VERSION : version;
        final String downloadUrl = buildDownloadUrl(ver);
        log.getLogger().println("[Allure] Installing Allure CLI " + ver + " from " + downloadUrl);

        final FilePath toolsDir = installDir.getParent();
        if (toolsDir != null) {
            toolsDir.mkdirs();
                    }
        final FilePath tempDir = (toolsDir != null ? toolsDir : installDir).createTempDir("allure-download-", null);
        try {
            final FilePath zipFile = tempDir.child(ARTIFACT_ID + DASH + ver + ZIP_EXT);
            downloadZip(downloadUrl, zipFile, log);

            log.getLogger().println("[Allure] Extracting to " + installDir.getRemote());
            extractZip(zipFile, installDir, ver, log);

            zipFile.delete();
        } finally {
            tempDir.deleteRecursive();
        }

        validateInstallation(installDir, ver);

        log.getLogger().println("[Allure] Installation complete");
        return installDir;
    }

    private boolean isCachedInstallation(
            final FilePath targetDir,
            final TaskListener log) throws IOException, InterruptedException {
        if (!targetDir.exists()) {
            return false;
        }
        final FilePath binAllure = targetDir.child(BIN_DIR).child(ALLURE_BIN);
        final FilePath binAllureBat = targetDir.child(BIN_DIR).child(ALLURE_BAT);
        if (binAllure.exists() || binAllureBat.exists()) {
            log.getLogger().println("[Allure] Using cached Allure CLI at " + targetDir.getRemote());
            return true;
        }
        LOGGER.log(Level.WARNING,
                "Allure installation directory exists but is incomplete, re-installing: {0}",
                targetDir.getRemote());
        targetDir.deleteRecursive();
        return false;
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void downloadZip(
            final String downloadUrl,
            final FilePath destination,
            final TaskListener log) throws IOException {
        final URL url = new URL(downloadUrl);
        final Proxy proxy = getJenkinsProxy(url);
        final HttpURLConnection connection = (HttpURLConnection) url.openConnection(proxy);
        try {
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", "Jenkins-Allure-Plugin");

            final int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException(
                        "[Allure] Failed to download from " + downloadUrl
                        + " — HTTP " + responseCode
                        + ". Check the version and baseUrl settings.");
            }

            try (InputStream in = connection.getInputStream()) {
                destination.copyFrom(in);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted", e);
        } finally {
            connection.disconnect();
        }
        log.getLogger().println("[Allure] Download complete: " + destination.getRemote());
    }

    private String toRelativePath(final String entryName,
                                  final String topLevelPrefix,
                                  final String altTopLevelPrefix) {
        if (entryName == null) {
            return "";
        }
        if (entryName.startsWith(topLevelPrefix)) {
            return entryName.substring(topLevelPrefix.length());
        }
        if (entryName.startsWith(altTopLevelPrefix)) {
            return entryName.substring(altTopLevelPrefix.length());
        }
        return entryName;
    }

    private void extractEntry(final ZipEntry entry,
                              final ZipInputStream zis,
                              final FilePath targetDir,
                              final String relativePath) throws IOException, InterruptedException {
        final FilePath destPath = targetDir.child(relativePath);
        if (entry.isDirectory()) {
            destPath.mkdirs();
            return;
        }

        final FilePath parent = destPath.getParent();
        if (parent != null) {
            parent.mkdirs();
        }
        destPath.copyFrom(zis);

        if (BIN_ALLURE.equals(relativePath)) {
            destPath.chmod(493);
        }
    }

    @SuppressWarnings({"PMD.NcssCount", "PMD.CyclomaticComplexity", "PMD.CognitiveComplexity"})
    private void extractZip(
            final FilePath zipFile,
            final FilePath targetDir,
            final String ver,
            final TaskListener log) throws IOException, InterruptedException {

        final String topLevelPrefix = ARTIFACT_ID + DASH + ver + PATH_SEP;
        final String altTopLevelPrefix = ALLURE_BIN + DASH + ver + PATH_SEP;

        targetDir.mkdirs();

        try (InputStream fis = zipFile.read();
             ZipInputStream zis = new ZipInputStream(fis)) {

            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                final String entryName = entry.getName();

                final String relativePath = toRelativePath(entryName, topLevelPrefix, altTopLevelPrefix);

                if (relativePath.isEmpty()) {
                    zis.closeEntry();
                    entry = zis.getNextEntry();
                    continue;
                }

                if (isUnsafeZipEntry(relativePath)) {
                    log.getLogger().println(
                            "[Allure] WARNING: Skipping potentially unsafe zip entry: " + entryName);
                    zis.closeEntry();
                    entry = zis.getNextEntry();
                    continue;
                }

                extractEntry(entry, zis, targetDir, relativePath);
                zis.closeEntry();
                entry = zis.getNextEntry();
            }
        }
    }

    private void validateInstallation(
            final FilePath targetDir,
            final String ver) throws IOException, InterruptedException {
        final FilePath binAllure = targetDir.child(BIN_DIR).child(ALLURE_BIN);
        final FilePath binAllureBat = targetDir.child(BIN_DIR).child(ALLURE_BAT);
        if (!binAllure.exists() && !binAllureBat.exists()) {
            targetDir.deleteRecursive();
            throw new IOException(
                    "[Allure] Installation validation failed for version " + ver
                    + ": bin/allure not found in " + targetDir.getRemote()
                    + ". The downloaded archive may be corrupt or the version may not exist."
                    + " Check the version and baseUrl in Global Tool Configuration.");
        }
    }

    static boolean isUnsafeZipEntry(final String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return true;
        }

        if (relativePath.startsWith(PATH_SEP)) {
            return true;
        }

        if (relativePath.startsWith("\\\\")) {
            return true;
        }

        if (WINDOWS_DRIVE_ABS.matcher(relativePath).matches()) {
            return true;
        }

        final String[] parts = relativePath.split("[/\\\\]");
        for (final String part : parts) {
            if ("..".equals(part)) {
                return true;
            }
        }
        return false;
    }
}
