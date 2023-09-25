/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.subsystem.test;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.operations.validation.OperationValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.transform.OperationTransformer.TransformedOperation;
import org.jboss.as.model.test.ModelTestModelControllerService;
import org.jboss.as.model.test.StringConfigurationPersister;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceContainer;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class LegacyKernelServicesImpl extends AbstractKernelServicesImpl implements KernelServicesInternal {

    public LegacyKernelServicesImpl(ServiceContainer container, ModelTestModelControllerService controllerService,
            StringConfigurationPersister persister, ManagementResourceRegistration rootRegistration,
            OperationValidator operationValidator, String mainSubsystemName, ExtensionRegistry extensionRegistry,
            ModelVersion legacyModelVersion, boolean successfulBoot, Throwable bootError, boolean registerTransformers) {
        // FIXME LegacyKernelServicesImpl constructor
        super(container, controllerService, persister, rootRegistration, operationValidator, mainSubsystemName,
                extensionRegistry, legacyModelVersion, successfulBoot, bootError, registerTransformers);
    }

    @Override
    public TransformedOperation transformOperation(ModelVersion modelVersion, ModelNode operation)
            throws OperationFailedException {
        //Will throw an error since we are not the main controller
        checkIsMainController();
        return null;
    }

    @Override
    public ModelNode readTransformedModel(ModelVersion modelVersion, boolean includeDefaults) {
        //Will throw an error since we are not the main controller
        checkIsMainController();
        return null;
    }

    @Override
    public ModelNode executeOperation(ModelVersion modelVersion, TransformedOperation op) {
        //Will throw an error since we are not the main controller
        checkIsMainController();
        return null;
    }

    @Override
    public TransformedOperation executeInMainAndGetTheTransformedOperation(ModelNode op, ModelVersion modelVersion) {
        //Will throw an error since we are not the main controller
        checkIsMainController();
        return null;
    }

    @Override
    public Class<?> getTestClass() {
        checkIsMainController();
        return null;
    }
}
