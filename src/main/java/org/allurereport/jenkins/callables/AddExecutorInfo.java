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
package org.allurereport.jenkins.callables;

import java.util.HashMap;
import java.util.Map;

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

    private final String reportName;

    public AddExecutorInfo(final String url,
                           final String buildName,
                           final String buildUrl,
                           final String reportUrl,
                           final String buildId,
                           final String reportName) {
        this.url = url;
        this.buildName = buildName;
        this.buildUrl = buildUrl;
        this.reportUrl = reportUrl;
        this.buildId = buildId;
        this.reportName = reportName;
    }

    @Override
    protected Object getData() {
        final HashMap<String, Object> data = new HashMap<>();
        data.put("name", "Jenkins");
        data.put("type", "jenkins");
        data.put("buildOrder", buildId);
        data.put("buildName", buildName);
        putIfNotBlank(data, "url", url);
        putIfNotBlank(data, "buildUrl", buildUrl);
        putIfNotBlank(data, "reportUrl", reportUrl);
        data.put("reportName", reportName);
        return data;
    }

    private static void putIfNotBlank(final Map<String, Object> data,
                                      final String key,
                                      final String value) {
        if (value != null && !value.isEmpty()) {
            data.put(key, value);
        }
    }

    @Override
    protected String getFileName() {
        return EXECUTOR_JSON;
    }
}
