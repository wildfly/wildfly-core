/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.cli.command.aesh;

import java.io.IOException;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.readline.Prompt;
import org.aesh.readline.completion.Completion;
import org.jboss.as.cli.CommandContext;

/**
 * CLI specific {@code CommandInvocation} that exposes
 * {@link org.jboss.as.cli.CommandContext}.
 *
 * @author jdenise@redhat.com
 */
public interface CLICommandInvocation extends CommandInvocation {
    CommandContext getCommandContext();

    String inputLine(Prompt prompt, Completion completer) throws InterruptedException, IOException;
}
