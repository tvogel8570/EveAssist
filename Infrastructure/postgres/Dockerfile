FROM postgres:18.6-alpine3.24

# set default user and working directory
USER postgres
WORKDIR /var/lib/postgresql

# copy database initialization script and database backup to where it will be automatically executed on FIRST startup only
COPY sql/init/* /docker-entrypoint-initdb.d/
COPY --chown=postgres:postgres backup/latest-postgresschema.pgdump /opt/sde/sde-postgres-latest.dmp
