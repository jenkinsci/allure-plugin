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
import hudson.Launcher;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Common interface for Allure tool installations (both Allure 2 and Allure 3).
 */
public interface AllureInstallation {

    /**
     * Get the executable path for the allure command.
     *
     * @param launcher the launcher to use for remote execution
     * @return the path to the allure executable
     * @throws InterruptedException if the operation is interrupted
     * @throws IOException if an I/O error occurs
     */
    String getExecutable(@NonNull Launcher launcher) throws InterruptedException, IOException;

    /**
     * Get the command prefix used to invoke Allure. Most installations consist of one executable.
     * Managed Allure 3 installations return the private Node.js executable and CLI script as
     * separate arguments so Jenkins never has to forward workspace paths through a batch file.
     *
     * @param launcher the launcher to use for remote inspection
     * @return command arguments that must precede Allure CLI arguments
     * @throws InterruptedException if the operation is interrupted
     * @throws IOException if an I/O error occurs
     */
    default List<String> getCommandPrefix(final @NonNull Launcher launcher)
            throws InterruptedException, IOException {
        return Collections.singletonList(getExecutable(launcher));
    }

    /**
     * Get the resolved Allure version. Managed installations return an exact version;
     * legacy installations may return only the detected major version.
     *
     * @param launcher the launcher to use for remote inspection
     * @return the exact or best-known version
     * @throws InterruptedException if the operation is interrupted
     * @throws IOException if an I/O error occurs
     */
    default String getVersion(final @NonNull Launcher launcher) throws InterruptedException, IOException {
        return getMajorVersion(launcher);
    }

    /**
     * Get the major version of Allure (e.g., "1", "2", or "3").
     *
     * @param launcher the launcher to use for remote execution
     * @return the major version string
     * @throws InterruptedException if the operation is interrupted
     * @throws IOException if an I/O error occurs
     */
    String getMajorVersion(@NonNull Launcher launcher) throws InterruptedException, IOException;

    /**
     * Get the name of this installation.
     *
     * @return the installation name
     */
    String getName();
}
