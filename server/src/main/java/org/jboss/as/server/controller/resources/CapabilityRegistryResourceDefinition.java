/*
 * JBoss, Home of Professional Open Source
 * Copyright 2016, Red Hat, Inc., and individual contributors as indicated
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

package org.jboss.as.server.controller.resources;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CAPABILITY_REGISTRY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;

import java.util.Set;

import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleListAttributeDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.capability.registry.CapabilityId;
import org.jboss.as.controller.capability.registry.CapabilityRegistration;
import org.jboss.as.controller.capability.registry.CapabilityScope;
import org.jboss.as.controller.capability.registry.ImmutableCapabilityRegistry;
import org.jboss.as.controller.capability.registry.RegistrationPoint;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.server.controller.descriptions.ServerDescriptions;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author Tomaz Cerar (c) 2015 Red Hat Inc.
 */
public class CapabilityRegistryResourceDefinition extends SimpleResourceDefinition {

    private static final SimpleListAttributeDefinition DEPENDENT_ADDRESS = new SimpleListAttributeDefinition.
            Builder("dependent-address", new SimpleAttributeDefinition("parameter", ModelType.PROPERTY, false)).build();

    private static final SimpleAttributeDefinition NAME = SimpleAttributeDefinitionBuilder.create("name", ModelType.STRING)
            .setRequired(true)
            .build();

    private static final StringListAttributeDefinition REGISTRATION_POINTS = new StringListAttributeDefinition.Builder("registration-points")
                .build();

    private static final SimpleAttributeDefinition DYNAMIC = SimpleAttributeDefinitionBuilder.create("dynamic", ModelType.BOOLEAN, false)
            .build();
    private static final SimpleAttributeDefinition SCOPE = SimpleAttributeDefinitionBuilder.create("scope", ModelType.STRING, true)
            .build();

    private static final ObjectTypeAttributeDefinition CAPABILITY = new ObjectTypeAttributeDefinition.Builder("capability", NAME, DYNAMIC, SCOPE, REGISTRATION_POINTS)
            .build();

    private static final ObjectListAttributeDefinition CAPABILITIES = new ObjectListAttributeDefinition.Builder("capabilities", CAPABILITY)
            .build();

    private static final ObjectTypeAttributeDefinition POSSIBLE_CAPABILITY = new ObjectTypeAttributeDefinition.Builder("possible-capability", NAME, DYNAMIC, REGISTRATION_POINTS)
            .build();

    private static final ObjectListAttributeDefinition POSSIBLE_CAPABILITIES = new ObjectListAttributeDefinition.Builder("possible-capabilities", POSSIBLE_CAPABILITY)
            .build();

    private static final OperationDefinition GET_PROVIDER_POINTS = new SimpleOperationDefinitionBuilder("get-provider-points", ServerDescriptions.getResourceDescriptionResolver("core", CAPABILITY_REGISTRY))
            .addParameter(NAME)
            .setReadOnly()
            .setRuntimeOnly()
            .setReplyType(ModelType.LIST)
            .setReplyValueType(ModelType.STRING)
            .build();
    private static final OperationDefinition GET_CAPABILITY = new SimpleOperationDefinitionBuilder("get-capability", ServerDescriptions.getResourceDescriptionResolver("core", CAPABILITY_REGISTRY))
            .addParameter(NAME)
            .addParameter(SCOPE)
            .setReadOnly()
            .setRuntimeOnly()
            .setReplyParameters(CAPABILITY)
            .build();
    private static final OperationDefinition SUGGEST_CAPABILITIES = new SimpleOperationDefinitionBuilder("suggest-capabilities", ServerDescriptions.getResourceDescriptionResolver("core", CAPABILITY_REGISTRY))
            .addParameter(NAME)
            .addParameter(DEPENDENT_ADDRESS)
            .setReadOnly()
            .setRuntimeOnly()
            .setReplyType(ModelType.LIST)
            .setReplyValueType(ModelType.STRING)
            .build();

    private final ImmutableCapabilityRegistry capabilityRegistry;


