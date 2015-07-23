/*
 * JBoss, Home of Professional Open Source
 * Copyright 2015, Red Hat, Inc., and individual contributors as indicated
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
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.capability.registry.CapabilityRegistration;
import org.jboss.as.controller.capability.registry.ImmutableCapabilityRegistry;
import org.jboss.as.controller.capability.registry.RegistrationPoint;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.server.controller.descriptions.ServerDescriptions;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author Tomaz Cerar (c) 2015 Red Hat Inc.
 */
class CapabilityRegistryResourceDefinition extends SimpleResourceDefinition {


    private static final SimpleAttributeDefinition NAME = SimpleAttributeDefinitionBuilder.create("name", ModelType.STRING)
            .setAllowNull(false)
            .build();

    private static final StringListAttributeDefinition REGISTRATION_POINTS = new StringListAttributeDefinition.Builder("registration-points")
            .build();

    private static final SimpleAttributeDefinition DYNAMIC = SimpleAttributeDefinitionBuilder.create("dynamic", ModelType.BOOLEAN, false)
                .build();

    private static final ObjectTypeAttributeDefinition CAPABILITY = new ObjectTypeAttributeDefinition.Builder("capability", NAME, DYNAMIC, REGISTRATION_POINTS)
            .build();


    private static final ObjectListAttributeDefinition POSSIBLE_CAPABILITIES = new ObjectListAttributeDefinition.Builder("possible-capabilities", CAPABILITY)
            .build();

    private static final ObjectListAttributeDefinition CAPABILITIES = new ObjectListAttributeDefinition.Builder("capabilities", CAPABILITY)
            .build();
    private final ImmutableCapabilityRegistry capabilityRegistry;


    public CapabilityRegistryResourceDefinition(final ImmutableCapabilityRegistry capabilityRegistry) {
        super(new Parameters(
                        PathElement.pathElement(CORE_SERVICE, CAPABILITY_REGISTRY),
                        ServerDescriptions.getResourceDescriptionResolver("core", CAPABILITY_REGISTRY))
                        .setRuntime()
        );
        this.capabilityRegistry = capabilityRegistry;
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadOnlyAttribute(POSSIBLE_CAPABILITIES, (context, operation) -> {
            populateCapabilities(capabilityRegistry.getPossibleCapabilities(), context.getResult());
        });
        resourceRegistration.registerReadOnlyAttribute(CAPABILITIES, (context, operation) -> {
            populateCapabilities(capabilityRegistry.getCapabilities(), context.getResult());
        });
    }

    private static void populateRegistrationPoints(ModelNode points, Set<RegistrationPoint> registrationPoints) {
        for (RegistrationPoint point : registrationPoints) {
            points.add(point.getAddress().toCLIStyleString());
        }
    }

    private static void populateCapabilities(Set<CapabilityRegistration> caps, ModelNode res) {
        for (CapabilityRegistration cr : caps) {
            ModelNode cap = res.add();
            cap.get(NAME.getName()).set(cr.getCapabilityName());
            cap.get(DYNAMIC.getName()).set(cr.getCapability().isDynamicallyNamed());
            populateRegistrationPoints(cap.get(REGISTRATION_POINTS.getName()), cr.getRegistrationPoints());
        }
    }

}
