package org.wildfly.extension.elytron;

import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.subsystem.test.AbstractSubsystemTest;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.junit.Assert.assertEquals;

public class AuthenticationClientTestCase extends AbstractSubsystemTest {
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
            Assert.fail(services.getBootError().toString());
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
