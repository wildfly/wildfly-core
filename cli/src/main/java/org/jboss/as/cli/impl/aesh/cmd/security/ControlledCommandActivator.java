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
package org.jboss.as.cli.impl.aesh.cmd.security;

import java.io.IOException;
import org.aesh.command.impl.internal.ProcessedCommand;
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
    public boolean isActivated(ProcessedCommand cmd) {
        if (!super.isActivated(cmd)) {
            return false;
        }
        AbstractControlledCommand controlled = (AbstractControlledCommand) cmd.getCommand();
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
