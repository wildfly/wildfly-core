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
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.security.auth.server.SecurityIdentity;

/**
 * The resource for the legacy behaviour where all attributes are read as resolved and
 * attributes/operation parameters are typed and you can't use expressions
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ExposeModelResourceResolved extends ExposeModelResource {

    public static final PathElement PATH_ELEMENT = PathElement.pathElement(CommonAttributes.EXPOSE_MODEL, CommonAttributes.RESOLVED);

    public static final SimpleAttributeDefinition DOMAIN_NAME = SimpleAttributeDefinitionBuilder.create(CommonAttributes.DOMAIN_NAME, ModelType.STRING, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(CommonAttributes.DEFAULT_RESOLVED_DOMAIN))
            .build();

    static final SimpleAttributeDefinition PROPER_PROPERTY_FORMAT = SimpleAttributeDefinitionBuilder.create(CommonAttributes.PROPER_PROPERTY_FORMAT, ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.TRUE)
            .build();

    private final RuntimeHostControllerInfoAccessor hostInfoAccessor;

    ExposeModelResourceResolved(ManagedAuditLogger auditLoggerInfo, JmxAuthorizer authorizer, Supplier<SecurityIdentity> securityIdentitySupplier, RuntimeHostControllerInfoAccessor hostInfoAccessor) {
        super(PATH_ELEMENT, auditLoggerInfo, authorizer, securityIdentitySupplier, hostInfoAccessor, DOMAIN_NAME, PROPER_PROPERTY_FORMAT);
        this.hostInfoAccessor = hostInfoAccessor;
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);
        resourceRegistration.registerReadWriteAttribute(PROPER_PROPERTY_FORMAT, null, new JMXWriteAttributeHandler(hostInfoAccessor, PROPER_PROPERTY_FORMAT));
    }


}
