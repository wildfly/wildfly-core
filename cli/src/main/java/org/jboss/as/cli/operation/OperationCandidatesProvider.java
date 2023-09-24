/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.operation;

import java.util.Collection;
import java.util.Map;

import org.jboss.as.cli.CommandArgument;
import org.jboss.as.cli.CommandContext;


/**
 * Provider of candidates for the tab completion.
 *
 * @author Alexey Loubyansky
 */
public interface OperationCandidatesProvider {

    Collection<String> getNodeNames(CommandContext ctx, OperationRequestAddress prefix);

    Collection<String> getNodeTypes(CommandContext ctx, OperationRequestAddress prefix);

    Collection<String> getOperationNames(CommandContext ctx, OperationRequestAddress prefix);

    Collection<CommandArgument> getProperties(CommandContext ctx, String operationName, OperationRequestAddress address);

    Map<String, OperationRequestHeader> getHeaders(CommandContext ctx);
}
