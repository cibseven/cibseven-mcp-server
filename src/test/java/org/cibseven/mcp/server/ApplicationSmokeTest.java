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
package org.cibseven.mcp.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.sun.net.httpserver.HttpServer;

/**
 * Boots the full application against a local fixture OpenAPI document (no network)
 * and verifies that the MCP tool set is built. Spring Security resolves the OAuth2
 * issuer metadata at startup, so a local stub serves it.
 */
@SpringBootTest(properties = {
        "cibseven.openapi.url=src/test/resources/openapi/fixture-openapi.json"
})
class ApplicationSmokeTest {

    private static HttpServer issuer;

    @DynamicPropertySource
    static void issuerUri(DynamicPropertyRegistry registry) throws Exception {
        issuer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        String base = "http://localhost:" + issuer.getAddress().getPort();
        byte[] metadata = ("{\"issuer\":\"" + base + "\",\"jwks_uri\":\"" + base + "/jwks\","
                + "\"authorization_endpoint\":\"" + base + "/authorize\","
                + "\"token_endpoint\":\"" + base + "/token\"}").getBytes(StandardCharsets.UTF_8);
        byte[] jwks = new JWKSet(new RSAKeyGenerator(2048).keyID("test").algorithm(JWSAlgorithm.RS256)
                .generate()).toPublicJWKSet().toString().getBytes(StandardCharsets.UTF_8);
        issuer.createContext("/", exchange -> {
            byte[] body = exchange.getRequestURI().getPath().endsWith("/jwks") ? jwks : metadata;
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        issuer.start();
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> base);
    }

    @AfterAll
    static void stopIssuer() {
        issuer.stop(0);
    }

    @Autowired
    private ApplicationContext context;

    @Test
    void contextStartsAndBuildsMcpTools() {
        assertThat(context.containsBean("getTools")).isTrue();
        List<?> tools = (List<?>) context.getBean("getTools");
        assertThat(tools).isNotEmpty();
    }
}
