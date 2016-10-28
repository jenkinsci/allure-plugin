package ru.yandex.qatools.allure.jenkins.dsl;

import hudson.Extension;
import javaposse.jobdsl.dsl.helpers.publisher.PublisherContext;
import javaposse.jobdsl.plugin.ContextExtensionPoint;
import javaposse.jobdsl.plugin.DslExtensionMethod;
import ru.yandex.qatools.allure.jenkins.AllureReportDefaultUploader;
import ru.yandex.qatools.allure.jenkins.AllureReportPublisher;
import ru.yandex.qatools.allure.jenkins.config.AllureReportConfig;

import java.util.List;

/**
 * @author Marat Mavlutov <mavlyutov@yandex-team.ru>
 */
@Extension(optional = true)
@SuppressWarnings("unused")
public class AllurePluginJobDslExtension extends ContextExtensionPoint {

    @DslExtensionMethod(context = PublisherContext.class)
    public Object allure(List paths) {
        return new AllureReportPublisher(AllureReportConfig.newInstance(paths), new AllureReportDefaultUploader());
    }

    @DslExtensionMethod(context = PublisherContext.class)
    public Object allure(List paths, Runnable closure) {

        AllureReportPublisherContext context = new AllureReportPublisherContext(AllureReportConfig.newInstance(paths), new AllureReportDefaultUploader());
        executeInContext(closure, context);

        return new AllureReportPublisher(context.getConfig(), context.getUploader());
    }
}
