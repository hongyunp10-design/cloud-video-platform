# Cloud Video Processing Platform

A cloud-native video processing platform built with **Java Spring Boot, Python FastAPI, Ray, Kubernetes, MySQL, Redis, and FFmpeg**, featuring persistent asynchronous job execution, distributed video transcoding, fault recovery, and automated GitOps deployment.

The project demonstrates backend reliability patterns and Kubernetes-based application delivery through an end-to-end video processing workflow.

## Architecture

```text
                 Client
                   |
                   v
          Java Job Service
           (Spring Boot)
                   |
         +---------+---------+
         |                   |
       MySQL                Redis
  Tasks / Audit          Task Cache
         |
         v
    Async Job Worker
         |
         v
      Python API
       (FastAPI)
         |
         v
      Ray Cluster
    +------------+
    |  Ray Head  |
    | Actor      |
    | Bootstrap  |
    +------------+
          |
    +-----+-----+
    |           |
 Ray Worker  Ray Worker
    |           |
    +-----+-----+
          |
        FFmpeg
          |
          v
     Shared PVC
  Video + Manifest
```

All application services run in Kubernetes. Java manages persistent job state and execution, while Python and Ray handle distributed video processing.

## Core Features

### 1. Persistent Asynchronous Job Management

The Java Spring Boot service implements a database-backed asynchronous task execution system.

- Persistent task lifecycle: `PENDING → RUNNING → SUCCESS / FAILED / DEAD`
- Atomic task claiming to prevent duplicate execution
- Retry support and failure recovery
- Timeout detection and recovery
- Attempt fencing to prevent stale workers from overwriting newer execution results
- Task audit records
- Idempotent task creation
- Redis-backed task caching

MySQL serves as the source of truth for task state.

### 2. Distributed Video Processing

The Python API integrates with a Kubernetes-hosted Ray cluster to execute video processing tasks.

- Video upload and staging
- Detached, named Ray Actor for video processing
- Actor bootstrap and recovery
- FFmpeg transcoding
- 360p, 480p, and 720p output profiles
- Shared PVC storage for source and processed videos
- Persistent processing manifest and output verification

The Kubernetes deployment uses a Ray Head and two Ray Worker replicas.

### 3. Kubernetes Deployment

The application uses Kubernetes resources including:

| Component | Kubernetes Resource |
|---|---|
| Java Job Service | Deployment + Service |
| Python API | Deployment + Service |
| Ray Head | Deployment + Service |
| Ray Workers | Deployment |
| MySQL | StatefulSet + Service |
| Redis | Deployment + Service |
| Shared Video Storage | PersistentVolume + PersistentVolumeClaim |
| MySQL Credentials | Kubernetes Secret |

The system includes readiness and liveness probes, service discovery, persistent storage, and declarative deployment manifests.

### 4. CI/CD and GitOps

The delivery pipeline uses **GitHub Actions, Docker Buildx, GitHub Container Registry (GHCR), Kustomize, and Argo CD**.

```text
GitHub Push
     |
     v
Maven Verify + Tests
     |
     v
JAR Build Artifact
     |
     v
Docker Buildx
(AMD64 / ARM64)
     |
     v
GHCR Image Publishing
     |
     v
GitOps Image Version Update
     |
     v
Argo CD Automatic Sync
     |
     v
Kubernetes Rollout
     |
     v
End-to-End Validation
```

Key delivery practices:

- Full Java test suite runs before image publication
- The tested Java JAR is reused for container packaging
- Immutable Git commit SHA image tags
- Multi-platform container images
- Automated updates to GitOps image references
- Argo CD automatic synchronization and self-healing
- Git-based image version changes for rollback
- Automated end-to-end smoke testing

## Repository Structure

```text
cloud-video-platform/
├── job-service/             # Java Spring Boot control plane
├── api-service/             # Python FastAPI
├── worker-service/          # Ray actor and FFmpeg processing
├── gateway/                 # Gateway module
├── k8s/                     # Kubernetes manifests
├── deploy/
│   ├── gitops/              # Kustomize GitOps overlay
│   └── argocd/              # Argo CD Application
├── scripts/
│   └── e2e_smoke.py         # End-to-end smoke test
├── .github/workflows/       # CI/CD
├── Makefile
└── README.md
```

