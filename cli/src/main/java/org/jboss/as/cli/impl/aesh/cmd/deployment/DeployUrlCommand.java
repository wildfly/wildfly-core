/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd.deployment;

import org.jboss.as.cli.impl.aesh.cmd.deployment.security.Permissions;
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
import org.jboss.as.cli.impl.aesh.cmd.security.ControlledCommandActivator;
import org.jboss.as.cli.impl.aesh.cmd.deployment.security.OptionActivators.NameActivator;
import org.jboss.as.cli.impl.aesh.cmd.deployment.security.OptionActivators.UrlActivator;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.aesh.CLIConverterInvocation;

/**
 * Deploy an URL. All fields are public to be accessible from legacy commands.
 * To be made private when legacies are removed.
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
    public DeployUrlCommand(CommandContext ctx, String replaceName) {
        super(ctx, null, replaceName);
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
