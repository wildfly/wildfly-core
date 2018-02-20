/*
 * JBoss, Home of Professional Open Source
 * Copyright 2017, Red Hat, Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.test.integration.management.api;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.security.SecureRandom;
import java.util.Random;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.model.test.ChildFirstClassLoaderBuilder;
import org.jboss.as.process.protocol.StreamUtils;
import org.jboss.as.test.integration.management.ManagementOperations;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.ByteArrayAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;


/**
 * Base class to test supported remoting libraries combinations. It is in the shared module so it can be overridden
 * in both fill and core.
 *
 * @author Emanuel Muckenhuber
 */
public abstract class ClientCompatibilityUnitTestBase {
    protected abstract static class ClientCompatibilityServerSetup {

        public void setup(final ModelControllerClient client) throws Exception {

            ModelNode socketBindingOp = Util.createAddOperation(PathAddress.parseCLIStyleAddress("/socket-binding-group=standard-sockets/socket-binding=management-native"));
            socketBindingOp.get("interface").set("management");
            socketBindingOp.get("port").set("9999");

            ManagementOperations.executeOperation(client, socketBindingOp);

            // Determine the we should be using a security-realm or SASL
            String securityRealm = "ManagementRealm";
            String saslAuthFactory = null;
            ModelNode op = Operations.createReadResourceOperation(Operations.createAddress("core-service", "management", "management-interface", "http-interface"));
            ModelNode result = ManagementOperations.executeOperation(client, op);
            if (result.hasDefined("security-realm")) {
                securityRealm = result.get("security-realm").asString();
            } else if (result.hasDefined("http-upgrade")) {
                final ModelNode httpUpgrade = result.get("http-upgrade");
                if (httpUpgrade.hasDefined("sasl-authentication-factory")) {
                    saslAuthFactory = httpUpgrade.get("sasl-authentication-factory").asString();
                }
            }

            op = new ModelNode();
            op.get(ModelDescriptionConstants.OP_ADDR).set(address());
            op.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.ADD);
            op.get(ModelDescriptionConstants.SOCKET_BINDING).set("management-native");

            if (saslAuthFactory != null) {
                op.get("sasl-authentication-factory").set(saslAuthFactory);
            } else {
                op.get(ModelDescriptionConstants.SECURITY_REALM).set(securityRealm);
            }
            ManagementOperations.executeOperation(client, op);
        }

        public void tearDown(final ModelControllerClient client) throws Exception {
            ModelNode op = new ModelNode();
            op.get(ModelDescriptionConstants.OP_ADDR).set(address());
            op.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.REMOVE);
            ManagementOperations.executeOperation(client, op);

            ManagementOperations.executeOperation(client,
                    Util.createRemoveOperation(PathAddress.parseCLIStyleAddress("/socket-binding-group=standard-sockets/socket-binding=management-native")));

