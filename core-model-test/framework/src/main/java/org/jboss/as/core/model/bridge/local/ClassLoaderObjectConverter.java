/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.bridge.local;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.core.model.test.LegacyModelInitializerEntry;
import org.jboss.as.host.controller.ignored.IgnoreDomainResourceTypeResource;
import org.jboss.as.model.test.ModelTestOperationValidatorFilter;
import org.jboss.dmr.ModelNode;

/**
 * This interface will only be loaded up by the app classloader.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public interface ClassLoaderObjectConverter {
    Object convertModelNodeToChildCl(ModelNode object);
    ModelNode convertModelNodeFromChildCl(Object object);
    Object convertModelVersionToChildCl(ModelVersion modelVersion);
    Object convertLegacyModelInitializerEntryToChildCl(LegacyModelInitializerEntry initializer);
    Object convertIgnoreDomainTypeResourceToChildCl(IgnoreDomainResourceTypeResource resource);
    Object convertValidateOperationsFilterToChildCl(ModelTestOperationValidatorFilter validateOpsFilter);
}
