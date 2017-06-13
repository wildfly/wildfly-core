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

import org.jboss.as.cli.impl.aesh.commands.deployment.security.Permissions;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.converter.Converter;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.aesh.command.validator.OptionValidatorException;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.aesh.commands.security.ControlledCommandActivator;
import org.jboss.as.cli.impl.aesh.commands.deployment.security.Activators.NameActivator;
import org.jboss.as.cli.impl.aesh.commands.deployment.security.Activators.UrlActivator;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.aesh.CLIConverterInvocation;

/**
 * XXX jfdenise, all fields are public to be accessible from legacy view. To be
 * made private when removed.
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "deploy-url", description = "", activator = ControlledCommandActivator.class)
public class DeployUrlCommand extends AbstractDeployContentCommand {

    public static class UrlConverter implements Converter<URL, CLIConverterInvocation> {

        @Override
        public URL convert(CLIConverterInvocation c) throws OptionValidatorException {
            try {
                return new URL(c.getInput());
            } catch (MalformedURLException e) {
                throw new OptionValidatorException(e.getLocalizedMessage());
            }
        }

    }

    @Option(hasValue = true, required = false, completer
            = EnableCommand.NameCompleter.class,
            activator = NameActivator.class)
    public String name;

    @Argument(required = true, activator = UrlActivator.class,
            converter = UrlConverter.class)
    public URL deploymentUrl;

    public DeployUrlCommand(CommandContext ctx, Permissions permissions) {
        super(ctx, permissions);
    }

    @Deprecated
    public DeployUrlCommand(CommandContext ctx) {
        this(ctx, null);
    }

    @Override
    protected void checkArgument() throws CommandException {
        if (deploymentUrl == null) {
            throw new CommandException("No deployment url");
        }
    }

    @Override
    protected String getName() {
        if (name != null) {
            return name;
        }
        String name = deploymentUrl.getPath();
        // strip trailing slash if present
        if (name.charAt(name.length() - 1) == '/') {
            name = name.substring(0, name.length() - 1);
        }
        // take only last element of the path
        if (name.lastIndexOf('/') > -1) {
            name = name.substring(name.lastIndexOf('/') + 1);
        }
        return name;
    }

    @Override
    protected void addContent(CommandContext ctx, ModelNode content) throws OperationFormatException {
        content.get(Util.URL).set(deploymentUrl.toExternalForm());
    }

    @Override
    protected String getCommandName() {
        return "deploy-url";
    }

    @Override
    protected ModelNode execute(CommandContext ctx, ModelNode request)
            throws IOException {
        return ctx.getModelControllerClient().execute(request);
    }
}