            ServerReload.executeReloadAndWaitForCompletion(client);
        }

        private ModelNode address() {
            return PathAddress.pathAddress()
                    .append(ModelDescriptionConstants.CORE_SERVICE, ModelDescriptionConstants.MANAGEMENT)
                    .append(ModelDescriptionConstants.MANAGEMENT_INTERFACE, ModelDescriptionConstants.NATIVE_INTERFACE).toModelNode();
        }
    }

    protected static final String CONTROLLER_ADDRESS = System.getProperty("node0", "localhost");

    protected static final String WF_CLIENT = "org.wildfly:wildfly-controller-client";
    protected static final String WFCORE_CLIENT = "org.wildfly.core:wildfly-controller-client";
    protected static final String AS7_CLIENT = "org.jboss.as:jboss-as-controller-client";

    protected static final String[] excludes = new String[]{"org.jboss.threads:jboss-threads", "org.jboss:jboss-dmr", "org.jboss.logging:jboss-logging"};
    protected static final Archive deployment;

    static {
        final WebArchive archive = ShrinkWrap.create(WebArchive.class);
        // Create basic archive which exceeds the remoting window size
        for (int i = 0; i < 10; i++) {
            final byte[] data = new byte[8096];
            new Random(new SecureRandom().nextLong()).nextBytes(data);
            archive.add(new ByteArrayAsset(data), "data" + i);
        }
        deployment = archive;
    }


    @Test
    public void test711Final() throws Exception {
        testAS7("7.1.1.Final");
    }

    @Test
    public void test720Final() throws Exception {
        testAS7("7.2.0.Final");
    }

    @Test
    public void test800Final() throws Exception {
        test(createClient(WF_CLIENT, "8.0.0.Final", CONTROLLER_ADDRESS, 9999));
    }

    @Ignore("http upgrade compat issue")
    @Test
    public void test800FinalHttp() throws Exception {
        test(createClient(WF_CLIENT, "8.0.0.Final", CONTROLLER_ADDRESS, 9990));
    }

    @Test
    public void test810Final() throws Exception {
        test(createClient(WF_CLIENT, "8.1.0.Final", CONTROLLER_ADDRESS, 9999));
    }

    @Ignore("http upgrade compat issue")
    @Test
    public void test810FinalHttp() throws Exception {
        test(createClient(WF_CLIENT, "8.1.0.Final", CONTROLLER_ADDRESS, 9990));
    }

    @Test
    public void test820Final() throws Exception {
        test(createClient(WF_CLIENT, "8.2.0.Final", CONTROLLER_ADDRESS, 9999));
    }

    @Ignore("http upgrade compat issue")
    @Test
    public void test820FinalHttp() throws Exception {
        test(createClient(WF_CLIENT, "8.2.0.Final", CONTROLLER_ADDRESS, 9990));
    }

    @Test
    public void test821Final() throws Exception {
        test(createClient(WF_CLIENT, "8.2.1.Final", CONTROLLER_ADDRESS, 9999));
    }

    @Ignore("http upgrade compat issue")
    @Test
    public void test821FinalHttp() throws Exception {
        test(createClient(WF_CLIENT, "8.2.1.Final", CONTROLLER_ADDRESS, 9990));
    }

    @Test
    public void testCore100Final() throws Exception {
        testWF("1.0.0.Final", 9999);
    }

    @Test
    public void testCore100FinalHttp() throws Exception {
        testWF("1.0.0.Final", 9990);
    }

    @Test
    public void testCore101Final() throws Exception {
        testWF("1.0.1.Final", 9999);
    }

    @Test
    public void testCore101FinalHttp() throws Exception {
        testWF("1.0.1.Final", 9990);
    }

    @Test
    public void testCore210Final() throws Exception {
        testWF("2.1.0.Final", 9999);
    }

    @Test
    public void testCore210FinalHttp() throws Exception {
        testWF("2.1.0.Final", 9990);
    }

    @Test
    public void testCore221Final() throws Exception {
        testWF("2.2.1.Final", 9999);
    }

    @Test
    public void testCore221FinalHttp() throws Exception {
        testWF("2.2.1.Final", 9990);
    }

    @Test
    public void testCore3010Final() throws Exception {
        testWF("3.0.10.Final", 9999);
    }

    @Test
    public void testCore3010FinalHttp() throws Exception {
        testWF("3.0.10.Final", 9990);
    }

    @Test
    public void testCurrent() throws Exception {
        test(ModelControllerClient.Factory.create(CONTROLLER_ADDRESS, 9999));
    }

    @Test
    public void testCurrentHttp() throws Exception {
        test(ModelControllerClient.Factory.create(CONTROLLER_ADDRESS, 9990));
    }

    protected void  testAS7(final String version) throws Exception {
        test(createClient(AS7_CLIENT, version, CONTROLLER_ADDRESS, 9999));
    }

    protected void testWF(final String version, int port) throws Exception {
        test(createClient(WFCORE_CLIENT, version, CONTROLLER_ADDRESS, port));
    }

    protected void test(final ModelControllerClient client) throws Exception {
        try {
            final ModelNode operation = new ModelNode();
            operation.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.READ_RESOURCE_OPERATION);
            operation.get(ModelDescriptionConstants.OP_ADDR).setEmptyList();
            // include a lot of garbage
            operation.get(ModelDescriptionConstants.RECURSIVE).set(true);
            operation.get(ModelDescriptionConstants.INCLUDE_RUNTIME).set(true);
            operation.get(ModelDescriptionConstants.INCLUDE_DEFAULTS).set(true);
            final ModelNode result = client.execute(operation);
            Assert.assertEquals(operation.toString(), ModelDescriptionConstants.SUCCESS, result.get(ModelDescriptionConstants.OUTCOME).asString());

            final ModelNode deploy = new ModelNode();
            deploy.get(ModelDescriptionConstants.OP).set("add");
            deploy.get(ModelDescriptionConstants.OP_ADDR).add("deployment", "compat-test.war");
            deploy.get("content").get(0).get("input-stream-index").set(0);
            deploy.get("auto-start").set(true);

            final Operation o = OperationBuilder.create(deploy)
                    .addInputStream(deployment.as(ZipExporter.class).exportAsInputStream()).build();

            try {
                final ModelNode deployResult = client.execute(o);
                Assert.assertEquals(deployResult.toString(), ModelDescriptionConstants.SUCCESS, deployResult.get(ModelDescriptionConstants.OUTCOME).asString());

            } finally {
                final ModelNode undeploy = new ModelNode();
                undeploy.get(ModelDescriptionConstants.OP).set("remove");
                undeploy.get(ModelDescriptionConstants.OP_ADDR).add("deployment", "compat-test.war");

                try {
                    client.execute(undeploy);
                } catch (IOException ignore) {
                    ignore.printStackTrace();
                }

            }
        } finally {
            StreamUtils.safeClose(client);
        }
    }

    protected static ModelControllerClient createClient(final String artifact, final String version, final String host, final int port) throws Exception {

        final ChildFirstClassLoaderBuilder classLoaderBuilder = new ChildFirstClassLoaderBuilder(false);
        classLoaderBuilder.addRecursiveMavenResourceURL(artifact + ":" + version, excludes);
        classLoaderBuilder.addParentFirstClassPattern("org.jboss.as.controller.client.ModelControllerClient");
        classLoaderBuilder.addParentFirstClassPattern("org.jboss.as.controller.client.OperationMessageHandler");
        classLoaderBuilder.addParentFirstClassPattern("org.jboss.as.controller.client.Operation");
        classLoaderBuilder.addParentFirstClassPattern("org.jboss.as.controller.client.OperationResponse*");
        classLoaderBuilder.addParentFirstClassPattern("org.jboss.as.protocol.logging.ProtocolLogger*");

        final ClassLoader classLoader = classLoaderBuilder.build();
        final Class<?> factoryClass = classLoader.loadClass("org.jboss.as.controller.client.ModelControllerClient$Factory");
        final Method factory = factoryClass.getMethod("create", String.class, int.class);
        try {
            final Object client = factory.invoke(null, host, port);

            final InvocationHandler invocationHandler = new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    return method.invoke(client, args);
                }
            };
            final Class<?>[] interfaces = new Class<?>[]{ModelControllerClient.class};
            return (ModelControllerClient) Proxy.newProxyInstance(classLoader, interfaces, invocationHandler);
        } catch (InvocationTargetException e) {
            Throwable t = e.getCause();
            if (t == null) {
                throw e;
            }
            throw t instanceof Exception ? (Exception) t : new RuntimeException(t);
        }
    }

}
