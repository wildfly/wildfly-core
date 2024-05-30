/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.test;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADDR_PARAMS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ANNOTATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILDREN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPLEX_ATTRIBUTE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEFAULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FEATURE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FEATURE_ID;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FEATURE_REFERENCE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPTIONAL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_PARAMS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_PARAMS_MAPPING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PACKAGE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PACKAGES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PARAMS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROVIDES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_FEATURE_DESCRIPTION_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REFS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REQUIRES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.ModelControllerClientFactory;
import org.jboss.as.controller.ModelOnlyWriteAttributeHandler;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PASSIVE;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.operations.global.ReadFeatureDescriptionHandler;
import org.jboss.as.controller.registry.RuntimePackageDependency;
import org.jboss.as.controller.registry.AliasEntry;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.Assert;
import org.junit.Test;


public class ReadFeatureDescriptionTestCase extends AbstractControllerTestBase {

    private static final String TEST_EXTENSION = "org.wildfly.testextension";
    private static final String TEST_SUBSYSTEM = "testsubsystem";
    private static final String RESOURCE = "resource";
    private static final String MAIN_RESOURCE = "main-resource";
    private static final String SPECIAL_NAMES_RESOURCE = "special-names-resource";
    private static final String REFERENCING_RESOURCE = "referencing-resource";
    private static final String RUNTIME_RESOURCE = "runtime-resource";
    private static final String NON_FEATURE_RESOURCE = "non-feature-resource";
    private static final String TEST = "test";
    private static final String MAIN_RESOURCE_CAPABILITY_NAME = "main-resource-capability";
    private static final String MAIN_RESOURCE_PACKAGE_NAME = "main-resource-package";
    private static final String MAIN_RESOURCE_OPT_PACKAGE_NAME = "optional-main-resource-package";
    private static final String MAIN_RESOURCE_PASSIVE_PACKAGE_NAME = "passive-main-resource-package";
    private static final String MAIN_RESOURCE_REQUIRED_PACKAGE_NAME = "required-main-resource-package";
    private static final String ROOT_CAPABILITY_NAME = "root-capability";
    private static final String DYNAMIC_CAPABILITY_NAME = "dynamic-capability";
    private static final String NOTIFICATION_REGISTRY_CAPABILITY_NAME = "org.wildfly.management.notification-handler-registry";

    private static final OperationStepHandler WRITE_HANDLER = new ModelOnlyWriteAttributeHandler();

    private ManagementResourceRegistration registration;

    @Override
    public void setupController() throws InterruptedException {
        super.setupController();
        // register read-feature op
        // can't do this in #initModel() because `capabilityRegistry` is not available at that stage
        registration.registerOperationHandler(ReadFeatureDescriptionHandler.DEFINITION,
                ReadFeatureDescriptionHandler.getInstance(capabilityRegistry), true);
    }

    /**
     * Reads subsystem that has sub-resources, but no attributes. Subsystem provides a capability.
     *
     * Expectations:
     *
     * - feature-id param,
     * - param for extension which contains this subsystem,
     * - provided capability is listed in 'provides',
     * - 'refs' contain {feature = "extension", include = true},
     * - packages contain "[extension name]".
     */
    @Test
    public void testSubsystem() throws OperationFailedException {
        PathAddress address = PathAddress.pathAddress(SUBSYSTEM, TEST_SUBSYSTEM);
        ModelNode result = readFeatureDescription(address);

        ModelNode feature = result.require(FEATURE);
        Assert.assertEquals(serializeAddress(address), feature.require(NAME).asString());

        // check params
        Map<String, ModelNode> params = extractParamsToMap(feature);
        Assert.assertEquals(2, params.size());
        assertFeatureIdParam(SUBSYSTEM, TEST_SUBSYSTEM, params); // path parameter
        assertParamWithDefault(EXTENSION, TEST_EXTENSION, params); // referencing extension

        // check provides
        ModelNode provides = feature.require(PROVIDES);
        Assert.assertEquals(1, provides.asList().size());
        Assert.assertEquals("subsystem-capability", provides.get(0).asString());

        // check ref on extension
        ModelNode refs = feature.require(REFS);
        Assert.assertEquals(1, refs.asList().size());
        Assert.assertEquals("extension", refs.require(0).require(FEATURE).asString());
        Assert.assertTrue(refs.require(0).require(INCLUDE).asBoolean());

        // check packages
        ModelNode packages = feature.require(PACKAGES);
        Assert.assertEquals(1, packages.asList().size());
        Assert.assertEquals(TEST_EXTENSION, packages.get(0).require(PACKAGE).asString());
    }

