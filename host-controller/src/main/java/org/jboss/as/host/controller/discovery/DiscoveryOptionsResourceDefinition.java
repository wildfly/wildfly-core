/*
* JBoss, Home of Professional Open Source.
* Copyright 2013, Red Hat Middleware LLC, and individual contributors
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

package org.jboss.as.host.controller.discovery;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PrimitiveListAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.validation.PropertyValidator;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.AttributeAccess.Flag;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.host.controller.descriptions.HostResolver;
import org.jboss.as.host.controller.operations.DiscoveryOptionsReadAttributeHandler;
import org.jboss.as.host.controller.operations.DiscoveryWriteAttributeHandler;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * {@link org.jboss.as.controller.ResourceDefinition} for a resource representing discovery options.
 *
 * @author Farah Juma
 */
public class DiscoveryOptionsResourceDefinition extends SimpleResourceDefinition {

    public static DiscoveryOptionsResourceDefinition INSTANCE = new DiscoveryOptionsResourceDefinition();

    public static final PrimitiveListAttributeDefinition DISCOVERY_OPTIONS = new PrimitiveListAttributeDefinition.Builder(ModelDescriptionConstants.DISCOVERY_OPTIONS, ModelType.PROPERTY)
        .setRequired(false)
        .setElementValidator(new PropertyValidator(false, new StringLengthValidator(1)))
        .setDeprecated(ModelVersion.create(1))
        .build();

    public static final ObjectListAttributeDefinition OPTIONS;
    static {

        final SimpleAttributeDefinition name = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.NAME, ModelType.STRING)
                .setRequired(true)
                .setValidator(new StringLengthValidator(1))
                .build();

        AttributeDefinition[] attrs = new AttributeDefinition[1 + DiscoveryOptionResourceDefinition.DISCOVERY_ATTRIBUTES.length];
        attrs[0] = name;
        System.arraycopy(DiscoveryOptionResourceDefinition.DISCOVERY_ATTRIBUTES, 0, attrs, 1, DiscoveryOptionResourceDefinition.DISCOVERY_ATTRIBUTES.length);
        final ObjectTypeAttributeDefinition doAttr = ObjectTypeAttributeDefinition.Builder
                .of(ModelDescriptionConstants.CUSTOM_DISCOVERY, attrs)
                .addAlternatives(ModelDescriptionConstants.STATIC_DISCOVERY)
                .build();

        attrs = new AttributeDefinition[1 + StaticDiscoveryResourceDefinition.STATIC_DISCOVERY_ATTRIBUTES.length];
        attrs[0] = name;
        System.arraycopy(StaticDiscoveryResourceDefinition.STATIC_DISCOVERY_ATTRIBUTES, 0, attrs, 1, StaticDiscoveryResourceDefinition.STATIC_DISCOVERY_ATTRIBUTES.length);
        final ObjectTypeAttributeDefinition sdAttr = ObjectTypeAttributeDefinition.Builder
                .of(ModelDescriptionConstants.STATIC_DISCOVERY, attrs)
                .addAlternatives(ModelDescriptionConstants.CUSTOM_DISCOVERY)
                .build();

        final ObjectTypeAttributeDefinition listItem = ObjectTypeAttributeDefinition.Builder
                .of("list-item", doAttr, sdAttr).build();

        OPTIONS = ObjectListAttributeDefinition.Builder.of(ModelDescriptionConstants.OPTIONS, listItem)
                .setType(ModelType.LIST)
                .addFlag(Flag.STORAGE_CONFIGURATION)
                .setRequired(false)
                .setDefaultValue(new ModelNode().setEmptyList())
                .build();
    }

    private DiscoveryOptionsResourceDefinition() {
        super(PathElement.pathElement(CORE_SERVICE, ModelDescriptionConstants.DISCOVERY_OPTIONS),
                HostResolver.getResolver(ModelDescriptionConstants.DISCOVERY_OPTIONS));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);
        resourceRegistration.registerReadOnlyAttribute(DISCOVERY_OPTIONS, new DiscoveryOptionsReadAttributeHandler());
        resourceRegistration.registerReadWriteAttribute(OPTIONS, null, new DiscoveryWriteAttributeHandler());
    }
}
