/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.elytron;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADMIN_ONLY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SASL_AUTHENTICATION_FACTORY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;

import javax.security.auth.callback.CallbackHandler;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.test.integration.domain.AbstractSecondaryHCAuthenticationTestCase;
import org.jboss.as.test.integration.domain.management.util.Authentication;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test a secondary HC connecting to the domain Elytron authentication context.
 */
public class SecondaryHostControllerElytronAuthenticationTestCase extends AbstractSecondaryHCAuthenticationTestCase {

    protected static final String RIGHT_PASSWORD = DomainLifecycleUtil.SECONDARY_HOST_PASSWORD;

    private static ModelControllerClient domainPrimaryClient;
    private static ModelControllerClient domainSecondaryClient;
    private static DomainTestSupport testSupport;

    private final String BAD_PASSWORD = "bad_password";
    private final int FAILED_RELOAD_TIMEOUT_MILLIS = 10_000;

    @BeforeClass
    public static void setupDomain() throws Exception {
        // Set up a domain with a primary that doesn't support local auth so secondarys have to use configured credentials
        testSupport = DomainTestSupport.create(
                DomainTestSupport.Configuration.create(SecondaryHostControllerElytronAuthenticationTestCase.class.getSimpleName(),
                        "domain-configs/domain-minimal.xml",
                        "host-configs/host-primary-elytron.xml", "host-configs/host-secondary-elytron.xml"));

        // Tweak the callback handler so the primary test driver client can authenticate
        // To keep setup simple it uses the same credentials as the secondary host
        CallbackHandler callbackHandler = Authentication.getCallbackHandler("secondary", RIGHT_PASSWORD, "ManagementRealm");
        testSupport.getDomainPrimaryConfiguration().setCallbackHandler(callbackHandler);
        testSupport.getDomainSecondaryConfiguration().setCallbackHandler(callbackHandler);


        testSupport.start();

        domainPrimaryClient = testSupport.getDomainPrimaryLifecycleUtil().getDomainClient();
        domainSecondaryClient = testSupport.getDomainSecondaryLifecycleUtil().getDomainClient();
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport.close();
        testSupport = null;
        domainPrimaryClient = null;
        domainSecondaryClient = null;
    }

    @Override
    protected ModelControllerClient getDomainPrimaryClient() {
        return domainPrimaryClient;
    }

    @Override
    protected ModelControllerClient getDomainSecondaryClient() {
        return domainSecondaryClient;
    }

    @Test
    public void testSecondaryRegistration() throws Exception {
        secondaryWithDigestM5Mechanism();
        // TODO WFLY-8630 restore this
        //secondaryWithPlainMechanism();
        secondaryWithInvalidPassword();
    }

    private void secondaryWithDigestM5Mechanism() throws Exception {
        // Simply check that the initial startup produced a registered secondary
        readHostControllerStatus(getDomainPrimaryClient());
    }

    private void secondaryWithPlainMechanism() throws Exception {
        // Set the allowed mechanism to PLAIN
        getDomainPrimaryClient().execute(changePresentedMechanisms("primary", new HashSet<>(Arrays.asList("PLAIN"))));
        getDomainSecondaryClient().execute(changeSaslMechanism("secondary", "PLAIN"));

        // Reload the secondary and check that it produces a registered secondary
        reloadSecondary();
        readHostControllerStatus(getDomainPrimaryClient());

        // Set the allowed mechanism back to Digest-MD5
        getDomainPrimaryClient().execute(changePresentedMechanisms("primary", new HashSet<>(Arrays.asList("DIGEST-MD5"))));
        getDomainSecondaryClient().execute(changeSaslMechanism("secondary", "DIGEST-MD5"));
        reloadSecondary();
        testSupport.getDomainSecondaryLifecycleUtil().awaitHostController(System.currentTimeMillis());
        readHostControllerStatus(domainPrimaryClient);
    }

