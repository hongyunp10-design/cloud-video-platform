
.PHONY: preflight deploy status

# Verify the target cluster, credentials and required images.
preflight:
	@test "$$(kubectl config current-context)" = "minikube" || { \
		echo "ERROR: Kubernetes context must be minikube"; \
		exit 1; \
	}
	@kubectl cluster-info >/dev/null
	@kubectl get secret mysql-credentials >/dev/null || { \
		echo "ERROR: mysql-credentials Secret is missing"; \
		exit 1; \
	}
	@for image in api-service:v18 worker-service:s13 job-service:dev; do \
		minikube image ls | grep -Fq "$$image" || { \
			echo "ERROR: Missing Minikube image $$image"; \
			exit 1; \
		}; \
	done
	@echo "Preflight checks passed."

# Deploy the validated application stack.
deploy: preflight local-deploy-guard
	kubectl apply -f k8s/pv-pvc.yaml

	kubectl apply -f k8s/mysql.yaml
	kubectl apply -f k8s/redis.yaml
	kubectl rollout status statefulset/mysql --timeout=240s
	kubectl rollout status deployment/redis --timeout=180s

	kubectl apply -f k8s/ray-cluster.yaml
	kubectl rollout status deployment/ray-head --timeout=240s
	kubectl rollout status deployment/ray-worker --timeout=240s
	kubectl wait --for=condition=Ready pod -l app=ray-head --timeout=180s
	kubectl wait --for=condition=Ready pod -l app=ray-worker --timeout=180s

	kubectl apply -f k8s/api-deployment.yaml
	kubectl rollout status deployment/api-service --timeout=180s

	kubectl apply -f k8s/job-service-deployment.yaml
	kubectl rollout status deployment/job-service --timeout=240s

	@echo "Kubernetes deployment completed."

# Inspect deployed components.
status:
	kubectl get deployments
	kubectl get statefulsets
	kubectl get pods
	kubectl get pvc
	kubectl get services


.PHONY: build-images

API_IMAGE := api-service:v18
WORKER_IMAGE := worker-service:s13
JOB_IMAGE := job-service:dev

build-images:
	docker build -t $(API_IMAGE) ./api-service
	docker build --no-cache -t $(WORKER_IMAGE) ./worker-service
	docker build -t $(JOB_IMAGE) ./job-service

	@echo "Verifying Worker image source integrity..."
	@local_hash="$$(shasum -a 256 worker-service/tasks/video_tasks.py | awk '{print $$1}')"; \
	image_hash="$$(docker run --rm --entrypoint sha256sum $(WORKER_IMAGE) /app/tasks/video_tasks.py | awk '{print $$1}')"; \
	test "$$local_hash" = "$$image_hash" || { \
		echo "ERROR: Worker source hash mismatch"; \
		exit 1; \
	}
	@docker run --rm --entrypoint python3 $(WORKER_IMAGE) -c \
		'from tasks.video_tasks import VideoWorker; print("VideoWorker import OK")'
	@docker run --rm --entrypoint sh $(JOB_IMAGE) -c \
		'test -s /app/app.jar && echo "Java JAR OK"'

	@echo "Loading images into Minikube..."
	minikube image load --overwrite=true $(API_IMAGE)
	minikube image load --overwrite=true $(WORKER_IMAGE)
	minikube image load --overwrite=true $(JOB_IMAGE)

	@echo "Image build and validation completed."


.PHONY: bootstrap dev-up

# Initialize required local Kubernetes credentials.
bootstrap:
	@test "$$(kubectl config current-context)" = "minikube" || { \
		echo "ERROR: Kubernetes context must be minikube"; \
		exit 1; \
	}
	@kubectl cluster-info >/dev/null

	@if kubectl get secret mysql-credentials >/dev/null 2>&1; then \
		echo "MySQL credentials already exist. Keeping existing Secret."; \
	elif kubectl get pvc mysql-data-mysql-0 >/dev/null 2>&1; then \
		echo "ERROR: MySQL data exists but credentials are missing."; \
		echo "Restore the original Secret instead of generating new passwords."; \
		exit 1; \
	else \
		echo "Generating MySQL credentials..."; \
		root_password="$$(openssl rand -hex 24)"; \
		app_password="$$(openssl rand -hex 24)"; \
		kubectl create secret generic mysql-credentials \
			--from-literal=root-password="$$root_password" \
			--from-literal=app-password="$$app_password"; \
	fi

# Full local build and deployment.
dev-up: local-deploy-guard bootstrap build-images deploy
	@echo "Development environment deployment completed."

.PHONY: e2e-test

e2e-test:
	@test "$$(kubectl config current-context)" = "minikube" || { \
		echo "ERROR: Kubernetes context must be minikube"; \
		exit 1; \
	}
	kubectl exec -i deployment/api-service -c api-service -- \
		python3 - < scripts/e2e_smoke.py

.PHONY: local-deploy-guard

local-deploy-guard:
	@if kubectl get application cloud-video-platform -n argocd >/dev/null 2>&1; then \
		echo "ERROR: This cluster is managed by Argo CD."; \
		echo "Use the GitOps overlay instead of make deploy."; \
		exit 1; \
	fi