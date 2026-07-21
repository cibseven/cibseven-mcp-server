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

import java.io.IOException;
import java.util.Map;

import org.cibseven.mcp.auth.EngineRestAuthProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

class EngineAuthorizationServiceTest {

    private static final RequiredAuthorization CREATE_DEPLOYMENT =
            new RequiredAuthorization("Deployment", 9, "CREATE");

    private final Authentication alice =
            new TestingAuthenticationToken("alice", "creds", "ROLE_USER");

    private MockWebServer engine;
    private EngineAuthorizationService service;

    @BeforeEach
    void setUp() throws IOException {
        engine = new MockWebServer();
        engine.start();
        EngineRestAuthProvider stubAuth = authentication -> Map.of("Authorization", "Bearer t");
        service = new EngineAuthorizationService(
                engine.url("/engine-rest").toString(), stubAuth);
    }

    @AfterEach
    void tearDown() throws IOException {
        engine.shutdown();
    }

    @Test
    void returnsTrueWhenEngineReportsAuthorized() throws InterruptedException {
        engine.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"authorized\":true}"));

        assertThat(service.isAuthorized(alice, CREATE_DEPLOYMENT)).isTrue();

        RecordedRequest request = engine.takeRequest();
        assertThat(request.getPath())
                .contains("/engine-rest/authorization/check")
                .contains("permissionName=CREATE")
                .contains("resourceName=Deployment")
                .contains("resourceType=9");
    }

    @Test
    void returnsFalseWhenEngineReportsNotAuthorized() {
        engine.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"authorized\":false}"));

        assertThat(service.isAuthorized(alice, CREATE_DEPLOYMENT)).isFalse();
    }

    @Test
    void cachesTheDecisionPerSubjectResourceAndPermission() {
        engine.enqueue(new MockResponse().setBody("{\"authorized\":false}"));

        assertThat(service.isAuthorized(alice, CREATE_DEPLOYMENT)).isFalse();
        assertThat(service.isAuthorized(alice, CREATE_DEPLOYMENT)).isFalse();

        assertThat(engine.getRequestCount()).isEqualTo(1);
    }

    @Test
    void failsOpenOnEngineError() {
        engine.enqueue(new MockResponse().setResponseCode(500));

        assertThat(service.isAuthorized(alice, CREATE_DEPLOYMENT)).isTrue();
    }
}
