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
                    docker rm -f $FLASK_CONTAINER $NGINX_CONTAINER 2>/dev/null || true
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
        stage('Run Flask') {
            steps {
                echo "Start flask container (not exposed to host)"
                sh '''
                    set -eux
                    docker run -d \
                        --name $FLASK_CONTAINER \
                        --network $NETWORK \
                        --group-add $(stat -c '%g' /var/run/docker.sock) \
                        -v /var/run/docker.sock:/var/run/docker.sock \
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
                        -p 127.0.0.1:$NGINX_PORT:80 \
                        $NGINX_IMAGE
                '''
            }
        }
        stage('Verify') {
            steps {
                echo "Send request and verify response is 200"
                sh '''
                    set -eux
                    sleep 3
                    DOCKER_HOST_IP=$(ip route | awk '/default/ { print $3 }')
                    curl -f http://$DOCKER_HOST_IP:8989/health
                    curl -f http://$DOCKER_HOST_IP:8989/containers
                '''
            }
        }
    }
    post {
        always {
            echo "Cleanup containers and network"
            sh '''
                docker rm -f $FLASK_CONTAINER $NGINX_CONTAINER 2>/dev/null || true
                docker network rm $NETWORK 2>/dev/null || true
            '''
        }
    }
}