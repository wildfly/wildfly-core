/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller;

import static org.jboss.as.controller.PathAddress.pathAddress;
import static org.jboss.as.controller.PathElement.pathElement;
import static org.jboss.as.controller.SimpleAttributeDefinitionBuilder.create;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UNDEFINE_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;
import static org.jboss.dmr.ModelType.STRING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.persistence.ConfigurationExtension;
import org.jboss.as.controller.persistence.ConfigurationExtensionFactory;
import org.jboss.as.controller.persistence.yaml.YamlConfigurationExtension;
import org.jboss.as.controller.persistence.yaml.YamlConfigurationExtensionTest;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.BeforeClass;
import org.junit.Test;

public class YamlConfigurationParallelBootTest {

    private static final String MY_RESOURCE = "my-resource";
    private static ImmutableManagementResourceRegistration rootRegistration;
    private static final PathElement SUBSYSTEM_SYSTEM_PROPERTIES_PATH = pathElement(SUBSYSTEM, "system-properties");
    private static final PathElement SUBSYSTEM_PROPERTIES_PATH = pathElement(SUBSYSTEM, "properties");
    private static final PathElement SUBSYSTEM_CLASSPATH_PATH = pathElement(SUBSYSTEM, "classpaths");
    private static final PathElement SUBSYSTEM_BASIC_PATH = pathElement(SUBSYSTEM, "basics");
    private static final PathElement SUBSYSTEM_LIST_PATH = pathElement(SUBSYSTEM, "lists");

