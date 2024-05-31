-- initialize database
CREATE USER localdevuser WITH PASSWORD 'password1';
CREATE DATABASE keycloak;
GRANT ALL PRIVILEGES ON DATABASE keycloak TO localdevuser;
\c keycloak postgres
GRANT ALL ON SCHEMA public TO localdevuser;
