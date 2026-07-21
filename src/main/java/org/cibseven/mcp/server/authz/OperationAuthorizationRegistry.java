/*
 * Copyright CIB software GmbH and/or licensed to CIB software GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. CIB software licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.cibseven.mcp.server.authz;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.cibseven.mcp.restapi.models.HTTPRoute;
import org.cibseven.mcp.restapi.models.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Maps an OpenAPI operation to the CIB seven {@link RequiredAuthorization} needed to invoke
 * it.
 *
 * <p>Resolution order: an explicit override table ({@code authz/operation-authorization.json}
 * on the classpath, keyed by {@code operationId}) for the operations whose required
 * authorization the heuristic cannot infer, then a heuristic derived from the operation's
 * name/HTTP method and the first path segment. Operations that resolve to neither return
 * {@link Optional#empty()} — they are left ungated (fail-open), with the engine's own
 * authorization as the backstop.</p>
 *
 * <p>The mapping is intentionally coarse (resource-type level, no instance ids) and
 * best-effort: it covers the common, high-risk cases well and leaves the long tail to the
 * engine. Unmapped operationIds are logged once so the override table can be extended.</p>
 */
public class OperationAuthorizationRegistry {

    private static final Logger logger =
            LoggerFactory.getLogger(OperationAuthorizationRegistry.class);

    private static final String OVERRIDES_RESOURCE = "authz/operation-authorization.json";

    /** First path segment -> engine resource (name, numeric type). */
    private record ResourceRef(String name, int type) {
    }

    private static final Map<String, ResourceRef> RESOURCE_BY_SEGMENT = Map.ofEntries(
            Map.entry("process-definition", new ResourceRef("ProcessDefinition", 6)),
            Map.entry("process-instance", new ResourceRef("ProcessInstance", 8)),
            Map.entry("execution", new ResourceRef("ProcessInstance", 8)),
            Map.entry("variable-instance", new ResourceRef("ProcessInstance", 8)),
            Map.entry("job", new ResourceRef("ProcessInstance", 8)),
            Map.entry("external-task", new ResourceRef("ProcessInstance", 8)),
            Map.entry("incident", new ResourceRef("ProcessInstance", 8)),
            Map.entry("job-definition", new ResourceRef("ProcessDefinition", 6)),
            Map.entry("task", new ResourceRef("Task", 7)),
            Map.entry("deployment", new ResourceRef("Deployment", 9)),
            Map.entry("decision-definition", new ResourceRef("DecisionDefinition", 10)),
            Map.entry("decision-requirements-definition",
                    new ResourceRef("DecisionRequirementsDefinition", 14)),
            Map.entry("batch", new ResourceRef("Batch", 13)),
            Map.entry("user", new ResourceRef("User", 1)),
            Map.entry("group", new ResourceRef("Group", 2)),
            Map.entry("authorization", new ResourceRef("Authorization", 4)),
            Map.entry("tenant", new ResourceRef("Tenant", 11)),
            Map.entry("filter", new ResourceRef("Filter", 5)));

    /** ProcessDefinition, used for the /history/** family (READ_HISTORY / DELETE_HISTORY). */
    private static final ResourceRef PROCESS_DEFINITION = new ResourceRef("ProcessDefinition", 6);

    private final Map<String, RequiredAuthorization> overrides;
    private final Set<String> loggedUnmapped = ConcurrentHashMap.newKeySet();

    public OperationAuthorizationRegistry() {
        this.overrides = loadOverrides();
        logger.info("Loaded {} operation-authorization overrides", overrides.size());
    }

    /** @return the authorization required to call the route, or empty if left ungated. */
    public Optional<RequiredAuthorization> requiredFor(HTTPRoute route) {
        if (route == null || route.getOperationId() == null) {
            return Optional.empty();
        }

        RequiredAuthorization override = overrides.get(route.getOperationId());
        if (override != null) {
            return Optional.of(override);
        }

        String segment = firstPathSegment(route.getPath());
        String permission = permissionFor(route.getMethod(), route.getOperationId());

        if ("history".equals(segment)) {
            String historyPermission = "DELETE".equals(permission) ? "DELETE_HISTORY" : "READ_HISTORY";
            return Optional.of(new RequiredAuthorization(
                    PROCESS_DEFINITION.name(), PROCESS_DEFINITION.type(), historyPermission));
        }

        ResourceRef resource = RESOURCE_BY_SEGMENT.get(segment);
        if (resource == null) {
            if (loggedUnmapped.add(route.getOperationId())) {
                logger.info("No authorization mapping for operation '{}' ({} {}) — left ungated",
                        route.getOperationId(), route.getMethod(), route.getPath());
            }
            return Optional.empty();
        }

        return Optional.of(new RequiredAuthorization(resource.name(), resource.type(), permission));
    }

    /**
     * Best-effort permission from the operation name (CIB seven operationIds are prefixed
     * consistently) with an HTTP-method fallback. Read-style POST queries (e.g.
     * {@code queryProcessInstances}) are correctly classified as READ rather than CREATE.
     */
    private static String permissionFor(HttpMethod method, String operationId) {
        String id = operationId.toLowerCase();
        if (id.startsWith("get") || id.startsWith("query") || id.startsWith("count")
                || id.startsWith("list") || id.startsWith("fetch")) {
            return "READ";
        }
        if (id.startsWith("create")) {
            return "CREATE";
        }
        if (id.startsWith("delete") || id.startsWith("remove")) {
            return "DELETE";
        }
        if (id.startsWith("update") || id.startsWith("set") || id.startsWith("put")
                || id.startsWith("modify") || id.startsWith("suspend") || id.startsWith("activate")) {
            return "UPDATE";
        }
        return switch (method) {
            case GET, HEAD, OPTIONS, TRACE -> "READ";
            case DELETE -> "DELETE";
            case PUT, PATCH -> "UPDATE";
            case POST -> "CREATE";
        };
    }

    private static String firstPathSegment(String path) {
        if (path == null) {
            return "";
        }
        String p = path.startsWith("/") ? path.substring(1) : path;
        int slash = p.indexOf('/');
        return slash >= 0 ? p.substring(0, slash) : p;
    }

    private static Map<String, RequiredAuthorization> loadOverrides() {
        Map<String, RequiredAuthorization> result = new HashMap<>();
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = new ClassPathResource(OVERRIDES_RESOURCE).getInputStream()) {
            Map<String, JsonNode> raw =
                    mapper.convertValue(mapper.readTree(in),
                            new TypeReference<Map<String, JsonNode>>() {});
            for (Map.Entry<String, JsonNode> field : raw.entrySet()) {
                if (field.getKey().startsWith("_") || !field.getValue().isObject()) {
                    continue;
                }
                result.put(field.getKey(),
                        mapper.treeToValue(field.getValue(), RequiredAuthorization.class));
            }
        }
        catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to load " + OVERRIDES_RESOURCE + " from the classpath", e);
        }
        return result;
    }
}