    /**
     * Since this test results in an invalid configuration of a host, it needs to be run last in the test sequence.
     *
     * @throws Exception
     */
    private void secondaryWithInvalidPassword() throws Exception {
        // Set up a bad password
        getDomainSecondaryClient().execute(changePassword(BAD_PASSWORD));

        // Reload the secondary, after being reloaded, the secondary should fail because it won't be able to connect to primary
        reloadWithoutChecks();

        // Verify that the secondary host is not running
        Assert.assertFalse("Host \"secondary\" has connected to primary even though it has bad password",
                hostRunning(FAILED_RELOAD_TIMEOUT_MILLIS));

        // Note that the secondary is now lost to us - we can't configure it via primary
    }

    private ModelNode changeSaslMechanism(String secondaryName, String mechanism) {
        ModelNode setAllowedMechanism = new ModelNode();
        setAllowedMechanism.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
        setAllowedMechanism.get(OP_ADDR).set(
                new ModelNode().add(HOST, secondaryName).add(SUBSYSTEM, "elytron")
                        .add("authentication-configuration", "secondaryHostAConfiguration"));
        setAllowedMechanism.get(NAME).set("allow-sasl-mechanisms");
        setAllowedMechanism.get(VALUE).set(new ModelNode().add(mechanism));

        return setAllowedMechanism;
    }

    private ModelNode changePresentedMechanisms(String secondaryName, Set<String> mechanisms) {
        ModelNode setPresentedMechanisms = new ModelNode();
        setPresentedMechanisms.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
        setPresentedMechanisms.get(OP_ADDR).set(
                new ModelNode().add(HOST, secondaryName).add(SUBSYSTEM, "elytron")
                        .add(SASL_AUTHENTICATION_FACTORY, "management-sasl-authentication"));
        setPresentedMechanisms.get(NAME).set("mechanism-configurations");

        ModelNode allMechanismsNode = new ModelNode();

        for (String mechanism : mechanisms) {
            ModelNode mechanismNode = new ModelNode();
            mechanismNode.get("mechanism-name").set(mechanism);
            if (mechanism.equals("LOCAL-JBOSS-USER")) {
                mechanismNode.get("realm-mapper").set("local");
            } else {
                mechanismNode.get("mechanism-realm-configurations").set(new ModelNode().get("realm-name").set("ManagementRealm"));
            }

            allMechanismsNode.add(mechanismNode);
        }

        setPresentedMechanisms.get(VALUE).set(allMechanismsNode);

        return setPresentedMechanisms;
    }

    private ModelNode changePassword(String password) {
        ModelNode setPassword = new ModelNode();
        setPassword.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
        setPassword.get(OP_ADDR).set(
                new ModelNode().add(HOST, "secondary").add(SUBSYSTEM, "elytron")
                        .add("authentication-configuration", "secondaryHostAConfiguration"));
        setPassword.get(NAME).set("credential-reference.clear-text");
        setPassword.get(VALUE).set(password);

        return setPassword;
    }

    private void reloadWithoutChecks() throws IOException {
        ModelNode reloadSecondary = new ModelNode();
        reloadSecondary.get(OP).set("reload");
        reloadSecondary.get(OP_ADDR).add(HOST, "secondary");
        reloadSecondary.get(ADMIN_ONLY).set(false);
        try {
            getDomainSecondaryClient().execute(reloadSecondary);
        } catch(IOException e) {
            final Throwable cause = e.getCause();
            if (!(cause instanceof ExecutionException) && !(cause instanceof CancellationException)) {
                throw e;
            } // else ignore, this might happen if the channel gets closed before we got the response
        }
    }

    private boolean hostRunning(long timeout) throws Exception {
        final long time = System.currentTimeMillis() + timeout;
        do {
            Thread.sleep(250);
            if (lookupHostInModel(getDomainPrimaryClient())) {
                return true;
            }
        } while (System.currentTimeMillis() < time);

        return false;
    }
}
