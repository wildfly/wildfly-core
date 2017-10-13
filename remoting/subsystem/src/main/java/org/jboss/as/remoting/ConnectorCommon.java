/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.dmr.ModelType;

/**
 * Common attributes shared by both types of connector.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class ConnectorCommon {

    static final SimpleAttributeDefinition SERVER_NAME = new SimpleAttributeDefinitionBuilder(CommonAttributes.SERVER_NAME, ModelType.STRING, true)
            .setAllowExpression(true)
            .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, true))
            .addAccessConstraint(RemotingExtension.REMOTING_SECURITY_DEF)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition SASL_AUTHENTICATION_FACTORY = new SimpleAttributeDefinitionBuilder(CommonAttributes.SASL_AUTHENTICATION_FACTORY, ModelType.STRING, true)
            .setMinSize(1)
            .addAccessConstraint(RemotingExtension.REMOTING_SECURITY_DEF)
            .setNullSignificant(true)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition SASL_PROTOCOL = new SimpleAttributeDefinitionBuilder(CommonAttributes.SASL_PROTOCOL, ModelType.STRING, true)
            .setAllowExpression(true)
            .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, true))
            .setDefaultValue(Protocol.REMOTE.toModelNode())
            .addAccessConstraint(RemotingExtension.REMOTING_SECURITY_DEF)
            .setRestartAllServices()
            .build();

}
