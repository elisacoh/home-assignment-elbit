import docker
from flask import Flask, jsonify
import os
from prometheus_client import Counter, make_wsgi_app
from werkzeug.middleware.dispatcher import DispatcherMiddleware


app = Flask(__name__)

REQUEST_COUNT = Counter(
    'http_requests_total',
    'Total HTTP requests',
    ['endpoint']
)

"""
talks to the local docker engine and gets the list of running containers
"""
@app.route("/containers")
def get_running_containers():
    REQUEST_COUNT.labels(endpoint='/containers').inc()
    try:
        client = docker.DockerClient(base_url=os.environ.get('DOCKER_HOST', 'unix:///var/run/docker.sock'))
        containers = client.containers.list()
        return [{"name": c.name, "status": c.status, "id": c.short_id} for c in containers]

    except docker.errors.DockerException as err:
        return {"error": str(err)}, 500

@app.route("/health")
def health():
    REQUEST_COUNT.labels(endpoint='/health').inc()
    return jsonify({"status": "ok"}), 200


app.wsgi_app = DispatcherMiddleware(app.wsgi_app, {
    '/metrics': make_wsgi_app()
})

if __name__ == "__main__":
    app.run()