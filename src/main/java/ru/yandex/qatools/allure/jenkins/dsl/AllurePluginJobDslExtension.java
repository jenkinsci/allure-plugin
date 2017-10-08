package ru.yandex.qatools.allure.jenkins.dsl;

import hudson.Extension;
import javaposse.jobdsl.dsl.helpers.publisher.PublisherContext;
import javaposse.jobdsl.plugin.ContextExtensionPoint;
import javaposse.jobdsl.plugin.DslExtensionMethod;
import ru.yandex.qatools.allure.jenkins.AllureReportPublisher;
import ru.yandex.qatools.allure.jenkins.config.ResultsConfig;

import java.util.List;

/**
 * @author Marat Mavlutov <{@literal mavlyutov@yandex-team.ru}>
 */
@Extension(optional = true)
public class AllurePluginJobDslExtension extends ContextExtensionPoint {

    @DslExtensionMethod(context = PublisherContext.class)
    public Object allure(List<String> paths, boolean disabled) {
        return new AllureReportPublisher(ResultsConfig.convertPaths(paths), disabled);
    }

    @DslExtensionMethod(context = PublisherContext.class)
    public Object allure(List<String> paths, boolean disabled, Runnable closure) {

        final AllureReportPublisherContext context = new AllureReportPublisherContext(
                new AllureReportPublisher(ResultsConfig.convertPaths(paths), disabled));
        executeInContext(closure, context);

        return context.getPublisher();
    }
}
