#!groovy

@Library('cib-pipeline-library') _

import de.cib.pipeline.library.Constants

// Builds and tests the application, deploys the jar to artifacts.cibseven.org
// (distributionManagement from org.cibseven:release-parent), builds the Docker
// image with jib and pushes it to Harbor, and publishes the Helm chart under
// helm/cibseven-mcp-restapi to Harbor -- all versioned by the Maven pom version.
// Run with RELEASE_BUILD=true to release the jar, image and chart together on one
// version.
//
// The Docker image is built by the standard 'Create & Push Docker Image' stage via
// the jib-maven-plugin (configured in pom.xml, no Dockerfile) -- the CIB-standard
// approach for Spring Boot apps. It pushes to jib.to.image (harbor.cib.de/dev/
// cibseven-mcp-restapi) and runs before the Helm stages, so the image exists before
// the chart that references it is published. The public Docker Hub image is built
// (also with jib) and published separately via .github/workflows/build-and-publish.yml,
// since GitHub runners cannot reach the internal Harbor registry.

standardMavenPipeline(
    uiParamPresets: [
        'UNIT_TESTS': true,
        'SAST': true,
        'CREATE_DOCKER_IMAGE': true,
        'DEPLOY_HELM_CHARTS_TO_HARBOR': true
    ],
    primaryBranch: 'main',
    // -Djib.useOnlyProjectCache / disableUpdateChecks keep jib's base-image cache local
    // to the build workspace (as done by other CIB jib pipelines).
    mvnParams: '-U -Djib.useOnlyProjectCache=true -Djib.disableUpdateChecks=true',
    helmChartPaths: ['helm/cibseven-mcp-restapi'],
    notificationUrl: Constants.NOTIFICATION_URL_CIBSEVEN,
    mvnContainerName: Constants.MAVEN_JDK_17_CONTAINER
)
