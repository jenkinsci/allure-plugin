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
