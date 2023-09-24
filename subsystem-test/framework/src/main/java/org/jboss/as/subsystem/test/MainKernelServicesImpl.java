/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.subsystem.test;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_DEFAULTS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_TRANSFORMED_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.operations.validation.OperationValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.transform.OperationResultTransformer;
import org.jboss.as.controller.transform.OperationTransformer;
import org.jboss.as.controller.transform.OperationTransformer.TransformedOperation;
import org.jboss.as.controller.transform.TransformationContext;
import org.jboss.as.controller.transform.TransformationTarget;
import org.jboss.as.controller.transform.TransformationTargetImpl;
import org.jboss.as.controller.transform.TransformerOperationAttachment;
import org.jboss.as.controller.transform.Transformers;
import org.jboss.as.model.test.ModelTestModelControllerService;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.as.model.test.StringConfigurationPersister;
import org.jboss.as.server.mgmt.ManagementWorkerService;
import org.jboss.as.version.Version;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceContainer;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
class MainKernelServicesImpl extends AbstractKernelServicesImpl {
    private final Class<?> testClass;
    private static final ModelVersion CURRENT_CORE_VERSION = ModelVersion.create(Version.MANAGEMENT_MAJOR_VERSION,
            Version.MANAGEMENT_MINOR_VERSION, Version.MANAGEMENT_MICRO_VERSION);

    protected MainKernelServicesImpl(ServiceContainer container, ModelTestModelControllerService controllerService,
            StringConfigurationPersister persister, ManagementResourceRegistration rootRegistration,
            OperationValidator operationValidator, String mainSubsystemName, ExtensionRegistry extensionRegistry,
            ModelVersion legacyModelVersion, boolean successfulBoot, Throwable bootError,
            boolean registerTransformers, Class<?> testClass) {
        super(container, controllerService, persister, rootRegistration, operationValidator, mainSubsystemName, extensionRegistry,
                legacyModelVersion, successfulBoot, bootError, registerTransformers);
        this.testClass = testClass;
        //add mgmt worker
        ManagementWorkerService.installService(container.subTarget());
    }

    /**
     * Transforms an operation in the main controller to the format expected by the model controller containing
     * the legacy subsystem
     *
     * @param modelVersion the subsystem model version of the legacy subsystem model controller
     * @param operation the operation to transform
     * @return the transformed operation
     * @throws IllegalStateException if this is not the test's main model controller
     */
    @Override
    public TransformedOperation transformOperation(ModelVersion modelVersion, ModelNode operation) throws OperationFailedException {
        return transformOperation(modelVersion, operation, null);
    }

    /**
     * Transforms an operation in the main controller to the format expected by the model controller containing
     * the legacy subsystem
     *
     * @param modelVersion the subsystem model version of the legacy subsystem model controller
     * @param operation the operation to transform
     * @param attachment attachments propagated from the operation context to the created transformer context.
     *                   This may be {@code null}. In a non-test scenario, this will be added by operation handlers
     *                   triggering the transformation, but for tests this needs to be hard-coded. Tests will need to
     *                   ensure themselves that the relevant attachments get set.
     * @return the transformed operation
     * @throws IllegalStateException if this is not the test's main model controller
     */
    private TransformedOperation transformOperation(ModelVersion modelVersion, ModelNode operation,
                                                   TransformerOperationAttachment attachment) throws OperationFailedException {
        checkIsMainController();
        PathElement pathElement = PathElement.pathElement(SUBSYSTEM, mainSubsystemName);
        PathAddress opAddr = PathAddress.pathAddress(operation.get(OP_ADDR));
        // Composite operations have no address
        if ((opAddr.size() > 0 && opAddr.getElement(0).equals(pathElement)) || operation.get(OP).asString().equals(COMPOSITE)) {

            final Map<PathAddress, ModelVersion> subsystem = Collections.singletonMap(PathAddress.EMPTY_ADDRESS.append(pathElement), modelVersion);
            final TransformationTarget transformationTarget = TransformationTargetImpl.create(null, extensionRegistry.getTransformerRegistry(), getCoreModelVersionByLegacyModelVersion(modelVersion),
                    subsystem, TransformationTarget.TransformationTargetType.SERVER);

            final Transformers transformers = Transformers.Factory.create(transformationTarget);
            final TransformationContext transformationContext = createTransformationContext(transformationTarget, attachment);
            return transformers.transformOperation(transformationContext, operation);
        }
        return new OperationTransformer.TransformedOperation(operation, OperationResultTransformer.ORIGINAL_RESULT);
    }

    /**
     * Transforms the model to the legacy subsystem model version
     * @param modelVersion the target legacy subsystem model version
     * @return the transformed model
     * @throws IllegalStateException if this is not the test's main model controller
     */
    @Override
    public ModelNode readTransformedModel(ModelVersion modelVersion, boolean includeDefaults) {
        getLegacyServices(modelVersion);//Checks we are the main controller
        ModelNode op = new ModelNode();
        op.get(OP).set(READ_TRANSFORMED_RESOURCE_OPERATION);
        op.get(OP_ADDR).set(PathAddress.EMPTY_ADDRESS.toModelNode());
        op.get(SUBSYSTEM).set(mainSubsystemName);
        op.get(INCLUDE_DEFAULTS).set(includeDefaults);
        ModelNode response = internalExecute(op, new ReadTransformedResourceOperation(getTransformersRegistry(), getCoreModelVersionByLegacyModelVersion(modelVersion), modelVersion));
        return ModelTestUtils.checkResultAndGetContents(response);
    }

