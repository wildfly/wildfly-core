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
package org.jboss.as.cli.impl.aesh.cmd.deployment;

import org.jboss.as.cli.impl.aesh.cmd.deployment.security.Permissions;
import java.io.IOException;
import java.util.List;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.aesh.cmd.LegacyBridge;
import org.jboss.as.cli.impl.aesh.cmd.deployment.security.AccessRequirements;
import org.jboss.as.cli.impl.aesh.cmd.deployment.security.OptionActivators.DisabledActivator;
import org.jboss.as.cli.impl.aesh.cmd.deployment.security.OptionActivators.EnabledActivator;
import org.jboss.as.cli.impl.aesh.cmd.deployment.security.OptionActivators.ReplaceActivator;
import org.jboss.as.cli.impl.aesh.cmd.deployment.security.OptionActivators.RuntimeNameActivator;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.cli.command.aesh.activator.HideOptionActivator;

/**
 * Base class for deployment commands that deplkoy some content (URL or file).
 * All fields are public to be accessible from legacy commands. To be made
 * private when legacies are removed.
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "deployment-deploy-content", description = "")
public abstract class AbstractDeployContentCommand extends AbstractDeployCommand implements LegacyBridge {

    @Deprecated
    @Option(hasValue = false, activator = HideOptionActivator.class)
    private boolean help;

    @Option(hasValue = false, required = false, activator = ReplaceActivator.class,
            shortName = 'r')
    public boolean replace;

    @Option(hasValue = false, required = false, activator = DisabledActivator.class)
    public boolean disabled;

    @Option(hasValue = false, required = false, activator = EnabledActivator.class)
    public boolean enabled;

    @Option(hasValue = true, name = "runtime-name", required = false, activator
            = RuntimeNameActivator.class)
    public String runtimeName;

    AbstractDeployContentCommand(CommandContext ctx,
            Permissions permissions) {
        super(ctx, AccessRequirements.deployContentAccess(permissions), permissions);
    }

    protected abstract void checkArgument() throws CommandException;

    protected abstract String getCommandName();

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation)
            throws CommandException, InterruptedException {
        if (help) {
            commandInvocation.println(commandInvocation.getHelpInfo("deploy "
                    + getCommandName()));
            return CommandResult.SUCCESS;
        }
        return execute(commandInvocation.getCommandContext());
    }

    @Override
    public CommandResult execute(CommandContext ctx)
            throws CommandException {
        checkArgument();
        try {
            ModelNode request = buildRequest(ctx);
            final ModelNode result = execute(ctx, request);
            if (!Util.isSuccess(result)) {
                throw new CommandException(Util.getFailureDescription(result));
            }
        } catch (IOException e) {
            throw new CommandException("Failed to deploy", e);
        } catch (CommandFormatException ex) {
            throw new CommandException(ex);
        }
        return CommandResult.SUCCESS;
    }

    protected abstract ModelNode execute(CommandContext ctx, ModelNode request)
            throws IOException;

    protected ModelNode buildDeploymentRequest(CommandContext ctx, String op)
            throws OperationFormatException {
        return buildDeploymentRequest(ctx, op, getName(), runtimeName, false);
    }

    private ModelNode buildDeploymentRequest(CommandContext ctx, String op,
            String name, final String runtimeName, boolean addHeaders) throws OperationFormatException {
        // replace
        final ModelNode request = new ModelNode();
        request.get(Util.OPERATION).set(op);
        request.get(Util.NAME).set(name);
        if (op.equals(Util.ADD)) { // replace is on root, add is on deployed artifact.
            request.get(Util.ADDRESS, Util.DEPLOYMENT).set(name);
        }
        if (runtimeName != null) {
            request.get(Util.RUNTIME_NAME).set(runtimeName);
        }
        final ModelNode content = request.get(Util.CONTENT).get(0);
        addContent(ctx, content);
        if (addHeaders && headers != null) {
            ModelNode opHeaders = request.get(Util.OPERATION_HEADERS);
            opHeaders.set(headers);
        }
        return request;
    }

    protected abstract void addContent(CommandContext ctx, ModelNode content)
            throws OperationFormatException;

    protected abstract String getName();

    @Override
    public ModelNode buildRequest(CommandContext context)
            throws CommandFormatException {
        // In case Batch or DMR call, must check that argument is valid.
        try {
            checkArgument();
        } catch (CommandException ex) {
            throw new CommandFormatException(ex);
        }
        CommandContext ctx = context;
        String name = getName();

        Boolean disable = null;
        if (disabled) {
            disable = true;
        } else if (enabled) {
            disable = false;
        }

        if (replace) {
            if (((disabled || enabled) && ctx.isDomainMode()) || serverGroups != null
                    || allServerGroups) {
                throw new CommandFormatException("--replace only replaces the content "
                        + "in the deployment repository and can't be used in combination with any of "
                        + "--enabled, --disabled, --server-groups or --all-server-groups.");
            }

            if (Util.isDeploymentInRepository(name, ctx.getModelControllerClient())) {
                ModelNode request = buildDeploymentRequest(ctx,
                        Util.FULL_REPLACE_DEPLOYMENT, name, runtimeName, true);
                if (disable != null) {
                    request.get(Util.ENABLED).set(!disable);
                }
                return request;
            } else if (ctx.isDomainMode()) {
                // add deployment to the repository (disabled in domain (i.e. not associated with any sg))
                ModelNode request = buildDeploymentRequest(ctx, Util.ADD,
                        name, runtimeName, true);
                return request;
            }
            // standalone mode will add and deploy
        }

        if (disabled) {
            if (serverGroups != null || allServerGroups) {
                throw new CommandFormatException("--server-groups and --all-server-groups "
                        + "can't be used in combination with --disabled.");
            }

            if (!ctx.isBatchMode() && Util.isDeploymentInRepository(name,
                    ctx.getModelControllerClient())) {
                throw new CommandFormatException("'" + name + "' already exists "
                        + "in the deployment repository (use --replace"
                        + " to replace the existing content in the repository).");
            }

            // add deployment to the repository disabled
            ModelNode request = buildDeploymentRequest(ctx, Util.ADD, name,
                    runtimeName, true);
            return request;
        }

        ModelNode deployRequest = new ModelNode();
        if (ctx.isDomainMode()) {
            final List<String> sgList = getServerGroups(ctx);
            deployRequest.get(Util.OPERATION).set(Util.COMPOSITE);
            deployRequest.get(Util.ADDRESS).setEmptyList();
            ModelNode steps = deployRequest.get(Util.STEPS);
            for (String serverGroup : sgList) {
                steps.add(Util.configureDeploymentOperation(Util.ADD, name,
                        serverGroup));
            }
            for (String serverGroup : sgList) {
                steps.add(Util.configureDeploymentOperation(Util.DEPLOY, name,
                        serverGroup));
            }
        } else {
            if (serverGroups != null || allServerGroups) {
                throw new CommandFormatException("--all-server-groups and --server-groups "
                        + "can't appear in standalone mode.");
            }
            deployRequest.get(Util.OPERATION).set(Util.DEPLOY);
            deployRequest.get(Util.ADDRESS, Util.DEPLOYMENT).set(name);
        }

        ModelNode compositeStep = createExtraStep(ctx);
        final ModelNode composite = new ModelNode();
        composite.get(Util.OPERATION).set(Util.COMPOSITE);
        composite.get(Util.ADDRESS).setEmptyList();
        final ModelNode steps = composite.get(Util.STEPS);
        steps.add(compositeStep);
        steps.add(deployRequest);
        if (headers != null) {
            ModelNode opHeaders = composite.get(Util.OPERATION_HEADERS);
            opHeaders.set(headers);
        }
        return composite;
    }

    protected List<String> getServerGroups(CommandContext ctx)
            throws CommandFormatException {
        return DeploymentCommand.getServerGroups(ctx, ctx.getModelControllerClient(),
                allServerGroups, serverGroups, null);
    }

    protected ModelNode createExtraStep(CommandContext ctx)
            throws CommandFormatException {
        if (!ctx.isBatchMode() && Util.isDeploymentInRepository(getName(),
                ctx.getModelControllerClient())) {
            throw new CommandFormatException("'" + getName() + "' already exists in "
                    + "the deployment repository (use "
                    + "--replace to replace the existing content in the repository).");
        }

        ModelNode request = buildDeploymentRequest(ctx, Util.ADD);
        request.get(Util.ADDRESS, Util.DEPLOYMENT).set(getName());
        if (ctx.isDomainMode()) {
            request.get(Util.ENABLED).set(true);
        }

        return request;
    }
}
