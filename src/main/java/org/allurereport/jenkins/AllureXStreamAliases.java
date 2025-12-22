package org.allurereport.jenkins;


import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Items;
import hudson.model.Run;
import jenkins.model.Jenkins;

import org.allurereport.jenkins.callables.AbstractAddInfo;
import org.allurereport.jenkins.callables.AddExecutorInfo;
import org.allurereport.jenkins.callables.AddTestRunInfo;
import org.allurereport.jenkins.callables.AllureReportArchive;
import org.allurereport.jenkins.callables.FindByGlob;

import org.allurereport.jenkins.config.AllureReportConfig;
import org.allurereport.jenkins.config.PropertyConfig;
import org.allurereport.jenkins.config.ReportBuildPolicy;
import org.allurereport.jenkins.config.ReportBuildPolicyDecision;
import org.allurereport.jenkins.config.ResultPolicy;
import org.allurereport.jenkins.config.ResultsConfig;

import org.allurereport.jenkins.dsl.AllurePluginJobDslExtension;
import org.allurereport.jenkins.dsl.AllureReportPublisherContext;

import org.allurereport.jenkins.exception.AllurePluginException;

import org.allurereport.jenkins.tools.AllureCommandlineInstallation;
import org.allurereport.jenkins.tools.AllureCommandlineInstaller;

import org.allurereport.jenkins.utils.BuildSummary;
import org.allurereport.jenkins.utils.BuildUtils;
import org.allurereport.jenkins.utils.ChartUtils;
import org.allurereport.jenkins.utils.FilePathUtils;
import org.allurereport.jenkins.utils.TrueZipArchiver;
import org.allurereport.jenkins.utils.ZipUtils;

/**
 * Registers XStream compatibility aliases so that
 * old configs with "ru.yandex.qatools.allure.jenkins.*"
 * continue to work after renaming packages to "org.allurereport.jenkins.*".
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
