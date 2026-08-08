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
package org.allurereport.jenkins;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AllureReportPluginUrlTest {

    private static final String CLASSIC_URL = AllureReportPlugin.URL_PATH;
    private static final String DUAL_A2 = "allure2";
    private static final String DUAL_A3 = "allure3";
    private static final String CUSTOM = "custom-report";

    @Test
    public void defaultReportDirMapsToClassicAllureUrl() {
        assertThat(AllureReportPlugin.urlNameForReportDir(null)).isEqualTo(CLASSIC_URL);
        assertThat(AllureReportPlugin.urlNameForReportDir("")).isEqualTo(CLASSIC_URL);
        assertThat(AllureReportPlugin.urlNameForReportDir(AllureReportPlugin.REPORT_PATH))
                .isEqualTo(CLASSIC_URL);
        assertThat(AllureReportPlugin.urlNameForReportDir("tests/" + AllureReportPlugin.REPORT_PATH))
                .isEqualTo(CLASSIC_URL);
        assertThat(AllureReportPlugin.urlNameForReportDir(CLASSIC_URL)).isEqualTo(CLASSIC_URL);
    }

    @Test
    public void explicitReportDirIsUsedAsUrlForDualJobs() {
        assertThat(AllureReportPlugin.urlNameForReportDir(DUAL_A2)).isEqualTo(DUAL_A2);
        assertThat(AllureReportPlugin.urlNameForReportDir(DUAL_A3)).isEqualTo(DUAL_A3);
        assertThat(AllureReportPlugin.urlNameForReportDir(CUSTOM)).isEqualTo(CUSTOM);
    }
}
