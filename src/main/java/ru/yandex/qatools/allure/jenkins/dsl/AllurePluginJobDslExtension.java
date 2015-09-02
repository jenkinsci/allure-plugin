package ru.yandex.qatools.allure.jenkins.dsl;

import hudson.Extension;
import javaposse.jobdsl.dsl.helpers.publisher.PublisherContext;
import javaposse.jobdsl.plugin.ContextExtensionPoint;
import javaposse.jobdsl.plugin.DslExtensionMethod;
import ru.yandex.qatools.allure.jenkins.AllureReportPublisher;
import ru.yandex.qatools.allure.jenkins.config.AllureReportConfig;

/**
 * @author Marat Mavlutov <mavlyutov@yandex-team.ru>
 */
@Extension(optional = true)
@SuppressWarnings("unused")
public class AllurePluginJobDslExtension extends ContextExtensionPoint {

    @DslExtensionMethod(context = PublisherContext.class)
    public Object allure(String resultsPattern) {
        return new AllureReportPublisher(AllureReportConfig.newInstance(resultsPattern, true));
    }

    @DslExtensionMethod(context = PublisherContext.class)
    public Object allure(String resultsPattern, Runnable closure) {

        AllureReportPublisherContext context = new AllureReportPublisherContext(resultsPattern);
        executeInContext(closure, context);

        return new AllureReportPublisher(context.getConfig());
    }
}
