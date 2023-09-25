/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.cli;

import static java.lang.System.getProperty;
import static java.lang.System.getSecurityManager;
import static java.lang.System.getenv;
import static java.security.AccessController.doPrivileged;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.handlers.CommandHandlerWithHelp;
import org.jboss.as.cli.handlers.DefaultFilenameTabCompleter;
import org.jboss.as.cli.handlers.FilenameTabCompleter;
import org.jboss.as.cli.handlers.SimpleTabCompleter;
import org.jboss.as.cli.handlers.WindowsFilenameTabCompleter;
import org.jboss.as.cli.impl.ArgumentWithListValue;
import org.jboss.as.cli.impl.ArgumentWithValue;
import org.jboss.as.cli.impl.ArgumentWithoutValue;
import org.jboss.as.cli.impl.DefaultCompleter;
import org.jboss.as.cli.impl.FileSystemPathArgument;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.as.cli.util.SimpleTable;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.patching.Constants;
import org.jboss.as.patching.IoUtils;
import org.jboss.as.patching.PatchingException;
import org.jboss.as.patching.logging.PatchLogger;
import org.jboss.as.patching.metadata.BundledPatch.BundledPatchEntry;
import org.jboss.as.patching.metadata.Identity;
import org.jboss.as.patching.metadata.Patch;
import org.jboss.as.patching.metadata.PatchBundleXml;
import org.jboss.as.patching.metadata.PatchElement;
import org.jboss.as.patching.metadata.PatchXml;
import org.jboss.as.patching.tool.PatchOperationBuilder;
import org.jboss.as.patching.tool.PatchOperationTarget;
import org.jboss.dmr.ModelNode;
import org.wildfly.security.manager.action.ReadEnvironmentPropertyAction;
import org.wildfly.security.manager.action.ReadPropertyAction;

/**
 * WARNING: NO MORE IN USE, REPLACED BY PatchCommand.
 *
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2012 Red Hat Inc.
 */
public class PatchHandler extends CommandHandlerWithHelp {

    static final String PATCH = "patch";
    static final String APPLY = "apply";
    static final String ROLLBACK = "rollback";
    static final String HISTORY = "history";
    static final String INFO = "info";
    static final String INSPECT = "inspect";

    private final ArgumentWithValue host;

    private final ArgumentWithValue action;

    private final ArgumentWithoutValue path;

    private final ArgumentWithValue patchId;
    private final ArgumentWithValue patchStream;
    private final ArgumentWithoutValue rollbackTo;
    private final ArgumentWithValue resetConfiguration;

    private final ArgumentWithoutValue overrideModules;
    private final ArgumentWithoutValue overrideAll;
    private final ArgumentWithListValue override;
    private final ArgumentWithListValue preserve;

    private final ArgumentWithoutValue distribution;
    private final ArgumentWithoutValue modulePath;
    private final ArgumentWithoutValue bundlePath;

    private final ArgumentWithoutValue verbose;

    private final ArgumentWithoutValue streams;

    private final ArgumentWithoutValue excludeAgedOut;

    /** whether the output should be displayed in a human friendly form or JSON - tools friendly */
    private final ArgumentWithoutValue jsonOutput;

    private static final String lineSeparator = getSecurityManager() == null ? getProperty("line.separator") : doPrivileged(new ReadPropertyAction("line.separator"));

    public PatchHandler(final CommandContext context) {
        super(PATCH, false);

        action = new ArgumentWithValue(this, new SimpleTabCompleter(new String[]{APPLY, ROLLBACK, HISTORY, INFO, INSPECT}), 0, "--action");

        host = new ArgumentWithValue(this, new DefaultCompleter(CandidatesProviders.HOSTS), "--host") {
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
                boolean connected = ctx.getControllerHost() != null;
                return connected && ctx.isDomainMode() && super.canAppearNext(ctx);
            }
        };

        // apply & rollback arguments

