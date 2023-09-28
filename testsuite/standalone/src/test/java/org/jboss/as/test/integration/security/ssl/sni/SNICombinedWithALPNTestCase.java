/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.security.ssl.sni;

import static org.jboss.as.controller.client.helpers.ClientConstants.CONTENT;
import static org.jboss.as.controller.client.helpers.ClientConstants.DEPLOYMENT;
import static org.jboss.as.controller.client.helpers.Operations.createAddOperation;
import static org.jboss.as.controller.client.helpers.Operations.createAddress;
import static org.jboss.as.controller.client.helpers.Operations.createRemoveOperation;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilePermission;
import java.io.IOException;
import java.lang.reflect.ReflectPermission;
import java.math.BigInteger;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.CompletableFuture;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.security.auth.x500.X500Principal;

import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentHelper;
import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.as.test.shared.PermissionUtils;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.ServerSetupTask;
import org.wildfly.core.testrunner.WildFlyRunner;
import org.wildfly.security.x500.GeneralName;
import org.wildfly.security.x500.cert.SubjectAlternativeNamesExtension;
import org.wildfly.security.x500.cert.X509CertificateBuilder;
import org.wildfly.test.undertow.UndertowSSLService;
import org.wildfly.test.undertow.UndertowSSLServiceActivator;
import org.wildfly.test.undertow.UndertowServiceActivator;
import org.xnio.OptionMap;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.ssl.XnioSsl;

import io.undertow.UndertowOptions;
import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientExchange;
import io.undertow.client.ClientRequest;
import io.undertow.client.UndertowClient;
import io.undertow.protocols.ssl.UndertowXnioSsl;
import io.undertow.server.DefaultByteBufferPool;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import io.undertow.util.Protocols;
import io.undertow.util.StringReadChannelListener;

@RunWith(WildFlyRunner.class)
@ServerSetup(SNICombinedWithALPNTestCase.Setup.class)
public class SNICombinedWithALPNTestCase {

    public static final String SHA_256_WITH_RSA = "SHA256withRSA";
    private static final String ALIAS = "server";
    private static final String PASSWORD = "password";
    private static final String[] HOST_KEY_STORE = {"subsystem", "elytron", "key-store", "host"};
    private static final String[] IP_KEY_STORE = {"subsystem", "elytron", "key-store", "ip"};
    private static final String[] HOST_KEY_MANAGER = {"subsystem", "elytron", "key-manager", "host"};
    private static final String[] IP_KEY_MANAGER = {"subsystem", "elytron", "key-manager", "ip"};
    private static final String[] HOST_SSL_CONTEXT = {"subsystem", "elytron", "server-ssl-context", "host"};
    private static final String[] IP_SSL_CONTEXT = {"subsystem", "elytron", "server-ssl-context", "ip"};
    private static final String[] SNI_SSL_CONTEXT = {"subsystem", "elytron", "server-ssl-sni-context", "test-context"};
    private static final String TEST_JAR = "test.jar";

    private static File hostNameKeystore;
    private static File ipKeystore;

    private static boolean assumptionsSatisfied = true;

    static class Setup implements ServerSetupTask {

