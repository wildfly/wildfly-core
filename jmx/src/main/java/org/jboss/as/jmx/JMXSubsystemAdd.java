/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
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

package org.jboss.as.jmx;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.access.management.JmxAuthorizer;
import org.jboss.as.controller.audit.ManagedAuditLogger;
import org.jboss.as.controller.extension.RuntimeHostControllerInfoAccessor;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author Thomas.Diesler@jboss.com
 * @author Emanuel Muckenhuber
 */
class JMXSubsystemAdd extends AbstractAddStepHandler {

    private final ManagedAuditLogger auditLoggerInfo;
    private final JmxAuthorizer authorizer;
    private final RuntimeHostControllerInfoAccessor hostInfoAccessor;

    JMXSubsystemAdd(ManagedAuditLogger auditLoggerInfo, JmxAuthorizer authorizer, RuntimeHostControllerInfoAccessor hostInfoAccessor) {
        super(JMXSubsystemRootResource.JMX_CAPABILITY, JMXSubsystemRootResource.CORE_MBEAN_SENSITIVITY);
        this.auditLoggerInfo = auditLoggerInfo;
        this.authorizer = authorizer;
        this.hostInfoAccessor = hostInfoAccessor;
    }

    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        launchServices(context, Resource.Tools.readModel(context.readResource(PathAddress.EMPTY_ADDRESS)), auditLoggerInfo, authorizer, hostInfoAccessor);
    }

    static void launchServices(OperationContext context, ModelNode model, ManagedAuditLogger auditLoggerInfo,
                               JmxAuthorizer authorizer, RuntimeHostControllerInfoAccessor hostInfoAccessor) throws OperationFailedException {

        // Add the MBean service
        String resolvedDomain = getDomainName(context, model, CommonAttributes.RESOLVED);
        String expressionsDomain = getDomainName(context, model, CommonAttributes.EXPRESSION);
        boolean legacyWithProperPropertyFormat = false;
        if (model.hasDefined(CommonAttributes.PROPER_PROPERTY_FORMAT)) {
            legacyWithProperPropertyFormat = ExposeModelResourceExpression.DOMAIN_NAME.resolveModelAttribute(context, model).asBoolean();
        }
        boolean coreMBeanSensitivity = JMXSubsystemRootResource.CORE_MBEAN_SENSITIVITY.resolveModelAttribute(context, model).asBoolean();
        final boolean isMasterHc;
        if (context.getProcessType() == ProcessType.HOST_CONTROLLER) {
            isMasterHc = hostInfoAccessor.getHostControllerInfo(context).isMasterHc();
        } else {
            isMasterHc = false;
        }
        MBeanServerService.addService(context.getServiceTarget(), resolvedDomain, expressionsDomain, legacyWithProperPropertyFormat,
                            coreMBeanSensitivity, auditLoggerInfo, authorizer, context.getProcessType(), isMasterHc);
    }

    /**
     * return {@code null} if the {@code child} model is not exposed in JMX.
     */
    static String getDomainName(OperationContext context, ModelNode model, String child) throws OperationFailedException {
        if (!model.hasDefined(CommonAttributes.EXPOSE_MODEL)) {
            return null;
        }
        if (!model.get(CommonAttributes.EXPOSE_MODEL).hasDefined(child)) {
            return null;
        }
        ModelNode childModel = model.get(CommonAttributes.EXPOSE_MODEL, child);
        return ExposeModelResource.getDomainNameAttribute(child).resolveModelAttribute(context, childModel).asString();
    }

}
