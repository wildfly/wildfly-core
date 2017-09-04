/*
Copyright 2017 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
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
