/*
 * JBoss, Home of Professional Open Source.
 * Copyright ${year}, Red Hat, Inc., and individual contributors
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
package org.jboss.as.server.services.net;

import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.SocketBindingGroupRemoveHandler;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.resource.AbstractSocketBindingGroupResourceDefinition;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author Kabir Khan
 */
public class SocketBindingGroupResourceDefinition extends AbstractSocketBindingGroupResourceDefinition {

    public static SocketBindingGroupResourceDefinition INSTANCE = new SocketBindingGroupResourceDefinition();

    public static final SimpleAttributeDefinition PORT_OFFSET = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.PORT_OFFSET, ModelType.INT, true)
            .setAllowExpression(true).setValidator(new IntRangeValidator(-65535, 65535, true, true))
            .setDefaultValue(new ModelNode().set(0)).setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES).build();

    private SocketBindingGroupResourceDefinition() {
        super(BindingGroupAddHandler.INSTANCE, SocketBindingGroupRemoveHandler.INSTANCE);
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerSubModel(SocketBindingResourceDefinition.INSTANCE);
        resourceRegistration.registerSubModel(RemoteDestinationOutboundSocketBindingResourceDefinition.INSTANCE);
        resourceRegistration.registerSubModel(LocalDestinationOutboundSocketBindingResourceDefinition.INSTANCE);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);
        resourceRegistration.registerReadWriteAttribute(PORT_OFFSET, null, new ReloadRequiredWriteAttributeHandler(PORT_OFFSET));
    }
}
