/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
