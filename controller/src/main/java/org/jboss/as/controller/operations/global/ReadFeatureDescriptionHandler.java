/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.controller.operations.global;

import java.util.ArrayList;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADDRESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ANNOTATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ATTRIBUTES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CAPABILITY_REFERENCE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILDREN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEFAULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXCEPTIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FEATURE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FEATURE_REFERENCE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NILLABLE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STORAGE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE;
import static org.jboss.as.controller.operations.global.GlobalOperationAttributes.RECURSIVE;
import static org.jboss.as.controller.operations.global.GlobalOperationAttributes.RECURSIVE_DEPTH;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.UnauthorizedException;
import org.jboss.as.controller.access.Action.ActionEffect;
import org.jboss.as.controller.access.AuthorizationResult;
import org.jboss.as.controller.access.AuthorizationResult.Decision;
import org.jboss.as.controller.access.ResourceAuthorization;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.AttributeAccess.Storage;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_FEATURE_DESCRIPTION_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.registry.AttributeAccess.Storage.CONFIGURATION;

import java.util.HashSet;
import java.util.Set;
import java.util.StringJoiner;
import org.jboss.as.controller.CapabilityReferenceRecorder;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.capability.registry.CapabilityId;
import org.jboss.as.controller.capability.registry.CapabilityRegistration;
import org.jboss.as.controller.capability.registry.CapabilityScope;
import org.jboss.as.controller.capability.registry.ImmutableCapabilityRegistry;
import org.jboss.as.controller.capability.registry.RegistrationPoint;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CAPABILITY_REFERENCE_PATTERN_ELEMENTS;
import org.jboss.as.controller.registry.AliasEntry;
import org.jboss.as.controller.registry.AliasStepHandler;
import org.jboss.as.controller.registry.AttributeAccess;

/**
 * Global operation handler that describes the resource as a Galleon feature.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 * @author Emmanuel Hugonnet
 * @author Alexey Loubyansky
 */
public class ReadFeatureDescriptionHandler extends GlobalOperationHandlers.AbstractMultiTargetHandler {

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(READ_FEATURE_DESCRIPTION_OPERATION, ControllerResolver.getResolver("global"))
            .setParameters(RECURSIVE, RECURSIVE_DEPTH)
            .setReadOnly()
            .withFlag(OperationEntry.Flag.HIDDEN)
            .setReplyValueType(ModelType.OBJECT)
            .build();

    private final ImmutableCapabilityRegistry capabilityRegistry;

    private static final String $PROFILE_PREFIX = "$profile.";
    private static final String ADDRESS_PARAMETERS = "addr-params";
    private static final String ADDRESS_PARAMETERS_MAPPING = "addr-params-mapping";
    private static final String DOMAIN_EXTENSION = "domain.extension";
    private static final String FEATURE_ID = "feature-id";
    private static final String HOST_EXTENSION = "host.extension";
    private static final String OPERATION_PARAMETERS = "op-params";
    private static final String OPERATION_PARAMETERS_MAPPING = "op-params-mapping";
    private static final String PARAMETERS = "params";
    private static final String REFERENCES = "refs";

    //Placeholder for NoSuchResourceExceptions coming from proxies to remove the child in ReadFeatureHandler
    private static final ModelNode PROXY_NO_SUCH_RESOURCE;

    static {
        //Create something non-used since we cannot
        ModelNode none = new ModelNode();
        none.get("no-such-resource").set("no$such$resource");
        none.protect();
        PROXY_NO_SUCH_RESOURCE = none;
    }

    public static OperationStepHandler getInstance(ImmutableCapabilityRegistry capabilityRegistry) {
        return new ReadFeatureDescriptionHandler(capabilityRegistry);
    }

    private ReadFeatureDescriptionHandler(final ImmutableCapabilityRegistry capabilityRegistry) {
        super(true);
        this.capabilityRegistry = capabilityRegistry;
    }

    private ReadFeatureAccessControlContext getAccessControlContext() {
        return null;
    }

    @Override
    void doExecute(OperationContext context, ModelNode operation, FilteredData filteredData, boolean ignoreMissingResource) throws OperationFailedException {
        final PathAddress address = context.getCurrentAddress();
        ReadFeatureAccessControlContext accessControlContext = getAccessControlContext() == null ? new ReadFeatureAccessControlContext(address, null) : getAccessControlContext();
        doExecute(context, operation, accessControlContext);
    }

    void doExecute(OperationContext context, ModelNode operation, ReadFeatureAccessControlContext accessControlContext) throws OperationFailedException {
        if (accessControlContext.parentAddresses == null) {
            doExecuteInternal(context, operation, accessControlContext);
        } else {
            try {
                doExecuteInternal(context, operation, accessControlContext);
            } catch (Resource.NoSuchResourceException | UnauthorizedException nsre) {
                context.getResult().set(new ModelNode());
            }
        }
    }

