/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.jboss.as.cli.Util;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.dmr.ModelNode;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 *
 * @author jdenise
 */
public class BootScriptInvokerTestCase {

    private static class TestClient implements ModelControllerClient {

        private class TestOperationResponse implements OperationResponse {

            @Override
            public ModelNode getResponseNode() {
                return mn;
            }

            @Override
            public List<OperationResponse.StreamEntry> getInputStreams() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public OperationResponse.StreamEntry getInputStream(String arg0) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void close() throws IOException {
            }

        }

        private final Set<String> seen = new HashSet<>();
        private final ModelNode mn;

        private TestClient() {
            mn = new ModelNode();
            mn.get("outcome").set("success");
            mn.get("result").get("request-properties");
        }

        @Override
        public OperationResponse executeOperation(Operation op, OperationMessageHandler arg1) throws IOException {
            seen.add(op.getOperation().get("operation").asString());
            return new TestOperationResponse();
        }

        @Override
        public CompletableFuture<ModelNode> executeAsync(Operation op, OperationMessageHandler arg1) {
            seen.add(op.getOperation().get("operation").asString());
            return CompletableFuture.completedFuture(mn);
        }

        @Override
        public CompletableFuture<OperationResponse> executeOperationAsync(Operation arg0, OperationMessageHandler arg1) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public void close() throws IOException {
        }

    }

    @BeforeClass
    public static void init() {
    }

    @Test
    public void test() throws Exception {
        Path output = Files.createTempFile("cli-boot-output", ".txt");
        output.toFile().deleteOnExit();
        Path file = Files.createTempFile("cli-boot", ".txt");
        file.toFile().deleteOnExit();
        Path warnFile = Files.createTempFile("cli-boot-warn", ".txt");
        warnFile.toFile().deleteOnExit();
        Path errorFile = Files.createTempFile("cli-boot-error", ".txt");
        errorFile.toFile().deleteOnExit();
        Path fooFile = Files.createTempFile("cli-boot-foo", ".txt");
        fooFile.toFile().deleteOnExit();
        Path propertiesFile = Files.createTempFile("cli-boot-error", ".txt");
        propertiesFile.toFile().deleteOnExit();
        StringBuilder props = new StringBuilder();

        String propFoo = "tests.foo";
        WildFlySecurityManager.setPropertyPrivileged(propFoo, "foo");

        String propError = "tests.errors";
        String propWarning = "tests.warnings";

        props.append(propError + "=" + escapePath(errorFile.toString()) + "\n");
        props.append(propWarning + "=" + escapePath(warnFile.toString()) + "\n");
        props.append(propFoo + "=" + escapePath(fooFile.toString()) + "\n");
        Files.write(propertiesFile, props.toString().getBytes());
        WildFlySecurityManager.setPropertyPrivileged("org.wildfly.internal.cli.boot.hook.script.output.file", output.toString());
        WildFlySecurityManager.setPropertyPrivileged("org.wildfly.internal.cli.boot.hook.script.warn.file", warnFile.toString());
        WildFlySecurityManager.setPropertyPrivileged("org.wildfly.internal.cli.boot.hook.script.error.file", errorFile.toString());
        WildFlySecurityManager.setPropertyPrivileged("org.wildfly.internal.cli.boot.hook.script.properties", propertiesFile.toString());
        BootScriptInvoker invoker = new BootScriptInvoker();

        Set<String> operations = new HashSet<>();
        operations.add("op1");
        operations.add("op2");
        Set<String> echos = new LinkedHashSet<>();
        echos.add("echo TestWarning > ${" + propWarning + "}");
        echos.add("echo TestError > ${" + propError + "}");
        echos.add("echo TestFoo >> ${" + propFoo + "}");
        echos.add("echo TestFoo2 >> ${" + propFoo + "}");

        StringBuilder builder = new StringBuilder();
        for (String s : operations) {
            builder = builder.append(":" + s + "\n");
        }
        for (String s : echos) {
            builder = builder.append(s + "\n");
        }
        Files.write(file, builder.toString().getBytes());
        TestClient client = new TestClient();
        StringBuilder outContent = new StringBuilder();
        boolean failure = false;
        try {
            try {
                invoker.runCliScript(client, file.toFile());
                failure = true;
            } catch (Exception ex) {
                // Expected.
            }
            if (failure) {
                throw new Exception("Test should have failed");
            }
        } finally {
            for (String l : Files.readAllLines(output)) {
                outContent.append(l).append("\n");
                System.out.println(l);
            }
        }
        assertTrue(client.seen.containsAll(operations));
        assertTrue(outContent.toString().contains("success"));
        assertTrue(Files.readAllLines(warnFile).get(0).equals("TestWarning"));
        assertTrue(Files.readAllLines(errorFile).get(0).equals("TestError"));
        assertTrue(Files.readAllLines(fooFile).get(0).equals("TestFoo"));
        assertTrue(Files.readAllLines(fooFile).get(1).equals("TestFoo2"));

        // Properties have been cleared
        assertNull(WildFlySecurityManager.getPropertyPrivileged(propError, null));
        assertNull(WildFlySecurityManager.getPropertyPrivileged(propWarning, null));
        assertEquals("foo", WildFlySecurityManager.getPropertyPrivileged(propFoo, null));
    }

    @Test
    public void testFailure() throws Exception {
        Set<String> unavailable = new HashSet<>();
        unavailable.add("connect");
        unavailable.add("clear");
        unavailable.add("reload");
        unavailable.add("shutdown");
        unavailable.add("embed-server");
        unavailable.add("embed-host-controller");
        for (String cmd : unavailable) {
            BootScriptInvoker invoker = new BootScriptInvoker();
            Path file = Files.createTempFile("cli-boot", ".txt");
            file.toFile().deleteOnExit();
            Files.write(file, cmd.getBytes());
            TestClient client = new TestClient();
            boolean error = false;
            try {
                invoker.runCliScript(client, file.toFile());
                error = true;
            } catch (Exception ex) {
                // XXX OK expected
            }
            if (error) {
                throw new Exception(cmd + " should have failed");
            }
        }
    }

    private static String escapePath(String filePath) {
        if (Util.isWindows()) {
            StringBuilder builder = new StringBuilder();
            for (char c : filePath.toCharArray()) {
                if (c == '\\') {
                    builder.append('\\');
                }
                builder.append(c);
            }
            return builder.toString();
        } else {
            return filePath;
        }
    }
}
