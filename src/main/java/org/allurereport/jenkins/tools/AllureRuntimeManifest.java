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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Versions and immutable artifacts tested with this plugin release.
 */
public final class AllureRuntimeManifest {

    public static final int UNKNOWN_MAJOR_VERSION = 0;
    public static final String RECOMMENDED_ALLURE_2_VERSION = "2.46.0";
    public static final String RECOMMENDED_ALLURE_VERSION = "3.16.0";
    public static final String RECOMMENDED_NODE_VERSION = "24.14.1";

    private static final String RUNTIME_REVISION = "1";
    private static final Pattern FIRST_NUMBER = Pattern.compile("(\\d+)");

    private AllureRuntimeManifest() {
    }

    public static int majorVersion(final String version) {
        if (version == null) {
            return UNKNOWN_MAJOR_VERSION;
        }
        final Matcher matcher = FIRST_NUMBER.matcher(version.trim());
        if (!matcher.find()) {
            return UNKNOWN_MAJOR_VERSION;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return UNKNOWN_MAJOR_VERSION;
        }
    }

    public static boolean isAllure2(final String version) {
        return majorVersion(version) == 2;
    }

    public static boolean isAllure3(final String version) {
        return majorVersion(version) >= 3;
    }

    static String runtimeResource() {
        return "/org/allurereport/jenkins/tools/runtime/allure-"
                + RECOMMENDED_ALLURE_VERSION + ".zip";
    }

    static String releaseId(final String allureVersion) {
        return "allure-" + allureVersion
                + "-node-" + RECOMMENDED_NODE_VERSION
                + "-runtime-" + RUNTIME_REVISION;
    }
}