    /**
     * Reads feature on storage resource that:
     *
     * - has add handler,
     * - contains read-write storage attributes,
     * - contains read-only runtime attribute,
     * - contains complex (list and object) attributes that are also marked as resource-only,
     * - provides a capability.
     *
     * Expectations:
     *
     * - annotation for 'add' operation with address parameters and operation parameters (not containing resource-only or read-only),
     * - feature-id params (subsystem, resource),
     * - params for all storage attributes except complex ones,
     * - complex attributes described as children,
     * - references parent subsystem,
     * - provided capability is listed in 'provides',
     * - capability referenced by optional-attr is listed in 'requires'.
     */
    @Test
    public void testStorageResource() throws OperationFailedException {
        PathAddress address = PathAddress.pathAddress(SUBSYSTEM, TEST_SUBSYSTEM).append(RESOURCE, MAIN_RESOURCE);
        ModelNode result = readFeatureDescription(address);

        ModelNode feature = result.require(FEATURE);
        Assert.assertEquals(serializeAddress(address), feature.require(NAME).asString());

        // check annotation
        ModelNode annotation = feature.require(ANNOTATION);
        Assert.assertEquals(ADD, annotation.require(NAME).asString());
        Assert.assertArrayEquals(new String[] {SUBSYSTEM, RESOURCE}, annotation.require(ADDR_PARAMS).asString().split(","));
        assertSortedArrayEquals(new String[] {"optional-attr", "mandatory-attr"}, annotation.require(OP_PARAMS).asString().split(","));

        // check params
        Map<String, ModelNode> params = extractParamsToMap(feature);
        assertFeatureIdParam(SUBSYSTEM, TEST_SUBSYSTEM, params);
        assertFeatureIdParam(RESOURCE, MAIN_RESOURCE, params);
        // optional-attr is nillable
        Assert.assertTrue(params.containsKey("optional-attr"));
        Assert.assertTrue(params.get("optional-attr").require("nillable").asBoolean());
        // mandatory-attr is not nillable
        Assert.assertTrue(params.containsKey("mandatory-attr"));
        Assert.assertFalse(params.get("mandatory-attr").has("nillable"));
        // read-only attr not present
        Assert.assertFalse(params.containsKey("read-only-attr"));

        // refs
        ModelNode refs = feature.require(REFS);
        Assert.assertEquals(1, refs.asList().size());
        Assert.assertEquals(SUBSYSTEM + "." + TEST_SUBSYSTEM, refs.get(0).require(FEATURE).asString()); // referenced to parent

        // provides
        ModelNode provides = feature.require(PROVIDES);
        Assert.assertEquals(1, provides.asList().size());
        Assert.assertEquals(MAIN_RESOURCE_CAPABILITY_NAME + "." + MAIN_RESOURCE, provides.get(0).asString());

        // complex attributes

        ModelNode children = feature.require(CHILDREN);

        ModelNode listAttr = children.require("subsystem.testsubsystem.resource.main-resource.list-attr");
        Assert.assertEquals("subsystem.testsubsystem.resource.main-resource.list-attr", listAttr.require(NAME).asString());
        annotation = listAttr.require(ANNOTATION);
        Assert.assertEquals("list-add", annotation.require(NAME).asString());
        Assert.assertEquals("list-attr", annotation.require(COMPLEX_ATTRIBUTE).asString());
        Assert.assertArrayEquals(new String[] {SUBSYSTEM, RESOURCE}, annotation.require(ADDR_PARAMS).asString().split(","));
        assertSortedArrayEquals(new String[] {"optional-attr"}, annotation.require(OP_PARAMS).asString().split(","));
        Assert.assertEquals("subsystem.testsubsystem.resource.main-resource", listAttr.require(REFS).asList().get(0).require(FEATURE).asString());
        params = extractParamsToMap(listAttr);
        Assert.assertTrue(params.containsKey(SUBSYSTEM));
        Assert.assertTrue(params.containsKey(RESOURCE));
        Assert.assertTrue(params.containsKey("optional-attr"));

        ModelNode objectAttr = children.require("subsystem.testsubsystem.resource.main-resource.object-attr");
        Assert.assertEquals("subsystem.testsubsystem.resource.main-resource.object-attr", objectAttr.require(NAME).asString());
        annotation = objectAttr.require(ANNOTATION);
        Assert.assertEquals("write-attribute", annotation.require(NAME).asString());
        Assert.assertEquals("object-attr", annotation.require(COMPLEX_ATTRIBUTE).asString());
        Assert.assertArrayEquals(new String[] {SUBSYSTEM, RESOURCE}, annotation.require(ADDR_PARAMS).asString().split(","));
        assertSortedArrayEquals(new String[] {"optional-attr"}, annotation.require(OP_PARAMS).asString().split(","));
        Assert.assertEquals("subsystem.testsubsystem.resource.main-resource", listAttr.require(REFS).asList().get(0).require(FEATURE).asString());
        params = extractParamsToMap(objectAttr);
        assertFeatureIdParam(SUBSYSTEM, TEST_SUBSYSTEM, params);
        assertFeatureIdParam(RESOURCE, MAIN_RESOURCE, params);
        Assert.assertTrue(params.containsKey("optional-attr"));

        ModelNode complexObjectAttr = children.require("subsystem.testsubsystem.resource.main-resource.complex-object-attr");
        Assert.assertEquals("subsystem.testsubsystem.resource.main-resource.complex-object-attr", complexObjectAttr.require(NAME).asString());
        annotation = complexObjectAttr.require(ANNOTATION);
        Assert.assertEquals("write-attribute", annotation.require(NAME).asString());
        Assert.assertEquals("complex-object-attr", annotation.require(COMPLEX_ATTRIBUTE).asString());
        Assert.assertArrayEquals(new String[] {SUBSYSTEM, RESOURCE}, annotation.require(ADDR_PARAMS).asString().split(","));
        assertSortedArrayEquals(new String[] {"optional-attr", "dynamic-attr"}, annotation.require(OP_PARAMS).asString().split(","));
        Assert.assertEquals("subsystem.testsubsystem.resource.main-resource", listAttr.require(REFS).asList().get(0).require(FEATURE).asString());
        // requires
        ModelNode attrRequires = complexObjectAttr.require(REQUIRES);
        Assert.assertEquals(2, attrRequires.asList().size());
        Map<String, ModelNode> caps = extractToMap(attrRequires, NAME);
        Assert.assertTrue(caps.containsKey(ROOT_CAPABILITY_NAME));
        Assert.assertTrue(caps.get(ROOT_CAPABILITY_NAME).require(OPTIONAL).asBoolean());
        Assert.assertTrue(caps.containsKey(DYNAMIC_CAPABILITY_NAME + ".$dynamic-attr"));
        params = extractParamsToMap(complexObjectAttr);
        assertFeatureIdParam(SUBSYSTEM, TEST_SUBSYSTEM, params);
        assertFeatureIdParam(RESOURCE, MAIN_RESOURCE, params);
        Assert.assertTrue(params.containsKey("optional-attr"));

        // requires
        ModelNode requires = feature.require(REQUIRES);
        Assert.assertEquals(1, requires.asList().size());
        // requires `root-capability` because `optional-attr` references it
        Assert.assertEquals(ROOT_CAPABILITY_NAME, requires.asList().get(0).require(NAME).asString());
        Assert.assertTrue(requires.asList().get(0).require(OPTIONAL).asBoolean());

        // packages
        ModelNode packages = feature.require(PACKAGES);
        Assert.assertEquals(3, packages.asList().size());
        int expected = packages.asList().size();
        for (ModelNode mn : packages.asList()) {
            String name = mn.get(PACKAGE).asString();
            switch (name) {
                case MAIN_RESOURCE_REQUIRED_PACKAGE_NAME: {
                    expected -= 1;
                    Assert.assertFalse(mn.hasDefined(OPTIONAL));
                    Assert.assertFalse(mn.hasDefined(PASSIVE));
                    break;
                }
                case MAIN_RESOURCE_OPT_PACKAGE_NAME: {
                    Assert.assertTrue(mn.hasDefined(OPTIONAL));
                    Assert.assertFalse(mn.hasDefined(PASSIVE));
                    expected -= 1;
                    break;
                }
                case MAIN_RESOURCE_PASSIVE_PACKAGE_NAME: {
                    Assert.assertTrue(mn.hasDefined(OPTIONAL));
                    Assert.assertTrue(mn.hasDefined(PASSIVE));
                    expected -= 1;
                    break;
                }
            }
        }
        Assert.assertEquals(0, expected);
    }

