/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.instmgr;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.operations.validation.ParameterValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.core.instmgr.logging.InstMgrLogger;
import org.wildfly.installationmanager.spi.InstallationManagerFactory;

/**
 * Base class for Custom Patches operation handlers
 */
abstract class InstMgrCustomPatchHandler extends InstMgrOperationStepHandler {

    protected static final AttributeDefinition MANIFEST_GA = new SimpleAttributeDefinitionBuilder(InstMgrConstants.MANIFEST, ModelType.STRING)
            .setStorageRuntime()
            .setRequired(true)
            .setValidator(new ManifestValidator())
            .build();

    public InstMgrCustomPatchHandler(InstMgrService imService, InstallationManagerFactory imf) {
        super(imService, imf);
    }

    protected static class ManifestValidator implements ParameterValidator {

        @Override
        public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
            final String manifestGA = value.asStringOrNull();

            if (manifestGA != null) {
                if (manifestGA.contains("\\") || manifestGA.contains("/")) {
                    throw InstMgrLogger.ROOT_LOGGER.invalidManifestGAV(manifestGA);
                }
                String[] parts = manifestGA.split(":");
                for (String part : parts) {
                    if (part == null || "".equals(part)) {
                        throw InstMgrLogger.ROOT_LOGGER.invalidManifestGAV(manifestGA);
                    }
                }
                if (parts.length != 2) {
                    throw InstMgrLogger.ROOT_LOGGER.invalidManifestGAOnly(manifestGA);
                }
            }
        }
    }
}
