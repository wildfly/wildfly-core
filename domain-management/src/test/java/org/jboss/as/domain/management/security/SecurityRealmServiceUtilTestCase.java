/*
Copyright 2018 Red Hat, Inc.

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

package org.jboss.as.domain.management.security;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.AccessController;
import java.security.Security;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslServer;

import org.jboss.as.domain.management.SecurityRealm;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContextConfigurationClient;
import org.wildfly.security.auth.server.SaslAuthenticationFactory;
import org.wildfly.security.password.WildFlyElytronPasswordProvider;
import org.wildfly.security.sasl.SaslMechanismSelector;
import org.wildfly.security.sasl.digest.WildFlyElytronSaslDigestProvider;
import org.wildfly.security.sasl.util.SaslMechanismInformation;

/**
 * Tests SecurityRealm.ServiceUtil handling for WFCORE-4099 / MSC-240.
 *
 * @author Brian Stansberry
 */
public class SecurityRealmServiceUtilTestCase {

    private static final String TESTNAME = SecurityRealmServiceUtilTestCase.class.getSimpleName();

    private ServiceContainer container;
    private Path tmpDir;

    @Before
    public void setup() throws IOException {
        container = ServiceContainer.Factory.create(TESTNAME);
        tmpDir = Files.createTempDirectory(TESTNAME);
    }

