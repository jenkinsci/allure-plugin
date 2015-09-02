freeStyleJob('allure') {

    publishers {
        allure('target/allure-results') {
            buildFor('UNSTABLE')
            includeProperties(true)
        }
    }
}