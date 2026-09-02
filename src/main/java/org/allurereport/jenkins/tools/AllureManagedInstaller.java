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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.ProxyConfiguration;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolInstallerDescriptor;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * Installs either generation of the Allure commandline behind one Jenkins tool.
 * Allure 3 receives a private Node.js runtime and never changes the build's Node.js.
 */
@SuppressWarnings({
        "ClassDataAbstractionCoupling",
        "PMD.GodClass",
        "PMD.NcssCount",
        "PMD.TooManyMethods"
})
public class AllureManagedInstaller extends ToolInstaller {

    public static final String VERSION_POLICY_RECOMMENDED = "recommended-3";
    public static final String VERSION_POLICY_RECOMMENDED_ALLURE_2 = "recommended-2";
    public static final String VERSION_POLICY_FIXED = "fixed";
    public static final String DEFAULT_NODE_DOWNLOAD_BASE_URL = "https://nodejs.org/dist";
    public static final String DEFAULT_NPM_REGISTRY = "https://registry.npmjs.org";

    static final Pattern SEMVER_PATTERN =
            Pattern.compile(
                    "^\\d+\\.\\d+\\.\\d+"
                            + "(?:-[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?"
                            + "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$"
            );

    private static final String RELEASES_DIRECTORY = "releases";
    private static final String ALLURE_EXECUTABLE = "allure";
    private static final String ALLURE_DIRECTORY = ALLURE_EXECUTABLE;
    private static final String WINDOWS_ALLURE_EXECUTABLE = "allure.cmd";
    private static final String BIN_DIRECTORY = "bin";
    private static final String MANIFEST_FILE = ".allure-installation";
    private static final String NODE_CACHE_DIRECTORY = "caches/allure-plugin/node";
    private static final String NPM_CACHE_DIRECTORY = "cache/npm";
    private static final String NPM_LOCK_DIRECTORY = "cache/npm-locks";
    private static final String PACKAGE_JSON = "package.json";
    private static final String PACKAGE_LOCK_JSON = "package-lock.json";
    private static final String PACKAGES = "packages";
    private static final String ALLURE_CLI_PATH = "node_modules/allure/cli.js";
    private static final String USER_AGENT = "Jenkins-Allure-Plugin";
    private static final String PATH_SEPARATOR = "/";
    private static final String LF = "\n";
    private static final String CRLF = "\r\n";
    private static final String END_QUOTED_ARGUMENT = "\" ";
    private static final String WINDOWS_RUNTIME_PREFIX = "\"%~dp0..\\";
    private static final String HTTP_SCHEME = "http";
    private static final String HTTPS_SCHEME = "https";
    private static final String FILE_SCHEME = "file";
    private static final String SHA_256 = "SHA-256";
    private static final String SHA_256_UNAVAILABLE = "SHA-256 is unavailable";
    private static final String TEMPORARY_SUFFIX = ".tmp";
    private static final int CONNECT_TIMEOUT_MILLIS = 15_000;
    private static final int READ_TIMEOUT_MILLIS = 120_000;
    private static final long DOWNLOAD_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(10);
    private static final int INSTALL_TIMEOUT_MINUTES = 10;
    private static final int VERIFY_TIMEOUT_MINUTES = 2;
    private static final int MAX_PROCESS_OUTPUT = 20_000;
    private static final int OUTPUT_DRAIN_TIMEOUT_SECONDS = 5;
    private static final long MAX_PACKAGE_LOCK_SIZE = 10L * 1024 * 1024;
    private static final int MAX_VERSION_LENGTH = 128;
    private static final int PACKAGE_LOCK_VERSION = 3;
    private static final int EXECUTABLE_MODE = 493; // 0755
    private static final Pattern SHA512_INTEGRITY =
            Pattern.compile("^sha512-[A-Za-z0-9+/]+={0,2}$");
    private static final Map<String, Object> CACHE_LOCKS = new ConcurrentHashMap<>();
    private static final Map<String, Object> NPM_LOCKS = new ConcurrentHashMap<>();

    private final String versionPolicy;
    private String version;
    private String allure2BaseUrl;
    private String nodeDownloadBaseUrl;
    private String npmRegistry;

    @DataBoundConstructor
    public AllureManagedInstaller(final String versionPolicy) {
        super(null);
        this.versionPolicy = versionPolicy;
    }

    public String getVersionPolicy() {
        return StringUtils.defaultIfBlank(versionPolicy, VERSION_POLICY_RECOMMENDED);
    }

    public String getVersion() {
        return version;
    }

    @DataBoundSetter
    public void setVersion(final String version) {
        this.version = StringUtils.trimToNull(version);
    }

