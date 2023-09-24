/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.jmx;

import java.util.function.Supplier;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.RestartParentResourceAddHandler;
import org.jboss.as.controller.RestartParentResourceRemoveHandler;
import org.jboss.as.controller.RestartParentWriteAttributeHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.access.management.JmxAuthorizer;
import org.jboss.as.controller.audit.ManagedAuditLogger;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.extension.RuntimeHostControllerInfoAccessor;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.jmx.logging.JmxLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;
import org.wildfly.security.auth.server.SecurityIdentity;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
abstract class ExposeModelResource extends SimpleResourceDefinition {

    private final ManagedAuditLogger auditLoggerInfo;
    private final JmxAuthorizer authorizer;
    private final Supplier<SecurityIdentity> securityIdentitySupplier;
    private final SimpleAttributeDefinition domainName;
    private final RuntimeHostControllerInfoAccessor hostInfoAccessor;

    ExposeModelResource(PathElement pathElement, ManagedAuditLogger auditLoggerInfo, JmxAuthorizer authorizer, Supplier<SecurityIdentity> securityIdentitySupplier,
            RuntimeHostControllerInfoAccessor hostInfoAccessor, SimpleAttributeDefinition domainName, SimpleAttributeDefinition...otherAttributes) {
        super(pathElement,
                JMXExtension.getResourceDescriptionResolver(CommonAttributes.EXPOSE_MODEL + "." + pathElement.getValue()),
                new ShowModelAdd(auditLoggerInfo, authorizer, securityIdentitySupplier, domainName, hostInfoAccessor, otherAttributes),
                new ShowModelRemove(auditLoggerInfo, authorizer, securityIdentitySupplier, hostInfoAccessor));
        this.auditLoggerInfo = auditLoggerInfo;
        this.authorizer = authorizer;
        this.securityIdentitySupplier = securityIdentitySupplier;
        this.domainName = domainName;
        this.hostInfoAccessor = hostInfoAccessor;
    }

    static SimpleAttributeDefinition getDomainNameAttribute(String childName) {
        if (CommonAttributes.RESOLVED.equals(childName)){
            return ExposeModelResourceResolved.DOMAIN_NAME;
        } else if (CommonAttributes.EXPRESSION.equals(childName)) {
            return ExposeModelResourceExpression.DOMAIN_NAME;
        }

        throw JmxLogger.ROOT_LOGGER.unknownChild(childName);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadWriteAttribute(domainName, null, new JMXWriteAttributeHandler(hostInfoAccessor, domainName));
    }

    class JMXWriteAttributeHandler extends RestartParentWriteAttributeHandler {
        private final RuntimeHostControllerInfoAccessor hostInfoAccessor;
        JMXWriteAttributeHandler(RuntimeHostControllerInfoAccessor hostInfoAccessor, AttributeDefinition attr) {
            super(ModelDescriptionConstants.SUBSYSTEM, attr);
            this.hostInfoAccessor = hostInfoAccessor;
        }

        @Override
        protected void recreateParentService(OperationContext context, ModelNode parentModel) throws OperationFailedException {
            JMXSubsystemAdd.launchServices(context, parentModel, auditLoggerInfo, authorizer, securityIdentitySupplier, hostInfoAccessor);
        }

        @Override
        protected ServiceName getParentServiceName(PathAddress parentAddress) {
            return MBeanServerService.SERVICE_NAME;
        }
    }

    private static class ShowModelAdd extends RestartParentResourceAddHandler {

        private final ManagedAuditLogger auditLoggerInfo;
        private final JmxAuthorizer authorizer;
        private final Supplier<SecurityIdentity> securityIdentitySupplier;
        private final SimpleAttributeDefinition domainName;
        private final SimpleAttributeDefinition[] otherAttributes;
        private final RuntimeHostControllerInfoAccessor hostInfoAccessor;

        private ShowModelAdd(ManagedAuditLogger auditLoggerInfo, JmxAuthorizer authorizer, Supplier<SecurityIdentity> securityIdentitySupplier,
                SimpleAttributeDefinition domainName, RuntimeHostControllerInfoAccessor hostInfoAccessor,
                SimpleAttributeDefinition...otherAttributes) {
            super(ModelDescriptionConstants.SUBSYSTEM);
            this.auditLoggerInfo = auditLoggerInfo;
            this.authorizer = authorizer;
            this.securityIdentitySupplier = securityIdentitySupplier;
            this.domainName = domainName;
            this.otherAttributes = otherAttributes;
            this.hostInfoAccessor = hostInfoAccessor;
        }

        @Override
        protected void populateModel(ModelNode operation, ModelNode model) throws OperationFailedException {
            domainName.validateAndSet(operation, model);
            if (otherAttributes.length > 0) {
                for (SimpleAttributeDefinition attr : otherAttributes) {
                    attr.validateAndSet(operation, model);
                }
            }
        }

        @Override
        protected void recreateParentService(OperationContext context, ModelNode parentModel) throws OperationFailedException {
            JMXSubsystemAdd.launchServices(context, parentModel, auditLoggerInfo, authorizer, securityIdentitySupplier, hostInfoAccessor);
        }

        @Override
        protected ServiceName getParentServiceName(PathAddress parentAddress) {
            return MBeanServerService.SERVICE_NAME;
        }
    }

    private static class ShowModelRemove extends RestartParentResourceRemoveHandler {

        private final ManagedAuditLogger auditLoggerInfo;
        private final JmxAuthorizer authorizer;
        private final Supplier<SecurityIdentity> securityIdentitySupplier;
        private final RuntimeHostControllerInfoAccessor hostInfoAccessor;

        private ShowModelRemove(ManagedAuditLogger auditLoggerInfo, JmxAuthorizer authorizer, Supplier<SecurityIdentity> securityIdentitySupplier, RuntimeHostControllerInfoAccessor hostInfoAccessor) {
            super(ModelDescriptionConstants.SUBSYSTEM);
            this.auditLoggerInfo = auditLoggerInfo;
            this.authorizer = authorizer;
            this.securityIdentitySupplier = securityIdentitySupplier;
            this.hostInfoAccessor = hostInfoAccessor;
        }

        @Override
        protected void recreateParentService(OperationContext context, ModelNode parentModel) throws OperationFailedException {
            JMXSubsystemAdd.launchServices(context, parentModel, auditLoggerInfo, authorizer, securityIdentitySupplier, hostInfoAccessor);
        }

        @Override
        protected ServiceName getParentServiceName(PathAddress parentAddress) {
            return MBeanServerService.SERVICE_NAME;
        }
    }
}
