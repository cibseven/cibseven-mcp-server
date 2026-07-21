#!/bin/bash -eux

# Builds the container image with jib (no Dockerfile) and pushes it to Docker Hub.
# jib is daemonless: it assembles and pushes the image directly from the compiled
# classes + dependencies, no `docker build`. The base image and container config live
# in pom.xml; here we only override the push target (Docker Hub) and the tags.

VERSION=${VERSION:-$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)}

IMAGE=docker.io/cibseven/cibseven-mcp-restapi

# SNAPSHOT versions are mutable and may be re-published; only released
# (non-SNAPSHOT) versions are immutable and skipped once they already exist.
if [[ "${VERSION}" != *-SNAPSHOT ]] && docker manifest inspect "${IMAGE}:${VERSION}" > /dev/null 2>&1; then
    echo "Not pushing already released docker image: ${IMAGE}:${VERSION}"
    exit 0
fi

# Extra tags beyond the version tag in jib.to.image. "latest" refers to the latest
# release and is pushed only when building from a release tag (main only ever holds a
# -SNAPSHOT, so the released version is published from its tag). GITHUB_REF_TYPE is
# "tag" or "branch"; the SNAPSHOT guard is belt-and-braces (a release tag is never one).
tag_args=()
push_latest=false
if [ "${GITHUB_REF_TYPE}" = "tag" ] && [[ "${VERSION}" != *-SNAPSHOT ]]; then
    tag_args=(-Djib.to.tags=latest)
    push_latest=true
fi

# Authenticate both pull (base image from Docker Hub, avoids anonymous rate limits) and
# push with the same Docker Hub credentials.
mvn -B -U package jib:build \
    -DskipTests -Dmaven.test.skip \
    -Djib.to.image="${IMAGE}:${VERSION}" \
    "${tag_args[@]}" \
    -Djib.from.auth.username="${DOCKER_HUB_USERNAME}" \
    -Djib.from.auth.password="${DOCKER_HUB_PASSWORD}" \
    -Djib.to.auth.username="${DOCKER_HUB_USERNAME}" \
    -Djib.to.auth.password="${DOCKER_HUB_PASSWORD}"

{
    echo "Tags released:"
    echo "- ${IMAGE}:${VERSION}"
    if [ "${push_latest}" = true ]; then
        echo "- ${IMAGE}:latest"
    fi
} >> "$GITHUB_STEP_SUMMARY"
