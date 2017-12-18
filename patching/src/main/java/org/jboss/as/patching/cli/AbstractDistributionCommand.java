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
package org.jboss.as.patching.cli;

import java.io.File;
import static java.lang.System.getProperty;
import static java.lang.System.getSecurityManager;
import static java.lang.System.getenv;
import static java.security.AccessController.doPrivileged;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.completer.OptionCompleter;
import org.aesh.command.converter.Converter;
import org.aesh.command.impl.internal.ParsedCommand;
import org.aesh.command.option.Option;
import org.aesh.command.validator.OptionValidatorException;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.handlers.FilenameTabCompleter;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.as.patching.Constants;
import org.jboss.as.patching.logging.PatchLogger;
import org.jboss.as.patching.tool.PatchOperationBuilder;
import org.jboss.as.patching.tool.PatchOperationTarget;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.cli.command.aesh.CLICompleterInvocation;
import org.wildfly.core.cli.command.aesh.CLIConverterInvocation;
import org.wildfly.core.cli.command.aesh.activator.AbstractOptionActivator;
import org.wildfly.core.cli.command.aesh.activator.DomainOptionActivator;
import org.wildfly.security.manager.action.ReadEnvironmentPropertyAction;
import org.wildfly.security.manager.action.ReadPropertyAction;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;

