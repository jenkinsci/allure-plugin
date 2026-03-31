freeStyleJob('allure') {

    publishers {
        allure(['target/first-results', 'target/second-results']) {
            buildFor('UNSTABLE')
            property('key', 'value')
            includeProperties(true)
            resultPolicy('FAILURE_IF_FAILED_OR_BROKEN')
            unstableThresholdPercent(50)
            failureThresholdCount(2)
            reportName('Team Allure')
        }
    }
}
