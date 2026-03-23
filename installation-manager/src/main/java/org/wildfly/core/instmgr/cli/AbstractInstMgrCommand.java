/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.instmgr.cli;

import static org.jboss.as.cli.Util.ADDRESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.wildfly.core.instmgr.cli.UpdateCommand.CONFIRM_OPTION;
import static org.wildfly.core.instmgr.cli.UpdateCommand.DRY_RUN_OPTION;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.completer.OptionCompleter;
import org.aesh.command.impl.internal.ParsedCommand;
import org.aesh.command.option.Option;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.cli.command.aesh.CLICompleterInvocation;
import org.wildfly.core.cli.command.aesh.activator.AbstractOptionActivator;
import org.wildfly.core.cli.command.aesh.activator.AbstractRejectOptionActivator;
import org.wildfly.core.cli.command.aesh.activator.DomainOptionActivator;
import org.wildfly.core.instmgr.InstMgrConstants;

@CommandDefinition(name = "abstract-inst-mgr-cmd", description = "", activator = InstMgrActivator.class)
public abstract class AbstractInstMgrCommand implements Command<CLICommandInvocation> {
    static final PathElement CORE_SERVICE_INSTALLER = PathElement.pathElement(CORE_SERVICE, InstMgrGroupCommand.COMMAND_NAME);
    static final String NO_RESOLVE_LOCAL_CACHE_OPTION= "no-resolve-local-cache";
    static final String USE_DEFAULT_LOCAL_CACHE_OPTION = "use-default-local-cache";

    @Option(name = "host", completer = AbstractInstMgrCommand.HostsCompleter.class, activator = AbstractInstMgrCommand.HostsActivator.class)
    protected String host;

    /**
     * General Execute Operation method.
     *
     * @param ctx
     * @return ModelNode with the result of a successful execution.
     * @throws CommandException If the operation was not success or an error occurred.
     */
    protected ModelNode executeOp(CommandContext ctx, String host) throws CommandException {
        validateHostParameter(ctx, host);

        PathAddress address;
        if (ctx.isDomainMode()) {
            address = createHost(host, ctx.getModelControllerClient());
        } else {
            address = createStandalone();
        }

        final Operation request = buildOperation();
        request.getOperation().get(ADDRESS).set(address.toModelNode());

        ModelNode response;
        try {
            response = ctx.getModelControllerClient().execute(request);
        } catch (IOException ex) {
            throw new CommandException("Failed to execute the Operation " + request.getOperation().asString(), ex);
        } finally {
            try {
                request.close();
            } catch (Throwable t) {
                throw new CommandException("Failed to close the operation resource.", t);
            }
        }

        if (!Util.isSuccess(response)) {
            throw new CommandException(Util.getFailureDescription(response));
        }

        return response;
    }

    protected abstract Operation buildOperation() throws CommandException;

