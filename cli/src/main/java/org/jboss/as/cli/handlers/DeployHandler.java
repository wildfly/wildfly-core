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
package org.jboss.as.cli.handlers;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.aesh.command.CommandException;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.accesscontrol.AccessRequirement;
import org.jboss.as.cli.accesscontrol.AccessRequirementBuilder;
import org.jboss.as.cli.accesscontrol.PerNodeOperationAccess;
import org.jboss.as.cli.impl.ArgumentWithListValue;
import org.jboss.as.cli.impl.ArgumentWithValue;
import org.jboss.as.cli.impl.ArgumentWithoutValue;
import org.jboss.as.cli.impl.CommaSeparatedCompleter;
import org.jboss.as.cli.impl.FileSystemPathArgument;
import org.jboss.as.cli.impl.aesh.cmd.LegacyBridge;
import org.jboss.as.cli.impl.aesh.cmd.deployment.DeployArchiveCommand;
import org.jboss.as.cli.impl.aesh.cmd.deployment.DeployFileCommand;
import org.jboss.as.cli.impl.aesh.cmd.deployment.DeployUrlCommand;
import org.jboss.as.cli.impl.aesh.cmd.deployment.EnableAllCommand;
import org.jboss.as.cli.impl.aesh.cmd.deployment.EnableCommand;
import org.jboss.as.cli.impl.aesh.cmd.deployment.ListCommand;
import org.jboss.as.cli.impl.aesh.cmd.deployment.security.AccessRequirements;
import org.jboss.as.cli.impl.aesh.cmd.deployment.security.Permissions;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestAddress;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.core.cli.command.DMRCommand;

/**
 *
 * @author Alexey Loubyansky
 */
@Deprecated
public class DeployHandler extends DeploymentHandler {

    private final ArgumentWithoutValue force;
    private final ArgumentWithoutValue l;
    private final ArgumentWithoutValue path;
    private final ArgumentWithoutValue url;
    private final ArgumentWithoutValue name;
    private final ArgumentWithoutValue rtName;
    private final ArgumentWithListValue serverGroups;
    private final ArgumentWithoutValue allServerGroups;
    private final ArgumentWithoutValue disabled;
    private final ArgumentWithoutValue enabled;
    private final ArgumentWithoutValue unmanaged;
    private final ArgumentWithValue script;

    private AccessRequirement listPermission;
    private AccessRequirement fullReplacePermission;
    private AccessRequirement mainAddPermission;
    private AccessRequirement deployPermission;
    private PerNodeOperationAccess serverGroupAddPermission;

    private static final String ALL = "*";

    private static final String REPLACE_OPTION = "force";

