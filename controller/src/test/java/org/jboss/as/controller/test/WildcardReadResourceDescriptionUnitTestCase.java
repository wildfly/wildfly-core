/*
Copyright 2016 Red Hat, Inc.

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

package org.jboss.as.controller.test;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.NoopOperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.OverrideDescriptionProvider;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.operations.global.GlobalNotifications;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.Test;

/**
 * Tests of handling of wildcard addresses in the read-resource operation.
 *
 * This one is separate from WildcardReadResourceUnitTestCase because r-r-d
 * has special handling for the /host=* /server=*  pattern and the test setup
 * for WildcardReadResourceUnitTestCase uses that pattern. I could have removed
 * that pattern but I elected not to disturb that test and just create a new one.
 *
 * @author Brian Stansberry
 */
public class WildcardReadResourceDescriptionUnitTestCase  extends AbstractControllerTestBase {

    private static final PathElement webSubsystem = PathElement.pathElement("subsystem", "web");
    private static final PathElement connector = PathElement.pathElement("connector");
    private static final PathElement defaultConnector = PathElement.pathElement("connector", "default");
    private static final PathElement specialConnector = PathElement.pathElement("connector", "special");
    private static final PathElement statistics = PathElement.pathElement("statistics", "test");

    private static final PathElement otherSubsystem = PathElement.pathElement("subsystem", "other");
    private static final PathElement server = PathElement.pathElement("server");
    private static final PathElement resource = PathElement.pathElement("resource");

