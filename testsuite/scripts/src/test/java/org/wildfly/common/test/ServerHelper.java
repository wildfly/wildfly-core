/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2020 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.common.test;

import static org.jboss.as.controller.client.helpers.ClientConstants.CONTROLLER_PROCESS_STATE_STARTING;
import static org.jboss.as.controller.client.helpers.ClientConstants.CONTROLLER_PROCESS_STATE_STOPPING;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class ServerHelper {
    public static final ModelNode EMPTY_ADDRESS = new ModelNode().setEmptyList();
    public static final int TIMEOUT = TimeoutUtil.adjust(Integer.parseInt(System.getProperty("jboss.test.start.timeout", "15")));
    public static final Path JBOSS_HOME;
    public static final String[] DEFAULT_SERVER_JAVA_OPTS = {
            "-Djboss.management.http.port=" + TestSuiteEnvironment.getServerPort(),
            "-Djboss.bind.address.management=" + TestSuiteEnvironment.getServerAddress(),
    };

    static {
        EMPTY_ADDRESS.protect();
        final String jbossHome = System.getProperty("jboss.home");

        if (isNullOrEmpty(jbossHome)) {
            throw new RuntimeException("Failed to configure environment. No jboss.home system property or JBOSS_HOME " +
                    "environment variable set.");
        }
        JBOSS_HOME = Paths.get(jbossHome).toAbsolutePath();
    }

    /**
     * Checks to see if a standalone server is running.
     *
     * @param client the client used to communicate with the server
     *
     * @return {@code true} if the server is running, otherwise {@code false}
     */
    public static boolean isStandaloneRunning(final ModelControllerClient client) {
        try {
            final ModelNode response = client.execute(Operations.createReadAttributeOperation(EMPTY_ADDRESS, "server-state"));
            if (Operations.isSuccessfulOutcome(response)) {
                final String state = Operations.readResult(response).asString();
                return !CONTROLLER_PROCESS_STATE_STARTING.equals(state)
                        && !CONTROLLER_PROCESS_STATE_STOPPING.equals(state);
            }
        } catch (RuntimeException | IOException ignore) {
        }
        return false;
    }

    public static List<JsonObject> readLogFileFromModel(final String logFileName) throws IOException {
        final ModelNode address = Operations.createAddress("subsystem", "logging", "log-file", logFileName);
        final ModelNode op = Operations.createReadAttributeOperation(address, "stream");
        try (ModelControllerClient client = TestSuiteEnvironment.getModelControllerClient()) {
            final OperationResponse response = client.executeOperation(Operation.Factory.create(op), OperationMessageHandler.logging);
            final ModelNode result = response.getResponseNode();
            if (Operations.isSuccessfulOutcome(result)) {
                final OperationResponse.StreamEntry entry = response.getInputStream(Operations.readResult(result).asString());
                if (entry == null) {
                    throw new RuntimeException(String.format("Failed to find entry with UUID %s for log file %s",
                            Operations.readResult(result).asString(), logFileName));
                }
                final List<JsonObject> lines = new ArrayList<>();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(entry.getStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        try (JsonReader jsonReader = Json.createReader(new StringReader(line))) {
                            lines.add(jsonReader.readObject());
                        }
                    }
                }
                return lines;
            }
            throw new RuntimeException(String.format("Failed to read log file %s: %s", logFileName, Operations.getFailureDescription(result).asString()));
        }
    }

    private static boolean isNullOrEmpty(final String value) {
        return value == null || value.trim().isEmpty();
    }
}
