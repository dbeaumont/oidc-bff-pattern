-- Création des utilisateurs PostgreSQL
CREATE USER keycloak_user WITH PASSWORD 'changeme_keycloak';
CREATE USER bff_user       WITH PASSWORD 'changeme_bff';
CREATE USER api_user       WITH PASSWORD 'changeme_api';

-- Création des bases de données
CREATE DATABASE keycloak_db OWNER keycloak_user ENCODING 'UTF8';
CREATE DATABASE bff_db       OWNER bff_user       ENCODING 'UTF8';
CREATE DATABASE api_db       OWNER api_user       ENCODING 'UTF8';

-- Droits
GRANT ALL PRIVILEGES ON DATABASE keycloak_db TO keycloak_user;
GRANT ALL PRIVILEGES ON DATABASE bff_db       TO bff_user;
GRANT ALL PRIVILEGES ON DATABASE api_db       TO api_user;