        @Override
        public void setup(ManagementClient managementClient) throws Exception {
            if (!canHostAddressBeTranslated()) {
                // This is checked by Assumption in the beforeClass() method, but needs to be handled also here because
                // the setup runs before the beforeClass() method.
                assumptionsSatisfied = false;
                return;
            }

            InetAddress[] addresses = InetAddress.getAllByName(TestSuiteEnvironment.getHttpAddress());
            String hostname = addresses[0].getHostName();

            hostNameKeystore = File.createTempFile("test", ".keystore");
            ipKeystore = File.createTempFile("test", ".keystore");

            generateFileKeyStore(hostNameKeystore, hostname, null);
            generateFileKeyStore(ipKeystore, hostname, addresses);

            ModelNode credential = new ModelNode();
            credential.get("clear-text").set("password");

            ModelNode modelNode = createAddOperation(createAddress(HOST_KEY_STORE));
            modelNode.get("type").set("jks");
            modelNode.get("path").set(hostNameKeystore.getAbsolutePath());
            modelNode.get("credential-reference").set(credential);
            managementClient.executeForResult(modelNode);

            modelNode = createAddOperation(createAddress(IP_KEY_STORE));
            modelNode.get("type").set("jks");
            modelNode.get("path").set(ipKeystore.getAbsolutePath());
            modelNode.get("credential-reference").set(credential);
            managementClient.executeForResult(modelNode);

            modelNode = createAddOperation(createAddress(HOST_KEY_MANAGER));
            modelNode.get("algorithm").set(keyAlgorithm());
            modelNode.get("key-store").set("host");
            modelNode.get("credential-reference").set(credential);
            managementClient.executeForResult(modelNode);

            modelNode = createAddOperation(createAddress(IP_KEY_MANAGER));
            modelNode.get("algorithm").set(keyAlgorithm());
            modelNode.get("key-store").set("ip");
            modelNode.get("credential-reference").set(credential);
            managementClient.executeForResult(modelNode);

            modelNode = createAddOperation(createAddress(HOST_SSL_CONTEXT));
            modelNode.get("key-manager").set("host");
            modelNode.get("protocols").set(new ModelNode().add("TLSv1.2"));
            managementClient.executeForResult(modelNode);

            modelNode = createAddOperation(createAddress(IP_SSL_CONTEXT));
            modelNode.get("key-manager").set("ip");
            modelNode.get("protocols").set(new ModelNode().add("TLSv1.2"));
            managementClient.executeForResult(modelNode);

            modelNode = createAddOperation(createAddress(SNI_SSL_CONTEXT));
            modelNode.get("default-ssl-context").set("ip");
            ModelNode hostContextMap = new ModelNode();
            hostContextMap.get(hostname).set("host");
            modelNode.get("host-context-map").set(hostContextMap);
            managementClient.executeForResult(modelNode);

            JavaArchive jar = ShrinkWrap.create(JavaArchive.class, TEST_JAR)
                    .addClasses(UndertowServiceActivator.DEPENDENCIES)
                    .addClasses(UndertowSSLService.class)
                    .addAsResource(new StringAsset("Dependencies: io.undertow.core"), "META-INF/MANIFEST.MF")
                    .addAsManifestResource(PermissionUtils.createPermissionsXmlAsset(UndertowServiceActivator.appendPermissions(new FilePermission("<<ALL FILES>>", "read"),
                            new RuntimePermission("getClassLoader"),
                            new RuntimePermission("accessDeclaredMembers"),
                            new RuntimePermission("accessClassInPackage.sun.security.ssl"),
                            new ReflectPermission("suppressAccessChecks"))), "permissions.xml")
                    .addAsServiceProviderAndClasses(ServiceActivator.class, UndertowSSLServiceActivator.class);
            deploy(jar, managementClient);

            ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());
        }

