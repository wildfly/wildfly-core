/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.jboss.as.subsystem.test.transformationutils;

import static org.jboss.as.controller.PathAddress.EMPTY_ADDRESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_DEFAULTS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.operations.global.ReadResourceHandler;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.controller.persistence.SubsystemMarshallingContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.as.subsystem.test.AbstractSubsystemTest;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.subsystem.test.ControllerInitializer;
import org.jboss.as.subsystem.test.InternalPackageProtectedAccess;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.subsystem.test.simple.subsystem.SimpleSubsystemExtension;
import org.jboss.as.subsystem.test.transformationutils.subsystem.TransformationUtilsExtension;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLElementWriter;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class TransformationUtilsTestCase extends AbstractSubsystemTest {

    private static final DelegatingExtension DELEGATING_EXTENSION = new DelegatingExtension();
    private KernelServices kernelServices;

    private static final PathAddress SUBSYSTEM_ADDR = PathAddress.pathAddress(SUBSYSTEM, TransformationUtilsExtension.SUBSYSTEM_NAME);

    private static Boolean includeUndefined = null;


    public TransformationUtilsTestCase() {
        super(TransformationUtilsExtension.SUBSYSTEM_NAME, DELEGATING_EXTENSION);
    }

    @After
    public void after() {
        kernelServices.shutdown();
        kernelServices = null;
        DELEGATING_EXTENSION.current = null;
        includeUndefined = null;
    }

    @Test
    public void testNoChildNoAttrReg_EmptySubsystemIsDefined_inclUndefined() throws Exception {
        List<ModelNode> operations = new ArrayList<>();
        operations.add(Util.createAddOperation(SUBSYSTEM_ADDR));

        ModelNode model = runAndReadSubsystemModel(TransformationUtilsExtension.createBuilder(), operations, true);

        ModelNode expected = new ModelNode();
        expected.setEmptyObject();

        ModelTestUtils.compare(expected, model);
    }

    @Test
    public void testNoChildWithAttrReg_EmptySubsystemIsDefined_inclUndefined() throws Exception {
        TransformationUtilsExtension.Builder builder = TransformationUtilsExtension.createBuilder()
                .addAttribute("test");

        List<ModelNode> operations = new ArrayList<>();
        operations.add(Util.createAddOperation(SUBSYSTEM_ADDR));

        ModelNode model = runAndReadSubsystemModel(builder, operations, true);

        ModelNode expected = new ModelNode();
        expected.get("test").set(new ModelNode());

        ModelTestUtils.compare(expected, model);
    }

    @Test
    public void testNoChildWithAttrReg_SubsystemWithAttrIsDefined_inclUndefined() throws Exception {
        TransformationUtilsExtension.Builder builder = TransformationUtilsExtension.createBuilder()
                .addAttribute("test");

        List<ModelNode> operations = new ArrayList<>();
        ModelNode add = Util.createAddOperation(SUBSYSTEM_ADDR);
        add.get("test").set("one");
        operations.add(add);

        ModelNode model = runAndReadSubsystemModel(builder, operations, true);

        ModelNode expected = new ModelNode();
        expected.get("test").set("one");

        ModelTestUtils.compare(expected, model);
    }

    @Test
    public void testHasChildNoAttrReg_EmptySubsystemIsDefined_inclUndefined() throws Exception {
        // This is one of the cases that https://issues.redhat.com/browse/WFCORE-7036 fixes
        TransformationUtilsExtension.Builder builder = TransformationUtilsExtension.createBuilder();
        PathElement pe = PathElement.pathElement("child", "test");
        builder.createChildBuilder(pe);

        List<ModelNode> operations = new ArrayList<>();
        operations.add(Util.createAddOperation(SUBSYSTEM_ADDR));


        ModelNode model = runAndReadSubsystemModel(builder, operations, true);

        ModelNode expected = new ModelNode();
        expected.setEmptyObject();

        ModelTestUtils.compare(expected, model);
    }

    @Test
    public void testHasChildNoAttrReg_SubsystemWithChildIsDefined_inclUndefined() throws Exception {
        TransformationUtilsExtension.Builder builder = TransformationUtilsExtension.createBuilder();
        PathElement pe = PathElement.pathElement("child", "test");
        builder.createChildBuilder(pe);

        List<ModelNode> operations = new ArrayList<>();
        operations.add(Util.createAddOperation(SUBSYSTEM_ADDR));
        operations.add(Util.createAddOperation(SUBSYSTEM_ADDR.append(pe)));


        ModelNode model = runAndReadSubsystemModel(builder, operations, true);

        ModelNode expected = new ModelNode();
        expected.get(pe.getKey(), pe.getValue()).setEmptyObject();

        ModelTestUtils.compare(expected, model);
    }


    @Test
    public void testHasChildWithAttrReg_EmptySubsystemIsDefined_inclUndefined() throws Exception {
        TransformationUtilsExtension.Builder builder = TransformationUtilsExtension.createBuilder()
                .addAttribute("test");
        builder.createChildBuilder(PathElement.pathElement("child", "test"));

        List<ModelNode> operations = new ArrayList<>();
        operations.add(Util.createAddOperation(SUBSYSTEM_ADDR));

        ModelNode model = runAndReadSubsystemModel(builder, operations, true);

        ModelNode expected = new ModelNode();
        expected.get("test").set(new ModelNode());

        ModelTestUtils.compare(expected, model);
    }

    @Test
    public void testHasChildWithAttrReg_SubsystemWithAttrIsDefined_inclUndefined() throws Exception {
        TransformationUtilsExtension.Builder builder = TransformationUtilsExtension.createBuilder()
                .addAttribute("test");
        builder.createChildBuilder(PathElement.pathElement("child", "test"));

        List<ModelNode> operations = new ArrayList<>();
        ModelNode add = Util.createAddOperation(SUBSYSTEM_ADDR);
        add.get("test").set("one");
        operations.add(add);

        ModelNode model = runAndReadSubsystemModel(builder, operations, true);

        ModelNode expected = new ModelNode();
        expected.get("test").set("one");

        ModelTestUtils.compare(expected, model);
    }

    @Test
    public void testHasChildWithAttrReg_SubsystemWithChildIsDefined_inclUndefined() throws Exception {
        TransformationUtilsExtension.Builder builder = TransformationUtilsExtension.createBuilder()
                .addAttribute("test");
        PathElement pe = PathElement.pathElement("child", "test");
        builder.createChildBuilder(pe);

        List<ModelNode> operations = new ArrayList<>();
        operations.add(Util.createAddOperation(SUBSYSTEM_ADDR));
        operations.add(Util.createAddOperation(SUBSYSTEM_ADDR.append(pe)));


        ModelNode model = runAndReadSubsystemModel(builder, operations, true);

        ModelNode expected = new ModelNode();
        // Attribute is undefined
        expected.get("test").set(new ModelNode()); //Undefined
        expected.get(pe.getKey(), pe.getValue()).setEmptyObject();

        ModelTestUtils.compare(expected, model);
    }

    //////////

    @Test
    public void testNoChildNoAttrReg_EmptySubsystemIsDefined_exclUndefined() throws Exception {
        List<ModelNode> operations = new ArrayList<>();
        operations.add(Util.createAddOperation(SUBSYSTEM_ADDR));

        ModelNode model = runAndReadSubsystemModel(TransformationUtilsExtension.createBuilder(), operations, false);

        ModelNode expected = new ModelNode();
        expected.setEmptyObject();

        ModelTestUtils.compare(expected, model);
    }

    @Test
    public void testNoChildWithAttrReg_EmptySubsystemIsDefined_exclUndefined() throws Exception {
        TransformationUtilsExtension.Builder builder = TransformationUtilsExtension.createBuilder()
                .addAttribute("test");

        List<ModelNode> operations = new ArrayList<>();
        operations.add(Util.createAddOperation(SUBSYSTEM_ADDR));

        ModelNode model = runAndReadSubsystemModel(builder, operations, false);

        ModelNode expected = new ModelNode();
        expected.setEmptyObject();

        ModelTestUtils.compare(expected, model);
    }

    @Test
    public void testNoChildWithAttrReg_SubsystemWithAttrIsDefined_exclUndefined() throws Exception {
        TransformationUtilsExtension.Builder builder = TransformationUtilsExtension.createBuilder()
                .addAttribute("test");

        List<ModelNode> operations = new ArrayList<>();
        ModelNode add = Util.createAddOperation(SUBSYSTEM_ADDR);
        add.get("test").set("one");
        operations.add(add);

        ModelNode model = runAndReadSubsystemModel(builder, operations, false);

        ModelNode expected = new ModelNode();
        expected.get("test").set("one");

        ModelTestUtils.compare(expected, model);
    }

    @Test
    public void testHasChildNoAttrReg_EmptySubsystemIsDefined_exclUndefined() throws Exception {
        // This is one of the cases that https://issues.redhat.com/browse/WFCORE-7036 fixes
        TransformationUtilsExtension.Builder builder = TransformationUtilsExtension.createBuilder();
        PathElement pe = PathElement.pathElement("child", "test");
        builder.createChildBuilder(pe);

        List<ModelNode> operations = new ArrayList<>();
        operations.add(Util.createAddOperation(SUBSYSTEM_ADDR));


        ModelNode model = runAndReadSubsystemModel(builder, operations, false);

        ModelNode expected = new ModelNode();
        expected.setEmptyObject();

        ModelTestUtils.compare(expected, model);
    }

    @Test
    public void testHasChildNoAttrReg_SubsystemWithChildIsDefined_exclUndefined() throws Exception {
        TransformationUtilsExtension.Builder builder = TransformationUtilsExtension.createBuilder();
        PathElement pe = PathElement.pathElement("child", "test");
        builder.createChildBuilder(pe);

        List<ModelNode> operations = new ArrayList<>();
        operations.add(Util.createAddOperation(SUBSYSTEM_ADDR));
        operations.add(Util.createAddOperation(SUBSYSTEM_ADDR.append(pe)));


        ModelNode model = runAndReadSubsystemModel(builder, operations, false);

        ModelNode expected = new ModelNode();
        expected.get(pe.getKey(), pe.getValue()).setEmptyObject();

        ModelTestUtils.compare(expected, model);
    }


    @Test
    public void testHasChildWithAttrReg_EmptySubsystemIsDefined_exclUndefined() throws Exception {
        // This is one of the cases that https://issues.redhat.com/browse/WFCORE-7036 fixes
        TransformationUtilsExtension.Builder builder = TransformationUtilsExtension.createBuilder()
                .addAttribute("test");
        builder.createChildBuilder(PathElement.pathElement("child", "test"));

        List<ModelNode> operations = new ArrayList<>();
        operations.add(Util.createAddOperation(SUBSYSTEM_ADDR));

        ModelNode model = runAndReadSubsystemModel(builder, operations, false);

        ModelNode expected = new ModelNode();
        expected.setEmptyObject();

        ModelTestUtils.compare(expected, model);
    }

    @Test
    public void testHasChildWithAttrReg_SubsystemWithAttrIsDefined_exclUndefined() throws Exception {
        TransformationUtilsExtension.Builder builder = TransformationUtilsExtension.createBuilder()
                .addAttribute("test");
        builder.createChildBuilder(PathElement.pathElement("child", "test"));

        List<ModelNode> operations = new ArrayList<>();
        ModelNode add = Util.createAddOperation(SUBSYSTEM_ADDR);
        add.get("test").set("one");
        operations.add(add);

        ModelNode model = runAndReadSubsystemModel(builder, operations, false);

        ModelNode expected = new ModelNode();
        expected.get("test").set("one");

        ModelTestUtils.compare(expected, model);
    }

    @Test
    public void testHasChildWithAttrReg_SubsystemWithChildIsDefined_exclUndefined() throws Exception {
        TransformationUtilsExtension.Builder builder = TransformationUtilsExtension.createBuilder()
                .addAttribute("test");
        PathElement pe = PathElement.pathElement("child", "test");
        builder.createChildBuilder(pe);

        List<ModelNode> operations = new ArrayList<>();
        operations.add(Util.createAddOperation(SUBSYSTEM_ADDR));
        operations.add(Util.createAddOperation(SUBSYSTEM_ADDR.append(pe)));


        ModelNode model = runAndReadSubsystemModel(builder, operations, false);

        ModelNode expected = new ModelNode();
        expected.get(pe.getKey(), pe.getValue()).setEmptyObject();

        ModelTestUtils.compare(expected, model);
    }

//    @Test
//    public void testChildRegEmptySubsystemIsDefined() throws Exception {
//        List<ModelNode> operations = new ArrayList<>();
//        operations.add(Util.createAddOperation(SUBSYSTEM_ADDR));
//        TransformationUtilsExtension.Builder builder = TransformationUtilsExtension.createBuilder();
//        builder.
//
//        ModelNode model = runAndReadSubsystemModel(, operations);
//
//        Assert.assertTrue(model.isDefined());
//    }

    private ModelNode runAndReadSubsystemModel(TransformationUtilsExtension.Builder builder, List<ModelNode> bootOps, boolean includeUndefined) throws Exception {
        TransformationUtilsTestCase.includeUndefined = includeUndefined;
        Assert.assertNull(kernelServices);
        DELEGATING_EXTENSION.current = builder.build();
        kernelServices = createKernelServicesBuilder(new TestInitialization())
                .setBootOperations(bootOps)
                .build();

        ModelNode model = kernelServices.executeForResult(Util.createOperation("invoke-transformation-utils", EMPTY_ADDRESS));
        model = model.get(SUBSYSTEM).get(TransformationUtilsExtension.SUBSYSTEM_NAME);
        return model;
    }


    private static class DelegatingExtension implements Extension {

        volatile Extension current;

        DelegatingExtension() {
        }

        @Override
        public void initialize(ExtensionContext context) {
            current.initialize(context);
        }

        @Override
        public void initializeParsers(ExtensionParsingContext context) {
            context.setSubsystemXmlMapping(TransformationUtilsExtension.SUBSYSTEM_NAME, DummyParser.NAMESPACE, new DummyParser());
        }
    }


    private static class DummyParser implements XMLStreamConstants, XMLElementReader<List<ModelNode>>, XMLElementWriter<SubsystemMarshallingContext> {
        public static final String NAMESPACE = "urn:mycompany:dummy:1.0";

        /** {@inheritDoc} */
        @Override
        public void writeContent(XMLExtendedStreamWriter writer, SubsystemMarshallingContext context) throws XMLStreamException {
            // We don't actually invoke this parser, so what is does doesn't  matter.
            // It is just here since the testing framework requires a parser
            context.startSubsystemElement(SimpleSubsystemExtension.NAMESPACE, false);
            writer.writeEndElement();
        }

        /** {@inheritDoc} */
        @Override
        public void readElement(XMLExtendedStreamReader reader, List<ModelNode> list) throws XMLStreamException {
            // We don't actually invoke this parser, so the contents don't matter.
            // It is just here since the testing framework requires a parser
            ParseUtils.requireNoContent(reader);
            list.add(Util.createAddOperation(SUBSYSTEM_ADDR));
        }
    }


    private static class TestInitialization extends AdditionalInitialization.ManagementAdditionalInitialization {
        @Override
        protected ControllerInitializer createControllerInitializer() {
            return new ControllerInitializer() {
                @Override
                protected void initializeModel(Resource rootResource, ManagementResourceRegistration rootRegistration) {
                    super.initializeModel(rootResource, rootRegistration);
                    rootRegistration.registerOperationHandler(
                            new SimpleOperationDefinitionBuilder("invoke-transformation-utils", NonResolvingResourceDescriptionResolver.INSTANCE)
                                    .build(),
                            new OperationStepHandler() {
                                @Override
                                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                                    if (includeUndefined == null) {
                                        throw new IllegalStateException();
                                    }
                                    final boolean includeDefaults = operation.get(INCLUDE_DEFAULTS).asBoolean(true);
                                    // Add a step to transform the result of a READ_RESOURCE.
                                    // Do this first, Stage.IMMEDIATE
                                    final ModelNode readResourceResult = new ModelNode();
                                    context.addStep(new OperationStepHandler() {
                                        @Override
                                        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                                            // This mimics what TransformationUtils

                                            Resource root = InternalPackageProtectedAccess.modelToResource(
                                                    context.getRootResourceRegistration(), readResourceResult.get(RESULT), includeUndefined);
                                            context.getResult().set(Resource.Tools.readModel(root));
                                        }
                                    }, OperationContext.Stage.MODEL, true);

                                    // Now add a step to do the READ_RESOURCE, also IMMEDIATE. This will execute *before* the one ^^^
                                    final ModelNode op = new ModelNode();
                                    op.get(OP).set(READ_RESOURCE_OPERATION);
                                    op.get(OP_ADDR).set(PathAddress.EMPTY_ADDRESS.toModelNode());
                                    op.get(RECURSIVE).set(true);
                                    op.get(INCLUDE_DEFAULTS).set(includeDefaults);
                                    context.addStep(readResourceResult, op, ReadResourceHandler.INSTANCE, OperationContext.Stage.MODEL, true);
                                }
                            }
                    );
                }
            };
        }
    }


}
