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

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The CIB seven authorization a caller must hold to invoke an operation: a
 * {@code (resource, permission)} pair, expressed with the engine's own resource name and
 * numeric resource type (see {@code org.cibseven.bpm.engine.authorization.Resources}) and
 * permission name (see {@code Permissions}).
 *
 * <p>These are exactly the {@code resourceName}, {@code resourceType} and
 * {@code permissionName} query parameters of engine-rest's
 * {@code GET /authorization/check} ({@code isUserAuthorized}).</p>
 */
public record RequiredAuthorization(
        @JsonProperty("resourceName") String resourceName,
        @JsonProperty("resourceType") int resourceType,
        @JsonProperty("permissionName") String permissionName) {
}
