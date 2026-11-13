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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Starts the repackaged server jar with {@code java -jar}, as the Docker image does, and uses
 * its MCP endpoint. Unlike {@link ApplicationSmokeTest}, it runs on the runtime classpath of
 * the jar only: a library that the server needs but gets only in test scope (such as
 * commons-io for reading a specification from a file) fails here.
 *
 * <p>A local stub serves the OAuth2 issuer metadata and the engine REST API.</p>
 */
class PackagedServerIT {

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(90);
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Authorization headers that reached the engine REST API stub. */
    private static final List<String> engineAuthorizations = new CopyOnWriteArrayList<>();

    private static HttpServer stub;
    private static Path specification;
    private static Path serverLog;
    private static Process server;
    private static String baseUrl;
    private static String token;

    @BeforeAll
    static void startServer() throws Exception {
        RSAKey key = new RSAKeyGenerator(2048).keyID("it").algorithm(JWSAlgorithm.RS256).generate();
        stub = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        String stubUrl = "http://localhost:" + stub.getAddress().getPort();
        byte[] metadata = ("{\"issuer\":\"" + stubUrl + "\",\"jwks_uri\":\"" + stubUrl + "/jwks\","
                + "\"authorization_endpoint\":\"" + stubUrl + "/authorize\","
                + "\"token_endpoint\":\"" + stubUrl + "/token\"}").getBytes(UTF_8);
        byte[] jwks = new JWKSet(key).toPublicJWKSet().toString().getBytes(UTF_8);
        stub.createContext("/.well-known/", exchange -> respond(exchange, metadata));
        stub.createContext("/jwks", exchange -> respond(exchange, jwks));
        stub.createContext("/engine-rest/", exchange -> {
            engineAuthorizations.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            respond(exchange, "{\"id\":\"42\",\"name\":\"Review invoice\"}".getBytes(UTF_8));
        });
        stub.start();

        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("it").build(),
                new JWTClaimsSet.Builder()
                        .issuer(stubUrl)
                        .subject("demo")
                        .claim("preferred_username", "demo")
                        .issueTime(new Date())
                        .expirationTime(new Date(System.currentTimeMillis() + 600_000))
                        .build());
        jwt.sign(new RSASSASigner(key));
        token = jwt.serialize();

        // as a file: reading a specification from a file needs commons-io at runtime
        specification = Files.createTempFile("fixture-openapi", ".json");
        try (InputStream in = PackagedServerIT.class.getResourceAsStream("/openapi/fixture-openapi.json")) {
            Files.copy(in, specification, StandardCopyOption.REPLACE_EXISTING);
        }

        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        baseUrl = "http://localhost:" + port;
        Path jar = Paths.get(System.getProperty("server.jar"));
        serverLog = jar.resolveSibling("packaged-server-it.log");
        String java = ProcessHandle.current().info().command().orElse("java");
        server = new ProcessBuilder(java, "-jar", jar.toString(),
                "--server.port=" + port,
                "--cibseven.openapi.url=" + specification,
                "--spring.security.oauth2.resourceserver.jwt.issuer-uri=" + stubUrl,
                "--cibseven.webclient.engineRest.url=" + stubUrl)
                .redirectErrorStream(true)
                .redirectOutput(serverLog.toFile())
                .start();
        waitUntilUp();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (server != null) {
            server.destroy();
            if (!server.waitFor(20, TimeUnit.SECONDS)) {
                server.destroyForcibly();
            }
        }
        if (stub != null) {
            stub.stop(0);
        }
        if (specification != null) {
            Files.deleteIfExists(specification);
        }
    }

    @Test
    void mcpEndpointNeedsAToken() throws Exception {
        HttpResponse<String> response = mcp(null, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}");

        assertThat(response.statusCode()).as(PackagedServerIT::serverLogTail).isEqualTo(401);
    }

    @Test
    void listsTheToolsOfTheSpecificationFile() throws Exception {
        HttpResponse<String> response = mcp(token, "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}");

        assertThat(response.statusCode()).as(PackagedServerIT::serverLogTail).isEqualTo(200);
        List<String> names = new ArrayList<>();
        JSON.readTree(response.body()).path("result").path("tools").forEach(tool -> names.add(tool.path("name").asString()));
        assertThat(names).contains("getTask", "createTask", "updateTask", "createDeployment");
    }

    @Test
    void callsTheEngineRestApiWithTheTokenOfTheCaller() throws Exception {
        HttpResponse<String> response = mcp(token, "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"getTask\",\"arguments\":{\"id\":\"42\"}}}");

        assertThat(response.statusCode()).as(PackagedServerIT::serverLogTail).isEqualTo(200);
        JsonNode result = JSON.readTree(response.body()).path("result");
        assertThat(result.path("isError").asBoolean()).as(response.body()).isFalse();
        assertThat(result.path("content").path(0).path("text").asString()).contains("Review invoice");
        assertThat(engineAuthorizations).contains("Bearer " + token);
    }

    private static HttpResponse<String> mcp(String bearer, String request) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(request));
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void waitUntilUp() throws Exception {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        HttpRequest health = HttpRequest.newBuilder(URI.create(baseUrl + "/actuator/health")).build();
        while (System.nanoTime() < deadline) {
            if (!server.isAlive()) {
                throw new IllegalStateException("The server jar stopped during startup:\n" + serverLogTail());
            }
            try {
                if (HTTP.send(health, HttpResponse.BodyHandlers.ofString()).statusCode() == 200) {
                    return;
                }
            } catch (IOException notYetListening) {
                // retry
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException("The server jar did not start within " + STARTUP_TIMEOUT + ":\n" + serverLogTail());
    }

    private static String serverLogTail() {
        try {
            List<String> lines = Files.readAllLines(serverLog, UTF_8);
            return String.join("\n", lines.subList(Math.max(0, lines.size() - 60), lines.size()));
        } catch (IOException e) {
            return "(no server log: " + e.getMessage() + ")";
        }
    }

    private static void respond(HttpExchange exchange, byte[] body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
