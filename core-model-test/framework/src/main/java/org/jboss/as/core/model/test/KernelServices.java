/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test;

import java.util.List;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.host.controller.ignored.IgnoreDomainResourceTypeResource;
import org.jboss.as.model.test.ModelTestKernelServices;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public interface KernelServices extends ModelTestKernelServices<KernelServices> {

    /**
     * Applies the master domain model to the slave controller with the given model version
     *
     * @param modelVersion the model version of the legacy controller
     * @param ignoredResources resources ignored on the legacy controller
     * @throws IllegalStateException if we are not the main controller
     * @throws org.jboss.as.controller.OperationFailedException if something went wrong applying the master domain model
     */
    void applyMasterDomainModel(ModelVersion modelVersion, List<IgnoreDomainResourceTypeResource> ignoredResources);
}
