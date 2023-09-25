/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.accesscontrol;

import static org.wildfly.common.Assert.checkNotNullParam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.controller.client.ModelControllerClient;

/**
 * @author Alexey Loubyansky
 *
 */
public class PerNodeOperationAccess extends BaseOperationAccessRequirement {

    private static final Boolean[] EMPTY_BARR = new Boolean[0];

    private final String nodeType;
    private List<String> nodeNames = Collections.emptyList();
    private Boolean[] stateOn = null;
    private List<String> allowedOn;

    PerNodeOperationAccess(String nodeType, String operation) {
        super(operation);
        this.nodeType = checkNotNullParam("nodeType", nodeType);
    }

    public PerNodeOperationAccess(CommandContext ctx, String nodeType, String address, String operation) {
        this(nodeType, address, operation);
        ctx.addEventListener(this);
    }

    PerNodeOperationAccess(String nodeType, String address, String operation) {
        super(address, operation);
        this.nodeType = checkNotNullParam("nodeType", nodeType);
    }

    PerNodeOperationAccess(String nodeType, OperationRequestAddress address, String operation) {
        super(address, operation);
        this.nodeType = checkNotNullParam("nodeType", nodeType);
    }

    @Override
    public void resetState() {
        nodeNames = null;
        stateOn = null;
        allowedOn = null;
    }

    public List<String> getAllowedOn(CommandContext ctx) {
        if(allowedOn == null) {
            if (ctx.getConfig().isAccessControl()) {
                if (stateOn == null) {
                    initList(ctx.getModelControllerClient());
                }
                completeAllowedOn(ctx.getModelControllerClient());
            } else {
                allowedOn = Util.getNodeNames(ctx.getModelControllerClient(), null, nodeType);
            }
        }
        return allowedOn;
    }

    @Override
    protected boolean checkAccess(CommandContext ctx) {
        final ModelControllerClient client = ctx.getModelControllerClient();
        if(client == null) {
            return false;
        }
        if(!ctx.isDomainMode()) {
            return false;
        }
        return initList(client);
    }

    @Override
    public String toString() {
        if(toString == null) {
            final StringBuilder buf = new StringBuilder();
            buf.append(nodeType).append("=*").append(super.toString());
            toString = buf.toString();
        }
        return toString;
    }

    protected boolean initList(ModelControllerClient client) {
        nodeNames = Util.getNodeNames(client, null, nodeType);
        if(nodeNames.isEmpty()) {
            allowedOn = nodeNames;
            stateOn = EMPTY_BARR;
            return false;
        }
        this.stateOn = new Boolean[nodeNames.size()];
        final String[] parent = new String[2];
        parent[0] = nodeType;
        for(int i = 0; i < nodeNames.size(); ++i) {
            parent[1] = nodeNames.get(i);
            if(CLIAccessControl.isExecute(client, parent, address, operation)) {
                stateOn[i] = true;
                return true;
            } else {
                stateOn[i] = false;
            }
        }
        return false;
    }

    protected void completeAllowedOn(ModelControllerClient client) {
        if(nodeNames.isEmpty()) {
            allowedOn = nodeNames;
            return;
        }
        allowedOn = new ArrayList<String>(stateOn.length);
        final String[] parent = new String[2];
        parent[0] = nodeType;
        for(int i = 0; i < stateOn.length; ++i) {
            Boolean state = stateOn[i];
            if(state == null) {
                parent[1] = nodeNames.get(i);
                state = CLIAccessControl.isExecute(client, parent, address, operation);
                stateOn[i] = state;
            }
            if(state) {
                allowedOn.add(nodeNames.get(i));
            }
        }
    }
}
