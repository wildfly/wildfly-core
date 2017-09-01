/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017, Red Hat, Inc., and individual contributors
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
package org.jboss.as.cli.impl.aesh.commands;

import java.io.IOException;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;
import org.jboss.as.cli.Util;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.cli.command.aesh.activator.HideOptionActivator;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "version", description = "")
public class VersionCommand implements Command<CLICommandInvocation> {

    @Deprecated
    @Option(hasValue = false, activator = HideOptionActivator.class)
    private boolean help;

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        if (help) {
            commandInvocation.println(commandInvocation.getHelpInfo("version"));
            return CommandResult.SUCCESS;
        }
        final StringBuilder buf = new StringBuilder();
        buf.append("JBoss Admin Command-line Interface\n");
        buf.append("JBOSS_HOME: ").append(WildFlySecurityManager.getEnvPropertyPrivileged("JBOSS_HOME", null)).append('\n');
        buf.append("Release: ");
        final ModelControllerClient client = commandInvocation.getCommandContext().getModelControllerClient();
        if (client == null) {
            buf.append("<connect to the controller and re-run the version command to see the release info>\n");
        } else {
            final ModelNode req = new ModelNode();
            req.get(Util.OPERATION).set(Util.READ_RESOURCE);
            req.get(Util.ADDRESS).setEmptyList();
            try {
                final ModelNode response = client.execute(req);
                if (Util.isSuccess(response)) {
                    if (response.hasDefined(Util.RESULT)) {
                        final ModelNode result = response.get(Util.RESULT);
                        byte flag = 0;
                        if (result.hasDefined(Util.RELEASE_VERSION)) {
                            buf.append(result.get(Util.RELEASE_VERSION).asString());
                            ++flag;
                        }
                        if (result.hasDefined(Util.RELEASE_CODENAME)) {
                            String codename = result.get(Util.RELEASE_CODENAME).asString();
                            if (codename.length() > 0) {
                                buf.append(" \"").append(codename).append('\"');
                                ++flag;
                            }
                        }
                        if (flag == 0) {
                            buf.append("release info was not provided by the controller");
                        }

                        if (result.hasDefined(Util.PRODUCT_NAME)) {
                            buf.append("\nProduct: ").append(result.get(Util.PRODUCT_NAME).asString());
                            if (result.hasDefined(Util.PRODUCT_VERSION)) {
                                buf.append(' ').append(result.get(Util.PRODUCT_VERSION).asString());
                            }
                        }
                    } else {
                        buf.append("result was not available.");
                    }
                } else {
                    buf.append(Util.getFailureDescription(response));
                }
                buf.append('\n');
            } catch (IOException e) {
                throw new CommandException("Failed to get the AS release info: " + e.getLocalizedMessage());
            }
        }
        buf.append("JAVA_HOME: ").append(WildFlySecurityManager.getEnvPropertyPrivileged("JAVA_HOME", null)).append('\n');
        buf.append("java.version: ").append(WildFlySecurityManager.getPropertyPrivileged("java.version", null)).append('\n');
        buf.append("java.vm.vendor: ").append(WildFlySecurityManager.getPropertyPrivileged("java.vm.vendor", null)).append('\n');
        buf.append("java.vm.version: ").append(WildFlySecurityManager.getPropertyPrivileged("java.vm.version", null)).append('\n');
        buf.append("os.name: ").append(WildFlySecurityManager.getPropertyPrivileged("os.name", null)).append('\n');
        buf.append("os.version: ").append(WildFlySecurityManager.getPropertyPrivileged("os.version", null));
        commandInvocation.println(buf.toString());
        return CommandResult.SUCCESS;
    }
}
