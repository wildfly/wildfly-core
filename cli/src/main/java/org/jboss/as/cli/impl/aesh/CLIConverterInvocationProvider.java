/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh;

import org.wildfly.core.cli.command.aesh.CLIConverterInvocation;
import org.aesh.command.converter.ConverterInvocation;
import org.aesh.command.converter.ConverterInvocationProvider;
import org.jboss.as.cli.impl.CommandContextImpl;

/**
 * A CLI specific {@code ConverterInvocationProvider} that creates {@link
 * org.wildfly.core.cli.command.aesh.CLIConverterInvocation}.
 *
 * @author jdenise@redhat.com
 */
public class CLIConverterInvocationProvider implements ConverterInvocationProvider<CLIConverterInvocation> {

    private final CommandContextImpl commandContext;

    public CLIConverterInvocationProvider(CommandContextImpl commandContext) {
        this.commandContext = commandContext;
    }

    @Override
    public CLIConverterInvocation enhanceConverterInvocation(ConverterInvocation converterInvocation) {
        return new CLIConverterInvocation(commandContext.newTimeoutCommandContext(),
                converterInvocation.getAeshContext(), converterInvocation.getInput());
    }
}
