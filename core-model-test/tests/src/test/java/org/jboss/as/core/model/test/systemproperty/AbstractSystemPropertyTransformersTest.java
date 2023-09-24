/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test.systemproperty;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BOOT_TIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;

import java.util.List;

import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.core.model.test.AbstractCoreModelTest;
import org.jboss.as.core.model.test.KernelServices;
import org.jboss.as.core.model.test.KernelServicesBuilder;
import org.jboss.as.core.model.test.LegacyKernelServicesInitializer;
import org.jboss.as.core.model.test.TestModelType;
import org.jboss.as.core.model.test.TransformersTestParameterized.TransformersParameter;
import org.jboss.as.core.model.test.util.StandardServerGroupInitializers;
import org.jboss.as.core.model.test.util.TransformersTestParameter;
import org.jboss.as.model.test.FailedOperationTransformationConfig;
import org.jboss.as.model.test.ModelFixer;
import org.jboss.as.model.test.ModelTestControllerVersion;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public abstract class AbstractSystemPropertyTransformersTest extends AbstractCoreModelTest {

    private final ModelVersion modelVersion;
    private final ModelTestControllerVersion testControllerVersion;
    private final boolean serverGroup;
    private final ModelNode expectedUndefined;

    public AbstractSystemPropertyTransformersTest(TransformersTestParameter params, boolean serverGroup) {
        this.modelVersion = params.getModelVersion();
        this.testControllerVersion = params.getTestControllerVersion();
        this.serverGroup = serverGroup;
        this.expectedUndefined = getExpectedUndefined(params.getModelVersion());
    }

    @TransformersParameter
    public static List<TransformersTestParameter> parameters(){
        return TransformersTestParameter.setupVersions();
    }

    @Test
    public void testSystemPropertyTransformer() throws Exception {
        KernelServicesBuilder builder = createKernelServicesBuilder(TestModelType.DOMAIN)
                .setXmlResource(serverGroup ? "domain-servergroup-systemproperties.xml" : "domain-systemproperties.xml");
        if (serverGroup) {
            builder.setModelInitializer(StandardServerGroupInitializers.XML_MODEL_INITIALIZER, StandardServerGroupInitializers.XML_MODEL_WRITE_SANITIZER);
        }

        LegacyKernelServicesInitializer legacyInitializer = builder.createLegacyKernelServicesBuilder(modelVersion, testControllerVersion);
        if (serverGroup) {
            StandardServerGroupInitializers.addServerGroupInitializers(legacyInitializer);
        }

        KernelServices mainServices = builder.build();
        Assert.assertTrue(mainServices.isSuccessfulBoot());

        KernelServices legacyServices = mainServices.getLegacyServices(modelVersion);
        Assert.assertTrue(legacyServices.isSuccessfulBoot());

        ModelFixer fixer = new StandardServerGroupInitializers.Fixer(modelVersion);
        ModelNode legacyModel = checkCoreModelTransformation(mainServices, modelVersion, fixer, fixer);
        ModelNode properties = legacyModel;
        if (serverGroup) {
            properties = legacyModel.get(SERVER_GROUP, "test");
        }
        properties = properties.get(SYSTEM_PROPERTY);
        Assert.assertEquals(expectedUndefined, properties.get("sys.prop.test.one", BOOT_TIME));
        Assert.assertEquals(1, properties.get("sys.prop.test.one", VALUE).asInt());
        Assert.assertEquals(ModelNode.TRUE, properties.get("sys.prop.test.two", BOOT_TIME));
        Assert.assertEquals(2, properties.get("sys.prop.test.two", VALUE).asInt());
        Assert.assertEquals(ModelNode.FALSE, properties.get("sys.prop.test.three", BOOT_TIME));
        Assert.assertEquals(3, properties.get("sys.prop.test.three", VALUE).asInt());
        Assert.assertEquals(expectedUndefined, properties.get("sys.prop.test.four", BOOT_TIME));
        Assert.assertFalse(properties.get("sys.prop.test.four", VALUE).isDefined());

        //Test the write attribute handler, the 'add' got tested at boot time
        PathAddress baseAddress = serverGroup ? PathAddress.pathAddress(PathElement.pathElement(SERVER_GROUP, "test")) : PathAddress.EMPTY_ADDRESS;
        PathAddress propAddress = baseAddress.append(SYSTEM_PROPERTY, "sys.prop.test.two");
        //value should just work
        ModelNode op = Util.getWriteAttributeOperation(propAddress, VALUE, new ModelNode("test12"));
        ModelTestUtils.checkOutcome(mainServices.executeOperation(modelVersion, mainServices.transformOperation(modelVersion, op)));
        Assert.assertEquals("test12", ModelTestUtils.getSubModel(legacyServices.readWholeModel(), propAddress).get(VALUE).asString());

        //boot time should be 'true' if undefined
        op = Util.getWriteAttributeOperation(propAddress, BOOT_TIME, new ModelNode());
        ModelTestUtils.checkOutcome(mainServices.executeOperation(modelVersion, mainServices.transformOperation(modelVersion, op)));
        Assert.assertTrue(ModelTestUtils.getSubModel(legacyServices.readWholeModel(), propAddress).get(BOOT_TIME).asBoolean());
        op = Util.getUndefineAttributeOperation(propAddress, BOOT_TIME);
        ModelTestUtils.checkOutcome(mainServices.executeOperation(modelVersion, mainServices.transformOperation(modelVersion, op)));
        Assert.assertTrue(ModelTestUtils.getSubModel(legacyServices.readWholeModel(), propAddress).get(BOOT_TIME).asBoolean());
    }

    @Test
    public void testSystemPropertiesWithExpressions() throws Exception {
        System.setProperty("sys.prop.test.one", "ONE");
        KernelServicesBuilder builder = createKernelServicesBuilder(TestModelType.DOMAIN);
        if (serverGroup) {
            builder.setModelInitializer(StandardServerGroupInitializers.XML_MODEL_INITIALIZER, StandardServerGroupInitializers.XML_MODEL_WRITE_SANITIZER);
        }

        LegacyKernelServicesInitializer legacyInitializer = builder.createLegacyKernelServicesBuilder(modelVersion, testControllerVersion);
        if (serverGroup) {
            StandardServerGroupInitializers.addServerGroupInitializers(legacyInitializer);
        }

        KernelServices mainServices = builder.build();
        Assert.assertTrue(mainServices.isSuccessfulBoot());

        //This passes since the boot currently does not invoke the transformers, and the rejected expression transformer which
        //would fail fails on the way out.
        KernelServices legacyServices = mainServices.getLegacyServices(modelVersion);
        Assert.assertTrue(legacyServices.isSuccessfulBoot());


        List<ModelNode> ops = builder.parseXmlResource(serverGroup ? "domain-servergroup-systemproperties-with-expressions.xml" : "domain-systemproperties-with-expressions.xml");

        FailedOperationTransformationConfig config;
        if (allowExpressions()) {
            config = FailedOperationTransformationConfig.NO_FAILURES;
        } else {
            PathAddress root = serverGroup ? PathAddress.pathAddress(PathElement.pathElement(SERVER_GROUP)) : PathAddress.EMPTY_ADDRESS;
            config = new FailedOperationTransformationConfig()
                .addFailedAttribute(root.append(PathElement.pathElement(SYSTEM_PROPERTY)), new FailedOperationTransformationConfig.RejectExpressionsConfig(BOOT_TIME, VALUE));
        }
        ModelTestUtils.checkFailedTransformedBootOperations(mainServices, modelVersion, ops, config);

        checkCoreModelTransformation(mainServices, modelVersion, new RbacModelFixer(modelVersion), new RbacModelFixer(modelVersion) {
            @Override
            public ModelNode fixModel(ModelNode modelNode) {
                modelNode = super.fixModel(modelNode);
                modelNode.remove(SOCKET_BINDING_GROUP);
                if (!allowExpressions()) {
                    modelNode =  resolve(modelNode);
                    ModelNode sysPropRoot = serverGroup ? modelNode.get(SERVER_GROUP, "test") : modelNode;
                    for (Property sysprop : sysPropRoot.get(SYSTEM_PROPERTY).asPropertyList()) {
                        ModelNode bootTime;
//                        if (sysprop.getValue().hasDefined(BOOT_TIME) && (bootTime = sysprop.getValue().get(BOOT_TIME)).getType() == ModelType.STRING) {
//                            // Convert to boolean
//                            sysPropRoot.get(SYSTEM_PROPERTY, sysprop.getName(), BOOT_TIME).set(bootTime.asBoolean());
//                        }
                    }
                }
                return modelNode;
            }
        });
    }

    private ModelNode getExpectedUndefined(ModelVersion modelVersion){
        if (modelVersion_1_4_0_OrGreater()) {
            return new ModelNode();
        } else {
            return ModelNode.TRUE;
        }
    }

    private boolean allowExpressions() {
        return modelVersion_1_4_0_OrGreater();
    }

    private boolean modelVersion_1_4_0_OrGreater() {
        return ModelVersion.compare(ModelVersion.create(1, 4, 0), modelVersion) >= 0;
    }

    private static ModelNode resolve(ModelNode unresolved) {
        try {
            return ExpressionResolver.TEST_RESOLVER.resolveExpressions(unresolved);
        } catch (OperationFailedException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