    @Test
    public void testReadResourceDescription() throws Exception {
        final ModelController controller = getController();

        // Non-wildcard path down to connector
        ModelNode address = new ModelNode();
        address.add("subsystem", "web");
        address.add("connector", "*");

        final ModelNode read = new ModelNode();
        read.get(OP).set("read-resource-description");
        read.get(OP_ADDR).set(address);
        read.get("recursive").set(true);

        ModelNode response = controller.execute(read, null, null, null);
        assertEquals(response.toString(), "success", response.get("outcome").asString());
        ModelNode result = response.get("result");

        assertEquals(result.toString(), 2, result.asInt());  // connector=* and connector=special
        Boolean foundSpecial = null;
        for (ModelNode node : result.asList()) {
            foundSpecial = validateNode(result, node, foundSpecial);
        }

        // Wildcard path
        address = new ModelNode();
        address.add("subsystem", "*");
        address.add("connector", "*");

        read.get(OP_ADDR).set(address);

        response = controller.execute(read, null, null, null);
        assertEquals(response.toString(), "success", response.get("outcome").asString());
        result = response.get("result");

        assertEquals(result.toString(), 2, result.asInt());foundSpecial = null;
        for (ModelNode node : result.asList()) {
            foundSpecial = validateNode(result, node, foundSpecial);
        }

        // TODO something like this could test a WFCORE-1751 fix
        /*
        address = new ModelNode();
        address.add("subsystem", "[web,other]");
        address.add("connector", "*");

        read.get(OP_ADDR).set(address);

        response = controller.execute(read, null, null, null);
        assertEquals(response.toString(), "success", response.get("outcome").asString());
        result = response.get("result");

        assertEquals(result.toString(), 2, result.asInt());
        */

        // Include statistics
        address = new ModelNode();
        address.add("subsystem", "*");
        address.add("connector", "*");
        address.add("statistics", "*");

        read.get(OP_ADDR).set(address);

        response = controller.execute(read, null, null, null);
        assertEquals(response.toString(), "success", response.get("outcome").asString());
        result = response.get("result");

        assertEquals(result.toString(), 1, result.asInt());
        validateStatisticsEntry(result, result.get(0));

        // Fully specify statistics
        address = new ModelNode();
        address.add("subsystem", "*");
        address.add("connector", "*");
        address.add("statistics", "test");

        read.get(OP_ADDR).set(address);

        response = controller.execute(read, null, null, null);
        assertEquals(response.toString(), "success", response.get("outcome").asString());
        result = response.get("result");

        assertEquals(result.toString(), 1, result.asInt());
        validateStatisticsEntry(result, result.get(0));

        // Fully specify connector
        address = new ModelNode();
        address.add("subsystem", "*");
        address.add("connector", "special");
        address.add("statistics", "*");

        read.get(OP_ADDR).set(address);

        response = controller.execute(read, null, null, null);
        assertEquals(response.toString(), "success", response.get("outcome").asString());
        result = response.get("result");

        assertEquals(result.toString(), 1, result.asInt());
        validateStatisticsEntry(result, result.get(0));

        // Fully specify connector and statistics
        address = new ModelNode();
        address.add("subsystem", "*");
        address.add("connector", "special");
        address.add("statistics", "test");

        read.get(OP_ADDR).set(address);

        response = controller.execute(read, null, null, null);
        assertEquals(response.toString(), "success", response.get("outcome").asString());
        result = response.get("result");

        assertEquals(result.toString(), 1, result.asInt());
        validateStatisticsEntry(result, result.get(0));

        // No wildcard
        address = new ModelNode();
        address.add("subsystem", "web");
        address.add("connector", "special");
        address.add("statistics", "test");

        read.get(OP_ADDR).set(address);

        response = controller.execute(read, null, null, null);
        assertEquals(response.toString(), "success", response.get("outcome").asString());
        result = response.get("result");
        validateStatisticsDesc(result, result);

        // Wrong subsystem
        address = new ModelNode();
        address.add("subsystem", "other");
        address.add("connector", "special");
        address.add("statistics", "test");

        read.get(OP_ADDR).set(address);

        response = controller.execute(read, null, null, null);
        validateFailedResponse(response, address);

        // Wrong connector
        address = new ModelNode();
        address.add("subsystem", "web");
        address.add("connector", "default");
        address.add("statistics", "test");

        read.get(OP_ADDR).set(address);

        response = controller.execute(read, null, null, null);
        validateFailedResponse(response, address);

        // Wrong connector, wildcard subsystem
        address = new ModelNode();
        address.add("subsystem", "*");
        address.add("connector", "default");
        address.add("statistics", "test");

        read.get(OP_ADDR).set(address);

        response = controller.execute(read, null, null, null);
        validateFailedResponse(response, address);

        // Wrong connector, wildcard subsystem and stats
        address = new ModelNode();
        address.add("subsystem", "*");
        address.add("connector", "default");
        address.add("statistics", "*");

        read.get(OP_ADDR).set(address);

        response = controller.execute(read, null, null, null);
        validateFailedResponse(response, address);

        // Bogus child
        address = new ModelNode();
        address.add("subsystem", "web");
        address.add("connector", "special");
        address.add("statistics", "test");
        address.add("bogus", "*");

        read.get(OP_ADDR).set(address);

        response = controller.execute(read, null, null, null);
        validateFailedResponse(response, address);

        // Bogus child, wildcard parent
        address = new ModelNode();
        address.add("subsystem", "web");
        address.add("connector", "special");
        address.add("statistics", "*");
        address.add("bogus", "*");

        read.get(OP_ADDR).set(address);

        response = controller.execute(read, null, null, null);
        validateFailedResponse(response, address);

        // Bogus child, wildcard grandparent
        address = new ModelNode();
        address.add("subsystem", "web");
        address.add("connector", "*");
        address.add("statistics", "*");
        address.add("bogus", "*");

        read.get(OP_ADDR).set(address);

        response = controller.execute(read, null, null, null);
        validateFailedResponse(response, address);

        // Bogus child, wildcard ancestors
        address = new ModelNode();
        address.add("subsystem", "*");
        address.add("connector", "*");
        address.add("statistics", "*");
        address.add("bogus", "*");

        read.get(OP_ADDR).set(address);

        response = controller.execute(read, null, null, null);
        validateFailedResponse(response, address);

        // Bogus child, wrong connector
        address = new ModelNode();
        address.add("subsystem", "*");
        address.add("connector", "default");
        address.add("statistics", "*");
        address.add("bogus", "*");

        read.get(OP_ADDR).set(address);

        response = controller.execute(read, null, null, null);
        validateFailedResponse(response, PathAddress.pathAddress(address).getParent());

        // Bogus child, wrong subsystem
        address = new ModelNode();
        address.add("subsystem", "other");
        address.add("connector", "*");
        address.add("statistics", "*");
        address.add("bogus", "*");

        read.get(OP_ADDR).set(address);

        response = controller.execute(read, null, null, null);
        // TODO ideally it would fail like this but for this case, unlike some other ones above
        // ModelControllerImpl.DefaultPrepareStepHandler is failing to find the r-r-d OSH
        // so it fails there with the full address.
        //validateFailedResponse(response, PathAddress.pathAddress(address).getParent().getParent());
        // That's not perfect but is acceptable
        validateFailedResponse(response, address);

        // WFCORE-2022 multitarget address with a server resource
        address = new ModelNode();
        address.add("subsystem", "other");
        address.add("server", "*");
        address.add("resource", "*");

        read.get(OP_ADDR).set(address);

        response = controller.execute(read, null, null, null);
        assertEquals(response.toString(), "success", response.get("outcome").asString());
    }

