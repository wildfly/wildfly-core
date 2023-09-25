/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers.jca;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.DefaultCompleter;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author Alexey Loubyansky
 */
public class JDBCDriverNameProvider implements DefaultCompleter.CandidatesProvider {

    public static final JDBCDriverNameProvider INSTANCE = new JDBCDriverNameProvider();

    @Override
    public Collection<String> getAllCandidates(CommandContext ctx) {

        if(ctx.isDomainMode()) {
            // in the domain it's too complicated
            return Collections.emptyList();
        }

        final ModelControllerClient client = ctx.getModelControllerClient();
        if(client == null) {
            return Collections.emptyList();
        }

        final ModelNode req = new ModelNode();
        req.get(Util.OPERATION).set(Util.INSTALLED_DRIVERS_LIST);
        final ModelNode address = req.get(Util.ADDRESS);
        address.add(Util.SUBSYSTEM, Util.DATASOURCES);

        final ModelNode response;
        try {
            response = client.execute(req);
        } catch (IOException e) {
            return Collections.emptyList();
        }
        if(!response.hasDefined(Util.RESULT)) {
            return Collections.emptyList();
        }
        final List<ModelNode> nodeList = response.get(Util.RESULT).asList();
        final List<String> names = new ArrayList<String>(nodeList.size());
        for(ModelNode node : nodeList) {
            if(node.has(Util.DRIVER_NAME)) {
                names.add(node.get(Util.DRIVER_NAME).asString());
            }
        }
        return names;
    }
}
