/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.cli.impl.aesh.commands.deployment;

import org.jboss.as.cli.impl.aesh.commands.deployment.security.Activators;
import org.jboss.as.cli.impl.aesh.commands.deployment.security.Permissions;
import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Argument;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.impl.aesh.commands.security.ControlledCommandActivator;

/**
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "undeploy", description = "", activator = ControlledCommandActivator.class)
public class UndeployCommand extends AbstractUndeployCommand {

    // Argument comes first, aesh behavior.
    @Argument(required = true, activator = Activators.UndeployNameActivator.class,
            completer = EnableCommand.NameCompleter.class)
    public String name;

    // XXX jfdenise, is public for compat reason. Make it private when removing compat code.
    public UndeployCommand(CommandContext ctx, Permissions permissions) {
        super(ctx, permissions);
    }

    @Deprecated
    public UndeployCommand(CommandContext ctx) {
        this(ctx, null);
    }

    @Override
    protected boolean keepContent() {
        return false;
    }

    @Override
    protected String getName() {
        return name;
    }

    @Override
    protected String getCommandName() {
        return "undeploy";
    }
}
