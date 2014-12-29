# Allure Jenkins Plugin

[![build](https://img.shields.io/teamcity/http/teamcity.qatools.ru/s/allure_jenkins_plugin_master_build.svg?style=flat)](http://teamcity.qatools.ru/viewType.html?buildTypeId=allure_jenkins_plugin_master_build&guest=1)

This plugin allows to automatically generate [Allure report](http://allure.qatools.ru) **from existing XML files** and attach it to build during Jenkins job run.

![image](https://raw.github.com/allure-framework/allure-core/master/allure-dashboard.png)

## Installation
 1. Open **Manage Jenkins > Manage Plugins > Available**
 2. Find **Allure Report** plugin
 3. Install this plugin

## Configuration
 1. Open job configuration page
 2. Ensure that [Allure XML files](https://github.com/allure-framework/allure-core/wiki#gathering-information-about-tests) are generated during the build
 3. Add **Allure Report Generation** post build action

![configuration](https://raw.githubusercontent.com/allure-framework/allure-jenkins-plugin/master/img/allure-configuration.png)

## Usage
When build is finished a link to Allure report will appear on the build page:

![configuration](https://raw.githubusercontent.com/allure-framework/allure-jenkins-plugin/master/img/allure-sidebar.png)
![configuration](https://raw.githubusercontent.com/allure-framework/allure-jenkins-plugin/master/img/allure-report.png)

## Default Settings
 1. Open Jenkins global configuration
 2. Find **Allure Settings** configuration block
 
![configuration](https://raw.githubusercontent.com/allure-framework/allure-jenkins-plugin/master/img/allure-settings.png)


## Development
To build the plugin you need to have any JDK 1.7+ and [Apache Maven](http://maven.apache.org/).
 1. Setup Jenkins plugin development environment: https://wiki.jenkins-ci.org/display/JENKINS/Plugin+tutorial
 2. Execute the following command: `$ mvn clean package`

## Contact us
Mailing list: [allure@yandex-team.ru](mailto:allure@yandex-team.ru)
