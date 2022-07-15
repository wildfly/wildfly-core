/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2021 Red Hat, Inc., and individual contributors
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

package org.jboss.as.test.manualmode.logging.module;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.as.test.manualmode.logging.AbstractLoggingTestCase;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public abstract class AbstractCustomModuleTestCase extends AbstractLoggingTestCase {

    static final Deque<ModelNode> CLEANUP_OPS = new LinkedList<>();

    @AfterClass
    public static void cleanup() throws Exception {
        container.undeploy(DEPLOYMENT_NAME);
        try {
            final CompositeOperationBuilder builder = CompositeOperationBuilder.create();
            ModelNode op;
            while ((op = CLEANUP_OPS.poll()) != null) {
                builder.addStep(op);
            }
            executeOperation(builder.build());
        } finally {
            if (container.isStarted()) {
                container.stop();
            }
        }
    }

    @BeforeClass
    public static void startServer() {
        TestEnvironment.createModules();
        container.start();
    }

    static void deployApp() throws Exception {
        final JavaArchive jar = ShrinkWrap.create(JavaArchive.class, DEPLOYMENT_NAME)
                .addClass(LoggingDeploymentServiceActivator.class)
                .addAsServiceProvider(ServiceActivator.class, LoggingDeploymentServiceActivator.class);
        container.deploy(jar, DEPLOYMENT_NAME);
    }

    static List<JsonObject> readLogFile(final Path logFile) throws IOException {
        final List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        final List<JsonObject> result = new ArrayList<>();
        for (String line : lines) {
            try (JsonReader reader = Json.createReader(new StringReader(line))) {
                result.add(reader.readObject());
            }
        }
        return result;
    }
}
