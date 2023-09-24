/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.model.test.ModelFixer;
import org.jboss.dmr.ModelNode;

/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public interface LegacyKernelServicesInitializer {

    LegacyKernelServicesInitializer initializerCreateModelResource(PathAddress parentAddress, PathElement relativeResourceAddress, ModelNode model, String... capabilities);

    LegacyKernelServicesInitializer initializerCreateModelResource(PathAddress parentAddress, PathElement relativeResourceAddress, ModelNode model);

    /**
     * If called, will not use boot operations rather ship across the model via ApplyRemoteMasterDomainHandler
     */
    LegacyKernelServicesInitializer setDontUseBootOperations();

    /**
     * By default all operations sent into the model controller will be validated on boot. Operations matching what is
     * set up here will not be validated. This is mainly because the {@link org.jboss.as.controller.operations.validation.OperationValidator} used in 7.1.x did not handle expressions very well
     * when checking ranges. If there is a problem you should try to call {@link #addOperationValidationResolve(String, PathAddress)}
     * first.
     *
     * @param name the name of the operation, or {@code *} as a wildcard capturing all names
     * @param pathAddress the address of the operation, the pathAddress may use {@code *} as a wildcard for both the key and the value of {@link PathElement}s
     */
    LegacyKernelServicesInitializer addOperationValidationExclude(String name, PathAddress pathAddress);

    /**
     * By default all operations sent into the model controller will be validated on boot. Operations matching what is
     * set up here will not be validated. This is mainly because the {@link org.jboss.as.controller.operations.validation.OperationValidator} used in 7.1.x did not handle expressions very well
     * when checking ranges.
     *
     * @param name the name of the operation, or {@code *} as a wildcard capturing all names
     * @param pathAddress the address of the operation, the pathAddress may use {@code *} as a wildcard for both the key and the value of {@link PathElement}s
     */
    LegacyKernelServicesInitializer addOperationValidationResolve(String name, PathAddress pathAddress);

    LegacyKernelServicesInitializer skipReverseControllerCheck();

    LegacyKernelServicesInitializer configureReverseControllerCheck(ModelFixer mainModelFixer, ModelFixer legacyModelFixer);

}
