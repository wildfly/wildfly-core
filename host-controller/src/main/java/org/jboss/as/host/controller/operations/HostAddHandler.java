/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.operations;

import static org.jboss.as.controller.client.helpers.ClientConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_CONTROLLER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.LOCAL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOTE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.controller.LocalHostControllerInfo;
import org.jboss.as.host.controller.descriptions.HostResolver;
import org.jboss.as.host.controller.logging.HostControllerLogger;
import org.jboss.as.host.controller.model.host.HostDefinition;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * The handler to add the local host definition to the DomainModel.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author <a href="mailto:kwills@redhat.com">Ken Wills</a>
 */
public class HostAddHandler extends AbstractAddStepHandler {

    public static final OperationContext.AttachmentKey<Boolean> HOST_ADD_AFTER_BOOT = OperationContext.AttachmentKey.create(Boolean.class);
    public static final OperationContext.AttachmentKey<String> HOST_NAME = OperationContext.AttachmentKey.create(String.class);

    private static final RuntimeCapability<Void> HOST_RUNTIME_CAPABILITY = RuntimeCapability
            .Builder.of("org.wildfly.host.controller", false)
            .build();

    public static final String OPERATION_NAME = "add";

    private static final SimpleAttributeDefinition PERSIST_NAME = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.PERSIST_NAME, ModelType.BOOLEAN)
            .setRequired(false)
            .setDefaultValue(ModelNode.FALSE)
            .build();

    private static final SimpleAttributeDefinition IS_DOMAIN_CONTROLLER = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.IS_DOMAIN_CONTROLLER, ModelType.BOOLEAN)
            .setRequired(false)
            .setDefaultValue(new ModelNode().set(Boolean.TRUE))
            .setDeprecated(ModelVersion.create(6), false)
            .build();

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, HostResolver.getResolver("host"))
            .withFlag(OperationEntry.Flag.HOST_CONTROLLER_ONLY)
            .addParameter(PERSIST_NAME)
            .addParameter(IS_DOMAIN_CONTROLLER)
            .build();

    private final HostDefinition hostDefinition;

    public HostAddHandler(final HostDefinition hostDefinition) {
        this.hostDefinition = hostDefinition;
    }

    /**
     * {@inheritDoc}
     */
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final PathAddress pa = context.getCurrentAddress();
        // if we're not already at the root, call this at the root as an immediate step
        if (!pa.equals(PathAddress.EMPTY_ADDRESS)) {
            final ModelNode cloned = operation.clone();
            cloned.get(OP_ADDR).set(PathAddress.EMPTY_ADDRESS.toModelNode());
            context.attach(HOST_NAME, pa.getLastElement().getValue());
            context.addStep(cloned, this, OperationContext.Stage.MODEL, true);
            return;
        }

        // see if a host has already been added.
        Resource root = context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS, false);
        if (!root.getChildrenNames(HOST).isEmpty()) {
            // there is a host already registered
            final String exists = root.getChildrenNames(HOST).iterator().next();
            throw HostControllerLogger.ROOT_LOGGER.cannotAddHostAlreadyRegistered(exists);
        }

        final String hostName = context.getAttachment(HOST_NAME);
        if (hostName == null) {
            throw HostControllerLogger.ROOT_LOGGER.nullHostName();
        }

        boolean persistName = false;
        if (operation.has(ModelDescriptionConstants.PERSIST_NAME)) {
            persistName = operation.get(ModelDescriptionConstants.PERSIST_NAME).asBoolean();
        }

        boolean isDomainController = true;
        if (operation.has(ModelDescriptionConstants.IS_DOMAIN_CONTROLLER)) {
            isDomainController = operation.get(ModelDescriptionConstants.IS_DOMAIN_CONTROLLER).asBoolean();
        }

        final ModelNode dc = new ModelNode();
        if (isDomainController) {
            dc.get(LOCAL).setEmptyObject();
        } else {
            dc.get(REMOTE).setEmptyObject();
        }

        if (!context.isBooting() && !isDomainController) {
            // this is a slave add using /host=foo:add() manually. Don't allow this.
            throw HostControllerLogger.ROOT_LOGGER.cannotAddSlaveHostAfterBoot();
        }

        context.registerCapability(HOST_RUNTIME_CAPABILITY);

        final PathAddress hostAddress = PathAddress.pathAddress(PathElement.pathElement(HOST, hostName));
        final LocalHostControllerInfo localHostControllerInfo = hostDefinition.getLocalHostControllerInfo();
        // register as DC or slave, we do this before registering the host definition.
        ((LocalHostControllerInfoImpl) localHostControllerInfo).setMasterDomainController(isDomainController || !context.isBooting());

        hostDefinition.registerHostModel(hostName);
        final Resource rootResource = context.createResource(hostAddress);
        final ModelNode model = rootResource.getModel();
        model.get(DOMAIN_CONTROLLER).set(dc);
        hostDefinition.initCoreModel(model);

        // check to see if we need to enable domainController to allow host op routing
        // this is only used during an empty config boot, the parsers will add the necessary op
        // for a normal boot.
        if (isDomainController || !context.isBooting()) {
            final ManagementResourceRegistration rootRegistration = context.getResourceRegistrationForUpdate();
            final ModelNode update = new ModelNode();
            update.get(OP_ADDR).set(hostAddress.toModelNode());
            update.get(OP).set(LocalDomainControllerAddHandler.OPERATION_NAME);
            context.attach(HOST_ADD_AFTER_BOOT, !context.isBooting());
            context.addStep(update, rootRegistration.getOperationHandler(hostAddress, LocalDomainControllerAddHandler.OPERATION_NAME), OperationContext.Stage.MODEL, true);
        }
        hostDefinition.initModelServices(context, hostAddress, rootResource);

        // if we added with /host=foo:add(persist-name=true) write-attribute on the hc name
        if (!context.isBooting() && persistName) {
            final ManagementResourceRegistration rootRegistration = context.getResourceRegistrationForUpdate();
            final ModelNode name = new ModelNode();
            name.get(OP_ADDR).set(hostAddress.toModelNode());
            name.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
            name.get(ModelDescriptionConstants.NAME).set(ModelDescriptionConstants.NAME);
            name.get(ModelDescriptionConstants.VALUE).set(hostName);
            context.addStep(name, rootRegistration.getOperationHandler(hostAddress, WRITE_ATTRIBUTE_OPERATION), OperationContext.Stage.MODEL, false);
        }
    }

}
