freeStyleJob('allure') {

    publishers {
        allure(['target/first-results', 'target/second-results']) {
            buildFor('UNSTABLE')
            property('key', 'value')
            includeProperties(true)
        }
    }
}