# cibseven-mcp-restapi Helm chart

Deploys the **CIB seven MCP server** (`cibseven/cibseven-mcp-restapi` image): a
Spring Boot application hosting the
[cibseven-mcp-restapi](https://github.com/cibseven/cibseven-mcp-restapi) library,
which exposes an external CIB seven engine REST API as MCP tools.

> The chart keeps its historical name `cibseven-mcp-restapi` (matching the
> published image) even though the application repository is
> `cibseven-mcp-server`, so existing deployments that pin the chart by name keep
> working.

- **Image:** `cibseven/cibseven-mcp-restapi` (built with jib — no Dockerfile —
  published to Docker Hub via `.github/workflows/build-and-publish.yml` and to Harbor
  by the Jenkins pipeline).
- **Template library:** all Kubernetes objects are produced by the shared CIB
  `common-tpl-lib` chart (`oci://harbor.cib.de/charts`, version `2.7.0`).

## Install

The `common-tpl-lib` dependency is not vendored in this repository.
`helm dependency build` pulls it from `oci://harbor.cib.de/charts`, which requires
credentials for CIB's Harbor registry; without them the chart cannot be built.

```bash
helm dependency build

helm install my-mcp . \
  --namespace cibseven --create-namespace \
  --set application.cibseven.webclient.engineRest.url=https://my-engine.example.org
```

## Configuration

| Value | Default | Description |
| --- | --- | --- |
| `application.cibseven.webclient.engineRest.url` | `http://localhost:8080` | Engine REST base URL the MCP tools call. |
| `application.cibseven.mcp.restapi-mcp` | `true` | Enable the REST API MCP server. |
| `application.spring.ai.mcp.server.streamable-http.mcp-endpoint` | `/mcp` | MCP endpoint path. |
| `secrets.env` | `{}` | Secret environment variables, e.g. `ENGINE_REST_JWT_SECRET` (minted-jwt mode), `GRAPH_CLIENT_SECRET` (graph resolver). |
| `global.image.repository` | `cibseven` | Image registry/organisation. |
| `image.tag` | `""` (→ `appVersion`) | Overrides the image tag. |
| `resources.enabled` / `global.resources.enabled` | `false` | Apply CPU/memory requests & limits. |

The full `application` map is rendered verbatim into a Kubernetes **Secret**
mounted at `/opt/cib/conf/application.yaml` and loaded via
`SPRING_CONFIG_ADDITIONAL_LOCATION`, so any Spring property of the application
can be set per deployment — including the OAuth2 protection of the MCP endpoint
(`spring.security.oauth2.resourceserver.jwt.issuer-uri`), the advertised scopes
(`cibseven.mcp.oauth2.scopes-supported`) and the outbound engine-rest
authentication mode (`cibseven.mcp.engine-rest.*`). See the
[application README](../../README.md) for the complete property reference.

## Health probes

The chart uses HTTP probes against the Spring Boot actuator
(`/actuator/health/liveness` and `/actuator/health/readiness`), which the image
exposes by default.

## Security notes

- Deployment-specific configuration (including any credentials in the
  `application` map or `secrets.env`) is rendered into a Kubernetes **Secret**,
  never a ConfigMap. For real environments prefer an `ExternalSecret` / sealed
  secret over plaintext override values.
- The container runs as a non-root user (`runAsUser: 1000`,
  `runAsNonRoot: true`), drops all Linux capabilities and disables privilege
  escalation.
- Never expose an unprotected MCP endpoint: set
  `spring.security.oauth2.resourceserver.jwt.issuer-uri` for anything reachable
  beyond localhost, and expose only `/mcp` and the `/.well-known/oauth-*`
  discovery paths through your ingress.
