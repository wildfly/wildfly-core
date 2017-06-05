/*
 * Copyright 2017 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.core.test.standalone.mgmt;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;

import java.io.IOException;
import java.net.ConnectException;

import javax.inject.Inject;
import javax.security.sasl.SaslException;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.ModelControllerClientConfiguration;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.WildflyTestRunner;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.MatchRule;
import org.wildfly.security.sasl.SaslMechanismSelector;

/**
 * Tests default SASL configuration for management interface.
 *
 * @author Josef Cacek
 */
@RunWith(WildflyTestRunner.class)
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
            executeWhoAmI();
        } catch (IOException e) {
            Throwable cause = e.getCause();
            Assert.assertThat(cause, is(instanceOf(ConnectException.class)));
            Assert.assertThat(cause.getCause(), is(instanceOf(SaslException.class)));
        }
    }

    private ModelNode executeWhoAmI() throws IOException {
        ModelControllerClient client = ModelControllerClient.Factory.create(new ModelControllerClientConfiguration.Builder()
                .setHostName(managementClient.getMgmtAddress()).setPort(9990).setConnectionTimeout(10000).build());

        ModelNode operation = new ModelNode();
        operation.get("operation").set("whoami");
        operation.get("verbose").set("true");

        return client.execute(operation);
    }

    private void assertWhoAmI(String expected) {
        try {
            ModelNode result = executeWhoAmI();
            Assert.assertTrue("The whoami operation should finish with success", Operations.isSuccessfulOutcome(result));
            Assert.assertEquals("The whoami operation returned unexpected value", expected,
                    Operations.readResult(result).get("identity").get("username").asString());
        } catch (IOException e) {
            Assert.fail("The whoami operation failed - " + e.getMessage());
        }
    }

}