    public String getAllure2BaseUrl() {
        return allure2BaseUrl;
    }

    @DataBoundSetter
    public void setAllure2BaseUrl(final String allure2BaseUrl) {
        this.allure2BaseUrl = StringUtils.trimToNull(allure2BaseUrl);
    }

    public String getNodeDownloadBaseUrl() {
        return nodeDownloadBaseUrl;
    }

    @DataBoundSetter
    public void setNodeDownloadBaseUrl(final String nodeDownloadBaseUrl) {
        this.nodeDownloadBaseUrl = StringUtils.trimToNull(nodeDownloadBaseUrl);
    }

    public String getNpmRegistry() {
        return npmRegistry;
    }

    @DataBoundSetter
    public void setNpmRegistry(final String npmRegistry) {
        this.npmRegistry = StringUtils.trimToNull(npmRegistry);
    }

    public boolean isRecommended() {
        final String policy = getVersionPolicy();
        return VERSION_POLICY_RECOMMENDED.equals(policy)
                || VERSION_POLICY_RECOMMENDED_ALLURE_2.equals(policy);
    }

    public String getResolvedVersion() {
        final String policy = getVersionPolicy();
        if (VERSION_POLICY_RECOMMENDED_ALLURE_2.equals(policy)) {
            return AllureRuntimeManifest.RECOMMENDED_ALLURE_2_VERSION;
        }
        if (VERSION_POLICY_RECOMMENDED.equals(policy)) {
            return AllureRuntimeManifest.RECOMMENDED_ALLURE_VERSION;
        }
        return version;
    }

    String resolveVersion() throws IOException {
        final String policy = getVersionPolicy();
        if (!VERSION_POLICY_RECOMMENDED.equals(policy)
                && !VERSION_POLICY_RECOMMENDED_ALLURE_2.equals(policy)
                && !VERSION_POLICY_FIXED.equals(policy)) {
            throw new IOException("Unknown Allure version policy: " + policy);
        }
        final String resolved = getResolvedVersion();
        if (StringUtils.isBlank(resolved)
                || resolved.length() > MAX_VERSION_LENGTH
                || !SEMVER_PATTERN.matcher(resolved).matches()) {
            throw new IOException("Allure version must be an exact semantic version, for example 3.16.0");
        }
        final int major = AllureRuntimeManifest.majorVersion(resolved);
        if (major != 2 && major != 3) {
            throw new IOException("Managed installation supports Allure 2.x and 3.x; requested " + resolved);
        }
        return resolved;
    }

    String releaseId() throws IOException {
        return AllureRuntimeManifest.releaseId(resolveVersion());
    }

    static String executableRelativePath(final String version, final boolean windows) {
        return RELEASES_DIRECTORY + PATH_SEPARATOR + AllureRuntimeManifest.releaseId(version)
                + PATH_SEPARATOR + BIN_DIRECTORY + PATH_SEPARATOR
                + (windows ? WINDOWS_ALLURE_EXECUTABLE : ALLURE_EXECUTABLE);
    }

    static List<String> commandRelativePaths(final String version, final NodePlatform platform) {
        final String releasePrefix = RELEASES_DIRECTORY + PATH_SEPARATOR
                + AllureRuntimeManifest.releaseId(version) + PATH_SEPARATOR;
        return Arrays.asList(
                releasePrefix + platform.nodeExecutableRelativePath(),
                releasePrefix + ALLURE_DIRECTORY + PATH_SEPARATOR + ALLURE_CLI_PATH
        );
    }

    @Override
    public FilePath performInstallation(final ToolInstallation tool,
                                        final Node node,
                                        final TaskListener log) throws IOException, InterruptedException {
        final String resolvedVersion = resolveVersion();
        if (AllureRuntimeManifest.isAllure2(resolvedVersion)) {
            return installAllure2(tool, node, log, resolvedVersion);
        }
        return installAllure3(tool, node, log, resolvedVersion);
    }

    private FilePath installAllure2(final ToolInstallation tool,
                                    final Node node,
                                    final TaskListener log,
                                    final String resolvedVersion) throws IOException, InterruptedException {
        final AllureCommandlineDirectInstaller installer =
                new AllureCommandlineDirectInstaller(resolvedVersion);
        installer.setBaseUrl(allure2BaseUrl);
        installer.setVerifyChecksum(true);
        installer.setRequireHttps(true);
        return installer.performInstallation(tool, node, log);
    }

