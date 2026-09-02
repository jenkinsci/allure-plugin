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

import hudson.tools.InstallSourceProperty;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolProperty;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlSelect;
import org.htmlunit.html.HtmlTextInput;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AllureManagedInstallerUiTest {

    private static final String TOOL_NAME = "Allure";
    private static final String CONFIGURE_TOOLS = "configureTools";
    private static final String CONFIG_FORM = "config";
    private static final String FIXED_VERSION = "3.15.1";
    private static final String ALLURE_2_BASE_URL = "https://maven.example.test/repository";
    private static final String NODE_DOWNLOAD_BASE_URL = "https://node.example.test/dist";
    private static final String NPM_REGISTRY = "https://npm.example.test";
    private static final String LEGACY_VERSION = "2.35.1";
    private static final String LEGACY_BASE_URL = "https://legacy.example.test/repository";
    private static final String LEGACY_PATH_NAME = "Existing Allure 3 PATH";
    private static final String VERSION_SUFFIX = ")";
    private static final String RECOMMENDED_ALLURE_2_LABEL_PREFIX = "Recommended Allure 2 (";
    private static final String RECOMMENDATION_STATUS_PREFIX = "This plugin recommends Allure ";
    private static final String STYLE_ATTRIBUTE = "style";
    private static final String HIDDEN_DISPLAY = "display: none";
    private static final String VERSION_POLICY_DESCRIPTION =
            "Recommended Allure versions are tested and updated with this Jenkins plugin. "
                    + "For Allure 3, the installer also manages a compatible Node.js runtime.";

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void toolsPageUsesUnifiedAllureNames() throws Exception {
        configure(new AllureManagedInstaller(AllureManagedInstaller.VERSION_POLICY_RECOMMENDED));

        final HtmlPage page = jenkins.createWebClient().goTo(CONFIGURE_TOOLS);

        assertThat(descriptor().getDisplayName()).isEqualTo(TOOL_NAME);
        assertThat(jenkins.jenkins.getDescriptorByType(AllureManagedInstaller.DescriptorImpl.class).getDisplayName())
                .isEqualTo("Install Allure");
        assertThat(page.asNormalizedText())
                .contains("Allure installations")
                .doesNotContain("Allure Commandline");
    }

    @Test
    public void toolsPageShowsRecommendedAllureChoicesWithoutVersionedHelpText() throws Exception {
        configure(new AllureManagedInstaller(AllureManagedInstaller.VERSION_POLICY_RECOMMENDED));

        final HtmlPage page = jenkins.createWebClient().goTo(CONFIGURE_TOOLS);

        assertThat(page.asXml())
                .contains("Recommended Allure 3 ("
                        + AllureRuntimeManifest.RECOMMENDED_ALLURE_VERSION + VERSION_SUFFIX)
                .contains(RECOMMENDED_ALLURE_2_LABEL_PREFIX
                        + AllureRuntimeManifest.RECOMMENDED_ALLURE_2_VERSION + VERSION_SUFFIX)
                .contains(VERSION_POLICY_DESCRIPTION);
        assertThat(page.asXml())
                .doesNotContain("Currently Allure ")
                .doesNotContain(RECOMMENDATION_STATUS_PREFIX)
                .doesNotContain("Allure 3 (from PATH) installations");
    }

    @Test
    public void recommendedAllure2PolicySurvivesToolsPageRoundtrip() throws Exception {
        configure(new AllureManagedInstaller(AllureManagedInstaller.VERSION_POLICY_RECOMMENDED_ALLURE_2));

        final HtmlPage page = jenkins.createWebClient().goTo(CONFIGURE_TOOLS);
        assertThat(page.asXml())
                .contains(RECOMMENDED_ALLURE_2_LABEL_PREFIX
                        + AllureRuntimeManifest.RECOMMENDED_ALLURE_2_VERSION + VERSION_SUFFIX)
                .doesNotContain(RECOMMENDATION_STATUS_PREFIX);
        jenkins.submit(page.getFormByName(CONFIG_FORM));

        final AllureManagedInstaller saved = configuredInstaller();
        assertThat(saved.getVersionPolicy())
                .isEqualTo(AllureManagedInstaller.VERSION_POLICY_RECOMMENDED_ALLURE_2);
        assertThat(saved.getVersion()).isNull();
        assertThat(saved.resolveVersion()).isEqualTo(AllureRuntimeManifest.RECOMMENDED_ALLURE_2_VERSION);
    }

    @Test
    public void fixedVersionFieldOnlyAppearsForFixedPolicy() throws Exception {
        configure(new AllureManagedInstaller(AllureManagedInstaller.VERSION_POLICY_RECOMMENDED));

        final HtmlPage page = jenkins.createWebClient().goTo(CONFIGURE_TOOLS);
        final HtmlForm form = page.getFormByName(CONFIG_FORM);
        final HtmlSelect policy = form.getSelectByName("versionPolicy");
        final HtmlTextInput fixedVersion = form.getInputByName("_.version");
        final HtmlElement fixedVersionRow = fixedVersion.getFirstByXPath(
                "ancestor::div[contains(concat(' ', normalize-space(@class), ' '), "
                        + "' jenkins-form-item ')][1]"
        );

        assertThat(fixedVersionRow.getAttribute(STYLE_ATTRIBUTE)).contains(HIDDEN_DISPLAY);

        policy.setSelectedAttribute(AllureManagedInstaller.VERSION_POLICY_RECOMMENDED_ALLURE_2, true);
        assertThat(fixedVersionRow.getAttribute(STYLE_ATTRIBUTE)).contains(HIDDEN_DISPLAY);

        policy.setSelectedAttribute(AllureManagedInstaller.VERSION_POLICY_FIXED, true);
        assertThat(fixedVersionRow.getAttribute(STYLE_ATTRIBUTE)).doesNotContain(HIDDEN_DISPLAY);

        policy.setSelectedAttribute(AllureManagedInstaller.VERSION_POLICY_RECOMMENDED, true);
        assertThat(fixedVersionRow.getAttribute(STYLE_ATTRIBUTE)).contains(HIDDEN_DISPLAY);
    }

    @Test
    public void fixedPolicyAndMirrorsSurviveToolsPageRoundtrip() throws Exception {
        final AllureManagedInstaller installer = new AllureManagedInstaller(
                AllureManagedInstaller.VERSION_POLICY_FIXED
        );
        installer.setVersion(FIXED_VERSION);
        installer.setAllure2BaseUrl(ALLURE_2_BASE_URL);
        installer.setNodeDownloadBaseUrl(NODE_DOWNLOAD_BASE_URL);
        installer.setNpmRegistry(NPM_REGISTRY);
        configure(installer);

        final HtmlPage page = jenkins.createWebClient().goTo(CONFIGURE_TOOLS);
        final HtmlForm form = page.getFormByName(CONFIG_FORM);
        jenkins.submit(form);

        final AllureManagedInstaller saved = configuredInstaller();
        assertThat(saved.getVersionPolicy()).isEqualTo(AllureManagedInstaller.VERSION_POLICY_FIXED);
        assertThat(saved.getVersion()).isEqualTo(FIXED_VERSION);
        assertThat(saved.getAllure2BaseUrl()).isEqualTo(ALLURE_2_BASE_URL);
        assertThat(saved.getNodeDownloadBaseUrl()).isEqualTo(NODE_DOWNLOAD_BASE_URL);
        assertThat(saved.getNpmRegistry()).isEqualTo(NPM_REGISTRY);
    }

    @Test
    public void legacyDirectInstallerSurvivesToolsPageRoundtrip() throws Exception {
        final AllureCommandlineDirectInstaller installer = new AllureCommandlineDirectInstaller(LEGACY_VERSION);
        installer.setBaseUrl(LEGACY_BASE_URL);
        configure(installer);

        final HtmlPage page = jenkins.createWebClient().goTo(CONFIGURE_TOOLS);
        jenkins.submit(page.getFormByName(CONFIG_FORM));

        final ToolInstaller saved = configuredToolInstaller();
        assertThat(saved).isInstanceOf(AllureCommandlineDirectInstaller.class);
        final AllureCommandlineDirectInstaller direct = (AllureCommandlineDirectInstaller) saved;
        assertThat(direct.getVersion()).isEqualTo(LEGACY_VERSION);
        assertThat(direct.getBaseUrl()).isEqualTo(LEGACY_BASE_URL);
    }

    @Test
    public void hiddenLegacyPathDescriptorSurvivesToolsPageRoundtrip() throws Exception {
        final Allure3Installation.DescriptorImpl legacyDescriptor =
                jenkins.jenkins.getDescriptorByType(Allure3Installation.DescriptorImpl.class);
        legacyDescriptor.setInstallations(new Allure3Installation(
                LEGACY_PATH_NAME,
                "",
                Collections.emptyList()
        ));

        final HtmlPage page = jenkins.createWebClient().goTo(CONFIGURE_TOOLS);
        assertThat(page.asNormalizedText()).doesNotContain(LEGACY_PATH_NAME);
        jenkins.submit(page.getFormByName(CONFIG_FORM));

        assertThat(legacyDescriptor.getInstallations())
                .extracting(Allure3Installation::getName)
                .containsExactly(LEGACY_PATH_NAME);
    }

    private void configure(final ToolInstaller installer) throws Exception {
        final InstallSourceProperty source = new InstallSourceProperty(Collections.singletonList(installer));
        final List<ToolProperty<?>> properties = Collections.singletonList(source);
        descriptor().setInstallations(new AllureCommandlineInstallation(TOOL_NAME, "", properties));
    }

    private AllureManagedInstaller configuredInstaller() {
        return (AllureManagedInstaller) configuredToolInstaller();
    }

    private ToolInstaller configuredToolInstaller() {
        final AllureCommandlineInstallation installation = descriptor().getInstallations()[0];
        final InstallSourceProperty source = installation.getProperties().get(InstallSourceProperty.class);
        return source.installers.get(0);
    }

    private AllureCommandlineInstallation.DescriptorImpl descriptor() {
        return jenkins.jenkins.getDescriptorByType(AllureCommandlineInstallation.DescriptorImpl.class);
    }
}
