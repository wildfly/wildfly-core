/*
 * Copyright 2021 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.controller.operations.common;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.logging.ControllerLogger.MGMT_OP_LOGGER;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.AuthorizationResult;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.persistence.ConfigurationPersistenceException;
import org.jboss.as.controller.persistence.ConfigurationPersister;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author Emmanuel Hugonnet (c) 2021 Red Hat, Inc.
 */
public abstract class AbstractXmlMarshallingHandler implements OperationStepHandler {

    private static final Set<Action.ActionEffect> EFFECTS
            = Collections.unmodifiableSet(EnumSet.of(Action.ActionEffect.ADDRESS, Action.ActionEffect.READ_CONFIG));

    private final ConfigurationPersister configPersister;

    protected AbstractXmlMarshallingHandler(final ConfigurationPersister configPersister) {
        this.configPersister = configPersister;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) {
        final PathAddress pa = context.getCurrentAddress();

        AuthorizationResult authResult = context.authorize(operation, EFFECTS);
        if (authResult.getDecision() != AuthorizationResult.Decision.PERMIT) {
            throw ControllerLogger.ROOT_LOGGER.unauthorized(operation.require(OP).asString(), pa, authResult.getExplanation());
        }

        final Resource resource = context.readResourceFromRoot(getBaseAddress());
        // Get the model recursively
        final ModelNode model = Resource.Tools.readModel(resource);
        try {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    BufferedOutputStream output = new BufferedOutputStream(baos)) {
                configPersister.marshallAsXml(model, output);
                attachResult(context, baos);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (IOException | ConfigurationPersistenceException e) {
            // Log this
            MGMT_OP_LOGGER.failedExecutingOperation(e, operation.require(ModelDescriptionConstants.OP), pa);
            context.getFailureDescription().set(e.toString());
        }
    }

    protected abstract void attachResult(OperationContext context, ByteArrayOutputStream baos);

    protected PathAddress getBaseAddress() {
        return PathAddress.EMPTY_ADDRESS;
    }
}
