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
import hudson.model.DownloadService;
import hudson.tools.DownloadFromUrlInstaller;
import hudson.tools.ToolInstallation;
import net.sf.json.JSONObject;
import org.allurereport.jenkins.Messages;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AllureCommandlineInstaller extends DownloadFromUrlInstaller {

    static final String CURRENT_DESCRIPTOR_ID = AllureCommandlineInstaller.class.getName();
    static final String LEGACY_DESCRIPTOR_ID =
            "ru.yandex.qatools.allure.jenkins.tools.AllureCommandlineInstaller";

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
        public List<? extends DownloadFromUrlInstaller.Installable> getInstallables() throws IOException {
            final List<? extends DownloadFromUrlInstaller.Installable> currentInstallables = super.getInstallables();
            if (!currentInstallables.isEmpty()) {
                return currentInstallables;
            }

            return readInstallables(LEGACY_DESCRIPTOR_ID);
        }

        @Override
        public boolean isApplicable(final Class<? extends ToolInstallation> toolType) {
            return toolType == AllureCommandlineInstallation.class;
        }

        private List<? extends DownloadFromUrlInstaller.Installable> readInstallables(final String metadataId)
                throws IOException {
            final JSONObject data = new DownloadService.Downloadable(metadataId).getData();
            if (data == null) {
                return Collections.emptyList();
            }

            final DownloadFromUrlInstaller.InstallableList installables =
                    (DownloadFromUrlInstaller.InstallableList) JSONObject.toBean(
                            data,
                            DownloadFromUrlInstaller.InstallableList.class
                    );
            if (installables == null || installables.list == null) {
                return Collections.emptyList();
            }

            return Arrays.asList(installables.list);
        }
    }
}
