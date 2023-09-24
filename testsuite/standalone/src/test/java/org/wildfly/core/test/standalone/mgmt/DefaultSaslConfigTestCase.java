/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.test.standalone.mgmt;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;

import java.io.IOException;
import java.net.ConnectException;

import jakarta.inject.Inject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;
import javax.security.sasl.SaslException;

import org.hamcrest.MatcherAssert;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.ModelControllerClientConfiguration;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.WildFlyRunner;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.MatchRule;
import org.wildfly.security.sasl.SaslMechanismSelector;

/**
 * Tests default SASL configuration for management interface.
 *
 * @author Josef Cacek
 */
@RunWith(WildFlyRunner.class)
public class DefaultSaslConfigTestCase {

    @Inject
    protected ManagementClient managementClient;

    /**
     * Tests that JBOSS-LOCAL-USER SASL mechanism can be used for authentication in default server configuration.
     */
    @Test
    public void testJBossLocalInDefault() throws Exception {
        AuthenticationContext.empty()
                .with(MatchRule.ALL, AuthenticationConfiguration.empty().useDefaultProviders()
                        .setSaslMechanismSelector(SaslMechanismSelector.NONE.addMechanism("JBOSS-LOCAL-USER")))
                .run(() -> assertWhoAmI("$local"));
    }

    /**
     * Tests that DIGEST-MD5 SASL mechanism can be used for authentication in default server configuration.
     */
    @Test
    public void testDigestAuthn() throws Exception {
        AuthenticationContext.empty()
                .with(MatchRule.ALL, AuthenticationConfiguration.empty().useDefaultProviders()
                        .setSaslMechanismSelector(SaslMechanismSelector.NONE.addMechanism("DIGEST-MD5"))
                        .useName("testSuite").usePassword("testSuitePassword"))
                .run(() -> assertWhoAmI("testSuite"));
    }

    /**
     * Tests that DIGEST-MD5 SASL mechanism can be used for authentication in default server configuration.
     *
     * The supplied CallbackHandler should take priority for the username and password over the values
     * on the AuthenticationConfiguration.
     */
    @Test
    public void testDigestAuthnCallbackHandler() throws Exception {
        AuthenticationContext.empty()
                .with(MatchRule.ALL, AuthenticationConfiguration.empty().useDefaultProviders()
                        .setSaslMechanismSelector(SaslMechanismSelector.NONE.addMechanism("DIGEST-MD5"))
                        .useName("bad").usePassword("bad"))
                .run(() -> assertWhoAmI("testSuite", callbackHandler("testSuite", "testSuitePassword", "ManagementRealm")));
    }

    /**
     * Tests that DIGEST-MD5 SASL mechanism fail if wrong password is used.
     */
    @Test
    public void testDigestWrongPass() throws Exception {
        AuthenticationContext.empty()
                .with(MatchRule.ALL, AuthenticationConfiguration.empty().useDefaultProviders()
                        .setSaslMechanismSelector(SaslMechanismSelector.NONE.addMechanism("DIGEST-MD5"))
                        .useName("testSuite").usePassword("testSuite"))
                .run(() -> assertAuthenticationFails());
    }

    /**
     * Tests that PLAIN SASL mechanism can't be used for authentication in default server configuration.
     */
    @Test
    public void testPlainAuthn() throws Exception {
        AuthenticationContext.empty()
                .with(MatchRule.ALL, AuthenticationConfiguration.empty().useDefaultProviders()
                        .setSaslMechanismSelector(SaslMechanismSelector.NONE.addMechanism("PLAIN"))
                        .useName("testSuite").usePassword("testSuitePassword"))
                .run(() -> assertAuthenticationFails());
    }

    /**
     * Tests that ANONYMOUS SASL mechanism can't be used for authentication in default server configuration.
     */
    @Test
    public void testAnonymousFailsInDefault() throws Exception {
        AuthenticationContext.empty()
                .with(MatchRule.ALL, AuthenticationConfiguration.empty().useDefaultProviders()
                        .setSaslMechanismSelector(SaslMechanismSelector.NONE.addMechanism("ANONYMOUS"))
                        .useAnonymous())
                .run(() -> assertAuthenticationFails());
    }

    private void assertAuthenticationFails() {
        try {
            executeWhoAmI(null);
        } catch (IOException e) {
            Throwable cause = e.getCause();
            MatcherAssert.assertThat(cause, is(instanceOf(ConnectException.class)));
            MatcherAssert.assertThat(cause.getCause(), is(instanceOf(SaslException.class)));
        }
    }

    private ModelNode executeWhoAmI(final CallbackHandler callbackHandler) throws IOException {
        ModelControllerClient client = ModelControllerClient.Factory.create(new ModelControllerClientConfiguration.Builder()
                .setHostName(managementClient.getMgmtAddress()).setPort(9990).setConnectionTimeout(10000).setHandler(callbackHandler).build());

        ModelNode operation = new ModelNode();
        operation.get("operation").set("whoami");
        operation.get("verbose").set("true");

        return client.execute(operation);
    }

    private void assertWhoAmI(String expected) {
        assertWhoAmI(expected, null);
    }

    private void assertWhoAmI(String expected, CallbackHandler callbackHandler) {
        try {
            ModelNode result = executeWhoAmI(callbackHandler);
            Assert.assertTrue("The whoami operation should finish with success", Operations.isSuccessfulOutcome(result));
            Assert.assertEquals("The whoami operation returned unexpected value", expected,
                    Operations.readResult(result).get("identity").get("username").asString());
        } catch (IOException e) {
            Assert.fail("The whoami operation failed - " + e.getMessage());
        }
    }

    private static CallbackHandler callbackHandler(final String username, final String password, final String realm) {
        return new CallbackHandler() {

            @Override
            public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
                for (Callback current : callbacks) {
                    if (current instanceof NameCallback) {
                        NameCallback ncb = (NameCallback) current;
                        ncb.setName(username);
                    } else if (current instanceof PasswordCallback) {
                        PasswordCallback pcb = (PasswordCallback) current;
                        pcb.setPassword(password.toCharArray());
                    } else if (current instanceof RealmCallback) {
                        RealmCallback rcb = (RealmCallback) current;
                        rcb.setText(realm != null ? realm : rcb.getDefaultText());
                    } else {
                        throw new UnsupportedCallbackException(current);
                    }
                }
            }

        };
    }

}
