/*
Copyright 2017 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package org.jboss.as.cli.impl.aesh.cmd.deployment;

import org.jboss.as.cli.impl.aesh.cmd.deployment.security.CommandWithPermissions;
import org.jboss.as.cli.impl.aesh.cmd.deployment.security.Permissions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.aesh.cmd.AbstractCompleter;
import org.jboss.as.cli.impl.aesh.cmd.HeadersCompleter;
import org.jboss.as.cli.impl.aesh.cmd.HeadersConverter;
import org.jboss.as.cli.impl.aesh.cmd.security.ControlledCommandActivator;
import org.jboss.as.cli.impl.aesh.cmd.deployment.security.AccessRequirements;
import org.jboss.as.cli.impl.aesh.cmd.deployment.security.OptionActivators.InfoNameActivator;
import org.jboss.as.cli.impl.aesh.cmd.deployment.security.OptionActivators.InfoServerGroupActivator;
import org.jboss.as.cli.util.SimpleTable;
import org.jboss.as.cli.util.StrictSizeTable;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.wildfly.core.cli.command.DMRCommand;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.cli.command.aesh.CLICompleterInvocation;
import org.wildfly.core.cli.command.aesh.activator.HideOptionActivator;

/**
 * Display deployment info. All fields are public to be accessible from legacy
 * commands. To be made private when legacies are removed.
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "info", description = "", activator = ControlledCommandActivator.class)
public class InfoCommand extends CommandWithPermissions implements DMRCommand {

    public static class ServerGroupCompleter
            extends AbstractCompleter {

        @Override
        protected List<String> getItems(CLICompleterInvocation completerInvocation) {
            List<String> groups = Collections.emptyList();
            if (completerInvocation.getCommandContext().getModelControllerClient() != null) {
                CommandWithPermissions rc = (CommandWithPermissions) completerInvocation.getCommand();
                return rc.getPermissions().getServerGroupAddPermission().
                        getAllowedOn(completerInvocation.getCommandContext());
            }
            return groups;
        }
    }

    private static final String ADDED = "added";
    private static final String ENABLED = "ENABLED";
    private static final String NAME = "NAME";
    private static final String PERSISTENT = "PERSISTENT";
    private static final String RUNTIME_NAME = "RUNTIME-NAME";
    private static final String NOT_ADDED = "not added";
    private static final String N_A = "n/a";
    private static final String SERVER_GROUP = "SERVER-GROUP";
    private static final String STATE = "STATE";
    private static final String STATUS = "STATUS";

    @Deprecated
    @Option(hasValue = false, activator = HideOptionActivator.class)
    protected boolean help;

    // Argument comes first, aesh behavior.
    @Argument(completer = EnableCommand.NameCompleter.class, activator = InfoNameActivator.class)
    public String deploymentName;

    @Option(converter = HeadersConverter.class, completer = HeadersCompleter.class,
            required = false)
    public ModelNode headers;

    @Option(name = "server-group", activator = InfoServerGroupActivator.class,
            completer = ServerGroupCompleter.class,
            required = false)
    public String serverGroup;

    private List<String> addedServerGroups;
    private List<String> otherServerGroups;

    public InfoCommand(CommandContext ctx, Permissions permissions) {
        super(ctx, AccessRequirements.infoAccess(permissions), permissions);
    }

    @Deprecated
    public InfoCommand(CommandContext ctx) {
        this(ctx, null);
    }

    private String getName() {
        return deploymentName;
    }

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation)
            throws CommandException, InterruptedException {
        if (help) {
            commandInvocation.println(commandInvocation.getHelpInfo("deployment info"));
            return CommandResult.SUCCESS;
        }
        try {
            ModelNode request = buildRequest(commandInvocation.getCommandContext());
            final ModelNode result = commandInvocation.getCommandContext().
                    getModelControllerClient().execute(request);
            if (!Util.isSuccess(result)) {
                throw new CommandException(Util.getFailureDescription(result));
            }
            handleResponse(commandInvocation.getCommandContext(), result);
        } catch (IOException e) {
            throw new CommandException("Failed to deploy", e);
        } catch (CommandFormatException ex) {
            throw new CommandException(ex);
        }
        return CommandResult.SUCCESS;
    }

    @Override
    public ModelNode buildRequest(CommandContext ctx)
            throws CommandFormatException {
        String deploymentName = getName();
        if (ctx.isDomainMode() && deploymentName == null
                && (serverGroup == null || serverGroup.isEmpty())) {
            throw new CommandFormatException("Either a name or --server-group is required.");
        }

        final ModelNode request = new ModelNode();
        if (ctx.isDomainMode()) {
            final List<String> serverGroups
                    = Util.getServerGroups(ctx.getModelControllerClient());
            addedServerGroups = null;
            otherServerGroups = null;
            if (serverGroup == null) {
                if (deploymentName == null || deploymentName.indexOf('*') >= 0) {
                    // wildcards in domain mode aren't supported
                    throw new CommandFormatException("If --server-group "
                            + " is not specified, a name "
                            + " must be set to a specific deployment name, "
                            + "wildcards are allowed only when --server-group is provided.");
                }

                final ModelNode validateRequest = new ModelNode();
                validateRequest.get(Util.OPERATION).set(Util.COMPOSITE);
                validateRequest.get(Util.ADDRESS).setEmptyList();
                ModelNode steps = validateRequest.get(Util.STEPS);
                for (String serverGroup : serverGroups) {
                    final ModelNode step = new ModelNode();
                    step.get(Util.ADDRESS).setEmptyList();
                    step.get(Util.OPERATION).set(Util.VALIDATE_ADDRESS);
                    final ModelNode value = step.get(Util.VALUE);
                    value.add(Util.SERVER_GROUP, serverGroup);
                    value.add(Util.DEPLOYMENT, deploymentName);
                    steps.add(step);
                }
                final ModelControllerClient client = ctx.getModelControllerClient();
                final ModelNode response;
                try {
                    response = client.execute(validateRequest);
                } catch (IOException e) {
                    throw new CommandFormatException("Failed to query server groups "
                            + "for deployment " + deploymentName, e);
                }

                if (!response.hasDefined(Util.RESULT)) {
                    throw new CommandFormatException("The validation response came "
                            + "back w/o result: " + response);
                }
                ModelNode result = response.get(Util.RESULT);
//                if(!result.hasDefined(Util.DOMAIN_RESULTS)) {
//                    throw new CommandFormatException(Util.DOMAIN_RESULTS + " aren't available for validation request: " + result);
//                }
//                result = result.get(Util.DOMAIN_RESULTS);

                // TODO could be this... could be that
                if (result.hasDefined(Util.DOMAIN_RESULTS)) {
                    result = result.get(Util.DOMAIN_RESULTS);
                }
                final List<Property> stepResponses = result.asPropertyList();
                for (int i = 0; i < serverGroups.size(); ++i) {
                    final Property prop = stepResponses.get(i);
                    ModelNode stepResponse = prop.getValue();
                    if (stepResponse.has(prop.getName())) { // TODO remove when the structure is consistent
                        stepResponse = stepResponse.get(prop.getName());
                    }
                    if (stepResponse.hasDefined(Util.RESULT)) {
                        final ModelNode stepResult = stepResponse.get(Util.RESULT);
                        if (stepResult.hasDefined(Util.VALID)
                                && stepResult.get(Util.VALID).asBoolean()) {
                            if (addedServerGroups == null) {
                                addedServerGroups = new ArrayList<String>();
                            }
                            addedServerGroups.add(serverGroups.get(i));
                        } else {
                            if (otherServerGroups == null) {
                                otherServerGroups = new ArrayList<String>();
                            }
                            otherServerGroups.add(serverGroups.get(i));
                        }
                    } else {
                        if (otherServerGroups == null) {
                            otherServerGroups = new ArrayList<String>();
                        }
                        otherServerGroups.add(serverGroups.get(i));
                    }
                }

                request.get(Util.OPERATION).set(Util.COMPOSITE);
                request.get(Util.ADDRESS).setEmptyList();
                steps = request.get(Util.STEPS);

                ModelNode step = new ModelNode();
                ModelNode address = step.get(Util.ADDRESS);
                address.add(Util.DEPLOYMENT, deploymentName);
                step.get(Util.OPERATION).set(Util.READ_RESOURCE);
                steps.add(step);

                if (addedServerGroups != null) {
                    for (String serverGroup : addedServerGroups) {
                        step = new ModelNode();
                        address = step.get(Util.ADDRESS);
                        address.add(Util.SERVER_GROUP, serverGroup);
                        address.add(Util.DEPLOYMENT, deploymentName);
                        step.get(Util.OPERATION).set(Util.READ_RESOURCE);
                        steps.add(step);
                    }
                }
            } else if (serverGroup.indexOf('*') >= 0) {
                throw new CommandFormatException("Wildcards are not allowed here, "
                        + "please give a valid server-group name.");
            } else {
                request.get(Util.OPERATION).set(Util.COMPOSITE);
                request.get(Util.ADDRESS).setEmptyList();
                final ModelNode steps = request.get(Util.STEPS);

                ModelNode step = new ModelNode();
                step.get(Util.OPERATION).set(Util.READ_CHILDREN_RESOURCES);
                step.get(Util.CHILD_TYPE).set(Util.DEPLOYMENT);
                step.get(Util.INCLUDE_RUNTIME).set(Util.TRUE);
                steps.add(step);

                step = new ModelNode();
                step.get(Util.OPERATION).set(Util.READ_CHILDREN_RESOURCES);
                step.get(Util.ADDRESS).add(Util.SERVER_GROUP, serverGroup);
                step.get(Util.CHILD_TYPE).set(Util.DEPLOYMENT);
                step.get(Util.INCLUDE_RUNTIME).set(Util.TRUE);
                steps.add(step);
            }
        } else if (deploymentName == null || deploymentName.indexOf('*') >= 0) {
            request.get(Util.OPERATION).set(Util.READ_CHILDREN_RESOURCES);
            request.get(Util.CHILD_TYPE).set(Util.DEPLOYMENT);
            request.get(Util.INCLUDE_RUNTIME).set(Util.TRUE);
        } else {
            final ModelNode address = request.get(Util.ADDRESS);
            address.add(Util.DEPLOYMENT, deploymentName);
            request.get(Util.OPERATION).set(Util.READ_RESOURCE);
            request.get(Util.INCLUDE_RUNTIME).set(true);
        }
        if (headers != null) {
            ModelNode opHeaders = request.get(Util.OPERATION_HEADERS);
            opHeaders.set(headers);
        }
        return request;
    }

    public void handleResponse(CommandContext ctx, ModelNode response)
            throws CommandFormatException {
        try {
            if (!response.hasDefined(Util.RESULT)) {
                throw new CommandFormatException("The operation response came back "
                        + "w/o result: " + response);
            }
            ModelNode result = response.get(Util.RESULT);

            if (ctx.isDomainMode()) {
//                if(!result.hasDefined(Util.DOMAIN_RESULTS)) {
//                    throw new CommandFormatException(Util.DOMAIN_RESULTS + " aren't available " + result);
//                    return;
//                }
                // TODO it could be... could be not...
                if (result.hasDefined(Util.DOMAIN_RESULTS)) {
                    result = result.get(Util.DOMAIN_RESULTS);
                }

                final Iterator<Property> steps = result.asPropertyList().iterator();
                if (!steps.hasNext()) {
                    throw new CommandFormatException("Response for the main resource "
                            + "info of the deployment is missing: " + result);
                }

                // /deployment=<name>
                ModelNode step = steps.next().getValue();
                if (step.has(Util.STEP_1)) { // TODO remove when the structure is consistent
                    step = step.get(Util.STEP_1);
                }
                if (!step.has(Util.RESULT)) {
                    throw new CommandFormatException("Failed to read the main resource "
                            + "info of the deployment: " + Util.getFailureDescription(step));
                }
                ModelNode stepResponse = step.get(Util.RESULT);

                if (serverGroup != null) {
                    step = result.get(Util.STEP_1);
                    if (!step.isDefined()) {
                        throw new CommandFormatException("No step outcome for deployment "
                                + "resources (step-1): " + result);
                    }
                    final ModelNode allDeployments = step.get(Util.RESULT);
                    if (!allDeployments.isDefined()) {
                        throw new CommandFormatException("No result for deployment "
                                + "resources (step-1): " + result);
                    }

                    step = result.get(Util.STEP_2);
                    if (!step.isDefined()) {
                        throw new CommandFormatException("No step outcome for "
                                + "server-group deployment resources (step-2): "
                                + result);
                    }
                    final ModelNode sgDeployments = step.get(Util.RESULT);
                    if (!sgDeployments.isDefined()) {
                        throw new CommandFormatException("No result for server-group "
                                + "deployment resources (step-2): " + result);
                    }

                    final String deploymentName = getName();
                    final Pattern pattern
                            = Pattern.compile(Util.wildcardToJavaRegex(deploymentName == null ? "*"
                                    : deploymentName));

                    final SimpleTable table = new SimpleTable(new String[]{NAME,
                        RUNTIME_NAME, STATE}, ctx.getTerminalWidth());
                    for (String name : allDeployments.keys()) {
                        if (!pattern.matcher(name).matches()) {
                            continue;
                        }
                        if (sgDeployments.hasDefined(name)) {
                            final ModelNode node = sgDeployments.get(name);
                            table.addLine(new String[]{node.get(Util.NAME).asString(),
                                node.get(Util.RUNTIME_NAME).asString(),
                                node.get(Util.ENABLED).asBoolean()
                                ? Util.ENABLED : ADDED});
                        } else {
                            final ModelNode resource = allDeployments.get(name);
                            table.addLine(new String[]{resource.get(Util.NAME).asString(),
                                resource.get(Util.RUNTIME_NAME).asString(), NOT_ADDED});
                        }
                    }
                    if (!table.isEmpty()) {
                        ctx.printLine(table.toString(true));
                    }
                } else {
                    final StrictSizeTable table = new StrictSizeTable(1);
                    table.addCell(Util.NAME, stepResponse.get(Util.NAME).asString());
                    table.addCell(Util.RUNTIME_NAME, stepResponse.
                            get(Util.RUNTIME_NAME).asString());
                    ctx.printLine(table.toString());
                    final SimpleTable groups
                            = new SimpleTable(new String[]{SERVER_GROUP, STATE}, ctx.getTerminalWidth());
                    if (addedServerGroups == null) {
                        if (steps.hasNext()) {
                            throw new CommandFormatException("Didn't expect results "
                                    + "for server groups but received "
                                    + (result.asPropertyList().size() - 1)
                                    + " more steps.");
                        }
                    } else {
                        for (String sg : addedServerGroups) {
                            final Property prop = steps.next();
                            stepResponse = prop.getValue();
                            if (stepResponse.has(prop.getName())) { // TODO remove when the structure is consistent
                                stepResponse = stepResponse.get(prop.getName());
                            }

                            if (stepResponse.hasDefined(Util.RESULT)) {
                                final ModelNode stepResult = stepResponse.
                                        get(Util.RESULT);
                                if (stepResult.hasDefined(Util.ENABLED)) {
                                    groups.addLine(new String[]{sg,
                                        stepResult.get(Util.ENABLED).asBoolean()
                                        ? Util.ENABLED : ADDED});
                                } else {
                                    groups.addLine(new String[]{sg, N_A});
                                }
                            } else {
                                groups.addLine(new String[]{sg, "no response"});
                            }
                        }
                    }

                    if (otherServerGroups != null) {
                        for (String sg : otherServerGroups) {
                            groups.addLine(new String[]{sg, NOT_ADDED});
                        }
                    }
                    ctx.printLine(groups.toString(true));
                }
            } else {
                final SimpleTable table = new SimpleTable(new String[]{NAME,
                    RUNTIME_NAME, PERSISTENT, ENABLED, STATUS}, ctx.getTerminalWidth());
                final String deploymentName = getName();
                if (deploymentName == null || deploymentName.indexOf('*') >= 0) {
                    final List<Property> list = result.asPropertyList();
                    if (!list.isEmpty()) {
                        final Pattern pattern
                                = Pattern.compile(Util.wildcardToJavaRegex(deploymentName == null ? "*"
                                        : deploymentName));
                        for (Property p : list) {
                            final ModelNode node = p.getValue();
                            final String name = node.get(Util.NAME).asString();
                            if (pattern.matcher(name).matches()) {
                                table.addLine(new String[]{name,
                                    node.get(Util.RUNTIME_NAME).asString(),
                                    node.get(Util.PERSISTENT).asString(),
                                    node.get(Util.ENABLED).asString(),
                                    node.get(Util.STATUS).asString()});
                            }
                        }
                    }
                } else {
                    table.addLine(new String[]{result.get(Util.NAME).asString(),
                        result.get(Util.RUNTIME_NAME).asString(),
                        result.get(Util.PERSISTENT).asString(),
                        result.get(Util.ENABLED).asString(),
                        result.get(Util.STATUS).asString()});
                }
                if (!table.isEmpty()) {
                    ctx.printLine(table.toString());
                }
            }
        } finally {
            addedServerGroups = null;
            otherServerGroups = null;
        }
    }
}
