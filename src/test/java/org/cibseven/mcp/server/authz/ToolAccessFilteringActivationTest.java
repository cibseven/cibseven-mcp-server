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

import java.util.List;

import org.cibseven.mcp.restapi.access.AccessFilteringStatelessTransport;
import org.cibseven.mcp.restapi.access.ToolAccessPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import io.modelcontextprotocol.spec.McpStatelessServerTransport;

/**
 * Boots the full application with a {@link ToolAccessPolicy} bean present (as the real
 * gated configuration would) and verifies that the library's access-filtering transport
 * supersedes Spring AI's default one, the router function is still served, and the tool set
 * is still built — i.e. the whole wiring activates cleanly, without bean conflicts.
 */
@SpringBootTest(properties = {
        "cibseven.openapi.url=src/test/resources/openapi/fixture-openapi.json"
})
@Import(ToolAccessFilteringActivationTest.StubPolicyConfiguration.class)
class ToolAccessFilteringActivationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void filteringTransportIsWiredWhenAPolicyIsPresent() {
        // The @Primary transport the MCP server binds to is our filtering wrapper.
        assertThat(context.getBean(McpStatelessServerTransport.class))
                .isInstanceOf(AccessFilteringStatelessTransport.class);

        // The HTTP router function bean is still provided (served by the real delegate).
        assertThat(context.containsBean("webMvcStatelessServerRouterFunction")).isTrue();

        // Tools are still built.
        assertThat((List<?>) context.getBean("getTools")).isNotEmpty();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class StubPolicyConfiguration {

        @Bean
        ToolAccessPolicy toolAccessPolicy() {
            return (authentication, route) -> true;
        }
    }
}
