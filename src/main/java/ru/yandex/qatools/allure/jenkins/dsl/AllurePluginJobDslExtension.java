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
package ru.yandex.qatools.allure.jenkins.dsl;

import hudson.Extension;
import ru.yandex.qatools.allure.jenkins.AllureReportPublisher;
import ru.yandex.qatools.allure.jenkins.config.ResultsConfig;

import java.util.List;
import javaposse.jobdsl.dsl.helpers.publisher.PublisherContext;
import javaposse.jobdsl.plugin.ContextExtensionPoint;
import javaposse.jobdsl.plugin.DslExtensionMethod;

/**
 * @author <a href="mailto:mavlyutov@yandex-team.ru">Marat Mavlutov</a>
 */
@Extension(optional = true)
public class AllurePluginJobDslExtension extends ContextExtensionPoint {

    @DslExtensionMethod(context = PublisherContext.class)
    public Object allure(final List<String> paths) {
        return new AllureReportPublisher(ResultsConfig.convertPaths(paths));
    }

    @DslExtensionMethod(context = PublisherContext.class)
    public Object allure(final List<String> paths, final Runnable closure) {

        final AllureReportPublisherContext context = new AllureReportPublisherContext(
                new AllureReportPublisher(ResultsConfig.convertPaths(paths)));
        executeInContext(closure, context);

        return context.getPublisher();
    }
}