    private void doExecuteInternal(final OperationContext context, final ModelNode operation, final ReadFeatureAccessControlContext accessControlContext) throws OperationFailedException {

        for (AttributeDefinition def : DEFINITION.getParameters()) {
            def.validateOperation(operation);
        }
        final String opName = operation.require(OP).asString();
        PathAddress opAddr = PathAddress.pathAddress(operation.get(OP_ADDR));
        // WFCORE-76
        final boolean recursive = GlobalOperationHandlers.getRecursive(context, operation);

        final ImmutableManagementResourceRegistration registry = getResourceRegistrationCheckForAlias(context, opAddr);
        final PathAddress pa = registry.getPathAddress();
        final ModelNode feature = describeFeature(Locale.US, registry, CapabilityScope.Factory.create(context.getProcessType(), pa),
                isProfileScope(context.getProcessType(), pa));
        if (pa.size() == 0 && context.getProcessType().isServer()) { //server-root feature spec
            ModelNode param = new ModelNode();
            param.get(ModelDescriptionConstants.NAME).set("server-root");
            param.get(ModelDescriptionConstants.DEFAULT).set("/");
            param.get(FEATURE_ID).set(true);
            feature.require(FEATURE).get(PARAMETERS).add(param);
            feature.require(FEATURE).require(ANNOTATION).get(ADDRESS_PARAMETERS).set("server-root");
        }

        if (pa.getLastElement() != null && SUBSYSTEM.equals(pa.getLastElement().getKey())) {
            String extension = getExtension(context, pa.getLastElement().getValue());
            if (extension != null) {
                ModelNode extensionParam = new ModelNode();
                extensionParam.get(ModelDescriptionConstants.NAME).set(EXTENSION);
                extensionParam.get(DEFAULT).set(extension);
                feature.get(FEATURE).get(PARAMETERS).add(extensionParam);
                ModelNode packages = feature.get(FEATURE).get("packages").setEmptyList();
                ModelNode packageNode = new ModelNode();
                packageNode.get("package").set(extension + ".main");
                packages.add(packageNode);
            }
        }
        final Map<PathElement, ModelNode> childResources = recursive ? new HashMap<>() : Collections.<PathElement, ModelNode>emptyMap();

        // We're going to add a bunch of steps that should immediately follow this one. We are going to add them
        // in reverse order of how they should execute, as that is the way adding a Stage.IMMEDIATE step works
        // Last to execute is the handler that assembles the overall response from the pieces created by all the other steps
        final ReadFeatureAssemblyHandler assemblyHandler = new ReadFeatureAssemblyHandler(feature, childResources, accessControlContext);
        context.addStep(assemblyHandler, OperationContext.Stage.MODEL, true);

        if (recursive) {
            final ModelNode children;
            if (!feature.get(FEATURE).get(CHILDREN).isDefined()) {
                children = feature.get(FEATURE).get(CHILDREN).setEmptyObject();
            } else {
                children = feature.get(FEATURE).get(CHILDREN);
            }
            for (final PathElement element : registry.getChildAddresses(PathAddress.EMPTY_ADDRESS)) {
                PathAddress relativeAddr = PathAddress.pathAddress(element);
                ImmutableManagementResourceRegistration childReg = registry.getSubModel(relativeAddr);

                boolean readChild = true;
                if (childReg.isRemote()) {
                    readChild = false;
                }
                if (childReg.isAlias()) {
                    readChild = false;
                }
                if (childReg.isRuntimeOnly()) {
                    readChild = false;
                }
                if (!childReg.isFeature()) {
                    readChild = false;
                }

                if (readChild) {
                    final ModelNode childNode = children.get(element.getKey());
                    childNode.get(FEATURE);
                    final ModelNode rrOp = operation.clone();
                    final PathAddress address;
                    try {
                        address = PathAddress.pathAddress(opAddr, element);
                    } catch (Exception e) {
                        continue;
                    }
                    rrOp.get(OP_ADDR).set(address.toModelNode());
                    // WFCORE-76
                    GlobalOperationHandlers.setNextRecursive(context, operation, rrOp);
                    final ModelNode rrRsp = new ModelNode();
                    childResources.put(element, rrRsp);

                    final OperationStepHandler handler = getRecursiveStepHandler(childReg, opName, accessControlContext, address);
                    context.addStep(rrRsp, rrOp, handler, OperationContext.Stage.MODEL, true);
                }
            }
        }

        context.completeStep(new OperationContext.RollbackHandler() {
            @Override
            public void handleRollback(OperationContext context, ModelNode operation) {

                if (!context.hasFailureDescription()) {
                    for (final ModelNode value : childResources.values()) {
                        if (value.hasDefined(FAILURE_DESCRIPTION)) {
                            context.getFailureDescription().set(value.get(FAILURE_DESCRIPTION));
                            break;
                        }
                    }
                }
            }
        });
    }

