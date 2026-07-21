#!groovy

@Library('cib-pipeline-library') _

import de.cib.pipeline.library.Constants

// Builds and tests the application, deploys the jar to artifacts.cibseven.org
// (distributionManagement from org.cibseven:release-parent) and publishes the
// Helm chart under helm/cibseven-mcp-restapi to Harbor, versioned by the Maven
// pom version. The Docker image (cibseven/cibseven-mcp-restapi) is built and
// published separately via GitHub Actions (.github/workflows/build-and-publish.yml).

standardMavenPipeline(
    uiParamPresets: [
        'UNIT_TESTS': true,
        'SAST': true,
        'DEPLOY_HELM_CHARTS_TO_HARBOR': true
    ],
    primaryBranch: 'main',
    mvnParams: '-U',
    helmChartPaths: ['helm/cibseven-mcp-restapi'],
    notificationUrl: Constants.NOTIFICATION_URL_CIBSEVEN,
    mvnContainerName: Constants.MAVEN_JDK_17_CONTAINER
)
