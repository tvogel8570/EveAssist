FROM postgres:18.6-alpine3.24

# set default user and working directory
USER postgres
WORKDIR /var/lib/postgresql

# copy the database initialization script to where it will be automatically executed on FIRST startup only. The SDE is
# not part of the image: load-sde.sh loads it into a running database.
COPY sql/init/* /docker-entrypoint-initdb.d/
