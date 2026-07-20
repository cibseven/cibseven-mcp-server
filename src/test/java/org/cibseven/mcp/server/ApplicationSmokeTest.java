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

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * Boots the full application against a local fixture OpenAPI document (no network)
 * and verifies that the MCP tool set is built.
 */
@SpringBootTest(properties = {
        "cibseven.openapi.url=src/test/resources/openapi/fixture-openapi.json"
})
class ApplicationSmokeTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextStartsAndBuildsMcpTools() {
        assertThat(context.containsBean("getTools")).isTrue();
        List<?> tools = (List<?>) context.getBean("getTools");
        assertThat(tools).isNotEmpty();
    }
}