    /**
     * Resource that contains an attribute with feature reference on capability of `main-resource`.
     *
     * Expectations:
     *
     * - references main-resource,
     * - requires main-resource-capabality.
     */
    @Test
    public void testReferencingResource() throws OperationFailedException {
        PathAddress address = PathAddress.pathAddress(SUBSYSTEM, TEST_SUBSYSTEM).append(RESOURCE, REFERENCING_RESOURCE);
        ModelNode result = readFeatureDescription(address);

        ModelNode feature = result.require(FEATURE);
        Assert.assertEquals(serializeAddress(address), feature.require(NAME).asString());

        // params
        Map<String, ModelNode> params = extractParamsToMap(feature);
        Assert.assertTrue(params.containsKey(MAIN_RESOURCE)); // resource has a `main-resource` attribute

        // refs
        Map<String, ModelNode> refs = extractRefsToMap(feature);
        Assert.assertEquals(2, refs.size());
        Assert.assertTrue(refs.containsKey(SUBSYSTEM + "." + TEST_SUBSYSTEM)); // ref to parent
        // ref to resource=main-resource, because the attribute above references main-resource-capability
        Assert.assertTrue(refs.containsKey(SUBSYSTEM + "." + TEST_SUBSYSTEM + "." + RESOURCE + "." + MAIN_RESOURCE));

        // requires
        Map<String, ModelNode> requires = extractToMap(feature.require(REQUIRES), NAME);
        Assert.assertEquals(2, requires.size());
        Assert.assertTrue(requires.containsKey(MAIN_RESOURCE_CAPABILITY_NAME + ".$main-resource"));
        Assert.assertFalse(requires.get(MAIN_RESOURCE_CAPABILITY_NAME + ".$main-resource").require(OPTIONAL).asBoolean());
        Assert.assertTrue(requires.containsKey(ROOT_CAPABILITY_NAME));
    }

