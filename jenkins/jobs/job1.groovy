// job1:
// - agent pull code from github repo
// - build docker container and push it to docker hub

pipeline {
    agent any

    environment {
        IMAGE = "elcosah/flaskapp-elbit"
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
                echo "BUild Docker image"
                sh '''
                    set -eux
                    docker build -f flask-app/Dockerfile.multistage -t "$IMAGE:$TAG" ./flask-app
                '''
            }
        }

        stage('Push'){
            when {
                expression { return (env.GIT_BRANCH ?: '') == 'origin/main' }
            }

            steps {
                echo "Push validated image to Docker Hub"
                withCredentials([usernamePassword(credentialsId: 'docker-creds', usernameVariable: 'DH_USER', passwordVariable: 'DH_PASS')]){
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
                    message: "SUCCESS *${env.JOB_NAME}* #${env.BUILD_NUMBER} succeeded — Flask image pushed\n${env.BUILD_URL}")
            } catch(e) { echo "Slack notification failed: ${e}" }
        }
        build job: 'job2-build-nginx'
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
