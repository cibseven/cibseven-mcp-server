#!/bin/bash -eux

VERSION=${VERSION:-$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)}
JAVA=${JAVA:-17}

IMAGE=cibseven/cibseven-mcp-restapi

function build_and_push {
    local tags=("$@")
    printf -v tag_arguments -- "--tag $IMAGE:%s " "${tags[@]}"
    docker buildx build .                         \
        $tag_arguments                            \
        --build-arg JAVA=${JAVA}                  \
        --cache-to type=gha,mode=max,scope="$GITHUB_REF_NAME-java${JAVA}-image" \
        --cache-from type=gha,scope="$GITHUB_REF_NAME-java${JAVA}-image" \
        --platform linux/amd64                    \
        --provenance=false                        \
        --push

      echo "Tags released:" >> $GITHUB_STEP_SUMMARY
      printf -- "- $IMAGE:%s\n" "${tags[@]}" >> $GITHUB_STEP_SUMMARY
}

CHECK_TAG="${VERSION}"

# SNAPSHOT versions are mutable and may be re-published; only released
# (non-SNAPSHOT) versions are immutable and skipped once they already exist.
if [[ "${VERSION}" != *-SNAPSHOT ]] && [ $(docker manifest inspect $IMAGE:${CHECK_TAG} > /dev/null ; echo $?) == '0' ]; then
    echo "Not pushing already released docker image: $IMAGE:${CHECK_TAG}"
    exit 0
fi

docker login -u "${DOCKER_HUB_USERNAME}" -p "${DOCKER_HUB_PASSWORD}"

tags+=("${VERSION}")

# Latest Docker image is created and pushed just once when a new version is released.
# Latest tag refers to the latest release (never a SNAPSHOT).
# Uses GITHUB_REF_NAME because git rev-parse returns "HEAD" (detached) on GitHub runners.
if [ "$GITHUB_REF_NAME" = "main" ] && [[ "${VERSION}" != *-SNAPSHOT ]]; then
    tags+=("latest")
fi

build_and_push "${tags[@]}"
