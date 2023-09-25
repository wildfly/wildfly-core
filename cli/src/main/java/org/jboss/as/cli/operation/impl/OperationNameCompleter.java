/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.operation.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.DefaultCompleter;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author Alexey Loubyansky
 */
public class OperationNameCompleter extends DefaultCompleter {

    public OperationNameCompleter(final OperationRequestAddress address) {
        super(new CandidatesProvider(){
            @Override
            public Collection<String> getAllCandidates(CommandContext ctx) {
                final ModelNode req = new ModelNode();
                final ModelNode addrNode = req.get(Util.ADDRESS);
                for(OperationRequestAddress.Node node : address) {
                    addrNode.add(node.getType(), node.getName());
                }
                req.get(Util.OPERATION).set(Util.READ_OPERATION_NAMES);
                if(ctx.getConfig().isAccessControl()) {
                    req.get(Util.ACCESS_CONTROL).set(true);
                }
                final ModelNode response;
                try {
                    response = ctx.getModelControllerClient().execute(req);
                } catch (Exception e) {
                    return Collections.emptyList();
                }
                final ModelNode result = response.get(Util.RESULT);
                if(!result.isDefined()) {
                    return Collections.emptyList();
                }
                final List<ModelNode> list = result.asList();
                final List<String> names = new ArrayList<String>(list.size());
                for(ModelNode node : list) {
                    names.add(node.asString());
                }
                return names;
            }});
    }
}
