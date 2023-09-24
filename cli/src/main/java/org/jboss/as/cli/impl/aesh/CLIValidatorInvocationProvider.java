/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh;

import org.aesh.command.validator.ValidatorInvocation;
import org.wildfly.core.cli.command.aesh.CLIValidatorInvocation;
import org.aesh.command.validator.ValidatorInvocationProvider;
import org.jboss.as.cli.impl.CommandContextImpl;

/**
 * A CLI specific {@code ValidatorInvocationProvider} that creates {@link
 * org.wildfly.core.cli.command.aesh.CLIValidatorInvocation}.
 *
 * @author jdenise@redhat.com
 */
public class CLIValidatorInvocationProvider implements ValidatorInvocationProvider<CLIValidatorInvocation> {

    private final CommandContextImpl context;

    public CLIValidatorInvocationProvider(CommandContextImpl context) {
        this.context = context;
    }

    @Override
    public CLIValidatorInvocation enhanceValidatorInvocation(ValidatorInvocation validatorInvocation) {
        return new CLIValidatorInvocationImpl(context.newTimeoutCommandContext(), validatorInvocation.getValue(),
                validatorInvocation.getAeshContext(), validatorInvocation.getCommand());
    }
}