    public DeployHandler(CommandContext ctx) {
        super(ctx, "deploy", true);

        final DefaultOperationRequestAddress requiredAddress = new DefaultOperationRequestAddress();
        requiredAddress.toNodeType(Util.DEPLOYMENT);
        addRequiredPath(requiredAddress);

        l = new ArgumentWithoutValue(this, "-l");
        l.setExclusive(true);
        l.setAccessRequirement(listPermission);

        final AccessRequirement addOrReplacePermission = AccessRequirementBuilder.Factory.create(ctx)
                .any()
                .requirement(mainAddPermission)
                .requirement(fullReplacePermission)
                .build();

        final FilenameTabCompleter pathCompleter = FilenameTabCompleter.newCompleter(ctx);
        path = new FileSystemPathArgument(this, pathCompleter, 0, "--path");
        path.addCantAppearAfter(l);
        path.setAccessRequirement(addOrReplacePermission);

        url = new ArgumentWithValue(this, "--url");
        url.addCantAppearAfter(path);
        path.addCantAppearAfter(url);
        url.setAccessRequirement(addOrReplacePermission);

        force = new ArgumentWithoutValue(this, "--force", "-f");
        force.addRequiredPreceding(path);
        force.setAccessRequirement(fullReplacePermission);

        name = new ArgumentWithValue(this, new CommandLineCompleter() {
            @Override
            public int complete(CommandContext ctx, String buffer, int cursor, List<String> candidates) {

                ParsedCommandLine args = ctx.getParsedCommandLine();
                try {
                    if (path.isPresent(args) || url.isPresent(args)) {
                        return -1;
                    }
                } catch (CommandFormatException e) {
                    return -1;
                }

                int nextCharIndex = 0;
                while (nextCharIndex < buffer.length()) {
                    if (!Character.isWhitespace(buffer.charAt(nextCharIndex))) {
                        break;
                    }
                    ++nextCharIndex;
                }

                if (ctx.getModelControllerClient() != null) {
                    List<String> deployments = Util.getDeployments(ctx.getModelControllerClient());
                    if (deployments.isEmpty()) {
                        return -1;
                    }

                    String opBuffer = buffer.substring(nextCharIndex).trim();
                    if (opBuffer.isEmpty()) {
                        candidates.addAll(deployments);
                        candidates.add(ALL);
                    } else if (ALL.startsWith(opBuffer)) {
                        candidates.add(ALL + " ");
                    } else {
                        for (String name : deployments) {
                            if (name.startsWith(opBuffer)) {
                                candidates.add(name);
                            }
                        }
                        Collections.sort(candidates);
                    }
                    return nextCharIndex;
                } else {
                    return -1;
                }

            }
        }, "--name");
        name.addCantAppearAfter(l);
        path.addCantAppearAfter(name);
        url.addCantAppearAfter(name);
        name.setAccessRequirement(deployPermission);

        rtName = new ArgumentWithValue(this, "--runtime-name");
        rtName.addRequiredPreceding(path);
        rtName.setAccessRequirement(addOrReplacePermission);

        allServerGroups = new ArgumentWithoutValue(this, "--all-server-groups") {
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
                if (!ctx.isDomainMode()) {
                    return false;
                }
                return super.canAppearNext(ctx);
            }
        };

        allServerGroups.addRequiredPreceding(path);
        allServerGroups.addRequiredPreceding(name);
        allServerGroups.addCantAppearAfter(force);
        force.addCantAppearAfter(allServerGroups);
        allServerGroups.setAccessRequirement(deployPermission);

