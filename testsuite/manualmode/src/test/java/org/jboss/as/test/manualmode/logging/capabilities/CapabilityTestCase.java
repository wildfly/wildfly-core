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

package org.jboss.as.test.manualmode.logging.capabilities;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import javax.inject.Inject;

import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.wildfly.core.testrunner.Parameter;
import org.wildfly.core.testrunner.Parameters;
import org.wildfly.core.testrunner.Server;
import org.wildfly.core.testrunner.ServerController;

/**
 * Tests that a configuration should fail to be configured in admin-only mode as they will fail to boot the server if
 * started normally.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings({"StaticVariableMayNotBeInitialized", "StaticVariableUsedBeforeInitialization"})
public class CapabilityTestCase {

    private static final Path CONFIG_DIR = Paths.get(TestSuiteEnvironment.getJBossHome(), "standalone", "configuration");
    private static final Path DFT_SERVER_CONFIG = CONFIG_DIR.resolve(System.getProperty("server.config", "standalone.xml"));
    private static final Path SERVER_CONFIG = CONFIG_DIR.resolve("standalone-logging.xml");

    static final String PROFILE_NAME = "test-profile";
    static final String HANDLER_NAME = "CONSOLE";
    static final String FORMATTER_NAME = "PATTERN";

    static final String NOT_FOUND = "WFLYCTL0369";
    static final String CANNOT_REMOVE = "WFLYCTL0367";

    @Parameter
    public String currentProfile;

    @Inject
    protected static ServerController container;

    @Parameters
    public static Collection<Object> data() {
        return Arrays.asList(null, PROFILE_NAME);
    }

    @BeforeClass
    public static void startAdminOnly() throws IOException {
        Assert.assertTrue("Could not locate default standalone configuration file: " + DFT_SERVER_CONFIG, Files.exists(DFT_SERVER_CONFIG));
        // Copy the server configuration
        Files.copy(DFT_SERVER_CONFIG, SERVER_CONFIG);
        container.start(SERVER_CONFIG.getFileName().toString(), Server.StartMode.ADMIN_ONLY);
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();
        // Add a logging profile
        builder.addStep(Operations.createAddOperation(createProfileSubsystemAddress(PROFILE_NAME)));

        // Add a default formatter
        builder.addStep(Operations.createAddOperation(createProfileSubsystemAddress(PROFILE_NAME, "pattern-formatter", FORMATTER_NAME)));

        // Add a handler to the profile
        ModelNode op = Operations.createAddOperation(createProfileSubsystemAddress(PROFILE_NAME, "console-handler", HANDLER_NAME));
        op.get("named-formatter").set(FORMATTER_NAME);
        builder.addStep(op);

        op = Operations.createAddOperation(createProfileSubsystemAddress(PROFILE_NAME, "root-logger", "ROOT"));
        op.get("handlers").setEmptyList().add(HANDLER_NAME);
        builder.addStep(op);
        executeOperation(builder.build());
    }

    @AfterClass
    public static void shutdownServer() throws IOException {
        try {
            if (container != null && container.isStarted()) {
                container.stop();
            }
        } finally {
            // Delete the server configuration
            Files.deleteIfExists(SERVER_CONFIG);
        }
    }

    void executeOperationForFailure(final ModelNode op, final String messageId) throws IOException {
        executeOperationForFailure(op, PatternPredicate.of(".*" + messageId + ".*"));
    }

    void executeOperationForFailure(final ModelNode op, final Predicate<String> predicate) throws IOException {
        executeOperationForFailure(Operation.Factory.create(op), predicate);
    }

    void executeOperationForFailure(final Operation op, final Predicate<String> predicate) throws IOException {
        Assert.assertNotNull(container);
        final ModelNode result = container.getClient().getControllerClient().execute(op);
        Assert.assertFalse("Expected the operation to fail: " + op, Operations.isSuccessfulOutcome(result));
        final String failureDescription = Operations.getFailureDescription(result).asString();
        Assert.assertTrue(String.format("Expected %s for the failure message found: %s", predicate, failureDescription),
                predicate.test(failureDescription));
    }

    ModelNode createSubsystemAddress(final String... parts) {
        return createProfileSubsystemAddress(currentProfile, parts);
    }

    static void executeOperation(final ModelNode op) throws IOException {
        executeOperation(Operation.Factory.create(op));
    }

    static void executeOperation(final Operation op) throws IOException {
        Assert.assertNotNull(container);
        final ModelNode result = container.getClient().getControllerClient().execute(op);
        if (!Operations.isSuccessfulOutcome(result)) {
            Assert.fail(Operations.getFailureDescription(result).asString());
        }
    }

    private static ModelNode createProfileSubsystemAddress(final String profile, final String... parts) {
        final Collection<String> addressParts = new ArrayList<>();
        addressParts.add("subsystem");
        addressParts.add("logging");
        if (profile != null) {
            addressParts.add("logging-profile");
            addressParts.add(profile);
        }
        Collections.addAll(addressParts, parts);
        return Operations.createAddress(addressParts);
    }

    @SuppressWarnings("SameParameterValue")
    static class PatternPredicate implements Predicate<String> {
        private final Pattern expectedPattern;
        private final Pattern unexpectedPattern;

        private PatternPredicate(final String expectedPattern, final String unexpectedPattern) {
            this.expectedPattern = expectedPattern == null ? null : Pattern.compile(expectedPattern, Pattern.DOTALL);
            this.unexpectedPattern = unexpectedPattern == null ? null : Pattern.compile(unexpectedPattern, Pattern.DOTALL);
        }

        static PatternPredicate of(final String expectedPattern) {
            return new PatternPredicate(expectedPattern, null);
        }

        static PatternPredicate of(final String expectedPattern, final String unexpectedPattern) {
            return new PatternPredicate(expectedPattern, unexpectedPattern);
        }

        @Override
        public boolean test(final String s) {
            if (expectedPattern != null && unexpectedPattern != null) {
                return expectedPattern.matcher(s).matches() && !unexpectedPattern.matcher(s).matches();
            } else if (expectedPattern != null) {
                return expectedPattern.matcher(s).matches();
            } else if (unexpectedPattern != null) {
                return !unexpectedPattern.matcher(s).matches();
            }
            return false;
        }

        @Override
        public String toString() {
            return "(expectedPattern[" + expectedPattern + "], unexpectedPattern[" + unexpectedPattern + "])";
        }
    }
}