    /**
     * Reads runtime resource and storage resource marked as setFeature(false).
     *
     * Expectations: Operation should be registered but return `undefined`.
     */
    @Test
    public void testNonFeatureResources() throws OperationFailedException {
        assertReadFeatureUndefined(PathAddress.pathAddress(SUBSYSTEM, TEST_SUBSYSTEM).append(RESOURCE, RUNTIME_RESOURCE));
        assertReadFeatureUndefined(PathAddress.pathAddress(SUBSYSTEM, TEST_SUBSYSTEM).append(RESOURCE, NON_FEATURE_RESOURCE));
    }

    /**
     * Reads resource that:
     *
     * - contains attributes with special names ('host', 'profile'),
     * - contains an attribute that conflicts with resource path element (path '_test_/storage-resource', attribute 'test'),
     * - doesn't have add handler.
     *
     * Expectations:
     *
     * - mentioned attributes need to be remapped to '*-feature'.
     * - annotation will reference 'write-attribute' operation instead of 'add' operation.
     */
    @Test
    public void testSpecialAttributeNames() throws OperationFailedException {
        PathAddress address = PathAddress.pathAddress(SUBSYSTEM, TEST_SUBSYSTEM).append(TEST, SPECIAL_NAMES_RESOURCE);
        ModelNode result = readFeatureDescription(address);

        ModelNode feature = result.require(FEATURE);
        Assert.assertEquals(serializeAddress(address), feature.require(NAME).asString());
        Map<String, ModelNode> params = extractParamsToMap(feature);
        assertFeatureIdParam(SUBSYSTEM, TEST_SUBSYSTEM, params);
        assertFeatureIdParam(TEST, SPECIAL_NAMES_RESOURCE, params); // note that parameter "test" is bound to address element
        Assert.assertTrue(params.containsKey(TEST + "-feature")); // while resource attribute "test" is renamed to "test-feature"
        Assert.assertTrue(params.containsKey(HOST + "-feature")); // likewise "host" should be renamed to "host-feature"
        Assert.assertTrue(params.containsKey(PROFILE + "-feature")); // and the same for "profile"

        // annotation
        ModelNode annotation = feature.require(ANNOTATION);
        Assert.assertArrayEquals(new String[] {SUBSYSTEM, TEST}, annotation.require(ADDR_PARAMS).asString().split(","));
        Assert.assertEquals(WRITE_ATTRIBUTE_OPERATION, annotation.require(NAME).asString());
        // the renamed "*-feature" parameters should be mapped to their original names
        assertSortedArrayEquals(new String[] {HOST, TEST, PROFILE},
                annotation.require(OP_PARAMS_MAPPING).asString().split(","));
        assertSortedArrayEquals(new String[] {HOST + "-feature", TEST + "-feature", PROFILE + "-feature"},
                annotation.require(OP_PARAMS).asString().split(","));
    }

