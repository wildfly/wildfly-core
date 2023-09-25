/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.accesscontrol;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestAddress;
import org.jboss.as.controller.client.ModelControllerClient;

/**
 * @author Alexey Loubyansky
 *
 */
public class HostServerOperationAccess extends BaseOperationAccessRequirement {

    private int lastCheckedServer;
    private Map<String,List<String>> toCheck;
    private Map<String,List<String>> allowed;

    public HostServerOperationAccess(CommandContext ctx, String address, String operation) {
        this(address, operation);
        ctx.addEventListener(this);
    }

    HostServerOperationAccess(String address, String operation) {
        super(address, operation);
    }

    HostServerOperationAccess(String operation) {
        super(operation);
    }

    HostServerOperationAccess(OperationRequestAddress address, String operation) {
        super(address, operation);
    }

    @Override
    public void resetState() {
        lastCheckedServer = 0;
        toCheck = null;
        allowed = null;
    }

    public Collection<String> getAllowedHosts(CommandContext ctx) {
        if(allowed == null) {
            initAllowedLists(ctx);
        }
        return allowed.keySet();
    }

    public Collection<String> getAllowedServers(CommandContext ctx, String host) {
        if(allowed == null) {
            initAllowedLists(ctx);
        }
        final List<String> servers = allowed.get(host);
        return servers == null ? Collections.<String>emptyList() : servers;
    }

    protected void initAllowedLists(CommandContext ctx) {
        if(ctx.getConfig().isAccessControl()) {
            if(!isSatisfied(ctx)) {
                allowed = Collections.emptyMap();
            } else {
                completeAccessCheck(ctx);
            }
        } else {
            final ModelControllerClient client = ctx.getModelControllerClient();
            allowed = new HashMap<String,List<String>>();
            final OperationRequestAddress hostAddress = new DefaultOperationRequestAddress();
            hostAddress.toNodeType(Util.HOST);
            final List<String> hosts = Util.getNodeNames(client, null, Util.HOST);
            for(String host : hosts) {
                hostAddress.toNode(host);
                final List<String> servers = Util.getNodeNames(client, hostAddress, Util.SERVER);
                if(!servers.isEmpty()) {
                    allowed.put(host, servers);
                }
            }
        }
    }

    protected void completeAccessCheck(CommandContext ctx) {

        final ModelControllerClient client = ctx.getModelControllerClient();
        allowed = new HashMap<String,List<String>>();
        final OperationRequestAddress hostAddress = new DefaultOperationRequestAddress();
        hostAddress.toNodeType(Util.HOST);
        final String[] parent = new String[4];
        parent[0] = Util.HOST;
        parent[2] = Util.SERVER;
        for(Map.Entry<String, List<String>> entry : toCheck.entrySet()) {
            final String host = entry.getKey();
            hostAddress.toNode(host);
            List<String> servers = entry.getValue();
            List<String> allowedServers = null;
            int i = 0;
            if(servers == null) {
                servers = Util.getNodeNames(client, hostAddress, Util.SERVER);
            } else {
                allowedServers = new ArrayList<String>();
                allowed.put(host, allowedServers);
                allowedServers.add(servers.get(lastCheckedServer));
                i = lastCheckedServer + 1;
            }
            parent[1] = host;
            while(i < servers.size()) {
                final String server = servers.get(i++);
                parent[3] = server;
                if(CLIAccessControl.isExecute(client, parent, address, operation)) {
                    if(allowedServers == null) {
                        allowedServers = new ArrayList<String>();
                        allowed.put(host, allowedServers);
                    }
                    allowedServers.add(server);
                }
            }
        }
    }

    @Override
    protected boolean checkAccess(CommandContext ctx) {

        final ModelControllerClient client = ctx.getModelControllerClient();
        final List<String> hosts = Util.getNodeNames(client, null, Util.HOST);
        if(hosts.isEmpty()) {
            return false;
        }

        final OperationRequestAddress hostAddress = new DefaultOperationRequestAddress();
        hostAddress.toNodeType(Util.HOST);
        final String[] parent = new String[4];
        parent[0] = Util.HOST;
        parent[2] = Util.SERVER;
        toCheck = new HashMap<String,List<String>>();
        boolean satisfied = false;
        for(String host : hosts) {
            if (!satisfied) {
                lastCheckedServer = 0;
                hostAddress.toNode(host);
                parent[1] = host;
                final List<String> servers = Util.getNodeNames(client, hostAddress, Util.SERVER);
                for (String server : servers) {
                    parent[3] = server;
                    if (CLIAccessControl.isExecute(client, parent, address, operation)) {
                        toCheck.put(host, servers);
                        satisfied = true;
                        break;
                    }
                    ++lastCheckedServer;
                }
            } else {
                toCheck.put(host, null);
            }
        }
        return satisfied;
    }
}