/**
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "abstract-distribution-cmd", description = "")
public abstract class AbstractDistributionCommand implements Command<CLICommandInvocation> {

    public static class FilePathConverter implements Converter<List<File>, CLIConverterInvocation> {

        @Override
        public List<File> convert(CLIConverterInvocation converterInvocation) throws OptionValidatorException {
            final String[] values = converterInvocation.getInput().split(Pattern.quote(File.pathSeparator));
            CommandContext ctx = converterInvocation.getCommandContext();
            List<File> files = new ArrayList<>();
            for (String value : values) {
                FilenameTabCompleter pathCompleter = FilenameTabCompleter.newCompleter(ctx);
                if (value != null) {
                    if (value.length() >= 0 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
                        value = value.substring(1, value.length() - 1);
                    }
                    value = pathCompleter.translatePath(value);
                    File f = new File(value);
                    if (!f.exists()) {
                        throw new OptionValidatorException("File " + f.getAbsolutePath() + " does not exist.");
                    }
                    files.add(f);
                }
            }
            return files;
        }

    }
    public static class FilePathCompleter implements OptionCompleter<CLICompleterInvocation> {

        @Override
        public void complete(CLICompleterInvocation completerInvocation) {
            List<String> candidates = new ArrayList<>();
            final String[] values = completerInvocation.getGivenCompleteValue().split(Pattern.quote(File.pathSeparator));
            String path = values[values.length - 1];
            FilenameTabCompleter pathCompleter
                    = FilenameTabCompleter.newCompleter(completerInvocation.getCommandContext());
            int cursor = pathCompleter.complete(completerInvocation.
                    getCommandContext(),
                    path, path.length(), candidates);
            completerInvocation.addAllCompleterValues(candidates);
            completerInvocation.setOffset(completerInvocation.getGivenCompleteValue().length() - cursor);
            completerInvocation.setAppendSpace(false);
        }

    }
    public static class HostsActivator extends AbstractOptionActivator implements DomainOptionActivator {

        @Override
        public boolean isActivated(ParsedCommand processedCommand) {
            return (getCommandContext().getModelControllerClient() != null)
                    && getCommandContext().isDomainMode();
        }
    };

    public static class DisconnectedActivator extends AbstractOptionActivator {

        @Override
        public boolean isActivated(ParsedCommand processedCommand) {
            return getCommandContext().getModelControllerClient() == null;
        }
    };

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

    @Option(completer = HostsCompleter.class, activator = HostsActivator.class)
    private String host;

    @Option(required = false, activator = DisconnectedActivator.class)
    private File distribution;

    @Option(name = "module-path", completer = FilePathCompleter.class, converter = FilePathConverter.class,
            required = false, activator = DisconnectedActivator.class)
    private List<File> modulePath;

    @Option(name = "bundle-path", converter = FilePathConverter.class,
            required = false)
    private List<File> bundlePath;

    private final String action;

    static final String lineSeparator = getSecurityManager() == null ? getProperty("line.separator") : doPrivileged(new ReadPropertyAction("line.separator"));

    protected AbstractDistributionCommand(String action) {
        this.action = action;
    }

    String getHost() {
        return host;
    }

    String getPatchStream() {
        return null;
    }

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        if (host != null && !commandInvocation.getCommandContext().isDomainMode()) {
            throw new CommandException("The --host option is not available in the current context. "
                    + "Connection to the controller might be unavailable or not running in domain mode.");
        } else if (host == null && commandInvocation.getCommandContext().isDomainMode()) {
            throw new CommandException("The --host option must be used in domain mode.");
        }
        final PatchOperationTarget target = createPatchOperationTarget(commandInvocation.getCommandContext());
        final PatchOperationBuilder builder = createPatchOperationBuilder(commandInvocation.getCommandContext());
        final ModelNode response;
        try {
            response = builder.execute(target);
        } catch (Exception e) {
            throw new CommandException(action + " failed", e);
        }
        if (!Util.isSuccess(response)) {
            final ModelNode fd = response.get(ModelDescriptionConstants.FAILURE_DESCRIPTION);
            if (!fd.isDefined()) {
                throw new CommandException("Failed to apply patch: " + response.asString());
            }
            if (fd.has(Constants.CONFLICTS)) {
                final StringBuilder buf = new StringBuilder();
                buf.append(fd.get(Constants.MESSAGE).asString()).append(": ");
                final ModelNode conflicts = fd.get(Constants.CONFLICTS);
                String title = "";
                if (conflicts.has(Constants.BUNDLES)) {
                    formatConflictsList(buf, conflicts, "", Constants.BUNDLES);
                    title = ", ";
                }
                if (conflicts.has(Constants.MODULES)) {
                    formatConflictsList(buf, conflicts, title, Constants.MODULES);
                    title = ", ";
                }
                if (conflicts.has(Constants.MISC)) {
                    formatConflictsList(buf, conflicts, title, Constants.MISC);
                }
                buf.append(lineSeparator).append("Use the --override-all, --override=[] or --preserve=[] arguments in order to resolve the conflict.");
                throw new CommandException(buf.toString());
            } else {
                throw new CommandException(Util.getFailureDescription(response));
            }
        }
        handleResponse(commandInvocation.getCommandContext(), response);
        return CommandResult.SUCCESS;
    }

    protected void handleResponse(CommandContext ctx, ModelNode response) throws CommandException {
        ctx.printLine(response.toJSONString(false));
    }

    protected abstract PatchOperationBuilder createPatchOperationBuilder(CommandContext ctx) throws CommandException;

    PatchOperationTarget createPatchOperationTarget(CommandContext ctx) throws CommandException {
        final PatchOperationTarget target;
        final ParsedCommandLine args = ctx.getParsedCommandLine();
        if (ctx.getModelControllerClient() != null) {
            if (distribution != null) {
                throw new CommandException("--distribution is not allowed when connected to the controller.");
            }
            if (modulePath != null) {
                throw new CommandException("--module-path is not allowed when connected to the controller.");
            }
            if (bundlePath != null) {
                throw new CommandException("--bundle-path is not allowed when connected to the controller.");
            }
            if (ctx.isDomainMode()) {
                target = PatchOperationTarget.createHost(host, ctx.getModelControllerClient());
            } else {
                target = PatchOperationTarget.createStandalone(ctx.getModelControllerClient());
            }
        } else {
            final File root = getJBossHome();
            final List<File> modules = getFSArgument(modulePath, args, root, "modules");
            final List<File> bundles = getFSArgument(bundlePath, args, root, "bundles");
            try {
                target = PatchOperationTarget.createLocal(root, modules, bundles);
            } catch (Exception e) {
                throw new CommandException("Unable to apply patch to local JBOSS_HOME=" + root, e);
            }
        }
        return target;
    }

    private static final String HOME = "JBOSS_HOME";
    private static final String HOME_DIR = "jboss.home.dir";

    private File getJBossHome() {
        if (distribution != null) {
            return distribution;
        }

        String resolved = getSecurityManager() == null ? getenv(HOME) : doPrivileged(new ReadEnvironmentPropertyAction(HOME));
        if (resolved == null) {
            resolved = getSecurityManager() == null ? getProperty(HOME_DIR) : doPrivileged(new ReadPropertyAction(HOME_DIR));
        }
        if (resolved == null) {
            throw PatchLogger.ROOT_LOGGER.cliFailedToResolveDistribution();
        }
        return new File(resolved);
    }

    private static List<File> getFSArgument(List<File> files, final ParsedCommandLine args, final File root, final String param) {
        if (files != null) {
            return files;
        }
        return Collections.singletonList(new File(root, param));
    }

    private static void formatConflictsList(final StringBuilder buf, final ModelNode conflicts, String title, String contentType) {
        buf.append(title);
        final List<ModelNode> list = conflicts.get(contentType).asList();
        int i = 0;
        while (i < list.size()) {
            final ModelNode item = list.get(i++);
            buf.append(item.asString());
            if (i < list.size()) {
                buf.append(", ");
            }
        }
    }

}
