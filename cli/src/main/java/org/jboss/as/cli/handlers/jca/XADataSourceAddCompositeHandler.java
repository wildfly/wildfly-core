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
 * Command handler that adds an xa datasource and its xa properties in one composite operation.
 *
 * @author Alexey Loubyansky
 */
public class XADataSourceAddCompositeHandler extends ResourceCompositeOperationHandler implements OperationCommandWithDescription {

    private static final String XA_DATASOURCE_PROPERTIES = "xa-datasource-properties";

    private ArgumentWithValue xaProps;

    public XADataSourceAddCompositeHandler(CommandContext ctx, String nodeType) {
        super(ctx, "xa-data-source-add", nodeType, null, Util.ADD);

        xaProps = new ArgumentWithValue(this, null, ArgumentValueConverter.PROPERTIES, "--" + XA_DATASOURCE_PROPERTIES);
    }

    @Override
    protected Map<String, CommandArgument> loadArguments(CommandContext ctx) {
        final Map<String, CommandArgument> args = super.loadArguments(ctx);
        args.put(xaProps.getFullName(), xaProps);
        return args;
    }

    protected ModelNode buildRequestWithoutHeaders(CommandContext ctx) throws CommandFormatException {
        final ModelNode req = super.buildRequestWithoutHeaders(ctx);
        final ModelNode steps = req.get(Util.STEPS);

//        final String xaPropsStr = xaProps.getOriginalValue(ctx.getParsedCommandLine(), false);
//        if(xaPropsStr != null) {
//            final List<Property> propsList = xaProps.getValueConverter().fromString(ctx, xaPropsStr).asPropertyList();
        final ModelNode xaPropsNode = xaProps.toModelNode(ctx);
        if(xaPropsNode != null) {
            final List<Property> propsList = xaPropsNode.asPropertyList();
            for(Property prop : propsList) {
                final ModelNode address = this.buildOperationAddress(ctx);
                address.add(XA_DATASOURCE_PROPERTIES, prop.getName());
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
        final ModelNode xaProps = allProps.get(XA_DATASOURCE_PROPERTIES);

        xaProps.get(Util.DESCRIPTION).set("A comma-separated list of XA datasource properties in key=value pair format.");
        xaProps.get(Util.TYPE).set(ModelType.LIST);
        xaProps.get(Util.REQUIRED).set(false);
        xaProps.get(Util.NILLABLE).set(false);

        return result;
    }
}
