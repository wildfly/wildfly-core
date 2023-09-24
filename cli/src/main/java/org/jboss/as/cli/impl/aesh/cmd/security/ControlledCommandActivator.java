/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd.security;

import java.io.IOException;
import org.aesh.command.impl.internal.ParsedCommand;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.aesh.cmd.ConnectedActivator;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.dmr.ModelNode;

/**
 * A command activator that enables the command only if the security constraints
 * are full-filled.
 *
 * @author jdenise@redhat.com
 */
public class ControlledCommandActivator extends ConnectedActivator {

    @Override
    public boolean isActivated(ParsedCommand cmd) {
        if (!super.isActivated(cmd)) {
            return false;
        }
        AbstractControlledCommand controlled = (AbstractControlledCommand) cmd.command();
        CommandContext ctx = getCommandContext();
        if (controlled.isDependsOnProfile() && ctx.isDomainMode()) { // not checking address in all the profiles
            return ctx.getConfig().isAccessControl()
                    ? controlled.getAccessRequirement().isSatisfied(ctx) : true;
        }

        boolean available;
        if (controlled.getRequiredType() == null) {
            available = isAddressValid(ctx, controlled.getRequiredAddress());
        } else {
            final ModelNode request = new ModelNode();
            final ModelNode address = request.get(Util.ADDRESS);
            for (OperationRequestAddress.Node node : controlled.getRequiredAddress()) {
                address.add(node.getType(), node.getName());
            }
            request.get(Util.OPERATION).set(Util.READ_CHILDREN_TYPES);
            ModelNode result;
            try {
                result = ctx.getModelControllerClient().execute(request);
            } catch (IOException e) {
                return false;
            }
            available = Util.listContains(result, controlled.getRequiredType());
        }

        if (ctx.getConfig().isAccessControl()) {
            available = available && controlled.getAccessRequirement().isSatisfied(ctx);
        }
        return available;
    }

    protected boolean isAddressValid(CommandContext ctx,
            OperationRequestAddress requiredAddress) {
        final ModelNode request = new ModelNode();
        final ModelNode address = request.get(Util.ADDRESS);
        address.setEmptyList();
        request.get(Util.OPERATION).set(Util.VALIDATE_ADDRESS);
        final ModelNode addressValue = request.get(Util.VALUE);
        for (OperationRequestAddress.Node node : requiredAddress) {
            addressValue.add(node.getType(), node.getName());
        }
        final ModelNode response;
        try {
            response = ctx.getModelControllerClient().execute(request);
        } catch (IOException e) {
            return false;
        }
        final ModelNode result = response.get(Util.RESULT);
        if (!result.isDefined()) {
            return false;
        }
        final ModelNode valid = result.get(Util.VALID);
        if (!valid.isDefined()) {
            return false;
        }
        return valid.asBoolean();
    }
}