    private boolean validateNode(ModelNode fullResult, ModelNode node) {
        return validateNode(fullResult, node, false);
    }

    private boolean validateNode(ModelNode fullResult, ModelNode node, Boolean foundSpecial) {
        assertEquals(fullResult.toString(), "success", node.get("outcome").asString());
        assertTrue(fullResult.toString(), node.hasDefined("address"));
        assertTrue(fullResult.toString(), node.hasDefined("result"));
        for (int i = 1; i < 7; i++) {
            assertTrue(fullResult.toString(), node.hasDefined("result", "attributes", "" + i));
        }
        assertFalse(fullResult.toString(), node.hasDefined("result", "operations"));
        assertFalse(fullResult.toString(), node.hasDefined("result", "notifications"));
        PathAddress nodePA = PathAddress.pathAddress(node.get("address"));
        if (node.hasDefined("result", "children", "statistics")) {
            assertNotEquals(fullResult.toString(), Boolean.TRUE, foundSpecial);
            assertEquals(fullResult.toString(), specialConnector, nodePA.getLastElement());
            validateStatisticsChildDesc(fullResult, node.get("result", "children", "statistics"));
            return true;
        } else {
            assertNotEquals(fullResult.toString(), Boolean.FALSE, foundSpecial);
            assertNotEquals(fullResult.toString(), specialConnector, nodePA.getLastElement());
            assertEquals(fullResult.toString(), "connector", nodePA.getLastElement().getKey());
            ModelNode children = node.get("result", "children");
            assertEquals(fullResult.toString(), ModelType.OBJECT, children.getType());
            assertEquals(fullResult.toString(), 0, children.asInt());
            return false;
        }
    }

    private void validateStatisticsEntry(ModelNode fullResult, ModelNode node) {
        assertTrue(fullResult.toString(), node.hasDefined("address"));
        assertTrue(fullResult.toString(), node.hasDefined("result"));
        PathAddress nodePA = PathAddress.pathAddress(node.get("address"));
        assertEquals(fullResult.toString(), statistics, nodePA.getLastElement());
        validateStatisticsDesc(fullResult, node.get("result"));

    }

    private void validateStatisticsChildDesc(ModelNode fullResult, ModelNode modelNode) {
        assertTrue(fullResult.toString(), modelNode.hasDefined("description"));
        assertTrue(fullResult.toString(), modelNode.hasDefined("model-description", "test"));
        validateStatisticsDesc(fullResult, modelNode.get("model-description", "test"));
    }

    private void validateStatisticsDesc(ModelNode fullResult, ModelNode desc) {
        assertTrue(fullResult.toString(), desc.hasDefined("attributes", "7"));
        assertFalse(fullResult.toString(), desc.hasDefined("operations"));
        assertFalse(fullResult.toString(), desc.hasDefined("notifications"));
        ModelNode children = desc.get("children");
        assertEquals(fullResult.toString(), ModelType.OBJECT, children.getType());
        assertEquals(fullResult.toString(), 0, children.asInt());
    }

