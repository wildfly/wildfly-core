/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operations.global;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NESTED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PARAMS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CONFIG_AS_FEATURES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SPEC;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.PathAddressFilter;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.AttributeAccess.AccessType;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 *
 * @author Alexey Loubyansky
 */
public class ReadConfigAsFeaturesOperationHandler implements OperationStepHandler {

    private static final String ID = "id";

    public static final SimpleOperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(READ_CONFIG_AS_FEATURES_OPERATION, ControllerResolver.getResolver(SUBSYSTEM))
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.READ_WHOLE_CONFIG)
            .setReadOnly()
            .withFlag(OperationEntry.Flag.HIDDEN)
            .setReplyType(ModelType.LIST)
            .setReplyValueType(ModelType.OBJECT)
            .addParameter(SimpleAttributeDefinitionBuilder.create(NESTED, ModelType.BOOLEAN)
                    .setRequired(false)
                    .setDefaultValue(ModelNode.TRUE)
                    .build())
            .build();

    private final String operationName;
    private final PathAddressFilter addressFilter;

    public ReadConfigAsFeaturesOperationHandler() {
        this(null);
    }

    public ReadConfigAsFeaturesOperationHandler(PathAddressFilter addressFilter) {
        this.operationName = READ_CONFIG_AS_FEATURES_OPERATION;
        this.addressFilter = addressFilter;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final PathAddress address = PathAddress.pathAddress(operation.require(ModelDescriptionConstants.OP_ADDR));
        if (addressFilter != null && !addressFilter.accepts(address)) {
            return;
        }
        final ImmutableManagementResourceRegistration registration = context.getResourceRegistration();
        if (registration.isAlias() || registration.isRemote() || registration.isRuntimeOnly()) {
            return;
        }
        final Resource resource = context.readResource(PathAddress.EMPTY_ADDRESS, false);
        if (resource.isProxy() || resource.isRuntime()) {
            return;
        }

        final ModelNode result = context.getResult();
        result.setEmptyList();
        final ModelNode results = new ModelNode().setEmptyList();
        final AtomicReference<ModelNode> failureRef = new AtomicReference<>();

        final boolean nest = operation.hasDefined(NESTED) ? operation.get(NESTED).asBoolean() : true;
        final ModelNode feature = registration.isFeature() ? readAsFeature(resource, registration, operation, address, context, results, nest) : null;

        // Step to handle failed operations
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                boolean failed = false;
                if (failureRef.get() != null) {
                    // One of our subsystems failed
                    context.getFailureDescription().set(failureRef.get());
                    failed = true;
                }
                if (!failed) {
                    final List<ModelNode> children = results.asList();
                    if (feature == null) {
                        for (ModelNode child : children) {
                            result.add(child);
                        }
                    } else if (nest) {
                        if (!children.isEmpty()) {
                            final ModelNode nested = feature.get(ModelDescriptionConstants.CHILDREN).setEmptyList();
                            for (final ModelNode childRsp : children) {
                                nested.add(childRsp);
                            }
                        }
                        result.add(feature);
                    } else {
                        result.add(feature);
                        if (!children.isEmpty()) {
                            for (final ModelNode childRsp : children) {
                                result.add(childRsp);
                            }
                        }
                    }
                    context.getResult().set(result);
                }
            }
        }, OperationContext.Stage.MODEL, true);

        describeChildren(resource, registration, address, context, failureRef, results, operation, nest);
    }

    private void describeChildren(final Resource resource, final ImmutableManagementResourceRegistration registration,
            final PathAddress address, OperationContext context,
            final AtomicReference<ModelNode> failureRef, final ModelNode results, ModelNode operation, Boolean nest) {
        for(String childType : resource.getChildTypes()) {
            for(Resource.ResourceEntry entry : resource.getChildren(childType)) {
                if(addressFilter != null && !addressFilter.accepts(address.append(entry.getPathElement()))) {
                    continue;
                }
                final ImmutableManagementResourceRegistration childRegistration = registration.getSubModel(PathAddress.EMPTY_ADDRESS.append(entry.getPathElement()));
                if(childRegistration == null) {
                    ControllerLogger.ROOT_LOGGER.debugf("Couldn't find a registration for %s at %s for resource %s at %s", entry.getPathElement().toString(), registration.getPathAddress().toCLIStyleString(), resource, address.toCLIStyleString());
                    continue;
                }
                if(childRegistration.isRuntimeOnly() || childRegistration.isRemote() || childRegistration.isAlias()) {
                    continue;
                }
                describeChildResource(entry, registration, address, context, failureRef, results, operation, nest);
            }
        }
    }

    private ModelNode readAsFeature(final Resource resource,
            final ImmutableManagementResourceRegistration registration, ModelNode operation, final PathAddress address,
            OperationContext context, ModelNode results, boolean nest) throws OperationFailedException {
            ImmutableManagementResourceRegistration registry = context.getRootResourceRegistration().getSubModel(address);

            final ModelNode featureNode = new ModelNode();
            featureNode.get(SPEC).set(registry.getFeature());

            final ModelNode idParams = featureNode.get(ID);
            final Set<String> idParamNames;
            final int addrSize = address.size();
            if (addrSize > 0) {
                idParamNames = new HashSet<>(addrSize);
                if(addrSize > 1) {
                    int i = 0;
                    while (i < addrSize - 1) {
                        final PathElement elt = address.getElement(i++);
                        final String paramName = elt.getKey();
                        idParamNames.add(paramName);
                        if(!nest) {
                            idParams.get(paramName).set(elt.getValue());
                        }
                    }
                }
                final PathElement lastElt = address.getLastElement();
                final String paramName = lastElt.getKey();
                idParams.get(paramName).set(lastElt.getValue());
                idParamNames.add(paramName);
            } else {
                idParamNames = Collections.emptySet();
            }

            final DescriptionProvider addDescr = registration.getOperationDescription(PathAddress.EMPTY_ADDRESS, ModelDescriptionConstants.ADD);
            final ModelNode addProps = addDescr == null ? null : addDescr.getModelDescription(null).get(ModelDescriptionConstants.REQUEST_PROPERTIES);

            List<ModelNode> children = Collections.emptyList();
            ModelNode params = null;
            final ModelNode model = resource.getModel();
            try(Stream<String> attrs = registration.getAttributeNames(PathAddress.EMPTY_ADDRESS).stream()) {
                final Iterator<String> i = attrs.iterator();
                while(i.hasNext()) {
                    final String attrName = i.next();
                    if(!model.hasDefined(attrName)) {
                        continue;
                    }
                    final AttributeAccess attrAccess = registration.getAttributeAccess(PathAddress.EMPTY_ADDRESS, attrName);
                    if(attrAccess.getStorageType() != AttributeAccess.Storage.CONFIGURATION ||
                            !(attrAccess.getAccessType().equals(AccessType.READ_WRITE) ||
                            addProps != null && addProps.has(attrName))) {
                        continue;
                    }
                    final AttributeDefinition attrDef = attrAccess.getAttributeDefinition();
                    if (!attrDef.isRequired() || !(addProps != null && addProps.hasDefined(attrName))) {
                        final ModelType attrType = attrDef.getType();
                        if (attrType.equals(ModelType.OBJECT)
                                && ObjectTypeAttributeDefinition.class.isAssignableFrom(attrDef.getClass())) {
                            final ModelNode child = readObjectAttributeAsFeature(registration, (ObjectTypeAttributeDefinition) attrDef,
                                    idParams, idParamNames, model.get(attrName), nest);
                            if (children.isEmpty()) {
                                children = Collections.singletonList(child);
                                continue;
                            }
                            if (children.size() == 1) {
                                final List<ModelNode> tmp = children;
                                children = new ArrayList<>(2);
                                children.add(tmp.get(0));
                            }
                            children.add(child);
                            continue;
                        }
                        /* NOTE: the commented out code allows to split the list as an atomic value into a list of item features
                         * the reason it's commented out is that currently list items have no IDs and so can't be compared and merged
                        if(attrType.equals(ModelType.LIST) && ObjectListAttributeDefinition.class.isAssignableFrom(attrAccess.getAttributeDefinition().getClass())) {
                            final List<ModelNode> features = getListAttributeFeature(registration,
                                (ObjectListAttributeDefinition) attrAccess.getAttributeDefinition(), idParams, idParamNames,
                                model.get(attrName).asList());
                            if(children.isEmpty()) {
                                children = features;
                                continue;
                            }
                            children.addAll(features);
                             continue;
                         }
                         */
                    }
                    String paramName = attrName;
                    if (idParamNames.contains(attrName) || ((PROFILE.equals(attrName) || HOST.equals(attrName)) && isSubsystem(address))) {
                        paramName = attrName + "-feature";
                    }
                    if(params == null) {
                        params = featureNode.get(PARAMS);
                    }
                    params.get(paramName).set(model.get(attrName));
                }
            }

            if(!children.isEmpty()) {
                for(ModelNode child : children) {
                    results.add(child);
                }
            }
            return featureNode;
    }

    private ModelNode readObjectAttributeAsFeature(final ImmutableManagementResourceRegistration registration, ObjectTypeAttributeDefinition attrDef,
            ModelNode idParams, Set<String> idParamNames, ModelNode objectValue, boolean nest) {
        final ModelNode featureNode = new ModelNode();
        featureNode.get(SPEC).set(registration.getFeature() + '.' + attrDef.getName());

        if(!nest) {
            featureNode.get(ID).set(idParams.clone());
        }

        final ModelNode params = featureNode.get(PARAMS);
        final AttributeDefinition[] attrs = attrDef.getValueTypes();
        for(AttributeDefinition attr : attrs) {
            String attrName = attr.getName();
            if(!objectValue.hasDefined(attrName)) {
                continue;
            }
            final ModelNode attrValue = objectValue.get(attrName);
            if(idParamNames.contains(attrName)) {
                attrName += "-feature";
            }
            params.get(attrName).set(attrValue);
        }
        return featureNode;
    }

