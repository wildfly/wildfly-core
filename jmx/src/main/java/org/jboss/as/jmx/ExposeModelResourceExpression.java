/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.jmx;

import java.util.function.Supplier;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.access.management.JmxAuthorizer;
import org.jboss.as.controller.audit.ManagedAuditLogger;
import org.jboss.as.controller.extension.RuntimeHostControllerInfoAccessor;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.security.auth.server.SecurityIdentity;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ExposeModelResourceExpression extends ExposeModelResource{

    static final PathElement PATH_ELEMENT = PathElement.pathElement(CommonAttributes.EXPOSE_MODEL, CommonAttributes.EXPRESSION);

    static final SimpleAttributeDefinition DOMAIN_NAME = SimpleAttributeDefinitionBuilder.create(CommonAttributes.DOMAIN_NAME, ModelType.STRING, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(CommonAttributes.DEFAULT_EXPRESSION_DOMAIN))
            .build();

    ExposeModelResourceExpression(ManagedAuditLogger auditLoggerInfo, JmxAuthorizer authorizer, Supplier<SecurityIdentity> securityIdentitySupplier, RuntimeHostControllerInfoAccessor hostInfoAccessor) {
        super(PATH_ELEMENT, auditLoggerInfo, authorizer, securityIdentitySupplier, hostInfoAccessor, DOMAIN_NAME);
    }
}