    private void validateFailedResponse(ModelNode response, ModelNode address) {
        validateFailedResponse(response, PathAddress.pathAddress(address));
    }

    private void validateFailedResponse(ModelNode response, PathAddress expected) {
        assertEquals(response.toString(), "failed", response.get("outcome").asString());
        String fd = response.get("failure-description").asString();
        assertTrue(response.toString(), fd.startsWith("WFLYCTL0030"));
        int start = fd.indexOf('[');
        int end = fd.indexOf(']');
        assertTrue(response.toString(), start > 0);
        assertTrue(response.toString(), end > start);
        PathAddress reported = PathAddress.pathAddress(ModelNode.fromString(fd.substring(start, end + 1)));
        assertEquals(response.toString(), expected, reported);
    }

    @Override
    protected void initModel(ManagementModel managementModel) {
        ManagementResourceRegistration root = managementModel.getRootResourceRegistration();
        GlobalOperationHandlers.registerGlobalOperations(root, processType);
        root.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, GenericSubsystemDescribeHandler.INSTANCE);

        root.registerOperationHandler(SimpleOperationDefinitionBuilder.of("setup",
                NonResolvingResourceDescriptionResolver.INSTANCE).build(), NoopOperationStepHandler.WITHOUT_RESULT);

        GlobalNotifications.registerGlobalNotifications(root, processType);


        final ManagementResourceRegistration subsystemW = root.registerSubModel(new SimpleResourceDefinition(webSubsystem, NonResolvingResourceDescriptionResolver.INSTANCE));
        final ManagementResourceRegistration connectors = subsystemW.registerSubModel(new SimpleResourceDefinition(connector, NonResolvingResourceDescriptionResolver.INSTANCE));
        connectors.registerReadOnlyAttribute(TestUtils.createNillableAttribute("1", ModelType.STRING), null);
        connectors.registerReadOnlyAttribute(TestUtils.createNillableAttribute("2", ModelType.STRING), null);
        connectors.registerReadOnlyAttribute(TestUtils.createNillableAttribute("3", ModelType.STRING), null);
        connectors.registerReadOnlyAttribute(TestUtils.createNillableAttribute("4", ModelType.STRING), null);
        connectors.registerReadOnlyAttribute(TestUtils.createNillableAttribute("5", ModelType.STRING), null);
        connectors.registerReadOnlyAttribute(TestUtils.createNillableAttribute("6", ModelType.STRING), null);

        final ManagementResourceRegistration specialConnectors = connectors.registerOverrideModel("special", new OverrideDescriptionProvider() {

            @Override
            public Map<String, ModelNode> getAttributeOverrideDescriptions(Locale locale) {
                return Collections.emptyMap();
            }

            @Override
            public Map<String, ModelNode> getChildTypeOverrideDescriptions(Locale locale) {
                Map<String, ModelNode> children = new HashMap<String, ModelNode>();
                ModelNode node = new ModelNode();
                node.get(DESCRIPTION).set("statistics");
                children.put("statistics", node);
                return children;
            }
        });
        final ManagementResourceRegistration stats = specialConnectors.registerSubModel(
                new SimpleResourceDefinition(statistics,
                        NonResolvingResourceDescriptionResolver.INSTANCE));
        stats.registerReadOnlyAttribute(TestUtils.createNillableAttribute("7", ModelType.STRING), null);

        ManagementResourceRegistration otherSubsystemModel = root.registerSubModel(new SimpleResourceDefinition(otherSubsystem, NonResolvingResourceDescriptionResolver.INSTANCE));
        ManagementResourceRegistration serverModel = otherSubsystemModel.registerSubModel(new SimpleResourceDefinition(server, NonResolvingResourceDescriptionResolver.INSTANCE));
        serverModel.registerSubModel(new SimpleResourceDefinition(resource, NonResolvingResourceDescriptionResolver.INSTANCE));

    }
}
