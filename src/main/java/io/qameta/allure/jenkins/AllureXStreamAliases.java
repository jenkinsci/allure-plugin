package io.qameta.allure.jenkins;


import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Items;
import hudson.model.Run;
import jenkins.model.Jenkins;

import io.qameta.allure.jenkins.callables.AbstractAddInfo;
import io.qameta.allure.jenkins.callables.AddExecutorInfo;
import io.qameta.allure.jenkins.callables.AddTestRunInfo;
import io.qameta.allure.jenkins.callables.AllureReportArchive;
import io.qameta.allure.jenkins.callables.FindByGlob;

import io.qameta.allure.jenkins.config.AllureReportConfig;
import io.qameta.allure.jenkins.config.PropertyConfig;
import io.qameta.allure.jenkins.config.ReportBuildPolicy;
import io.qameta.allure.jenkins.config.ReportBuildPolicyDecision;
import io.qameta.allure.jenkins.config.ResultPolicy;
import io.qameta.allure.jenkins.config.ResultsConfig;

import io.qameta.allure.jenkins.dsl.AllurePluginJobDslExtension;
import io.qameta.allure.jenkins.dsl.AllureReportPublisherContext;

import io.qameta.allure.jenkins.exception.AllurePluginException;

import io.qameta.allure.jenkins.tools.AllureCommandlineInstallation;
import io.qameta.allure.jenkins.tools.AllureCommandlineInstaller;

import io.qameta.allure.jenkins.utils.BuildSummary;
import io.qameta.allure.jenkins.utils.BuildUtils;
import io.qameta.allure.jenkins.utils.ChartUtils;
import io.qameta.allure.jenkins.utils.FilePathUtils;
import io.qameta.allure.jenkins.utils.TrueZipArchiver;
import io.qameta.allure.jenkins.utils.ZipUtils;

/**
 * Registers XStream compatibility aliases so that
 * old configs with "ru.yandex.qatools.allure.jenkins.*"
 * continue to work after renaming packages to "io.qameta.allure.jenkins.*".
 */
@SuppressWarnings("PMD.CouplingBetweenObjects")
public final class AllureXStreamAliases {

    private static final Logger LOGGER =
        Logger.getLogger(AllureXStreamAliases.class.getName());

    private AllureXStreamAliases() {
    }

    private static void alias(final String oldClassName, final Class<?> type) {
        Items.XSTREAM2.addCompatibilityAlias(oldClassName, type);
        Jenkins.XSTREAM2.addCompatibilityAlias(oldClassName, type);
        Run.XSTREAM2.addCompatibilityAlias(oldClassName, type);
    }

    @Initializer(after = InitMilestone.PLUGINS_STARTED)
    public static void registerAliases() {
        LOGGER.log(Level.INFO, "Registering Allure XStream compatibility aliases");
        registerCallableAliases();
        registerConfigAliases();
        registerDslAliases();
        registerExceptionAliases();
        registerToolAliases();
        registerUtilsAliases();
        registerMainPluginClassAliases();
    }

    private static void registerCallableAliases() {
        alias("ru.yandex.qatools.allure.jenkins.callables.AbstractAddInfo",
            AbstractAddInfo.class);
        alias("ru.yandex.qatools.allure.jenkins.callables.AddExecutorInfo",
            AddExecutorInfo.class);
        alias("ru.yandex.qatools.allure.jenkins.callables.AddTestRunInfo",
            AddTestRunInfo.class);
        alias("ru.yandex.qatools.allure.jenkins.callables.AllureReportArchive",
            AllureReportArchive.class);
        alias("ru.yandex.qatools.allure.jenkins.callables.FindByGlob",
            FindByGlob.class);
    }

    private static void registerConfigAliases() {
        alias("ru.yandex.qatools.allure.jenkins.config.AllureReportConfig",
            AllureReportConfig.class);
        alias("ru.yandex.qatools.allure.jenkins.config.PropertyConfig",
            PropertyConfig.class);
        alias("ru.yandex.qatools.allure.jenkins.config.ReportBuildPolicy",
            ReportBuildPolicy.class);
        alias("ru.yandex.qatools.allure.jenkins.config.ReportBuildPolicyDecision",
            ReportBuildPolicyDecision.class);
        alias("ru.yandex.qatools.allure.jenkins.config.ResultPolicy",
            ResultPolicy.class);
        alias("ru.yandex.qatools.allure.jenkins.config.ResultsConfig",
            ResultsConfig.class);
    }

    private static void registerDslAliases() {
        alias("ru.yandex.qatools.allure.jenkins.dsl.AllurePluginJobDslExtension",
            AllurePluginJobDslExtension.class);
        alias("ru.yandex.qatools.allure.jenkins.dsl.AllureReportPublisherContext",
            AllureReportPublisherContext.class);
    }

    private static void registerExceptionAliases() {
        alias("ru.yandex.qatools.allure.jenkins.exception.AllurePluginException",
            AllurePluginException.class);
    }

    private static void registerToolAliases() {
        alias("ru.yandex.qatools.allure.jenkins.tools.AllureCommandlineInstallation",
            AllureCommandlineInstallation.class);
        alias("ru.yandex.qatools.allure.jenkins.tools.AllureCommandlineInstaller",
            AllureCommandlineInstaller.class);
    }

    private static void registerUtilsAliases() {
        alias("ru.yandex.qatools.allure.jenkins.utils.BuildSummary",
            BuildSummary.class);
        alias("ru.yandex.qatools.allure.jenkins.utils.BuildUtils",
            BuildUtils.class);
        alias("ru.yandex.qatools.allure.jenkins.utils.ChartUtils",
            ChartUtils.class);
        alias("ru.yandex.qatools.allure.jenkins.utils.FilePathUtils",
            FilePathUtils.class);
        alias("ru.yandex.qatools.allure.jenkins.utils.TrueZipArchiver",
            TrueZipArchiver.class);
        alias("ru.yandex.qatools.allure.jenkins.utils.ZipUtils",
            ZipUtils.class);
    }

    private static void registerMainPluginClassAliases() {
        alias("ru.yandex.qatools.allure.jenkins.AllureBuildAction",
            AllureBuildAction.class);
        alias("ru.yandex.qatools.allure.jenkins.AllureProjectAction",
            AllureProjectAction.class);
        alias("ru.yandex.qatools.allure.jenkins.AllureReportBuildAction",
            AllureReportBuildAction.class);
        alias("ru.yandex.qatools.allure.jenkins.AllureReportPlugin",
            AllureReportPlugin.class);
        alias("ru.yandex.qatools.allure.jenkins.AllureReportProjectAction",
            AllureReportProjectAction.class);
        alias("ru.yandex.qatools.allure.jenkins.AllureReportPublisher",
            AllureReportPublisher.class);
        alias("ru.yandex.qatools.allure.jenkins.AllureReportPublisherDescriptor",
            AllureReportPublisherDescriptor.class);
        alias("ru.yandex.qatools.allure.jenkins.ReportBuilder",
            ReportBuilder.class);
    }
}