    private String getExtension(OperationContext context, String subsystem) {
        for (String extensionName : context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS, false).getChildrenNames(EXTENSION)) {
            Resource extension = context.readResourceFromRoot(PathAddress.pathAddress(EXTENSION, extensionName), false);
            if (extension.getChildrenNames(SUBSYSTEM).contains(subsystem)) {
                return extensionName;
            }
        }
        return null;
    }

    private OperationStepHandler getRecursiveStepHandler(ImmutableManagementResourceRegistration childReg, String opName, ReadFeatureAccessControlContext accessControlContext, PathAddress address) {
        OperationStepHandler overrideHandler = childReg.getOperationHandler(PathAddress.EMPTY_ADDRESS, opName);
        if (overrideHandler != null && (overrideHandler.getClass() == ReadFeatureDescriptionHandler.class || overrideHandler.getClass() == AliasStepHandler.class)) {
            // not an override
            overrideHandler = null;
        }

        if (overrideHandler != null) {
            return new NestedReadFeatureHandler(capabilityRegistry, overrideHandler);
        }
        return new NestedReadFeatureHandler(capabilityRegistry, new ReadFeatureAccessControlContext(address, accessControlContext));
    }

    private ImmutableManagementResourceRegistration getResourceRegistrationCheckForAlias(OperationContext context, PathAddress opAddr) {
        //The direct root registration is only needed if we are doing access-control=true
        final ImmutableManagementResourceRegistration root = context.getRootResourceRegistration();
        final ImmutableManagementResourceRegistration registry = root.getSubModel(opAddr);

        AliasEntry aliasEntry = registry.getAliasEntry();
        if (aliasEntry == null) {
            return registry;
        }
        //Get hold of the real registry if it was an alias
        PathAddress realAddress = aliasEntry.convertToTargetAddress(opAddr, AliasEntry.AliasContext.create(opAddr, context));
        assert !realAddress.equals(opAddr) : "Alias was not translated";

        return root.getSubModel(realAddress);
    }

    private ModelNode describeFeature(final Locale locale, final ImmutableManagementResourceRegistration registration,
            final CapabilityScope capabilityScope, boolean isProfile) {
        if (!registration.isFeature() || registration.isRuntimeOnly() || registration.isAlias()) {
            return new ModelNode();
        }
        final ModelNode result = new ModelNode();
        final PathAddress pa = registration.getPathAddress();
        final ModelNode resourceDescriptionNode = registration.getModelDescription(PathAddress.EMPTY_ADDRESS)
                .getModelDescription(locale);
        final ModelNode feature = result.get(FEATURE);
        feature.get(ModelDescriptionConstants.NAME).set(registration.getFeature());
        final DescriptionProvider addDescriptionProvider = registration.getOperationDescription(PathAddress.EMPTY_ADDRESS, ModelDescriptionConstants.ADD);
        final ModelNode requestProperties;
        final Map<String, String> featureParamMappings;
        if (addDescriptionProvider != null) {
            ModelNode annotation = feature.get(ANNOTATION);
            annotation.get(ModelDescriptionConstants.NAME).set(ModelDescriptionConstants.ADD);
            requestProperties = addDescriptionProvider.getModelDescription(locale)
                    .get(ModelDescriptionConstants.REQUEST_PROPERTIES);
            featureParamMappings = addParams(feature, pa, requestProperties);
            addOpParam(annotation, requestProperties, featureParamMappings);
        } else {
            requestProperties = new ModelNode().setEmptyList();
            StringJoiner params = new StringJoiner(",");
            params.setEmptyValue("");
            if (resourceDescriptionNode.hasDefined(ATTRIBUTES)) {
                final ModelNode attributeNodes = resourceDescriptionNode.require(ATTRIBUTES);
                for (AttributeAccess attAccess : registration.getAttributes(PathAddress.EMPTY_ADDRESS).values()) {
                    if (CONFIGURATION != attAccess.getStorageType() ||
                            attAccess.getAccessType() != AttributeAccess.AccessType.READ_WRITE) {
                        continue;
                    }
                    final AttributeDefinition attDef = attAccess.getAttributeDefinition();
//                    if(attDef.isDeprecated()) {
//                        continue;
//                    }
                    switch (attDef.getType()) {
                        case LIST:
                            if (!ObjectListAttributeDefinition.class.isAssignableFrom(attDef.getClass())) {
                                requestProperties.add(attDef.getName(), attributeNodes.get(attDef.getName()));
                            }
                            break;
                        case OBJECT:
                            break;
                        default:
                            requestProperties.add(attDef.getName(), attributeNodes.get(attDef.getName()));
                            break;
                    }
                }
                featureParamMappings = addParams(feature, pa, requestProperties);
                if (requestProperties.isDefined() && !requestProperties.asList().isEmpty()) {
                    ModelNode annotation = feature.get(ANNOTATION);
                    annotation.get(ModelDescriptionConstants.NAME).set(ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION);
                    addOpParam(annotation, requestProperties, featureParamMappings);
                } else {
                    feature.remove(ANNOTATION); // no operation
                }
            } else {
                featureParamMappings = Collections.emptyMap();
            }
        }
        Set<String> capabilities = new HashSet<>();
        for (RuntimeCapability<?> cap : registration.getCapabilities()) {
            String capabilityName = cap.getName();
            if (cap.isDynamicallyNamed()) {
                PathAddress aliasAddress = createAliasPathAddress(registration, pa);
                capabilityName = cap.getDynamicName(aliasAddress);
            }
            if (isProfile) {
                capabilityName = $PROFILE_PREFIX + capabilityName;
            }
            capabilities.add(capabilityName);
            feature.get("provides").add(capabilityName);
        }
        processComplexAttributes(feature, registration);
        addReferences(feature, registration);
        addRequiredCapabilities(feature, registration, requestProperties, capabilityScope, isProfile, capabilities,
                featureParamMappings);
        return result;
    }

    private void processComplexAttributes(final ModelNode parentFeature, final ImmutableManagementResourceRegistration registration) {
        for (AttributeAccess attAccess : registration.getAttributes(PathAddress.EMPTY_ADDRESS).values()) {
            if(attAccess.getStorageType() != CONFIGURATION || attAccess.getAccessType() != AttributeAccess.AccessType.READ_WRITE) {
                continue;
            }
            final AttributeDefinition attDef = attAccess.getAttributeDefinition();
//            if(attDef.isDeprecated()) {
//                continue;
//            }
            switch (attDef.getType()) {
                case LIST:
                    if (ObjectListAttributeDefinition.class.isAssignableFrom(attDef.getClass())) {
                        processListAttribute(parentFeature, registration, (ObjectListAttributeDefinition) attDef);
                    }
                    break;
                case OBJECT:
                    if (ObjectTypeAttributeDefinition.class.isAssignableFrom(attDef.getClass())) {
                        ObjectTypeAttributeDefinition objAttDef = (ObjectTypeAttributeDefinition) attDef;
                        processObjectAttribute(parentFeature, registration, objAttDef);
                    }
                    break;
                default:
            }
        }
    }

    private void processObjectAttribute(final ModelNode parentFeature, final ImmutableManagementResourceRegistration registration, ObjectTypeAttributeDefinition objAttDef) {
        // we need a non resource feature
        final ModelNode attFeature = new ModelNode();
        final String specName = parentFeature.require(NAME).asString() + "." + objAttDef.getName();
        attFeature.get(NAME).set(specName);
        final ModelNode annotation = attFeature.get(ANNOTATION);
        annotation.get(ModelDescriptionConstants.NAME).set(WRITE_ATTRIBUTE_OPERATION);
        annotation.get("complex-attribute").set(objAttDef.getName());
        if (parentFeature.hasDefined(ANNOTATION)) {
            annotation.get(ADDRESS_PARAMETERS)
                    .set(parentFeature.require(ANNOTATION).require(ADDRESS_PARAMETERS));
            if (parentFeature.require(ANNOTATION).hasDefined(ADDRESS_PARAMETERS_MAPPING)) {
                annotation.get(ADDRESS_PARAMETERS)
                        .set(parentFeature.require(ANNOTATION).require(ADDRESS_PARAMETERS_MAPPING));
            }
        } else {
            addParams(attFeature, registration.getPathAddress(), new ModelNode().setEmptyList());
        }

        ModelNode refs = attFeature.get(REFERENCES).setEmptyList();
        ModelNode ref = new ModelNode();
        ref.get(FEATURE).set(parentFeature.require(NAME));
        refs.add(ref);

        ModelNode params = attFeature.get(PARAMETERS).setEmptyList();
        Set<String> idParams = new HashSet<>();
        if (parentFeature.hasDefined(PARAMETERS)) {
            for (ModelNode param : parentFeature.require(PARAMETERS).asList()) {
                if (param.hasDefined(FEATURE_ID) && param.get(FEATURE_ID).asBoolean()) {
                    idParams.add(param.get(NAME).asString());
                    params.add(param);
                }
            }
        }

        final AttributeDefinition[] attrs = objAttDef.getValueTypes();
        Map<String, String> opParamMapping = Collections.emptyMap();
        final ModelNode requestProps = new ModelNode();
        for(AttributeDefinition attr : attrs) {
            final ModelNode param = new ModelNode();
            String paramName = attr.getName();
            requestProps.add(new Property(paramName, new ModelNode()));
            if(idParams.contains(paramName)) {
                paramName += "-feature";
                if(opParamMapping.isEmpty()) {
                    opParamMapping = Collections.singletonMap(attr.getName(), paramName);
                } else {
                    if(opParamMapping.size() == 1) {
                        opParamMapping = new HashMap<>(opParamMapping);
                    }
                    opParamMapping.put(attr.getName(), paramName);
                }
            }
            param.get(ModelDescriptionConstants.NAME).set(paramName);
            if(!attr.isRequired()) {
                param.get(NILLABLE).set(true);
            }
            if (objAttDef.getDefaultValue() != null && objAttDef.getDefaultValue().isDefined()) {
                param.get(ModelDescriptionConstants.DEFAULT).set(objAttDef.getDefaultValue());
            }
            if(attr.getType() == ModelType.LIST) {
                param.get(TYPE).set("List<String>");
            }
            params.add(param);
        }
        addOpParam(annotation, requestProps, opParamMapping);

        parentFeature.get(CHILDREN).get(specName).set(attFeature);
    }

    private void processListAttribute(final ModelNode parentFeature, final ImmutableManagementResourceRegistration registration, ObjectListAttributeDefinition objAttDef) {
        //System.out.println("List attr " + registration.getPathAddress().toCLIStyleString() + " " + objAttDef.getName());
        final ModelNode parentSpecName = parentFeature.require(NAME);
        ModelNode attFeature = new ModelNode();
        final String specName = parentSpecName.asString() + "." + objAttDef.getName();
        attFeature.get(NAME).set(specName);
        ModelNode annotation = attFeature.get(ANNOTATION);
        annotation.get(ModelDescriptionConstants.NAME).set("list-add");
        annotation.get("complex-attribute").set(objAttDef.getName());
        if (parentFeature.hasDefined(ANNOTATION)) {
            annotation.get(ADDRESS_PARAMETERS)
                    .set(parentFeature.require(ANNOTATION).require(ADDRESS_PARAMETERS));
            if (parentFeature.require(ANNOTATION).hasDefined(ADDRESS_PARAMETERS_MAPPING)) {
                annotation.get(ADDRESS_PARAMETERS)
                        .set(parentFeature.require(ANNOTATION).require(ADDRESS_PARAMETERS_MAPPING));
            }
        } else {
            addParams(attFeature, registration.getPathAddress(), new ModelNode().setEmptyList());
        }

        ModelNode refs = attFeature.get(REFERENCES).setEmptyList();
        ModelNode ref = new ModelNode();
        ref.get(FEATURE).set(parentSpecName);
        refs.add(ref);

        final ModelNode params = attFeature.get(PARAMETERS).setEmptyList();
        final Set<String> idParams = new HashSet<>();
        if (parentFeature.hasDefined(PARAMETERS)) {
            for (ModelNode param : parentFeature.require(PARAMETERS).asList()) {
                if (param.hasDefined(FEATURE_ID) && param.get(FEATURE_ID).asBoolean()) {
                    final ModelNode parentParam = new ModelNode();
                    final String paramName = param.get(NAME).asString();
                    parentParam.get(NAME).set(paramName);
                    params.add(parentParam);
                    idParams.add(paramName);
                }
            }
        }

        final ObjectTypeAttributeDefinition itemType = objAttDef.getValueType();
        final AttributeDefinition[] attrs = itemType.getValueTypes();
        Map<String, String> opParamMapping = Collections.emptyMap();
        final ModelNode requestProps = new ModelNode();
        for(AttributeDefinition attr : attrs) {
            final ModelNode param = new ModelNode();
            String paramName = attr.getName();
            requestProps.add(new Property(paramName, new ModelNode()));
            if(idParams.contains(paramName)) {
                paramName += "-feature";
                if(opParamMapping.isEmpty()) {
                    opParamMapping = Collections.singletonMap(attr.getName(), paramName);
                } else {
                    if(opParamMapping.size() == 1) {
                        opParamMapping = new HashMap<>(opParamMapping);
                    }
                    opParamMapping.put(attr.getName(), paramName);
                }
            }
            param.get(ModelDescriptionConstants.NAME).set(paramName);
            if(!attr.isRequired()) {
                param.get(NILLABLE).set(true);
            }
            if (objAttDef.getDefaultValue() != null && objAttDef.getDefaultValue().isDefined()) {
                param.get(ModelDescriptionConstants.DEFAULT).set(objAttDef.getDefaultValue());
            }
            if(attr.getType() == ModelType.LIST) {
                param.get(TYPE).set("List<String>");
            }
            params.add(param);
        }
        addOpParam(annotation, requestProps, opParamMapping);

        parentFeature.get(CHILDREN).get(specName).set(attFeature);
    }

    private Map<String, String> addParams(ModelNode feature, PathAddress address, ModelNode requestProperties) {
        ModelNode params = feature.get(PARAMETERS).setEmptyList();
        Set<String> paramNames = new HashSet<>();
        StringJoiner addressParams = new StringJoiner(",");
        for (PathElement elt : address) {
            String paramName = elt.getKey();
            ModelNode param = new ModelNode();
            param.get(ModelDescriptionConstants.NAME).set(paramName);
            if (PROFILE.equals(elt.getKey()) || HOST.equals(elt.getKey())) {
                param.get(DEFAULT).set("GLN_UNDEFINED");
            } else if (!elt.isWildcard()) {
                param.get(DEFAULT).set(elt.getValue());
            }
            param.get(FEATURE_ID).set(true);
            params.add(param);
            paramNames.add(paramName);
            addressParams.add(paramName);
        }
        Map<String, String> featureParamMappings = new HashMap<>();
        for (Property att : requestProperties.asPropertyList()) {
            final ModelNode attDescription = att.getValue();
//            if(isDeprecated(attDescription)) {
//                continue;
//            }
            ModelNode param = new ModelNode();
            String paramName;
            if (paramNames.contains(att.getName()) || ((PROFILE.equals(att.getName()) || HOST.equals(att.getName())) && isSubsystem(address))) {
                paramName = att.getName() + "-feature";
                featureParamMappings.put(att.getName(), paramName);
            } else {
                paramName = att.getName();
            }
            param.get(ModelDescriptionConstants.NAME).set(paramName);
            paramNames.add(paramName);
            if (attDescription.hasDefined(NILLABLE) && attDescription.get(NILLABLE).asBoolean()) {
                param.get(NILLABLE).set(true);
            }
            if (attDescription.hasDefined(ModelDescriptionConstants.DEFAULT) && attDescription.hasDefined(CAPABILITY_REFERENCE)) {
                param.get(ModelDescriptionConstants.DEFAULT).set(attDescription.get(ModelDescriptionConstants.DEFAULT));
            }
            if (attDescription.hasDefined(TYPE) && "LIST".equals(attDescription.get(TYPE).asString())) {
                try {
                    switch (ModelType.valueOf(attDescription.get(VALUE_TYPE).asString())) {
                        case STRING:
                        case INT:
                        case BIG_DECIMAL:
                        case BIG_INTEGER:
                        case DOUBLE:
                        case LONG:
                        case BOOLEAN:
                            param.get(TYPE).set("List<String>");
                            break;
                        default:
                    }
                } catch (IllegalArgumentException ex) {
                    //value_type is an object
                }
            }
            params.add(param);
        }
        final ModelNode annotationNode = feature.get(ANNOTATION);
        annotationNode.get(ADDRESS_PARAMETERS).set(addressParams.toString());
        return featureParamMappings;
    }

