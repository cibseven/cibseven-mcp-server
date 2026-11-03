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

import org.cibseven.mcp.auth.EngineRestAuthProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the CIB seven authorization-aware tool-access policy — the guardrail that hides and
 * blocks tools the authenticated user is not permitted to invoke.
 *
 * <p>Gated on {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}: the guardrail is
 * meaningful only when the MCP endpoint is OAuth2-protected and callers carry an identity
 * (the same condition that turns on inbound protection in the library). When it is not set
 * no policy bean exists, and the library's filtering plumbing stays dormant — every tool is
 * exposed exactly as before.</p>
 *
 * <p>Publishing {@link CibSevenEngineToolAccessPolicy} as a {@code ToolAccessPolicy} bean is
 * what activates the library's {@code tools/list} filter and {@code tools/call} guard.</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.security.oauth2.resourceserver.jwt.issuer-uri")
public class AuthzPolicyConfiguration {

    @Bean
    public OperationAuthorizationRegistry operationAuthorizationRegistry() {
        return new OperationAuthorizationRegistry();
    }

    @Bean
    public EngineAuthorizationService engineAuthorizationService(
            @Value("${cibseven.webclient.engineRest.url:http://localhost:8080}") String hostUrl,
            @Value("${cibseven.webclient.engineRest.path:/engine-rest}") String engineRestPath,
            EngineRestAuthProvider authProvider) {

        String sanitizedPath = engineRestPath.replaceAll("^/+|/+$", "");
        String baseUrl = (hostUrl.endsWith("/") ? hostUrl : hostUrl + "/") + sanitizedPath;
        return new EngineAuthorizationService(baseUrl, authProvider);
    }

    @Bean
    public CibSevenEngineToolAccessPolicy cibSevenEngineToolAccessPolicy(
            OperationAuthorizationRegistry operationAuthorizationRegistry,
            EngineAuthorizationService engineAuthorizationService) {
        return new CibSevenEngineToolAccessPolicy(
                operationAuthorizationRegistry, engineAuthorizationService);
    }
}
