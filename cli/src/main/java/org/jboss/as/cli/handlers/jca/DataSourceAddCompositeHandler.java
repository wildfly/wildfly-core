/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers.jca;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.jboss.as.cli.ArgumentValueConverter;
import org.jboss.as.cli.CommandArgument;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.handlers.OperationCommandWithDescription;
import org.jboss.as.cli.handlers.ResourceCompositeOperationHandler;
import org.jboss.as.cli.impl.ArgumentWithValue;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;

/**
 * Command handler that allows to add connection properties while adding the data-source in one composite operation.
 *
 * @author Alexey Loubyansky
 */
public class DataSourceAddCompositeHandler extends ResourceCompositeOperationHandler implements OperationCommandWithDescription {

    private static final String CONNECTION_PROPERTIES = "connection-properties";

    private ArgumentWithValue conProps;

    public DataSourceAddCompositeHandler(CommandContext ctx, String nodeType) {
        super(ctx, "data-source-add", nodeType, null, Util.ADD);
        conProps = new ArgumentWithValue(this, null, ArgumentValueConverter.PROPERTIES, "--" + CONNECTION_PROPERTIES);
    }

    @Override
    protected Map<String, CommandArgument> loadArguments(CommandContext ctx) {
        final Map<String, CommandArgument> args = super.loadArguments(ctx);
        args.put(conProps.getFullName(), conProps);
        return args;
    }

    protected ModelNode buildRequestWithoutHeaders(CommandContext ctx) throws CommandFormatException {
        final ModelNode req = super.buildRequestWithoutHeaders(ctx);
        final ModelNode steps = req.get(Util.STEPS);

        final ModelNode conPropsNode = conProps.toModelNode(ctx);
        if(conPropsNode != null) {
            final List<Property> propsList = conPropsNode.asPropertyList();
            for(Property prop : propsList) {
                final ModelNode address = this.buildOperationAddress(ctx);
                address.add(CONNECTION_PROPERTIES, prop.getName());
                final ModelNode addProp = new ModelNode();
                addProp.get(Util.ADDRESS).set(address);
                addProp.get(Util.OPERATION).set(Util.ADD);
                addProp.get(Util.VALUE).set(prop.getValue());
                steps.add(addProp);
            }
        }
        return req;
    }

    @Override
    public ModelNode getOperationDescription(CommandContext ctx) throws CommandLineException {
        ModelNode request = initRequest(ctx);
        if(request == null) {
            return null;
        }
        request.get(Util.OPERATION).set(Util.READ_OPERATION_DESCRIPTION);
        request.get(Util.NAME).set(Util.ADD);
        final ModelNode response;
        try {
            response = ctx.getModelControllerClient().execute(request);
        } catch (IOException e) {
            throw new CommandFormatException("Failed to execute read-operation-description.", e);
        }
        if (!response.hasDefined(Util.RESULT)) {
            return null;
        }
        final ModelNode result = response.get(Util.RESULT);

        final ModelNode opDescr = result.get(Util.DESCRIPTION);
        final StringBuilder buf = new StringBuilder();
        buf.append(opDescr.asString());
        buf.append(" (unlike the add operation, this command accepts xa-datasource-properties).");
        opDescr.set(buf.toString());

        final ModelNode allProps = result.get(Util.REQUEST_PROPERTIES);
        final ModelNode conProps = allProps.get(CONNECTION_PROPERTIES);

        conProps.get(Util.DESCRIPTION).set("A comma-separated list of datasource connection properties in key=value pair format.");
        conProps.get(Util.TYPE).set(ModelType.LIST);
        conProps.get(Util.REQUIRED).set(false);
        conProps.get(Util.NILLABLE).set(false);

        return result;
    }
}
