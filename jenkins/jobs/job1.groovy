// job1:
// - agent pull code from github repo
// - build docker container and push it to docker hub

pipeline {
    agent {label 'docker'}

    environment {
        IMAGE = "elcosah/flaskapp-elbit"
    }

    stages {
        stage('INFO')
        {
            steps{
                script {
                    env.TAG = env.GIT_COMMIT.take(7)
                }
            sh 'echo "$GIT_COMMIT"'
            sh 'echo "TAG=$TAG"'
            sh 'echo "IMAGE=$IMAGE"'
            }
        }
        stage('Checkout'){
            steps{
                checkout scm
            }
        }

        stage('Build') {
            steps {
                echo "BUild Docker image"
                sh '''
                    set -eux
                    docker build -f Dockerfile.multistage -t "$IMAGE:$TAG" .
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
        always {
            echo "Done"
        }
    }

}
