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

import java.time.Duration;

import org.cibseven.mcp.auth.EngineRestAuthProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Answers "may this user perform {@code (permission)} on {@code (resource)}?" by delegating
 * to CIB seven engine-rest's {@code GET /authorization/check} ({@code isUserAuthorized}).
 *
 * <p>The check is performed <em>as the caller</em> — the outgoing request carries the same
 * {@link EngineRestAuthProvider} credentials the tool call would use (forwarded token or
 * freshly minted per-user JWT), so the engine evaluates the authenticated user's effective
 * grants (including groups, global grants and revokes). No elevated permission is required
 * and the engine's authorization logic is never re-implemented here.</p>
 *
 * <p>Decisions are cached per {@code (subject, resourceType, permission)} with a short TTL.
 * The check is <strong>fail-open</strong>: on any transport/parse error, or a non-2xx
 * response (e.g. a permission/resource combination the engine rejects), the operation is
 * treated as allowed and a warning is logged — the engine's own authorization remains the
 * real gate on the actual call, and failing open avoids blanking the tool list on a
 * transient hiccup. When engine authorization is disabled the check simply returns
 * {@code true}, so this whole layer becomes a no-op.</p>
 */
public class EngineAuthorizationService {

    private static final Logger logger =
            LoggerFactory.getLogger(EngineAuthorizationService.class);

    private final String checkUrl;
    private final EngineRestAuthProvider authProvider;
    private final OkHttpClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Cache<String, Boolean> decisionCache =
            Caffeine.newBuilder()
                    .maximumSize(50_000)
                    .expireAfterWrite(Duration.ofMinutes(5))
                    .build();

    public EngineAuthorizationService(String engineRestBaseUrl, EngineRestAuthProvider authProvider) {
        this.checkUrl = engineRestBaseUrl + "/authorization/check";
        this.authProvider = authProvider;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(10))
                .writeTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * @return {@code true} if the caller is authorized for the required permission (or the
     *         check could not be conclusively denied — see fail-open behaviour).
     */
    public boolean isAuthorized(Authentication authentication, RequiredAuthorization required) {
        String cacheKey = subjectOf(authentication)
                + "|" + required.resourceType() + "|" + required.permissionName();
        return Boolean.TRUE.equals(
                decisionCache.get(cacheKey, key -> check(authentication, required)));
    }

    private boolean check(Authentication authentication, RequiredAuthorization required) {
        HttpUrl base = HttpUrl.parse(checkUrl);
        if (base == null) {
            logger.warn("Cannot parse authorization-check URL '{}' — allowing (fail-open)", checkUrl);
            return true;
        }
        HttpUrl url = base.newBuilder()
                .addQueryParameter("permissionName", required.permissionName())
                .addQueryParameter("resourceName", required.resourceName())
                .addQueryParameter("resourceType", Integer.toString(required.resourceType()))
                .build();

        Request.Builder builder = new Request.Builder().url(url).get();
        authProvider.authHeaders(authentication).forEach(builder::header);

        try (Response response = client.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) {
                logger.warn("authorization/check returned {} for {} on {} — allowing (fail-open)",
                        response.code(), required.permissionName(), required.resourceName());
                return true;
            }
            ResponseBody body = response.body();
            if (body == null) {
                logger.warn("authorization/check returned empty body — allowing (fail-open)");
                return true;
            }
            JsonNode node = objectMapper.readTree(body.string());
            return node.path("authorized").asBoolean(true);
        }
        catch (Exception e) {
            logger.warn("authorization/check failed for {} on {} — allowing (fail-open): {}",
                    required.permissionName(), required.resourceName(), e.getMessage());
            return true;
        }
    }

    private static String subjectOf(Authentication authentication) {
        Jwt jwt = EngineRestAuthProvider.inboundJwt(authentication);
        if (jwt != null && jwt.getSubject() != null) {
            return jwt.getSubject();
        }
        return authentication != null ? authentication.getName() : "anonymous";
    }
}
