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
import hudson.tools.DownloadFromUrlInstaller;
import hudson.tools.ToolInstallation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.allurereport.jenkins.Messages;

/**
 * @author Artem Eroshenko {@literal <eroshenkoam@yandex-team.ru>}
 */
public class AllureCommandlineInstaller extends DownloadFromUrlInstaller {

    @DataBoundConstructor
    public AllureCommandlineInstaller(final String id) {
        super(id);
    }

    /**
     * Descriptor implementation for Allure downloading.
     */
    @SuppressWarnings("TrailingComment")
    @Extension
    public static class DescriptorImpl extends DownloadFromUrlInstaller
            .DescriptorImpl<AllureCommandlineInstaller> { //NOSONAR

        @Override
        @NonNull
        public String getDisplayName() {
            return Messages.AllureCommandlineInstaller_DisplayName();
        }

        @Override
        public boolean isApplicable(final Class<? extends ToolInstallation> toolType) {
            return toolType == AllureCommandlineInstallation.class;
        }
    }
}