    @BeforeClass
    public static void setUp() {
        StandardResourceDescriptionResolver descriptionResolver = new StandardResourceDescriptionResolver(MY_RESOURCE, YamlConfigurationExtensionTest.class.getName(), Thread.currentThread().getContextClassLoader());
        SimpleResourceDefinition rootResource = new SimpleResourceDefinition(new SimpleResourceDefinition.Parameters(ResourceRegistration.root(), descriptionResolver));
        AttributeDefinition valueAtt = SimpleAttributeDefinitionBuilder.create(VALUE, STRING, true)
                .setAllowExpression(true)
                .setValidator(new StringLengthValidator(0, true, true))
                .build();
        SimpleResourceDefinition systemPropertyResource = new SimpleResourceDefinition(new SimpleResourceDefinition.Parameters(PathElement.pathElement("system-property"), descriptionResolver)
                .setAddHandler(new AbstractBoottimeAddStepHandler() {
                })) {
            @Override
            public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
                super.registerAttributes(resourceRegistration);
                resourceRegistration.registerReadWriteAttribute(valueAtt, null, ModelOnlyWriteAttributeHandler.INSTANCE);
            }

            @Override
            public void registerOperations(ManagementResourceRegistration resourceRegistration) {
                super.registerOperations(resourceRegistration);
                resourceRegistration.registerOperationHandler(SimpleOperationDefinitionBuilder.of(WRITE_ATTRIBUTE_OPERATION, descriptionResolver).build(), ModelOnlyWriteAttributeHandler.INSTANCE);
            }
        };
        SimpleResourceDefinition subsystemSystemPropertyResource = new SimpleResourceDefinition(new SimpleResourceDefinition.Parameters(SUBSYSTEM_SYSTEM_PROPERTIES_PATH, descriptionResolver)
                .setAddHandler(new AbstractBoottimeAddStepHandler() {
                })) {
            @Override
            public void registerChildren(ManagementResourceRegistration resourceRegistration) {
                super.registerChildren(resourceRegistration);
                resourceRegistration.registerSubModel(systemPropertyResource);
            }
        };
        SimpleResourceDefinition extensionResource = new SimpleResourceDefinition(new SimpleResourceDefinition.Parameters(pathElement("extension"), descriptionResolver)
                .setAddHandler(new AbstractBoottimeAddStepHandler() {
                }));
        SimpleResourceDefinition subsystemExtensionResource = new SimpleResourceDefinition(new SimpleResourceDefinition.Parameters(pathElement(SUBSYSTEM, "extensions"), descriptionResolver)
                .setAddHandler(new AbstractBoottimeAddStepHandler() {
                })) {
            @Override
            public void registerChildren(ManagementResourceRegistration resourceRegistration) {
                super.registerChildren(resourceRegistration);
                resourceRegistration.registerSubModel(extensionResource);
            }
        };
        AttributeDefinition listAtt = PrimitiveListAttributeDefinition.Builder.of("strings", STRING).build();
        SimpleResourceDefinition listResource = new SimpleResourceDefinition(new SimpleResourceDefinition.Parameters(pathElement("list"), descriptionResolver)
                .setAddHandler(new AbstractBoottimeAddStepHandler() {
                })) {
            @Override
            public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
                super.registerAttributes(resourceRegistration);
                resourceRegistration.registerReadWriteAttribute(listAtt, null, ModelOnlyWriteAttributeHandler.INSTANCE);
            }
        };
        SimpleResourceDefinition subsystemListResource = new SimpleResourceDefinition(new SimpleResourceDefinition.Parameters(SUBSYSTEM_LIST_PATH, descriptionResolver)
                .setAddHandler(new AbstractBoottimeAddStepHandler() {
                })) {
            @Override
            public void registerChildren(ManagementResourceRegistration resourceRegistration) {
                super.registerChildren(resourceRegistration);
                resourceRegistration.registerSubModel(listResource);
            }
        };
        SimpleResourceDefinition basicResource = new SimpleResourceDefinition(new SimpleResourceDefinition.Parameters(pathElement("basic"), descriptionResolver)
                .setAddHandler(new AbstractBoottimeAddStepHandler() {
                })) {
            @Override
            public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
                super.registerAttributes(resourceRegistration);
                resourceRegistration.registerReadWriteAttribute(valueAtt, null, ModelOnlyWriteAttributeHandler.INSTANCE);
            }
        };
        SimpleResourceDefinition subsystemBasicResource = new SimpleResourceDefinition(new SimpleResourceDefinition.Parameters(SUBSYSTEM_BASIC_PATH, descriptionResolver)
                .setAddHandler(new AbstractBoottimeAddStepHandler() {
                })) {
            @Override
            public void registerChildren(ManagementResourceRegistration resourceRegistration) {
                super.registerChildren(resourceRegistration);
                resourceRegistration.registerSubModel(basicResource);
            }
        };
        PropertiesAttributeDefinition propertiesAtt = new PropertiesAttributeDefinition.Builder("props", false).build();
        SimpleResourceDefinition propertyResource = new SimpleResourceDefinition(new SimpleResourceDefinition.Parameters(pathElement("property"), descriptionResolver)
                .setAddHandler(new AbstractBoottimeAddStepHandler() {
                })) {
            @Override
            public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
                super.registerAttributes(resourceRegistration);
                resourceRegistration.registerReadWriteAttribute(propertiesAtt, null, ModelOnlyWriteAttributeHandler.INSTANCE);
            }

            @Override
            public void registerOperations(ManagementResourceRegistration resourceRegistration) {
                super.registerOperations(resourceRegistration);
                resourceRegistration.registerOperationHandler(SimpleOperationDefinitionBuilder.of(WRITE_ATTRIBUTE_OPERATION, descriptionResolver).build(), ModelOnlyWriteAttributeHandler.INSTANCE);
            }
        };
        SimpleResourceDefinition subsystemPropertyResource = new SimpleResourceDefinition(new SimpleResourceDefinition.Parameters(SUBSYSTEM_PROPERTIES_PATH, descriptionResolver)
                .setAddHandler(new AbstractBoottimeAddStepHandler() {
                })) {
            @Override
            public void registerChildren(ManagementResourceRegistration resourceRegistration) {
                super.registerChildren(resourceRegistration);
                resourceRegistration.registerSubModel(propertyResource);
            }
        };
        ObjectTypeAttributeDefinition CLASS = ObjectTypeAttributeDefinition.Builder.of("class",
                create("class-name", ModelType.STRING, false)
                        .setAllowExpression(false)
                        .build(),
                create("module", ModelType.STRING, false)
                        .setAllowExpression(false)
                        .build())
                .build();
        ObjectMapAttributeDefinition COMPLEX_MAP = ObjectMapAttributeDefinition.create("complex-map", CLASS)
                .setRequired(false)
                .build();
        SimpleResourceDefinition classpathResource = new SimpleResourceDefinition(new SimpleResourceDefinition.Parameters(PathElement.pathElement("classpath"), descriptionResolver)
                .setAddHandler(new AbstractBoottimeAddStepHandler() {
                })) {
            @Override
            public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
                super.registerAttributes(resourceRegistration);
                resourceRegistration.registerReadWriteAttribute(COMPLEX_MAP, null, ModelOnlyWriteAttributeHandler.INSTANCE);
            }
        };
        SimpleResourceDefinition subsystemClasspathResource = new SimpleResourceDefinition(new SimpleResourceDefinition.Parameters(SUBSYSTEM_CLASSPATH_PATH, descriptionResolver)
                .setAddHandler(new AbstractBoottimeAddStepHandler() {
                })) {
            @Override
            public void registerChildren(ManagementResourceRegistration resourceRegistration) {
                super.registerChildren(resourceRegistration);
                resourceRegistration.registerSubModel(classpathResource);
            }
        };
        SimpleResourceDefinition childResource = new SimpleResourceDefinition(new SimpleResourceDefinition.Parameters(PathElement.pathElement("child"), descriptionResolver)
                .setAddHandler(new AbstractBoottimeAddStepHandler() {
                })) {
            @Override
            public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
                super.registerAttributes(resourceRegistration);
                resourceRegistration.registerReadWriteAttribute(valueAtt, null, ModelOnlyWriteAttributeHandler.INSTANCE);
            }
        };
        SimpleResourceDefinition parentResource = new SimpleResourceDefinition(new SimpleResourceDefinition.Parameters(PathElement.pathElement("parent"), descriptionResolver)
                .setAddHandler(new AbstractBoottimeAddStepHandler() {
                })) {
            @Override
            public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
                super.registerAttributes(resourceRegistration);
                resourceRegistration.registerReadWriteAttribute(valueAtt, null, ModelOnlyWriteAttributeHandler.INSTANCE);
            }

            @Override
            public void registerChildren(ManagementResourceRegistration resourceRegistration) {
                super.registerChildren(resourceRegistration);
                resourceRegistration.registerSubModel(childResource);
            }
        };
        SimpleResourceDefinition subsystemParentResource = new SimpleResourceDefinition(new SimpleResourceDefinition.Parameters(PathElement.pathElement(SUBSYSTEM, "parents"), descriptionResolver)
                .setAddHandler(new AbstractBoottimeAddStepHandler() {
                })) {
            @Override
            public void registerChildren(ManagementResourceRegistration resourceRegistration) {
                super.registerChildren(resourceRegistration);
                resourceRegistration.registerSubModel(parentResource);
            }
        };
        ManagementResourceRegistration root = ManagementResourceRegistration.Factory.forProcessType(ProcessType.EMBEDDED_SERVER).createRegistration(rootResource);
        GlobalOperationHandlers.registerGlobalOperations(root, ProcessType.EMBEDDED_SERVER);
        root.registerSubModel(subsystemSystemPropertyResource);
        root.registerSubModel(subsystemExtensionResource);
        root.registerSubModel(subsystemListResource);
        root.registerSubModel(subsystemBasicResource);
        root.registerSubModel(subsystemPropertyResource);
        root.registerSubModel(subsystemClasspathResource);
        root.registerSubModel(subsystemParentResource);

        AttributeDefinition connectorType = SimpleAttributeDefinitionBuilder.create("type", STRING, true)
                .setAllowExpression(true)
                .setValidator(new StringLengthValidator(0, true, true))
                .build();
        ManagementResourceRegistration connectorRegistration = root.registerSubModel(new SimpleResourceDefinition(new SimpleResourceDefinition.Parameters(
                PathElement.pathElement("connector"), NonResolvingResourceDescriptionResolver.INSTANCE)
                .setAddHandler(new AbstractBoottimeAddStepHandler() {
                })
                .setRemoveHandler(new AbstractRemoveStepHandler() {
                }))
        );
        connectorRegistration.registerReadWriteAttribute(connectorType, null, ModelOnlyWriteAttributeHandler.INSTANCE);
        ManagementResourceRegistration acceptorRegistration = connectorRegistration.registerSubModel(
                new SimpleResourceDefinition(
                        new SimpleResourceDefinition.Parameters(PathElement.pathElement("acceptor"), NonResolvingResourceDescriptionResolver.INSTANCE)
                                .setAddHandler(new AbstractBoottimeAddStepHandler() {
                                })
                                .setRemoveHandler(new AbstractRemoveStepHandler() {
                                }))
        );
        acceptorRegistration.registerReadWriteAttribute(connectorType, null, ModelOnlyWriteAttributeHandler.INSTANCE);
        rootRegistration = root;
    }

    /**
     * Verify adding new resources.
     *
     * @throws java.net.URISyntaxException
     */
    @Test
    public void testAddingNewResources() throws URISyntaxException {
        List<ParsedBootOp> postExtensionOps = new ArrayList<>();
        postExtensionOps.add(createParallelBootOperation(
                new ParsedBootOp(Operations.createAddOperation(pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH).toModelNode()), null),
                new ParsedBootOp(Operations.createAddOperation(pathAddress(SUBSYSTEM_PROPERTIES_PATH).toModelNode()), null),
                new ParsedBootOp(Operations.createAddOperation(pathAddress(SUBSYSTEM_CLASSPATH_PATH).toModelNode()), null)
        ));
        ConfigurationExtension instance = ConfigurationExtensionFactory.createConfigurationExtension(getYamlPath("simple.yml"));
        instance.processOperations(rootRegistration, postExtensionOps);
        assertFalse(postExtensionOps.isEmpty());
        assertEquals(1, postExtensionOps.size());
        List<ModelNode> childOperations = postExtensionOps.get(0).getChildOperations();
        assertEquals("ChildOperations: " + childOperations, 10, childOperations.size());
        postExtensionOps = new ArrayList<>();
        for (ModelNode op : childOperations) {
            postExtensionOps.add(new ParsedBootOp(op, null));
        }
        assertEquals(ADD, postExtensionOps.get(0).operationName);
        assertEquals(pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH), postExtensionOps.get(0).address);
        assertEquals(ADD, postExtensionOps.get(1).operationName);
        assertEquals(pathAddress(SUBSYSTEM_PROPERTIES_PATH), postExtensionOps.get(1).address);
        assertEquals(ADD, postExtensionOps.get(2).operationName);
        assertEquals(pathAddress(SUBSYSTEM_CLASSPATH_PATH), postExtensionOps.get(2).address);
        assertEquals(ADD, postExtensionOps.get(3).operationName);
        assertEquals(pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH, pathElement("system-property", "aaa")), postExtensionOps.get(3).address);
        assertTrue(postExtensionOps.get(3).operation.hasDefined("value"));
        assertEquals(ADD, postExtensionOps.get(4).operationName);
        assertEquals(pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH, pathElement("system-property", "bbb")), postExtensionOps.get(4).address);
        assertTrue(postExtensionOps.get(4).operation.hasDefined("value"));
        assertEquals(ADD, postExtensionOps.get(5).operationName);
        assertEquals(pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH, pathElement("system-property", "ccc")), postExtensionOps.get(5).address);
        assertTrue(postExtensionOps.get(5).operation.hasDefined("value"));
        assertEquals("test", postExtensionOps.get(5).operation.get("value").asString());
        assertEquals(ADD, postExtensionOps.get(6).operationName);
        assertEquals(pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH, pathElement("system-property", "value")), postExtensionOps.get(6).address);
        assertTrue(postExtensionOps.get(6).operation.hasDefined("value"));
        assertEquals("test", postExtensionOps.get(6).operation.get("value").asString());
        assertEquals(ADD, postExtensionOps.get(7).operationName);
        assertEquals(pathAddress(SUBSYSTEM_BASIC_PATH, pathElement("basic", "test")), postExtensionOps.get(7).address);
        assertFalse(postExtensionOps.get(7).operation.hasDefined("value"));
        assertEquals(ADD, postExtensionOps.get(8).operationName);
        assertEquals(pathAddress(SUBSYSTEM_PROPERTIES_PATH, pathElement("property", "test-property")), postExtensionOps.get(8).address);
        assertTrue(postExtensionOps.get(8).operation.hasDefined("props"));
        ModelNode properties = postExtensionOps.get(8).operation.get("props").asObject();
        assertEquals("0", properties.get("ip_ttl").asString());
        assertEquals("5", properties.get("tcp_ttl").asString());
        assertEquals(ADD, postExtensionOps.get(9).operationName);
        assertEquals(pathAddress(SUBSYSTEM_CLASSPATH_PATH, pathElement("classpath", "runtime")), postExtensionOps.get(9).address);
        assertTrue(postExtensionOps.get(9).operation.hasDefined("complex-map"));
        ModelNode objectMap = postExtensionOps.get(9).operation.get("complex-map").asObject();
        assertEquals("org.widlfly.test.Main", objectMap.get("main-class").asObject().get("class-name").asString());
        assertEquals("org.wildfly.test:main", objectMap.get("main-class").asObject().get("module").asString());
        assertEquals("org.widlfly.test.MyTest", objectMap.get("test-class").asObject().get("class-name").asString());
        assertEquals("org.wildfly.test:main", objectMap.get("test-class").asObject().get("module").asString());

    }

    /**
     * Verify setting attributes.
     *
     * @throws java.net.URISyntaxException
     */
    @Test
    public void testWriteAttribute() throws URISyntaxException {
        List<ParsedBootOp> postExtensionOps = new ArrayList<>();
        postExtensionOps.add(createParallelBootOperation(
                new ParsedBootOp(Operations.createAddOperation(pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH).toModelNode()), null),
                new ParsedBootOp(Operations.createAddOperation(pathAddress(SUBSYSTEM_PROPERTIES_PATH).toModelNode()), null),
                new ParsedBootOp(Operations.createAddOperation(pathAddress(SUBSYSTEM_CLASSPATH_PATH).toModelNode()), null),
                new ParsedBootOp(Operations.createAddOperation(pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH, pathElement("system-property", "aaa")).toModelNode()), null),
                new ParsedBootOp(Operations.createAddOperation(pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH, pathElement("system-property", "bbb")).toModelNode()), null),
                new ParsedBootOp(Operations.createAddOperation(pathAddress(SUBSYSTEM_PROPERTIES_PATH, pathElement("property", "test-property")).toModelNode()), null),
                new ParsedBootOp(Operations.createAddOperation(pathAddress(SUBSYSTEM_CLASSPATH_PATH, pathElement("classpath", "runtime")).toModelNode()), null)
        ));
        ConfigurationExtension instance = new YamlConfigurationExtension();
        instance.load(getYamlPath("simple.yml"));
        instance.processOperations(rootRegistration, postExtensionOps);
        assertFalse(postExtensionOps.isEmpty());
        assertEquals(1, postExtensionOps.size());
        List<ModelNode> childOperations = postExtensionOps.get(0).getChildOperations();
        assertEquals("ChildOperations: " + childOperations, 14, childOperations.size());
        postExtensionOps = new ArrayList<>();
        for (ModelNode op : childOperations) {
            postExtensionOps.add(new ParsedBootOp(op, null));
        }
        assertEquals(ADD, postExtensionOps.get(0).operationName);
        assertEquals(pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH), postExtensionOps.get(0).address);
        assertEquals(ADD, postExtensionOps.get(1).operationName);
        assertEquals(pathAddress(SUBSYSTEM_PROPERTIES_PATH), postExtensionOps.get(1).address);
        assertEquals(ADD, postExtensionOps.get(2).operationName);
        assertEquals(pathAddress(SUBSYSTEM_CLASSPATH_PATH), postExtensionOps.get(2).address);
        assertEquals(ADD, postExtensionOps.get(3).operationName);
        assertEquals(pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH, pathElement("system-property", "aaa")), postExtensionOps.get(3).address);
        assertFalse(postExtensionOps.get(3).operation.hasDefined("value"));
        assertEquals(ADD, postExtensionOps.get(4).operationName);
        assertEquals(pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH, pathElement("system-property", "bbb")), postExtensionOps.get(4).address);
        assertFalse(postExtensionOps.get(4).operation.hasDefined("value"));
        assertEquals(ADD, postExtensionOps.get(5).operationName);
        assertEquals(pathAddress(SUBSYSTEM_PROPERTIES_PATH, pathElement("property", "test-property")), postExtensionOps.get(5).address);
        assertFalse(postExtensionOps.get(5).operation.hasDefined("value"));
        assertEquals(ADD, postExtensionOps.get(6).operationName);
        assertEquals(pathAddress(SUBSYSTEM_CLASSPATH_PATH, pathElement("classpath", "runtime")), postExtensionOps.get(6).address);
        assertFalse(postExtensionOps.get(6).operation.hasDefined("value"));
        assertEquals(WRITE_ATTRIBUTE_OPERATION, postExtensionOps.get(7).operationName);
        assertTrue(postExtensionOps.get(7).operation.hasDefined("value"));
        assertEquals("foo", postExtensionOps.get(7).operation.get("value").asString());
        assertEquals(pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH, pathElement("system-property", "aaa")), postExtensionOps.get(7).address);
        assertEquals(WRITE_ATTRIBUTE_OPERATION, postExtensionOps.get(8).operationName);
        assertTrue(postExtensionOps.get(8).operation.hasDefined("value"));
        assertEquals("bar", postExtensionOps.get(8).operation.get("value").asString());
        assertEquals(pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH, pathElement("system-property", "bbb")), postExtensionOps.get(8).address);
        assertEquals(WRITE_ATTRIBUTE_OPERATION, postExtensionOps.get(8).operationName);
        assertTrue(postExtensionOps.get(8).operation.hasDefined("value"));
        assertEquals("bar", postExtensionOps.get(8).operation.get("value").asString());
        assertEquals(ADD, postExtensionOps.get(9).operationName);
        assertEquals(pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH, pathElement("system-property", "ccc")), postExtensionOps.get(9).address);
        assertTrue(postExtensionOps.get(9).operation.hasDefined("value"));
        assertEquals("test", postExtensionOps.get(9).operation.get("value").asString());
        assertEquals(ADD, postExtensionOps.get(10).operationName);
        assertEquals(pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH, pathElement("system-property", "value")), postExtensionOps.get(10).address);
        assertTrue(postExtensionOps.get(10).operation.hasDefined("value"));
        assertEquals("test", postExtensionOps.get(10).operation.get("value").asString());
        assertEquals(ADD, postExtensionOps.get(11).operationName);
        assertEquals(pathAddress(SUBSYSTEM_BASIC_PATH, pathElement("basic", "test")), postExtensionOps.get(11).address);
        assertFalse(postExtensionOps.get(11).operation.hasDefined("value"));

        assertEquals(WRITE_ATTRIBUTE_OPERATION, postExtensionOps.get(12).operationName);
        assertEquals(pathAddress(SUBSYSTEM_PROPERTIES_PATH, pathElement("property", "test-property")), postExtensionOps.get(12).address);
        assertTrue(postExtensionOps.get(12).operation.hasDefined("name"));
        assertEquals("props", postExtensionOps.get(12).operation.get("name").asString());
        assertTrue(postExtensionOps.get(12).operation.hasDefined("value"));
        ModelNode properties = postExtensionOps.get(12).operation.get("value").asObject();
        assertEquals("0", properties.get("ip_ttl").asString());
        assertEquals("5", properties.get("tcp_ttl").asString());

        assertEquals(WRITE_ATTRIBUTE_OPERATION, postExtensionOps.get(13).operationName);
        assertEquals(pathAddress(SUBSYSTEM_CLASSPATH_PATH, pathElement("classpath", "runtime")), postExtensionOps.get(13).address);
        assertTrue(postExtensionOps.get(13).operation.hasDefined("value"));
        assertEquals("complex-map", postExtensionOps.get(13).operation.get("name").asString());
        assertTrue(postExtensionOps.get(13).operation.hasDefined("value"));
        ModelNode objectMap = postExtensionOps.get(13).operation.get("value").asObject();
        assertEquals("org.widlfly.test.Main", objectMap.get("main-class").get("class-name").asString());
        assertEquals("org.wildfly.test:main", objectMap.get("main-class").get("module").asString());
        assertEquals("org.widlfly.test.MyTest", objectMap.get("test-class").get("class-name").asString());
        assertEquals("org.wildfly.test:main", objectMap.get("test-class").get("module").asString());
    }

    /**
     * Verify removing a resource and adding it again.
     *
     * @throws URISyntaxException
     */
    @Test
    public void testAddRemoveNewResources() throws URISyntaxException {
        List<ParsedBootOp> postExtensionOps = new ArrayList<>();
        postExtensionOps.add(createParallelBootOperation(
                new ParsedBootOp(Operations.createAddOperation(pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH).toModelNode()), null)
        ));
        ConfigurationExtension instance = new YamlConfigurationExtension();
        instance.load(getYamlPath("simple_add_delete.yml"));
        instance.processOperations(rootRegistration, postExtensionOps);
        assertFalse(postExtensionOps.isEmpty());
        assertEquals(1, postExtensionOps.size());
        List<ModelNode> childOperations = postExtensionOps.get(0).getChildOperations();
        assertEquals("ChildOperations: " + childOperations, 3, childOperations.size());
        postExtensionOps = new ArrayList<>();
        for (ModelNode op : childOperations) {
            postExtensionOps.add(new ParsedBootOp(op, null));
        }
        assertEquals(3, postExtensionOps.size());
        assertEquals(ADD, postExtensionOps.get(0).operationName);
        assertEquals(pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH), postExtensionOps.get(0).address);
        assertEquals(ADD, postExtensionOps.get(1).operationName);
        assertEquals(pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH, pathElement("system-property", "aaa")), postExtensionOps.get(1).address);
        assertTrue(postExtensionOps.get(1).operation.hasDefined("value"));
        assertEquals("foo", postExtensionOps.get(1).operation.get("value").asString());
        assertEquals(ADD, postExtensionOps.get(2).operationName);
        assertEquals(pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH, pathElement("system-property", "ccc")), postExtensionOps.get(2).address);
        assertTrue(postExtensionOps.get(2).operation.hasDefined("value"));
        assertEquals("test", postExtensionOps.get(2).operation.get("value").asString());
    }

    /**
     * Verify removing a resource and adding it again.
     *
     * @throws URISyntaxException
     */
    @Test
    public void testRemoveAddNewResources() throws URISyntaxException {
        ModelNode addOperation = Operations.createAddOperation(pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH, pathElement("system-property", "bbb")).toModelNode());
        addOperation.get("value").set("foo");
        List<ParsedBootOp> postExtensionOps = new ArrayList<>();
        postExtensionOps.add(createParallelBootOperation(
                new ParsedBootOp(Operations.createAddOperation(pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH).toModelNode()), null),
                new ParsedBootOp(addOperation, null)
        ));
        ConfigurationExtension instance = new YamlConfigurationExtension();
        instance.load(getYamlPath("simple_delete_add_1.yml"), getYamlPath("simple_delete_add_2.yml"));
        instance.processOperations(rootRegistration, postExtensionOps);
        assertFalse(postExtensionOps.isEmpty());
        assertEquals(1, postExtensionOps.size());
        List<ModelNode> childOperations = postExtensionOps.get(0).getChildOperations();
        assertEquals("ChildOperations: " + childOperations, 5, childOperations.size());
        postExtensionOps = new ArrayList<>();
        for (ModelNode op : childOperations) {
            postExtensionOps.add(new ParsedBootOp(op, null));
        }
        assertEquals(5, postExtensionOps.size());
        assertEquals(ADD, postExtensionOps.get(0).operationName);
        assertEquals(pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH), postExtensionOps.get(0).address);
        assertEquals(ADD, postExtensionOps.get(1).operationName);
        assertEquals(pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH, pathElement("system-property", "aaa")), postExtensionOps.get(1).address);
        assertTrue(postExtensionOps.get(1).operation.hasDefined("value"));
        assertEquals("foo", postExtensionOps.get(1).operation.get("value").asString());
        assertEquals(ADD, postExtensionOps.get(2).operationName);
        assertEquals(pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH, pathElement("system-property", "ccc")), postExtensionOps.get(2).address);
        assertTrue(postExtensionOps.get(2).operation.hasDefined("value"));
        assertEquals("test", postExtensionOps.get(2).operation.get("value").asString());
        assertEquals(WRITE_ATTRIBUTE_OPERATION, postExtensionOps.get(3).operationName);
        assertEquals(pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH, pathElement("system-property", "ccc")), postExtensionOps.get(3).address);
        assertTrue(postExtensionOps.get(3).operation.hasDefined("value"));
        assertEquals("test", postExtensionOps.get(3).operation.get("value").asString());
        assertEquals(ADD, postExtensionOps.get(4).operationName);
        assertEquals(pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH, pathElement("system-property", "bbb")), postExtensionOps.get(4).address);
        assertTrue(postExtensionOps.get(4).operation.hasDefined("value"));
        assertEquals("bar", postExtensionOps.get(4).operation.get("value").asString());
    }

    /**
     * Verify that resource creation will be updated with the YAML definition.
     *
     * @throws URISyntaxException
     */
    @Test
    public void testAddResourceOverride() throws URISyntaxException {
        List<ParsedBootOp> postExtensionOps = new ArrayList<>();
        postExtensionOps.add(createParallelBootOperation(
                new ParsedBootOp(Operations.createAddOperation(pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH).toModelNode()), null)
        ));
        ConfigurationExtension instance = new YamlConfigurationExtension();
        instance.load(getYamlPath("simple_overwrite.yml"), getYamlPath("simple_override.yml"));
        instance.processOperations(rootRegistration, postExtensionOps);
        assertFalse(postExtensionOps.isEmpty());
        assertEquals(1, postExtensionOps.size());
        List<ModelNode> childOperations = postExtensionOps.get(0).getChildOperations();
        assertEquals("ChildOperations: " + childOperations, 4, childOperations.size());
        postExtensionOps = new ArrayList<>();
        for (ModelNode op : childOperations) {
            postExtensionOps.add(new ParsedBootOp(op, null));
        }
        assertEquals(4, postExtensionOps.size());
        assertEquals(ADD, postExtensionOps.get(0).operationName);
        assertEquals(pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH), postExtensionOps.get(0).address);
        assertEquals(ADD, postExtensionOps.get(1).operationName);
        assertEquals(PathAddress.pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH, pathElement("system-property", "aaa")), postExtensionOps.get(1).address);
        assertTrue(postExtensionOps.get(1).operation.hasDefined("value"));
        assertEquals("foo", postExtensionOps.get(1).operation.get("value").asString());
        assertEquals(ADD, postExtensionOps.get(2).operationName);
        assertEquals(PathAddress.pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH, pathElement("system-property", "bbb")), postExtensionOps.get(2).address);
        assertTrue(postExtensionOps.get(2).operation.hasDefined("value"));
        assertEquals("test", postExtensionOps.get(2).operation.get("value").asString());
        assertEquals(WRITE_ATTRIBUTE_OPERATION, postExtensionOps.get(3).operationName);
        assertEquals(PathAddress.pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH, pathElement("system-property", "bbb")), postExtensionOps.get(3).address);
        assertTrue(postExtensionOps.get(3).operation.hasDefined("value"));
        assertEquals("test-override", postExtensionOps.get(3).operation.get("value").asString());
    }

    /**
     * Verify that resource creation will be updated with the YAML definition.
     *
     * @throws URISyntaxException
     */
    @Test
    public void testAddResourceOverwrite() throws URISyntaxException {
        List<ParsedBootOp> postExtensionOps = new ArrayList<>();
        postExtensionOps.add(createParallelBootOperation(
                new ParsedBootOp(Operations.createAddOperation(pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH).toModelNode()), null)
        ));
        ConfigurationExtension instance = new YamlConfigurationExtension();
        instance.load(getYamlPath("simple_overwrite.yml"));
        instance.processOperations(rootRegistration, postExtensionOps);
        assertFalse(postExtensionOps.isEmpty());
        assertEquals(1, postExtensionOps.size());
        List<ModelNode> childOperations = postExtensionOps.get(0).getChildOperations();
        assertEquals("ChildOperations: " + childOperations, 3, childOperations.size());
        postExtensionOps = new ArrayList<>();
        for (ModelNode op : childOperations) {
            postExtensionOps.add(new ParsedBootOp(op, null));
        }
        assertEquals(ADD, postExtensionOps.get(0).operationName);
        assertEquals(pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH), postExtensionOps.get(0).address);
        assertEquals(ADD, postExtensionOps.get(1).operationName);
        assertEquals(PathAddress.pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH, pathElement("system-property", "aaa")), postExtensionOps.get(1).address);
        assertTrue(postExtensionOps.get(1).operation.hasDefined("value"));
        assertEquals("foo", postExtensionOps.get(1).operation.get("value").asString());
        assertEquals(ADD, postExtensionOps.get(2).operationName);
        assertEquals(PathAddress.pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH, pathElement("system-property", "bbb")), postExtensionOps.get(2).address);
        assertTrue(postExtensionOps.get(2).operation.hasDefined("value"));
        assertEquals("test", postExtensionOps.get(2).operation.get("value").asString());
    }

    /**
     * Verify undefining attributes.
     *
     * @throws java.net.URISyntaxException
     */
    @Test
    public void testUndefineAttribute() throws URISyntaxException {
        List<ParsedBootOp> postExtensionOps = new ArrayList<>();
        postExtensionOps.add(createParallelBootOperation(
                new ParsedBootOp(Operations.createAddOperation(pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH).toModelNode()), null),
                new ParsedBootOp(Operations.createAddOperation(pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH, pathElement("system-property", "aaa")).toModelNode()), null),
                new ParsedBootOp(Operations.createAddOperation(pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH, pathElement("system-property", "bbb")).toModelNode()), null)
        ));
        ConfigurationExtension instance = new YamlConfigurationExtension();
        instance.load(getYamlPath("simple_undefine.yml"));
        instance.processOperations(rootRegistration, postExtensionOps);
        assertFalse(postExtensionOps.isEmpty());
        assertEquals(1, postExtensionOps.size());
        List<ModelNode> childOperations = postExtensionOps.get(0).getChildOperations();
        assertEquals("ChildOperations: " + childOperations, 5, childOperations.size());
        postExtensionOps = new ArrayList<>();
        for (ModelNode op : childOperations) {
            postExtensionOps.add(new ParsedBootOp(op, null));
        }
        assertEquals(5, postExtensionOps.size());
        assertEquals(ADD, postExtensionOps.get(0).operationName);
        assertEquals(pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH), postExtensionOps.get(0).address);
        assertEquals(ADD, postExtensionOps.get(1).operationName);
        assertEquals(pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH, pathElement("system-property", "aaa")), postExtensionOps.get(1).address);
        assertFalse(postExtensionOps.get(1).operation.hasDefined("value"));
        assertEquals(ADD, postExtensionOps.get(2).operationName);
        assertEquals(pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH, pathElement("system-property", "bbb")), postExtensionOps.get(2).address);
        assertFalse(postExtensionOps.get(2).operation.hasDefined("value"));
        assertEquals(pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH, pathElement("system-property", "aaa")), postExtensionOps.get(3).address);
        assertEquals(UNDEFINE_ATTRIBUTE_OPERATION, postExtensionOps.get(3).operationName);
        assertEquals("value", postExtensionOps.get(3).operation.get("name").asString());
        assertEquals(pathAddress(SUBSYSTEM_SYSTEM_PROPERTIES_PATH, pathElement("system-property", "bbb")), postExtensionOps.get(4).address);
        assertEquals(UNDEFINE_ATTRIBUTE_OPERATION, postExtensionOps.get(4).operationName);
        assertEquals("value", postExtensionOps.get(4).operation.get("name").asString());
    }

    /**
     * Verify resource creation with a list attribute.
     *
     * @throws java.net.URISyntaxException
     */
    @Test
    public void testListAttribute() throws URISyntaxException {
        List<ParsedBootOp> postExtensionOps = new ArrayList<>();
        postExtensionOps.add(createParallelBootOperation(
                new ParsedBootOp(Operations.createAddOperation(pathAddress(SUBSYSTEM_LIST_PATH).toModelNode()), null)
        ));
        ConfigurationExtension instance = new YamlConfigurationExtension();
        instance.load(getYamlPath("simple_list.yml"));
        instance.processOperations(rootRegistration, postExtensionOps);
        assertFalse(postExtensionOps.isEmpty());
        assertEquals(1, postExtensionOps.size());
        List<ModelNode> childOperations = postExtensionOps.get(0).getChildOperations();
        assertEquals("ChildOperations: " + childOperations, 2, childOperations.size());
        postExtensionOps = new ArrayList<>();
        for (ModelNode op : childOperations) {
            postExtensionOps.add(new ParsedBootOp(op, null));
        }
        assertEquals(2, postExtensionOps.size());
        assertEquals(ADD, postExtensionOps.get(0).operationName);
        assertEquals(pathAddress(SUBSYSTEM_LIST_PATH), postExtensionOps.get(0).address);
        assertEquals(ADD, postExtensionOps.get(1).operationName);
        assertEquals(pathAddress(SUBSYSTEM_LIST_PATH, pathElement("list", "my-list")), postExtensionOps.get(1).address);
        assertTrue(postExtensionOps.get(1).operation.hasDefined("strings"));
        assertEquals(ModelType.LIST, postExtensionOps.get(1).operation.get("strings").getType());
        List<ModelNode> values = postExtensionOps.get(1).operation.get("strings").asList();
        assertEquals(2, values.size());
        assertEquals("foo", values.get(0).asString());
        assertEquals("bar", values.get(1).asString());
    }

    private ParsedBootOp createParallelBootOperation(ParsedBootOp... ops) {
        ParallelBootOperationStepHandler handler = new ParallelBootOperationStepHandler(Executors.newSingleThreadExecutor(), rootRegistration, new ControlledProcessState(true), null, 0, null);
        ParsedBootOp parallelBootOperation = handler.getParsedBootOp();
        for (ParsedBootOp op : ops) {
            handler.addSubsystemOperation(op);
        }
        return parallelBootOperation;
    }

    private Path getYamlPath(String yaml) throws URISyntaxException {
        return Paths.get(this.getClass().getResource("yaml/" + yaml).toURI());
    }
}