    protected static void validateHostParameter(CommandContext ctx, String host) throws CommandException{
        if (host != null && !ctx.isDomainMode()) {
            throw new CommandException("The --host option is not available in the current context. "
                    + "Connection to the controller might be unavailable or not running in domain mode.");
        } else if (host == null && ctx.isDomainMode()) {
            throw new CommandException("The --host option must be used in domain mode.");
        }
    }

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        throw new CommandException("Command action is missing.");
    }

    public static class HostsActivator extends AbstractOptionActivator implements DomainOptionActivator {

        @Override
        public boolean isActivated(ParsedCommand processedCommand) {
            return getCommandContext().getModelControllerClient() != null && getCommandContext().isDomainMode();
        }
    }

    public static class DryRunActivator extends AbstractRejectOptionActivator {
        public DryRunActivator() {
            super(CONFIRM_OPTION);
        }
    }

    public static class ConfirmActivator extends AbstractRejectOptionActivator {
        public ConfirmActivator() {
            super(DRY_RUN_OPTION);
        }
    }

    public static class UseDefaultLocalCacheActivator extends AbstractRejectOptionActivator {
        public UseDefaultLocalCacheActivator() {
            super(NO_RESOLVE_LOCAL_CACHE_OPTION);
        }
    }

    public static class NoResolveLocalCacheActivator extends AbstractRejectOptionActivator {
        public NoResolveLocalCacheActivator() {
            super(USE_DEFAULT_LOCAL_CACHE_OPTION);
        }
    }

    public static class HostsCompleter implements OptionCompleter<CLICompleterInvocation> {
        @Override
        public void complete(CLICompleterInvocation completerInvocation) {
            List<String> values = new ArrayList<>();
            Collection<String> candidates = getCandidates(completerInvocation.getCommandContext());
            String opBuffer = completerInvocation.getGivenCompleteValue();
            if (opBuffer.isEmpty()) {
                values.addAll(candidates);
            } else {
                for (String name : candidates) {
                    if (name.startsWith(opBuffer)) {
                        values.add(name);
                    }
                }
                Collections.sort(values);
            }
            completerInvocation.addAllCompleterValues(values);
        }

        private Collection<String> getCandidates(CommandContext ctx) {
            return CandidatesProviders.HOSTS.getAllCandidates(ctx);
        }
    }

    protected static PathAddress createStandalone() {
        return PathAddress.pathAddress(CORE_SERVICE_INSTALLER);
    }

    protected static PathAddress createHost(final String hostName, final ModelControllerClient client) {
        final PathElement host = PathElement.pathElement(HOST, hostName);
        final PathAddress address = PathAddress.pathAddress(host, CORE_SERVICE_INSTALLER);

        return address;
    }

    /**
     * Convert a list of repositories passed as a command argument into a Model Node object and add it to the Operation passed
     * as an argument.
     *
     * @param op The Model Node of the Operation
     * @param repositories The List of repositories
     * @Throws MalformedURLException If there is an invalid URL
     * @Throws IllegalArgumentException If the format of the repositories in the List is invalid.
     */
    static void addRepositoriesToModelNode(ModelNode op, List<String> repositories) throws CommandException {
        if (repositories == null || repositories.isEmpty()) {
            return;
        }

        ModelNode repositoriesMn = new ModelNode().addEmptyList();
        for (int i = 0; i < repositories.size(); i++) {
            String repoStr = repositories.get(i);
            ModelNode repositoryMn = new ModelNode();
            String idStr;
            String urlStr;
            String[] split = repoStr.split("::");
            try {
                if (split.length == 1) {
                    new URL(repoStr);
                    idStr = "id" + i;
                    urlStr = repoStr;
                } else if (split.length == 2) {
                    idStr = split[0];
                    urlStr = split[1];
                } else {
                    throw new IllegalArgumentException();
                }
                repositoryMn.get(InstMgrConstants.REPOSITORY_ID).set(idStr);
                repositoryMn.get(InstMgrConstants.REPOSITORY_URL).set(urlStr);
                repositoriesMn.add(repositoryMn);
            } catch (Exception w) {
                throw new CommandException("Invalid Repository URL. Valid values are either URLs or ID::URL");
            }
        }
        op.get(InstMgrConstants.REPOSITORIES).set(repositoriesMn);
    }

    static void addManifestVersionsToModelNode(ModelNode op, List<String> manifestVersions) throws CommandException {
        if (manifestVersions == null || manifestVersions.isEmpty()) {
            return;
        }

        ModelNode manifestVersionsMn = new ModelNode().addEmptyList();
        for (int i = 0; i < manifestVersions.size(); i++) {
            String inputStr = manifestVersions.get(i);
            ModelNode manifestVersionMn = new ModelNode();
            String channelId;
            String manifestVersion;
            String[] split = inputStr.split("::");
            try {
                if (split.length == 2) {
                    channelId = split[0];
                    manifestVersion = split[1];
                } else {
                    throw new IllegalArgumentException();
                }
                manifestVersionMn.get(InstMgrConstants.CHANNEL_NAME).set(channelId);
                manifestVersionMn.get(InstMgrConstants.MANIFEST_VERSION).set(manifestVersion);
                manifestVersionsMn.add(manifestVersionMn);
            } catch (Exception w) {
                throw new CommandException(String.format(
                        "Invalid manifest versions definition. Expected string '<channelId>::<manifestVersion>' but got '%s'.",
                        inputStr));
            }
        }
        op.get(InstMgrConstants.MANIFEST_VERSIONS).set(manifestVersionsMn);
    }

    static void addManifestToModelNode(ModelNode modelNode, String manifest) {
        if (manifest == null || "".equals(manifest)) {
            return;
        }

        try {
            ModelNode manifestMn = new ModelNode();
            if (isValidUrl(manifest)) {
                manifestMn.get(InstMgrConstants.MANIFEST_URL).set(manifest);
            } else if (isValidCoordinate(manifest)) {
                manifestMn.get(InstMgrConstants.MANIFEST_GAV).set(manifest);
            } else {
                String validUrlFromPath = isValidUrlFromPath(manifest);
                if (validUrlFromPath != null) {
                    manifestMn.get(InstMgrConstants.MANIFEST_URL).set(validUrlFromPath);
                } else {
                    throw new IllegalArgumentException("Invalid manifest format. It can be an URL, Maven GAV or path");
                }
            }

            modelNode.get(InstMgrConstants.MANIFEST).set(manifestMn);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid manifest format. It can be an URL, Maven GAV or path");
        }
    }

    static boolean isValidUrl(String urlGavOrPath) {
        try {
            new URL(urlGavOrPath);
        } catch (MalformedURLException e) {
            // no valid URL
            return false;
        }
        return true;
    }

    static String isValidUrlFromPath(String urlGavOrPath) {
        try {
            return Paths.get(urlGavOrPath).toUri().toURL().toString();
        } catch (MalformedURLException | InvalidPathException e) {
            // no valid URL/path
            return null;
        }
    }

    static boolean isValidCoordinate(String gav) {
        if (gav.contains("\\") || gav.contains("/")) {
            return false;
        }

        String[] parts = gav.split(":");
        for (String part : parts) {
            if (part == null || "".equals(part)) {
                return false;
            }
        }
        if (parts.length != 2 && parts.length != 3) { // GA or GAV
            return false;
        }

        return true;
    }
}
