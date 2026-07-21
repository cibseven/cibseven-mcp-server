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

import java.util.Optional;

import org.cibseven.mcp.restapi.access.ToolAccessPolicy;
import org.cibseven.mcp.restapi.models.HTTPRoute;
import org.springframework.security.core.Authentication;

/**
 * CIB seven implementation of the library's {@link ToolAccessPolicy}: a tool is allowed when
 * the authenticated caller holds the engine authorization the operation requires.
 *
 * <p>Contributing this bean is what activates the library's tool-list filtering and
 * call-time guard. Operations for which no authorization can be determined are allowed
 * (fail-open); the {@link EngineAuthorizationService} is likewise fail-open, so the engine's
 * own authorization stays the ultimate gate.</p>
 */
public class CibSevenEngineToolAccessPolicy implements ToolAccessPolicy {

    private final OperationAuthorizationRegistry registry;
    private final EngineAuthorizationService engineAuthorizationService;

    public CibSevenEngineToolAccessPolicy(
            OperationAuthorizationRegistry registry,
            EngineAuthorizationService engineAuthorizationService) {
        this.registry = registry;
        this.engineAuthorizationService = engineAuthorizationService;
    }

    @Override
    public boolean isAllowed(Authentication authentication, HTTPRoute route) {
        Optional<RequiredAuthorization> required = registry.requiredFor(route);
        if (required.isEmpty()) {
            return true;
        }
        return engineAuthorizationService.isAuthorized(authentication, required.get());
    }
}