The demonstrated Kubernetes processing path uses Java Job Service, Python API, and Ray directly. The Gateway module is not part of the validated Kubernetes deployment path.

## Local Development

### Prerequisites

- Docker Desktop
- Minikube
- kubectl
- Make
- Git
- Sufficient CPU and memory resources to run the complete stack

### Local Deployment

Start Minikube and verify the cluster context:

```bash
minikube start
kubectl config current-context
```

The expected context is `minikube`.

For an environment not already managed by Argo CD:

```bash
make bootstrap
make build-images
make deploy
make status
```

Alternatively:

```bash
make dev-up
```

`make dev-up` combines environment bootstrap, local image builds, and deployment.

**Note:** The bootstrap process is designed for a local Minikube environment. A completely fresh cluster deployment has not yet been independently validated. Existing MySQL credentials and persistent data are preserved.

### GitOps Deployment

The GitOps environment uses GHCR images with explicit Git SHA tags.

Kustomize configuration:

```text
deploy/gitops/kustomization.yaml
```

Argo CD Application definition:

```text
deploy/argocd/cloud-video-platform.yaml
```

Once Argo CD manages the cluster, local `make deploy` and `make dev-up` are intentionally blocked to prevent configuration conflicts.

Application delivery is controlled through Git.

## End-to-End Testing

Run the smoke test from the repository root:

```bash
make e2e-test
```

The test executes inside the Python API Pod and uses real application services.

It validates:

1. API and Ray Actor health.
2. Test video generation and staging.
3. Java task creation.
4. Asynchronous task execution until `SUCCESS`.
5. Ray processing completion.
6. Manifest integrity.
7. Existence and sizes of 360p, 480p, and 720p output files.

Example successful result:

```text
[PASS] API and Ray Actor health
[PASS] Stage video
[PASS] Create Java task
[TASK] PENDING
[TASK] RUNNING
[TASK] SUCCESS
[PASS] 360p
[PASS] 480p
[PASS] 720p

E2E PASSED
```

This test uses real Java, MySQL, Python, Ray, FFmpeg, and Kubernetes storage components rather than mocked processing results.

## Design Decisions

| Decision | Rationale |
|---|---|
| MySQL-backed tasks | Durable state and concurrency control |
| Atomic claim | Prevent multiple executors from claiming the same task |
| Attempt fencing | Protect against stale execution results |
| Ray for processing | Separate video computation from the Java control plane |
| Kubernetes Services | Internal service discovery |
| StatefulSet for MySQL | Stable identity and persistent storage |
| PVC for video files | Shared storage across processing services |
| Git SHA image tags | Traceable and reproducible deployments |
| GitOps with Argo CD | Declarative deployment, drift correction, and version control |

## Limitations and Future Improvements

This project is a local Kubernetes engineering demonstration, not a production-grade managed video service.

Current limitations include:

- Single-node Minikube deployment
- Local shared PVC rather than distributed object storage in the validated Kubernetes configuration
- No production-grade high availability
- No horizontal autoscaling policy
- Ray Client initialization may experience transient startup failures
- Limited production observability and alerting

The application code includes optional S3-related functionality, but S3 uploads are disabled in the validated Kubernetes E2E environment.

These limitations are intentional to keep the project focused on backend reliability, distributed execution, Kubernetes fundamentals, and CI/CD practices.

## Technology Stack

**Backend:** Java 17, Spring Boot, Python, FastAPI, MyBatis

**Data:** MySQL 8.4, Redis 7

**Processing:** Ray, FFmpeg

**Infrastructure:** Docker, Kubernetes, Minikube, PVC

**CI/CD:** GitHub Actions, Docker Buildx, GHCR, Kustomize, Argo CD

## Project Status

The platform has successfully completed:

- Java integration testing
- Kubernetes application deployment
- Multi-architecture Docker image publishing
- Argo CD manual deployment
- Automated GitOps image promotion
- Argo CD automatic synchronization
- End-to-end video processing validation

The project is maintained as a reproducible engineering portfolio demonstrating cloud-native backend and platform development.