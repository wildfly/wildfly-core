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
package org.jboss.as.cli.impl.aesh.cmd;

import java.io.IOException;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "version", description = "")
public class VersionCommand implements Command<CLICommandInvocation> {

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        CommandContext ctx = commandInvocation.getCommandContext();
        final StringBuilder buf = new StringBuilder();
        buf.append("JBoss Admin Command-line Interface\n");
        buf.append("JBOSS_HOME: ").append(WildFlySecurityManager.getEnvPropertyPrivileged("JBOSS_HOME", null)).append('\n');
        buf.append("Release: ");
        final ModelControllerClient client = ctx.getModelControllerClient();
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
        ctx.printLine(buf.toString());
        return CommandResult.SUCCESS;
    }

}