    public CapabilityRegistryResourceDefinition(final ImmutableCapabilityRegistry capabilityRegistry) {
        super(new Parameters(
                PathElement.pathElement(CORE_SERVICE, CAPABILITY_REGISTRY),
                ServerDescriptions.getResourceDescriptionResolver("core", CAPABILITY_REGISTRY))
                .setRuntime()
        );
        assert capabilityRegistry != null;
        this.capabilityRegistry = capabilityRegistry;
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadOnlyAttribute(POSSIBLE_CAPABILITIES,
                (context, operation) -> populateCapabilities(capabilityRegistry.getPossibleCapabilities(), context.getResult(), true));
        resourceRegistration.registerReadOnlyAttribute(CAPABILITIES,
                (context, operation) -> populateCapabilities(capabilityRegistry.getCapabilities(), context.getResult(), false));
    }

    private static void populateRegistrationPoints(ModelNode points, Set<RegistrationPoint> registrationPoints) {
        for (RegistrationPoint point : registrationPoints) {
            points.add(point.getAddress().toCLIStyleString());
        }
    }

    private static void populateCapabilities(Set<CapabilityRegistration<?>> caps, ModelNode res, boolean possible) {
        for (CapabilityRegistration cr : caps) {
            ModelNode cap = res.add();
            cap.get(NAME.getName()).set(cr.getCapabilityName());
            cap.get(DYNAMIC.getName()).set(cr.getCapability().isDynamicallyNamed());
            if (!possible) {
                cap.get(SCOPE.getName()).set(cr.getCapabilityScope().getName());
            }
            populateRegistrationPoints(cap.get(REGISTRATION_POINTS.getName()), cr.getRegistrationPoints());
        }
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        resourceRegistration.registerOperationHandler(GET_PROVIDER_POINTS, (context, operation) -> {
            final ModelNode model = new ModelNode();
            NAME.validateAndSet(operation, model);
            final String name = NAME.resolveModelAttribute(context, model).asString();
            CapabilityId id = new CapabilityId(name, CapabilityScope.GLOBAL); //for possible capabilities it is always global
            Set<PathAddress> providerPoints = capabilityRegistry.getPossibleProviderPoints(id);
            for (PathAddress point : providerPoints) {
                context.getResult().add(point.toCLIStyleString());
            }
        });

        resourceRegistration.registerOperationHandler(GET_CAPABILITY, (context, operation) -> {
            final ModelNode model = new ModelNode();
            NAME.validateAndSet(operation, model);
            SCOPE.validateAndSet(operation, model);
            final String name = NAME.resolveModelAttribute(context, model).asString();
            final CapabilityScope scope;
            if (model.hasDefined(SCOPE.getName())) {
                String scopeName = SCOPE.resolveModelAttribute(context, model).asString();
                scope = CapabilityScope.Factory.forName(scopeName);
            } else {
                scope = CapabilityScope.GLOBAL;
            }
            CapabilityId id = new CapabilityId(name, scope);
            CapabilityRegistration reg = capabilityRegistry.getCapability(id);
            if (reg!=null) {
                ModelNode result = context.getResult();
                populateCapabilityRegistration(reg, result);
            }
        });

        resourceRegistration.registerOperationHandler(SUGGEST_CAPABILITIES, (context, operation) -> {
            final String name = NAME.resolveModelAttribute(context, operation).asString();
            PathAddress address = PathAddress.pathAddress(DEPENDENT_ADDRESS.
                    resolveModelAttribute(context, operation));
            CapabilityScope dependentScope = CapabilityScope.Factory.
                create(context.getProcessType(),
                        address);
            Set<String> capabilities = capabilityRegistry.getDynamicCapabilityNames(name, dependentScope);
            for(String capability : capabilities) {
                context.getResult().add(capability);
            }
        });
    }

    private void populateCapabilityRegistration(CapabilityRegistration reg, ModelNode capability) {
        populateRegistrationPoints(capability.get(REGISTRATION_POINTS.getName()), reg.getRegistrationPoints());
    }
}
