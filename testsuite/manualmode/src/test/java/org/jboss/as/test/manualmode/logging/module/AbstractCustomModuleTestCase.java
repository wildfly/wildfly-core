/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.as.test.manualmode.logging.AbstractLoggingTestCase;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.AfterClass;
import org.junit.Assert;
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
        Assert.assertNotNull(container);
        Assert.assertTrue(container.isStarted()); // if container is not started, we get a NPE in container.getClient() within undeploy()
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
        Assert.assertNotNull(container);
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
