# Home Assignment – Elbit DevOps

## What I built

Two-part project: a Jenkins CI/CD pipeline that builds and validates a Dockerized Flask + Nginx stack, and a Kubernetes deployment of that same app with KEDA autoscaling.

---

## Part 1 – Jenkins Pipeline

### How the pipeline works

The pipeline is driven by a **DSL seed job**: instead of creating job1, job2, job3 manually in Jenkins, a single seed job reads `jenkins/seed_job.groovy` and generates all three pipeline jobs programmatically. 
This means the pipeline itself is code (reproducible, versionable).

The three jobs cascade automatically: job1 triggers job2 on success, job2 triggers job3. 
If any job fails, the chain stops and a Slack notification is sent to `#ci-cd`.

- **job1** builds the Flask app image and pushes it to Docker Hub (tagged with the git commit SHA + `latest`)
- **job2** builds the custom Nginx image and pushes it
- **job3** runs both containers, verifies the stack, then tdestroys them

The push stage in job1 and job2 is only when `/main` branch; builds happen on every commit, but images are only pushed when merging to main.

### Flask app

The app exposes two endpoints:
- `/containers` — queries the local Docker engine and returns the list of running containers
- `/health` — used by Kubernetes probes

I also added `/metrics` (Prometheus format) which i used later for KEDA trigger in Part 2.

The app runs with **Gunicorn** (2 workers) instead of Flask's dev server.

### Docker socket security

The app needs to talk to the Docker daemon to list containers. 
Mounting `/var/run/docker.sock` directly into a container gives it full Docker root access; anything running in that container can create, delete, or inspect any container on the host. 

Instead I used **`tecnativa/docker-socket-proxy`**: a sidecar container that sits between the app and the socket, and only allows the specific API calls I need (`CONTAINERS=1`). The Flask container connects to it via `DOCKER_HOST=tcp://socket-proxy:2375` and never touches the socket directly.

### Nginx

Nginx sits in front of Flask as a reverse proxy. It injects `X-Real-IP` and `X-Forwarded-For` headers so the upstream knows the real client IP. I used an `upstream` block with `keepalive 8` so Nginx maintains a pool of persistent connections to Flask instead of opening a new TCP connection on every request.

Both containers run on an isolated Docker bridge network meaning Flask is not reachable from outside, only Nginx's port is bound to the host.

### Pipeline reliability

Some considerations:
- `set -eux` in every shell block — fails fast, logs every command, no silent errors
- `git checkout` happens before reading `$GIT_COMMIT` (otherwise it's null on manual triggers)
- Cleanup runs in `post { always { ... } }` : containers are removed even if the pipeline fails mid-run, and also at the start of job3 to clear any leftovers from a previous broken run

### Docker image

Multistage build: the builder stage compiles Python wheels (needs gcc/build-essential), the runtime stage only copies the pre-built wheels (no compiler in the final image). The app runs as a non-root user (`appuser`).

---

## Part 2 – Kubernetes + KEDA

### Deployment

Standard Kubernetes deployment with:
- **Persistent volume** mounted at `/app/data` (ReadWriteOnce, 1Gi)
- Liveness and readiness probes on `/health`
- CPU and memory requests/limits defined

### KEDA autoscaling

I use a ScaledObject **Prometheus trigger** . It scales based on the  HTTP request rate:

```
sum(rate(http_requests_total[2m])) > 5 req/s → scale up (max 5 replicas)
```

This required installing `kube-prometheus-stack` and adding a `ServiceMonitor` so Prometheus knows to scrape the Flask `/metrics` endpoint (added script setup.sh for this purpose).

---

## Potential improvements

- **Jenkins agent with Docker label** and dedicated dynamic agent 
- **GitHub webhook** instead of SCM polling every 5 minutes (lower latency, less noise)
- **Helm chart** for the K8s manifests would make configuration (image tag, replicas, resource limits) easier to manage across environments
- **Gunicorn worker tuning** currently hardcoded at 2 workers; ideally derived from CPU count (`2 * CPU + 1`)