        serverGroups = new ArgumentWithListValue(this, new CommaSeparatedCompleter() {
            @Override
            protected Collection<String> getAllCandidates(CommandContext ctx) {
                return serverGroupAddPermission.getAllowedOn(ctx);
            }
        }, "--server-groups") {
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
                if (!ctx.isDomainMode()) {
                    return false;
                }
                return super.canAppearNext(ctx);
            }
        };
        serverGroups.addRequiredPreceding(path);
        serverGroups.addRequiredPreceding(name);
        serverGroups.addCantAppearAfter(force);
        force.addCantAppearAfter(serverGroups);
        serverGroups.setAccessRequirement(deployPermission);

        serverGroups.addCantAppearAfter(allServerGroups);
        allServerGroups.addCantAppearAfter(serverGroups);

        disabled = new ArgumentWithoutValue(this, "--disabled");
        disabled.addRequiredPreceding(path);
        disabled.addCantAppearAfter(serverGroups);
        disabled.addCantAppearAfter(allServerGroups);
        if (ctx.isDomainMode()) {
            disabled.addCantAppearAfter(force);
            force.addCantAppearAfter(disabled);
        }
        disabled.setAccessRequirement(mainAddPermission);

        enabled = new ArgumentWithoutValue(this, "--enabled");
        enabled.addRequiredPreceding(path);
        enabled.addCantAppearAfter(serverGroups);
        enabled.addCantAppearAfter(allServerGroups);
        if (ctx.isDomainMode()) {
            enabled.addCantAppearAfter(force);
            enabled.addCantAppearAfter(disabled);
            disabled.addCantAppearAfter(enabled);
            force.addCantAppearAfter(enabled);
        }
        enabled.setAccessRequirement(mainAddPermission);

        unmanaged = new ArgumentWithoutValue(this, "--unmanaged");
        unmanaged.addRequiredPreceding(path);
        unmanaged.setAccessRequirement(mainAddPermission);

        script = new ArgumentWithValue(this, "--script");
        script.addRequiredPreceding(path);
    }

    @Override
    protected AccessRequirement setupAccessRequirement(CommandContext ctx) {
        Permissions permissions = new Permissions(ctx);
        listPermission = permissions.getListPermission();
        fullReplacePermission = permissions.getFullReplacePermission();
        mainAddPermission = permissions.getMainAddPermission();
        serverGroupAddPermission = permissions.getServerGroupAddPermission();
        deployPermission = permissions.getDeployPermission();

        return AccessRequirements.deploymentAccess(permissions).apply(ctx);
    }

    @Override
    protected void doHandle(CommandContext ctx) throws CommandLineException {
        ParsedCommandLine args = ctx.getParsedCommandLine();
        boolean l = this.l.isPresent(args);
        if (!args.hasProperties() || l) {
            try {
                ListCommand.listDeployments(ctx, l);
            } catch (CommandException ex) {
                throw new CommandLineException(ex.getLocalizedMessage());
            }
            return;
        }

        final boolean unmanaged = this.unmanaged.isPresent(args);

        final String path = this.path.getValue(args);
        final String name = this.name.getValue(args);
        final String rtName = this.rtName.getValue(args);
        final String url = this.url.getValue(args);
        final boolean force = this.force.isPresent(args);
        final boolean disabled = this.disabled.isPresent(args);
        final boolean enabled = this.enabled.isPresent(args);
        final String serverGroups = this.serverGroups.getValue(args);
        final boolean allServerGroups = this.allServerGroups.isPresent(args);

        final ModelNode headersNode = headers.toModelNode(ctx);
        if (headersNode != null && headersNode.getType() != ModelType.OBJECT) {
            throw new CommandFormatException("--headers option has wrong value '" + headersNode + "'");
        }
        if (path == null && url == null) {
            if (name == null) {
                throw new CommandLineException("Filesystem path, --url or --name is "
                        + " required.");
            }
            if (name.equals(ALL)) {
                if (force || disabled) {
                    throw new CommandLineException("force and disabled can't be used "
                            + "when deploying all disabled deployments");
                }
                EnableAllCommand command = new EnableAllCommand(ctx);
                command.allServerGroups = allServerGroups;
                command.headers = headersNode;
                command.serverGroups = serverGroups;
                try {
                    command.execute(ctx);
                } catch (CommandException ex) {
                    throw new CommandLineException(ex.getLocalizedMessage());
                }
                return;

            } else {
                EnableCommand command = new EnableCommand(ctx);
                command.allServerGroups = allServerGroups;
                command.headers = headersNode;
                command.serverGroups = serverGroups;
                command.name = name;
                try {
                    command.execute(ctx);
                } catch (CommandException ex) {
                    throw new CommandLineException(ex.getLocalizedMessage());
                }
                return;
            }
        }

        if (path != null) {
            if (url != null) {
                throw new CommandLineException("Filesystem path and --url can't be used together.");
            }
            File f = new File(path);
            LegacyBridge c;
            if (DeployArchiveCommand.isCliArchive(f)) {
                DeployArchiveCommand command = new DeployArchiveCommand(ctx);
                command.file = f;
                command.script = this.script.getValue(args);
                c = command;
            } else {
                DeployFileCommand command = new DeployFileCommand(ctx, REPLACE_OPTION);
                command.allServerGroups = allServerGroups;
                command.disabled = disabled;
                command.enabled = enabled;
                command.file = f;
                command.replace = force;
                command.headers = headersNode;
                command.name = name;
                command.runtimeName = rtName;
                command.serverGroups = serverGroups;
                command.unmanaged = unmanaged;
                c = command;
            }
            try {
                c.execute(ctx);
            } catch (CommandException ex) {
                throw new CommandLineException(ex.getLocalizedMessage());
            }
            return;
        }

        if (url != null) {
            if (path != null) {
                throw new CommandLineException("Filesystem path and --url can't be "
                        + "used together.");
            }
            DeployUrlCommand command = new DeployUrlCommand(ctx, REPLACE_OPTION);
            command.allServerGroups = allServerGroups;
            command.disabled = disabled;
            command.enabled = enabled;
            try {
                command.deploymentUrl = new URL(url);
            } catch (MalformedURLException ex) {
                throw new CommandLineException(ex);
            }
            command.replace = force;
            command.headers = headersNode;
            command.runtimeName = rtName;
            command.serverGroups = serverGroups;

            try {
                command.execute(ctx);
            } catch (CommandException ex) {
                throw new CommandLineException(ex.getLocalizedMessage());
            }
        }
    }

    @Override
    public ModelNode buildRequestWithoutHeaders(CommandContext ctx) throws CommandFormatException {
        ParsedCommandLine args = ctx.getParsedCommandLine();
        boolean l = this.l.isPresent(args);
        if (!args.hasProperties() || l) {
            throw new CommandFormatException("No Option");
        }

        final boolean unmanaged = this.unmanaged.isPresent(args);

        final String path = this.path.getValue(args);
        final String name = this.name.getValue(args);
        final String rtName = this.rtName.getValue(args);
        final String url = this.url.getValue(args);
        final boolean force = this.force.isPresent(args);
        final boolean disabled = this.disabled.isPresent(args);
        final boolean enabled = this.enabled.isPresent(args);
        final String serverGroups = this.serverGroups.getValue(args);
        final boolean allServerGroups = this.allServerGroups.isPresent(args);

        final ModelNode headersNode = headers.toModelNode(ctx);
        if (headersNode != null && headersNode.getType() != ModelType.OBJECT) {
            throw new CommandFormatException("--headers option has wrong value '" + headersNode + "'");
        }

        if (path == null && url == null) {
            if (name == null) {
                throw new CommandFormatException("Filesystem path, --url or --name is "
                        + " required.");
            }

            if (name.equals(ALL)) {
                if (force || disabled) {
                    throw new CommandFormatException("force and disabled can't be used "
                            + "when deploying all disabled deployments");
                }
                EnableAllCommand command = new EnableAllCommand(ctx);
                command.allServerGroups = allServerGroups;
                command.headers = headersNode;
                command.serverGroups = serverGroups;
                return command.buildRequest(ctx);
            } else {
                EnableCommand command = new EnableCommand(ctx);
                command.allServerGroups = allServerGroups;
                command.headers = headersNode;
                command.serverGroups = serverGroups;
                command.name = name;
                return command.buildRequest(ctx);
            }
        }

        if (path != null) {
            if (url != null) {
                throw new CommandFormatException("Filesystem path and --url can't be used together.");
            }
            File f = new File(path);
            DMRCommand c;
            if (DeployArchiveCommand.isCliArchive(f)) {
                DeployArchiveCommand command = new DeployArchiveCommand(ctx);
                command.file = f;
                command.script = this.script.getValue(args);
                c = command;
            } else {
                DeployFileCommand command = new DeployFileCommand(ctx, REPLACE_OPTION);
                command.allServerGroups = allServerGroups;
                command.disabled = disabled;
                command.enabled = enabled;
                command.file = f;
                command.replace = force;
                command.headers = headersNode;
                command.name = name;
                command.runtimeName = rtName;
                command.serverGroups = serverGroups;
                command.unmanaged = unmanaged;
                c = command;
            }
            return c.buildRequest(ctx);
        }

        if (url != null) {
            if (path != null) {
                throw new CommandFormatException("Filesystem path and --url can't be "
                        + "used together.");
            }
            DeployUrlCommand command = new DeployUrlCommand(ctx, REPLACE_OPTION);
            command.allServerGroups = allServerGroups;
            command.disabled = disabled;
            command.enabled = enabled;
            try {
                command.deploymentUrl = new URL(url);
            } catch (MalformedURLException ex) {
                throw new CommandFormatException(ex);
            }
            command.replace = force;
            command.headers = headersNode;
            command.runtimeName = rtName;
            command.serverGroups = serverGroups;
            return command.buildRequest(ctx);
        }
        throw new CommandFormatException("Invalid Options.");
    }
}
