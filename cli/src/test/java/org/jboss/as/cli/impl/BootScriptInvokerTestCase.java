/*
Copyright 2019 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package org.jboss.as.cli.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.dmr.ModelNode;
import org.jboss.threads.AsyncFuture;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 *
 * @author jdenise
 */
public class BootScriptInvokerTestCase {

    private class TestClient implements ModelControllerClient {

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

        private class TestAsync implements AsyncFuture<ModelNode> {

            @Override
            public AsyncFuture.Status await() throws InterruptedException {
                return AsyncFuture.Status.COMPLETE;
            }

            @Override
            public AsyncFuture.Status await(long arg0, TimeUnit arg1) throws InterruptedException {
                return AsyncFuture.Status.COMPLETE;
            }

            @Override
            public ModelNode getUninterruptibly() throws CancellationException, ExecutionException {
                return mn;
            }

            @Override
            public ModelNode getUninterruptibly(long arg0, TimeUnit arg1) throws CancellationException, ExecutionException, TimeoutException {
                return mn;
            }

            @Override
            public AsyncFuture.Status awaitUninterruptibly() {
                return AsyncFuture.Status.COMPLETE;
            }

            @Override
            public AsyncFuture.Status awaitUninterruptibly(long arg0, TimeUnit arg1) {
                return AsyncFuture.Status.COMPLETE;
            }

            @Override
            public AsyncFuture.Status getStatus() {
                return AsyncFuture.Status.COMPLETE;
            }

            @Override
            public <A> void addListener(AsyncFuture.Listener<? super ModelNode, A> arg0, A arg1) {

            }

            @Override
            public boolean cancel(boolean arg0) {
                return true;
            }

            @Override
            public void asyncCancel(boolean arg0) {
            }

            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public boolean isDone() {
                return true;
            }

            @Override
            public ModelNode get() throws InterruptedException, ExecutionException {
                return mn;
            }

            @Override
            public ModelNode get(long arg0, TimeUnit arg1) throws InterruptedException, ExecutionException, TimeoutException {
                return mn;
            }
        }

        private Set<String> seen = new HashSet<>();
        private ModelNode mn;

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
        public AsyncFuture<ModelNode> executeAsync(Operation op, OperationMessageHandler arg1) {
            seen.add(op.getOperation().get("operation").asString());
            return new TestAsync();
        }

        @Override
        public AsyncFuture<OperationResponse> executeOperationAsync(Operation arg0, OperationMessageHandler arg1) {
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

        props.append(propError + "=" + errorFile.toString() + "\n");
        props.append(propWarning + "=" + warnFile.toString() + "\n");
        props.append(propFoo + "=" + fooFile.toString() + "\n");
        Files.write(propertiesFile, props.toString().getBytes());
        WildFlySecurityManager.setPropertyPrivileged("org.wildfly.internal.cli.boot.hook.script.output.file", output.toString());
        WildFlySecurityManager.setPropertyPrivileged("org.wildfly.internal.cli.boot.hook.script.warn.file", warnFile.toString());
        WildFlySecurityManager.setPropertyPrivileged("org.wildfly.internal.cli.boot.hook.script.error.file", errorFile.toString());
        WildFlySecurityManager.setPropertyPrivileged("org.wildfly.internal.cli.boot.hook.script.properties", propertiesFile.toString());
        BootScriptInvoker invoker = new BootScriptInvoker();

        Set<String> operations = new HashSet<>();
        operations.add("op1");
        operations.add("op2");
        Set<String> echos = new HashSet<>();
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
        try {
            invoker.runCliScript(client, file.toFile());
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
}
