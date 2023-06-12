FROM postgres:15-alpine

RUN apk update && \
    apk upgrade --latest

# set default user and working directory
USER postgres
WORKDIR /var/lib/postgresql

 # copy database initialization script and database backup to where it will be automatically executed on FIRST startup only
COPY sql/init/* /docker-entrypoint-initdb.d/
COPY sql/backup/postgres-schema-latest.dmp /var/lib/postgresql/backup/sde-postgres-latest.dmp