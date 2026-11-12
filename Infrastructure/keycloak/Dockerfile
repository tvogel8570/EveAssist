# Keycloak version, defined once; the builder and final stages both start from this stage. It is pinned to a patch release,
# for dev and prod alike (both build from this file), because a floating 26.8 tag let dev run 26.8.1 while prod got 26.8.2,
# which rejects admin API tokens that have no audience: a difference that only showed at the first prod start. To move to a
# newer patch release, change it here, rebuild the dev image and the prod image, reload the dev realm if the file changed
# (documentation/runbooks/keycloak-realm.md), and note it in documentation/runbooks/deploy-log.md.
FROM quay.io/keycloak/keycloak:26.8.2 AS keycloak

# create builder image with curl properly installed
FROM registry.access.redhat.com/ubi9 AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs curl --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

# build optimized keycloak image
FROM keycloak AS builder

ENV KC_HEALTH_ENABLED=true
ENV KC_METRICS_ENABLED=true
ENV KC_DB=postgres
ENV KC_CACHE=local

WORKDIR /opt/keycloak
RUN /opt/keycloak/bin/kc.sh build --features="quick-theme,ciba"

# starting with base keycloak image
FROM keycloak
# copy from build image to custom image
USER root
COPY --from=ubi-micro-build /mnt/rootfs /

# continue with snc specific files
USER 1000
COPY --from=builder /opt/keycloak/ /opt/keycloak/
# the realm is imported when the database holds none. Dev bind-mounts the same file over this one. Prod relies on this copy,
# so an image and the realm it imports are one version. The file has placeholders only; the secrets come from the environment.
COPY --chown=1000:0 EveAssist-realm-export.json /opt/keycloak/data/import/EveAssist-realm-export.json
