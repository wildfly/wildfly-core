/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.cli.command;

import org.jboss.as.cli.Attachments;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.dmr.ModelNode;

/**
 * Command that implements this interface can build the associated DMR request.
 *
 * @author jdenise@redhat.com
 */
public interface DMRCommand {
    ModelNode buildRequest(CommandContext context) throws CommandFormatException;

    default ModelNode buildRequest(CommandContext context,
            Attachments attachments) throws CommandFormatException {
        return buildRequest(context);
    }
}
