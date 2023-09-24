/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.accesscontrol;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.controller.client.ModelControllerClient;

/**
 * @author Alexey Loubyansky
 *
 */
public class MainOperationAccessRequirement extends BaseOperationAccessRequirement {


    MainOperationAccessRequirement(String operation) {
        super(operation);
    }

    MainOperationAccessRequirement(String address, String operation) {
        super(address, operation);
    }

    MainOperationAccessRequirement(OperationRequestAddress address, String operation) {
        super(address, operation);
    }

    @Override
    protected boolean checkAccess(CommandContext ctx) {
        final ModelControllerClient client = ctx.getModelControllerClient();
        if(client == null) {
            return false;
        }
        return CLIAccessControl.isExecute(client, address, operation);
    }
}
