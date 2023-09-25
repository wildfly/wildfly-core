/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.controller.resources;

import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.version.ProductConfig;
import org.jboss.as.version.Version;
import org.jboss.dmr.ModelNode;

/**
 * Initializes the part of the model where the versions are stored
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class VersionModelInitializer {
    public static void registerRootResource(Resource rootResource, ProductConfig cfg) {
        ModelNode model = rootResource.getModel();
        model.get(ModelDescriptionConstants.MANAGEMENT_MAJOR_VERSION).set(Version.MANAGEMENT_MAJOR_VERSION);
        model.get(ModelDescriptionConstants.MANAGEMENT_MINOR_VERSION).set(Version.MANAGEMENT_MINOR_VERSION);
        model.get(ModelDescriptionConstants.MANAGEMENT_MICRO_VERSION).set(Version.MANAGEMENT_MICRO_VERSION);

        model.get(ModelDescriptionConstants.RELEASE_VERSION).set(Version.AS_VERSION);
        model.get(ModelDescriptionConstants.RELEASE_CODENAME).set(Version.AS_RELEASE_CODENAME);

        if (cfg != null) {
            if (cfg.getProductVersion() != null) {
                model.get(ModelDescriptionConstants.PRODUCT_VERSION).set(cfg.getProductVersion());
            }
            if (cfg.getProductName() != null) {
                model.get(ModelDescriptionConstants.PRODUCT_NAME).set(cfg.getProductName());
            }
        }
    }
}
