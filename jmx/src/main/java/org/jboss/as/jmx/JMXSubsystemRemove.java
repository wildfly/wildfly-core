/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.jmx;

import java.util.function.Supplier;

import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.access.management.JmxAuthorizer;
import org.jboss.as.controller.audit.ManagedAuditLogger;
import org.jboss.as.controller.extension.RuntimeHostControllerInfoAccessor;
import org.jboss.dmr.ModelNode;
import org.wildfly.security.auth.server.SecurityIdentity;

/**
 * Removes the remoting subsystem
 *
 * @author Kabir Khan
 */
public class JMXSubsystemRemove extends AbstractRemoveStepHandler {

    private final ManagedAuditLogger auditLoggerInfo;
    private final JmxAuthorizer authorizer;
    private final Supplier<SecurityIdentity> securityIdentitySupplier;
    private final RuntimeHostControllerInfoAccessor hostInfoAccessor;

    JMXSubsystemRemove(ManagedAuditLogger auditLoggerInfo, JmxAuthorizer authorizer, Supplier<SecurityIdentity> securityIdentitySupplier, RuntimeHostControllerInfoAccessor hostInfoAccessor) {
        this.auditLoggerInfo = auditLoggerInfo;
        this.authorizer = authorizer;
        this.securityIdentitySupplier = securityIdentitySupplier;
        this.hostInfoAccessor = hostInfoAccessor;
    }

    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) {
        if (isRemoveService(context)) {
            //Since so many things can depend on this, only remove if the user set the ALLOW_RESOURCE_SERVICE_RESTART operation header
            context.removeService(MBeanServerService.SERVICE_NAME);
        } else {
            context.reloadRequired();
        }
    }

    protected void recoverServices(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        if (isRemoveService(context)) {
            JMXSubsystemAdd.launchServices(context, model, auditLoggerInfo, authorizer, securityIdentitySupplier, hostInfoAccessor);
        } else {
            context.revertReloadRequired();
        }
    }

    private boolean isRemoveService(OperationContext context) {
        if (context.isNormalServer()) {
            if (context.isResourceServiceRestartAllowed()) {
                context.removeService(MBeanServerService.SERVICE_NAME);
                return true;
            }
        }
        return false;
    }
}
