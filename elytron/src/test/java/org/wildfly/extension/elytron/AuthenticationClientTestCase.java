/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static org.jboss.as.controller.client.helpers.ClientConstants.WRITE_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.junit.Assert.assertEquals;

import java.util.Map;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.security.CredentialReference;
import org.jboss.as.subsystem.test.AbstractSubsystemTest;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class AuthenticationClientTestCase extends AbstractSubsystemTest {
    private static final String AUTH_CONFIG_NAME = "myAuthConfig";
    private static final PathAddress ROOT_ADDRESS = PathAddress.pathAddress(SUBSYSTEM, ElytronExtension.SUBSYSTEM_NAME);
    private static final PathAddress AUTH_CONFIG_ADDRESS = ROOT_ADDRESS.append(ElytronDescriptionConstants.AUTHENTICATION_CONFIGURATION, AUTH_CONFIG_NAME);
    private KernelServices services = null;

    public AuthenticationClientTestCase() {
        super(ElytronExtension.SUBSYSTEM_NAME, new ElytronExtension());
    }

    @Before
    public void init() throws Exception {
        String subsystemXml;
        if (JdkUtils.isIbmJdk()) {
            subsystemXml = "tls-ibm.xml";
        } else {
            subsystemXml = JdkUtils.getJavaSpecVersion() <= 12 ? "tls-sun.xml" : "tls-oracle13plus.xml";
        }
        services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource(subsystemXml).build();
        if (!services.isSuccessfulBoot()) {
            if (services.getBootError() != null) {
                Assert.fail(services.getBootError().toString());
            }
            Assert.fail("Failed to boot, no reason provided");
        }
    }

    private ModelNode assertSuccess(ModelNode response) {
        if (!response.get(OUTCOME).asString().equals(SUCCESS)) {
            Assert.fail(response.toJSONString(false));
        }
        return response;
    }

    private ModelNode assertFailed(ModelNode response) {
        if (! response.get(OUTCOME).asString().equals(FAILED)) {
            Assert.fail(response.toJSONString(false));
        }
        return response;
    }

    private void testWriteAnonymousAuthConfig(boolean anonymous) {
        ModelNode operation = Util.createEmptyOperation(WRITE_ATTRIBUTE_OPERATION, AUTH_CONFIG_ADDRESS);
        operation.get(ElytronDescriptionConstants.NAME).set(ElytronDescriptionConstants.ANONYMOUS);
        operation.get(ElytronDescriptionConstants.VALUE).set(anonymous);
        assertSuccess(services.executeOperation(operation));
    }

    private void addAndTestKerberosSecurityFactory(String kerberosName) {
        PathAddress kbrAddress = ROOT_ADDRESS.append(ElytronDescriptionConstants.KERBEROS_SECURITY_FACTORY, kerberosName);
        Map<String, ModelNode> parameters = Map.of(ElytronDescriptionConstants.PATH, ModelNode.fromString("\"/test-server.keytab\""),
                                                   ElytronDescriptionConstants.PRINCIPAL, ModelNode.fromString("\"HTTP/test-server.elytron.org@ELYTRON.ORG\""));

        ModelNode kerberos = Util.createAddOperation(kbrAddress, parameters);
        assertSuccess(services.executeOperation(kerberos));
    }

    @Test
    public void testWriteAnonymousTrueOnly() {
        assertSuccess(services.executeOperation(Util.createAddOperation(AUTH_CONFIG_ADDRESS)));
        testWriteAnonymousAuthConfig(true);
    }

    @Test
    public void testWriteAnonymousFalseOnly() {
        assertSuccess(services.executeOperation(Util.createAddOperation(AUTH_CONFIG_ADDRESS)));
        testWriteAnonymousAuthConfig(false);
    }

    @Test
    public void testWriteMechanismPropertiesOnly() {
        assertSuccess(services.executeOperation(Util.createAddOperation(AUTH_CONFIG_ADDRESS)));
        ModelNode operation = Util.createEmptyOperation(WRITE_ATTRIBUTE_OPERATION, AUTH_CONFIG_ADDRESS);
        operation.get(ElytronDescriptionConstants.NAME).set(ElytronDescriptionConstants.MECHANISM_PROPERTIES);
        operation.get(ElytronDescriptionConstants.VALUE).set("key1", "value1");
        assertSuccess(services.executeOperation(operation));
    }

    @Test
    public void testWriteCredentialReferenceClearTextOnly() {
        assertSuccess(services.executeOperation(Util.createAddOperation(AUTH_CONFIG_ADDRESS)));

        ModelNode credentialReference = new ModelNode();
        credentialReference.get(CredentialReference.CLEAR_TEXT).set("StorePassword");

        ModelNode operation = Util.createEmptyOperation(WRITE_ATTRIBUTE_OPERATION, AUTH_CONFIG_ADDRESS);
        operation.get(ElytronDescriptionConstants.NAME).set(CredentialReference.CREDENTIAL_REFERENCE);
        operation.get(ElytronDescriptionConstants.VALUE).set(credentialReference);
        assertSuccess(services.executeOperation(operation));
    }

    @Test
    public void testWriteWebservicesAuthConfigOnly() {
        assertSuccess(services.executeOperation(Util.createAddOperation(AUTH_CONFIG_ADDRESS)));

        ModelNode webservices = new ModelNode();
        webservices.get(ElytronDescriptionConstants.HTTP_MECHANISM).set("BASIC");
        webservices.get(ElytronDescriptionConstants.WS_SECURITY_TYPE).set("UsernameToken");

        ModelNode operation = Util.createEmptyOperation(WRITE_ATTRIBUTE_OPERATION, AUTH_CONFIG_ADDRESS);
        operation.get(ElytronDescriptionConstants.NAME).set(ElytronDescriptionConstants.WEBSERVICES);
        operation.get(ElytronDescriptionConstants.VALUE).set(webservices);
        assertSuccess(services.executeOperation(operation));
    }

    @Test
    public void testWriteAttributeWithRealmOnly() {
        assertSuccess(services.executeOperation(Util.createAddOperation(AUTH_CONFIG_ADDRESS)));

        ModelNode operation = Util.createEmptyOperation(WRITE_ATTRIBUTE_OPERATION, AUTH_CONFIG_ADDRESS);
        operation.get(ElytronDescriptionConstants.NAME).set(ElytronDescriptionConstants.REALM);
        operation.get(ElytronDescriptionConstants.VALUE).set("testRealm");
        assertSuccess(services.executeOperation(operation));
    }

    @Test
    public void testWriteAttributeWithExtendsOnly() {
        assertSuccess(services.executeOperation(Util.createAddOperation(AUTH_CONFIG_ADDRESS)));

        String authConfigName2 = "myAuthConfig2";
        assertSuccess(services.executeOperation(Util.createAddOperation(ROOT_ADDRESS.append(ElytronDescriptionConstants.AUTHENTICATION_CONFIGURATION, authConfigName2))));

        ModelNode operation = Util.createEmptyOperation(WRITE_ATTRIBUTE_OPERATION, AUTH_CONFIG_ADDRESS);
        operation.get(ElytronDescriptionConstants.NAME).set(ElytronDescriptionConstants.EXTENDS);
        operation.get(ElytronDescriptionConstants.VALUE).set(authConfigName2);
        assertSuccess(services.executeOperation(operation));
    }

    @Test
    public void testWriteAuthenticationNameOnly() {
        assertSuccess(services.executeOperation(Util.createAddOperation(AUTH_CONFIG_ADDRESS)));

        ModelNode operation = Util.createEmptyOperation(WRITE_ATTRIBUTE_OPERATION, AUTH_CONFIG_ADDRESS);
        operation.get(ElytronDescriptionConstants.NAME).set(ElytronDescriptionConstants.AUTHENTICATION_NAME);
        operation.get(ElytronDescriptionConstants.VALUE).set("foo");
        assertSuccess(services.executeOperation(operation));
    }

    @Test
    public void testWriteAuthorizationNameOnly() {
        assertSuccess(services.executeOperation(Util.createAddOperation(AUTH_CONFIG_ADDRESS)));
        ModelNode operation = Util.createEmptyOperation(WRITE_ATTRIBUTE_OPERATION, AUTH_CONFIG_ADDRESS);
        operation.get(ElytronDescriptionConstants.NAME).set(ElytronDescriptionConstants.AUTHORIZATION_NAME);
        operation.get(ElytronDescriptionConstants.VALUE).set("foo");
        assertSuccess(services.executeOperation(operation));
    }

    @Test
    public void testWritePort() {
        assertSuccess(services.executeOperation(Util.createAddOperation(AUTH_CONFIG_ADDRESS)));
        ModelNode operation = Util.createEmptyOperation(WRITE_ATTRIBUTE_OPERATION, AUTH_CONFIG_ADDRESS);
        operation.get(ElytronDescriptionConstants.NAME).set(ElytronDescriptionConstants.PORT);
        operation.get(ElytronDescriptionConstants.VALUE).set(8787);
        assertSuccess(services.executeOperation(operation));
    }

    @Test
    public void testWriteProtocol() {
        assertSuccess(services.executeOperation(Util.createAddOperation(AUTH_CONFIG_ADDRESS)));
        ModelNode operation = Util.createEmptyOperation(WRITE_ATTRIBUTE_OPERATION, AUTH_CONFIG_ADDRESS);
        operation.get(ElytronDescriptionConstants.NAME).set(ElytronDescriptionConstants.PROTOCOL);
        operation.get(ElytronDescriptionConstants.VALUE).set(ElytronDescriptionConstants.HTTP);
        assertSuccess(services.executeOperation(operation));
    }

    @Test
    public void testWriteHost() {
        assertSuccess(services.executeOperation(Util.createAddOperation(AUTH_CONFIG_ADDRESS)));
        ModelNode operation = Util.createEmptyOperation(WRITE_ATTRIBUTE_OPERATION, AUTH_CONFIG_ADDRESS);
        operation.get(ElytronDescriptionConstants.NAME).set(ElytronDescriptionConstants.HOST);
        operation.get(ElytronDescriptionConstants.VALUE).set("myhost.com");
        assertSuccess(services.executeOperation(operation));
    }

    @Test
    public void testWriteSecuritDomain() {
        String securityDomainName = "ApplicationDomain";
        PathAddress securityDomainAddress = ROOT_ADDRESS.append(ElytronDescriptionConstants.SECURITY_DOMAIN, securityDomainName);

        assertSuccess(services.executeOperation(Util.createAddOperation(AUTH_CONFIG_ADDRESS)));
        assertSuccess(services.executeOperation(Util.createAddOperation(securityDomainAddress)));

        ModelNode operation = Util.createEmptyOperation(WRITE_ATTRIBUTE_OPERATION, AUTH_CONFIG_ADDRESS);
        operation.get(ElytronDescriptionConstants.NAME).set(ElytronDescriptionConstants.SECURITY_DOMAIN);
        operation.get(ElytronDescriptionConstants.VALUE).set(securityDomainName);
        assertSuccess(services.executeOperation(operation));
    }

    @Test
    public void testInvalidWritePort() {
        assertSuccess(services.executeOperation(Util.createAddOperation(AUTH_CONFIG_ADDRESS)));
        ModelNode operation = Util.createEmptyOperation(WRITE_ATTRIBUTE_OPERATION, AUTH_CONFIG_ADDRESS);
        operation.get(ElytronDescriptionConstants.NAME).set(ElytronDescriptionConstants.PORT);
        operation.get(ElytronDescriptionConstants.VALUE).set("invalid");
        assertFailed(services.executeOperation(operation));
    }

    @Test
    public void testWriteFowardingModeAuthorization() {
        assertSuccess(services.executeOperation(Util.createAddOperation(AUTH_CONFIG_ADDRESS)));
        ModelNode operation = Util.createEmptyOperation(WRITE_ATTRIBUTE_OPERATION, AUTH_CONFIG_ADDRESS);
        operation.get(ElytronDescriptionConstants.NAME).set(ElytronDescriptionConstants.FORWARDING_MODE);
        operation.get(ElytronDescriptionConstants.VALUE).set(ElytronDescriptionConstants.AUTHORIZATION);
        assertSuccess(services.executeOperation(operation));
    }

    @Test
    public void testInvalidWriteFowardingMode() {
        assertSuccess(services.executeOperation(Util.createAddOperation(AUTH_CONFIG_ADDRESS)));
        ModelNode operation = Util.createEmptyOperation(WRITE_ATTRIBUTE_OPERATION, AUTH_CONFIG_ADDRESS);
        operation.get(ElytronDescriptionConstants.NAME).set(ElytronDescriptionConstants.FORWARDING_MODE);
        operation.get(ElytronDescriptionConstants.VALUE).set("foo");
        assertFailed(services.executeOperation(operation));
    }

    @Test
    public void testWriteSaslMechanismSelectorOnly() {
        assertSuccess(services.executeOperation(Util.createAddOperation(AUTH_CONFIG_ADDRESS)));
        ModelNode operation = Util.createEmptyOperation(WRITE_ATTRIBUTE_OPERATION, AUTH_CONFIG_ADDRESS);
        operation.get(ElytronDescriptionConstants.NAME).set(ElytronDescriptionConstants.SASL_MECHANISM_SELECTOR);
        operation.get(ElytronDescriptionConstants.VALUE).set("sasl-test");
        assertSuccess(services.executeOperation(operation));
    }

    @Test
    public void testWriteKerberosSecurityFactory() {
        assertSuccess(services.executeOperation(Util.createAddOperation(AUTH_CONFIG_ADDRESS)));

        String kerberosName = "kbTest";
        addAndTestKerberosSecurityFactory(kerberosName);

        ModelNode operation = Util.createEmptyOperation(WRITE_ATTRIBUTE_OPERATION, AUTH_CONFIG_ADDRESS);
        operation.get(ElytronDescriptionConstants.NAME).set(ElytronDescriptionConstants.KERBEROS_SECURITY_FACTORY);
        operation.get(ElytronDescriptionConstants.VALUE).set(kerberosName);
        assertSuccess(services.executeOperation(operation));
    }

    @Test
    public void testInvalidWriteAuthenticationNameWithKerberosSecurityFactory() {
        String kerberosName = "kbTest";

        addAndTestKerberosSecurityFactory(kerberosName);

        ModelNode operation = Util.createAddOperation(AUTH_CONFIG_ADDRESS, Map.of(ElytronDescriptionConstants.KERBEROS_SECURITY_FACTORY, ModelNode.fromString("\"" + kerberosName + "\"")));
        assertSuccess(services.executeOperation(operation));

        operation = Util.createEmptyOperation(WRITE_ATTRIBUTE_OPERATION, AUTH_CONFIG_ADDRESS);
        operation.get(ElytronDescriptionConstants.NAME).set(ElytronDescriptionConstants.AUTHENTICATION_NAME);
        operation.get(ElytronDescriptionConstants.VALUE).set("foo");
        assertFailed(services.executeOperation(operation));
    }

    @Test
    public void testAddWebservicesAuthConfig() {
        ModelNode webservices = new ModelNode();
        webservices.get(ElytronDescriptionConstants.HTTP_MECHANISM).set("BASIC");
        webservices.get(ElytronDescriptionConstants.WS_SECURITY_TYPE).set("UsernameToken");

        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(ElytronDescriptionConstants.AUTHENTICATION_CONFIGURATION, "myAuthConfig");
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        operation.get(ElytronDescriptionConstants.WEBSERVICES).set(webservices);
        assertSuccess(services.executeOperation(operation));

        operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(ElytronDescriptionConstants.AUTHENTICATION_CONFIGURATION, "myAuthConfig");
        operation.get(ClientConstants.OP).set(ClientConstants.READ_RESOURCE_OPERATION);
        ModelNode result = assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT);
        assertEquals(webservices, result.get(ElytronDescriptionConstants.WEBSERVICES));
    }

    @Test
    public void testAddInvalidHTTPMechWebservicesAuthConfig() {
        ModelNode webservices = new ModelNode();
        webservices.get(ElytronDescriptionConstants.HTTP_MECHANISM).set("DIGEST");
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(ElytronDescriptionConstants.AUTHENTICATION_CONFIGURATION, "myAuthConfig");
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        operation.get(ElytronDescriptionConstants.WEBSERVICES).set(webservices);
        assertFailed(services.executeOperation(operation));
    }

    @Test
    public void testAddInvalidWSSecurityWebservicesAuthConfig() {
        ModelNode webservices = new ModelNode();
        webservices.get(ElytronDescriptionConstants.HTTP_MECHANISM).set("InvalidToken");
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(ElytronDescriptionConstants.AUTHENTICATION_CONFIGURATION, "myAuthConfig");
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        operation.get(ElytronDescriptionConstants.WEBSERVICES).set(webservices);
        assertFailed(services.executeOperation(operation));
    }

    @Test
    public void testRemoveWebservicesAuthConfig() {
        ModelNode webservices = new ModelNode();

        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(ElytronDescriptionConstants.AUTHENTICATION_CONFIGURATION, "myAuthConfig");
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        operation.get(ElytronDescriptionConstants.WEBSERVICES).set(webservices);
        assertSuccess(services.executeOperation(operation));

        operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(ElytronDescriptionConstants.AUTHENTICATION_CONFIGURATION, "myAuthConfig");
        operation.get(ClientConstants.OP).set(ClientConstants.READ_RESOURCE_OPERATION);
        ModelNode result = assertSuccess(services.executeOperation(operation)).get(ClientConstants.RESULT);
        assertEquals(webservices, result.get(ElytronDescriptionConstants.WEBSERVICES));

        operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(ElytronDescriptionConstants.AUTHENTICATION_CONFIGURATION, "myAuthConfig");
        operation.get(ClientConstants.OP).set(ClientConstants.REMOVE_OPERATION);
        assertSuccess(services.executeOperation(operation));

        operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(ElytronDescriptionConstants.AUTHENTICATION_CONFIGURATION, "myAuthConfig");
        operation.get(ClientConstants.OP).set(ClientConstants.READ_RESOURCE_OPERATION);
        assertFailed(services.executeOperation(operation)).get(ClientConstants.RESULT);
    }
}