        overrideModules = new ArgumentWithoutValue(this, "--override-modules") {
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
                if (canOnlyAppearAfterActions(ctx, APPLY, ROLLBACK)) {
                    return super.canAppearNext(ctx);
                }
                return false;
            }
        };
        overrideModules.addRequiredPreceding(action);

        overrideAll = new ArgumentWithoutValue(this, "--override-all") {
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
                if (canOnlyAppearAfterActions(ctx, APPLY, ROLLBACK)) {
                    return super.canAppearNext(ctx);
                }
                return false;
            }
        };
        overrideAll.addRequiredPreceding(action);

        override = new ArgumentWithListValue(this, "--override") {
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
                if (canOnlyAppearAfterActions(ctx, APPLY, ROLLBACK)) {
                    return super.canAppearNext(ctx);
                }
                return false;
            }
        };
        override.addRequiredPreceding(action);

        preserve = new ArgumentWithListValue(this, "--preserve") {
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
                if (canOnlyAppearAfterActions(ctx, APPLY, ROLLBACK)) {
                    return super.canAppearNext(ctx);
                }
                return false;
            }
        };
        preserve.addRequiredPreceding(action);

        // apply arguments

        final FilenameTabCompleter pathCompleter = Util.isWindows() ? new WindowsFilenameTabCompleter(context) : new DefaultFilenameTabCompleter(context);
        path = new FileSystemPathArgument(this, pathCompleter, 1, "--path") {
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
                if (canOnlyAppearAfterActions(ctx, APPLY, INSPECT)) {
                    return super.canAppearNext(ctx);
                }
                return false;
            }

        };
        path.addRequiredPreceding(action);

        // rollback arguments

        patchId = new ArgumentWithValue(this, 1, "--patch-id") {
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
                if (canOnlyAppearAfterActions(ctx, INFO, ROLLBACK)) {
                    return super.canAppearNext(ctx);
                }
                return false;
            }
        };
        patchId.addRequiredPreceding(action);

        patchStream = new ArgumentWithValue(this, "--patch-stream") {
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
                if (canOnlyAppearAfterActions(ctx, HISTORY, INFO, ROLLBACK)) {
                    return super.canAppearNext(ctx);
                }
                return false;
            }
        };
        patchStream.addRequiredPreceding(action);

        rollbackTo = new ArgumentWithoutValue(this, "--rollback-to") {
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
                if (canOnlyAppearAfterActions(ctx, ROLLBACK)) {
                    return super.canAppearNext(ctx);
                }
                return false;
            }
        };
        rollbackTo.addRequiredPreceding(action);

        resetConfiguration = new ArgumentWithValue(this, SimpleTabCompleter.BOOLEAN, "--reset-configuration") {
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
                if (canOnlyAppearAfterActions(ctx, ROLLBACK)) {
                    return super.canAppearNext(ctx);
                }
                return false;
            }
        };
        resetConfiguration.addRequiredPreceding(action);

        distribution = new FileSystemPathArgument(this, pathCompleter, "--distribution") {
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
                if (ctx.getModelControllerClient() == null && canOnlyAppearAfterActions(ctx, APPLY, ROLLBACK, HISTORY, INFO)) {
                    return super.canAppearNext(ctx);
                }
                return false;
            }
        };

        modulePath = new FileSystemPathArgument(this, pathCompleter, "--module-path") {
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
                if (ctx.getModelControllerClient() == null && canOnlyAppearAfterActions(ctx, APPLY, ROLLBACK, HISTORY, INFO)) {
                    return super.canAppearNext(ctx);
                }
                return false;
            }
        };

        bundlePath = new FileSystemPathArgument(this, pathCompleter, "--bundle-path") {
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
                if (ctx.getModelControllerClient() == null && canOnlyAppearAfterActions(ctx, APPLY, ROLLBACK, HISTORY, INFO)) {
                    return super.canAppearNext(ctx);
                }
                return false;
            }
        };

        verbose = new ArgumentWithoutValue(this, "--verbose", "-v") {
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
                if (canOnlyAppearAfterActions(ctx, INFO, INSPECT)) {
                    return super.canAppearNext(ctx);
                }
                return false;
            }
        };
        verbose.addRequiredPreceding(action);

        jsonOutput = new ArgumentWithoutValue(this, "--json-output") {
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
//                if (canOnlyAppearAfterActions(ctx, INFO)) {
//                    return super.canAppearNext(ctx);
//                }
                // hide from the tab-completion for now
                return false;
            }
        };

        streams = new ArgumentWithoutValue(this, "--streams") {
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
                if (canOnlyAppearAfterActions(ctx, INFO)) {
                    return super.canAppearNext(ctx);
                }
                return false;
            }
        };
        streams.addRequiredPreceding(action);
        streams.addCantAppearAfter(verbose);
        verbose.addCantAppearAfter(streams);
        streams.addCantAppearAfter(patchStream);
        patchStream.addCantAppearAfter(streams);
        streams.addCantAppearAfter(patchId);
        patchId.addCantAppearAfter(streams);

        excludeAgedOut = new ArgumentWithoutValue(this, "--exclude-aged-out") {
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
                if (canOnlyAppearAfterActions(ctx, HISTORY)) {
                    return super.canAppearNext(ctx);
                }
                return false;
            }
        };
        excludeAgedOut.addRequiredPreceding(action);
    }

    private boolean canOnlyAppearAfterActions(CommandContext ctx, String... actions) {
        final String actionStr = this.action.getValue(ctx.getParsedCommandLine());
        if(actionStr == null || actions.length == 0) {
            return false;
        }
        return Arrays.asList(actions).contains(actionStr);
    }

    @Override
    protected void doHandle(CommandContext ctx) throws CommandLineException {
        final ParsedCommandLine parsedLine = ctx.getParsedCommandLine();
        if(host.isPresent(parsedLine) && !ctx.isDomainMode()) {
            throw new CommandFormatException("The --host option is not available in the current context. Connection to the controller might be unavailable or not running in domain mode.");
        }
        final String action = this.action.getValue(parsedLine);
        if(INSPECT.equals(action)) {
            doInspect(ctx);
            return;
        }
        final PatchOperationTarget target = createPatchOperationTarget(ctx);
        final PatchOperationBuilder builder = createPatchOperationBuilder(parsedLine);
        final ModelNode response;
        try {
            response = builder.execute(target);
        } catch (Exception e) {
            throw new CommandLineException(action + " failed", e);
        }
        if (!Util.isSuccess(response)) {
            final ModelNode fd = response.get(ModelDescriptionConstants.FAILURE_DESCRIPTION);
            if(!fd.isDefined()) {
                throw new CommandLineException("Failed to apply patch: " + response.asString());
            }
            if(fd.has(Constants.CONFLICTS)) {
                final StringBuilder buf = new StringBuilder();
                buf.append(fd.get(Constants.MESSAGE).asString()).append(": ");
                final ModelNode conflicts = fd.get(Constants.CONFLICTS);
                String title = "";
                if(conflicts.has(Constants.BUNDLES)) {
                    formatConflictsList(buf, conflicts, "", Constants.BUNDLES);
                    title = ", ";
                }
                if(conflicts.has(Constants.MODULES)) {
                    formatConflictsList(buf, conflicts, title, Constants.MODULES);
                    title = ", ";
                }
                if(conflicts.has(Constants.MISC)) {
                    formatConflictsList(buf, conflicts, title, Constants.MISC);
                }
                buf.append(lineSeparator).append("Use the --override-all, --override=[] or --preserve=[] arguments in order to resolve the conflict.");
                throw new CommandLineException(buf.toString());
            } else {
                throw new CommandLineException(Util.getFailureDescription(response));
            }
        }

        if(INFO.equals(action)) {
            if(patchId.getValue(parsedLine) != null) {
                final ModelNode result = response.get(ModelDescriptionConstants.RESULT);
                if(!result.isDefined()) {
                    return;
                }
                SimpleTable table = new SimpleTable(2, ctx.getTerminalWidth());
                table.addLine(new String[]{"Patch ID:", result.get(Constants.PATCH_ID).asString()});
                table.addLine(new String[]{"Type:", result.get(Constants.TYPE).asString()});
                table.addLine(new String[]{"Identity name:", result.get(Constants.IDENTITY_NAME).asString()});
                table.addLine(new String[]{"Identity version:", result.get(Constants.IDENTITY_VERSION).asString()});
                table.addLine(new String[]{"Description:", result.get(Constants.DESCRIPTION).asString()});
                if (result.hasDefined(Constants.LINK)) {
                    table.addLine(new String[]{"Link:", result.get(Constants.LINK).asString()});
                }
                ctx.printLine(table.toString(false));

                final ModelNode elements = result.get(Constants.ELEMENTS);
                if(elements.isDefined()) {
                    ctx.printLine("");
                    ctx.printLine("ELEMENTS");
                    for(ModelNode e : elements.asList()) {
                        table = new SimpleTable(2, ctx.getTerminalWidth());
                        table.addLine(new String[]{"Patch ID:", e.get(Constants.PATCH_ID).asString()});
                        table.addLine(new String[]{"Name:", e.get(Constants.NAME).asString()});
                        table.addLine(new String[]{"Type:", e.get(Constants.TYPE).asString()});
                        table.addLine(new String[]{"Description:", e.get(Constants.DESCRIPTION).asString()});
                        ctx.printLine("");
                        ctx.printLine(table.toString(false));
                    }
                }
            } else if(jsonOutput.isPresent(parsedLine)) {
                ctx.printLine(response.toJSONString(false));
            } else if(streams.isPresent(parsedLine)) {
                final List<ModelNode> list = response.get(ModelDescriptionConstants.RESULT).asList();
                if(list.size() == 1) {
                    ctx.printLine(list.get(0).asString());
                } else {
                    final List<String> streams = new ArrayList<String>(list.size());
                    for(ModelNode stream : list) {
                        streams.add(stream.asString());
                    }
                    ctx.printColumns(streams);
                }
            } else {
                final ModelNode result = response.get(ModelDescriptionConstants.RESULT);
                if(!result.isDefined()) {
                    return;
                }
                SimpleTable table = new SimpleTable(2, ctx.getTerminalWidth());
                table.addLine(new String[]{"Version:", result.get(Constants.VERSION).asString()});
                addPatchesInfo(result, table);
                ctx.printLine(table.toString(false));
                if(verbose.isPresent(parsedLine)) {
                    printLayerPatches(ctx, result, Constants.ADD_ON);
                    printLayerPatches(ctx, result, Constants.LAYER);
                }
            }
        } else {
            ctx.printLine(response.toJSONString(false));
        }
    }

    protected void printLayerPatches(CommandContext ctx, final ModelNode result, final String type) {
        ModelNode layer = result.get(type);
        if(layer.isDefined()) {
            final String header = Character.toUpperCase(type.charAt(0)) + type.substring(1) + ':';
            for(String name : layer.keys()) {
                final ModelNode node = layer.get(name);
                final SimpleTable table = new SimpleTable(2, ctx.getTerminalWidth());
                table.addLine(new String[]{header, name});
                addPatchesInfo(node, table);
                ctx.printLine(lineSeparator + table.toString(false));
            }
        }
    }

    protected void addPatchesInfo(final ModelNode result, SimpleTable table) {
        table.addLine(new String[]{"Cumulative patch ID:", result.get(Constants.CUMULATIVE).asString()});
        final List<ModelNode> patches = result.get(Constants.PATCHES).asList();
        final String patchesStr;
        if(patches.isEmpty()) {
            patchesStr = "none";
        } else {
            final StringBuilder buf = new StringBuilder();
            buf.append(patches.get(0).asString());
            for (int i = 1; i < patches.size(); ++i) {
                buf.append(',').append(patches.get(i).asString());
            }
            patchesStr = buf.toString();
        }
        table.addLine(new String[]{"One-off patches:", patchesStr});
    }

    protected void doInspect(CommandContext ctx) throws CommandLineException {
        final ParsedCommandLine parsedLine = ctx.getParsedCommandLine();
        final String patchPath = path.getValue(parsedLine, true);
        final File patchFile = new File(patchPath);
        if(!patchFile.exists()) {
            throw new CommandLineException("Failed to locate " + patchFile.getAbsolutePath());
        }
        ZipFile patchZip = null;
        InputStream is = null;
        try {
            patchZip = new ZipFile(patchFile);
            ZipEntry patchXmlEntry = patchZip.getEntry(PatchBundleXml.MULTI_PATCH_XML);
            if(patchXmlEntry == null) {
                patchXmlEntry = patchZip.getEntry(PatchXml.PATCH_XML);
                if(patchXmlEntry == null) {
                    throw new CommandLineException("Neither " + PatchBundleXml.MULTI_PATCH_XML + " nor " + PatchXml.PATCH_XML+
                            " were found in " + patchFile.getAbsolutePath());
                }
                is = patchZip.getInputStream(patchXmlEntry);
                final Patch patch = PatchXml.parse(is).resolvePatch(null, null);
                displayPatchXml(ctx, patch);
            } else {
                is = patchZip.getInputStream(patchXmlEntry);
                final List<BundledPatchEntry> patches = PatchBundleXml.parse(is).getPatches();
                displayPatchBundleXml(ctx, patches, patchZip);
            }
        } catch (ZipException e) {
            throw new CommandLineException("Failed to open " + patchFile.getAbsolutePath(), e);
        } catch (IOException e) {
            throw new CommandLineException("Failed to open " + patchFile.getAbsolutePath(), e);
        } catch (PatchingException e) {
            throw new CommandLineException("Failed to resolve parsed patch", e);
        } catch (XMLStreamException e) {
            throw new CommandLineException("Failed to parse patch.xml", e);
        } finally {
            if(is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                }
            }
            if (patchZip != null) {
                try {
                    patchZip.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private void displayPatchBundleXml(CommandContext ctx, List<BundledPatchEntry> patches, ZipFile patchZip) throws CommandLineException {

        if(patches.isEmpty()) {
            return;
        }

        for(BundledPatchEntry bundledPatch : patches) {
            final ZipEntry bundledZip = patchZip.getEntry(bundledPatch.getPatchPath());
            if(bundledZip == null) {
                throw new CommandLineException("Patch file not found in the bundle: " + bundledPatch.getPatchPath());
            }

            InputStream is = null;
            ZipInputStream bundledPatchIs = null;
            try {
                is = patchZip.getInputStream(bundledZip);
                bundledPatchIs = new ZipInputStream(is);
                ZipEntry bundledPatchXml = bundledPatchIs.getNextEntry();
                while(bundledPatchXml != null) {
                    if(PatchXml.PATCH_XML.equals(bundledPatchXml.getName())) {
                        break;
                    }
                    bundledPatchXml = bundledPatchIs.getNextEntry();
                }
                if(bundledPatchXml == null) {
                    throw new CommandLineException("Failed to locate " + PatchXml.PATCH_XML + " in bundled patch " + bundledPatch.getPatchPath());
                }
                final Patch patch = PatchXml.parse(bundledPatchIs).resolvePatch(null, null);

                if(verbose.isPresent(ctx.getParsedCommandLine())) {
                    // to make the separation better in the verbose mode
                    ctx.printLine("CONTENT OF " + bundledPatch.getPatchPath() + ':' + Util.LINE_SEPARATOR);
                }

                displayPatchXml(ctx, patch);
                ctx.printLine("");
            } catch (Exception e) {
                throw new CommandLineException("Failed to inspect " + bundledPatch.getPatchPath(), e);
            } finally {
                IoUtils.safeClose(bundledPatchIs);
                IoUtils.safeClose(is);
            }
        }
    }

    private void displayPatchXml(CommandContext ctx, Patch patch) throws CommandLineException {
        final Identity identity = patch.getIdentity();
        SimpleTable table = new SimpleTable(2, ctx.getTerminalWidth());
        table.addLine(new String[]{"Patch ID:", patch.getPatchId()});
        table.addLine(new String[]{"Type:", identity.getPatchType().getName()});
        table.addLine(new String[]{"Identity name:", identity.getName()});
        table.addLine(new String[]{"Identity version:", identity.getVersion()});
        table.addLine(new String[]{"Description:", patch.getDescription() == null ? "n/a" : patch.getDescription()});
        if (patch.getLink() != null) {
            table.addLine(new String[]{"Link:", patch.getLink()});
        }
        ctx.printLine(table.toString(false));

        if(verbose.isPresent(ctx.getParsedCommandLine())) {
            ctx.printLine("");
            ctx.printLine("ELEMENTS");
            for(PatchElement e : patch.getElements()) {
                table = new SimpleTable(2, ctx.getTerminalWidth());
                table.addLine(new String[]{"Patch ID:", e.getId()});
                table.addLine(new String[]{"Name:", e.getProvider().getName()});
                table.addLine(new String[]{"Type:", e.getProvider().isAddOn() ? Constants.ADD_ON : Constants.LAYER});
                table.addLine(new String[]{"Description:", e.getDescription()});
                ctx.printLine("");
                ctx.printLine(table.toString(false));
            }
        }
    }

    protected void formatConflictsList(final StringBuilder buf, final ModelNode conflicts, String title, String contentType) {
        buf.append(title);
        final List<ModelNode> list = conflicts.get(contentType).asList();
        int i = 0;
        while(i < list.size()) {
            final ModelNode item = list.get(i++);
            buf.append(item.asString());
            if(i < list.size()) {
                buf.append(", ");
            }
        }
    }

    private PatchOperationBuilder createPatchOperationBuilder(ParsedCommandLine args) throws CommandFormatException {
        final String action = this.action.getValue(args, true);

        PatchOperationBuilder builder;
        if (APPLY.equals(action)) {
            final String path = this.path.getValue(args, true);

            final File f = new File(path);
            if(!f.exists()) {
                // i18n is never used for CLI exceptions
                throw new CommandFormatException("Path " + f.getAbsolutePath() + " doesn't exist.");
            }
            if(f.isDirectory()) {
                throw new CommandFormatException(f.getAbsolutePath() + " is a directory.");
            }
            builder = PatchOperationBuilder.Factory.patch(f);
        } else if (ROLLBACK.equals(action)) {
            String resetConfigValue = resetConfiguration.getValue(args, true);
            boolean resetConfig;
            if(Util.TRUE.equalsIgnoreCase(resetConfigValue)) {
                resetConfig = true;
            } else if(Util.FALSE.equalsIgnoreCase(resetConfigValue)) {
                resetConfig = false;
            } else {
                throw new CommandFormatException("Unexpected value for --reset-configuration (only true and false are allowed): " + resetConfigValue);
            }
            final String patchStream = this.patchStream.getValue(args);
            if(patchId.isPresent(args)) {
                final String id = patchId.getValue(args, true);
                final boolean rollbackTo = this.rollbackTo.isPresent(args);
                builder = PatchOperationBuilder.Factory.rollback(patchStream, id, rollbackTo, resetConfig);
            } else {
                builder = PatchOperationBuilder.Factory.rollbackLast(patchStream, resetConfig);
            }
        } else if (INFO.equals(action)) {
            if(streams.isPresent(args)) {
                return PatchOperationBuilder.Factory.streams();
            }
            final String patchStream = this.patchStream.getValue(args);
            final String pId = patchId.getValue(args);
            if(pId == null) {
                builder = PatchOperationBuilder.Factory.info(patchStream);
            } else {
                builder = PatchOperationBuilder.Factory.info(patchStream, pId, verbose.isPresent(args));
            }
            return builder;
        } else if (HISTORY.equals(action)) {
            final String patchStream = this.patchStream.getValue(args);
            builder = PatchOperationBuilder.Factory.history(patchStream, excludeAgedOut.isPresent(args));
            return builder;
        } else {
            throw new CommandFormatException("Unrecognized action '" + action + "'");
        }

        if (overrideModules.isPresent(args)) {
            builder.ignoreModuleChanges();
        }
        if (overrideAll.isPresent(args)) {
            builder.overrideAll();
        }
        if (override.isPresent(args)) {
            final String overrideList = override.getValue(args);
            if(overrideList == null || overrideList.isEmpty()) {
                throw new CommandFormatException(override.getFullName() + " is missing value.");
            }
            for (String path : overrideList.split(",+")) {
                builder.overrideItem(path);
            }
        }
        if (preserve.isPresent(args)) {
            final String preserveList = preserve.getValue(args);
            if(preserveList == null || preserveList.isEmpty()) {
                throw new CommandFormatException(preserve.getFullName() + " is missing value.");
            }
            for (String path : preserveList.split(",+")) {
                builder.preserveItem(path);
            }
        }
        return builder;
    }

    private PatchOperationTarget createPatchOperationTarget(CommandContext ctx) throws CommandLineException {
        final PatchOperationTarget target;
        final ParsedCommandLine args = ctx.getParsedCommandLine();
        if (ctx.getModelControllerClient() != null) {
            if(distribution.isPresent(args)) {
                throw new CommandFormatException(distribution.getFullName() + " is not allowed when connected to the controller.");
            }
            if(modulePath.isPresent(args)) {
                throw new CommandFormatException(modulePath.getFullName() + " is not allowed when connected to the controller.");
            }
            if(bundlePath.isPresent(args)) {
                throw new CommandFormatException(bundlePath.getFullName() + " is not allowed when connected to the controller.");
            }
            if (ctx.isDomainMode()) {
                String hostName = host.getValue(args, true);
                target = PatchOperationTarget.createHost(hostName, ctx.getModelControllerClient());
            } else {
                target = PatchOperationTarget.createStandalone(ctx.getModelControllerClient());
            }
        } else {
            final String jbossHome = getJBossHome(args);
            final File root = new File(jbossHome);
            final List<File> modules = getFSArgument(modulePath, args, root, "modules");
            final List<File> bundles = getFSArgument(bundlePath, args, root, "bundles");
            try {
                target = PatchOperationTarget.createLocal(root, modules, bundles);
            } catch (Exception e) {
                throw new CommandLineException("Unable to apply patch to local JBOSS_HOME=" + jbossHome, e);
            }
        }
        return target;
    }

    private static final String HOME = "JBOSS_HOME";
    private static final String HOME_DIR = "jboss.home.dir";

    private String getJBossHome(final ParsedCommandLine args) {
        final String targetDistro = distribution.getValue(args);
        if(targetDistro != null) {
            return targetDistro;
        }

        String resolved = getSecurityManager() == null ? getenv(HOME) : doPrivileged(new ReadEnvironmentPropertyAction(HOME));
        if (resolved == null) {
            resolved = getSecurityManager() == null ? getProperty(HOME_DIR) : doPrivileged(new ReadPropertyAction(HOME_DIR));
        }
        if (resolved == null) {
            throw PatchLogger.ROOT_LOGGER.cliFailedToResolveDistribution();
        }
        return resolved;
    }

    private static List<File> getFSArgument(final ArgumentWithoutValue arg, final ParsedCommandLine args, final File root, final String param) {
        final String value = arg.getValue(args);
        if (value != null) {
            final String[] values = value.split(Pattern.quote(File.pathSeparator));
            if (values.length == 1) {
                return Collections.singletonList(new File(value));
            }
            final List<File> resolved = new ArrayList<File>(values.length);
            for (final String path : values) {
                resolved.add(new File(path));
            }
            return resolved;
        }
        return Collections.singletonList(new File(root, param));
    }
}
