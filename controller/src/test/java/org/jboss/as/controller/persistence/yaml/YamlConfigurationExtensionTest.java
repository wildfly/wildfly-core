/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.persistence.yaml;

import static org.jboss.as.controller.SimpleAttributeDefinitionBuilder.create;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UNDEFINE_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;
import static org.jboss.dmr.ModelType.STRING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.jboss.as.controller.AbstractBoottimeAddStepHandler;
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelOnlyWriteAttributeHandler;
import org.jboss.as.controller.ObjectMapAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.ParsedBootOp;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PrimitiveListAttributeDefinition;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.PropertiesAttributeDefinition;
import org.jboss.as.controller.ResourceRegistration;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.persistence.ConfigurationExtension;
import org.jboss.as.controller.persistence.ConfigurationExtensionFactory;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author Emmanuel Hugonnet (c) 2021 Red Hat, Inc.
 */
public class YamlConfigurationExtensionTest {

    private static final String MY_RESOURCE = "my-resource";
    private static ImmutableManagementResourceRegistration rootRegistration;

    public YamlConfigurationExtensionTest() {
    }

    @BeforeClass
    public static void setUp() {
        StandardResourceDescriptionResolver descriptionResolver = new StandardResourceDescriptionResolver(MY_RESOURCE, YamlConfigurationExtensionTest.class.getName(), Thread.currentThread().getContextClassLoader());
        SimpleResourceDefinition rootResource = new SimpleResourceDefinition(new SimpleResourceDefinition.Parameters(ResourceRegistration.root(), descriptionResolver));
        AttributeDefinition valueAtt = SimpleAttributeDefinitionBuilder.create(VALUE, STRING, true)
                .setAllowExpression(true)
                .setValidator(new StringLengthValidator(0, true, true))
                .build();
        SimpleResourceDefinition systemPropertyResource = new SimpleResourceDefinition(new SimpleResourceDefinition.Parameters(PathElement.pathElement("system-property"), descriptionResolver)
                .setAddHandler(new AbstractBoottimeAddStepHandler(valueAtt) {
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
        SimpleResourceDefinition extensionResource = new SimpleResourceDefinition(new SimpleResourceDefinition.Parameters(PathElement.pathElement("extension"), descriptionResolver)
                .setAddHandler(new AbstractBoottimeAddStepHandler(SimpleAttributeDefinitionBuilder.create("module", STRING, false)
                        .setAllowExpression(true)
                        .setValidator(new StringLengthValidator(0, true, true))
                        .build()) {
                }));
        AttributeDefinition listAtt = PrimitiveListAttributeDefinition.Builder.of("strings", STRING).build();
        SimpleResourceDefinition listResource = new SimpleResourceDefinition(new SimpleResourceDefinition.Parameters(PathElement.pathElement("list"), descriptionResolver)
                .setAddHandler(new AbstractBoottimeAddStepHandler(listAtt) {
                })) {
            @Override
            public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
                super.registerAttributes(resourceRegistration);
                resourceRegistration.registerReadWriteAttribute(listAtt, null, ModelOnlyWriteAttributeHandler.INSTANCE);
            }
        };
        SimpleResourceDefinition basicResource = new SimpleResourceDefinition(new SimpleResourceDefinition.Parameters(PathElement.pathElement("basic"), descriptionResolver)
                .setAddHandler(new AbstractBoottimeAddStepHandler(valueAtt) {
                })) {
            @Override
            public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
                super.registerAttributes(resourceRegistration);
                resourceRegistration.registerReadWriteAttribute(valueAtt, null, ModelOnlyWriteAttributeHandler.INSTANCE);
            }
        };
        PropertiesAttributeDefinition propertiesAtt = new PropertiesAttributeDefinition.Builder("props", false).build();
        SimpleResourceDefinition propertyResource = new SimpleResourceDefinition(new SimpleResourceDefinition.Parameters(PathElement.pathElement("property"), descriptionResolver)
                .setAddHandler(new AbstractBoottimeAddStepHandler(propertiesAtt) {
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
                .setAddHandler(new AbstractBoottimeAddStepHandler(COMPLEX_MAP) {
                })) {
            @Override
            public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
                super.registerAttributes(resourceRegistration);
                resourceRegistration.registerReadWriteAttribute(COMPLEX_MAP, null, ModelOnlyWriteAttributeHandler.INSTANCE);
            }
        };
        SimpleResourceDefinition childResource = new SimpleResourceDefinition(new SimpleResourceDefinition.Parameters(PathElement.pathElement("child"), descriptionResolver)
                .setAddHandler(new AbstractBoottimeAddStepHandler(valueAtt) {
                })) {
            @Override
            public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
                super.registerAttributes(resourceRegistration);
                resourceRegistration.registerReadWriteAttribute(valueAtt, null, ModelOnlyWriteAttributeHandler.INSTANCE);
            }
        };
        SimpleResourceDefinition parentResource = new SimpleResourceDefinition(new SimpleResourceDefinition.Parameters(PathElement.pathElement("parent"), descriptionResolver)
                .setAddHandler(new AbstractBoottimeAddStepHandler(valueAtt) {
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
        ManagementResourceRegistration root = ManagementResourceRegistration.Factory.forProcessType(ProcessType.EMBEDDED_SERVER).createRegistration(rootResource);
        GlobalOperationHandlers.registerGlobalOperations(root, ProcessType.EMBEDDED_SERVER);
        root.registerSubModel(systemPropertyResource);
        root.registerSubModel(extensionResource);
        root.registerSubModel(listResource);
        root.registerSubModel(basicResource);
        root.registerSubModel(propertyResource);
        root.registerSubModel(classpathResource);
        root.registerSubModel(parentResource);

        AttributeDefinition connectorType = SimpleAttributeDefinitionBuilder.create("type", STRING, true)
                .setAllowExpression(true)
                .setValidator(new StringLengthValidator(0, true, true))
                .build();
        ManagementResourceRegistration connectorRegistration = root.registerSubModel(new SimpleResourceDefinition(new SimpleResourceDefinition.Parameters(
                PathElement.pathElement("connector"), NonResolvingResourceDescriptionResolver.INSTANCE)
                .setAddHandler(new AbstractBoottimeAddStepHandler(connectorType) {
                })
                .setRemoveHandler(new AbstractRemoveStepHandler() {
                }))
        );
        connectorRegistration.registerReadWriteAttribute(connectorType, null, ModelOnlyWriteAttributeHandler.INSTANCE);
        ManagementResourceRegistration acceptorRegistration = connectorRegistration.registerSubModel(
                new SimpleResourceDefinition(
                        new SimpleResourceDefinition.Parameters(PathElement.pathElement("acceptor"), NonResolvingResourceDescriptionResolver.INSTANCE)
                                .setAddHandler(new AbstractBoottimeAddStepHandler(connectorType) {
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
        ConfigurationExtension instance = ConfigurationExtensionFactory.createConfigurationExtension(Paths.get(this.getClass().getResource("simple.yml").toURI()));
        instance.processOperations(rootRegistration, postExtensionOps);
        assertFalse(postExtensionOps.isEmpty());
        assertEquals(7, postExtensionOps.size());
        assertEquals(ADD, postExtensionOps.get(0).operationName);
        assertEquals(PathAddress.pathAddress("property", "test-property"), postExtensionOps.get(0).address);
        assertTrue(postExtensionOps.get(0).operation.hasDefined("props"));
        ModelNode properties = postExtensionOps.get(0).operation.get("props").asObject();
        assertEquals("0", properties.get("ip_ttl").asString());
        assertEquals("5", properties.get("tcp_ttl").asString());
        assertEquals(ADD, postExtensionOps.get(1).operationName);
        assertEquals(PathAddress.pathAddress("basic", "test"), postExtensionOps.get(1).address);
        assertFalse(postExtensionOps.get(1).operation.hasDefined("value"));
        assertEquals(ADD, postExtensionOps.get(2).operationName);
        assertEquals(PathAddress.pathAddress("classpath", "runtime"), postExtensionOps.get(2).address);
        ModelNode objectMap = postExtensionOps.get(2).operation.get("complex-map").asObject();
        assertEquals("org.widlfly.test.Main", objectMap.get("main-class").asObject().get("class-name").asString());
        assertEquals("org.wildfly.test:main", objectMap.get("main-class").asObject().get("module").asString());
        assertEquals("org.widlfly.test.MyTest", objectMap.get("test-class").asObject().get("class-name").asString());
        assertEquals("org.wildfly.test:main", objectMap.get("test-class").asObject().get("module").asString());
        assertEquals(ADD, postExtensionOps.get(3).operationName);
        assertEquals(PathAddress.pathAddress("system-property", "aaa"), postExtensionOps.get(3).address);
        assertTrue(postExtensionOps.get(3).operation.hasDefined("value"));
        assertEquals("foo", postExtensionOps.get(3).operation.get("value").asString());
        assertEquals(ADD, postExtensionOps.get(4).operationName);
        assertEquals(PathAddress.pathAddress("system-property", "bbb"), postExtensionOps.get(4).address);
        assertTrue(postExtensionOps.get(4).operation.hasDefined("value"));
        assertEquals("bar", postExtensionOps.get(4).operation.get("value").asString());
        assertEquals(ADD, postExtensionOps.get(5).operationName);
        assertEquals(PathAddress.pathAddress("system-property", "ccc"), postExtensionOps.get(5).address);
        assertTrue(postExtensionOps.get(5).operation.hasDefined("value"));
        assertEquals("test", postExtensionOps.get(5).operation.get("value").asString());
        assertEquals(ADD, postExtensionOps.get(6).operationName);
        assertEquals(PathAddress.pathAddress("system-property", "value"), postExtensionOps.get(6).address);
        assertTrue(postExtensionOps.get(6).operation.hasDefined("value"));
        assertEquals("test", postExtensionOps.get(6).operation.get("value").asString());
    }

    /**
     * Verify that an unknow resource will create an exception.
     *
     * @throws URISyntaxException
     */
    @Test
    public void testUnknownResource() throws URISyntaxException {
        List<ParsedBootOp> postExtensionOps = new ArrayList<>();
        postExtensionOps.add(new ParsedBootOp(Operations.createAddOperation(PathAddress.EMPTY_ADDRESS.toModelNode()), null));
        ConfigurationExtension instance = new YamlConfigurationExtension();
        instance.load(Paths.get(this.getClass().getResource("simple_error.yml").toURI()));
        try {
            instance.processOperations(rootRegistration, postExtensionOps);
            fail("Unknown resource should make the yaml extension fail");
        } catch (java.lang.IllegalArgumentException ex) {
            assertEquals("WFLYCTL0502: No child resource called 'system-propety' could be found at address '/'.", ex.getMessage());
        }
    }

    /**
     * Verify that we can create a graph of resources.
     *
     * @throws URISyntaxException
     */
    @Test
    public void testUnknownChildResource() throws URISyntaxException {
        List<ParsedBootOp> postExtensionOps = new ArrayList<>();
        postExtensionOps.add(new ParsedBootOp(Operations.createAddOperation(PathAddress.EMPTY_ADDRESS.toModelNode()), null));
        ConfigurationExtension instance = new YamlConfigurationExtension();
        instance.load(Paths.get(this.getClass().getResource("simple_child.yml").toURI()));
        instance.processOperations(rootRegistration, postExtensionOps);
        assertFalse(postExtensionOps.isEmpty());
        assertEquals(5, postExtensionOps.size());
        assertEquals(ADD, postExtensionOps.get(0).operationName);
        assertEquals(PathAddress.EMPTY_ADDRESS, postExtensionOps.get(0).address);
        assertEquals(ADD, postExtensionOps.get(1).operationName);
        assertEquals(PathAddress.pathAddress("parent", "homer"), postExtensionOps.get(1).address);
        assertEquals(ADD, postExtensionOps.get(2).operationName);
        assertEquals(PathAddress.pathAddress("parent", "homer").append("child", "bart"), postExtensionOps.get(2).address);
        assertEquals(ADD, postExtensionOps.get(3).operationName);
        assertEquals(PathAddress.pathAddress("parent", "homer").append("child", "lisa"), postExtensionOps.get(3).address);
        assertEquals(ADD, postExtensionOps.get(4).operationName);
        assertEquals(PathAddress.pathAddress("parent", "homer").append("child", "maggie"), postExtensionOps.get(4).address);
        postExtensionOps = new ArrayList<>();
        postExtensionOps.add(new ParsedBootOp(Operations.createAddOperation(PathAddress.EMPTY_ADDRESS.toModelNode()), null));
        instance = new YamlConfigurationExtension();
        instance.load(Paths.get(this.getClass().getResource("simple_child_error.yml").toURI()));
        try {
        instance.processOperations(rootRegistration, postExtensionOps);fail("Unknown resource should make the yaml extension fail");
        } catch (java.lang.IllegalArgumentException ex) {
            assertEquals("WFLYCTL0502: No child resource called 'children' could be found at address '/parent=homer'.", ex.getMessage());
        }
    }

    /**
     * Verify that resource creation will be updated with the YAML definition.
     *
     * @throws URISyntaxException
     */
    @Test
    public void testAddResourceOverwrite() throws URISyntaxException {
        List<ParsedBootOp> postExtensionOps = new ArrayList<>();
        ConfigurationExtension instance = new YamlConfigurationExtension();
        instance.load(Paths.get(this.getClass().getResource("simple_overwrite.yml").toURI()));
        instance.processOperations(rootRegistration, postExtensionOps);
        assertFalse(postExtensionOps.isEmpty());
        assertEquals(2, postExtensionOps.size());
        assertEquals(ADD, postExtensionOps.get(0).operationName);
        assertEquals(PathAddress.pathAddress("system-property", "aaa"), postExtensionOps.get(0).address);
        assertTrue(postExtensionOps.get(0).operation.hasDefined("value"));
        assertEquals("foo", postExtensionOps.get(0).operation.get("value").asString());
        assertEquals(ADD, postExtensionOps.get(1).operationName);
        assertEquals(PathAddress.pathAddress("system-property", "bbb"), postExtensionOps.get(1).address);
        assertTrue(postExtensionOps.get(1).operation.hasDefined("value"));
        assertEquals("test", postExtensionOps.get(1).operation.get("value").asString());
    }

     /**
     * Verify that resource creation will be updated with the YAML definition.
     *
     * @throws URISyntaxException
     */
    @Test
    public void testAddResourceOverride() throws URISyntaxException {
        List<ParsedBootOp> postExtensionOps = new ArrayList<>();
        ConfigurationExtension instance = new YamlConfigurationExtension();
        instance.load(Paths.get(this.getClass().getResource("simple_overwrite.yml").toURI()), Paths.get(this.getClass().getResource("simple_override.yml").toURI()));
        instance.processOperations(rootRegistration, postExtensionOps);
        assertFalse(postExtensionOps.isEmpty());
        assertEquals(3, postExtensionOps.size());
        assertEquals(ADD, postExtensionOps.get(0).operationName);
        assertEquals(PathAddress.pathAddress("system-property", "aaa"), postExtensionOps.get(0).address);
        assertTrue(postExtensionOps.get(0).operation.hasDefined("value"));
        assertEquals("foo", postExtensionOps.get(0).operation.get("value").asString());
        assertEquals(ADD, postExtensionOps.get(1).operationName);
        assertEquals(PathAddress.pathAddress("system-property", "bbb"), postExtensionOps.get(1).address);
        assertTrue(postExtensionOps.get(1).operation.hasDefined("value"));
        assertEquals("test", postExtensionOps.get(1).operation.get("value").asString());
        assertEquals(WRITE_ATTRIBUTE_OPERATION, postExtensionOps.get(2).operationName);
        assertEquals(PathAddress.pathAddress("system-property", "bbb"), postExtensionOps.get(2).address);
        assertTrue(postExtensionOps.get(2).operation.hasDefined("value"));
        assertEquals("test-override", postExtensionOps.get(2).operation.get("value").asString());
    }

    /**
     * Verify removing a resource and adding it again.
     *
     * @throws URISyntaxException
     */
    @Test
    public void testRemoveAddNewResources() throws URISyntaxException {
        List<ParsedBootOp> postExtensionOps = new ArrayList<>();
        ConfigurationExtension instance = new YamlConfigurationExtension();
        instance.load(Paths.get(this.getClass().getResource("simple_delete_add.yml").toURI()));
        instance.processOperations(rootRegistration, postExtensionOps);
        assertFalse(postExtensionOps.isEmpty());
        assertEquals(2, postExtensionOps.size());
        assertEquals(ADD, postExtensionOps.get(0).operationName);
        assertEquals(PathAddress.pathAddress("system-property", "aaa"), postExtensionOps.get(0).address);
        assertTrue(postExtensionOps.get(0).operation.hasDefined("value"));
        assertEquals("foo", postExtensionOps.get(0).operation.get("value").asString());
        assertEquals(ADD, postExtensionOps.get(1).operationName);
        assertEquals(PathAddress.pathAddress("system-property", "ccc"), postExtensionOps.get(1).address);
        assertTrue(postExtensionOps.get(1).operation.hasDefined("value"));
        assertEquals("test", postExtensionOps.get(1).operation.get("value").asString());
    }

    /**
     * Verify setting attributes.
     *
     * @throws java.net.URISyntaxException
     */
    @Test
    public void testWriteAttribute() throws URISyntaxException {
        List<ParsedBootOp> postExtensionOps = new ArrayList<>();
        postExtensionOps.add(new ParsedBootOp(Operations.createAddOperation(PathAddress.pathAddress("system-property", "aaa").toModelNode()), null));
        postExtensionOps.add(new ParsedBootOp(Operations.createAddOperation(PathAddress.pathAddress("system-property", "bbb").toModelNode()), null));
        postExtensionOps.add(new ParsedBootOp(Operations.createAddOperation(PathAddress.pathAddress("property", "test-property").toModelNode()), null));
        postExtensionOps.add(new ParsedBootOp(Operations.createAddOperation(PathAddress.pathAddress("classpath", "runtime").toModelNode()), null));
        ConfigurationExtension instance = new YamlConfigurationExtension();
        instance.load(Paths.get(this.getClass().getResource("simple.yml").toURI()));
        instance.processOperations(rootRegistration, postExtensionOps);
        assertFalse(postExtensionOps.isEmpty());
        assertEquals(11, postExtensionOps.size());
        assertEquals(ADD, postExtensionOps.get(0).operationName);
        assertEquals(PathAddress.pathAddress("system-property", "aaa"), postExtensionOps.get(0).address);
        assertFalse(postExtensionOps.get(0).operation.hasDefined("value"));
        assertEquals(ADD, postExtensionOps.get(1).operationName);
        assertEquals(PathAddress.pathAddress("system-property", "bbb"), postExtensionOps.get(1).address);
        assertFalse(postExtensionOps.get(1).operation.hasDefined("value"));
        assertEquals(ADD, postExtensionOps.get(2).operationName);
        assertEquals(PathAddress.pathAddress("property", "test-property"), postExtensionOps.get(2).address);
        assertFalse(postExtensionOps.get(2).operation.hasDefined("value"));
        assertEquals(ADD, postExtensionOps.get(3).operationName);
        assertEquals(PathAddress.pathAddress("classpath", "runtime"), postExtensionOps.get(3).address);
        assertFalse(postExtensionOps.get(3).operation.hasDefined("value"));
        assertEquals(WRITE_ATTRIBUTE_OPERATION, postExtensionOps.get(4).operationName);
        assertEquals(PathAddress.pathAddress("property", "test-property"), postExtensionOps.get(4).address);
        assertTrue(postExtensionOps.get(4).operation.hasDefined("name"));
        assertEquals("props", postExtensionOps.get(4).operation.get("name").asString());
        assertTrue(postExtensionOps.get(4).operation.hasDefined("value"));
        ModelNode properties = postExtensionOps.get(4).operation.get("value").asObject();
        assertEquals("0", properties.get("ip_ttl").asString());
        assertEquals("5", properties.get("tcp_ttl").asString());
        assertEquals(ADD, postExtensionOps.get(5).operationName);
        assertEquals(PathAddress.pathAddress("basic", "test"), postExtensionOps.get(5).address);
        assertFalse(postExtensionOps.get(5).operation.hasDefined("value"));
        assertEquals(WRITE_ATTRIBUTE_OPERATION, postExtensionOps.get(6).operationName);
        assertEquals(PathAddress.pathAddress("classpath", "runtime"), postExtensionOps.get(6).address);
        assertTrue(postExtensionOps.get(6).operation.hasDefined("value"));
        assertEquals("complex-map", postExtensionOps.get(6).operation.get("name").asString());
        assertTrue(postExtensionOps.get(6).operation.hasDefined("value"));
        ModelNode objectMap = postExtensionOps.get(6).operation.get("value").asObject();
        assertEquals("org.widlfly.test.Main", objectMap.get("main-class").get("class-name").asString());
        assertEquals("org.wildfly.test:main", objectMap.get("main-class").get("module").asString());
        assertEquals("org.widlfly.test.MyTest", objectMap.get("test-class").get("class-name").asString());
        assertEquals("org.wildfly.test:main", objectMap.get("test-class").get("module").asString());
        assertEquals(WRITE_ATTRIBUTE_OPERATION, postExtensionOps.get(7).operationName);
        assertTrue(postExtensionOps.get(7).operation.hasDefined("value"));
        assertEquals("foo", postExtensionOps.get(7).operation.get("value").asString());
        assertEquals(PathAddress.pathAddress("system-property", "aaa"), postExtensionOps.get(7).address);
        assertEquals(WRITE_ATTRIBUTE_OPERATION, postExtensionOps.get(8).operationName);
        assertTrue(postExtensionOps.get(8).operation.hasDefined("value"));
        assertEquals("bar", postExtensionOps.get(8).operation.get("value").asString());
        assertEquals(PathAddress.pathAddress("system-property", "bbb"), postExtensionOps.get(8).address);
        assertEquals(WRITE_ATTRIBUTE_OPERATION, postExtensionOps.get(8).operationName);
        assertTrue(postExtensionOps.get(8).operation.hasDefined("value"));
        assertEquals("bar", postExtensionOps.get(8).operation.get("value").asString());
        assertEquals(ADD, postExtensionOps.get(9).operationName);
        assertEquals(PathAddress.pathAddress("system-property", "ccc"), postExtensionOps.get(9).address);
        assertTrue(postExtensionOps.get(9).operation.hasDefined("value"));
        assertEquals("test", postExtensionOps.get(9).operation.get("value").asString());
        assertEquals(ADD, postExtensionOps.get(10).operationName);
        assertEquals(PathAddress.pathAddress("system-property", "value"), postExtensionOps.get(10).address);
        assertTrue(postExtensionOps.get(10).operation.hasDefined("value"));
        assertEquals("test", postExtensionOps.get(10).operation.get("value").asString());
    }

    /**
     * Verify undefining attributes.
     *
     * @throws java.net.URISyntaxException
     */
    @Test
    public void testUndefineAttribute() throws URISyntaxException {
        List<ParsedBootOp> postExtensionOps = new ArrayList<>();
        postExtensionOps.add(new ParsedBootOp(Operations.createAddOperation(PathAddress.pathAddress("system-property", "aaa").toModelNode()), null));
        postExtensionOps.add(new ParsedBootOp(Operations.createAddOperation(PathAddress.pathAddress("system-property", "bbb").toModelNode()), null));
        ConfigurationExtension instance = new YamlConfigurationExtension();
        instance.load(Paths.get(this.getClass().getResource("simple_undefine.yml").toURI()));
        instance.processOperations(rootRegistration, postExtensionOps);
        assertFalse(postExtensionOps.isEmpty());
        assertEquals(4, postExtensionOps.size());
        assertEquals(ADD, postExtensionOps.get(0).operationName);
        assertEquals(PathAddress.pathAddress("system-property", "aaa"), postExtensionOps.get(0).address);
        assertFalse(postExtensionOps.get(0).operation.hasDefined("value"));
        assertEquals(ADD, postExtensionOps.get(1).operationName);
        assertEquals(PathAddress.pathAddress("system-property", "bbb"), postExtensionOps.get(1).address);
        assertFalse(postExtensionOps.get(1).operation.hasDefined("value"));
        assertEquals(PathAddress.pathAddress("system-property", "aaa"), postExtensionOps.get(2).address);
        assertEquals(UNDEFINE_ATTRIBUTE_OPERATION, postExtensionOps.get(2).operationName);
        assertEquals("value", postExtensionOps.get(2).operation.get("name").asString());
        assertEquals(PathAddress.pathAddress("system-property", "bbb"), postExtensionOps.get(3).address);
        assertEquals(UNDEFINE_ATTRIBUTE_OPERATION, postExtensionOps.get(3).operationName);
        assertEquals("value", postExtensionOps.get(3).operation.get("name").asString());
    }

    /**
     * Verify resource creation with a list attribute.
     *
     * @throws java.net.URISyntaxException
     */
    @Test
    public void testListAttribute() throws URISyntaxException {
        List<ParsedBootOp> postExtensionOps = new ArrayList<>();
        ConfigurationExtension instance = new YamlConfigurationExtension();
        instance.load(Paths.get(this.getClass().getResource("simple_list.yml").toURI()));
        instance.processOperations(rootRegistration, postExtensionOps);
        assertFalse(postExtensionOps.isEmpty());
        assertEquals(1, postExtensionOps.size());
        assertEquals(ADD, postExtensionOps.get(0).operationName);
        assertEquals(PathAddress.pathAddress("list", "my-list"), postExtensionOps.get(0).address);
        assertTrue(postExtensionOps.get(0).operation.hasDefined("strings"));
        assertEquals(ModelType.LIST, postExtensionOps.get(0).operation.get("strings").getType());
        List<ModelNode> values = postExtensionOps.get(0).operation.get("strings").asList();
        assertEquals(2, values.size());
        assertEquals("foo", values.get(0).asString());
        assertEquals("bar", values.get(1).asString());
    }

    /**
     * Verify resource creation updated adding list attribute elements.
     *
     * @throws java.net.URISyntaxException
     */
    @Test
    public void testListMergeAttribute() throws URISyntaxException {
        List<ParsedBootOp> postExtensionOps = new ArrayList<>();
        ConfigurationExtension instance = new YamlConfigurationExtension();
        instance.load(Paths.get(this.getClass().getResource("simple_list_merge.yml").toURI()));
        instance.processOperations(rootRegistration, postExtensionOps);
        assertFalse(postExtensionOps.isEmpty());
        assertEquals(1, postExtensionOps.size());
        assertEquals(ADD, postExtensionOps.get(0).operationName);
        assertEquals(PathAddress.pathAddress("list", "my-list"), postExtensionOps.get(0).address);
        assertTrue(postExtensionOps.get(0).operation.hasDefined("strings"));
        assertEquals(ModelType.LIST, postExtensionOps.get(0).operation.get("strings").getType());
        List<ModelNode> values = postExtensionOps.get(0).operation.get("strings").asList();
        assertEquals(3, values.size());
        assertEquals("foo", values.get(0).asString());
        assertEquals("bar", values.get(1).asString());
        assertEquals("test", values.get(2).asString());
    }

    /**
     * Verify updating a list attribute by adding elements.
     *
     * @throws java.net.URISyntaxException
     */
    @Test
    public void testListAddAttribute() throws URISyntaxException {
        List<ParsedBootOp> postExtensionOps = new ArrayList<>();
        ModelNode listCreation = Operations.createAddOperation(PathAddress.pathAddress("list", "my-list").toModelNode());
        listCreation.get("strings").add("first");
        listCreation.get("strings").add("second");
        postExtensionOps.add(new ParsedBootOp(listCreation, null));
        ConfigurationExtension instance = new YamlConfigurationExtension();
        instance.load(Paths.get(this.getClass().getResource("simple_list_add.yml").toURI()));
        instance.processOperations(rootRegistration, postExtensionOps);
        assertFalse(postExtensionOps.isEmpty());
        assertEquals(3, postExtensionOps.size());
        assertEquals(ADD, postExtensionOps.get(0).operationName);
        assertEquals(PathAddress.pathAddress("list", "my-list"), postExtensionOps.get(0).address);
        assertEquals(ModelType.LIST, postExtensionOps.get(0).operation.get("strings").getType());
        assertTrue(postExtensionOps.get(0).operation.hasDefined("strings"));
        List<ModelNode> values = postExtensionOps.get(0).operation.get("strings").asList();
        assertEquals(2, values.size());
        assertEquals("first", values.get(0).asString());
        assertEquals("second", values.get(1).asString());
        assertEquals("list-add", postExtensionOps.get(1).operationName);
        assertEquals(PathAddress.pathAddress("list", "my-list"), postExtensionOps.get(1).address);
        assertTrue(postExtensionOps.get(1).operation.hasDefined("name"));
        assertEquals("strings", postExtensionOps.get(1).operation.get("name").asString());
        assertTrue(postExtensionOps.get(1).operation.hasDefined("value"));
        assertEquals("foo", postExtensionOps.get(1).operation.get("value").asString());
        assertTrue(postExtensionOps.get(1).operation.hasDefined("index"));
        assertEquals(0, postExtensionOps.get(1).operation.get("index").asInt());
        assertEquals("list-add", postExtensionOps.get(2).operationName);
        assertEquals(PathAddress.pathAddress("list", "my-list"), postExtensionOps.get(2).address);
        assertTrue(postExtensionOps.get(2).operation.hasDefined("name"));
        assertEquals("strings", postExtensionOps.get(2).operation.get("name").asString());
        assertTrue(postExtensionOps.get(2).operation.hasDefined("value"));
        assertEquals("bar", postExtensionOps.get(2).operation.get("value").asString());
    }

    /**
     * Verify that removing a non-existing resource doesn't fail.
     *
     * @throws java.net.URISyntaxException
     */
    @Test
    public void testRemoveUnexisitingResource() throws URISyntaxException {
        List<ParsedBootOp> postExtensionOps = new ArrayList<>();
        ConfigurationExtension instance = new YamlConfigurationExtension();
        instance.load(Paths.get(this.getClass().getResource("simple_remove.yml").toURI()));
        instance.processOperations(rootRegistration, postExtensionOps);
        assertFalse(postExtensionOps.isEmpty());
        assertEquals(3, postExtensionOps.size());
        assertEquals(ADD, postExtensionOps.get(0).operationName);
        assertEquals(PathAddress.pathAddress("connector", "my-connector"), postExtensionOps.get(0).address);
        assertTrue(postExtensionOps.get(0).operation.hasDefined("type"));
        assertEquals("test", postExtensionOps.get(0).operation.get("type").asString());
        assertEquals(ADD, postExtensionOps.get(1).operationName);
        assertEquals(PathAddress.pathAddress("connector", "my-connector").append("acceptor", "my-acceptor"), postExtensionOps.get(1).address);
        assertTrue(postExtensionOps.get(1).operation.hasDefined("type"));
        assertEquals("acceptor", postExtensionOps.get(1).operation.get("type").asString());
        assertEquals(ADD, postExtensionOps.get(2).operationName);
        assertEquals(PathAddress.pathAddress("connector", "old-connector"), postExtensionOps.get(2).address);
        assertTrue(postExtensionOps.get(2).operation.hasDefined("type"));
        assertEquals("old-test", postExtensionOps.get(2).operation.get("type").asString());
    }

    /**
     * Verify removing a resource.
     *
     * @throws java.net.URISyntaxException
     */
    @Test
    public void testRemoveResource() throws URISyntaxException {
        List<ParsedBootOp> postExtensionOps = new ArrayList<>();
        ModelNode oldConnector = Operations.createAddOperation(PathAddress.pathAddress("connector", "old-connector").toModelNode());
        oldConnector.get("type").set("prepare-test");
        postExtensionOps.add(new ParsedBootOp(oldConnector, null));
        ModelNode oldAcceptor = Operations.createAddOperation(PathAddress.pathAddress("connector", "old-connector").append("acceptor", "old-acceptor").toModelNode());
        oldAcceptor.get("type").set("acceptor");
        postExtensionOps.add(new ParsedBootOp(oldAcceptor, null));
        ConfigurationExtension instance = new YamlConfigurationExtension();
        instance.load(Paths.get(this.getClass().getResource("simple_remove.yml").toURI()));
        instance.processOperations(rootRegistration, postExtensionOps);
        assertFalse(postExtensionOps.isEmpty());
        assertEquals(4, postExtensionOps.size());
        //Preparation OPS
        assertEquals(ADD, postExtensionOps.get(0).operationName);
        assertEquals(PathAddress.pathAddress("connector", "old-connector"), postExtensionOps.get(0).address);
        assertTrue(postExtensionOps.get(0).operation.hasDefined("type"));
        assertEquals("prepare-test", postExtensionOps.get(0).operation.get("type").asString());
        //YAML OPS
        assertEquals(ADD, postExtensionOps.get(1).operationName);
        assertEquals(PathAddress.pathAddress("connector", "my-connector"), postExtensionOps.get(1).address);
        assertTrue(postExtensionOps.get(1).operation.hasDefined("type"));
        assertEquals("test", postExtensionOps.get(1).operation.get("type").asString());
        assertEquals(ADD, postExtensionOps.get(2).operationName);
        assertEquals(PathAddress.pathAddress("connector", "my-connector").append("acceptor", "my-acceptor"), postExtensionOps.get(2).address);
        assertTrue(postExtensionOps.get(2).operation.hasDefined("type"));
        assertEquals("acceptor", postExtensionOps.get(2).operation.get("type").asString());
        assertEquals(WRITE_ATTRIBUTE_OPERATION, postExtensionOps.get(3).operationName);
        assertEquals(PathAddress.pathAddress("connector", "old-connector"), postExtensionOps.get(3).address);
        assertTrue(postExtensionOps.get(3).operation.hasDefined("name"));
        assertEquals("type", postExtensionOps.get(3).operation.get("name").asString());
        assertTrue(postExtensionOps.get(3).operation.hasDefined("value"));
        assertEquals("old-test", postExtensionOps.get(3).operation.get("value").asString());
    }

    /**
     * Verify removing a resource.
     *
     * @throws java.net.URISyntaxException
     */
    @Test
    public void testRemoveParentResource() throws URISyntaxException {
        List<ParsedBootOp> postExtensionOps = new ArrayList<>();
        ModelNode oldConnector = Operations.createAddOperation(PathAddress.pathAddress("connector", "old-connector").toModelNode());
        oldConnector.get("type").set("prepare-test");
        postExtensionOps.add(new ParsedBootOp(oldConnector, null));
        ModelNode oldAcceptor = Operations.createAddOperation(PathAddress.pathAddress("connector", "old-connector").append("acceptor", "old-acceptor").toModelNode());
        oldAcceptor.get("type").set("acceptor");
        postExtensionOps.add(new ParsedBootOp(oldAcceptor, null));
        ConfigurationExtension instance = new YamlConfigurationExtension();
        instance.load(Paths.get(this.getClass().getResource("simple_parent_remove.yml").toURI()));
        instance.processOperations(rootRegistration, postExtensionOps);
        assertFalse(postExtensionOps.isEmpty());
        assertEquals(2, postExtensionOps.size());
        //Preparation OPS
        //YAML OPS
        assertEquals(ADD, postExtensionOps.get(0).operationName);
        assertEquals(PathAddress.pathAddress("connector", "my-connector"), postExtensionOps.get(0).address);
        assertTrue(postExtensionOps.get(0).operation.hasDefined("type"));
        assertEquals("test", postExtensionOps.get(0).operation.get("type").asString());
        assertEquals(ADD, postExtensionOps.get(1).operationName);
        assertEquals(PathAddress.pathAddress("connector", "my-connector").append("acceptor", "my-acceptor"), postExtensionOps.get(1).address);
        assertTrue(postExtensionOps.get(1).operation.hasDefined("type"));
        assertEquals("acceptor", postExtensionOps.get(1).operation.get("type").asString());
    }
}
