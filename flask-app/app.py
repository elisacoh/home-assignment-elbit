import docker
from flask import Flask, jsonify


app = Flask(__name__)

"""
talks to the local docker engine and gets the list of running containers
"""
@app.route("/containers")
def get_running_containers():
    try:
        client = docker.from_env()
        containers = client.containers.list()
        return [{"name": c.name, "status": c.status, "id": c.short_id} for c in containers]
    
    except docker.errors.DockerException as err:
        return {"error": str(err)}, 500

@app.route("/health")
def health():
    return jsonify({"status": "ok"}), 200

if __name__ == "__main__":
    app.run()