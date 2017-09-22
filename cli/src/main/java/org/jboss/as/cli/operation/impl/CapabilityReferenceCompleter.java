/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.cli.operation.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.DefaultCompleter;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.logging.Logger;

/**
 *
 * @author jdenise@redhat.com
 */
public class CapabilityReferenceCompleter extends DefaultCompleter {

    private static final Logger log = Logger.getLogger(CapabilityReferenceCompleter.class.getName());

    // For testing purpose.
    public CapabilityReferenceCompleter(CandidatesProvider provider) {
        super(provider);
    }

    public CapabilityReferenceCompleter(OperationRequestAddress address, String staticPart) {
        super((ctx) -> {
            ModelNode mn;
            try {
                mn = toNode(address);
            } catch (OperationFormatException ex) {
                return Collections.emptyList();
            }
            return getCapabilityNames(ctx, mn, staticPart);
        });
    }

    private static ModelNode toNode(OperationRequestAddress address) throws OperationFormatException {
        if (address.isEmpty()) {
            return new ModelNode();
        }
        ModelNode mn = new ModelNode();
        Iterator<OperationRequestAddress.Node> iterator = address.iterator();
        while (iterator.hasNext()) {
            OperationRequestAddress.Node node = iterator.next();
            if (node.getName() != null) {
                ModelNode value = new ModelNode();
                value.set(node.getName());
                mn.add(new Property(node.getType(),value));
            } else {
                throw new OperationFormatException(
                        "The node name is not specified for type '"
                        + node.getType() + "'");
            }
        }
        return mn;
    }

    public List<String> getCapabilityReferenceNames(CommandContext ctx,
            OperationRequestAddress address,
            String staticPart) {
        return getCapabilityNames(ctx, address, staticPart);
    }

    public static List<String> getCapabilityNames(CommandContext ctx,
            OperationRequestAddress address,
            String staticPart) {
        ModelNode mn;
        try {
            mn = toNode(address);
        } catch (OperationFormatException ex) {
            return Collections.emptyList();
        }
        return getCapabilityNames(ctx, mn, staticPart);
    }

    private static List<String> getCapabilityNames(CommandContext ctx, ModelNode address,
            String staticPart) {
        if (ctx.getModelControllerClient() == null) {
            return Collections.emptyList();
        }
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        ModelNode request;
        try {
            if (ctx.isDomainMode()) {
                // capabilities registry for each host contains host wide capabilities +
                // all other capabilities in global and other scopes.
                // So use the contextual host (if any) capabilities registry
                // or use the one local to the server.
                String host = null;
                Property prop = address.get(0).asProperty();
                if (prop.getName().equals(Util.HOST)) {
                    host = prop.getValue().asString();
                } else {
                    ModelControllerClient client = ctx.getModelControllerClient();
                    ModelNode req = Util.buildRequest(ctx,
                            new DefaultOperationRequestAddress(),
                            Util.READ_ATTRIBUTE);
                    req.get(Util.NAME).set(Util.LOCAL_HOST_NAME);
                    ModelNode outcome = client.execute(req);
                    if (!Util.isSuccess(outcome) || !outcome.has(Util.RESULT)) {
                        return Collections.emptyList();
                    }
                    host = outcome.get(Util.RESULT).asString();
                }
                builder.addNode(Util.HOST, host);
            }
            builder.addNode(Util.CORE_SERVICE, Util.CAPABILITY_REGISTRY);
            builder.setOperationName("suggest-capabilities");
            builder.addProperty("name", staticPart);
            request = builder.buildRequest();
            request.get("dependent-address").set(address);
        } catch (CommandFormatException | IOException e) {
            log.trace(null, e);
            return Collections.emptyList();
        }
        List<String> lst = new ArrayList<>();
        try {
            ModelNode response = ctx.getModelControllerClient().execute(request);
            if (Util.isSuccess(response)) {
                ModelNode result = response.get(Util.RESULT);
                if (result.isDefined()) {
                    for (ModelNode mn : result.asList()) {
                        lst.add(mn.asString());
                    }
                }
            }
        } catch (IOException ex) {
            log.trace(null, ex);
            return Collections.emptyList();
        }
        Collections.sort(lst);
        return lst;
    }
}
