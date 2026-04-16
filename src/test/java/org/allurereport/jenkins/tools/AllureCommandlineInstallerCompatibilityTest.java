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

import hudson.model.DownloadService;
import hudson.tools.InstallSourceProperty;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.assertj.core.api.Assertions.assertThat;

public class AllureCommandlineInstallerCompatibilityTest {

    private static final String LEGACY_VERSION_ID = "2.35.0";
    private static final String EMPTY_HOME = "<home/>";
    private static final String OPEN_PROPERTIES = "<properties>";
    private static final String CLOSE_PROPERTIES = "</properties>";
    private static final String OPEN_INSTALL_SOURCE = "<hudson.tools.InstallSourceProperty>";
    private static final String CLOSE_INSTALL_SOURCE = "</hudson.tools.InstallSourceProperty>";
    private static final String OPEN_INSTALLERS = "<installers>";
    private static final String CLOSE_INSTALLERS = "</installers>";
    private static final String OPEN_ID = "<id>";
    private static final String CLOSE_ID = "</id>";
    private static final String OPEN_TAG = "<";
    private static final String CLOSE_TAG = ">";
    private static final String OPEN_CLOSE_TAG = "</";
    private static final String SAMPLE_URL = "https://example.com/allure-commandline.zip";
    private static final String LIST_FIELD = "list";
    private static final String ID_FIELD = "id";
    private static final String NAME_FIELD = "name";
    private static final String URL_FIELD = "url";

    @Rule
    public JenkinsRule jRule = new JenkinsRule();

    @Test
    public void currentDescriptorShouldKeepCurrentDownloadServiceId() {
        final AllureCommandlineInstaller.DescriptorImpl descriptor = descriptor();

        assertThat(descriptor.getId()).isEqualTo(AllureCommandlineInstaller.CURRENT_DESCRIPTOR_ID);
    }

    @Test
    public void legacyDownloadableShouldBeRegistered() {
        assertThat(DownloadService.Downloadable.get(AllureCommandlineInstaller.LEGACY_DESCRIPTOR_ID))
                .isInstanceOf(LegacyAllureCommandlineInstallerDownloadable.class);
    }

    @Test
    public void shouldDeserializeLegacyInstallerConfiguration() {
        final Object deserialized = Jenkins.XSTREAM2.fromXML(
                installationXml(
                        "ru.yandex.qatools.allure.jenkins.tools.AllureCommandlineInstallation",
                        "Legacy",
                        "ru.yandex.qatools.allure.jenkins.tools.AllureCommandlineInstaller"
                )
        );

        assertThat(deserialized).isInstanceOf(AllureCommandlineInstallation.class);

        final AllureCommandlineInstallation installation = (AllureCommandlineInstallation) deserialized;
        final InstallSourceProperty installSource = installation.getProperties().get(InstallSourceProperty.class);

        assertThat(installSource).isNotNull();
        assertThat(installSource.installers).hasSize(1);
        assertThat(installSource.installers.get(0)).isInstanceOf(AllureCommandlineInstaller.class);

        final AllureCommandlineInstaller installer = (AllureCommandlineInstaller) installSource.installers.get(0);
        assertThat(installer.id).isEqualTo(LEGACY_VERSION_ID);
        assertThat(installer.getDescriptor().getId()).isEqualTo(AllureCommandlineInstaller.CURRENT_DESCRIPTOR_ID);
    }

    @Test
    public void shouldDeserializeCurrentInstallerConfiguration() {
        final Object deserialized = Jenkins.XSTREAM2.fromXML(
                installationXml(
                        "org.allurereport.jenkins.tools.AllureCommandlineInstallation",
                        "Current",
                        "org.allurereport.jenkins.tools.AllureCommandlineInstaller"
                )
        );

        assertThat(deserialized).isInstanceOf(AllureCommandlineInstallation.class);

        final AllureCommandlineInstallation installation = (AllureCommandlineInstallation) deserialized;
        final InstallSourceProperty installSource = installation.getProperties().get(InstallSourceProperty.class);

        assertThat(installSource).isNotNull();
        assertThat(installSource.installers).hasSize(1);
        assertThat(installSource.installers.get(0)).isInstanceOf(AllureCommandlineInstaller.class);

        final AllureCommandlineInstaller installer = (AllureCommandlineInstaller) installSource.installers.get(0);
        assertThat(installer.id).isEqualTo(LEGACY_VERSION_ID);
        assertThat(installer.getDescriptor().getId()).isEqualTo(AllureCommandlineInstaller.CURRENT_DESCRIPTOR_ID);
    }

    @Test
    public void shouldReadLegacyInstallerMetadata() throws Exception {
        writeMetadata(AllureCommandlineInstaller.LEGACY_DESCRIPTOR_ID, LEGACY_VERSION_ID);

        assertThat(descriptor().getInstallables())
                .extracting(installable -> installable.id)
                .containsExactly(LEGACY_VERSION_ID);
    }

    @Test
    public void shouldFallbackToCurrentInstallerMetadata() throws Exception {
        writeMetadata(AllureCommandlineInstaller.CURRENT_DESCRIPTOR_ID, LEGACY_VERSION_ID);

        assertThat(descriptor().getInstallables())
                .extracting(installable -> installable.id)
                .containsExactly(LEGACY_VERSION_ID);
    }

    private AllureCommandlineInstaller.DescriptorImpl descriptor() {
        return jRule.jenkins.getDescriptorByType(AllureCommandlineInstaller.DescriptorImpl.class);
    }

    private void writeMetadata(final String metadataId, final String versionId) throws Exception {
        final JSONObject installable = new JSONObject();
        installable.put(ID_FIELD, versionId);
        installable.put(NAME_FIELD, versionId);
        installable.put(URL_FIELD, SAMPLE_URL);

        final JSONArray installables = new JSONArray();
        installables.add(installable);

        final JSONObject metadata = new JSONObject();
        metadata.put(LIST_FIELD, installables);

        new DownloadService.Downloadable(metadataId).getDataFile().write(metadata.toString());
    }

    private String installationXml(final String installationTag,
                                   final String installationName,
                                   final String installerTag) {
        return OPEN_TAG + installationTag + CLOSE_TAG
                + "<name>" + installationName + "</name>"
                + EMPTY_HOME
                + OPEN_PROPERTIES
                + OPEN_INSTALL_SOURCE
                + OPEN_INSTALLERS
                + OPEN_TAG + installerTag + CLOSE_TAG
                + OPEN_ID + LEGACY_VERSION_ID + CLOSE_ID
                + OPEN_CLOSE_TAG + installerTag + CLOSE_TAG
                + CLOSE_INSTALLERS
                + CLOSE_INSTALL_SOURCE
                + CLOSE_PROPERTIES
                + OPEN_CLOSE_TAG + installationTag + CLOSE_TAG;
    }
}
