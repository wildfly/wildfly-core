/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.management;

import static org.jboss.as.controller.logging.ControllerLogger.ROOT_LOGGER;

import java.util.ArrayList;
import java.util.List;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.State;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceNotFoundException;
import org.jboss.msc.service.ServiceRegistry;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * The base add handler for management interfaces.
 * @author Emmanuel Hugonnet (c) 2016 Red Hat, inc.
 */
public abstract class ManagementInterfaceAddStepHandler extends AbstractAddStepHandler {

    public static final OperationContext.AttachmentKey<Boolean> MANAGEMENT_INTERFACE_KEY = OperationContext.AttachmentKey.create(Boolean.class);

   @Override
    protected boolean requiresRuntime(OperationContext context) {
        //TODO Gigantic HACK to disable the runtime part of this for the core model testing.
        //The core model testing currently uses RunningMode.ADMIN_ONLY, but in the real world
        //the http interface needs to be enabled even when that happens.
        //I don't want to wire up all the services unless I can avoid it, so for now the tests set this system property
        return WildFlySecurityManager.getPropertyPrivileged("jboss.as.test.disable.runtime", null) == null;
    }

    protected void addVerifyInstallationStep(OperationContext context, List<ServiceName> requiredServices) {
        if(context.isBooting()) {
            context.addStep(new LenientVerifyInstallationStep(requiredServices), OperationContext.Stage.VERIFY);
        }
    }

    protected String asStringIfDefined(OperationContext context, AttributeDefinition attribute, ModelNode model) throws OperationFailedException {
        ModelNode attributeValue = attribute.resolveModelAttribute(context, model);
        return attributeValue.isDefined() ? attributeValue.asString() : null;
    }

    private static class LenientVerifyInstallationStep implements OperationStepHandler {
        private final List<ServiceName> requiredServices;

        private LenientVerifyInstallationStep(List<ServiceName> requiredServices) {
            assert requiredServices != null;
            this.requiredServices = requiredServices;
        }

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            List<ServiceName> failures = new ArrayList<>();
            ServiceRegistry registry = context.getServiceRegistry(false);
            for (ServiceName serviceName : requiredServices) {
                try {
                    ServiceController<?> controller = registry.getService(serviceName);
                    if (controller == null || State.UP != controller.getState()) {
                        failures.add(serviceName);
                    }
                } catch (ServiceNotFoundException ex) {
                    failures.add(serviceName);
                }
            }
            if (!failures.isEmpty()) {
                Boolean attachment = context.getAttachment(MANAGEMENT_INTERFACE_KEY);
                if (attachment == null || !context.getAttachment(MANAGEMENT_INTERFACE_KEY)) {
                    context.attach(MANAGEMENT_INTERFACE_KEY, false);
                    context.addStep(new VerifyInstallationStep(), OperationContext.Stage.VERIFY);
                }
            } else {
                context.attach(MANAGEMENT_INTERFACE_KEY, true);
            }
        }
    }

    private static class VerifyInstallationStep implements OperationStepHandler {

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            Boolean attachment = context.getAttachment(MANAGEMENT_INTERFACE_KEY);
            if (attachment == null ||!context.getAttachment(MANAGEMENT_INTERFACE_KEY)) {
                ROOT_LOGGER.missingManagementServices();
                context.setRollbackOnly();
            }
        }
    }
}
