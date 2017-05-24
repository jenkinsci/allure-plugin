pipeline {
    agent {
        label 'java'
    }
    stages {
        stage("Build") {
            steps {
                sh './gradlew build jpi'
            }
        }
        stage("Reports") {
            steps {
                junit 'build/test-results/test/*.xml'
            }
        }
        stage('Archive') {
            steps{
                archiveArtifacts 'build/libs/*.hpi'
            }
        }
    }
    post {
        always {
            deleteDir()
        }

        failure {
            slackSend message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} failed (<${env.BUILD_URL}|Open>)",
                    color: 'danger', teamDomain: 'qameta', channel: 'allure', tokenCredentialId: 'allure-channel'
        }
    }
}