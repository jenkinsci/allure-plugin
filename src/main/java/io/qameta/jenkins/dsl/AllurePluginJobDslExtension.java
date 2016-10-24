package io.qameta.jenkins.dsl;

import hudson.Extension;
import io.qameta.jenkins.AllureReportPublisher;
import io.qameta.jenkins.config.AllureReportConfig;
import javaposse.jobdsl.dsl.helpers.publisher.PublisherContext;
import javaposse.jobdsl.plugin.ContextExtensionPoint;
import javaposse.jobdsl.plugin.DslExtensionMethod;

import java.util.List;

/**
 * @author Marat Mavlutov <mavlyutov@yandex-team.ru>
 */
@Extension(optional = true)
@SuppressWarnings("unused")
public class AllurePluginJobDslExtension extends ContextExtensionPoint {

    @DslExtensionMethod(context = PublisherContext.class)
    public Object allure(List<String> paths) {
        return new AllureReportPublisher(AllureReportConfig.newInstance(paths));
    }

    @DslExtensionMethod(context = PublisherContext.class)
    public Object allure(List<String> paths, Runnable closure) {

        AllureReportPublisherContext context = new AllureReportPublisherContext(paths);
        executeInContext(closure, context);

        return new AllureReportPublisher(context.getConfig());
    }
}
