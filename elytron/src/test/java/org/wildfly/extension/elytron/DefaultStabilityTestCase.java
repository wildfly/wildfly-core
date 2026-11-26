/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.elytron;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.subsystem.test.AbstractSubsystemTest;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ATTRIBUTES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_DESCRIPTION_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;

public class DefaultStabilityTestCase extends AbstractSubsystemTest {

    private static final String DYNAMIC_SSL_CLIENT_CONTEXT_NAME = "dcsc";
    private static final String TOKEN_REALM_NAME = "TokenRealmWithTransformer";
    private static final String PRINCIPAL_TRANSFORMER_NAME = "myCasePrincipalTransformerUpper";
    private static final String SUBSYSTEM = "subsystem";
    private static final String ELYTRON = "elytron";

    public DefaultStabilityTestCase() {
        super(ElytronExtension.SUBSYSTEM_NAME, new ElytronExtension(), Stability.DEFAULT);
    }

    private static KernelServices services = null;

    @Before
    public void initServices() throws Exception {
        TestEnvironment testEnvironment = new TestEnvironment(Stability.DEFAULT);
        services = super.createKernelServicesBuilder(testEnvironment).setSubsystemXmlResource("authentication-client.xml").build();
        if (!services.isSuccessfulBoot()) {
            if (services.getBootError() != null) {
                Assert.fail(services.getBootError().toString());
            }
            Assert.fail("Failed to boot, no reason provided");
        }
    }

    @Test
    public void testAddDynamicClientSSLContextPassesInDefaultStability() {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR)
                .add(SUBSYSTEM, ELYTRON).add(ElytronDescriptionConstants.DYNAMIC_CLIENT_SSL_CONTEXT, DYNAMIC_SSL_CLIENT_CONTEXT_NAME);
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        operation.get(ElytronDescriptionConstants.AUTHENTICATION_CONTEXT).set("ac");
        ModelNode response = services.executeOperation(operation);

        if (response.get(OUTCOME).asString().equals(FAILED)) {
            Assert.fail(response.toJSONString(false));
        }