    @After
    public void teardown() throws IOException {
        if (container != null) {
            container.shutdown();
            try {
                container.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            finally {
                container = null;
            }
        }
        if (tmpDir != null) {
            Files.walkFileTree(tmpDir, new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    FileVisitResult result = super.visitFile(file, attrs);
                    Files.deleteIfExists(file);
                    return  result;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    FileVisitResult result = super.postVisitDirectory(dir, exc);
                    Files.deleteIfExists(dir);
                    return  result;
                }
            });
        }
    }

    /**
     * Tests that SecurityRealm.ServiceUtil's dependency injection works regardless of what
     * ServiceTarget API was used for creating the target ServiceBuilder.
     */
    @SuppressWarnings("deprecation")
    @Test
    public void testDifferentServiceBuilderTypes() {
        ServiceTarget serviceTarget = container.subTarget();

        final Supplier<String> tmpDirSupplier = () -> tmpDir.toAbsolutePath().toString();

        final ServiceName realmServiceName = SecurityRealm.ServiceUtil.createServiceName(TESTNAME);
        final ServiceBuilder<?> realmBuilder = serviceTarget.addService(realmServiceName);
        final Consumer<SecurityRealm> securityRealmConsumer = realmBuilder.provides(realmServiceName, SecurityRealm.ServiceUtil.createLegacyServiceName(TESTNAME));
        final SecurityRealmService securityRealmService = new SecurityRealmService(securityRealmConsumer, null, null, null, null, tmpDirSupplier, new HashSet(), TESTNAME, false);
        realmBuilder.setInstance(securityRealmService);
        final ServiceController<?> realmController = realmBuilder.install();

        TestService legacy = new TestService();
        ServiceBuilder legacyBuilder = serviceTarget.addService(ServiceName.of("LEGACY"), legacy);
        SecurityRealm.ServiceUtil.addDependency(legacyBuilder, legacy.injector, TESTNAME);
        ServiceController<?> legacyController = legacyBuilder.install();

        TestService current = new TestService();
        ServiceBuilder currentBuilder = serviceTarget.addService(ServiceName.of("CURRENT"));
        SecurityRealm.ServiceUtil.addDependency(currentBuilder, current.injector, TESTNAME);
        currentBuilder.setInstance(current);
        ServiceController<?> currentController = currentBuilder.install();

        try {
            container.awaitStability(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Assert.fail("Interrupted");
        }

        Assert.assertEquals(ServiceController.State.UP, realmController.getState());
        Assert.assertEquals(ServiceController.State.UP, legacyController.getState());
        Assert.assertEquals(ServiceController.State.UP, currentController.getState());

        Assert.assertSame(securityRealmService, legacy.getValue());
        Assert.assertSame(securityRealmService, current.getValue());
    }

    private static void registerElytronProviders() {
        Security.insertProviderAt(WildFlyElytronSaslDigestProvider.getInstance(), 1);
        Security.insertProviderAt(WildFlyElytronPasswordProvider.getInstance(), 1);
    }

    private static void removeElytronProviders() {
        Security.removeProvider(WildFlyElytronSaslDigestProvider.getInstance().getName());
        Security.removeProvider(WildFlyElytronPasswordProvider.getInstance().getName());
    }

    private File createPropertyFile(String filename, String... users) throws IOException {
        File propertyUserFile = new File(tmpDir.toAbsolutePath().toString(), filename);
        propertyUserFile.createNewFile();
        try (FileOutputStream fos = new FileOutputStream(propertyUserFile)) {
            Properties domainPropeties = new Properties();
            if (users != null) {
                for (int i = 0; i < users.length; i += 2) {
                    domainPropeties.setProperty(users[i], users[i + 1]);
                }
            }
            domainPropeties.store(fos, "");
        }
        return propertyUserFile;
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testSaslAuthenticationFactoryDigest() throws Exception {
        registerElytronProviders();
        try {
            File propsFile = createPropertyFile(TESTNAME + "-users.properties", "user1", "password1");
            ServiceTarget serviceTarget = container.subTarget();
            final Supplier<String> tmpDirSupplier = () -> tmpDir.toAbsolutePath().toString();

            // register a realm service with a properties file to perform a SASL DIGEST-MD5 login
            final ServiceName realmServiceName = SecurityRealm.ServiceUtil.createServiceName(TESTNAME);
            final ServiceBuilder<?> realmBuilder = serviceTarget.addService(realmServiceName);
            final Consumer<SecurityRealm> securityRealmConsumer = realmBuilder.provides(realmServiceName, SecurityRealm.ServiceUtil.createLegacyServiceName(TESTNAME));
            // create the properties service to check username/password
            final ServiceName propsServiceName = PropertiesCallbackHandler.ServiceUtil.createServiceName("PropertiesRealm");
            final ServiceBuilder<?> propsBuilder = serviceTarget.addService(propsServiceName);
            final Consumer<CallbackHandlerService> chsConsumer = propsBuilder.provides(propsServiceName);
            propsBuilder.setInstance(new PropertiesCallbackHandler(chsConsumer, null, TESTNAME, propsFile.getAbsolutePath(), null, true));
            propsBuilder.setInitialMode(ServiceController.Mode.ON_DEMAND);
            propsBuilder.install();
            final SecurityRealmService securityRealmService = new SecurityRealmService(
                    securityRealmConsumer, null, null, null, null, tmpDirSupplier,
                    Collections.singleton(CallbackHandlerService.ServiceUtil.requires(realmBuilder, propsServiceName)),
                    TESTNAME, false);
            realmBuilder.setInstance(securityRealmService);
            realmBuilder.install();

            // wait for server stability
            container.awaitStability(60, TimeUnit.SECONDS);

            // get the sasl factory for DIGEST-MD5 and create the sasl server with it
            SaslAuthenticationFactory saslAuthFact = securityRealmService.getSaslAuthenticationFactory(new String[]{"DIGEST-MD5"}, true);
            Assert.assertNotNull("Server Sasl Factory is not null", saslAuthFact);
            SaslServer server = saslAuthFact.createMechanism("DIGEST-MD5");

            // now create a sasl client and perform the sasl dance
            final AuthenticationConfiguration authConfig = AuthenticationConfiguration.empty()
                            .useName("user1")
                            .usePassword("password1")
                            .useRealm(TESTNAME)
                            .setSaslMechanismSelector(SaslMechanismSelector.NONE.addMechanism(SaslMechanismInformation.Names.DIGEST_MD5));
            AuthenticationContextConfigurationClient contextConfigurationClient = AccessController.doPrivileged(AuthenticationContextConfigurationClient.ACTION);
            SaslClient client = contextConfigurationClient.createSaslClient(new URI("unknown://server"), authConfig, Collections.singletonList("DIGEST-MD5"));
            Assert.assertNotNull("Sasl client is not null", client);
            Assert.assertFalse("Sasl client has no initial response", client.hasInitialResponse());
            byte[] message = server.evaluateResponse(new byte[0]);
            message = client.evaluateChallenge(message);
            server.evaluateResponse(message);
            Assert.assertTrue("Sasl server is complete", server.isComplete());
            Assert.assertEquals("Correct user is logged in", "user1", server.getAuthorizationID());
        } finally {
            removeElytronProviders();
        }
    }

    @SuppressWarnings("deprecation")
    private static class TestService implements Service<SecurityRealm>, org.jboss.msc.Service {

        private final InjectedValue<SecurityRealm> injector = new InjectedValue<>();

        private SecurityRealm value;

        @Override
        public void start(StartContext context) throws StartException {
            value = injector.getValue();
        }

        @Override
        public void stop(StopContext context) {
            value = null;
        }

        @Override
        public SecurityRealm getValue() throws IllegalStateException, IllegalArgumentException {
            return value;
        }
    }
}
