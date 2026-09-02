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

import hudson.EnvVars;
import hudson.Functions;
import hudson.Util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class AllureLocalInstallationResolver {

    private static final String ALLURE = "allure";
    private static final String ALLURE_BATCH = "allure.bat";
    private static final String ALLURE_COMMAND = "allure.cmd";
    private static final String BIN_DIRECTORY = "bin";
    private static final String LIB_DIRECTORY = "lib";
    private static final String NODE_MODULES = "node_modules";
    private static final String PACKAGE_JSON = "package.json";
    private static final String CAN_FIND_ALLURE_MESSAGE = "Can't find allure commandline <%s>";
    private static final Pattern PACKAGE_VERSION =
            Pattern.compile("\\\"version\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern COMMANDLINE_JAR_VERSION =
            Pattern.compile("^allure-commandline-(.+)\\.jar$");

    private AllureLocalInstallationResolver() {
    }

    static Path executable(final String rawHome) {
        final Path home = home(rawHome);
        return home == null ? null : findExecutableInHome(home);
    }

    static String metadataVersion(final String rawHome) throws IOException {
        final Path home = requiredHome(rawHome);
        final String packageVersion = findPackageVersion(home);
        return packageVersion == null ? findCommandlineJarVersion(home) : packageVersion;
    }

    static String legacyMajorVersion(final String rawHome) {
        final Path home = home(rawHome);
        if (home == null) {
            return null;
        }
        if (Files.exists(home.resolve("app/allure-bundle.jar"))) {
            return "1";
        }
        return Files.isDirectory(home.resolve(LIB_DIRECTORY)) ? "2" : null;
    }

    private static Path requiredHome(final String rawHome) throws IOException {
        final Path resolved = home(rawHome);
        if (resolved == null || Files.notExists(resolved)) {
            throw new IOException(String.format(CAN_FIND_ALLURE_MESSAGE, resolved));
        }
        return resolved;
    }

    private static Path home(final String rawHome) {
        if (rawHome == null) {
            return null;
        }
        final String expanded = Util.replaceMacro(rawHome, EnvVars.masterEnvVars);
        if (expanded == null) {
            return null;
        }

        final Path configuredHome = Paths.get(expanded);
        if (findExecutableInHome(configuredHome) != null) {
            return configuredHome;
        }
        return findExtractedHome(configuredHome);
    }

    private static Path findExtractedHome(final Path configuredHome) {
        final File[] entries = configuredHome.toFile().listFiles();
        if (entries == null) {
            return null;
        }
        for (File entry : entries) {
            if (entry.isDirectory()
                    && entry.getName().startsWith(ALLURE)
                    && findExecutableInHome(entry.toPath()) != null) {
                return entry.toPath();
            }
        }
        return null;
    }

    private static Path findExecutableInHome(final Path home) {
        final Path bin = home.resolve(BIN_DIRECTORY);
        if (Functions.isWindows()) {
            return firstExisting(
                    bin.resolve(ALLURE_BATCH),
                    bin.resolve(ALLURE_COMMAND),
                    home.resolve(ALLURE_BATCH),
                    home.resolve(ALLURE_COMMAND)
            );
        }
        return firstExisting(bin.resolve(ALLURE), home.resolve(ALLURE));
    }

    private static Path firstExisting(final Path... candidates) {
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static String findPackageVersion(final Path home) throws IOException {
        final Path[] candidates = {
            home.resolve(NODE_MODULES).resolve(ALLURE).resolve(PACKAGE_JSON),
            home.resolve(LIB_DIRECTORY).resolve(NODE_MODULES).resolve(ALLURE).resolve(PACKAGE_JSON),
            home.resolve("..").resolve(LIB_DIRECTORY)
                    .resolve(NODE_MODULES).resolve(ALLURE).resolve(PACKAGE_JSON),
        };
        for (Path packageJson : candidates) {
            if (Files.exists(packageJson)) {
                final Matcher matcher = PACKAGE_VERSION.matcher(Files.readString(packageJson));
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        }
        return null;
    }

    private static String findCommandlineJarVersion(final Path home) throws IOException {
        final Path library = home.resolve(LIB_DIRECTORY);
        if (Files.notExists(library)) {
            return null;
        }
        try (Stream<Path> files = Files.list(library)) {
            return files.map(path -> path.getFileName().toString())
                    .map(COMMANDLINE_JAR_VERSION::matcher)
                    .filter(Matcher::matches)
                    .map(matcher -> matcher.group(1))
                    .findFirst()
                    .orElse(null);
        }
    }
}
