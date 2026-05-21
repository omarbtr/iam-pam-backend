# IAM-PAM — Kubernetes Deployment Guide

## Infrastructure Overview

| Component | IP | Role |
|---|---|---|
| Bastion VM | 192.168.112.138 | SSH gateway + guacd |
| K3s VM | 192.168.112.139 | Kubernetes master+worker |

---

## 1. Provision the K3s VM

### 1.1 Install K3s (single-node)

```bash
ssh omar@192.168.112.139

curl -sfL https://get.k3s.io | sh -

# Verify it's running
sudo systemctl status k3s
sudo k3s kubectl get nodes
```

### 1.2 Get the kubeconfig (needed for GitHub Actions)

```bash
sudo cat /etc/rancher/k3s/k3s.yaml
```

Copy the output — replace `127.0.0.1` with `192.168.112.139` in the `server:` field. This is the value for the `KUBE_CONFIG` GitHub secret.

---

## 2. Configure GitHub Secrets

Go to **Settings → Secrets and variables → Actions** in your GitHub repo and add:

| Secret | Value |
|---|---|
| `DOCKER_USERNAME` | Your Docker Hub username |
| `DOCKER_PASSWORD` | Your Docker Hub password or access token |
| `KUBE_CONFIG` | Full contents of k3s.yaml (with server IP replaced) |
| `VM_SSH_HOST` | `192.168.112.139` |
| `VM_SSH_USER` | `omar` |
| `VM_SSH_KEY` | Private key for SSH access to K3s VM |

---

## 3. Configure Secrets in the Manifests

Before first deploy, edit the following files with real values:

### `k8s/backend/secret.yaml`

```bash
# Generate a 32-character encryption key
openssl rand -base64 24

# Encode bastion SSH private key
base64 -w 0 /path/to/bastion_key
```

Fill in:
- `KEYCLOAK_CLIENT_SECRET` — from Keycloak admin console → Clients → iam-pam-backend → Credentials
- `RESOURCE_ENCRYPTION_KEY` — 32-char random string
- `FACE_JWT_SECRET` — random string
- `BASTION_KEY` — base64-encoded content of the bastion SSH private key

### `k8s/postgresql/secret.yaml`

Change `POSTGRES_PASSWORD` from `postgres` to a secure password and update `k8s/backend/secret.yaml` `DB_PASSWORD` to match.

---

## 4. Install nginx Ingress Controller

K3s ships with Traefik by default. The manifests use nginx annotations, so install nginx ingress instead:

```bash
# On the K3s VM
sudo k3s kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.9.4/deploy/static/provider/cloud/deploy.yaml

# Verify
sudo k3s kubectl get pods -n ingress-nginx
```

---

## 5. First Deploy

### 5.1 Build and push Docker images

```bash
# Backend (from repo root)
docker build -t <DOCKER_USERNAME>/iam-pam-backend:latest ./backend
docker push <DOCKER_USERNAME>/iam-pam-backend:latest

# Frontend
docker build -t <DOCKER_USERNAME>/iam-pam-frontend:latest ./frontend
docker push <DOCKER_USERNAME>/iam-pam-frontend:latest
```

### 5.2 Apply manifests manually (or let CI/CD do it)

```bash
export KUBECONFIG=~/.kube/k3s.yaml

kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/ingress.yaml

kubectl apply -f k8s/postgresql/
kubectl apply -f k8s/keycloak/
kubectl apply -f k8s/backend/
kubectl apply -f k8s/frontend/
kubectl apply -f k8s/openldap/
kubectl apply -f k8s/phpldapadmin/
kubectl apply -f k8s/pgadmin/
kubectl apply -f k8s/redis/
kubectl apply -f k8s/bastion/
```

### 5.3 Watch rollout

```bash
kubectl get pods -n iam-pam -w
```

---

## 6. Access the Application

Add to your `/etc/hosts` (or Windows `C:\Windows\System32\drivers\etc\hosts`):

```
192.168.112.139  iam-pam.local
```

