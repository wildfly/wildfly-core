/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.patching.cli;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import javax.xml.stream.XMLStreamException;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.util.SimpleTable;
import org.jboss.as.patching.Constants;
import org.jboss.as.patching.IoUtils;
import org.jboss.as.patching.PatchingException;
import org.jboss.as.patching.metadata.BundledPatch;
import org.jboss.as.patching.metadata.Identity;
import org.jboss.as.patching.metadata.Patch;
import org.jboss.as.patching.metadata.PatchBundleXml;
import org.jboss.as.patching.metadata.PatchElement;
import org.jboss.as.patching.metadata.PatchXml;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;

/**
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "inspect", description = "", activator = PatchCommand.PatchCommandActivator.class)
public class PatchInspect implements Command<CLICommandInvocation> {

    // Argument comes first, aesh behavior.
    @Argument(required = true)
    private File patchFile;

    @Option(hasValue = false, shortName = 'v', required = false)
    boolean verbose;

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        doInspect(commandInvocation.getCommandContext());
        return CommandResult.SUCCESS;
    }

    private void doInspect(CommandContext ctx) throws CommandException {
        if (patchFile == null) {
            throw new CommandException("No patch path provided");
        }
        if (!patchFile.exists()) {
            throw new CommandException("Failed to locate " + patchFile.getAbsolutePath());
        }
        ZipFile patchZip = null;
        InputStream is = null;
        try {
            patchZip = new ZipFile(patchFile);
            ZipEntry patchXmlEntry = patchZip.getEntry(PatchBundleXml.MULTI_PATCH_XML);
            if (patchXmlEntry == null) {
                patchXmlEntry = patchZip.getEntry(PatchXml.PATCH_XML);
                if (patchXmlEntry == null) {
                    throw new CommandException("Neither " + PatchBundleXml.MULTI_PATCH_XML + " nor " + PatchXml.PATCH_XML
                            + " were found in " + patchFile.getAbsolutePath());
                }
                is = patchZip.getInputStream(patchXmlEntry);
                final Patch patch = PatchXml.parse(is).resolvePatch(null, null);
                displayPatchXml(ctx, patch);
            } else {
                is = patchZip.getInputStream(patchXmlEntry);
                final List<BundledPatch.BundledPatchEntry> patches = PatchBundleXml.parse(is).getPatches();
                displayPatchBundleXml(ctx, patches, patchZip);
            }
        } catch (ZipException e) {
            throw new CommandException("Failed to open " + patchFile.getAbsolutePath(), e);
        } catch (IOException e) {
            throw new CommandException("Failed to open " + patchFile.getAbsolutePath(), e);
        } catch (PatchingException e) {
            throw new CommandException("Failed to resolve parsed patch", e);
        } catch (XMLStreamException e) {
            throw new CommandException("Failed to parse patch.xml", e);
        } finally {
            if (is != null) {
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

    private void displayPatchBundleXml(CommandContext ctx, List<BundledPatch.BundledPatchEntry> patches, ZipFile patchZip) throws CommandException {

        if (patches.isEmpty()) {
            return;
        }

        for (BundledPatch.BundledPatchEntry bundledPatch : patches) {
            final ZipEntry bundledZip = patchZip.getEntry(bundledPatch.getPatchPath());
            if (bundledZip == null) {
                throw new CommandException("Patch file not found in the bundle: " + bundledPatch.getPatchPath());
            }

            InputStream is = null;
            ZipInputStream bundledPatchIs = null;
            try {
                is = patchZip.getInputStream(bundledZip);
                bundledPatchIs = new ZipInputStream(is);
                ZipEntry bundledPatchXml = bundledPatchIs.getNextEntry();
                while (bundledPatchXml != null) {
                    if (PatchXml.PATCH_XML.equals(bundledPatchXml.getName())) {
                        break;
                    }
                    bundledPatchXml = bundledPatchIs.getNextEntry();
                }
                if (bundledPatchXml == null) {
                    throw new CommandLineException("Failed to locate " + PatchXml.PATCH_XML + " in bundled patch " + bundledPatch.getPatchPath());
                }
                final Patch patch = PatchXml.parse(bundledPatchIs).resolvePatch(null, null);

                if (verbose) {
                    // to make the separation better in the verbose mode
                    ctx.printLine("CONTENT OF " + bundledPatch.getPatchPath() + ':' + Util.LINE_SEPARATOR);
                }

                displayPatchXml(ctx, patch);
                ctx.printLine("");
            } catch (Exception e) {
                throw new CommandException("Failed to inspect " + bundledPatch.getPatchPath(), e);
            } finally {
                IoUtils.safeClose(bundledPatchIs);
                IoUtils.safeClose(is);
            }
        }
    }

    private void displayPatchXml(CommandContext ctx, Patch patch) throws CommandException {
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

        if (verbose) {
            ctx.printLine("");
            ctx.printLine("ELEMENTS");
            for (PatchElement e : patch.getElements()) {
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
}
