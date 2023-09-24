/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.patching.cli;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.util.SimpleTable;
import org.jboss.as.patching.Constants;
import org.jboss.as.patching.tool.PatchOperationBuilder;
import org.jboss.dmr.ModelNode;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.wildfly.core.cli.command.aesh.activator.AbstractDependRejectOptionActivator;
import org.wildfly.core.cli.command.aesh.activator.AbstractRejectOptionActivator;
import org.wildfly.core.cli.command.aesh.activator.HideOptionActivator;

/**
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "info", description = "")
public class PatchInfo extends AbstractDistributionCommand {

    public static class NoStreamsActivator extends AbstractRejectOptionActivator {

        public NoStreamsActivator() {
            super("streams");
        }
    }

    public static class NoPatchIdActivator extends AbstractRejectOptionActivator {

        public NoPatchIdActivator() {
            super("");
        }
    }

    public static class PatchIdNoStreamsActivator extends AbstractDependRejectOptionActivator {

        private static final Set<String> EXPECTED = new HashSet<>();
        private static final Set<String> NOT_EXPECTED = new HashSet<>();

        static {
            // Argument.
            EXPECTED.add("");
            NOT_EXPECTED.add("streams");
        }

        public PatchIdNoStreamsActivator() {
            super(true, EXPECTED, NOT_EXPECTED);
        }
    }

    @Option(name = "patch-stream", hasValue = true, required = false, activator = PatchIdNoStreamsActivator.class)
    private String patchStream;

    @Argument(completer = PatchIdCompleter.class, activator = NoStreamsActivator.class)
    private String patchIdArg;

    @Option(name = "patch-id", completer = PatchIdCompleter.class, activator = HideOptionActivator.class)
    private String patchId;

    @Option(hasValue = false, shortName = 'v', required = false, activator = PatchIdNoStreamsActivator.class)
    boolean verbose;

    @Option(hasValue = false, required = false, activator = NoPatchIdActivator.class)
    boolean streams;

    @Deprecated
    @Option(name = "json-output", hasValue = false, required = false, activator = HideOptionActivator.class)
    boolean jsonOutput;

    public PatchInfo() {
        super("info");
    }

    private String getPatchId() throws CommandException {
        if (patchId != null && patchIdArg != null) {
            throw new CommandException("patch-id argument and options can't be set al together.");
        }
        if (patchId != null) {
            return patchId;
        }
        if (patchIdArg != null && !patchIdArg.isEmpty()) {
            return patchIdArg;
        }
        return null;
    }

    @Override
    protected PatchOperationBuilder createPatchOperationBuilder(CommandContext ctx) throws CommandException {
        if (streams) {
            return PatchOperationBuilder.Factory.streams();
        }
        PatchOperationBuilder builder;
        final String pId = getPatchId();
        if (pId == null) {
            builder = PatchOperationBuilder.Factory.info(patchStream);
        } else {
            builder = PatchOperationBuilder.Factory.info(patchStream, pId, verbose);
        }
        return builder;
    }

    @Override
    protected void handleResponse(CommandContext ctx, ModelNode response) throws CommandException {
        if (getPatchId() != null) {
            final ModelNode result = response.get(ModelDescriptionConstants.RESULT);
            if (!result.isDefined()) {
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
            if (elements.isDefined()) {
                ctx.printLine("");
                ctx.printLine("ELEMENTS");
                for (ModelNode e : elements.asList()) {
                    table = new SimpleTable(2, ctx.getTerminalWidth());
                    table.addLine(new String[]{"Patch ID:", e.get(Constants.PATCH_ID).asString()});
                    table.addLine(new String[]{"Name:", e.get(Constants.NAME).asString()});
                    table.addLine(new String[]{"Type:", e.get(Constants.TYPE).asString()});
                    table.addLine(new String[]{"Description:", e.get(Constants.DESCRIPTION).asString()});
                    ctx.printLine("");
                    ctx.printLine(table.toString(false));
                }
            }
        } else if (jsonOutput) {
            ctx.printLine(response.toJSONString(false));
        } else if (streams) {
            final List<ModelNode> list = response.get(ModelDescriptionConstants.RESULT).asList();
            if (list.size() == 1) {
                ctx.printLine(list.get(0).asString());
            } else {
                final List<String> streams = new ArrayList<String>(list.size());
                for (ModelNode stream : list) {
                    streams.add(stream.asString());
                }
                ctx.printColumns(streams);
            }
        } else {
            final ModelNode result = response.get(ModelDescriptionConstants.RESULT);
            if (!result.isDefined()) {
                return;
            }
            SimpleTable table = new SimpleTable(2, ctx.getTerminalWidth());
            table.addLine(new String[]{"Version:", result.get(Constants.VERSION).asString()});
            addPatchesInfo(result, table);
            ctx.printLine(table.toString(false));
            if (verbose) {
                printLayerPatches(ctx, result, Constants.ADD_ON);
                printLayerPatches(ctx, result, Constants.LAYER);
            }
        }
    }

    private void printLayerPatches(CommandContext ctx, final ModelNode result, final String type) {
        ModelNode layer = result.get(type);
        if (layer.isDefined()) {
            final String header = Character.toUpperCase(type.charAt(0)) + type.substring(1) + ':';
            for (String name : layer.keys()) {
                final ModelNode node = layer.get(name);
                final SimpleTable table = new SimpleTable(2, ctx.getTerminalWidth());
                table.addLine(new String[]{header, name});
                addPatchesInfo(node, table);
                ctx.printLine(lineSeparator + table.toString(false));
            }
        }
    }

    private void addPatchesInfo(final ModelNode result, SimpleTable table) {
        table.addLine(new String[]{"Cumulative patch ID:", result.get(Constants.CUMULATIVE).asString()});
        final List<ModelNode> patches = result.get(Constants.PATCHES).asList();
        final String patchesStr;
        if (patches.isEmpty()) {
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

    @Override
    String getPatchStream() {
        return patchStream;
    }

}
