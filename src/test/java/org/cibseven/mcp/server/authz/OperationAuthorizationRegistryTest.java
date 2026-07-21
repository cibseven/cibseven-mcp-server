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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.cibseven.mcp.server.authz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.cibseven.mcp.restapi.models.HTTPRoute;
import org.cibseven.mcp.restapi.models.HttpMethod;
import org.junit.jupiter.api.Test;

class OperationAuthorizationRegistryTest {

    private final OperationAuthorizationRegistry registry = new OperationAuthorizationRegistry();

    @Test
    void appliesOverrideForProcessStart() {
        Optional<RequiredAuthorization> required =
                registry.requiredFor(route("startProcessInstance", "/process-definition/{id}/start",
                        HttpMethod.POST));

        assertThat(required).contains(
                new RequiredAuthorization("ProcessDefinition", 6, "CREATE_INSTANCE"));
    }

    @Test
    void appliesOverrideForTaskCompletion() {
        assertThat(registry.requiredFor(route("complete", "/task/{id}/complete", HttpMethod.POST)))
                .contains(new RequiredAuthorization("Task", 7, "TASK_WORK"));
    }

    @Test
    void appliesOverrideForMigrationExecution() {
        assertThat(registry.requiredFor(route("executeMigrationPlan", "/migration/execute",
                HttpMethod.POST)))
                .contains(new RequiredAuthorization("ProcessDefinition", 6, "MIGRATE_INSTANCE"));
    }

    @Test
    void heuristicMapsReadsCreatesAndDeletes() {
        assertThat(registry.requiredFor(route("getProcessDefinitions", "/process-definition",
                HttpMethod.GET)))
                .contains(new RequiredAuthorization("ProcessDefinition", 6, "READ"));

        assertThat(registry.requiredFor(route("createDeployment", "/deployment/create",
                HttpMethod.POST)))
                .contains(new RequiredAuthorization("Deployment", 9, "CREATE"));

        assertThat(registry.requiredFor(route("deleteProcessInstance", "/process-instance/{id}",
                HttpMethod.DELETE)))
                .contains(new RequiredAuthorization("ProcessInstance", 8, "DELETE"));
    }

    @Test
    void heuristicClassifiesPostQueriesAsReads() {
        assertThat(registry.requiredFor(route("queryProcessInstances", "/process-instance",
                HttpMethod.POST)))
                .contains(new RequiredAuthorization("ProcessInstance", 8, "READ"));
    }

    @Test
    void heuristicMapsHistoryReadsToReadHistoryOnProcessDefinition() {
        assertThat(registry.requiredFor(route("getHistoricProcessInstances",
                "/history/process-instance", HttpMethod.GET)))
                .contains(new RequiredAuthorization("ProcessDefinition", 6, "READ_HISTORY"));
    }

    @Test
    void leavesUnmappableOperationsUngated() {
        assertThat(registry.requiredFor(route("getRestAPIVersion", "/version", HttpMethod.GET)))
                .isEmpty();
    }

    private static HTTPRoute route(String operationId, String path, HttpMethod method) {
        HTTPRoute route = new HTTPRoute();
        route.setOperationId(operationId);
        route.setPath(path);
        route.setMethod(method);
        return route;
    }
}
