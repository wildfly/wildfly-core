/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;

/**
 * A persistent resource definition. This needs to be combined with {@link PersistentResourceXMLDescription} to
 * simplify the process of creating parsers and persisters.
 *
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2013 Red Hat Inc.
 */
public abstract class PersistentResourceDefinition extends SimpleResourceDefinition {

    protected PersistentResourceDefinition(PathElement pathElement, ResourceDescriptionResolver descriptionResolver) {
        super(pathElement, descriptionResolver);
    }

    protected PersistentResourceDefinition(PathElement pathElement, ResourceDescriptionResolver descriptionResolver, OperationStepHandler addHandler, OperationStepHandler removeHandler) {
        super(pathElement, descriptionResolver, addHandler, removeHandler);
    }

    protected PersistentResourceDefinition(PathElement pathElement, ResourceDescriptionResolver descriptionResolver, OperationStepHandler addHandler, OperationStepHandler removeHandler, OperationEntry.Flag addRestartLevel, OperationEntry.Flag removeRestartLevel) {
        super(new SimpleResourceDefinition.Parameters(pathElement, descriptionResolver)
                        .setAddHandler(addHandler)
                        .setRemoveHandler(removeHandler)
                        .setAddRestartLevel(addRestartLevel)
                        .setRemoveRestartLevel(removeRestartLevel)
        );
    }

    protected PersistentResourceDefinition(PathElement pathElement, ResourceDescriptionResolver descriptionResolver, OperationStepHandler addHandler,
                                           OperationStepHandler removeHandler, boolean isRuntime) {
        super(new SimpleResourceDefinition.Parameters(pathElement, descriptionResolver)
                               .setAddHandler(addHandler)
                               .setRemoveHandler(removeHandler)
                               .setRuntime(isRuntime)
               );
    }

    protected PersistentResourceDefinition(PathElement pathElement, ResourceDescriptionResolver descriptionResolver, OperationStepHandler addHandler,
                                           OperationStepHandler removeHandler, OperationEntry.Flag addRestartLevel, OperationEntry.Flag removeRestartLevel, boolean isRuntime) {
        super(new SimpleResourceDefinition.Parameters(pathElement, descriptionResolver)
                        .setAddHandler(addHandler)
                        .setRemoveHandler(removeHandler)
                        .setAddRestartLevel(addRestartLevel)
                        .setRemoveRestartLevel(removeRestartLevel)
                        .setRuntime(isRuntime)
        );
    }
    protected PersistentResourceDefinition(SimpleResourceDefinition.Parameters parameters){
        super(parameters);
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        super.registerChildren(resourceRegistration);
        for (PersistentResourceDefinition child : getChildren()) {
            resourceRegistration.registerSubModel(child);
        }
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);
        ReloadRequiredWriteAttributeHandler handler = new ReloadRequiredWriteAttributeHandler(getAttributes());
        for (AttributeDefinition attr : getAttributes()) {
            if(!attr.getFlags().contains(AttributeAccess.Flag.RESTART_ALL_SERVICES)) {
                throw ControllerLogger.ROOT_LOGGER.attributeWasNotMarkedAsReloadRequired(attr.getName(), resourceRegistration.getPathAddress());
            }
            resourceRegistration.registerReadWriteAttribute(attr, null, handler);
        }
    }

    protected List<? extends PersistentResourceDefinition> getChildren() {
        return Collections.emptyList();
    }

    public abstract Collection<AttributeDefinition> getAttributes();
}
