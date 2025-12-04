/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.audit;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.audit.ManagedAuditLogger;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.as.domain.management._private.DomainManagementResolver;

/**
 * {@link org.jboss.as.controller.ResourceDefinition} for the management audit logging resource.
 *
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 */
public class AccessAuditResourceDefinition extends SimpleResourceDefinition {

    public static final PathElement PATH_ELEMENT = PathElement.pathElement(ModelDescriptionConstants.ACCESS, ModelDescriptionConstants.AUDIT);

    private final ManagedAuditLogger auditLogger;
    private final PathManagerService pathManager;
    private final EnvironmentNameReader environmentReader;

    public AccessAuditResourceDefinition(final ManagedAuditLogger auditLogger, final PathManagerService pathManager, final EnvironmentNameReader environmentReader) {
        super(
                PATH_ELEMENT,
                DomainManagementResolver.getResolver("core.management.audit-log"),
                new AbstractAddStepHandler() {
                    @Override
                    protected boolean requiresRuntime(OperationContext context) {
                        return false;
                    }},
                new AbstractRemoveStepHandler() {
                    @Override
                    protected boolean requiresRuntime(OperationContext context) {
                        return false;
                    }});
        this.auditLogger = auditLogger;
        this.pathManager = pathManager;
        this.environmentReader = environmentReader;
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerSubModel(new JsonAuditLogFormatterResourceDefinition(auditLogger));
        resourceRegistration.registerSubModel(new FileAuditLogHandlerResourceDefinition(auditLogger, pathManager));
        resourceRegistration.registerSubModel(new PeriodicRotatingFileAuditLogHandlerResourceDefinition(auditLogger, pathManager));
        resourceRegistration.registerSubModel(new SizeRotatingFileAuditLogHandlerResourceDefinition(auditLogger, pathManager));
        resourceRegistration.registerSubModel(new SyslogAuditLogHandlerResourceDefinition(auditLogger, pathManager, environmentReader));
        resourceRegistration.registerSubModel(new InMemoryAuditLogHandlerResourceDefinition(auditLogger));
        resourceRegistration.registerSubModel(AuditLogLoggerResourceDefinition.createDefinition(auditLogger));
        if (!environmentReader.isServer()){
            resourceRegistration.registerSubModel(AuditLogLoggerResourceDefinition.createHostServerDefinition());
        }
    }


}