    /**
     * Reads root. Root provides a capability and contains an attribute.
     *
     * Expectations:
     *
     * - contains a param for the attribute and an id-param,
     * - contains annotation,
     * - lists the capability in "provides" section.
     */
    @Test
    public void testServerRoot() throws OperationFailedException {
        ModelNode result = readFeatureDescription(PathAddress.EMPTY_ADDRESS);

        ModelNode feature = result.require(FEATURE);
        Assert.assertEquals("server-root", feature.require(NAME).asString());
        Map<String, ModelNode> params = extractParamsToMap(feature);

        // params
        assertFeatureIdParam("server-root", "/", params);
        Assert.assertTrue(params.containsKey(NAME));

        // annotation
        ModelNode annotation = feature.require(ANNOTATION);
        Assert.assertEquals("server-root", annotation.require(ADDR_PARAMS).asString());
        Assert.assertEquals(WRITE_ATTRIBUTE_OPERATION, annotation.require(NAME).asString());
        Assert.assertEquals(NAME, annotation.require(OP_PARAMS).asString());

        // capabilities
        ModelNode provides = feature.require(PROVIDES);
        Assert.assertEquals(4, provides.asList().size());
        Set<String> providedCaps = new HashSet<>(4);
        for(ModelNode providedCap : provides.asList()) {
            providedCaps.add(providedCap.asString());
        }
        Assert.assertTrue(providedCaps.contains(ROOT_CAPABILITY_NAME));
        Assert.assertTrue(providedCaps.contains(DYNAMIC_CAPABILITY_NAME));
        Assert.assertTrue(providedCaps.contains(ModelControllerClientFactory.SERVICE_DESCRIPTOR.getName()));
        Assert.assertTrue(providedCaps.contains(NOTIFICATION_REGISTRY_CAPABILITY_NAME));
    }

    /**
     * Reads a subsystem recursively.
     *
     * The resource contains:
     *
     * - two storage resources,
     * - runtime resource and non-feature storage resource.
     *
     * Expectations:
     *
     * - only the two storage resources will be listed as children,
     * - children's children will be listed.
     *
     * (The usual stuff is tested by other tests.)
     */
    @Test
    public void testStorageResourceRecursive() throws OperationFailedException {
        ModelNode result = readFeatureDescription(PathAddress.pathAddress(SUBSYSTEM, TEST_SUBSYSTEM), true);

        ModelNode feature = result.require(FEATURE);
        Assert.assertEquals(SUBSYSTEM + "." + TEST_SUBSYSTEM, feature.require(NAME).asString());

        // subsystem params
        Map<String, ModelNode> params = extractParamsToMap(feature);
        assertFeatureIdParam(SUBSYSTEM, TEST_SUBSYSTEM, params);
        assertParamWithDefault(EXTENSION, TEST_EXTENSION, params);

        // subsystem children
        ModelNode resource1 = feature.require(CHILDREN).require("subsystem.testsubsystem.test.special-names-resource");
        Assert.assertEquals("subsystem.testsubsystem.test.special-names-resource", resource1.get(NAME).asString());
        params = extractParamsToMap(resource1);
        assertFeatureIdParam(SUBSYSTEM, TEST_SUBSYSTEM, params);
        assertFeatureIdParam(TEST, SPECIAL_NAMES_RESOURCE, params);
        Assert.assertTrue(params.containsKey("host-feature"));
        Assert.assertTrue(params.containsKey("test-feature"));
        Assert.assertTrue(params.containsKey("profile-feature"));

        ModelNode resource2 = feature.require(CHILDREN).require("subsystem.testsubsystem.resource.main-resource");
        Assert.assertEquals("subsystem.testsubsystem.resource.main-resource", resource2.get(NAME).asString());
        params = extractParamsToMap(resource2);
        assertFeatureIdParam(SUBSYSTEM, TEST_SUBSYSTEM, params);
        assertFeatureIdParam(RESOURCE, MAIN_RESOURCE, params);
        Assert.assertTrue(params.containsKey("optional-attr"));
        Assert.assertTrue(params.containsKey("mandatory-attr"));

        // check that nested resource children are also listed
        Assert.assertTrue(resource2.require(CHILDREN).get("subsystem.testsubsystem.resource.main-resource.list-attr").isDefined());
        Assert.assertTrue(resource2.require(CHILDREN).get("subsystem.testsubsystem.resource.main-resource.object-attr").isDefined());
    }

