/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test;

import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.registry.Resource;

/**
 * Fast track to initialize model with resources required by the operations required.
 * When testing the xml variety things added here might be added back to the marshalled
 * xml, causing problems when comparing the original and marshalled xml. Use {@link ModelWriteSanitizer}
 * to remove things added here from the model.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
@FunctionalInterface
public interface ModelInitializer {

    default void populateModel(ManagementModel managementModel) {
        populateModel(managementModel.getRootResource());
    }

    void populateModel(Resource rootResource);

    /** An initializer that does nothing. */
    ModelInitializer NO_OP = new ModelInitializer() { // the lambda for this just looked too funky for my tastes
        @Override
        public void populateModel(Resource rootResource) {
            // no-op
        }
    };

}
