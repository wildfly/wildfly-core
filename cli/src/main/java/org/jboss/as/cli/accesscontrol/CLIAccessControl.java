/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.accesscontrol;

import java.util.Iterator;

import org.jboss.as.cli.Util;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.cli.operation.OperationRequestAddress.Node;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;

/**
 * @author Alexey Loubyansky
 *
 */
public class CLIAccessControl {

    private static final Logger log = Logger.getLogger(CLIAccessControl.class);

    public static boolean isExecute(ModelControllerClient client, OperationRequestAddress address, String operation) {
        return isExecute(client, null, address, operation);
    }

    public static boolean isExecute(ModelControllerClient client, String[] parent, OperationRequestAddress address, String operation) {
        final ModelNode accessControl = getAccessControl(client, parent, address, true);
        if(accessControl == null) {
            return false;
        }

        if(!accessControl.has(Util.DEFAULT)) {
            log.warnf("access-control is missing defaults: %s", accessControl);
            return false;
        }

        final ModelNode defaults = accessControl.get(Util.DEFAULT);
        if(!defaults.has(Util.OPERATIONS)) {
            log.warnf("access-control/default is missing operations: %s", defaults);
            return false;
        }

        final ModelNode operations = defaults.get(Util.OPERATIONS);
        if(!operations.has(operation)) {
            log.tracef("The operation is missing in the description: %s", operation);
            return false;
        }

        final ModelNode opAC = operations.get(operation);
        if(!opAC.has(Util.EXECUTE)) {
            log.warnf("'execute' is missing for %s in %s", operation, accessControl);
            return false;
        }

        return opAC.get(Util.EXECUTE).asBoolean();
    }

    /**
     * Executed read-resource-description and returns access-control info.
     * Returns null in case there was any kind of problem.
     *
     * @param client
     * @param address
     * @return
     */
    public static ModelNode getAccessControl(ModelControllerClient client, OperationRequestAddress address, boolean operations) {
        return getAccessControl(client, null, address, operations);
    }

    public static ModelNode getAccessControl(ModelControllerClient client, String[] parent, OperationRequestAddress address, boolean operations) {

        if(client == null) {
            return null;
        }

        if(address.endsOnType()) {
            log.debug("The prefix ends on a type.");
            return null;
        }

        final ModelNode request = new ModelNode();
        setAddress(request, parent, address);
        request.get(Util.OPERATION).set(Util.READ_RESOURCE_DESCRIPTION);
        request.get(Util.ACCESS_CONTROL).set(Util.TRIM_DESCRIPTIONS);
        if(operations) {
            request.get(Util.OPERATIONS).set(true);
        }

        final ModelNode response;
        try {
            response = client.execute(request);
        } catch (Exception e) {
            log.warnf(e, "Failed to execute %s", Util.READ_RESOURCE_DESCRIPTION);
            return null;
        }

        if (!Util.isSuccess(response)) {
            log.debugf("Failed to execute %s:%s", Util.READ_RESOURCE_DESCRIPTION, response);
            return null;
        }

        if(!response.has(Util.RESULT)) {
            log.warnf("Response is missing result for %s:%s", Util.READ_RESOURCE_DESCRIPTION, response);
            return null;
        }

        final ModelNode result = response.get(Util.RESULT);
        if(!result.has(Util.ACCESS_CONTROL)) {
            log.warnf("Result is missing access-control for %s:%s", Util.READ_RESOURCE_DESCRIPTION, response);
            return null;
        }

        return result.get(Util.ACCESS_CONTROL);
    }

    private static void setAddress(ModelNode request, String[] parent, OperationRequestAddress address) {
        final ModelNode addressNode = request.get(Util.ADDRESS);
        addressNode.setEmptyList();
        if(parent != null) {
            int i = 0;
            while(i < parent.length) {
                addressNode.add(parent[i++], parent[i++]);
            }
        }

        if(!address.isEmpty()) {
            final Iterator<Node> iterator = address.iterator();
            while (iterator.hasNext()) {
                final OperationRequestAddress.Node node = iterator.next();
                if (node.getName() != null) {
                    addressNode.add(node.getType(), node.getName());
                } else if (iterator.hasNext()) {
                    throw new IllegalArgumentException(
                            "The node name is not specified for type '"
                                    + node.getType() + "'");
                }
            }
        }
    }
}
