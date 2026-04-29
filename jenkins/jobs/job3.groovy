// job3:
// - run flask + nginx containers
// - expose only nginx port on local jenkins machine
// - verify request returns 200
pipeline {
    agent any
    environment {
        FLASK_IMAGE  = "elcosah/flaskapp-elbit:latest"
        NGINX_IMAGE  = "elcosah/nginx-elbit:latest"
        NETWORK      = "elbit-net"
        FLASK_CONTAINER = "flask-app"
        NGINX_CONTAINER = "nginx-proxy"
        NGINX_PORT   = "8989"
    }
    stages {
        stage('INFO') {
            steps {
                sh 'echo "FLASK_IMAGE=$FLASK_IMAGE"'
                sh 'echo "NGINX_IMAGE=$NGINX_IMAGE"'
            }
        }
        stage('Cleanup') {
            steps {
                echo "Remove any existing containers and network"
                sh '''
                    set -eux
                    docker rm -f $FLASK_CONTAINER $NGINX_CONTAINER socket-proxy 2>/dev/null || true
                    docker network rm $NETWORK 2>/dev/null || true
                '''
            }
        }
        stage('Setup Network') {
            steps {
                echo "Create dedicated docker network"
                sh '''
                    set -eux
                    docker network create $NETWORK
                '''
            }
        }

        stage('Run Socket Proxy') {
            steps {
                sh '''
                    set -eux
                    docker run -d \
                        --name socket-proxy \
                        --network $NETWORK \
                        -e CONTAINERS=1 \
                        -v /var/run/docker.sock:/var/run/docker.sock:ro \
                        tecnativa/docker-socket-proxy
                '''
            }
        }
        stage('Run Flask') {
            steps {
                echo "Start flask container (not exposed to host)"
                sh '''
                    set -eux
                    docker run -d \
                        --name $FLASK_CONTAINER \
                        --network $NETWORK \
                        -e DOCKER_HOST=tcp://socket-proxy:2375 \
                        $FLASK_IMAGE
                '''
            }
        }
        stage('Run Nginx') {
            steps {
                echo "Start nginx container (only nginx port exposed)"
                sh '''
                    set -eux
                    docker run -d \
                        --name $NGINX_CONTAINER \
                        --network $NETWORK \
                        -p $NGINX_PORT:80 \
                        $NGINX_IMAGE
                '''
            }
        }
            stage('Verify') {
                steps {
                    echo "Send request and verify response is 200"
                    sh '''
                        set -eux
                        curl --retry 5 --retry-delay 2 --retry-connrefused -f http://localhost:8989/health
                        curl --retry 5 --retry-delay 2 --retry-connrefused -f http://localhost:8989/containers
                    '''
                }
            }
    }
    post {
        success {
            script {
                try {
                    slackSend(channel: '#ci-cd', color: 'good',
                        message: "SUCCESS: *${env.JOB_NAME}* #${env.BUILD_NUMBER} — Integration test passed\n${env.BUILD_URL}")
                } catch(e) { echo "Slack notification failed: ${e}" }
            }
        }
        failure {
            script {
                try {
                    slackSend(channel: '#ci-cd', color: 'danger',
                        message: "FAILURE: *${env.JOB_NAME}* #${env.BUILD_NUMBER} — Integration test FAILED\n${env.BUILD_URL}")
                } catch(e) { echo "Slack notification failed: ${e}" }
            }
        }
        always {
            echo "Cleanup containers and network"
            sh '''
                docker rm -f $FLASK_CONTAINER $NGINX_CONTAINER socket-proxy 2>/dev/null || true
                docker network rm $NETWORK 2>/dev/null || true
            '''
        }
    }
}