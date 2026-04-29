#!/bin/bash
set -euo pipefail

echo "==> Starting Minikube..."
minikube start

echo "==> Adding Helm repositories..."
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo add kedacore https://kedacore.github.io/charts
helm repo update

echo "==> Installing kube-prometheus-stack..."
helm upgrade --install prometheus prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --create-namespace \
  --set prometheus.prometheusSpec.podMonitorSelectorNilUsesHelmValues=false \
  --set prometheus.prometheusSpec.serviceMonitorSelectorNilUsesHelmValues=false \
  --wait

echo "==> Installing KEDA..."
helm upgrade --install keda kedacore/keda \
  --namespace keda \
  --create-namespace \
  --wait

echo "==> Waiting for CRDs to be ready..."
kubectl wait --for condition=established --timeout=60s \
  crd/scaledobjects.keda.sh \
  crd/servicemonitors.monitoring.coreos.com

echo "==> Deploying Flask app..."
kubectl apply -f k8s/pvc.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/servicemonitor.yaml
kubectl apply -f k8s/keda-scaledobject.yaml

echo "==> Waiting for flask-app to be ready..."
kubectl rollout status deployment/flask-app --timeout=120s

echo "==> Setup complete."