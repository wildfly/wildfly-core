/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.mgmt;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NATIVE_REMOTING_INTERFACE;

import java.util.List;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.server.controller.descriptions.ServerDescriptions;
import org.jboss.as.server.operations.NativeRemotingManagementAddHandler;
import org.jboss.as.server.operations.NativeRemotingManagementRemoveHandler;

/**
 * {@link org.jboss.as.controller.ResourceDefinition} for the Native Remoting Interface when running a standalone server.
 * (This reuses a connector from the remoting subsystem).
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class NativeRemotingManagementResourceDefinition extends SimpleResourceDefinition {

    private static final PathElement RESOURCE_PATH = PathElement.pathElement(MANAGEMENT_INTERFACE, NATIVE_REMOTING_INTERFACE);

    public static final NativeRemotingManagementResourceDefinition INSTANCE = new NativeRemotingManagementResourceDefinition();

    private final List<AccessConstraintDefinition> accessConstraints;

    private NativeRemotingManagementResourceDefinition() {
        super(new Parameters(RESOURCE_PATH, ServerDescriptions.getResourceDescriptionResolver("core.management.native-remoting-interface"))
            .setAddHandler(NativeRemotingManagementAddHandler.INSTANCE)
            .setRemoveHandler(NativeRemotingManagementRemoveHandler.INSTANCE)
            .setAddRestartLevel(OperationEntry.Flag.RESTART_NONE)
            .setRemoveRestartLevel(OperationEntry.Flag.RESTART_NONE));
        this.accessConstraints = SensitiveTargetAccessConstraintDefinition.MANAGEMENT_INTERFACES.wrapAsList();
        setDeprecated(ModelVersion.create(1, 7));
    }

    @Override
    public List<AccessConstraintDefinition> getAccessConstraints() {
        return accessConstraints;
    }
}
