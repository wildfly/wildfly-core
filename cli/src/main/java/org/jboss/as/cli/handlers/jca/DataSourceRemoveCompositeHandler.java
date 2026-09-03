/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers.jca;

import java.io.IOException;
import java.util.Map;

import org.jboss.as.cli.CommandArgument;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.handlers.OperationCommandWithDescription;
import org.jboss.as.cli.handlers.ResourceCompositeOperationHandler;
import org.jboss.as.cli.impl.ArgumentWithoutValue;
import org.jboss.dmr.ModelNode;

/**
 * Command handler for the {@code remove} operation on a datasource. When the
 * {@code --update-default-datasource} flag is set and the datasource being
 * removed is currently referenced as the default datasource in the EE subsystem
 * ({@code /subsystem=ee/service=default-bindings}), the handler prepends an
 * {@code undefine-attribute} step to clear that reference before performing
 * the removal, all within a single composite operation.
 *
 * @author WildFly Authors
 */
public class DataSourceRemoveCompositeHandler extends ResourceCompositeOperationHandler implements OperationCommandWithDescription {

    private static final String UNSET_IF_DEFAULT_DATASOURCE = "unset-if-default-datasource";

    private final ArgumentWithoutValue unsetIfDefaultDatasource;

    public DataSourceRemoveCompositeHandler(CommandContext ctx, String nodeType) {
        super(ctx, "data-source-remove", nodeType, null, Util.REMOVE);
        unsetIfDefaultDatasource = new ArgumentWithoutValue(this, "--" + UNSET_IF_DEFAULT_DATASOURCE);
    }

    @Override
    protected Map<String, CommandArgument> loadArguments(CommandContext ctx) {
        final Map<String, CommandArgument> args = super.loadArguments(ctx);
        args.put(unsetIfDefaultDatasource.getFullName(), unsetIfDefaultDatasource);
        return args;
    }

    @Override
    protected ModelNode buildRequestWithoutHeaders(CommandContext ctx) throws CommandFormatException {
        final ModelNode req = super.buildRequestWithoutHeaders(ctx);

        try {
            if (!unsetIfDefaultDatasource.isPresent(ctx.getParsedCommandLine())) {
                return req;
            }
        } catch (CommandFormatException e) {
            throw e;
        }

        // Read the current default-datasource attribute from /subsystem=ee/service=default-bindings
        final String currentDefault = readEeDefaultDatasource(ctx);
        if (currentDefault == null) {
            // No default configured, or ee subsystem absent — nothing to do
            return req;
        }

        // Determine the JNDI name of the datasource being removed so we can compare
        final String dsJndiName = buildDatasourceJndiName(ctx);
        if (dsJndiName == null || !dsJndiName.equals(currentDefault)) {
            // This datasource is not the current EE default — nothing to do
            return req;
        }

        // Prepend an undefine-attribute step for /subsystem=ee/service=default-bindings:datasource
        final ModelNode undefineStep = new ModelNode();
        final ModelNode eeAddress = buildEeDefaultBindingsAddress(ctx);
        undefineStep.get(Util.ADDRESS).set(eeAddress);
        undefineStep.get(Util.OPERATION).set(Util.UNDEFINE_ATTRIBUTE);
        undefineStep.get(Util.NAME).set(Util.DATASOURCE);

        // Insert the undefine step before the remove step
        final ModelNode steps = req.get(Util.STEPS);
        final ModelNode existingSteps = steps.clone();
        steps.setEmptyList();
        steps.add(undefineStep);
        for (ModelNode step : existingSteps.asList()) {
            steps.add(step);
        }

        return req;
    }

    /**
     * Builds the address for {@code /subsystem=ee/service=default-bindings}, prepending
     * {@code /profile=<name>} when running in domain mode.
     */
    private ModelNode buildEeDefaultBindingsAddress(CommandContext ctx) throws CommandFormatException {
        final ModelNode address = new ModelNode();
        if (isDependsOnProfile() && ctx.isDomainMode()) {
            final String profileName = profile.getValue(ctx.getParsedCommandLine());
            if (profileName == null) {
                throw new CommandFormatException("Required argument --profile is missing.");
            }
            address.add(Util.PROFILE, profileName);
        }
        address.add(Util.SUBSYSTEM, Util.EE);
        address.add(Util.SERVICE, Util.DEFAULT_BINDINGS);
        return address;
    }

    /**
     * Reads the {@code datasource} attribute from {@code /subsystem=ee/service=default-bindings}.
     * Returns {@code null} if the resource does not exist or the attribute is undefined.
     */
    private String readEeDefaultDatasource(CommandContext ctx) throws CommandFormatException {
        final ModelNode request = new ModelNode();
        final ModelNode address = buildEeDefaultBindingsAddress(ctx);
        request.get(Util.ADDRESS).set(address);
        request.get(Util.OPERATION).set(Util.READ_ATTRIBUTE);
        request.get(Util.NAME).set(Util.DATASOURCE);
        try {
            final ModelNode response = ctx.getModelControllerClient().execute(request);
            if (Util.isSuccess(response) && response.hasDefined(Util.RESULT)) {
                final ModelNode result = response.get(Util.RESULT);
                if (result.isDefined()) {
                    return result.asString();
                }
            }
        } catch (IOException e) {
            throw new CommandFormatException("Failed to read EE default datasource attribute.", e);
        }
        return null;
    }

    /**
     * Reads the {@code jndi-name} attribute from the datasource resource being removed.
     * Returns {@code null} if unavailable.
     */
    private String buildDatasourceJndiName(CommandContext ctx) throws CommandFormatException {
        final ModelNode address = buildOperationAddress(ctx);
        final ModelNode request = new ModelNode();
        request.get(Util.ADDRESS).set(address);
        request.get(Util.OPERATION).set(Util.READ_ATTRIBUTE);
        request.get(Util.NAME).set(Util.JNDI_NAME);
        try {
            final ModelNode response = ctx.getModelControllerClient().execute(request);
            if (Util.isSuccess(response) && response.hasDefined(Util.RESULT)) {
                final ModelNode result = response.get(Util.RESULT);
                if (result.isDefined()) {
                    return result.asString();
                }
            }
        } catch (IOException e) {
            throw new CommandFormatException("Failed to read datasource jndi-name attribute.", e);
        }
        return null;
    }

    @Override
    public ModelNode getOperationDescription(CommandContext ctx) throws CommandLineException {
        ModelNode request = initRequest(ctx);
        if (request == null) {
            return null;
        }
        request.get(Util.OPERATION).set(Util.READ_OPERATION_DESCRIPTION);
        request.get(Util.NAME).set(Util.REMOVE);
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

        final ModelNode allProps = result.get(Util.REQUEST_PROPERTIES);
        final ModelNode unsetDefaultProp = allProps.get(UNSET_IF_DEFAULT_DATASOURCE);
        unsetDefaultProp.get(Util.DESCRIPTION).set(
                "If present, and this datasource is currently set as the default datasource " +
                "in the EE subsystem (/subsystem=ee/service=default-bindings), unsets it " +
                "before removing the datasource. Has no effect if no default datasource is " +
                "configured or if the configured default datasource is a different datasource.");
        unsetDefaultProp.get(Util.TYPE).set(org.jboss.dmr.ModelType.BOOLEAN);
        unsetDefaultProp.get(Util.REQUIRED).set(false);
        unsetDefaultProp.get(Util.NILLABLE).set(true);

        return result;
    }
}
