.PHONY: help build test run run-prod docker-up docker-down docker-logs db-up db-down clean openapi

help:            ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

build:           ## Compile and package (skips tests)
	./mvnw -q -B clean package -DskipTests

test:            ## Run unit + integration tests (embedded PostgreSQL, no Docker needed)
	./mvnw -B verify

run:             ## Run locally with the dev profile (needs PostgreSQL, see `make db-up`)
	./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

run-prod:        ## Run the packaged jar with the prod profile (reads .env)
	set -a; . ./.env; set +a; java -jar target/fleet-operations-backend.jar --spring.profiles.active=prod

db-up:           ## Start only PostgreSQL in Docker
	docker compose up -d postgres

db-down:         ## Stop PostgreSQL
	docker compose stop postgres

docker-up:       ## Build and start PostgreSQL + backend
	docker compose up -d --build

docker-down:     ## Stop and remove containers (keeps volumes)
	docker compose down

docker-logs:     ## Tail backend logs
	docker compose logs -f backend

openapi:         ## Download the OpenAPI spec from a running backend
	curl -fsS http://localhost:8080/v3/api-docs -o docs/openapi.json && echo "written docs/openapi.json"

clean:           ## Remove build output
	./mvnw -q clean
