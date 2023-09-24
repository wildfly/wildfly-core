/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.batch;

import org.jboss.as.cli.handlers.ResponseHandler;
import org.jboss.as.cli.CommandContext;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author Alexey Loubyansky
 */
public interface BatchedCommand {

    String getCommand();

    ModelNode getRequest();

    ModelNode getDescriptionResponse();

    CommandContext getCommandContext();

    ResponseHandler getResponseHandler();
}