        @Override
        public void tearDown(ManagementClient managementClient) throws Exception {
            if (!assumptionsSatisfied) {
                // No setup has been done if assumptions failed.
                return;
            }

            hostNameKeystore.delete();
            ipKeystore.delete();

            managementClient.executeForResult(createRemoveOperation(createAddress(SNI_SSL_CONTEXT)));
            managementClient.executeForResult(createRemoveOperation(createAddress(IP_SSL_CONTEXT)));
            managementClient.executeForResult(createRemoveOperation(createAddress(HOST_SSL_CONTEXT)));
            managementClient.executeForResult(createRemoveOperation(createAddress(IP_KEY_MANAGER)));
            managementClient.executeForResult(createRemoveOperation(createAddress(HOST_KEY_MANAGER)));
            managementClient.executeForResult(createRemoveOperation(createAddress(IP_KEY_STORE)));
            managementClient.executeForResult(createRemoveOperation(createAddress(HOST_KEY_STORE)));
            undeploy(managementClient, TEST_JAR);
            ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());

        }
    }

    @BeforeClass
    public static void beforeClass() throws UnknownHostException {
        Assume.assumeTrue("Assuming the test if no resolution for the http address",
                canHostAddressBeTranslated());
    }

    private static boolean canHostAddressBeTranslated() throws UnknownHostException {
        InetAddress address = InetAddress.getByName(TestSuiteEnvironment.getHttpAddress());
        return !address.getHostName().equals(address.getHostAddress());
    }

    @Test
    public void testSimpleViaHostname() throws Exception {
        InetAddress address = InetAddress.getByName(TestSuiteEnvironment.getHttpAddress());
        String hostname = address.getHostName();
        XnioSsl ssl = createClientSSL(hostNameKeystore);
        UndertowClient client = UndertowClient.getInstance();
        DefaultByteBufferPool pool = new DefaultByteBufferPool(false, 1024);
        ClientConnection connection = client.connect(new URI("https", null, hostname, TestSuiteEnvironment.getHttpPort(), "", null, null), XnioWorker.getContextManager().get(), ssl, pool, OptionMap.create(UndertowOptions.ENABLE_HTTP2, true)).get();
        performSimpleTest(pool, connection, hostname);
    }

    @Test
    public void testHttpsViaIp() throws Exception {
        InetAddress address = InetAddress.getByName(TestSuiteEnvironment.getHttpAddress());
        String hostname = address instanceof Inet6Address? "[" + address.getHostAddress() + "]" : address.getHostAddress();
        XnioSsl ssl = createClientSSL(ipKeystore);
        UndertowClient client = UndertowClient.getInstance();
        DefaultByteBufferPool pool = new DefaultByteBufferPool(false, 1024);
        ClientConnection connection = client.connect(new URI("https", null, hostname, TestSuiteEnvironment.getHttpPort(), "", null, null), XnioWorker.getContextManager().get(), ssl, pool, OptionMap.create(UndertowOptions.ENABLE_HTTP2, true)).get();
        performSimpleTest(pool, connection, hostname);
    }

    private void performSimpleTest(DefaultByteBufferPool pool, ClientConnection connection, String hostname) throws InterruptedException, java.util.concurrent.ExecutionException {
        ClientRequest cr = new ClientRequest()
                .setPath("/")
                .setProtocol(Protocols.HTTP_1_1)
                .setMethod(Methods.GET);
        cr.getRequestHeaders().add(Headers.HOST, hostname);
        CompletableFuture<String> future = new CompletableFuture<>();
        connection.sendRequest(cr, new ClientCallback<ClientExchange>() {
            @Override
            public void completed(ClientExchange result) {
                result.setResponseListener(new ClientCallback<ClientExchange>() {
                    @Override
                    public void completed(ClientExchange result) {
                        new StringReadChannelListener(pool) {
                            @Override
                            protected void stringDone(String string) {
                                future.complete(string + ":" + result.getResponse().getProtocol());
                            }

                            @Override
                            protected void error(IOException e) {
                                future.completeExceptionally(e);
                            }
                        }.setup(result.getResponseChannel());
                    }

                    @Override
                    public void failed(IOException e) {
                        future.completeExceptionally(e);
                    }
                });
            }

            @Override
            public void failed(IOException e) {
                future.completeExceptionally(e);
            }
        });
        Assert.assertEquals("Response sent:HTTP/2.0", future.get());
    }

    private XnioSsl createClientSSL(File hostNameKeystore) throws NoSuchAlgorithmException, KeyStoreException, IOException, CertificateException, UnrecoverableKeyException, KeyManagementException {
        SSLContext clientContext = SSLContext.getInstance("TLS");
        KeyStore store = KeyStore.getInstance("jks");
        try (FileInputStream in = new FileInputStream(hostNameKeystore)) {
            store.load(in, PASSWORD.toCharArray());
        }

        KeyManagerFactory km = KeyManagerFactory.getInstance(keyAlgorithm());
        km.init(store, PASSWORD.toCharArray());
        TrustManagerFactory tm = TrustManagerFactory.getInstance(keyAlgorithm());
        tm.init(store);
        clientContext.init(km.getKeyManagers(), tm.getTrustManagers(), new SecureRandom());
        return new UndertowXnioSsl(Xnio.getInstance(), OptionMap.EMPTY, clientContext);
    }


    static void generateFileKeyStore(File path, String hostName, InetAddress[] addresses) {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048, new SecureRandom());
            KeyPair pair = keyGen.generateKeyPair();
            X509Certificate cert = generateCertificate(pair, hostName, addresses);

            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, PASSWORD.toCharArray());

            //Generate self-signed certificate
            X509Certificate[] chain = new X509Certificate[1];
            chain[0] = cert;
            keyStore.setKeyEntry(ALIAS, pair.getPrivate(), PASSWORD.toCharArray(), chain);
            try (FileOutputStream stream = new FileOutputStream(path)) {
                keyStore.store(stream, PASSWORD.toCharArray());
            }
            path.setReadable(false, false);
            path.setReadable(true, true);
            path.setWritable(false, false);
            path.setWritable(true, true);

            DomainManagementLogger.SECURITY_LOGGER.keystoreHasBeenCreated(path.toString(), getSha1Fingerprint(cert, "SHA-1"), getSha1Fingerprint(cert, "SHA-256"));

        } catch (Exception e) {
            throw DomainManagementLogger.SECURITY_LOGGER.failedToGenerateSelfSignedCertificate(e);
        }
    }

    static X509Certificate generateCertificate(KeyPair pair, String hostName, InetAddress[] addresses) throws Exception {
        PrivateKey privkey = pair.getPrivate();
        X509CertificateBuilder builder = new X509CertificateBuilder();
        Date from = new Date();
        Date to = new Date(from.getTime() + (1000L * 60L * 60L * 24L * 365L * 10L));
        BigInteger sn = new BigInteger(64, new SecureRandom());

        builder.setNotValidAfter(ZonedDateTime.ofInstant(Instant.ofEpochMilli(to.getTime()), TimeZone.getDefault().toZoneId()));
        builder.setNotValidBefore(ZonedDateTime.ofInstant(Instant.ofEpochMilli(from.getTime()), TimeZone.getDefault().toZoneId()));
        builder.setSerialNumber(sn);
        X500Principal owner = new X500Principal("CN=" + hostName);
        builder.setSubjectDn(owner);
        builder.setIssuerDn(owner);
        builder.setPublicKey(pair.getPublic());
        builder.setVersion(3);
        builder.setSignatureAlgorithmName(SHA_256_WITH_RSA);
        builder.setSigningKey(privkey);
        List<GeneralName> subjectAlternativeNames = new ArrayList<>();
        subjectAlternativeNames.add(new GeneralName.DNSName(hostName));
        if (addresses != null) {
            for (int i = 0; i < addresses.length; i++) {
                subjectAlternativeNames.add(new GeneralName.IPAddress(addresses[i].getHostAddress()));
            }
        }
        builder.addExtension(new SubjectAlternativeNamesExtension(false, subjectAlternativeNames));
        return builder.build();
    }

    private static String getSha1Fingerprint(X509Certificate cert, String algo) throws Exception {
        MessageDigest md = MessageDigest.getInstance(algo);
        byte[] der = cert.getEncoded();
        md.update(der);
        byte[] digest = md.digest();
        return hexify(digest);

    }

    private static String hexify(byte[] bytes) {
        char[] hexDigits = {'0', '1', '2', '3', '4', '5', '6', '7',
                '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
        StringBuffer buf = new StringBuffer(bytes.length * 2);
        for (int i = 0; i < bytes.length; ++i) {
            if (i > 0) {
                buf.append(":");
            }
            buf.append(hexDigits[(bytes[i] & 0xf0) >> 4]);
            buf.append(hexDigits[bytes[i] & 0x0f]);
        }
        return buf.toString();
    }

    /**
     * Determines key algorithm to be used based on the used JDK.
     *
     * @return IbmX509 in case of IBM JDK, SunX509 otherwise
     */
    private static String keyAlgorithm() {
        return "SunX509";
    }

    /**
     * Deploys the archive to the running server.
     *
     * @param archive the archive to deploy
     * @throws IOException if an error occurs deploying the archive
     */
    public static void deploy(final Archive<?> archive, ManagementClient managementClient) throws IOException {
        // Use an operation to allow overriding the runtime name
        final ModelNode address = Operations.createAddress(DEPLOYMENT, archive.getName());
        final ModelNode addOp = createAddOperation(address);
        addOp.get("enabled").set(true);
        // Create the content for the add operation
        final ModelNode contentNode = addOp.get(CONTENT);
        final ModelNode contentItem = contentNode.get(0);
        contentItem.get(ClientConstants.INPUT_STREAM_INDEX).set(0);

        // Create an operation and add the input archive
        final OperationBuilder builder = OperationBuilder.create(addOp);
        builder.addInputStream(archive.as(ZipExporter.class).exportAsInputStream());

        // Deploy the content and check the results
        final ModelNode result = managementClient.getControllerClient().execute(builder.build());
        if (!Operations.isSuccessfulOutcome(result)) {
            Assert.fail(String.format("Failed to deploy %s: %s", archive, Operations.getFailureDescription(result).asString()));
        }
    }

    public static void undeploy(ManagementClient client, final String runtimeName) throws ServerDeploymentHelper.ServerDeploymentException {
        final ServerDeploymentHelper helper = new ServerDeploymentHelper(client.getControllerClient());
        final Collection<Throwable> errors = new ArrayList<>();
        try {
            final ModelNode op = Operations.createReadResourceOperation(Operations.createAddress("deployment", runtimeName));
            final ModelNode result = client.getControllerClient().execute(op);
            if (Operations.isSuccessfulOutcome(result))
                helper.undeploy(runtimeName);
        } catch (Exception e) {
            errors.add(e);
        }
        if (!errors.isEmpty()) {
            final RuntimeException e = new RuntimeException("Error undeploying: " + runtimeName);
            for (Throwable error : errors) {
                e.addSuppressed(error);
            }
            throw e;
        }
    }
}