    /**
     * Reads alias to a storage resource.
     *
     * Expectations:
     *
     * - the target resource is read.
     */
    @Test
    public void testAliasToResource() throws OperationFailedException {
        ModelNode result = readFeatureDescription(PathAddress.pathAddress("alias", "alias-to-resource"));

        ModelNode feature = result.require(FEATURE);
        Assert.assertEquals("subsystem.testsubsystem.resource.main-resource", feature.require(NAME).asString());
        Map<String, ModelNode> params = extractParamsToMap(feature);
        assertFeatureIdParam(SUBSYSTEM, TEST_SUBSYSTEM, params);
        assertFeatureIdParam(RESOURCE, MAIN_RESOURCE, params);
    }

    private ModelNode readFeatureDescription(PathAddress address) throws OperationFailedException {
        return readFeatureDescription(address, false);
    }

    private ModelNode readFeatureDescription(PathAddress address, boolean recursive) throws OperationFailedException {
        ModelNode operation = createOperation(READ_FEATURE_DESCRIPTION_OPERATION, address);
        operation.get(RECURSIVE).set(recursive);
        ModelNode result = executeForResult(operation);
//        System.out.println(result);
        return result;
    }

    private void assertSortedArrayEquals(String[] expectedArray, String[] array) {
        Arrays.sort(expectedArray);
        Arrays.sort(array);
        Assert.assertArrayEquals(expectedArray, array);
    }

    private void assertReadFeatureUndefined(PathAddress address) throws OperationFailedException {
        ModelNode result = readFeatureDescription(address);
        ModelNode feature = result.get(FEATURE);
        Assert.assertFalse(feature.isDefined());
    }

    private void assertFeatureIdParam(String name, String defVal, Map<String, ModelNode> params) {
        Assert.assertTrue(params.containsKey(name));
        Assert.assertEquals(defVal, params.get(name).require(DEFAULT).asString());
        Assert.assertTrue(params.get(name).require(FEATURE_ID).asBoolean());
    }

    private void assertParamWithDefault(String name, String defVal, Map<String, ModelNode> params) {
        Assert.assertTrue(params.containsKey(name));
        Assert.assertEquals(defVal, params.get(name).require(DEFAULT).asString());
    }

    private Map<String, ModelNode> extractParamsToMap(ModelNode feature) {
        return extractToMap(feature.require(PARAMS), NAME);
    }

    private Map<String, ModelNode> extractRefsToMap(ModelNode feature) {
        return extractToMap(feature.require(REFS), FEATURE);
    }

    private Map<String, ModelNode> extractToMap(ModelNode node, String nameField) {
        HashMap<String, ModelNode> map = new HashMap<>();
        for (ModelNode ref : node.asList()) {
            map.put(ref.require(nameField).asString(), ref);
        }
        return map;
    }

    private String serializeAddress(PathAddress address) {
        StringBuilder sb = new StringBuilder();
        for (PathElement elem : address) {
            if (sb.length() != 0) {
                sb.append(".");
            }
            sb.append(elem.getKey()).append(".").append(elem.getValue());
        }
        return sb.toString();
    }

