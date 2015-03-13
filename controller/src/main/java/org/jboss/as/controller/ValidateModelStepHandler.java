/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014, JBoss Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.controller;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;

import java.util.Set;

import org.jboss.as.controller.OperationContext.Stage;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 */
class ValidateModelStepHandler implements OperationStepHandler {
    static final ValidateModelStepHandler INSTANCE = new ValidateModelStepHandler();

    private ValidateModelStepHandler() {

    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final Resource resource = loadResource(context);
        if (resource == null) {
            return;
        }

        final ModelNode model = resource.getModel();
        final ImmutableManagementResourceRegistration resourceRegistration = context.getResourceRegistration();
        final Set<String> attributeNames = resourceRegistration.getAttributeNames(PathAddress.EMPTY_ADDRESS);
        for (final String attributeName : attributeNames) {
            final boolean has = model.hasDefined(attributeName);
            final AttributeAccess access = context.getResourceRegistration().getAttributeAccess(PathAddress.EMPTY_ADDRESS, attributeName);
            if (access.getStorageType() != AttributeAccess.Storage.CONFIGURATION){
                continue;
            }
            final AttributeDefinition attr = access.getAttributeDefinition();
            if (!has && isRequired(attr, model)) {
                attemptReadMissingAttributeValueFromHandler(context, access, attributeName, new ErrorHandler() {
                    @Override
                    public void throwError() throws OperationFailedException {
                        throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.required(attributeName));
                    }});
            }
            if (!has) {
                continue;
            }

            if (attr.getRequires() != null) {
                for (final String required : attr.getRequires()) {
                    if (!model.hasDefined(required)) {
                        attemptReadMissingAttributeValueFromHandler(context, access, attributeName, new ErrorHandler() {
                            @Override
                            public void throwError() throws OperationFailedException {
                                throw ControllerLogger.ROOT_LOGGER.requiredAttributeNotSet(required, attr.getName());
                            }});
                    }
                }
            }

            if (!isAllowed(attr, model)) {
                //TODO should really use attemptReadMissingAttributeValueFromHandler() to make this totally good, but the
                //overhead might be bigger than is worth at the moment since we would have to invoke the extra steps for
                //every single attribute not found (and not found should be the normal).
                String[] alts = attr.getAlternatives();
                StringBuilder sb = null;
                if (alts != null) {
                    for (String alt : alts) {
                        if (model.hasDefined(alt)) {
                            if (sb == null) {
                                sb = new StringBuilder();
                            } else {
                                sb.append(", ");
                            }
                            sb.append(alt);
                        }
                    }
                }
                throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.invalidAttributeCombo(attributeName, sb));
            }
        }
        context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
    }

    private void attemptReadMissingAttributeValueFromHandler(final OperationContext context, final AttributeAccess attributeAccess,
            final String attributeName, final ErrorHandler errorHandler) throws OperationFailedException {
        OperationStepHandler handler = attributeAccess.getReadHandler();
        if (handler == null) {
            errorHandler.throwError();
        } else {
            final ModelNode readAttr = Util.getReadAttributeOperation(context.getCurrentAddress(), attributeName);

            //Do a read-attribute as an immediate step
            final ModelNode resultHolder = new ModelNode();
            context.addStep(resultHolder, readAttr, handler, Stage.MODEL, true);

            //Then check the read-attribute result in a later step and throw the error if it is not set
            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    if (!resultHolder.isDefined() && !resultHolder.hasDefined(RESULT)) {
                        errorHandler.throwError();
                    }
                }
            }, Stage.MODEL);
        }


    }

    private boolean isRequired(final AttributeDefinition def, final ModelNode model) {
        final boolean required = !def.isAllowNull() && !def.isResourceOnly();
        return required ? !hasAlternative(def.getAlternatives(), model) : required;
    }

    private boolean isAllowed(final AttributeDefinition def, final ModelNode model) {
        final String[] alternatives = def.getAlternatives();
        if (alternatives != null) {
            for (final String alternative : alternatives) {
                if (model.hasDefined(alternative)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean hasAlternative(final String[] alternatives, ModelNode operationObject) {
        if (alternatives != null) {
            for (final String alternative : alternatives) {
                if (operationObject.hasDefined(alternative)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Resource loadResource(OperationContext context) {
        final PathAddress address = context.getCurrentAddress();
        PathAddress current = PathAddress.EMPTY_ADDRESS;
        Resource resource = context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS, false);
        for (PathElement element : address) {
            if (!resource.hasChild(element)) {
                return null;
            }
            current = current.append(element);
            resource = context.readResourceFromRoot(current, false);
        }
        return resource;
    }

    private interface ErrorHandler {
        void throwError() throws OperationFailedException;
    }
}