/* This method breaks a list of objects into a list of features.
 * But given that the result of this method is used for generating config diffs and we can't establish an identity for the list items,
 * this method is not used and complex lists are handled as atomic attribute values.
    private List<ModelNode> getListAttributeFeature(final ImmutableManagementResourceRegistration registration, ObjectListAttributeDefinition attrDef,
            ModelNode idParams, Set<String> idParamNames, List<ModelNode> list) {
        final ObjectTypeAttributeDefinition itemType = attrDef.getValueType();
        final AttributeDefinition[] attrs = itemType.getValueTypes();

        List<ModelNode> features = new ArrayList<>(list.size());
        for(ModelNode item : list) {
            final ModelNode featureNode = new ModelNode();
            featureNode.get("spec").set(registration.getFeature() + '.' + attrDef.getName());

            final ModelNode params = featureNode.get(PARAMS);
            for(Property param : idParams.asPropertyList()) {
                params.get(param.getName()).set(param.getValue());
            }

            for(AttributeDefinition attr : attrs) {
                String attrName = attr.getName();
                if(!item.hasDefined(attrName)) {
                    continue;
                }
                final ModelNode attrValue = item.get(attrName);
                if(idParamNames.contains(attrName)) {
                    attrName += "-feature";
                }
                params.get(attrName).set(attrValue);
            }
            features.add(featureNode);
        }
        return features;
    }
*/
    private static boolean isSubsystem(PathAddress address) {
        for(PathElement elt : address) {
            if(SUBSYSTEM.equals(elt.getKey())) {
                return true;
            }
        }
        return false;
    }

    private void describeChildResource(final Resource.ResourceEntry entry,
            final ImmutableManagementResourceRegistration registration, final PathAddress address,
            OperationContext context, final AtomicReference<ModelNode> failureRef,
            final ModelNode results, ModelNode operation, Boolean nest) {
        final ModelNode childRsp = new ModelNode();
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                if (failureRef.get() == null) {
                    if (childRsp.hasDefined(FAILURE_DESCRIPTION)) {
                        failureRef.set(childRsp.get(FAILURE_DESCRIPTION));
                    } else if (childRsp.hasDefined(RESULT)) {
                        addChildOperation(address, childRsp.require(RESULT).asList(), results);
                    }
                }
            }
        }, OperationContext.Stage.MODEL, true);
        final ModelNode childOperation = operation.clone();
        childOperation.get(ModelDescriptionConstants.OP).set(operationName);
        if(nest != null) {
            childOperation.get(NESTED).set(nest);
        }
        final PathElement childPE = entry.getPathElement();
        childOperation.get(ModelDescriptionConstants.OP_ADDR).set(address.append(childPE).toModelNode());
        final ImmutableManagementResourceRegistration childRegistration = registration.getSubModel(PathAddress.EMPTY_ADDRESS.append(childPE));
        final OperationStepHandler stepHandler = childRegistration.getOperationHandler(PathAddress.EMPTY_ADDRESS, operationName);
        context.addStep(childRsp, childOperation, stepHandler, OperationContext.Stage.MODEL, true);
    }

    protected void addChildOperation(final PathAddress parent, final List<ModelNode> operations, ModelNode results) {
        for (final ModelNode operation : operations) {
            results.add(operation);
        }
    }
}
