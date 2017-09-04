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

import org.wildfly.core.cli.command.aesh.activator.CLIOptionActivator;
import org.aesh.command.activator.OptionActivator;
import org.aesh.command.activator.OptionActivatorProvider;
import org.jboss.as.cli.impl.CommandContextImpl;

/**
 * A CLI specific {@code OptionActivatorProvider} used to set
 * {@link org.jboss.as.cli.CommandContext} on {@code CLIOptionActivator}
 * instance.
 *
 * @author jdenise@redhat.com
 */
public class CLIOptionActivatorProvider implements OptionActivatorProvider {

    private final CommandContextImpl commandContext;

    public CLIOptionActivatorProvider(CommandContextImpl commandContext) {
        this.commandContext = commandContext;
    }

    @Override
    public OptionActivator enhanceOptionActivator(OptionActivator optionActivator) {

        if (optionActivator instanceof CLIOptionActivator) {
            ((CLIOptionActivator) optionActivator).setCommandContext(commandContext.newTimeoutCommandContext());
        }

        return optionActivator;
    }
}
