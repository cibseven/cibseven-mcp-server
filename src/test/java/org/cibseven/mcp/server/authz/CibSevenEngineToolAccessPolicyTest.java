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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.cibseven.mcp.restapi.models.HTTPRoute;
import org.cibseven.mcp.restapi.models.HttpMethod;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

class CibSevenEngineToolAccessPolicyTest {

    private final OperationAuthorizationRegistry registry = mock(OperationAuthorizationRegistry.class);
    private final EngineAuthorizationService engineAuthz = mock(EngineAuthorizationService.class);
    private final CibSevenEngineToolAccessPolicy policy =
            new CibSevenEngineToolAccessPolicy(registry, engineAuthz);

    private final Authentication alice =
            new TestingAuthenticationToken("alice", "creds", "ROLE_USER");

    @Test
    void allowsUnmappedOperationsWithoutConsultingTheEngine() {
        HTTPRoute route = route("getRestAPIVersion");
        when(registry.requiredFor(route)).thenReturn(Optional.empty());

        assertThat(policy.isAllowed(alice, route)).isTrue();
        verify(engineAuthz, never()).isAuthorized(any(), any());
    }

    @Test
    void delegatesMappedOperationsToTheEngineCheck() {
        HTTPRoute route = route("createDeployment");
        RequiredAuthorization required = new RequiredAuthorization("Deployment", 9, "CREATE");
        when(registry.requiredFor(route)).thenReturn(Optional.of(required));
        when(engineAuthz.isAuthorized(alice, required)).thenReturn(false);

        assertThat(policy.isAllowed(alice, route)).isFalse();
    }

    private static HTTPRoute route(String operationId) {
        HTTPRoute route = new HTTPRoute();
        route.setOperationId(operationId);
        route.setPath("/x");
        route.setMethod(HttpMethod.GET);
        return route;
    }
}
