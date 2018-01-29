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
