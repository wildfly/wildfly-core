/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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
