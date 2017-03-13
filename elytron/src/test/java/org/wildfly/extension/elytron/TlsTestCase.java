/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
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
package org.wildfly.extension.elytron;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.Provider;
import java.security.Security;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;

import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.subsystem.test.AbstractSubsystemTest;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.security.WildFlyElytronProvider;

/**
 * @author <a href="mailto:jkalina@redhat.com">Jan Kalina</a>
 */
public class TlsTestCase extends AbstractSubsystemTest {

    private static final Provider wildFlyElytronProvider = new WildFlyElytronProvider();
    private static CredentialStoreUtility csUtil = null;

    private final int TESTING_PORT = 18201;

    public TlsTestCase() {
        super(ElytronExtension.SUBSYSTEM_NAME, new ElytronExtension());
    }

    private KernelServices services = null;


    @BeforeClass
    public static void initTests() {
        AccessController.doPrivileged((PrivilegedAction<Integer>) () -> Security.insertProviderAt(wildFlyElytronProvider, 1));
        csUtil = new CredentialStoreUtility("target/tlstest.keystore");
        csUtil.addEntry("the-key-alias", "Elytron");
        csUtil.addEntry("master-password-alias", "Elytron");
    }

    @AfterClass
    public static void cleanUpTests() {
        csUtil.cleanUp();
        AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
            Security.removeProvider(wildFlyElytronProvider.getName());
            return null;
        });
    }

    @Before
    public void prepare() throws Throwable {
        if (services != null) return;
        services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource("tls-test.xml").build();
        if (!services.isSuccessfulBoot()) {
            Assert.fail(services.getBootError().toString());
        }
    }


    @Test
    public void testSslServiceNoAuth() throws Throwable {
        testCommunication("ServerSslContextNoAuth", "ClientSslContextNoAuth", "OU=Elytron,O=Elytron,C=CZ,ST=Elytron,CN=localhost", null);
    }

    @Test
    public void testSslServiceAuth() throws Throwable {
        testCommunication("ServerSslContextAuth", "ClientSslContextAuth", "OU=Elytron,O=Elytron,C=CZ,ST=Elytron,CN=localhost", "OU=Elytron,O=Elytron,C=UK,ST=Elytron,CN=Firefly");
    }

    @Test(expected = SSLHandshakeException.class)
    public void testSslServiceAuthRequiredButNotProvided() throws Throwable {
        testCommunication("ServerSslContextAuth", "ClientSslContextNoAuth", "OU=Elytron,O=Elytron,C=UK,ST=Elytron,CN=Firefly", "");
    }

    @Test
    public void testProviderTrustManager() throws Throwable {
        ServiceName serviceName = Capabilities.TRUST_MANAGERS_RUNTIME_CAPABILITY.getCapabilityServiceName("ProviderTrustManager");
        TrustManager[] trustManagers = (TrustManager[]) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(trustManagers);
    }

    @Test
    public void testRevocationLists() throws Throwable {
        ServiceName serviceName = Capabilities.TRUST_MANAGERS_RUNTIME_CAPABILITY.getCapabilityServiceName("trust-with-crl");
        TrustManager[] trustManagers = (TrustManager[]) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(trustManagers);
    }

    @Test
    public void testRevocationListsDp() throws Throwable {
        ServiceName serviceName = Capabilities.TRUST_MANAGERS_RUNTIME_CAPABILITY.getCapabilityServiceName("trust-with-crl-dp");
        TrustManager[] trustManagers = (TrustManager[]) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(trustManagers);
    }

    private SSLContext getSslContext(String contextName) {
        ServiceName serviceName = Capabilities.SSL_CONTEXT_RUNTIME_CAPABILITY.getCapabilityServiceName(contextName);
        SSLContext sslContext = (SSLContext) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(sslContext);
        return sslContext;
    }

    private void testCommunication(String serverContextName, String clientContextName, String expectedServerPrincipal, String expectedClientPrincipal) throws Throwable {
        SSLContext serverContext = getSslContext(serverContextName);
        SSLContext clientContext = getSslContext(clientContextName);

        ServerSocket listeningSocket = serverContext.getServerSocketFactory().createServerSocket();
        listeningSocket.bind(new InetSocketAddress("localhost", TESTING_PORT));
        SSLSocket clientSocket = (SSLSocket) clientContext.getSocketFactory().createSocket("localhost", TESTING_PORT);
        SSLSocket serverSocket = (SSLSocket) listeningSocket.accept();

        ExecutorService serverExecutorService = Executors.newSingleThreadExecutor();
        Future<byte[]> serverFuture = serverExecutorService.submit(() -> {
            try {
                byte[] received = new byte[2];
                serverSocket.getInputStream().read(received);
                serverSocket.getOutputStream().write(new byte[]{0x56, 0x78});

                if (expectedClientPrincipal != null) {
                    Assert.assertEquals(expectedClientPrincipal, serverSocket.getSession().getPeerPrincipal().getName());
                }

                return received;
            } catch (Exception e) {
                throw new RuntimeException("Server exception", e);
            }
        });

        ExecutorService clientExecutorService = Executors.newSingleThreadExecutor();
        Future<byte[]> clientFuture = clientExecutorService.submit(() -> {
            try {
                byte[] received = new byte[2];
                clientSocket.getOutputStream().write(new byte[]{0x12, 0x34});
                clientSocket.getInputStream().read(received);

                if (expectedServerPrincipal != null) {
                    Assert.assertEquals(expectedServerPrincipal, clientSocket.getSession().getPeerPrincipal().getName());
                }

                return received;
            } catch (Exception e) {
                throw new RuntimeException("Client exception", e);
            }
        });

        try {
            Assert.assertArrayEquals(new byte[]{0x12, 0x34}, serverFuture.get());
            Assert.assertArrayEquals(new byte[]{0x56, 0x78}, clientFuture.get());
            testSessionsReading(serverContextName, clientContextName, expectedServerPrincipal, expectedClientPrincipal);
        } catch (ExecutionException e) {
            if (e.getCause() != null && e.getCause() instanceof RuntimeException && e.getCause().getCause() != null) {
                throw e.getCause().getCause(); // unpack
            } else {
                throw e;
            }
        } finally {
            serverSocket.close();
            clientSocket.close();
            listeningSocket.close();
        }
    }

    private void testSessionsReading(String serverContextName, String clientContextName, String expectedServerPrincipal, String expectedClientPrincipal) {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(ElytronDescriptionConstants.SERVER_SSL_CONTEXT, serverContextName);
        operation.get(ClientConstants.OP).set(ClientConstants.READ_ATTRIBUTE_OPERATION);
        operation.get(ClientConstants.NAME).set(ElytronDescriptionConstants.ACTIVE_SESSION_COUNT);
        Assert.assertEquals("active session count", 1, services.executeOperation(operation).get(ClientConstants.RESULT).asInt());

        operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(ElytronDescriptionConstants.SERVER_SSL_CONTEXT, serverContextName);
        operation.get(ClientConstants.OP).set(ClientConstants.READ_CHILDREN_NAMES_OPERATION);
        operation.get(ClientConstants.CHILD_TYPE).set(ElytronDescriptionConstants.SSL_SESSION);
        List<ModelNode> sessions = services.executeOperation(operation).get(ClientConstants.RESULT).asList();
        Assert.assertEquals("session count in list", 1, sessions.size());

        operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(ElytronDescriptionConstants.SERVER_SSL_CONTEXT, serverContextName).add(ElytronDescriptionConstants.SSL_SESSION, sessions.get(0).asString());
        operation.get(ClientConstants.OP).set(ClientConstants.READ_ATTRIBUTE_OPERATION);
        operation.get(ClientConstants.NAME).set(ElytronDescriptionConstants.PEER_CERTIFICATES);
        ModelNode result = services.executeOperation(operation).get(ClientConstants.RESULT);
        System.out.println("server's peer certificates:");
        System.out.println(result);
        if (expectedClientPrincipal == null) {
            Assert.assertFalse(result.get(0).get(ElytronDescriptionConstants.SUBJECT).isDefined());
        } else {
            Assert.assertEquals(expectedClientPrincipal, result.get(0).get(ElytronDescriptionConstants.SUBJECT).asString());
        }

        operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(ElytronDescriptionConstants.CLIENT_SSL_CONTEXT, clientContextName);
        operation.get(ClientConstants.OP).set(ClientConstants.READ_ATTRIBUTE_OPERATION);
        operation.get(ClientConstants.NAME).set(ElytronDescriptionConstants.ACTIVE_SESSION_COUNT);
        Assert.assertEquals("active session count", 1, services.executeOperation(operation).get(ClientConstants.RESULT).asInt());

        operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(ElytronDescriptionConstants.CLIENT_SSL_CONTEXT, clientContextName);
        operation.get(ClientConstants.OP).set(ClientConstants.READ_CHILDREN_NAMES_OPERATION);
        operation.get(ClientConstants.CHILD_TYPE).set(ElytronDescriptionConstants.SSL_SESSION);
        sessions = services.executeOperation(operation).get(ClientConstants.RESULT).asList();
        Assert.assertEquals("session count in list", 1, sessions.size());

        operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(ElytronDescriptionConstants.CLIENT_SSL_CONTEXT, clientContextName).add(ElytronDescriptionConstants.SSL_SESSION, sessions.get(0).asString());
        operation.get(ClientConstants.OP).set(ClientConstants.READ_ATTRIBUTE_OPERATION);
        operation.get(ClientConstants.NAME).set(ElytronDescriptionConstants.PEER_CERTIFICATES);
        result = services.executeOperation(operation).get(ClientConstants.RESULT);
        System.out.println("client's peer certificates:");
        System.out.println(result);
        if (expectedServerPrincipal == null) {
            Assert.assertFalse(result.get(0).get(ElytronDescriptionConstants.SUBJECT).isDefined());
        } else {
            Assert.assertEquals(expectedServerPrincipal, result.get(0).get(ElytronDescriptionConstants.SUBJECT).asString());
        }
    }
}