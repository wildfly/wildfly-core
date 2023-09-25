/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test.util;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT_OVERLAY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.core.model.test.LegacyKernelServicesInitializer;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ExcludeCommonOperations {

    private ExcludeCommonOperations() {
    }

    public static void excludeBadOps_7_1_x(LegacyKernelServicesInitializer initializer) {
        //deployment overlays don't exist in 7.1.x
        initializer.addOperationValidationExclude(ADD, PathAddress.pathAddress(PathElement.pathElement(DEPLOYMENT_OVERLAY)));

        initializer.addOperationValidationExclude(ADD, PathAddress.pathAddress(PathElement.pathElement(DEPLOYMENT_OVERLAY), PathElement.pathElement(CONTENT)));
        initializer.addOperationValidationExclude(ADD, PathAddress.pathAddress(PathElement.pathElement(SERVER_GROUP), PathElement.pathElement(DEPLOYMENT_OVERLAY)));

        //Socket binding group/socket-binding has problems if there are expressions in the multicast-port
        initializer.addOperationValidationResolve(ADD, PathAddress.pathAddress(PathElement.pathElement(SOCKET_BINDING_GROUP), PathElement.pathElement(SOCKET_BINDING)));

        //Deployment operation validator thinks that content is required
        initializer.addOperationValidationExclude(ADD, PathAddress.pathAddress(PathElement.pathElement(SERVER_GROUP), PathElement.pathElement(DEPLOYMENT)));
    }
}
