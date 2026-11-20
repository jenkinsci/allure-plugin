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
            "5848553868b402d3fabb5b09270649977780a390f832155721025a08b5104455",
            62_893_692L,
            false
    ),
    LINUX_ARM64(
            "linux-arm64",
            "439e9c438acdf6a6ffe47418da709153d66c4bed812e30a473faacb107dd2791",
            62_611_009L,
            false
    ),
    DARWIN_X64(
            "darwin-x64",
            "525c9b29bb0263d687de60a4cec0e2a6db876a8eb032369e5795eca71e7c358e",
            59_802_926L,
            false
    ),
    DARWIN_ARM64(
            "darwin-arm64",
            "d38bd5e5a2ddce6bbb5ce45e46e208337da4d51fffe611e567fd32073d6439f6",
            58_334_661L,
            false
    ),
    WINDOWS_X64(
            "win-x64",
            "dcc60d59a0746999f99064a01c020445d54d84228c691cdbf25b49ac3b59150a",
            41_157_375L,
            true
    ),
    WINDOWS_ARM64(
            "win-arm64",
            "a9427139595156ef484e1fb08957676e09d4232368dd1743a2a9f93f3d241b14",
            37_005_985L,
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
