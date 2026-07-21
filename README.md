# CIB seven MCP Server

A ready-to-run **MCP server for [CIB seven](https://cibseven.org)**: a minimal Spring
Boot application hosting the
[cibseven-mcp-restapi](https://github.com/cibseven/cibseven-mcp-restapi) library, which
exposes the CIB seven engine REST API (or any other OpenAPI-described API) as **MCP
tools** for LLMs. This repository adds the packaging: the runnable application, the
Docker image and the Helm chart.

```
MCP client (claude.ai, VS Code, …)
        │  OAuth2 bearer token (Entra ID)
        ▼
cibseven-mcp-server  /mcp        ← this application
        │  passthrough token or freshly minted CIB seven JWT
        ▼
CIB seven engine-rest            ← external engine (not part of this deployment)
```

All functionality — OpenAPI→MCP tool mapping, OAuth2 protection of `/mcp`, and the
outbound engine-rest authentication strategies — comes auto-configured from the
library. This application contributes only configuration and packaging.

## Quick start

Run locally against an engine on `localhost:8080` (e.g. a CIB seven Run distribution):

```bash
mvn spring-boot:run
```

Or with Docker:

```bash
mvn package
docker build -t cibseven/cibseven-mcp-restapi .
docker run -p 8080:8080 \
  -e CIBSEVEN_WEBCLIENT_ENGINEREST_URL=https://my-engine.example.org \
  cibseven/cibseven-mcp-restapi
```

Then connect an MCP client to `http://localhost:8080/mcp` (see
[Debugging](https://github.com/cibseven/cibseven-mcp-restapi#debugging) in the library
README), or check health at `http://localhost:8080/actuator/health`.

> Without `spring.security.oauth2.resourceserver.jwt.issuer-uri` the MCP endpoint is
> **unprotected** — local development only. Never expose an unprotected MCP server.

## Configuration

Everything is standard Spring configuration; the key properties (see
[application.yaml](src/main/resources/application.yaml) for the commented reference and
the [library README](https://github.com/cibseven/cibseven-mcp-restapi) for full
details):

| Property / env var | Purpose |
| --- | --- |
| `cibseven.webclient.engineRest.url` | The engine-rest endpoint the MCP tools call. |
| `cibseven.openapi.url` | OpenAPI document to expose (defaults to the published CIB seven spec). |
| `spring.security.oauth2.resourceserver.jwt.issuer-uri` | Protects `/mcp` as an OAuth2 resource server (e.g. Entra ID). |
| `cibseven.mcp.oauth2.scopes-supported` | Scopes advertised to MCP clients without a scope input (claude.ai). |
| `cibseven.mcp.engine-rest.auth` | Outbound auth: `passthrough` (default) or `minted-jwt`. |
| `cibseven.mcp.engine-rest.minted-jwt.resolver` | Identity mapping: `claim`, `graph`, or `static` (dev/test). |
| `ENGINE_REST_JWT_SECRET` | Base64 HMAC secret shared with engine-rest (`minted-jwt` mode). Inject as a secret. |
| `GRAPH_CLIENT_ID` / `GRAPH_CLIENT_SECRET` | Entra app registration for the `graph` resolver. |

## Docker image

The image is built with [jib](https://github.com/GoogleContainerTools/jib) — no
Dockerfile — straight from the compiled classes on top of a public `eclipse-temurin:17-jre`
base, running as a non-root user with `-XX:MaxRAMPercentage=75.0` (see the
`jib-maven-plugin` configuration in [pom.xml](pom.xml)). It is published as
`cibseven/cibseven-mcp-restapi` to Docker Hub by the
[GitHub Actions workflow](.github/workflows/build-and-publish.yml) and to the internal
Harbor registry by the Jenkins pipeline.

## Helm chart

[helm/cibseven-mcp-restapi](helm/cibseven-mcp-restapi) deploys the image with hardened
security defaults, actuator HTTP probes, and a per-deployment `application` map rendered
into a Kubernetes Secret. The chart keeps its historical name `cibseven-mcp-restapi`
(matching the image) for compatibility with existing deployments. See the
[chart README](helm/cibseven-mcp-restapi/README.md).

## Building

```bash
mvn verify
```

The smoke test boots the full application against a local fixture OpenAPI document, so
no network access is required.

## History

This repository started as a fork of the CIB seven getting-started Spring Boot example
with an embedded engine and demo processes. Since version 1.0.0 it is a lean host
application for the MCP library only — the engine is expected to run separately.

## License

Apache License 2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).
