
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
deploy: preflight
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
