/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.operations.validation.OperationValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationTransformerRegistry;
import org.jboss.as.controller.transform.OperationResultTransformer;
import org.jboss.as.controller.transform.OperationTransformer;
import org.jboss.as.controller.transform.OperationTransformer.TransformedOperation;
import org.jboss.as.controller.transform.TransformationContext;
import org.jboss.as.controller.transform.TransformationTarget;
import org.jboss.as.controller.transform.TransformationTargetImpl;
import org.jboss.as.controller.transform.TransformerOperationAttachment;
import org.jboss.as.controller.transform.TransformerRegistry;
import org.jboss.as.controller.transform.Transformers;
import org.jboss.as.core.model.bridge.impl.LegacyControllerKernelServicesProxy;
import org.jboss.as.domain.controller.operations.ReadMasterDomainModelHandler;
import org.jboss.as.host.controller.ignored.IgnoreDomainResourceTypeResource;
import org.jboss.as.model.test.ModelTestModelControllerService;
import org.jboss.as.model.test.StringConfigurationPersister;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceContainer;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class MainKernelServicesImpl extends AbstractKernelServicesImpl {
    private final ExtensionRegistry extensionRegistry;

    public MainKernelServicesImpl(ServiceContainer container, ModelTestModelControllerService controllerService,
            StringConfigurationPersister persister, ManagementResourceRegistration rootRegistration,
            OperationValidator operationValidator, ModelVersion legacyModelVersion, boolean successfulBoot, Throwable bootError,
            ExtensionRegistry extensionRegistry) {
        // FIXME MainKernelServicesImpl constructor
        super(container, controllerService, persister, rootRegistration, operationValidator, legacyModelVersion, successfulBoot,
                bootError, extensionRegistry);
        this.extensionRegistry = extensionRegistry;
    }

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
    private TransformedOperation transformOperation(ModelVersion modelVersion, ModelNode operation, TransformerOperationAttachment attachment) throws OperationFailedException {
        checkIsMainController();
        TransformerRegistry transformerRegistry = extensionRegistry.getTransformerRegistry();

        PathAddress address = PathAddress.pathAddress(operation.get(OP_ADDR));
        Map<PathAddress, ModelVersion> subsystemVersions = Collections.<PathAddress, ModelVersion>emptyMap();
        OperationTransformerRegistry registry = transformerRegistry.resolveHost(modelVersion, subsystemVersions);

        TransformationTarget target = TransformationTargetImpl.create(null, extensionRegistry.getTransformerRegistry(), modelVersion,
                subsystemVersions, TransformationTarget.TransformationTargetType.DOMAIN);
        TransformationContext transformationContext = createTransformationContext(target, attachment);

        OperationTransformer operationTransformer = registry.resolveOperationTransformer(address, operation.get(OP).asString(), null).getTransformer();
        if (operationTransformer != null) {
            return operationTransformer.transformOperation(transformationContext, address, operation);
        }
        return new OperationTransformer.TransformedOperation(operation, OperationResultTransformer.ORIGINAL_RESULT);
    }

    @Override
    public ModelNode readTransformedModel(ModelVersion modelVersion) {
        checkIsMainController();

        ModelNode domainModel = new ModelNode();
        //Reassemble the model from the reead master domain model handler result
        for (ModelNode entry : callReadMasterDomainModelHandler(modelVersion).asList()) {
            PathAddress address = PathAddress.pathAddress(entry.require("domain-resource-address"));
            ModelNode toSet = domainModel;
            for (PathElement pathElement : address) {
                toSet = toSet.get(pathElement.getKey(), pathElement.getValue());
            }
            toSet.set(entry.require("domain-resource-model"));
        }
        return domainModel;
    }

    @Override
    public ModelNode callReadMasterDomainModelHandler(ModelVersion modelVersion){
        checkIsMainController();

        final TransformationTarget target = TransformationTargetImpl.create(null, extensionRegistry.getTransformerRegistry(), modelVersion,
                Collections.<PathAddress, ModelVersion>emptyMap(), TransformationTarget.TransformationTargetType.DOMAIN);
        final Transformers transformers = Transformers.Factory.create(target);

        ModelNode fakeOP = new ModelNode();
        fakeOP.get(OP).set("fake");
        ModelNode result = internalExecute(fakeOP, new ReadMasterDomainModelHandler(null, transformers, extensionRegistry, true));

        if (FAILED.equals(result.get(OUTCOME).asString())) {
            throw new RuntimeException(result.get(FAILURE_DESCRIPTION).asString());
        }

        return result.get(RESULT);
    }

    @Override
    public void applyMasterDomainModel(ModelVersion modelVersion, List<IgnoreDomainResourceTypeResource> ignoredResources) {
        checkIsMainController();
        LegacyControllerKernelServicesProxy legacyServices = (LegacyControllerKernelServicesProxy)getLegacyServices(modelVersion);
        ModelNode masterResources = callReadMasterDomainModelHandler(modelVersion);
        legacyServices.applyMasterDomainModel(masterResources, ignoredResources);

    }

    /**
     * Execute an operation in the  controller containg the passed in version.
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
        }
        OperationResultTransformer resultTransformer = op.getResultTransformer();
        if (resultTransformer != null) {
            result = resultTransformer.transformResult(result);
        }
        return result;
    }

}
