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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    private static volatile ValidateModelStepHandler INSTANCE;

    private final OperationStepHandler extraValidationStepHandler;

    private ValidateModelStepHandler(OperationStepHandler extraValidationStepHandler) {
        this.extraValidationStepHandler = extraValidationStepHandler;
    }

    static ValidateModelStepHandler getInstance(OperationStepHandler extraValidationStepHandler) {
        if (INSTANCE == null) {
            synchronized (ValidateModelStepHandler.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ValidateModelStepHandler(extraValidationStepHandler);
                }
            }
        }
        return INSTANCE;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final Resource resource = loadResource(context);
        if (resource == null) {
            return;
        }

        if (extraValidationStepHandler != null) {
            context.addStep(operation, extraValidationStepHandler, Stage.MODEL);
        }

        final ModelNode model = resource.getModel();
        final ImmutableManagementResourceRegistration resourceRegistration = context.getResourceRegistration();
        final Map<String, AttributeAccess> attributes = resourceRegistration.getAttributes(PathAddress.EMPTY_ADDRESS);
        for (final Map.Entry<String, AttributeAccess> entry : attributes.entrySet()) {
            String attributeName = entry.getKey();
            final boolean has = model.hasDefined(attributeName);
            final AttributeAccess access = entry.getValue();
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

            String[] requires = attr.getRequires();
            if (requires != null) {
                for (final String required : requires) {
                    if (!model.hasDefined(required)) {
                        // Check for alternatives that are in the same set of 'requires'
                        AttributeDefinition requiredAttr = getAttributeDefinition(required, attributes);
                        if (requiredAttr == null) {
                            // Coding mistake in the attr AD. Don't mess up the user; just debug log
                            ControllerLogger.MGMT_OP_LOGGER.debugf("AttributeDefinition for %s required by %s is null",
                                    required, attributeName);
                        } else if (!hasAlternative(getRelevantAlteratives(requiredAttr.getAlternatives(), requires), model)) {
                            attemptReadMissingAttributeValueFromHandler(context, access, attributeName, new ErrorHandler() {
                                @Override
                                public void throwError() throws OperationFailedException {
                                    throw ControllerLogger.ROOT_LOGGER.requiredAttributeNotSet(required, attr.getName());
                                }
                            });
                        }
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

            handleObjectAttributes(model, attr, attributeName);

        }
        context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
    }

    private static AttributeDefinition getAttributeDefinition(String name, final Map<String, AttributeAccess> attributes) {
        AttributeAccess aa = attributes.get(name);
        return aa == null ? null : aa.getAttributeDefinition();
    }

    private static AttributeDefinition getAttributeDefinition(String name, AttributeDefinition[] attrs) {
        for (AttributeDefinition peer : attrs) {
            if (name.equals(peer.getName())) {
                return peer;
            }
        }
        return null;
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

    private void handleObjectAttributes(ModelNode model, AttributeDefinition attr, String absoluteParentName) throws OperationFailedException {
        if (attr instanceof ObjectTypeAttributeDefinition) {
            validateNestedAttributes(model.get(attr.getName()), (ObjectTypeAttributeDefinition) attr, absoluteParentName);
        } else if (attr instanceof ObjectListAttributeDefinition) {
            ObjectTypeAttributeDefinition valueType = ((ObjectListAttributeDefinition) attr).getValueType();
            ModelNode list = model.get(attr.getName());
            for (int i = 0; i < list.asInt(); i++) {
                validateNestedAttributes(list.get(i), valueType, absoluteParentName + "[" + i + "]");
            }
        } else if (attr instanceof ObjectMapAttributeDefinition) {
            ObjectTypeAttributeDefinition valueType = ((ObjectMapAttributeDefinition) attr).getValueType();
            ModelNode map = model.get(attr.getName());
            for (String key : map.keys()) {
                validateNestedAttributes(map.get(key), valueType, absoluteParentName + "." + key);
            }
        }
    }

    private void validateNestedAttributes(ModelNode subModel, ObjectTypeAttributeDefinition attr, String absoluteParentName) throws OperationFailedException {
        if (!subModel.isDefined()) {
            return;
        }
        AttributeDefinition[] subAttrs = attr.getValueTypes();
        for (AttributeDefinition subAttr : subAttrs) {
            String subAttributeName = subAttr.getName();
            String absoluteName = absoluteParentName + "." + subAttributeName;
            if (!subModel.hasDefined(subAttributeName) && isRequired(subAttr, subModel)) {
                throw new OperationFailedException(ControllerLogger.MGMT_OP_LOGGER.required(subAttributeName));
            }
            if (!subModel.hasDefined(subAttributeName)) {
                continue;
            }
            String[] requires = subAttr.getRequires();
            if (requires != null) {
                for (final String required : requires) {
                    if (!subModel.hasDefined(required)) {
                        // Check for alternatives that are in the same set of 'requires'
                        AttributeDefinition requiredAttr = getAttributeDefinition(required, subAttrs);
                        if (requiredAttr == null) {
                            // Coding mistake in the subAttr AD. Don't mess up the user; just debug log
                            ControllerLogger.MGMT_OP_LOGGER.debugf("AttributeDefinition for %s required by %s of %s is null",
                                    required, subAttributeName, absoluteParentName);
                        } else if (!hasAlternative(getRelevantAlteratives(requiredAttr.getAlternatives(), requires), subModel)) {
                            throw ControllerLogger.MGMT_OP_LOGGER.requiredAttributeNotSet(absoluteParentName + "." + required, absoluteName);
                        }
                    }
                }
            }

            if (!isAllowed(subAttr, subModel)) {
                String[] alts = subAttr.getAlternatives();
                StringBuilder sb = null;
                if (alts != null) {
                    for (String alt : alts) {
                        if (subModel.hasDefined(alt)) {
                            if (sb == null) {
                                sb = new StringBuilder();
                            } else {
                                sb.append(", ");
                            }
                            sb.append(absoluteParentName + "." + alt);
                        }
                    }
                }
                throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.invalidAttributeCombo(absoluteName, sb));
            }

            handleObjectAttributes(subModel, subAttr, absoluteName);
        }
    }

    private boolean isRequired(final AttributeDefinition def, final ModelNode model) {
        return def.isRequired() && !def.isResourceOnly() && !hasAlternative(def.getAlternatives(), model);
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

    private String[] getRelevantAlteratives(String[] alternatives, String[] relevant) {
        if (alternatives == null || relevant == null || relevant.length == 0) {
            return null;
        }
        List<String> result = new ArrayList<>(alternatives.length);
        for (String alt : alternatives) {
            for (String rel : relevant) {
                if (alt.equals(rel)) {
                    result.add(alt);
                    break;
                }
            }
        }
        return result.size() == 0 ? null : result.toArray(new String[result.size()]);
    }

    private Resource loadResource(OperationContext context) {
        final PathAddress address = context.getCurrentAddress();
        PathAddress current = PathAddress.EMPTY_ADDRESS;
        final ImmutableManagementResourceRegistration mrr = context.getRootResourceRegistration();
        Resource resource = context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS, false);
        for (PathElement element : address) {
            if (resource.isRuntime()){
                return null;
            }
            current = current.append(element);
            final ImmutableManagementResourceRegistration subMrr = mrr.getSubModel(current);
            if (subMrr == null || subMrr.isRuntimeOnly() || subMrr.isRemote()) {
                return null;
            }
            if (!resource.hasChild(element)) {
                return null;
            }
            resource = context.readResourceFromRoot(current, false);
        }
        if (resource.isRuntime()) {
            return null;
        }
        return resource;
    }

    private interface ErrorHandler {
        void throwError() throws OperationFailedException;
    }
}