    private FilePath installAllure3(final ToolInstallation tool,
                                    final Node node,
                                    final TaskListener log,
                                    final String resolvedVersion) throws IOException, InterruptedException {
        final NodePlatform platform = detectPlatform(node);
        final FilePath installRoot = preferredLocation(tool, node);
        final FilePath releases = installRoot.child(RELEASES_DIRECTORY);
        final FilePath release = releases.child(AllureRuntimeManifest.releaseId(resolvedVersion));
        final String expectedManifest = manifest(resolvedVersion, platform);

        if (isValidInstallation(release, platform, expectedManifest)) {
            log.getLogger().printf(
                    "[Allure] Using cached Allure %s with Node.js %s at %s%n",
                    resolvedVersion,
                    AllureRuntimeManifest.RECOMMENDED_NODE_VERSION,
                    release.getRemote()
            );
            return installRoot;
        }

        releases.mkdirs();
        final FilePath staging = releases.createTempDir(".allure-install-", null);
        try {
            log.getLogger().printf(
                    "[Allure] Installing Allure %s with private Node.js %s on %s%n",
                    resolvedVersion,
                    AllureRuntimeManifest.RECOMMENDED_NODE_VERSION,
                    platform.getClassifier()
            );
            installNode(staging, platform, log);
            installAllureRuntime(staging, installRoot, platform, resolvedVersion, node, log);
            writeLaunchers(staging, platform);
            verifyRuntime(staging, platform, resolvedVersion, node);
            staging.child(MANIFEST_FILE).write(expectedManifest, StandardCharsets.UTF_8.name());

            if (release.exists()) {
                release.deleteRecursive();
            }
            staging.renameTo(release);
            log.getLogger().println("[Allure] Installation complete: " + release.getRemote());
            return installRoot;
        } finally {
            if (staging.exists()) {
                staging.deleteRecursive();
            }
        }
    }

    NodePlatform detectPlatform(final Node node) throws IOException, InterruptedException {
        final VirtualChannel channel = node.getChannel();
        if (channel == null) {
            throw new IOException("Cannot detect the Allure agent platform because the agent is offline");
        }
        return channel.call(new DetectPlatform());
    }

    private boolean isValidInstallation(final FilePath release,
                                        final NodePlatform platform,
                                        final String expectedManifest) throws IOException, InterruptedException {
        if (!release.exists()) {
            return false;
        }
        final FilePath manifestFile = release.child(MANIFEST_FILE);
        if (!manifestFile.exists() || !expectedManifest.equals(manifestFile.readToString())) {
            return false;
        }
        return release.child(platform.nodeExecutableRelativePath()).exists()
                && release.child(ALLURE_DIRECTORY).child(ALLURE_CLI_PATH).exists()
                && release.child(BIN_DIRECTORY)
                .child(platform.isWindows() ? WINDOWS_ALLURE_EXECUTABLE : ALLURE_EXECUTABLE)
                .exists();
    }

    private void installNode(final FilePath staging,
                             final NodePlatform platform,
                             final TaskListener log) throws IOException, InterruptedException {
        final Path cachedArchive = acquireNodeArchive(platform, log);
        final FilePath remoteArchive = staging.child(platform.archiveFileName());
        try (InputStream input = Files.newInputStream(cachedArchive)) {
            remoteArchive.copyFrom(input);
        }

        if (platform.isWindows()) {
            remoteArchive.unzip(staging);
        } else {
            remoteArchive.untar(staging, FilePath.TarCompression.GZIP);
            staging.child(platform.nodeExecutableRelativePath()).chmod(EXECUTABLE_MODE);
        }
        remoteArchive.delete();
    }

    Path acquireNodeArchive(final NodePlatform platform,
                            final TaskListener log) throws IOException {
        final Path cacheDirectory = Jenkins.get().getRootDir().toPath()
                .resolve(NODE_CACHE_DIRECTORY)
                .resolve(AllureRuntimeManifest.RECOMMENDED_NODE_VERSION)
                .resolve(platform.getClassifier())
                .resolve(platform.getSha256());
        final Path cachedArchive = cacheDirectory.resolve(platform.archiveFileName());
        final Object lock = CACHE_LOCKS.computeIfAbsent(platform.getSha256(), ignored -> new Object());

        synchronized (lock) {
            if (Files.exists(cachedArchive) && platform.getSha256().equals(sha256(cachedArchive))) {
                return cachedArchive;
            }
            Files.createDirectories(cacheDirectory);
            Files.deleteIfExists(cachedArchive);

            final Path staging = Files.createTempFile(
                    cacheDirectory,
                    platform.archiveFileName(),
                    TEMPORARY_SUFFIX
            );
            try {
                final URL downloadUrl = nodeArchiveUrl(platform);
                log.getLogger().println("[Allure] Downloading private Node.js runtime from " + downloadUrl);
                download(downloadUrl, staging, platform.getArchiveSize());
                final String actualDigest = sha256(staging);
                if (!platform.getSha256().equals(actualDigest)) {
                    throw new IOException(
                            "Checksum mismatch for " + platform.archiveFileName()
                                    + ": expected " + platform.getSha256() + " but got " + actualDigest
                    );
                }
                atomicMove(staging, cachedArchive);
                return cachedArchive;
            } finally {
                Files.deleteIfExists(staging);
            }
        }
    }

