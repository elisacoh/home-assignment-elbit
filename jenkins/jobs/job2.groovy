// job2:
// - build custom nginx image and push to docker hub

pipeline {
    agent any
    environment {
        IMAGE = "elcosah/nginx-elbit"
    }
    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.TAG = env.GIT_COMMIT.take(7)
                }
            }
        }
        stage('INFO') {
            steps {
                sh 'echo "TAG=$TAG"'
                sh 'echo "IMAGE=$IMAGE"'
            }
        }
        stage('Build') {
            steps {
                echo "Build custom nginx image"
                sh '''
                    set -eux
                    docker build -t "$IMAGE:$TAG" ./nginx
                '''
            }
        }
        stage('Push') {
            when {
                expression { return (env.GIT_BRANCH ?: '') == 'origin/main' }
            }
            steps {
                echo "Push nginx image to Docker Hub"
                withCredentials([usernamePassword(credentialsId: 'docker-creds', usernameVariable: 'DH_USER', passwordVariable: 'DH_PASS')]) {
                    sh '''
                        set -eux
                        echo "$DH_PASS" | docker login -u "$DH_USER" --password-stdin
                        docker push "$IMAGE:$TAG"
                        docker tag "$IMAGE:$TAG" "$IMAGE:latest"
                        docker push "$IMAGE:latest"
                        docker logout
                    '''
                }
            }
        }
    }
    post {
        success {
            script {
                try {
                    slackSend(channel: '#ci-cd', color: 'good',
                        message: "SUCCESS: *${env.JOB_NAME}* #${env.BUILD_NUMBER} succeeded — Nginx image pushed\n${env.BUILD_URL}")
                } catch(e) { echo "Slack notification failed: ${e}" }
            }
            build job: 'job3-integration-test'
        }
        failure {
            script {
                try {
                    slackSend(channel: '#ci-cd', color: 'danger',
                        message: "FAILURE: *${env.JOB_NAME}* #${env.BUILD_NUMBER} failed\n${env.BUILD_URL}")
                } catch(e) { echo "Slack notification failed: ${e}" }
            }
        }
        always {
            echo "Done"
        }
    }
}