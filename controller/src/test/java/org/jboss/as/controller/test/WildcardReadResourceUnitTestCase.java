/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.test;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
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
 * @author Emanuel Muckenhuber
 */
public class WildcardReadResourceUnitTestCase extends AbstractControllerTestBase {

    private static final PathElement host = PathElement.pathElement("host");
    private static final PathElement server = PathElement.pathElement("server");
    private static final PathElement subsystem = PathElement.pathElement("subsystem");
    private static final PathElement connector = PathElement.pathElement("connector");

    @Test
    public void testReadResource() throws Exception {
        final ModelController controller = getController();

        final ModelNode address = new ModelNode();
        address.add("host", "*");
        address.add("server", "[one,two]");
        address.add("subsystem", "web");
        address.add("connector", "*");

        final ModelNode read = new ModelNode();
        read.get(OP).set("read-resource");
        read.get(OP_ADDR).set(address);
        read.get("recursive").set(true);

        ModelNode result = controller.execute(read, null, null, null);
        result = result.get("result");

        assertEquals(result.toString(), 5, result.asInt()); // A,B one,two and two connector variants on B two

        final Map<PathAddress, String> keysByAddress = new HashMap<>();
        PathElement hostA = PathElement.pathElement("host", "A");
        PathElement hostB = PathElement.pathElement("host", "B");
        PathElement server1 = PathElement.pathElement("server", "one");
        PathElement server2 = PathElement.pathElement("server", "two");
        PathElement subs = PathElement.pathElement("subsystem", "web");
        PathElement conn = PathElement.pathElement("connector", "default");
        keysByAddress.put(PathAddress.pathAddress(hostA, server1, subs, conn), "1");
        keysByAddress.put(PathAddress.pathAddress(hostA, server2, subs, conn), "2");
        keysByAddress.put(PathAddress.pathAddress(hostB, server1, subs, conn), "4");
        keysByAddress.put(PathAddress.pathAddress(hostB, server2, subs, conn), "5");

        ModelNode specialNode = null;
        for (ModelNode node : result.asList()) {
            assertEquals(result.toString(), "success", node.get("outcome").asString());
            assertTrue(result.toString(), node.hasDefined("address"));
            assertTrue(result.toString(), node.hasDefined("result"));
            PathAddress nodePA = PathAddress.pathAddress(node.get("address"));
            String key = keysByAddress.get(nodePA);
            if (key != null) {
                assertTrue(result.toString(), node.hasDefined("result", key));
                ModelNode val = node.get("result", key);
                assertEquals(result.toString(), ModelType.OBJECT, val.getType());
                assertEquals(result.toString(), 0, val.asInt());
            } else {
                assertNull(result.toString(), specialNode);
                specialNode = node;
            }
        }

        assertNotNull(result.toString(), specialNode);
        PathAddress specialPA = PathAddress.pathAddress(specialNode.get("address"));
        assertEquals(result.toString(), PathAddress.pathAddress(hostB, server2, subs, PathElement.pathElement("connector", "special")), specialPA);

        assertTrue(result.toString(), specialNode.hasDefined("result", "statistics", "test", "7"));
        ModelNode val = specialNode.get("result", "statistics", "test", "7");
        assertEquals(result.toString(), ModelType.OBJECT, val.getType());
        assertEquals(result.toString(), 0, val.asInt());
    }

    @Override
    protected void initModel(ManagementModel managementModel) {
        ManagementResourceRegistration root = managementModel.getRootResourceRegistration();
        GlobalOperationHandlers.registerGlobalOperations(root, processType);
        root.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, GenericSubsystemDescribeHandler.INSTANCE);

        root.registerOperationHandler(SimpleOperationDefinitionBuilder.of("setup",
                NonResolvingResourceDescriptionResolver.INSTANCE).build(), new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

                final ModelNode model = new ModelNode();

                model.get("host", "A", "server", "one", "subsystem", "web", "connector", "default", "1").setEmptyObject();
                model.get("host", "A", "server", "two", "subsystem", "web", "connector", "default", "2").setEmptyObject();
                model.get("host", "A", "server", "three", "subsystem", "web", "connector", "other", "3").setEmptyObject();
                model.get("host", "B", "server", "one", "subsystem", "web", "connector", "default", "4").setEmptyObject();
                model.get("host", "B", "server", "two", "subsystem", "web", "connector", "default", "5").setEmptyObject();
                model.get("host", "B", "server", "three", "subsystem", "web", "connector", "default", "6").setEmptyObject();
                model.get("host", "B", "server", "two", "subsystem", "web", "connector", "special", "6").setEmptyObject();
                model.get("host", "B", "server", "two", "subsystem", "web", "connector", "special", "statistics", "test", "7").setEmptyObject();

                createModel(context, model);
            }
        });

        GlobalNotifications.registerGlobalNotifications(root, processType);


        final ManagementResourceRegistration hosts = root.registerSubModel(new SimpleResourceDefinition(host, NonResolvingResourceDescriptionResolver.INSTANCE));
        final ManagementResourceRegistration servers = hosts.registerSubModel(new SimpleResourceDefinition(server, NonResolvingResourceDescriptionResolver.INSTANCE));
        final ManagementResourceRegistration subsystems = servers.registerSubModel(new SimpleResourceDefinition(subsystem, NonResolvingResourceDescriptionResolver.INSTANCE));
        final ManagementResourceRegistration connectors = subsystems.registerSubModel(new SimpleResourceDefinition(connector, NonResolvingResourceDescriptionResolver.INSTANCE));
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
                new SimpleResourceDefinition(PathElement.pathElement("statistics", "test"),
                        NonResolvingResourceDescriptionResolver.INSTANCE));
        stats.registerReadOnlyAttribute(TestUtils.createNillableAttribute("7", ModelType.STRING), null);
    }
}
