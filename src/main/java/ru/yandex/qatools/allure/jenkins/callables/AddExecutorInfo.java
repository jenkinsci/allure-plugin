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
package ru.yandex.qatools.allure.jenkins.callables;

import java.util.HashMap;

/**
 * @author charlie (Dmitry Baev).
 */
public class AddExecutorInfo extends AbstractAddInfo {

    private static final String EXECUTOR_JSON = "executor.json";

    private final String url;

    private final String buildName;

    private final String buildUrl;

    private final String reportUrl;

    private final String buildId;

    public AddExecutorInfo(final String url,
                           final String buildName,
                           final String buildUrl,
                           final String reportUrl,
                           final String buildId) {
        this.url = url;
        this.buildName = buildName;
        this.buildUrl = buildUrl;
        this.reportUrl = reportUrl;
        this.buildId = buildId;
    }

    @Override
    protected Object getData() {
        final HashMap<String, Object> data = new HashMap<>();
        data.put("name", "Jenkins");
        data.put("type", "jenkins");
        data.put("url", url);
        data.put("buildOrder", buildId);
        data.put("buildName", buildName);
        data.put("buildUrl", buildUrl);
        data.put("reportUrl", reportUrl);
        data.put("reportName", "AllureReport");
        return data;
    }

    @Override
    protected String getFileName() {
        return EXECUTOR_JSON;
    }
}