        if (response.get("failure-description").asString().contains("No resource definition is registered for address")) {
            Assert.fail(response.toJSONString(false));
        }
    }

    /**
     * The {@code principal-transformer} attribute of {@code token-realm} is registered at
     * {@link Stability#COMMUNITY}, so at {@link Stability#DEFAULT} {@code ConcreteResourceRegistration} suppresses
     * the registration. The attribute is then absent from the resource description, an {@code add} supplying it
     * drops it silently instead of storing it, and it cannot be written.
     */
    @Test
    public void testTokenRealmPrincipalTransformerNotRegisteredInDefaultStability() {
        PathAddress address = PathAddress.pathAddress(SUBSYSTEM, ELYTRON)
                .append(ElytronDescriptionConstants.TOKEN_REALM, TOKEN_REALM_NAME);

        ModelNode add = Util.createAddOperation(address);
        add.get(ElytronDescriptionConstants.PRINCIPAL_CLAIM).set("sub");
        add.get(ElytronDescriptionConstants.PRINCIPAL_TRANSFORMER).set(PRINCIPAL_TRANSFORMER_NAME);
        ModelNode response = services.executeOperation(add);
        Assert.assertEquals(response.toJSONString(false), SUCCESS, response.get(OUTCOME).asString());

        // The attribute is not part of the management model exposed at DEFAULT stability
        ModelNode description = services.executeOperation(Util.createOperation(READ_RESOURCE_DESCRIPTION_OPERATION, address));
        Assert.assertEquals(description.toJSONString(false), SUCCESS, description.get(OUTCOME).asString());
        Assert.assertFalse(description.toJSONString(false),
                description.get(RESULT, ATTRIBUTES).hasDefined(ElytronDescriptionConstants.PRINCIPAL_TRANSFORMER));

        // The value supplied to the add operation is dropped rather than stored
        ModelNode resource = services.executeOperation(Util.createOperation(READ_RESOURCE_OPERATION, address));
        Assert.assertEquals(resource.toJSONString(false), SUCCESS, resource.get(OUTCOME).asString());
        Assert.assertFalse(resource.toJSONString(false),
                resource.get(RESULT).hasDefined(ElytronDescriptionConstants.PRINCIPAL_TRANSFORMER));

        // The value cannot be set afterwards either
        ModelNode write = services.executeOperation(Util.getWriteAttributeOperation(address,
                ElytronDescriptionConstants.PRINCIPAL_TRANSFORMER, PRINCIPAL_TRANSFORMER_NAME));
        Assert.assertEquals(write.toJSONString(false), FAILED, write.get(OUTCOME).asString());
    }

    /**
     * The {@code accept-ocsp-stapling} attribute of {@code trust-manager} is registered at {@link Stability#PREVIEW},
     * so at {@link Stability#DEFAULT} it is absent from the resource description, an {@code add} supplying it drops it
     * silently instead of storing it, and it cannot be written.
     */
    @Test
    public void testAcceptOcspStaplingNotRegisteredInDefaultStability() {
        PathAddress address = PathAddress.pathAddress(SUBSYSTEM, ELYTRON)
                .append(ElytronDescriptionConstants.TRUST_MANAGER, "stapling");

        ModelNode add = Util.createAddOperation(address);
        add.get(ElytronDescriptionConstants.KEY_STORE).set("accounts.keystore");
        add.get(ElytronDescriptionConstants.ACCEPT_OCSP_STAPLING).set(true);
        ModelNode response = services.executeOperation(add);
        Assert.assertEquals(response.toJSONString(false), SUCCESS, response.get(OUTCOME).asString());

        ModelNode description = services.executeOperation(Util.createOperation(READ_RESOURCE_DESCRIPTION_OPERATION, address));
        Assert.assertEquals(description.toJSONString(false), SUCCESS, description.get(OUTCOME).asString());
        Assert.assertFalse(description.toJSONString(false),
                description.get(RESULT, ATTRIBUTES).hasDefined(ElytronDescriptionConstants.ACCEPT_OCSP_STAPLING));

        ModelNode resource = services.executeOperation(Util.createOperation(READ_RESOURCE_OPERATION, address));
        Assert.assertEquals(resource.toJSONString(false), SUCCESS, resource.get(OUTCOME).asString());
        Assert.assertFalse(resource.toJSONString(false),
                resource.get(RESULT).hasDefined(ElytronDescriptionConstants.ACCEPT_OCSP_STAPLING));

        ModelNode write = services.executeOperation(Util.getWriteAttributeOperation(address,
                ElytronDescriptionConstants.ACCEPT_OCSP_STAPLING, true));
        Assert.assertEquals(write.toJSONString(false), FAILED, write.get(OUTCOME).asString());
    }

    /**
     * Only the preview schema accepts {@code accept-ocsp-stapling}; every schema enabled at {@link Stability#DEFAULT}
     * rejects it at parse time. Community and preview namespaces are not parsable at this stability.
     */
    @Test
    public void testAcceptOcspStaplingRejectedByDefaultSchemas() throws Exception {
        for (ElytronSubsystemSchema schema : ElytronSubsystemSchema.values()) {
            if (schema.getStability() != Stability.DEFAULT) {
                continue;
            }
            String xml = "<subsystem xmlns=\"" + schema.getNamespace().getUri() + "\"><tls><trust-managers>"
                    + "<trust-manager name=\"tm\" key-store=\"store\" accept-ocsp-stapling=\"true\"/>"
                    + "</trust-managers></tls></subsystem>";
            try {
                parse(xml);
                Assert.fail("Accepted accept-ocsp-stapling in " + schema);
            } catch (XMLStreamException expected) {
                Assert.assertTrue(expected.getMessage(),
                        expected.getMessage().contains(ElytronDescriptionConstants.ACCEPT_OCSP_STAPLING));
            }
        }
    }
}