    @Override
    protected void initModel(ManagementModel managementModel) {
        registration = managementModel.getRootResourceRegistration();

        // register root attr and capability
        ModelOnlyWriteAttributeHandler writeHandler = new ModelOnlyWriteAttributeHandler();
        registration.registerReadWriteAttribute(new SimpleAttributeDefinitionBuilder(NAME, ModelType.STRING).build(), null, writeHandler);
        registration.registerCapability(RuntimeCapability.Builder.of(ROOT_CAPABILITY_NAME).build());
        registration.registerCapability(RuntimeCapability.Builder.of(DYNAMIC_CAPABILITY_NAME, true).build());

        // register extension with child subsystem
        Resource extensionRes = Resource.Factory.create();
        extensionRes.registerChild(PathElement.pathElement(SUBSYSTEM, TEST_SUBSYSTEM), Resource.Factory.create());
        managementModel.getRootResource().registerChild(PathElement.pathElement(EXTENSION, TEST_EXTENSION), extensionRes);

        // subsystem=testsubsystem
        ManagementResourceRegistration subsysRegistration =
                registration.registerSubModel(new SimpleResourceDefinition(
                        new SimpleResourceDefinition.Parameters(PathElement.pathElement(SUBSYSTEM, TEST_SUBSYSTEM),
                                NonResolvingResourceDescriptionResolver.INSTANCE)));
        subsysRegistration.registerCapability(RuntimeCapability.Builder.of("subsystem-capability").build());

        // resource=main-resource
        ManagementResourceRegistration mainResourceRegistration =
                subsysRegistration.registerSubModel(new MainResourceDefinition(
                        PathElement.pathElement(RESOURCE, MAIN_RESOURCE),
                            NonResolvingResourceDescriptionResolver.INSTANCE));

        // resource=referencing-resource
        subsysRegistration.registerSubModel(new ReferencingResourceDefinition(PathElement.pathElement(RESOURCE, REFERENCING_RESOURCE),
                NonResolvingResourceDescriptionResolver.INSTANCE));

        // register runtime resource
        subsysRegistration.registerSubModel(new SimpleResourceDefinition(
                new SimpleResourceDefinition.Parameters(PathElement.pathElement(RESOURCE, RUNTIME_RESOURCE),
                        NonResolvingResourceDescriptionResolver.INSTANCE)
                        .setRuntime()));

        // register another resource that is marked as not-a-feature
        subsysRegistration.registerSubModel(new SimpleResourceDefinition(
                new SimpleResourceDefinition.Parameters(PathElement.pathElement(RESOURCE, NON_FEATURE_RESOURCE),
                        NonResolvingResourceDescriptionResolver.INSTANCE)
                        .setFeature(false)));

        // register resource "test=storage-resource" with attribute "test", so that the "test" parameter will need to be
        // remapped to "test-feature"
        subsysRegistration.registerSubModel(new TestResourceDefinition(PathElement.pathElement(TEST, SPECIAL_NAMES_RESOURCE),
                        NonResolvingResourceDescriptionResolver.INSTANCE));

        registration.registerAlias(PathElement.pathElement("alias", "alias-to-resource"), new AliasEntry(mainResourceRegistration) {
            @Override
            public PathAddress convertToTargetAddress(PathAddress aliasAddress, AliasContext aliasContext) {
                return getTargetAddress();
            }
        });
    }

    private static class MainResourceDefinition extends SimpleResourceDefinition {

        private static final RuntimeCapability<?> MAIN_RESOURCE_CAPABILITY =
                RuntimeCapability.Builder.of(MAIN_RESOURCE_CAPABILITY_NAME, true)
                        .build();

        private static final AttributeDefinition OPTIONAL_ATTRIBUTE =
                new SimpleAttributeDefinitionBuilder("optional-attr", ModelType.INT, true)
                        .setCapabilityReference(ROOT_CAPABILITY_NAME)
                        .build();
        private static final AttributeDefinition DYNAMIC_ATTRIBUTE =
                new SimpleAttributeDefinitionBuilder("dynamic-attr", ModelType.STRING, true)
                        .setCapabilityReference(DYNAMIC_CAPABILITY_NAME)
                        .build();
        private static final AttributeDefinition MANDATORY_ATTRIBUTE =
                new SimpleAttributeDefinitionBuilder("mandatory-attr", ModelType.INT)
                        .build();
        private static final AttributeDefinition READ_ONLY_ATTRIBUTE =
                new SimpleAttributeDefinitionBuilder("read-only-attr", ModelType.INT)
                        .setStorageRuntime()
                        .build();
        private static final ObjectTypeAttributeDefinition OBJECT_ATTRIBUTE =
                ObjectTypeAttributeDefinition.Builder.of("object-attr", OPTIONAL_ATTRIBUTE)
                        .setResourceOnly()
                        .build();
        private static final ObjectTypeAttributeDefinition COMPLEX_OBJECT_ATTRIBUTE =
                ObjectTypeAttributeDefinition.Builder.of("complex-object-attr", OPTIONAL_ATTRIBUTE, DYNAMIC_ATTRIBUTE)
                        .setResourceOnly()
                        .build();
        private static final ObjectListAttributeDefinition LIST_ATTRIBUTE =
                ObjectListAttributeDefinition.Builder.of("list-attr", OBJECT_ATTRIBUTE)
                        .setResourceOnly()
                        .build();