| Service | URL |
|---|---|
| Frontend | http://iam-pam.local |
| Backend API | http://iam-pam.local/api |
| Keycloak | http://iam-pam.local/realms/master |
| Keycloak Admin | http://iam-pam.local/admin |
| pgAdmin | http://192.168.112.139:5050 (NodePort — see note) |
| phpLDAPadmin | http://192.168.112.139:8090 (NodePort — see note) |

> **Note:** pgAdmin and phpLDAPadmin are internal tools. To access them from outside the cluster, expose them via NodePort or use `kubectl port-forward`:
> ```bash
> kubectl port-forward svc/pgadmin 5050:5050 -n iam-pam
> kubectl port-forward svc/phpldapadmin 8090:8090 -n iam-pam
> ```

---

## 7. Keycloak Initial Setup

After first deploy:

1. Open http://iam-pam.local/admin → log in with `admin` / `admin` (from `keycloak-secret`)
2. Create realm `iam-pam-realm`
3. Create client `iam-pam-backend` with credential mode enabled
4. Copy the client secret → update `k8s/backend/secret.yaml` `KEYCLOAK_CLIENT_SECRET`
5. Restart backend: `kubectl rollout restart deployment/backend -n iam-pam`

---

## 8. CI/CD Pipeline

### CI (`.github/workflows/ci.yml`)
- Triggers on **every push to any branch**
- Builds backend (Maven) and frontend (npm)
- Pushes Docker images tagged with both `:latest` and `:<git-sha>`

### CD (`.github/workflows/cd.yml`)
- Triggers on **push to `main` only**
- Pins images to the exact `:<git-sha>` for reproducibility
- Applies all manifests via `kubectl apply`
- Waits for rollout to complete

---

## 9. Operations

### View logs

```bash
kubectl logs -f deployment/backend -n iam-pam
kubectl logs -f deployment/frontend -n iam-pam
kubectl logs -f deployment/keycloak -n iam-pam
```

### Restart a deployment

```bash
kubectl rollout restart deployment/backend -n iam-pam
```

### Scale a deployment

```bash
kubectl scale deployment/backend --replicas=2 -n iam-pam
```

### Connect to a pod

```bash
kubectl exec -it deployment/backend -n iam-pam -- /bin/sh
```

### Check PVC usage

```bash
kubectl get pvc -n iam-pam
```

### Delete everything (destructive)

```bash
kubectl delete namespace iam-pam
```

---

## 10. Directory Structure

```
k8s/
├── namespace.yaml
├── ingress.yaml
├── postgresql/
│   ├── configmap.yaml      # init.sql (creates keycloak + iam_pam_db)
│   ├── secret.yaml         # POSTGRES_PASSWORD
│   ├── pvc.yaml            # 10Gi local-path
│   ├── deployment.yaml     # StatefulSet
│   └── service.yaml        # Headless (ClusterIP: None)
├── keycloak/
│   ├── configmap.yaml
│   ├── secret.yaml
│   ├── deployment.yaml     # init container: waits for postgres
│   └── service.yaml
├── backend/
│   ├── configmap.yaml
│   ├── secret.yaml         # DB_PASSWORD, KEYCLOAK_CLIENT_SECRET, bastion_key
│   ├── deployment.yaml     # mounts bastion_key at /app/secrets/bastion_key
│   └── service.yaml
├── frontend/
│   ├── deployment.yaml
│   └── service.yaml
├── openldap/
│   ├── pvc.yaml
│   ├── deployment.yaml
│   └── service.yaml
├── phpldapadmin/
│   ├── deployment.yaml
│   └── service.yaml
├── pgadmin/
│   ├── deployment.yaml
│   └── service.yaml
├── redis/
│   ├── deployment.yaml
│   └── service.yaml
└── bastion/
    └── external-service.yaml   # ExternalName → 192.168.112.138

.github/workflows/
├── ci.yml    # build + push on every branch
└── cd.yml    # deploy to K3s on main
```
