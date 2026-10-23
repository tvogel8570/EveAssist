# Keycloak version, defined once; the builder and final stages both start from this stage
FROM quay.io/keycloak/keycloak:26.8 AS keycloak

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

COPY --chown=1000:0 EveAssist-realm-export.json /opt/keycloak/data/import/