        MainResourceDefinition(PathElement pathElement, ResourceDescriptionResolver descriptionResolver) {
            super(new SimpleResourceDefinition.Parameters(pathElement, descriptionResolver)
                    .setAddHandler(new AddHandler())
                    .addCapabilities(MAIN_RESOURCE_CAPABILITY)
            );
        }

        @Override
        public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
            super.registerAttributes(resourceRegistration);
            resourceRegistration.registerReadWriteAttribute(OPTIONAL_ATTRIBUTE, null, WRITE_HANDLER);
            resourceRegistration.registerReadWriteAttribute(MANDATORY_ATTRIBUTE, null, WRITE_HANDLER);
            resourceRegistration.registerReadOnlyAttribute(READ_ONLY_ATTRIBUTE, null);
            resourceRegistration.registerReadWriteAttribute(OBJECT_ATTRIBUTE, null, WRITE_HANDLER);
            resourceRegistration.registerReadWriteAttribute(COMPLEX_OBJECT_ATTRIBUTE, null, WRITE_HANDLER);
            resourceRegistration.registerReadWriteAttribute(LIST_ATTRIBUTE, null, WRITE_HANDLER);
        }

        @Override
        public void registerAdditionalRuntimePackages(ManagementResourceRegistration resourceRegistration) {
            resourceRegistration.registerAdditionalRuntimePackages(RuntimePackageDependency.optional(MAIN_RESOURCE_OPT_PACKAGE_NAME),
                    RuntimePackageDependency.passive(MAIN_RESOURCE_PASSIVE_PACKAGE_NAME),
                    RuntimePackageDependency.required(MAIN_RESOURCE_REQUIRED_PACKAGE_NAME));
        }
    }

    private static class ReferencingResourceDefinition extends SimpleResourceDefinition {

        private static final String REFERENCING_RESOURCE_CAPABILITY_NAME = "referencing-resource-capability";

        private static final RuntimeCapability REFERENCING_RESOURCE_CAPABILITY =
                RuntimeCapability.Builder.of(REFERENCING_RESOURCE_CAPABILITY_NAME, true).build();

        private static final AttributeDefinition MAIN_RESOURCE_ATTR =
                new SimpleAttributeDefinitionBuilder(MAIN_RESOURCE, ModelType.STRING)
                        .setCapabilityReference(MAIN_RESOURCE_CAPABILITY_NAME, REFERENCING_RESOURCE_CAPABILITY)
                        .addArbitraryDescriptor(FEATURE_REFERENCE, ModelNode.TRUE)
                        .build();

        ReferencingResourceDefinition(PathElement pathElement, ResourceDescriptionResolver descriptionResolver) {
            super(new Parameters(pathElement, descriptionResolver)
                    .addCapabilities(REFERENCING_RESOURCE_CAPABILITY)
                    .addRequirement(REFERENCING_RESOURCE_CAPABILITY_NAME, null, ROOT_CAPABILITY_NAME, null)
            );
        }

        @Override
        public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
            super.registerAttributes(resourceRegistration);
            resourceRegistration.registerReadWriteAttribute(MAIN_RESOURCE_ATTR, null, WRITE_HANDLER);
        }
    }

    private static class TestResourceDefinition extends SimpleResourceDefinition {

        private static final AttributeDefinition PROFILE_ATTRIBUTE =
                new SimpleAttributeDefinitionBuilder(PROFILE, ModelType.INT).build();
        private static final AttributeDefinition HOST_ATTRIBUTE =
                new SimpleAttributeDefinitionBuilder(HOST, ModelType.INT).build();
        private static final AttributeDefinition TEST_ATTRIBUTE =
                new SimpleAttributeDefinitionBuilder(TEST, ModelType.INT).build();

        public TestResourceDefinition(PathElement pathElement, ResourceDescriptionResolver descriptionResolver) {
            super(pathElement, descriptionResolver);
        }

        @Override
        public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
            super.registerAttributes(resourceRegistration);
            resourceRegistration.registerReadWriteAttribute(PROFILE_ATTRIBUTE, null, WRITE_HANDLER);
            resourceRegistration.registerReadWriteAttribute(HOST_ATTRIBUTE, null, WRITE_HANDLER);
            resourceRegistration.registerReadWriteAttribute(TEST_ATTRIBUTE, null, WRITE_HANDLER);
        }
    }

    private static class AddHandler extends AbstractAddStepHandler {
        AddHandler() {
        }
    }
}
