/*
* JBoss, Home of Professional Open Source.
* Copyright 2011, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.as.remoting;

import static org.jboss.as.remoting.CommonAttributes.INCLUDE_MECHANISMS;
import static org.jboss.as.remoting.CommonAttributes.QOP;
import static org.jboss.as.remoting.CommonAttributes.REUSE_SESSION;
import static org.jboss.as.remoting.CommonAttributes.SASL;
import static org.jboss.as.remoting.CommonAttributes.SECURITY;
import static org.jboss.as.remoting.CommonAttributes.SERVER_AUTH;
import static org.jboss.as.remoting.CommonAttributes.STRENGTH;

import java.util.Collection;
import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.xnio.sasl.SaslQop;
import org.xnio.sasl.SaslStrength;

/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author Tomaz Cerar
 */
class SaslResource extends ConnectorChildResource {
    static final PathElement SASL_CONFIG_PATH = PathElement.pathElement(SECURITY, SASL);

    static final AttributeDefinition INCLUDE_MECHANISMS_ATTRIBUTE = new StringListAttributeDefinition.Builder(INCLUDE_MECHANISMS)
            .setRequired(false)
            .build();

    static final AttributeDefinition QOP_ATTRIBUTE = new StringListAttributeDefinition.Builder(QOP)
            .setRequired(false)
            .setElementValidator(EnumValidator.create(SaslQop.class, SaslQop.values()))
            .build();
    static final AttributeDefinition STRENGTH_ATTRIBUTE = new StringListAttributeDefinition.Builder(STRENGTH)
            .setRequired(false)
            .setElementValidator(EnumValidator.create(SaslStrength.class, SaslStrength.values()))
            .build();
    static final SimpleAttributeDefinition SERVER_AUTH_ATTRIBUTE = SimpleAttributeDefinitionBuilder.create(SERVER_AUTH, ModelType.BOOLEAN)
            .setDefaultValue(ModelNode.FALSE)
            .setRequired(false)
            .setAllowExpression(true)
            .build();
    static final SimpleAttributeDefinition REUSE_SESSION_ATTRIBUTE = SimpleAttributeDefinitionBuilder.create(REUSE_SESSION, ModelType.BOOLEAN)
            .setDefaultValue(ModelNode.FALSE)
            .setRequired(false)
            .setAllowExpression(true)
            .build();

    private final String parent;

    static final Collection<AttributeDefinition> ATTRIBUTES = List.of(INCLUDE_MECHANISMS_ATTRIBUTE, QOP_ATTRIBUTE, STRENGTH_ATTRIBUTE, SERVER_AUTH_ATTRIBUTE, REUSE_SESSION_ATTRIBUTE);

    SaslResource(final String parent) {
        super(new Parameters(SASL_CONFIG_PATH, RemotingExtension.getResourceDescriptionResolver(SASL))
                .setAddHandler(new AddResourceConnectorRestartHandler(parent, ATTRIBUTES))
                .setRemoveHandler(new RemoveResourceConnectorRestartHandler(parent))
                .setAccessConstraints(RemotingExtension.REMOTING_SECURITY_DEF));
        this.parent = parent;
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        final RestartConnectorWriteAttributeHandler writeHandler =
                new RestartConnectorWriteAttributeHandler(parent, ATTRIBUTES);
        resourceRegistration.registerReadWriteAttribute(INCLUDE_MECHANISMS_ATTRIBUTE, null, writeHandler);
        resourceRegistration.registerReadWriteAttribute(QOP_ATTRIBUTE, null, writeHandler);
        resourceRegistration.registerReadWriteAttribute(STRENGTH_ATTRIBUTE, null, writeHandler);
        resourceRegistration.registerReadWriteAttribute(REUSE_SESSION_ATTRIBUTE, null, writeHandler);
        resourceRegistration.registerReadWriteAttribute(SERVER_AUTH_ATTRIBUTE, null, writeHandler);
    }
}
