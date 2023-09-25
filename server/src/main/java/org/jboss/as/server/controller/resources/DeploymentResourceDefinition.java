/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.controller.resources;



import static org.jboss.as.server.controller.resources.DeploymentAttributes.CONTENT_RESOURCE_ALL;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.isUnmanagedContent;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.NotificationDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReadResourceNameOperationStepHandler;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.access.management.ApplicationTypeAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.server.deployment.DeploymentStatusHandler;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
@SuppressWarnings("deprecation")
public abstract class DeploymentResourceDefinition extends SimpleResourceDefinition {

    private final DeploymentResourceParent parent;
    public static final PathElement PATH = PathElement.pathElement(ModelDescriptionConstants.DEPLOYMENT);

    protected DeploymentResourceDefinition(DeploymentResourceParent parent, OperationStepHandler addHandler, OperationStepHandler removeHandler) {
        super(new Parameters(PATH, DeploymentAttributes.DEPLOYMENT_RESOLVER)
                .setAddHandler(addHandler)
                .setRemoveHandler(removeHandler)
                .setAccessConstraints(ApplicationTypeAccessConstraintDefinition.DEPLOYMENT));
        this.parent = parent;
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {

        for (AttributeDefinition attr : parent.getResourceAttributes()) {
            if (attr.getName().equals(DeploymentAttributes.STATUS.getName())) {
                resourceRegistration.registerMetric(attr, DeploymentStatusHandler.INSTANCE);
            } else if (attr.getName().equals(DeploymentAttributes.NAME.getName())) {
                resourceRegistration.registerReadOnlyAttribute(DeploymentAttributes.NAME, ReadResourceNameOperationStepHandler.INSTANCE);
            } else if (DeploymentAttributes.MANAGED.getName().equals(attr.getName())) {
                resourceRegistration.registerReadOnlyAttribute(DeploymentAttributes.MANAGED, new OperationStepHandler() {
                    @Override
                    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                        extractedManaged(context, operation);
                    }
                });
            } else {
                resourceRegistration.registerReadOnlyAttribute(attr, null);
            }
        }
    }

    public void extractedManaged(OperationContext context, ModelNode operation) {
        ModelNode deployment = context.readResource(PathAddress.EMPTY_ADDRESS, true).getModel();
        if(deployment.hasDefined(CONTENT_RESOURCE_ALL.getName())) {
            ModelNode content = deployment.get(CONTENT_RESOURCE_ALL.getName()).asList().get(0);
            context.getResult().set(!isUnmanagedContent(content));
        }
    }

    @Override
    public void registerNotifications(ManagementResourceRegistration resourceRegistration) {
        for (NotificationDefinition notif : parent.getNotifications()) {
            resourceRegistration.registerNotification(notif);
        }
    }

    protected DeploymentResourceParent getParent() {
        return parent;
    }

    protected abstract void registerAddOperation(final ManagementResourceRegistration registration, final OperationStepHandler handler,
            OperationEntry.Flag... flags);

    public static enum DeploymentResourceParent {
        DOMAIN (DeploymentAttributes.DOMAIN_RESOURCE_ATTRIBUTES, DeploymentAttributes.DOMAIN_ADD_ATTRIBUTES),
        SERVER_GROUP (DeploymentAttributes.SERVER_GROUP_RESOURCE_ATTRIBUTES, DeploymentAttributes.SERVER_GROUP_ADD_ATTRIBUTES),
        SERVER (DeploymentAttributes.SERVER_RESOURCE_ATTRIBUTES, DeploymentAttributes.SERVER_ADD_ATTRIBUTES,
            new NotificationDefinition[] {DeploymentAttributes.NOTIFICATION_DEPLOYMENT_DEPLOYED, DeploymentAttributes.NOTIFICATION_DEPLOYMENT_UNDEPLOYED});

        final AttributeDefinition[] resourceAttributes;
        final AttributeDefinition[] addAttributes;
        final NotificationDefinition[] notifications;

        private DeploymentResourceParent(AttributeDefinition[] resourceAttributes, AttributeDefinition[] addAttributes, NotificationDefinition... notifications) {
            this.resourceAttributes = resourceAttributes;
            this.addAttributes = addAttributes;
            this.notifications = notifications == null ? new NotificationDefinition[0] : notifications;
        }

        AttributeDefinition[] getResourceAttributes() {
            return resourceAttributes;
        }

        AttributeDefinition[] getAddAttributes() {
            return addAttributes;
        }

        NotificationDefinition[] getNotifications() {
            return notifications;
        }
    }
}
