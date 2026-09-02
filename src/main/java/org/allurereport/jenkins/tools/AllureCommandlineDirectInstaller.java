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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolInstallerDescriptor;
import jenkins.MasterToSlaveFileCallable;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
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
@SuppressWarnings("PMD.GodClass")
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
    private static final String VERSION_MARKER = ".allure-version";
    private static final String LINE_SEPARATOR = "\n";
    private static final String USER_AGENT_HEADER = "User-Agent";
    private static final String USER_AGENT = "Jenkins-Allure-Plugin";
    private static final String HTTP_STATUS_SEPARATOR = " — HTTP ";
    private static final String HTTP_SCHEME = "http";
    private static final String HTTPS_SCHEME = "https";
    private static final String DOT = ".";
    private static final String PARENT_DIRECTORY = "..";
    private static final String SPACE = " ";
    private static final String COLON = ":";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 60_000;
    private static final long DOWNLOAD_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(10);
    private static final long MAX_ARCHIVE_SIZE = 256L * 1024 * 1024;
    private static final long MAX_EXTRACTED_SIZE = 1024L * 1024 * 1024;
    private static final int MAX_ARCHIVE_ENTRIES = 50_000;
    private static final long MAX_CHECKSUM_SIZE = 1024;
    private static final int MAX_VERSION_LENGTH = 128;

    private static final Pattern WINDOWS_DRIVE_ABS =
            Pattern.compile("^[A-Za-z]:[\\\\/].*");
    private static final Pattern SHA256_PATTERN = Pattern.compile("^[0-9a-fA-F]{64}$");
    private static final Pattern WINDOWS_RESERVED_NAME = Pattern.compile(
            "(?i)^(?:con|prn|aux|nul|clock\\$|conin\\$|conout\\$|com[1-9¹²³]|lpt[1-9¹²³])"
                    + "(?:\\..*)?$"
    );
    private static final Pattern WINDOWS_INVALID_CHARACTER = Pattern.compile("[<>\"|?*]");

    static final Pattern SEMVER_PATTERN =
            Pattern.compile(
                    "^\\d+\\.\\d+\\.\\d+"
                            + "(?:-[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?"
                            + "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$"
            );

    private final String version;
    private String baseUrl;
    private boolean verifyChecksum;
    private boolean requireHttps;

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

    void setVerifyChecksum(final boolean verifyChecksum) {
        this.verifyChecksum = verifyChecksum;
    }

    void setRequireHttps(final boolean requireHttps) {
        this.requireHttps = requireHttps;
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

        final String ver = validatedVersion();
        final FilePath installDir = preferredLocation(tool, node);

        if (isCachedInstallation(installDir, ver, log)) {
            return installDir;
        }

        final String downloadUrl = buildDownloadUrl(ver);
        validateDownloadUrl(downloadUrl);
        log.getLogger().println(
                "[Allure] Installing Allure CLI " + ver + " from " + redactUrl(downloadUrl)
        );

        final FilePath toolsDir = installDir.getParent();
        if (toolsDir != null) {
            toolsDir.mkdirs();
        }
        final FilePath tempDir = (toolsDir != null ? toolsDir : installDir).createTempDir("allure-download-", null);
        try {
            final FilePath zipFile = tempDir.child(ARTIFACT_ID + DASH + ver + ZIP_EXT);
            downloadZip(downloadUrl, zipFile, log);
            if (verifyChecksum) {
                verifyDownloadChecksum(downloadUrl, zipFile);
            }

            final FilePath extracted = tempDir.child("extracted");
            log.getLogger().println("[Allure] Validating and extracting archive");
            extractZip(zipFile, extracted, ver);
            validateInstallation(extracted, ver);
            extracted.child(VERSION_MARKER)
                    .write(ver + LINE_SEPARATOR, StandardCharsets.UTF_8.name());

            if (installDir.exists()) {
                installDir.deleteRecursive();
            }
            extracted.renameTo(installDir);
        } finally {
            tempDir.deleteRecursive();
        }

        log.getLogger().println("[Allure] Installation complete");
        return installDir;
    }

    String validatedVersion() throws IOException {
        final String resolved = StringUtils.defaultIfBlank(version, AllureVersionService.FALLBACK_VERSION).trim();
        if (resolved.length() > MAX_VERSION_LENGTH || !SEMVER_PATTERN.matcher(resolved).matches()) {
            throw new IOException("Allure version must be an exact semantic version");
        }
        return resolved;
    }

    boolean isCachedInstallation(
            final FilePath targetDir,
            final String expectedVersion,
            final TaskListener log) throws IOException, InterruptedException {
        if (!targetDir.exists()) {
            return false;
        }
        final FilePath binAllure = targetDir.child(BIN_DIR).child(ALLURE_BIN);
        final FilePath binAllureBat = targetDir.child(BIN_DIR).child(ALLURE_BAT);
        final FilePath marker = targetDir.child(VERSION_MARKER);
        final boolean executableExists = binAllure.exists() || binAllureBat.exists();
        final boolean currentMarker = marker.exists()
                && expectedVersion.equals(marker.readToString().trim());
        final boolean matchingLegacyLayout = !marker.exists()
                && targetDir.child("lib")
                .child(ARTIFACT_ID + DASH + expectedVersion + ".jar")
                .exists();
        if (executableExists && (currentMarker || matchingLegacyLayout)) {
            if (!marker.exists()) {
                marker.write(
                        expectedVersion + LINE_SEPARATOR,
                        java.nio.charset.StandardCharsets.UTF_8.name()
                );
            }
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
        final String safeUrl = redactUrl(downloadUrl);
        final Proxy proxy = getJenkinsProxy(url);
        final HttpURLConnection connection = (HttpURLConnection) url.openConnection(proxy);
        try {
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty(USER_AGENT_HEADER, USER_AGENT);

            final int responseCode = connection.getResponseCode();
            validateRedirectTarget(connection.getURL());
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException(
                        "[Allure] Failed to download from " + safeUrl
                        + HTTP_STATUS_SEPARATOR + responseCode
                        + ". Check the version and baseUrl settings.");
            }
            final long declaredSize = connection.getContentLengthLong();
            if (declaredSize > MAX_ARCHIVE_SIZE) {
                throw new IOException(
                        "[Allure] Archive from " + safeUrl + " is too large: " + declaredSize + " bytes"
                );
            }

            try (InputStream source = connection.getInputStream();
                 SizeLimitedInputStream in = new SizeLimitedInputStream(
                         source,
                         MAX_ARCHIVE_SIZE,
                         DOWNLOAD_TIMEOUT_MS,
                         "Allure 2 archive"
                 )) {
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

    private void verifyDownloadChecksum(final String downloadUrl,
                                        final FilePath archive) throws IOException, InterruptedException {
        final String expected = downloadSha256(downloadUrl + ".sha256");
        final String actual = archive.act(new ComputeSha256());
        if (!expected.equalsIgnoreCase(actual)) {
            throw new IOException(
                    "[Allure] SHA-256 mismatch for " + redactUrl(downloadUrl)
                            + ": expected " + expected + " but got " + actual
            );
        }
    }

    private String downloadSha256(final String checksumUrl) throws IOException {
        final URL url = new URL(checksumUrl);
        final HttpURLConnection connection = (HttpURLConnection) url.openConnection(getJenkinsProxy(url));
        try {
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty(USER_AGENT_HEADER, USER_AGENT);
            final int responseCode = connection.getResponseCode();
            validateRedirectTarget(connection.getURL());
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException(
                        "[Allure] Failed to download SHA-256 checksum from " + redactUrl(checksumUrl)
                                + HTTP_STATUS_SEPARATOR + responseCode
                );
            }
            try (InputStream source = connection.getInputStream();
                 SizeLimitedInputStream input = new SizeLimitedInputStream(
                         source,
                         MAX_CHECKSUM_SIZE,
                         DOWNLOAD_TIMEOUT_MS,
                         "Allure 2 checksum"
                 )) {
                final String checksum = new String(input.readAllBytes(), StandardCharsets.US_ASCII).trim();
                if (!SHA256_PATTERN.matcher(checksum).matches()) {
                    throw new IOException("[Allure] Invalid SHA-256 checksum response from "
                            + redactUrl(checksumUrl));
                }
                return checksum.toLowerCase(Locale.ENGLISH);
            }
        } finally {
            connection.disconnect();
        }
    }

    static String redactUrl(final String rawUrl) {
        try {
            final URI uri = new URI(rawUrl);
            if (uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null) {
                return rawUrl;
            }
            if (uri.getHost() == null) {
                return uri.getScheme() + ":<redacted>";
            }
            return new URI(
                    uri.getScheme(),
                    null,
                    uri.getHost(),
                    uri.getPort(),
                    uri.getPath(),
                    null,
                    null
            ).toString();
        } catch (URISyntaxException e) {
            return "<redacted URL>";
        }
    }

    void validateDownloadUrl(final String rawUrl) throws IOException {
        final URL url = new URL(rawUrl);
        if (!HTTP_SCHEME.equalsIgnoreCase(url.getProtocol())
                && !HTTPS_SCHEME.equalsIgnoreCase(url.getProtocol())) {
            throw new IOException("[Allure] Maven repository URL must use http or https");
        }
        if (requireHttps && !HTTPS_SCHEME.equalsIgnoreCase(url.getProtocol())) {
            throw new IOException("[Allure] Managed Allure 2 Maven repository URL must use https");
        }
        if (requireHttps && url.getUserInfo() != null) {
            throw new IOException(
                    "[Allure] Managed Allure 2 Maven repository URL must not contain credentials"
            );
        }
        if (requireHttps && (url.getQuery() != null || url.getRef() != null)) {
            throw new IOException(
                    "[Allure] Managed Allure 2 Maven repository URL must not contain a query or fragment"
            );
        }
    }

    private void validateRedirectTarget(final URL url) throws IOException {
        if (requireHttps && !HTTPS_SCHEME.equalsIgnoreCase(url.getProtocol())) {
            throw new IOException("[Allure] Refusing Maven repository redirect to a non-HTTPS URL");
        }
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

    private long extractEntry(final ZipEntry entry,
                              final SizeLimitedInputStream archiveInput,
                              final FilePath targetDir,
                              final String relativePath,
                              final long remainingBytes) throws IOException, InterruptedException {
        final FilePath destPath = targetDir.child(relativePath);
        if (entry.isDirectory()) {
            destPath.mkdirs();
            final long before = archiveInput.getBytesRead();
            archiveInput.transferTo(java.io.OutputStream.nullOutputStream());
            return archiveInput.getBytesRead() - before;
        }
        if (entry.getSize() > remainingBytes) {
            throw new IOException("[Allure] Archive expands beyond the allowed size");
        }

        final FilePath parent = destPath.getParent();
        if (parent != null) {
            parent.mkdirs();
        }
        final long before = archiveInput.getBytesRead();
        destPath.copyFrom(archiveInput);

        if (BIN_ALLURE.equals(relativePath)) {
            destPath.chmod(493);
        }
        return archiveInput.getBytesRead() - before;
    }

    @SuppressWarnings({"PMD.NcssCount", "PMD.CyclomaticComplexity", "PMD.CognitiveComplexity"})
    void extractZip(
            final FilePath zipFile,
            final FilePath targetDir,
            final String ver) throws IOException, InterruptedException {
        extractZip(zipFile, targetDir, ver, MAX_EXTRACTED_SIZE, MAX_ARCHIVE_ENTRIES);
    }

    @SuppressWarnings({"PMD.NcssCount", "PMD.CyclomaticComplexity", "PMD.CognitiveComplexity"})
    void extractZip(
            final FilePath zipFile,
            final FilePath targetDir,
            final String ver,
            final long maxExtractedSize,
            final int maxArchiveEntries) throws IOException, InterruptedException {

        final String topLevelPrefix = ARTIFACT_ID + DASH + ver + PATH_SEP;
        final String altTopLevelPrefix = ALLURE_BIN + DASH + ver + PATH_SEP;

        targetDir.mkdirs();
        long extractedBytes = 0;
        int entryCount = 0;

        try (InputStream fis = zipFile.read();
             ZipInputStream zis = new ZipInputStream(fis);
             SizeLimitedInputStream archiveInput = new SizeLimitedInputStream(
                     zis,
                     maxExtractedSize,
                     DOWNLOAD_TIMEOUT_MS,
                     "Expanded Allure 2 archive"
             )) {

            archiveInput.checkTimeLimit();
            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                archiveInput.checkTimeLimit();
                entryCount++;
                if (entryCount > maxArchiveEntries) {
                    throw new IOException(
                            "[Allure] Archive contains more than " + maxArchiveEntries + " entries"
                    );
                }
                final String entryName = entry.getName();

                final String relativePath = toRelativePath(entryName, topLevelPrefix, altTopLevelPrefix);

                if (relativePath.isEmpty()) {
                    final long before = archiveInput.getBytesRead();
                    archiveInput.transferTo(java.io.OutputStream.nullOutputStream());
                    extractedBytes += archiveInput.getBytesRead() - before;
                    zis.closeEntry();
                    entry = zis.getNextEntry();
                    continue;
                }

                if (isUnsafeZipEntry(relativePath)) {
                    throw new IOException(
                            "[Allure] Refusing unsafe archive entry: " + safeEntryName(entryName)
                    );
                }

                extractedBytes += extractEntry(
                        entry,
                        archiveInput,
                        targetDir,
                        relativePath,
                        maxExtractedSize - extractedBytes
                );
                zis.closeEntry();
                archiveInput.checkTimeLimit();
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

        if (relativePath.startsWith("\\")) {
            return true;
        }

        if (WINDOWS_DRIVE_ABS.matcher(relativePath).matches()) {
            return true;
        }

        final String[] parts = relativePath.split("[/\\\\]");
        for (final String part : parts) {
            if (isUnsafePathComponent(part)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isUnsafePathComponent(final String part) {
        if (part.isEmpty() || DOT.equals(part) || PARENT_DIRECTORY.equals(part)) {
            return true;
        }
        if (part.endsWith(SPACE) || part.endsWith(DOT) || part.contains(COLON)) {
            return true;
        }
        return containsControlCharacter(part)
                || WINDOWS_INVALID_CHARACTER.matcher(part).find()
                || WINDOWS_RESERVED_NAME.matcher(part).matches();
    }

    private static boolean containsControlCharacter(final String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    private static String safeEntryName(final String entryName) {
        if (entryName == null) {
            return "<null>";
        }
        final StringBuilder safe = new StringBuilder();
        for (int index = 0; index < entryName.length() && safe.length() < 256; index++) {
            final char value = entryName.charAt(index);
            safe.append(Character.isISOControl(value) ? '?' : value);
        }
        return safe.toString();
    }

    private static final class ComputeSha256 extends MasterToSlaveFileCallable<String> {
        @Override
        public String invoke(final File file, final VirtualChannel channel) throws IOException {
            try {
                final MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (InputStream input = new DigestInputStream(Files.newInputStream(file.toPath()), digest)) {
                    input.transferTo(java.io.OutputStream.nullOutputStream());
                }
                final StringBuilder result = new StringBuilder(digest.getDigestLength() * 2);
                for (byte value : digest.digest()) {
                    result.append(String.format("%02x", value & 0xff));
                }
                return result.toString();
            } catch (NoSuchAlgorithmException e) {
                throw new IOException("SHA-256 is unavailable", e);
            }
        }
    }

    /**
     * Descriptor retained so installations created by the released Quick Setup flow remain editable.
     */
    @Extension
    public static class DescriptorImpl extends ToolInstallerDescriptor<AllureCommandlineDirectInstaller> {
        @Override
        @NonNull
        public String getDisplayName() {
            return "Direct download from Maven Central (legacy)";
        }

        @Override
        public boolean isApplicable(final Class<? extends ToolInstallation> toolType) {
            return false;
        }
    }
}
