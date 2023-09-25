/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh;

import org.aesh.command.completer.CompleterInvocation;
import org.wildfly.core.cli.command.aesh.CLICompleterInvocation;
import org.aesh.command.completer.CompleterInvocationProvider;
import org.jboss.as.cli.impl.CommandContextImpl;

/**
 * A CLI specific {@code CompleterInvocationProvider} that creates
 * {@code CLICompleterInvocation}.
 *
 * @author jdenise@redhat.com
 */
public class CLICompleterInvocationProvider implements CompleterInvocationProvider<CLICompleterInvocation> {

    private final CommandContextImpl ctx;

    public CLICompleterInvocationProvider(CommandContextImpl ctx) {
        this.ctx = ctx;
    }

    @Override
    public CLICompleterInvocation enhanceCompleterInvocation(CompleterInvocation completerInvocation) {
        return new CLICompleterInvocation(completerInvocation, ctx.newTimeoutCommandContext());
    }
}
