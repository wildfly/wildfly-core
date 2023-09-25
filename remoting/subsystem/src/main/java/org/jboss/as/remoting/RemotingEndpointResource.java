/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.remoting;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2013 Red Hat Inc.
 */
class RemotingEndpointResource extends SimpleResourceDefinition {


    static final PathElement ENDPOINT_PATH = PathElement.pathElement("configuration", "endpoint");
    static final Map<String, AttributeDefinition> ATTRIBUTES;

    static final RemotingEndpointResource INSTANCE = new RemotingEndpointResource();

    static {
        Map<String, AttributeDefinition> attrs = new LinkedHashMap<>();
        assert RemotingSubsystemRootResource.WORKER.getName().equals(RemotingSubsystemRootResource.WORKER.getXmlName());
        attrs.put(RemotingSubsystemRootResource.WORKER.getName(), RemotingSubsystemRootResource.WORKER);
        for (AttributeDefinition ad : RemotingSubsystemRootResource.OPTIONS) {
            assert ad.getName().equals(ad.getXmlName());
            attrs.put(ad.getName(), ad);
        }
        ATTRIBUTES = Collections.unmodifiableMap(attrs);
    }

    private RemotingEndpointResource() {
        super(new Parameters(ENDPOINT_PATH, RemotingExtension.getResourceDescriptionResolver("endpoint"))
                .setAddHandler(new RemotingEndpointAdd())
                .setRemoveHandler(new RemotingEndpointRemove())
                .setAddRestartLevel(OperationEntry.Flag.RESTART_ALL_SERVICES) // if this is added post-boot we assume it will trigger a restart required, although in rare cases it might not
                .setRemoveRestartLevel(OperationEntry.Flag.RESTART_ALL_SERVICES) // we assume it will trigger a restart required, although in rare cases it might not
                .setDeprecatedSince(ModelVersion.create(5))
        );
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        OperationStepHandler toParentHandler = new ToParentHandler();
        for (AttributeDefinition ad : ATTRIBUTES.values()) {
            resourceRegistration.registerReadWriteAttribute(ad, toParentHandler, toParentHandler);
        }
    }

    private static class ToParentHandler implements OperationStepHandler {

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            PathAddress parent = context.getCurrentAddress().getParent();
            ModelNode parentOp = operation.clone();
            parentOp.get(OP_ADDR).set(parent.toModelNode());

            OperationStepHandler osh = context.getRootResourceRegistration().getOperationHandler(parent, operation.get(OP).asString());
            context.addStep(parentOp, osh, OperationContext.Stage.MODEL, true);
        }
    }
}
