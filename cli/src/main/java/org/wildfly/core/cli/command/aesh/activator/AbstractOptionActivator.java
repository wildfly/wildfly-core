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
package org.wildfly.core.cli.command.aesh.activator;

import org.jboss.as.cli.CommandContext;

/**
 * Base class to develop a CLI command activator. It implements
 * {@link org.wildfly.core.cli.command.aesh.activator.CLIOptionActivator}.
 *
 * @author jfdenise
 */
public abstract class AbstractOptionActivator implements CLIOptionActivator {

    private CommandContext ctx;

    protected AbstractOptionActivator() {
    }

    @Override
    public void setCommandContext(CommandContext commandContext) {
        this.ctx = commandContext;
    }

    @Override
    public CommandContext getCommandContext() {
        return ctx;
    }
}
