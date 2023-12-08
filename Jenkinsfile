pipeline {
    agent any
    tools {
        maven 'maven-3.8.1'
        jdk 'java8'
    }
    options {
        buildDiscarder(logRotator(artifactNumToKeepStr: '5'))
    }
    triggers {
        GenericTrigger (
            causeString: 'Triggered by develop',
            genericVariables: [[key: 'ref', value: '$.ref']],
            printContributedVariables: true,
            printPostContent: true,
            token: 'nkmotmaster'
        )
    }
    stages {
        stage ('Build') {
            steps {
                sh 'mvn clean package'
            }
            post {
                success {
                    archiveArtifacts artifacts: 'target/Nukkit-*.jar', fingerprint: true
                }
            }
        }

        /* stage ('Deploy') {
            when {
                branch "master"
            }
            steps {
                sh 'mvn javadoc:javadoc javadoc:jar source:jar deploy -DskipTests'
                step([$class: 'JavadocArchiver',
                        javadocDir: 'target/site/apidocs',
                        keepAll: false])
            }
        } */
    }

    post {
        always {
            deleteDir()
        }
    }
}