//    private boolean isDeprecated(final ModelNode attDescription) {
//        return attDescription.hasDefined(ModelDescriptionConstants.DEPRECATED);
//    }

    private void addReferences(ModelNode feature, ImmutableManagementResourceRegistration registration) {
        PathAddress address = registration.getPathAddress();
        if (address == null || PathAddress.EMPTY_ADDRESS.equals(address)) {
            return;
        }
        ModelNode refs = feature.get(REFERENCES).setEmptyList();
        if (registration.getParent() != null && registration.getParent().isFeature()) {
            addReference(refs, registration.getParent());
        }
        PathElement element = registration.getPathAddress().getLastElement();
        if (SUBSYSTEM.equals(element.getKey())) {
            ModelNode ref = new ModelNode();
            final String rootType = registration.getPathAddress().getElement(0).getKey();
            if (HOST.equals(rootType)) {
                ref.get(FEATURE).set(HOST_EXTENSION);
            } else if (PROFILE.equals(rootType)) {
                ref.get(FEATURE).set(DOMAIN_EXTENSION);
            } else {
                ref.get(FEATURE).set(EXTENSION);
            }
            ref.get(INCLUDE).set(true);
            refs.add(ref);
        }
        if (refs.asList().isEmpty()) {
            feature.remove(REFERENCES);
        }
    }

    private void addReference(ModelNode refs, ImmutableManagementResourceRegistration registration) {
        PathAddress address = registration.getPathAddress();
        if (address == null || PathAddress.EMPTY_ADDRESS.equals(address)) {
            return;
        }
        if (registration.isFeature()) {
            ModelNode ref = new ModelNode();
            ref.get(FEATURE).set(registration.getFeature());
            refs.add(ref);
        }
        if (registration.getParent() != null) {
            addReference(refs, registration.getParent());
        }
    }

    private void addOpParam(ModelNode annotation, ModelNode requestProperties, Map<String, String> featureParamMappings) {
        if (!requestProperties.isDefined()) {
            return;
        }
        List<Property> request = requestProperties.asPropertyList();
        StringJoiner params = new StringJoiner(",");
        StringJoiner paramMappings = new StringJoiner(",");
        boolean keepMapping = false;
        for (Property att : request) {
//            if(isDeprecated(att.getValue())) {
//                continue;
//            }
            String realName = att.getName();
            if (featureParamMappings.containsKey(realName)) {
                keepMapping = true;
                params.add(featureParamMappings.get(realName));
            } else {
                params.add(realName);
            }
            paramMappings.add(realName);
        }
        if (keepMapping) {
            annotation.get(OPERATION_PARAMETERS_MAPPING).set(paramMappings.toString());
        }
        annotation.get(OPERATION_PARAMETERS).set(params.toString());
    }

    private void addRequiredCapabilities(ModelNode feature,
            final ImmutableManagementResourceRegistration registration,
            ModelNode requestProperties, CapabilityScope scope, boolean isProfile,
            Set<String> capabilities, Map<String, String> featureParamMappings) {
        if (requestProperties.isDefined()) {
            List<Property> request = requestProperties.asPropertyList();
            if (!request.isEmpty()) {
                ModelNode required = new ModelNode().setEmptyList();
                boolean filteredOut = false;
                for (String cap : capabilities) {
                    if (cap.startsWith("org.wildfly.domain.server-config.")) {
                        filteredOut = true;
                        break;
                    }
                }
                for (Property att : request) {
                    if (att.getValue().hasDefined(CAPABILITY_REFERENCE)) {
                        ModelNode capability = new ModelNode();
                        String capabilityName = att.getValue().get(CAPABILITY_REFERENCE).asString();
                        final String baseName;
                        if (capabilityName.indexOf('$') > 0) {
                            baseName = capabilityName.substring(0, capabilityName.indexOf('$') - 1);
                        } else {
                            baseName = capabilityName;
                        }
                        CapabilityRegistration<?> capReg = getCapability(new CapabilityId(baseName, scope));
                        String attributeName = featureParamMappings.containsKey(att.getName()) ? featureParamMappings.get(att.getName()) : att.getName();
                        if (capReg == null || capReg.getCapability().isDynamicallyNamed() && capabilityName.indexOf('$') <= 0) {
                            capabilityName = baseName + ".$" + attributeName;
                        }
                        if (filteredOut) {
                            continue;
                        }
                        if (att.getValue().hasDefined(CAPABILITY_REFERENCE_PATTERN_ELEMENTS)) {
                            List<String> elements = new ArrayList<>();
                            for (ModelNode elt : att.getValue().get(CAPABILITY_REFERENCE_PATTERN_ELEMENTS).asList()) {
                                elements.add("$" + elt.asString());
                            }
                            capabilityName = RuntimeCapability.buildDynamicCapabilityName(baseName, elements.toArray(new String[elements.size()]));
                        }
                        capability.get("optional").set(att.getValue().hasDefined(NILLABLE) && att.getValue().get(NILLABLE).asBoolean());
                        if (isProfile) {
                            if (!capabilityName.startsWith("org.wildfly.network.socket-binding")) {
                                capabilityName = $PROFILE_PREFIX + capabilityName;
                            }
                        }
                        capability.get(NAME).set(capabilityName);
                        if (!capabilityName.startsWith("org.wildfly.domain.server-group.")
                                && !capabilityName.startsWith("org.wildfly.domain.socket-binding-group.")) {
                            required.add(capability);
                        }
                        if (att.getValue().hasDefined(FEATURE_REFERENCE) && att.getValue().require(FEATURE_REFERENCE).asBoolean()) {
                            if (capReg != null) {
                                ImmutableManagementResourceRegistration root = getRootRegistration(registration);
                                ModelNode refs;
                                if (!feature.hasDefined(REFERENCES)) {
                                    refs = feature.get(REFERENCES).setEmptyList();
                                } else {
                                    refs = feature.get(REFERENCES);
                                }
                                if (registration.getParent() != null && registration.getParent().isFeature()) {
                                    addReference(refs, registration.getParent());
                                }
                                for (RegistrationPoint regPoint : capReg.getRegistrationPoints()) {
                                    ModelNode ref = new ModelNode();
                                    ref.get(FEATURE).set(root.getSubModel(regPoint.getAddress()).getFeature());
                                    refs.add(ref);
                                }
                            }
                        }
                    }
                }
                if (!registration.getRequirements().isEmpty()) {
                    PathAddress aliasAddress = createAliasPathAddress(registration, registration.getPathAddress());
                    for (CapabilityReferenceRecorder requirement : registration.getRequirements()) {
                        String[] segments = requirement.getRequirementPatternSegments(null, aliasAddress);
                        String[] dynamicElements;
                        if (segments == null || segments.length == 0) {
                            dynamicElements = null;
                        } else {
                            dynamicElements = new String[segments.length];
                            for (int i = 0; i < segments.length; i++) {
                                dynamicElements[i] = "$" + segments[i];
                            }
                        }
                        String baseRequirementName;
                        if (isProfile) {
                            baseRequirementName = $PROFILE_PREFIX + requirement.getBaseRequirementName();
                        } else {
                            baseRequirementName = requirement.getBaseRequirementName();
                        }
                        ModelNode capability = new ModelNode();
                        if (dynamicElements == null) {
                            capability.get(NAME).set(baseRequirementName);
                        } else {
                            capability.get(NAME).set(RuntimeCapability.buildDynamicCapabilityName(baseRequirementName, dynamicElements));
                        }
                        required.add(capability);
                    }
                }
                if (!required.asList().isEmpty()) {
                    feature.get(ModelDescriptionConstants.REQUIRES).set(required);
                }
            }
        }
    }

    private ImmutableManagementResourceRegistration getRootRegistration(final ImmutableManagementResourceRegistration registration) {
        if (!PathAddress.EMPTY_ADDRESS.equals(registration.getPathAddress())) {
            return getRootRegistration(registration.getParent());
        }
        return registration;
    }

    private boolean isSubsystem(PathAddress address) {
        for(PathElement elt : address) {
            if(SUBSYSTEM.equals(elt.getKey())) {
                return true;
            }
        }
        return false;
    }
    private boolean isProfileScope(ProcessType processType, PathAddress address) {
        if(processType.isServer() || address.size() < 2) {
            return false;
        }
        return PROFILE.equals(address.getElement(0).getKey());
    }

    private CapabilityRegistration<?> getCapability(CapabilityId capabilityId) {
        CapabilityRegistration<?> capReg = this.capabilityRegistry.getCapability(capabilityId);
        if (capReg == null) {
            for (CapabilityRegistration<?> reg : this.capabilityRegistry.getPossibleCapabilities()) {
                if (reg.getCapabilityId().getName().equals(capabilityId.getName())) {
                    capReg = reg;
                    break;
                }
            }
        }
        return capReg;
    }

    private PathAddress createAliasPathAddress(final ImmutableManagementResourceRegistration registration, PathAddress pa) {
        ImmutableManagementResourceRegistration registry = registration.getParent();
        List<PathElement> elements = new ArrayList<>();
        for (int i = pa.size() - 1; i >= 0; i--) {
            PathElement elt = pa.getElement(i);
            ImmutableManagementResourceRegistration childRegistration = registry.getSubModel(PathAddress.pathAddress(PathElement.pathElement(elt.getKey())));
            if (childRegistration == null) {
                elements.add(elt);
            } else {
                String value = "$" + elt.getKey();
                elements.add(PathElement.pathElement(elt.getKey(), value));
            }
            registry = registry.getParent();
        }
        Collections.reverse(elements);
        return PathAddress.pathAddress(elements.toArray(new PathElement[elements.size()]));
    }

    /**
     *
     * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
     */
    private static final class CheckResourceAccessHandler implements OperationStepHandler {

        private final boolean runtimeResource;
        private final boolean defaultSetting;
        private final ModelNode accessControlResult;
        private final ModelNode nodeDescription;

        CheckResourceAccessHandler(boolean runtimeResource, boolean defaultSetting, ModelNode accessControlResult, ModelNode nodeDescription) {
            this.runtimeResource = runtimeResource;
            this.defaultSetting = defaultSetting;
            this.accessControlResult = accessControlResult;
            this.nodeDescription = nodeDescription;
        }

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            ModelNode result = new ModelNode();
            boolean customDefaultCheck = operation.get(OP).asString().equals(GlobalOperationHandlers.CHECK_DEFAULT_RESOURCE_ACCESS);
            ResourceAuthorization authResp = context.authorizeResource(true, customDefaultCheck);
            if (authResp == null || authResp.getResourceResult(ActionEffect.ADDRESS).getDecision() == Decision.DENY) {
                if (!defaultSetting || authResp == null) {
                    //We are not allowed to see the resource, so we don't set the accessControlResult, meaning that the ReadFeatureHandler will ignore it for this address
                } else {
                    result.get(ActionEffect.ADDRESS.toString()).set(false);
                }
            } else {
//                if (!defaultSetting) {
//                    result.get(ADDRESS).set(operation.get(OP_ADDR));
//                }
                addResourceAuthorizationResults(result, authResp);

                ModelNode attributes = new ModelNode();
                attributes.setEmptyObject();

                if (result.get(READ).asBoolean()) {
                    if (nodeDescription.hasDefined(ATTRIBUTES)) {
                        for (Property attrProp : nodeDescription.require(ATTRIBUTES).asPropertyList()) {
                            ModelNode attributeResult = new ModelNode();
                            Storage storage = Storage.valueOf(attrProp.getValue().get(STORAGE).asString().toUpperCase(Locale.ENGLISH));
                            addAttributeAuthorizationResults(attributeResult, attrProp.getName(), authResp, storage == Storage.RUNTIME);
                            if (attributeResult.isDefined()) {
                                attributes.get(attrProp.getName()).set(attributeResult);
                            }
                        }
                    }
                    result.get(ATTRIBUTES).set(attributes);
                }
            }
            accessControlResult.set(result);
        }

        private void addResourceAuthorizationResults(ModelNode result, ResourceAuthorization authResp) {
            if (runtimeResource) {
                addResourceAuthorizationResult(result, authResp, ActionEffect.READ_RUNTIME);
                addResourceAuthorizationResult(result, authResp, ActionEffect.WRITE_RUNTIME);
            } else {
                addResourceAuthorizationResult(result, authResp, ActionEffect.READ_CONFIG);
                addResourceAuthorizationResult(result, authResp, ActionEffect.WRITE_CONFIG);
            }
        }

        private void addResourceAuthorizationResult(ModelNode result, ResourceAuthorization authResp, ActionEffect actionEffect) {
            AuthorizationResult authResult = authResp.getResourceResult(actionEffect);
            result.get(actionEffect == ActionEffect.READ_CONFIG || actionEffect == ActionEffect.READ_RUNTIME ? READ : WRITE).set(authResult.getDecision() == Decision.PERMIT);
        }

        private void addAttributeAuthorizationResults(ModelNode result, String attributeName, ResourceAuthorization authResp, boolean runtime) {
            if (runtime) {
                addAttributeAuthorizationResult(result, attributeName, authResp, ActionEffect.READ_RUNTIME);
                addAttributeAuthorizationResult(result, attributeName, authResp, ActionEffect.WRITE_RUNTIME);
            } else {
                addAttributeAuthorizationResult(result, attributeName, authResp, ActionEffect.READ_CONFIG);
                addAttributeAuthorizationResult(result, attributeName, authResp, ActionEffect.WRITE_CONFIG);
            }
        }

        private void addAttributeAuthorizationResult(ModelNode result, String attributeName, ResourceAuthorization authResp, ActionEffect actionEffect) {
            AuthorizationResult authorizationResult = authResp.getAttributeResult(attributeName, actionEffect);
            if (authorizationResult != null) {
                result.get(actionEffect == ActionEffect.READ_CONFIG || actionEffect == ActionEffect.READ_RUNTIME ? READ : WRITE).set(authorizationResult.getDecision() == Decision.PERMIT);
            }
        }
    }

    /**
     * Assembles the response to a read-feature request from the components
     * gathered by earlier steps.
     */
    private static class ReadFeatureAssemblyHandler implements OperationStepHandler {

        private final ModelNode featureDescription;
        private final Map<PathElement, ModelNode> childResources;
        private final ReadFeatureAccessControlContext accessControlContext;

        /**
         * Creates a ReadFeatureAssemblyHandler that will assemble the response
         * using the contents of the given maps.
         *
         * @param featureDescription basic description of the node, of its
         * attributes and of its child types
         * @param childResources read-resource-description response from child
         * resources, where the key is the PathAddress relative to the address
         * of the operation this handler is handling and the value is the full
         * read-resource response. Will not be {@code null}
         * @param accessControlContext context for tracking access control data
         * @param accessControl type of access control output that is needed
         */
        private ReadFeatureAssemblyHandler(final ModelNode featureDescription, final Map<PathElement, ModelNode> childResources,
                final ReadFeatureAccessControlContext accessControlContext) {
            this.featureDescription = featureDescription;
            this.childResources = childResources;
            this.accessControlContext = accessControlContext;
        }

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            for (Map.Entry<PathElement, ModelNode> entry : childResources.entrySet()) {
                final PathElement element = entry.getKey();
                final ModelNode value = entry.getValue();
                if (!value.has(FAILURE_DESCRIPTION)) {
                    ModelNode actualValue = value.get(RESULT);
                    if (actualValue.equals(PROXY_NO_SUCH_RESOURCE)) {
                        featureDescription.get(FEATURE).get(CHILDREN).remove(element.getKey());
                    } else if (actualValue.isDefined()) {
                        if (featureDescription.get(FEATURE).get(CHILDREN).has(element.getKey())) {
                            featureDescription.get(FEATURE).get(CHILDREN).remove(element.getKey());
                        }
                        if (actualValue.hasDefined(FEATURE)) {
                            String name = value.get(RESULT, FEATURE, NAME).asString();
                            featureDescription.get(FEATURE, CHILDREN, name).set(actualValue.get(FEATURE));
                        }
                    } else {
                        if (featureDescription.get(FEATURE).get(CHILDREN).has(element.getKey())) {
                            featureDescription.get(FEATURE).get(CHILDREN).remove(element.getKey());
                        }
                    }
                } else if (value.hasDefined(FAILURE_DESCRIPTION)) {
                    context.getFailureDescription().set(value.get(FAILURE_DESCRIPTION));
                    break;
                }
            }

            if (accessControlContext.defaultWildcardAccessControl != null && accessControlContext.localResourceAccessControlResults != null) {
                ModelNode accessControl = new ModelNode();
                accessControl.setEmptyObject();

                ModelNode defaultControl;
                if (accessControlContext.defaultWildcardAccessControl != null) {
                    accessControl.get(DEFAULT).set(accessControlContext.defaultWildcardAccessControl);
                    defaultControl = accessControlContext.defaultWildcardAccessControl;
                } else {
                    //TODO this should always be present
                    defaultControl = new ModelNode();
                }

                if (accessControlContext.localResourceAccessControlResults != null) {
                    ModelNode exceptions = accessControl.get(EXCEPTIONS);
                    exceptions.setEmptyObject();
                    for (Map.Entry<PathAddress, ModelNode> entry : accessControlContext.localResourceAccessControlResults.entrySet()) {
                        if (!entry.getValue().isDefined()) {
                            //If access was denied CheckResourceAccessHandler will leave this as undefined
                            continue;
                        }
                        if (!entry.getValue().equals(defaultControl)) {
                            //This has different values to the default due to vault expressions being used for attribute values. We need to include the address
                            //in the exception modelnode for the console to be easier able to parse it
                            ModelNode exceptionAddr = entry.getKey().toModelNode();
                            ModelNode exception = entry.getValue();
                            exception.get(ADDRESS).set(exceptionAddr);
                            exceptions.get(exceptionAddr.asString()).set(entry.getValue());
                        }
                    }
                }
            }
            context.getResult().set(featureDescription);
        }
    }

    private static final class ReadFeatureAccessControlContext {

        private final PathAddress opAddress;
        private final List<PathAddress> parentAddresses;
        private List<PathAddress> localResourceAddresses = null;
        private ModelNode defaultWildcardAccessControl;
        private Map<PathAddress, ModelNode> localResourceAccessControlResults = new HashMap<>();

        ReadFeatureAccessControlContext(PathAddress opAddress, ReadFeatureAccessControlContext parent) {
            this.opAddress = opAddress;
            this.parentAddresses = parent != null ? parent.parentAddresses : null;
        }

        void checkResourceAccess(final OperationContext context, final ImmutableManagementResourceRegistration registration, final ModelNode nodeDescription) {
            final ModelNode defaultAccess = Util.createOperation(
                    opAddress.size() > 0 && !opAddress.getLastElement().isWildcard()
                    ? GlobalOperationHandlers.CHECK_DEFAULT_RESOURCE_ACCESS : GlobalOperationHandlers.CHECK_RESOURCE_ACCESS,
                    opAddress);
            defaultWildcardAccessControl = new ModelNode();
            context.addStep(defaultAccess, new CheckResourceAccessHandler(registration.isRuntimeOnly(), true, defaultWildcardAccessControl, nodeDescription), OperationContext.Stage.MODEL, true);

            for (final PathAddress address : localResourceAddresses) {
                final ModelNode op = Util.createOperation(GlobalOperationHandlers.CHECK_RESOURCE_ACCESS, address);
                final ModelNode resultHolder = new ModelNode();
                localResourceAccessControlResults.put(address, resultHolder);
                context.addStep(op, new CheckResourceAccessHandler(registration.isRuntimeOnly(), false, resultHolder, nodeDescription), OperationContext.Stage.MODEL, true);
            }
        }
    }

    private class NestedReadFeatureHandler extends ReadFeatureDescriptionHandler {

        final ReadFeatureAccessControlContext accessControlContext;
        final OperationStepHandler overrideStepHandler;

        NestedReadFeatureHandler(final ImmutableCapabilityRegistry capabilityRegistry, ReadFeatureAccessControlContext accessControlContext) {
            super(capabilityRegistry);
            this.accessControlContext = accessControlContext;
            this.overrideStepHandler = null;
        }

        NestedReadFeatureHandler(final ImmutableCapabilityRegistry capabilityRegistry, OperationStepHandler overrideStepHandler) {
            super(capabilityRegistry);
            this.accessControlContext = null;
            this.overrideStepHandler = overrideStepHandler;
        }

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            if (accessControlContext != null) {
                doExecute(context, operation, accessControlContext);
            } else {
                try {
                    overrideStepHandler.execute(context, operation);
                } catch (Resource.NoSuchResourceException e) {
                    //Mark it as not accessible so that the assembly handler can remove it
                    context.getResult().set(PROXY_NO_SUCH_RESOURCE);
                } catch (UnauthorizedException e) {
                    //We were not allowed to read it, the assembly handler should still allow people to see it
                    context.getResult().set(new ModelNode());
                }
            }
        }
    }
}
