#!groovy

@Library('cib-pipeline-library') _

import de.cib.pipeline.library.Constants

// Single source for the primary branch, reused by the pipeline config and the
// customStageBody closure below (the library's pipelineParams.primaryBranch is
// not reachable from the closure's scope).
def primaryBranch = 'main'

// Builds and tests the application, deploys the jar to artifacts.cibseven.org
// (distributionManagement from org.cibseven:release-parent), builds the Docker
// image from the repo Dockerfile and pushes it to Harbor, and publishes the Helm
// chart under helm/cibseven-mcp-restapi to Harbor -- all versioned by the Maven
// pom version. Run with RELEASE_BUILD=true to release the jar, image and chart
// together on one version.
//
// The Docker image is built via BuildKit (buildContainerImage) from the same
// Dockerfile the GitHub Actions workflow uses, NOT via CREATE_DOCKER_IMAGE/jib
// (there is no jib-maven-plugin in the pom, and jib ignores the Dockerfile). Keep
// CREATE_DOCKER_IMAGE off. The public Docker Hub image (cibseven/cibseven-mcp-restapi)
// is still published separately via .github/workflows/build-and-publish.yml, since
// GitHub runners cannot reach the internal Harbor registry.

standardMavenPipeline(
    uiParamPresets: [
        'UNIT_TESTS': true,
        'SAST': true,
        'DEPLOY_HELM_CHARTS_TO_HARBOR': true
    ],
    primaryBranch: primaryBranch,
    mvnParams: '-U',
    helmChartPaths: ['helm/cibseven-mcp-restapi'],
    notificationUrl: Constants.NOTIFICATION_URL_CIBSEVEN,
    mvnContainerName: Constants.MAVEN_JDK_17_CONTAINER,
    // Add the BuildKit sidecar to the build pod so buildContainerImage (below) can build the
    // Dockerfile. BuildKit/Kaniko are disabled by default to save resources and must be opted
    // in per project via buildPodConfig -- no pipeline-library change needed.
    buildPodConfig: [
        (Constants.BUILDKIT_CONTAINER): [enabled: true]
    ],
    customStageBody: {
        // Build & push the Docker image from the repo Dockerfile (jlink/alpine) to
        // Harbor, tagged with the pom version so the image, jar and Helm chart all
        // share one version. Runs on main and on release builds (a release build has
        // already checked out the release tag, so the pom version is the released,
        // non-SNAPSHOT version). The build produces its own jar because a release build
        // does not leave the boot jar in target/ (release:perform builds in
        // target/checkout/), and the Dockerfile COPY needs target/cibseven-mcp-server-*.jar.
        if (env.BRANCH_NAME == primaryBranch || params.DEPLOY_ANY_BRANCH_TO_REPOSITORY) {
            def version = readMavenPom(file: 'pom.xml').version
            withMaven() {
                sh 'mvn package -DskipTests -Dmaven.test.skip'
            }
            buildContainerImage(
                imageName: 'cibseven-mcp-restapi',
                version: version,
                dockerfile: 'Dockerfile',
                args: [JAVA: '17']
            )
        }
    }
)
