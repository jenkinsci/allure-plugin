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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.stream.Stream;

enum NodePlatform {

    LINUX_X64(
            "linux-x64",
            "ace9fa104992ed0829642629c46ca7bd7fd6e76278cb96c958c4b387d29658ea",
            56_320_534L,
            false
    ),
    LINUX_ARM64(
            "linux-arm64",
            "734ff04fa7f8ed2e8a78d40cacf5ac3fc4515dac2858757cbab313eb483ba8a2",
            56_098_426L,
            false
    ),
    DARWIN_X64(
            "darwin-x64",
            "2526230ad7d922be82d4fdb1e7ee1e84303e133e3b4b0ec4c2897ab31de0253d",
            52_370_789L,
            false
    ),
    DARWIN_ARM64(
            "darwin-arm64",
            "25495ff85bd89e2d8a24d88566d7e2f827c6b0d3d872b2cebf75371f93fcb1fe",
            51_193_489L,
            false
    ),
    WINDOWS_X64(
            "win-x64",
            "6e50ce5498c0cebc20fd39ab3ff5df836ed2f8a31aa093cecad8497cff126d70",
            36_358_527L,
            true
    ),
    WINDOWS_ARM64(
            "win-arm64",
            "a7b7c68490e4a8cde1921fe5a0cfb3001d53f9c839e416903e4f28e727b62f60",
            32_730_793L,
            true
    );

    private final String classifier;
    private final String sha256;
    private final long archiveSize;
    private final boolean windows;

    private static final String AMD64 = "amd64";
    private static final String X86_64 = "x86_64";
    private static final String AARCH64 = "aarch64";
    private static final String ARM64 = "arm64";
    private static final String LINUX = "linux";
    private static final String MUSL_LOADER_PREFIX = "ld-musl-";
    private static final String MUSL_LOADER_SUFFIX = ".so.1";

    NodePlatform(final String classifier,
                 final String sha256,
                 final long archiveSize,
                 final boolean windows) {
        this.classifier = classifier;
        this.sha256 = sha256;
        this.archiveSize = archiveSize;
        this.windows = windows;
    }

    static NodePlatform detect() throws IOException {
        final String osName = System.getProperty("os.name", "");
        final boolean linux = osName.toLowerCase(Locale.ENGLISH).contains(LINUX);
        return detect(
                osName,
                System.getProperty("os.arch", ""),
                linux && isMuslLinux()
        );
    }

    static NodePlatform detect(final String osName,
                               final String architecture,
                               final boolean musl) throws IOException {
        final String os = osName.toLowerCase(Locale.ENGLISH);
        final String arch = architecture.toLowerCase(Locale.ENGLISH);

        if (os.contains(LINUX)) {
            if (musl) {
                throw new IOException(
                        "Managed Node.js is not available for musl-based agents, including Alpine. "
                                + "Configure an existing Allure installation for this node."
                );
            }
            return forArchitecture(arch, LINUX_X64, LINUX_ARM64, osName, architecture);
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return forArchitecture(arch, DARWIN_X64, DARWIN_ARM64, osName, architecture);
        }
        if (os.contains("windows")) {
            return forArchitecture(arch, WINDOWS_X64, WINDOWS_ARM64, osName, architecture);
        }
        throw unsupported(osName, architecture);
    }

    @SuppressFBWarnings(
            value = "DMI_HARDCODED_ABSOLUTE_FILENAME",
            justification = "These are the standard OS paths used to identify Alpine and the musl loader"
    )
    private static boolean isMuslLinux() throws IOException {
        return Files.exists(Paths.get("/etc/alpine-release"))
                || containsMuslLoader(Paths.get("/lib"))
                || containsMuslLoader(Paths.get("/lib64"))
                || containsMuslLoader(Paths.get("/usr/lib"));
    }

    private static boolean containsMuslLoader(final Path directory) throws IOException {
        if (Files.notExists(directory)) {
            return false;
        }
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.map(path -> path.getFileName().toString())
                    .anyMatch(name -> name.startsWith(MUSL_LOADER_PREFIX)
                            && name.endsWith(MUSL_LOADER_SUFFIX));
        }
    }

    private static NodePlatform forArchitecture(final String architecture,
                                                final NodePlatform x64,
                                                final NodePlatform arm64,
                                                final String originalOs,
                                                final String originalArchitecture) throws IOException {
        if (AMD64.equals(architecture) || X86_64.equals(architecture)) {
            return x64;
        }
        if (AARCH64.equals(architecture) || ARM64.equals(architecture)) {
            return arm64;
        }
        throw unsupported(originalOs, originalArchitecture);
    }

    private static IOException unsupported(final String osName, final String architecture) {
        return new IOException(
                "Managed Node.js does not support agent platform "
                        + osName + "/" + architecture
                        + ". Configure an existing Allure installation for this node."
        );
    }

    String getClassifier() {
        return classifier;
    }

    String getArchiveExtension() {
        return windows ? "zip" : "tar.gz";
    }

    String getSha256() {
        return sha256;
    }

    long getArchiveSize() {
        return archiveSize;
    }

    boolean isWindows() {
        return windows;
    }

    String archiveFileName() {
        return nodeHomeName() + "." + getArchiveExtension();
    }

    String nodeHomeName() {
        return "node-v" + AllureRuntimeManifest.RECOMMENDED_NODE_VERSION + "-" + classifier;
    }

    String nodeExecutableRelativePath() {
        return nodeHomeName() + (windows ? "/node.exe" : "/bin/node");
    }

    String npmCliRelativePath() {
        return nodeHomeName()
                + (windows ? "/node_modules/npm/bin/npm-cli.js" : "/lib/node_modules/npm/bin/npm-cli.js");
    }
}
