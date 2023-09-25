/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers;

import java.util.Collection;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.accesscontrol.AccessRequirement;
import org.jboss.as.cli.accesscontrol.PerNodeOperationAccess;
import org.jboss.as.cli.impl.ArgumentWithValue;
import org.jboss.as.cli.impl.DefaultCompleter;
import org.jboss.as.cli.impl.DefaultCompleter.CandidatesProvider;
import org.jboss.as.cli.impl.aesh.cmd.deployment.InfoCommand;
import org.jboss.as.cli.impl.aesh.cmd.deployment.security.AccessRequirements;
import org.jboss.as.cli.impl.aesh.cmd.deployment.security.Permissions;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author Alexey Loubyansky
 */
@Deprecated
public class DeploymentInfoHandler extends BaseOperationCommand {

    private final ArgumentWithValue name;
    private final ArgumentWithValue serverGroup;
    private PerNodeOperationAccess sgChildrenResourcesPermission;
    private final InfoCommand ic;

    public DeploymentInfoHandler(CommandContext ctx) {
        super(ctx, "deployment-info", true);
        name = new ArgumentWithValue(this, new DefaultCompleter(new CandidatesProvider(){
            @Override
            public Collection<String> getAllCandidates(CommandContext ctx) {
                return Util.getDeployments(ctx.getModelControllerClient());
            }}), "--name");

        serverGroup = new ArgumentWithValue(this, new DefaultCompleter(new CandidatesProvider(){
            @Override
            public Collection<String> getAllCandidates(CommandContext ctx) {
                return sgChildrenResourcesPermission.getAllowedOn(ctx);
            }}), "--server-group") {
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
                if(!ctx.isDomainMode()) {
                    return false;
                }
                return super.canAppearNext(ctx);
            }
        };
        ic = new InfoCommand(ctx);
    }

    @Override
    protected AccessRequirement setupAccessRequirement(CommandContext ctx) {
        Permissions permissions = new Permissions(ctx);
        sgChildrenResourcesPermission = permissions.getSgChildrenResourcesPermission();
        return AccessRequirements.infoAccess(permissions).apply(ctx);
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.OperationCommand#buildRequest(org.jboss.as.cli.CommandContext)
     */
    @Override
    public ModelNode buildRequestWithoutHeaders(CommandContext ctx) throws CommandFormatException {
        final ParsedCommandLine parsedCmd = ctx.getParsedCommandLine();
        ic.deploymentName = null;
        ic.serverGroup = null;
        String deploymentName = name.getValue(parsedCmd);
        if (name != null) {
            ic.deploymentName = deploymentName;
        }
        ic.serverGroup = serverGroup.getValue(parsedCmd);
        return ic.buildRequest(ctx);
    }

    @Override
    protected void handleResponse(CommandContext ctx, ModelNode response, boolean composite) throws CommandFormatException {
        ic.handleResponse(ctx, response);
    }
}
