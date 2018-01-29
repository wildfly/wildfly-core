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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
final class ValidateModelStepHandler implements OperationStepHandler {

    static final String INTERNAL_MODEL_VALIDATION_NAME = "internal-model-validation";

    private final ManagementModel managementModel;
    private final Set<PathAddress> toValidate;
    private final OperationStepHandler extraValidationStepHandler;

    ValidateModelStepHandler(final ManagementModel managementModel,
                             final Set<PathAddress> toValidate,
                             final OperationStepHandler extraValidationStepHandler) {
        this.managementModel = managementModel;
        this.toValidate = new HashSet<>(toValidate);
        this.extraValidationStepHandler = extraValidationStepHandler;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final Map<PathAddress, ResAndReg> resolved = new HashMap<>();
        for (PathAddress pa : toValidate) {
            validateAddress(context, pa, resolved);
        }
    }

    private void validateAddress(final OperationContext context,
                                 final PathAddress address,
                                 final Map<PathAddress, ResAndReg> resolved) throws OperationFailedException {
        final ResAndReg resAndReg = loadResource(address, resolved);
        if (resAndReg == null || resAndReg.resource == null) {
            return;
        }

        if (extraValidationStepHandler != null) {
            ModelNode op = Util.createOperation(ValidateModelStepHandler.INTERNAL_MODEL_VALIDATION_NAME, address);
            context.addStep(op, extraValidationStepHandler, Stage.MODEL);
        }

        final ModelNode model = resAndReg.resource.getModel();
        final Set<String> definedKeys;
        if (model.isDefined()) {
            final Set<String> keys = model.keys();
            definedKeys = new HashSet<>(keys.size());
            for (String key : keys) {
                if (model.hasDefined(key)) {
                    definedKeys.add(key);
                }
            }
        } else {
            definedKeys = Collections.emptySet();
        }

        final Map<String, AttributeAccess> attributes = resAndReg.reg.getAttributes(PathAddress.EMPTY_ADDRESS);
        for (final Map.Entry<String, AttributeAccess> entry : attributes.entrySet()) {
            final AttributeAccess access = entry.getValue();
            if (access.getStorageType() != AttributeAccess.Storage.CONFIGURATION){
                continue;
            }
            String attributeName = entry.getKey();
            final AttributeDefinition attr = access.getAttributeDefinition();
            if (!definedKeys.contains(attributeName)) {
                if (isRequired(attr, definedKeys)) {
                    attemptReadMissingAttributeValueFromHandler(context, address, access, attributeName,
                        new ErrorHandler() {
                            @Override
                            public void throwError() throws OperationFailedException {
                                    String[] alternatives = attr.getAlternatives();
                                    if (alternatives == null) {
                                        throw ControllerLogger.ROOT_LOGGER.required(attributeName);
                                    } else {
                                        Set<String> requiredAlternatives = new HashSet<>();
                                        for (int i = 0; i < alternatives.length; i++) {
                                            AttributeDefinition requiredAttr = getAttributeDefinition(alternatives[i], attributes);
                                            if (requiredAttr != null && requiredAttr.isRequired() && !requiredAttr.isResourceOnly()) {
                                                requiredAlternatives.add(alternatives[i]);
                                            }
                                        }
                                        throw ControllerLogger.ROOT_LOGGER.requiredWithAlternatives(attributeName, requiredAlternatives);
                                    }
                        }}
                    );
                }
                // no error means undefined is ok and there's nothing more to check for this one
                continue;
            }

            String[] requires = attr.getRequires();
            if (requires != null) {
                for (final String required : requires) {
                    if (!definedKeys.contains(required)) {
                        // Check for alternatives that are in the same set of 'requires'
                        AttributeDefinition requiredAttr = getAttributeDefinition(required, attributes);
                        if (requiredAttr == null) {
                            // Coding mistake in the attr AD. Don't mess up the user; just debug log
                            ControllerLogger.ROOT_LOGGER.debugf("AttributeDefinition for %s required by %s is null",
                                    required, attributeName);
                        } else if (!hasAlternative(getRelevantAlteratives(requiredAttr.getAlternatives(), requires), definedKeys)) {
                            attemptReadMissingAttributeValueFromHandler(context, address, access, attributeName, new ErrorHandler() {
                                @Override
                                public void throwError() throws OperationFailedException {
                                    throw ControllerLogger.ROOT_LOGGER.requiredAttributeNotSet(required, attr.getName());
                                }
                            });
                        }
                    }
                }
            }

            if (!isAllowed(attr, definedKeys)) {
                //TODO should really use attemptReadMissingAttributeValueFromHandler() to make this totally good, but the
                //overhead might be bigger than is worth at the moment since we would have to invoke the extra steps for
                //every single attribute not found (and not found should be the normal).
                String[] alts = attr.getAlternatives();
                StringBuilder sb = null;
                if (alts != null) {
                    for (String alt : alts) {
                        if (definedKeys.contains(alt)) {
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

    private void attemptReadMissingAttributeValueFromHandler(final OperationContext context, final PathAddress address,
            final AttributeAccess attributeAccess, final String attributeName, final ErrorHandler errorHandler) throws OperationFailedException {
        OperationStepHandler handler = attributeAccess.getReadHandler();
        if (handler == null) {
            errorHandler.throwError();
        } else {
            final ModelNode readAttr = Util.getReadAttributeOperation(address, attributeName);

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

    private void validateNestedAttributes(final ModelNode subModel, final ObjectTypeAttributeDefinition attr,
                                          final String absoluteParentName) throws OperationFailedException {
        if (!subModel.isDefined()) {
            return;
        }

        final Set<String> keys = subModel.keys();
        final Set<String> definedKeys = new HashSet<>(keys.size());
        for (String key : keys) {
            if (subModel.hasDefined(key)) {
                definedKeys.add(key);
            }
        }
        AttributeDefinition[] subAttrs = attr.getValueTypes();
        for (AttributeDefinition subAttr : subAttrs) {
            String subAttributeName = subAttr.getName();
            String absoluteName = absoluteParentName + "." + subAttributeName;
            if (!definedKeys.contains(subAttributeName)) {
                if (isRequired(subAttr, definedKeys)) {
                    String[] alternatives = attr.getAlternatives();
                    if (alternatives == null) {
                        throw ControllerLogger.ROOT_LOGGER.required(subAttributeName);
                    } else {
                        Set<String> requiredAlternatives = new HashSet<>();
                        for (int i = 0; i < alternatives.length; i++) {
                            AttributeDefinition requiredAttr = getAttributeDefinition(alternatives[i], subAttrs);
                            if (requiredAttr != null && requiredAttr.isRequired() && !requiredAttr.isResourceOnly()) {
                                requiredAlternatives.add(alternatives[i]);
                            }
                        }
                        throw ControllerLogger.ROOT_LOGGER.requiredWithAlternatives(subAttributeName, requiredAlternatives);
                    }
                }
                // else undefined is ok and there's nothing more to check for this one
                continue;
            }
            String[] requires = subAttr.getRequires();
            if (requires != null) {
                for (final String required : requires) {
                    if (!definedKeys.contains(required)) {
                        // Check for alternatives that are in the same set of 'requires'
                        AttributeDefinition requiredAttr = getAttributeDefinition(required, subAttrs);
                        if (requiredAttr == null) {
                            // Coding mistake in the subAttr AD. Don't mess up the user; just debug log
                            ControllerLogger.ROOT_LOGGER.debugf("AttributeDefinition for %s required by %s of %s is null",
                                    required, subAttributeName, absoluteParentName);
                        } else if (!hasAlternative(getRelevantAlteratives(requiredAttr.getAlternatives(), requires), definedKeys)) {
                            throw ControllerLogger.ROOT_LOGGER.requiredAttributeNotSet(absoluteParentName + "." + required, absoluteName);
                        }
                    }
                }
            }

            if (!isAllowed(subAttr, definedKeys)) {
                String[] alts = subAttr.getAlternatives();
                StringBuilder sb = null;
                if (alts != null) {
                    for (String alt : alts) {
                        if (definedKeys.contains(alt)) {
                            if (sb == null) {
                                sb = new StringBuilder();
                            } else {
                                sb.append(", ");
                            }
                            sb.append(absoluteParentName).append(".").append(alt);
                        }
                    }
                }
                throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.invalidAttributeCombo(absoluteName, sb));
            }

            handleObjectAttributes(subModel, subAttr, absoluteName);
        }
    }

    private boolean isRequired(final AttributeDefinition def, final Set<String> definedKeys) {
        return def.isRequired() && !def.isResourceOnly() && !hasAlternative(def.getAlternatives(), definedKeys);
    }

    private boolean isAllowed(final AttributeDefinition def, final Set<String> definedKeys) {
        final String[] alternatives = def.getAlternatives();
        if (alternatives != null) {
            for (final String alternative : alternatives) {
                if (definedKeys.contains(alternative)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean hasAlternative(final String[] alternatives, Set<String> definedKeys) {
        if (alternatives != null) {
            for (final String alternative : alternatives) {
                if (definedKeys.contains(alternative)) {
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

    private ResAndReg loadResource(final PathAddress address,
                                   final Map<PathAddress, ResAndReg> resolved) {
        ResAndReg resAndReg = resolved.get(PathAddress.EMPTY_ADDRESS);
        if (resAndReg == null) {
            final ImmutableManagementResourceRegistration mrr = managementModel.getRootResourceRegistration();
            Resource resource = managementModel.getRootResource();
            resAndReg = new ResAndReg(resource, mrr);
            resolved.put(PathAddress.EMPTY_ADDRESS, resAndReg);
        }
        PathAddress current = PathAddress.EMPTY_ADDRESS;
        Resource resource = resAndReg.resource;
        ImmutableManagementResourceRegistration mrr = resAndReg.reg;
        for (PathElement element : address) {
            if (resource == null || resource.isRuntime()){
                return null;
            }
            current = current.append(element);
            resAndReg = resolved.get(current);
            final ImmutableManagementResourceRegistration subMrr;
            if (resAndReg != null) {
                subMrr = resAndReg.reg;
            } else {
                subMrr = mrr.getSubModel(current);
            }
            if (subMrr == null || subMrr.isRuntimeOnly() || subMrr.isRemote() || !resource.hasChild(element)) {
                if (resAndReg == null) {
                    // Save the MRR
                    resAndReg = new ResAndReg(null, subMrr);
                    resolved.put(current, resAndReg);
                }
                return null;
            }
            if (resAndReg != null) {
                resource = resAndReg.resource;
            } else {
                resource =  resource.getChild(element);
                resAndReg = new ResAndReg(resource, subMrr);
                resolved.put(current, resAndReg);
            }
        }
        if (resource.isRuntime()) {
            return null;
        }
        return resAndReg;
    }

    private static class ResAndReg {
        private final Resource resource;
        private final ImmutableManagementResourceRegistration reg;

        private ResAndReg(Resource resource, ImmutableManagementResourceRegistration reg) {
            this.resource = resource;
            this.reg = reg;
        }
    }

    private interface ErrorHandler {
        void throwError() throws OperationFailedException;
    }
}

