.PHONY: help setup certs env up down restart build logs logs-bff logs-api logs-kc logs-nginx \
        ps sessions psql-bff psql-api psql-kc kc-export clean nuke

# ─── Variables ────────────────────────────────────────────────────────────────
COMPOSE  = docker compose
POSTGRES = $(COMPOSE) exec postgres psql -U postgres

# ─── Aide ─────────────────────────────────────────────────────────────────────

help: ## Affiche cette aide
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-18s\033[0m %s\n", $$1, $$2}' \
		| sort

# ─── Initialisation ───────────────────────────────────────────────────────────

setup: env certs ## Initialisation complète (env + certificats TLS)

env: ## Copie .env.example → .env si absent
	@if [ ! -f .env ]; then \
		cp .env.example .env; \
		echo "✓ .env créé — pensez à renseigner les mots de passe"; \
	else \
		echo "⚠ .env existe déjà, non écrasé"; \
	fi

certs: ## Génère les certificats TLS locaux avec mkcert
	@command -v mkcert >/dev/null 2>&1 || { echo "✗ mkcert non installé — https://github.com/FiloSottile/mkcert"; exit 1; }
	mkcert -install
	mkcert -cert-file nginx/certs/cert.pem -key-file nginx/certs/key.pem localhost
	@echo "✓ Certificats générés dans nginx/certs/"

# ─── Docker Compose ───────────────────────────────────────────────────────────

up: ## Démarre tous les services (build inclus)
	$(COMPOSE) up --build -d
	@echo "✓ Application disponible sur https://localhost:8443"

down: ## Arrête tous les services
	$(COMPOSE) down

restart: ## Redémarre tous les services
	$(COMPOSE) restart

build: ## Reconstruit les images sans démarrer
	$(COMPOSE) build

ps: ## Affiche l'état des services
	$(COMPOSE) ps

# ─── Logs ─────────────────────────────────────────────────────────────────────

logs: ## Logs de tous les services (suivi)
	$(COMPOSE) logs -f

logs-bff: ## Logs du BFF (suivi)
	$(COMPOSE) logs -f bff

logs-api: ## Logs de l'API (suivi)
	$(COMPOSE) logs -f api

logs-kc: ## Logs de Keycloak (suivi)
	$(COMPOSE) logs -f keycloak

logs-nginx: ## Logs de Nginx (suivi)
	$(COMPOSE) logs -f nginx

# ─── Base de données ──────────────────────────────────────────────────────────

sessions: ## Affiche les sessions BFF actives (Spring Session JDBC)
	$(COMPOSE) exec postgres psql -U bff_user -d bff_db \
		-c "SELECT session_id, TO_TIMESTAMP(creation_time/1000) AS created, \
		           TO_TIMESTAMP(last_access_time/1000) AS last_access, \
		           max_inactive_interval \
		    FROM SPRING_SESSION \
		    ORDER BY last_access_time DESC;"

psql-bff: ## Ouvre un shell psql sur bff_db
	$(COMPOSE) exec postgres psql -U bff_user -d bff_db

psql-api: ## Ouvre un shell psql sur api_db
	$(COMPOSE) exec postgres psql -U api_user -d api_db

psql-kc: ## Ouvre un shell psql sur keycloak_db
	$(COMPOSE) exec postgres psql -U keycloak_user -d keycloak_db

# ─── Keycloak ─────────────────────────────────────────────────────────────────

kc-export: ## Exporte la configuration du realm Keycloak
	$(COMPOSE) exec keycloak /opt/keycloak/bin/kc.sh export \
		--dir /opt/keycloak/data/import \
		--realm my-realm \
		--users realm_file
	@echo "✓ Realm exporté dans keycloak/"

# ─── Nettoyage ────────────────────────────────────────────────────────────────

clean: ## Arrête les services et supprime les volumes (données perdues)
	$(COMPOSE) down -v
	@echo "✓ Services arrêtés et volumes supprimés"

nuke: ## Supprime tout : volumes, images buildées et certificats
	$(COMPOSE) down -v --rmi local
	rm -f nginx/certs/cert.pem nginx/certs/key.pem
	@echo "✓ Nettoyage complet effectué"
