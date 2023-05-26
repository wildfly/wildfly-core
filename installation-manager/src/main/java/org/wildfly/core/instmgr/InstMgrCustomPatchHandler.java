/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