    private URL nodeArchiveUrl(final NodePlatform platform) throws IOException {
        final String baseUrl = StringUtils.defaultIfBlank(
                nodeDownloadBaseUrl,
                DEFAULT_NODE_DOWNLOAD_BASE_URL
        ).replaceAll("/+$", "");
        final String rawUrl = baseUrl + "/v" + AllureRuntimeManifest.RECOMMENDED_NODE_VERSION
                + PATH_SEPARATOR + platform.archiveFileName();
        try {
            final URI uri = new URI(rawUrl);
            validateRemoteUri(uri, "Node.js download URL", true);
            return uri.toURL();
        } catch (URISyntaxException e) {
            throw new IOException("Invalid Node.js download URL: " + rawUrl, e);
        }
    }

    static void download(final URL source,
                         final Path destination,
                         final long expectedSize) throws IOException {
        final URLConnection connection = FILE_SCHEME.equals(source.getProtocol())
                ? source.openConnection()
                : ProxyConfiguration.open(source);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        if (connection instanceof HttpURLConnection) {
            final int responseCode = ((HttpURLConnection) connection).getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Node.js download returned HTTP " + responseCode + " from " + source);
            }
        }
        final long declaredSize = connection.getContentLengthLong();
        if (declaredSize >= 0 && declaredSize != expectedSize) {
            throw new IOException(
                    "Node.js archive has unexpected Content-Length " + declaredSize
                            + "; expected " + expectedSize
            );
        }
        try (InputStream sourceStream = connection.getInputStream();
             SizeLimitedInputStream input = new SizeLimitedInputStream(
                     sourceStream,
                     expectedSize,
                     DOWNLOAD_TIMEOUT_MILLIS,
                     "Node.js archive"
             );
             OutputStream output = Files.newOutputStream(destination)) {
            input.transferTo(output);
            if (input.getBytesRead() != expectedSize) {
                throw new IOException(
                        "Node.js archive is truncated: expected " + expectedSize
                                + " bytes but received " + input.getBytesRead()
                );
            }
        } finally {
            if (connection instanceof HttpURLConnection) {
                ((HttpURLConnection) connection).disconnect();
            }
        }
    }

    private void installAllureRuntime(final FilePath staging,
                                      final FilePath installRoot,
                                      final NodePlatform platform,
                                      final String resolvedVersion,
                                      final Node node,
                                      final TaskListener log) throws IOException, InterruptedException {
        final FilePath allureHome = staging.child(ALLURE_DIRECTORY);
        allureHome.mkdirs();
        if (AllureRuntimeManifest.RECOMMENDED_ALLURE_VERSION.equals(resolvedVersion)) {
            try (InputStream runtime = AllureManagedInstaller.class.getResourceAsStream(
                    AllureRuntimeManifest.runtimeResource())) {
                if (runtime == null) {
                    throw new IOException(
                            "Bundled Allure runtime is missing: " + AllureRuntimeManifest.runtimeResource()
                    );
                }
                allureHome.unzipFrom(runtime);
            }
            return;
        }

        installFromNpm(staging, installRoot, platform, resolvedVersion, node, log);
    }

    private void installFromNpm(final FilePath staging,
                                final FilePath installRoot,
                                final NodePlatform platform,
                                final String resolvedVersion,
                                final Node node,
                                final TaskListener log) throws IOException, InterruptedException {
        log.getLogger().printf(
                "[Allure] Allure %s is not bundled; installing it from the configured npm registry%n",
                resolvedVersion
        );
        final FilePath allureHome = staging.child(ALLURE_DIRECTORY);
        allureHome.child(PACKAGE_JSON).write(
                packageJson(resolvedVersion),
                StandardCharsets.UTF_8.name()
        );
        final FilePath npmCache = installRoot.child(NPM_CACHE_DIRECTORY);
        npmCache.mkdirs();
        final String registry = validatedNpmRegistry();
        preparePackageLock(staging, installRoot, platform, resolvedVersion, node, log);
        runOnNode(
                node,
                npmCommand(staging, allureHome, npmCache, platform, registry, "ci", false),
                INSTALL_TIMEOUT_MINUTES,
                "npm ci for allure@" + resolvedVersion
        );
    }

    private void preparePackageLock(final FilePath staging,
                                    final FilePath installRoot,
                                    final NodePlatform platform,
                                    final String resolvedVersion,
                                    final Node node,
                                    final TaskListener log) throws IOException, InterruptedException {
        final FilePath allureHome = staging.child(ALLURE_DIRECTORY);
        final FilePath npmCache = installRoot.child(NPM_CACHE_DIRECTORY);
        final String registry = validatedNpmRegistry();
        final FilePath packageLock = allureHome.child(PACKAGE_LOCK_JSON);
        final FilePath cachedLock = npmLockCache(installRoot, resolvedVersion, registry);
        final Object lock = NPM_LOCKS.computeIfAbsent(cachedLock.getRemote(), ignored -> new Object());

        synchronized (lock) {
            if (cachedLock.exists()) {
                try {
                    validatePackageLock(cachedLock, resolvedVersion);
                    cachedLock.copyTo(packageLock);
                    return;
                } catch (IOException e) {
                    log.getLogger().println("[Allure] Discarding invalid cached npm lockfile: " + e.getMessage());
                    cachedLock.delete();
                }
            }

            runOnNode(
                    node,
                    npmCommand(staging, allureHome, npmCache, platform, registry, "install", true),
                    INSTALL_TIMEOUT_MINUTES,
                    "npm dependency resolution for allure@" + resolvedVersion
            );
            validatePackageLock(packageLock, resolvedVersion);
            cachePackageLock(packageLock, cachedLock);
        }
    }

    static String packageJson(final String resolvedVersion) {
        return "{\"name\":\"allure-jenkins-runtime\",\"private\":true,\"dependencies\":{\"allure\":\""
                + resolvedVersion + "\"}}" + LF;
    }

    static List<String> npmCommand(final FilePath staging,
                                   final FilePath allureHome,
                                   final FilePath npmCache,
                                   final NodePlatform platform,
                                   final String registry,
                                   final String action,
                                   final boolean packageLockOnly) {
        final List<String> command = new ArrayList<>();
        command.add(staging.child(platform.nodeExecutableRelativePath()).getRemote());
        command.add(staging.child(platform.npmCliRelativePath()).getRemote());
        command.add("--prefix");
        command.add(allureHome.getRemote());
        command.add(action);
        if (packageLockOnly) {
            command.add("--package-lock-only");
        }
        command.add("--ignore-scripts");
        command.add("--omit=dev");
        command.add("--no-audit");
        command.add("--no-fund");
        command.add("--cache");
        command.add(npmCache.getRemote());
        command.add("--registry");
        command.add(registry);
        return command;
    }

    private static FilePath npmLockCache(final FilePath installRoot,
                                         final String resolvedVersion,
                                         final String registry) throws IOException {
        return installRoot.child(NPM_LOCK_DIRECTORY)
                .child(AllureRuntimeManifest.releaseId(resolvedVersion))
                .child(sha256(registry).substring(0, 16))
                .child(PACKAGE_LOCK_JSON);
    }

    static void validatePackageLock(final FilePath packageLock,
                                    final String resolvedVersion)
            throws IOException, InterruptedException {
        if (!packageLock.exists()) {
            throw new IOException("npm did not create " + PACKAGE_LOCK_JSON);
        }
        final long size = packageLock.length();
        if (size <= 0 || size > MAX_PACKAGE_LOCK_SIZE) {
            throw new IOException(PACKAGE_LOCK_JSON + " has an invalid size: " + size);
        }
        try (InputStream input = packageLock.read();
             SizeLimitedInputStream limitedInput = new SizeLimitedInputStream(
                     input,
                     MAX_PACKAGE_LOCK_SIZE,
                     TimeUnit.MINUTES.toMillis(VERIFY_TIMEOUT_MINUTES),
                     PACKAGE_LOCK_JSON
             )) {
            final JsonNode root = new ObjectMapper().readTree(limitedInput);
            validatePackageLockStructure(root, resolvedVersion);
            final String lockedVersion = root.path(PACKAGES)
                    .path("node_modules/allure")
                    .path("version")
                    .asText();
            if (!resolvedVersion.equals(lockedVersion)) {
                throw new IOException(
                        PACKAGE_LOCK_JSON + " pins Allure " + lockedVersion
                                + " instead of " + resolvedVersion
                );
            }
        }
    }

    private static void validatePackageLockStructure(final JsonNode root,
                                                     final String resolvedVersion) throws IOException {
        final JsonNode packages = root.path(PACKAGES);
        if (!root.isObject()
                || root.path("lockfileVersion").asInt(-1) != PACKAGE_LOCK_VERSION
                || !packages.isObject()) {
            throw new IOException(PACKAGE_LOCK_JSON + " must be an npm v3 lockfile");
        }
        final String rootDependency = packages.path("")
                .path("dependencies")
                .path(ALLURE_EXECUTABLE)
                .asText();
        if (!resolvedVersion.equals(rootDependency)) {
            throw new IOException(PACKAGE_LOCK_JSON + " must pin the exact Allure dependency");
        }

        final java.util.Iterator<Map.Entry<String, JsonNode>> entries = packages.fields();
        while (entries.hasNext()) {
            final Map.Entry<String, JsonNode> entry = entries.next();
            if (entry.getKey().isEmpty()) {
                continue;
            }
            validateLockedPackage(entry.getValue());
        }
    }

    private static void validateLockedPackage(final JsonNode lockedPackage) throws IOException {
        final String integrity = lockedPackage.path("integrity").asText();
        if (!SHA512_INTEGRITY.matcher(integrity).matches()) {
            throw new IOException(PACKAGE_LOCK_JSON + " contains a package without SHA-512 integrity");
        }
        final String resolved = lockedPackage.path("resolved").asText();
        try {
            final URI uri = new URI(resolved);
            validateRemoteUri(uri, PACKAGE_LOCK_JSON + " package URL", false);
            if (!HTTPS_SCHEME.equals(StringUtils.lowerCase(uri.getScheme()))) {
                throw new IOException(PACKAGE_LOCK_JSON + " package URLs must use https");
            }
        } catch (URISyntaxException e) {
            throw new IOException(PACKAGE_LOCK_JSON + " contains an invalid package URL", e);
        }
    }

    private static void cachePackageLock(final FilePath packageLock,
                                         final FilePath cachedLock) throws IOException, InterruptedException {
        final FilePath cacheDirectory = cachedLock.getParent();
        if (cacheDirectory == null) {
            throw new IOException("Cannot determine npm lockfile cache directory");
        }
        cacheDirectory.mkdirs();
        final FilePath staging = cacheDirectory.createTempFile("package-lock-", TEMPORARY_SUFFIX);
        try {
            packageLock.copyTo(staging);
            if (cachedLock.exists()) {
                cachedLock.delete();
            }
            staging.renameTo(cachedLock);
        } finally {
            if (staging.exists()) {
                staging.delete();
            }
        }
    }

    String validatedNpmRegistry() throws IOException {
        final String registry = StringUtils.defaultIfBlank(npmRegistry, DEFAULT_NPM_REGISTRY);
        try {
            final URI uri = new URI(registry);
            validateRemoteUri(uri, "npm registry URL", false);
            if (!HTTPS_SCHEME.equals(StringUtils.lowerCase(uri.getScheme()))) {
                throw new IOException("npm registry URL must use https");
            }
            return uri.toString();
        } catch (URISyntaxException e) {
            throw new IOException("Invalid npm registry URL: " + registry, e);
        }
    }

    private void writeLaunchers(final FilePath staging,
                                final NodePlatform platform) throws IOException, InterruptedException {
        final FilePath bin = staging.child(BIN_DIRECTORY);
        bin.mkdirs();
        if (platform.isWindows()) {
            bin.child(WINDOWS_ALLURE_EXECUTABLE)
                    .write(windowsLauncher(platform), StandardCharsets.UTF_8.name());
        } else {
            final FilePath launcher = bin.child(ALLURE_EXECUTABLE);
            launcher.write(unixLauncher(platform), StandardCharsets.UTF_8.name());
            launcher.chmod(EXECUTABLE_MODE);
        }
    }

    private String unixLauncher(final NodePlatform platform) {
        return "#!/bin/sh" + LF
                + "SCRIPT_DIR=$(CDPATH= cd -- \"$(dirname \"$0\")\" && pwd)" + LF
                + "RUNTIME_DIR=$(CDPATH= cd -- \"$SCRIPT_DIR/..\" && pwd)" + LF
                + "exec \"$RUNTIME_DIR/" + platform.nodeExecutableRelativePath() + END_QUOTED_ARGUMENT
                + "\"$RUNTIME_DIR/" + ALLURE_DIRECTORY + PATH_SEPARATOR + ALLURE_CLI_PATH
                + "\" \"$@\"" + LF;
    }

    private String windowsLauncher(final NodePlatform platform) {
        return "@echo off" + CRLF
                + "setlocal" + CRLF
                + WINDOWS_RUNTIME_PREFIX
                + platform.nodeExecutableRelativePath().replace('/', '\\') + END_QUOTED_ARGUMENT
                + WINDOWS_RUNTIME_PREFIX + ALLURE_DIRECTORY + "\\"
                + ALLURE_CLI_PATH.replace('/', '\\') + "\" %*" + CRLF;
    }

    private void verifyRuntime(final FilePath staging,
                               final NodePlatform platform,
                               final String resolvedVersion,
                               final Node node) throws IOException, InterruptedException {
        final List<String> command = Arrays.asList(
                staging.child(platform.nodeExecutableRelativePath()).getRemote(),
                staging.child(ALLURE_DIRECTORY).child(ALLURE_CLI_PATH).getRemote(),
                "--version"
        );
        final String output = runOnNode(node, command, VERIFY_TIMEOUT_MINUTES, "Allure runtime verification");
        if (!output.trim().startsWith(resolvedVersion)) {
            throw new IOException(
                    "Installed Allure runtime reported '" + output.trim()
                            + "' instead of " + resolvedVersion
            );
        }
    }

    private static String runOnNode(final Node node,
                                    final List<String> command,
                                    final int timeoutMinutes,
                                    final String description) throws IOException, InterruptedException {
        final VirtualChannel channel = node.getChannel();
        if (channel == null) {
            throw new IOException("Cannot run " + description + " because the agent is offline");
        }
        return channel.call(new RunProcess(command, timeoutMinutes, description));
    }

    private String manifest(final String resolvedVersion, final NodePlatform platform) throws IOException {
        final StringBuilder result = new StringBuilder(256)
                .append("schema=1").append(LF)
                .append("allure.version=").append(resolvedVersion).append(LF)
                .append("node.version=").append(AllureRuntimeManifest.RECOMMENDED_NODE_VERSION).append(LF)
                .append("node.platform=").append(platform.getClassifier()).append(LF)
                .append("node.sha256=").append(platform.getSha256()).append(LF);
        if (!AllureRuntimeManifest.RECOMMENDED_ALLURE_VERSION.equals(resolvedVersion)) {
            result.append("npm.registry.sha256=")
                    .append(sha256(validatedNpmRegistry()))
                    .append(LF);
        }
        return result.toString();
    }

    private static void validateRemoteUri(final URI uri,
                                          final String description,
                                          final boolean allowFile) throws IOException {
        final String scheme = StringUtils.lowerCase(uri.getScheme());
        final boolean http = isHttpScheme(scheme);
        validateRemoteScheme(scheme, description, allowFile, http);
        if (http && StringUtils.isBlank(uri.getHost())) {
            throw new IOException(description + " must include a host");
        }
        if (uri.getUserInfo() != null) {
            throw new IOException(description + " must not contain credentials");
        }
        if (uri.getQuery() != null || uri.getFragment() != null) {
            throw new IOException(description + " must not contain a query or fragment");
        }
    }

    private static boolean isHttpScheme(final String scheme) {
        return HTTP_SCHEME.equals(scheme) || HTTPS_SCHEME.equals(scheme);
    }

    private static void validateRemoteScheme(final String scheme,
                                             final String description,
                                             final boolean allowFile,
                                             final boolean http) throws IOException {
        if (!http && !(allowFile && FILE_SCHEME.equals(scheme))) {
            throw new IOException(description + " must use http, https"
                    + (allowFile ? ", or file" : ""));
        }
    }

    private static String sha256(final Path file) throws IOException {
        try {
            final MessageDigest digest = MessageDigest.getInstance(SHA_256);
            try (InputStream input = new DigestInputStream(Files.newInputStream(file), digest)) {
                input.transferTo(OutputStream.nullOutputStream());
            }
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(SHA_256_UNAVAILABLE, e);
        }
    }

    private static String sha256(final String value) throws IOException {
        try {
            final MessageDigest digest = MessageDigest.getInstance(SHA_256);
            return toHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(SHA_256_UNAVAILABLE, e);
        }
    }

    private static String toHex(final byte[] bytes) {
        final StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value & 0xff));
        }
        return result.toString();
    }

    private static void atomicMove(final Path source, final Path target) throws IOException {
        try {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static String readProcessOutput(final InputStream input) throws IOException {
        final byte[] tail = new byte[MAX_PROCESS_OUTPUT];
        final byte[] chunk = new byte[8192];
        int retained = 0;
        int writePosition = 0;
        int count = input.read(chunk);
        while (count >= 0) {
            if (count > 0) {
                final int firstPart = Math.min(count, MAX_PROCESS_OUTPUT - writePosition);
                System.arraycopy(chunk, 0, tail, writePosition, firstPart);
                final int secondPart = count - firstPart;
                if (secondPart > 0) {
                    System.arraycopy(chunk, firstPart, tail, 0, secondPart);
                }
                writePosition = (writePosition + count) % MAX_PROCESS_OUTPUT;
                retained = Math.min(MAX_PROCESS_OUTPUT, retained + count);
            }
            count = input.read(chunk);
        }

        final ByteArrayOutputStream result = new ByteArrayOutputStream(retained);
        if (retained < MAX_PROCESS_OUTPUT) {
            result.write(tail, 0, retained);
        } else {
            result.write(tail, writePosition, MAX_PROCESS_OUTPUT - writePosition);
            result.write(tail, 0, writePosition);
        }
        return result.toString(StandardCharsets.UTF_8.name());
    }

    private static final class DetectPlatform extends MasterToSlaveCallable<NodePlatform, IOException> {
        @Override
        public NodePlatform call() throws IOException {
            return NodePlatform.detect();
        }
    }

    private static final class RunProcess extends MasterToSlaveCallable<String, IOException> {
        private final List<String> command;
        private final int timeoutMinutes;
        private final String description;

        RunProcess(final List<String> command, final int timeoutMinutes, final String description) {
            this.command = new ArrayList<>(command);
            this.timeoutMinutes = timeoutMinutes;
            this.description = description;
        }

        @Override
        public String call() throws IOException {
            final ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            final Process process = processBuilder.start();
            final ExecutorService outputReader = Executors.newSingleThreadExecutor(task -> {
                final Thread thread = new Thread(task, "allure-process-output");
                thread.setDaemon(true);
                return thread;
            });
            final Future<String> output = outputReader.submit(
                    () -> readProcessOutput(process.getInputStream())
            );
            try {
                final boolean finished;
                try {
                    finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
                } catch (InterruptedException e) {
                    process.destroyForcibly();
                    Thread.currentThread().interrupt();
                    throw new IOException(description + " was interrupted", e);
                }
                if (!finished) {
                    process.destroyForcibly();
                    closeProcessOutput(process);
                    throw new IOException(description + " timed out after " + timeoutMinutes + " minutes");
                }
                final String processOutput = awaitProcessOutput(output);
                if (process.exitValue() != 0) {
                    throw new IOException(
                            description + " failed with exit code " + process.exitValue() + ": " + processOutput
                    );
                }
                return processOutput;
            } finally {
                output.cancel(true);
                closeProcessOutput(process);
                outputReader.shutdownNow();
            }
        }

        private static String awaitProcessOutput(final Future<String> output) throws IOException {
            try {
                return output.get(OUTPUT_DRAIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while reading process output", e);
            } catch (ExecutionException e) {
                throw new IOException("Failed to read process output", e);
            } catch (TimeoutException e) {
                throw new IOException("Timed out while reading process output", e);
            }
        }

        private static void closeProcessOutput(final Process process) {
            try {
                process.getInputStream().close();
            } catch (IOException ignored) {
                // Best-effort cleanup after the process has exited or has been terminated.
            }
        }
    }

    @Extension
    @Symbol("allureManaged")
    public static class DescriptorImpl extends ToolInstallerDescriptor<AllureManagedInstaller> {

        @Override
        @NonNull
        public String getDisplayName() {
            return "Install Allure";
        }

        @Override
        public boolean isApplicable(final Class<? extends ToolInstallation> toolType) {
            return toolType == AllureCommandlineInstallation.class;
        }

        public String getRecommendedVersion() {
            return AllureRuntimeManifest.RECOMMENDED_ALLURE_VERSION;
        }

        public String getRecommendedAllure2Version() {
            return AllureRuntimeManifest.RECOMMENDED_ALLURE_2_VERSION;
        }

        public String getRecommendedNodeVersion() {
            return AllureRuntimeManifest.RECOMMENDED_NODE_VERSION;
        }

        public FormValidation doCheckVersion(@QueryParameter final String value,
                                             @QueryParameter final String versionPolicy) {
            if (VERSION_POLICY_RECOMMENDED_ALLURE_2.equals(versionPolicy)
                    || VERSION_POLICY_RECOMMENDED.equals(versionPolicy)) {
                return FormValidation.ok();
            }
            if (StringUtils.isBlank(value) || !SEMVER_PATTERN.matcher(value.trim()).matches()) {
                return FormValidation.error("Enter an exact version such as 3.16.0 or 2.46.0");
            }
            final int major = AllureRuntimeManifest.majorVersion(value);
            if (major != 2 && major != 3) {
                return FormValidation.error("Only Allure 2.x and 3.x are supported");
            }
            return FormValidation.ok();
        }
    }
}
