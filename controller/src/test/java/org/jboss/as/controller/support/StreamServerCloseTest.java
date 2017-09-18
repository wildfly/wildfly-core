/*
Copyright 2017 Red Hat, Inc.

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

package org.jboss.as.controller.support;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.spec.InvalidKeySpecException;

import javax.net.ssl.SSLContext;
import javax.security.sasl.SaslServerFactory;

import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.spi.NetworkServerProvider;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.wildfly.security.WildFlyElytronProvider;
import org.wildfly.security.auth.realm.SimpleMapBackedSecurityRealm;
import org.wildfly.security.auth.server.MechanismConfiguration;
import org.wildfly.security.auth.server.SaslAuthenticationFactory;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.spec.ClearPasswordSpec;
import org.wildfly.security.permission.PermissionVerifier;
import org.wildfly.security.sasl.util.SaslFactories;
import org.wildfly.security.sasl.util.SaslMechanismInformation;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;

/**
 * Test that an AcceptingChannel created from an Endpoint can be rapidly closed and reopened.
 *
 * @author Brian Stansberry
 */
public class StreamServerCloseTest {
    protected static Endpoint endpoint;
    private static String providerName;
    private Closeable streamServer;
//    private Channel clientChannel;
//    private Channel serverChannel;
//    private Connection connection;
//    private Registration serviceRegistration;

    @BeforeClass
    public static void create() throws Exception {
        final WildFlyElytronProvider provider = new WildFlyElytronProvider();
        Security.addProvider(provider);
        providerName = provider.getName();
        endpoint = Endpoint.builder().setEndpointName("test").build();
    }

    @AfterClass
    public static void destroy() throws IOException, InterruptedException {
        IoUtils.safeClose(endpoint);
        Security.removeProvider(providerName);
    }

    @After
    public void afterTest() {
//        stopChannels();
//        IoUtils.safeClose(connection);
        IoUtils.safeClose(streamServer);
    }

    //@Test
    public void testRepeatedOpenClose() throws Exception {
        for (int i = 0; i < 1000; i++) {
            createStreamServer();
//            startChannels();
//            stopChannels();
            streamServer.close();
        }
    }

    private void createStreamServer() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        NetworkServerProvider networkServerProvider = endpoint.getConnectionProviderInterface("remote", NetworkServerProvider.class);
        final SecurityDomain.Builder domainBuilder = SecurityDomain.builder();
        final SimpleMapBackedSecurityRealm mainRealm = new SimpleMapBackedSecurityRealm();
        domainBuilder.addRealm("mainRealm", mainRealm).build();
        domainBuilder.setDefaultRealmName("mainRealm");
        domainBuilder.setPermissionMapper((permissionMappable, roles) -> PermissionVerifier.ALL);
        final PasswordFactory passwordFactory = PasswordFactory.getInstance("clear");
        mainRealm.setPasswordMap("bob", passwordFactory.generatePassword(new ClearPasswordSpec("pass".toCharArray())));
        final SaslServerFactory saslServerFactory = SaslFactories.getElytronSaslServerFactory();
        final SaslAuthenticationFactory.Builder builder = SaslAuthenticationFactory.builder();
        builder.setSecurityDomain(domainBuilder.build());
        builder.setFactory(saslServerFactory);
        builder.setMechanismConfigurationSelector(mechanismInformation -> SaslMechanismInformation.Names.SCRAM_SHA_256.equals(mechanismInformation.getMechanismName()) ? MechanismConfiguration.EMPTY : null);
        final SaslAuthenticationFactory saslAuthenticationFactory = builder.build();
        streamServer =  networkServerProvider.createServer(new InetSocketAddress("localhost", 30123), OptionMap.create(Options.SSL_ENABLED, Boolean.FALSE), saslAuthenticationFactory, SSLContext.getDefault());

    }

//    private void startChannels() throws IOException, URISyntaxException, InterruptedException {
//        final FutureResult<Channel> passer = new FutureResult<Channel>();
//        serviceRegistration = endpoint.registerService("org.jboss.test", new OpenListener() {
//            public void channelOpened(final Channel channel) {
//                passer.setResult(channel);
//            }
//
//            public void registrationTerminated() {
//            }
//        }, OptionMap.EMPTY);
//        if (connection == null) {
//            IoFuture<Connection> futureConnection = AuthenticationContext.empty().with(MatchRule.ALL, AuthenticationConfiguration.empty().useName("bob").usePassword("pass").setSaslMechanismSelector(SaslMechanismSelector.NONE.addMechanism("SCRAM-SHA-256"))).run(new PrivilegedAction<IoFuture<Connection>>() {
//                public IoFuture<Connection> run() {
//                    try {
//                        return endpoint.connect(new URI("remote://localhost:30123"), OptionMap.EMPTY);
//                    } catch (URISyntaxException e) {
//                        throw new RuntimeException(e);
//                    }
//                }
//            });
//            connection = futureConnection.get();
//        }
//        IoFuture<Channel> futureChannel = connection.openChannel("org.jboss.test", OptionMap.EMPTY);
//        clientChannel = futureChannel.get();
//        serverChannel = passer.getIoFuture().get();
//        assertNotNull(serverChannel);
//
//    }
//
//    private void stopChannels() {
//        IoUtils.safeClose(clientChannel);
//        IoUtils.safeClose(serverChannel);
//        IoUtils.safeClose(serviceRegistration);
//    }
}