    /**
     * Execute an operation in the  controller containg the passed in version of the subsystem.
     * The operation and results will be translated from the format for the main controller to the
     * legacy controller's format.
     *
     * @param modelVersion the subsystem model version of the legacy subsystem model controller
     * @param op the operation for the main controller
     * @throws IllegalStateException if this is not the test's main model controller
     * @throws IllegalStateException if there is no legacy controller containing the version of the subsystem
     */
    @Override
    public ModelNode executeOperation(final ModelVersion modelVersion, final TransformedOperation op) {
        KernelServices legacy = getLegacyServices(modelVersion);
        ModelNode result = new ModelNode();
        if (op.getTransformedOperation() != null) {
            result = legacy.executeOperation(op.getTransformedOperation(), new ModelController.OperationTransactionControl() {
                    @Override
                    public void operationPrepared(ModelController.OperationTransaction transaction, ModelNode result) {
                        if(op.rejectOperation(result)) {
                            transaction.rollback();
                        } else {
                            transaction.commit();
                        }
                    }
                });
            // TODO this still does not really model the way rejection is handled in the domain
            if(op.rejectOperation(result)) {
                final ModelNode newResponse = new ModelNode();
                newResponse.get(OUTCOME).set(FAILED);
                newResponse.get(FAILURE_DESCRIPTION).set(op.getFailureDescription());
                return newResponse;
            }
        }
        OperationResultTransformer resultTransformer = op.getResultTransformer();
        if (resultTransformer != null) {
            result = resultTransformer.transformResult(result);
        }
        return result;
    }

    @Override
    public Class<?> getTestClass() {
        return testClass;
    }

    @Override
    public TransformedOperation executeInMainAndGetTheTransformedOperation(ModelNode op, ModelVersion modelVersion) {
        try {
            ModelNode wrapper = Util.createEmptyOperation(TransformerAttachmentGrabber.DESC.getName(), PathAddress.EMPTY_ADDRESS);
            wrapper.get(VALUE).set(op);
            ModelTestUtils.checkOutcome(executeOperation(wrapper));

            try {
                return transformOperation(modelVersion, op, TransformerAttachmentGrabber.getAttachment());
            } catch (OperationFailedException e) {
                throw new RuntimeException(e);
            }
        } finally {
            TransformerAttachmentGrabber.clear();
        }
    }


    private ModelVersion getCoreModelVersionByLegacyModelVersion(ModelVersion legacyModelVersion) {
        //The reason the core model version is important is that is used to know if the ignored slave resources are known on the host or not
        //e.g 7.2.x uses core model version >= 1.4.0 and so we know which resources are ignored
        //7.1.x uses core model version <= 1.4.0 and so we have no idea which resources are ignored
        //This is important for example in RejectExpressionValuesTransformer

        if (System.getProperty("jboss.test.core.model.version.override") != null) {
            return ModelVersion.fromString(System.getProperty("jboss.test.core.model.version.override"));
        }

        ModelVersion coreModelVersion = KnownVersions.getCoreModelVersionForSubsystemVersion(mainSubsystemName, legacyModelVersion);
        if (coreModelVersion != null) {
            return coreModelVersion;
        }

        String fileName = mainSubsystemName + "-versions-to-as-versions.properties";

        InputStream in = this.getClass().getResourceAsStream("/" + fileName);
        if (in == null) {
//            throw new IllegalArgumentException("Version " + legacyModelVersion + " of " + mainSubsystemName + " is not a known version. Please add it to " +
//                    KnownVersions.class.getName() + ". Or if that is not possible, " +
//                    "include a src/test/resources/" + fileName +
//                    " file, which maps AS versions to model versions. E.g.:\n1.1.0=7.1.2\n1.2.0=7.1.3");
            // Use current
            return CURRENT_CORE_VERSION;
        }
        Properties props = new Properties();
        try {
            props.load(in);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                in.close();
            } catch (IOException e) {
                //
            }
        }

        String asVersion = (String)props.get(legacyModelVersion.toString());
        if (asVersion == null) {
//            throw new IllegalArgumentException("src/test/resources/" + fileName +
//                    " does not contain an AS mapping for modelversion + " +
//                    legacyModelVersion + "'. It needs to map AS versions to model versions. E.g.:\n1.1.0=7.1.2\n1.2.0=7.1.3");
            // Use current
            return CURRENT_CORE_VERSION;
        }

        coreModelVersion = KnownVersions.AS_CORE_MODEL_VERSION_BY_AS_VERSION.get(asVersion);
        if (coreModelVersion == null) {
//            throw new IllegalArgumentException("Unknown AS version '" + asVersion + "' determined from src/test/resources/" + fileName +
//                    ". Known AS versions are " + KnownVersions.AS_CORE_MODEL_VERSION_BY_AS_VERSION.keySet());
            // Use current
            coreModelVersion = CURRENT_CORE_VERSION;
        }
        return coreModelVersion;
    }

}
