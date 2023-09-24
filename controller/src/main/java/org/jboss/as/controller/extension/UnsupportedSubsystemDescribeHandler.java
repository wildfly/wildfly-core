/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.extension;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.dmr.ModelNode;

/**
 * Handler for the "describe" operation for an extension that is only supported for non-server
 * use in a mixed-version domain where legacy slaves still support the extension.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public class UnsupportedSubsystemDescribeHandler extends GenericSubsystemDescribeHandler {

    private final String extensionName;

    public UnsupportedSubsystemDescribeHandler(String extensionName) {
        this.extensionName = extensionName;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        if (context.getAttachment(SERVER_LAUNCH_KEY) != null) {
            throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.unsupportedLegacyExtension(extensionName));
        }

        super.execute(context, operation);
    }
}
