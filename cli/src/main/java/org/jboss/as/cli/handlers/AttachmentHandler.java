/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.jboss.as.cli.Attachments;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.ArgumentWithValue;
import org.jboss.as.cli.impl.ArgumentWithoutValue;
import org.jboss.as.cli.impl.FileSystemPathArgument;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author jdenise@redhat.com
 */
public class AttachmentHandler extends BatchModeCommandHandler {

    private static final String DISPLAY = "display";
    private static final String SAVE = "save";

    private final FileSystemPathArgument targetFile;
    private final ArgumentWithoutValue createDirs;
    private final ArgumentWithValue operation;
    private final ArgumentWithValue action;
    private final ArgumentWithoutValue overwrite;

    public AttachmentHandler(CommandContext ctx) {
        super(ctx, "attachment", true);

        action = new ArgumentWithValue(this, new CommandLineCompleter() {
            @Override
            public int complete(CommandContext ctx, String buffer, int cursor,
                    List<String> candidates) {
                if (buffer == null || buffer.isEmpty()) {
                    candidates.add(DISPLAY);
                    candidates.add(SAVE);
                    return cursor;
                }
                if (buffer.equals(DISPLAY) || buffer.equals(SAVE)) {
                    candidates.add(" ");
                    return cursor;
                }
                if (DISPLAY.startsWith(buffer)) {
                    candidates.add(DISPLAY + " ");
                    return 0;
                }
                if (SAVE.startsWith(buffer)) {
                    candidates.add(SAVE + " ");
                    return 0;
                }
                return -1;
            }
        }, 0, "--action");

        operation = new ArgumentWithValue(this, new CommandLineCompleter() {
            @Override
            public int complete(CommandContext ctx, String buffer, int cursor,
                    List<String> candidates) {

                final String substitutedLine = ctx.getParsedCommandLine().getSubstitutedLine();
                boolean skipWS;
                int wordCount;
                if (Character.isWhitespace(substitutedLine.charAt(0))) {
                    skipWS = true;
                    wordCount = 0;
                } else {
                    skipWS = false;
                    wordCount = 1;
                }
                int cmdStart = 1;
                while (cmdStart < substitutedLine.length()) {
                    if (skipWS) {
                        if (!Character.isWhitespace(substitutedLine.charAt(cmdStart))) {
                            skipWS = false;
                            ++wordCount;
                            if (wordCount == 3) {
                                break;
                            }
                        }
                    } else if (Character.isWhitespace(substitutedLine.charAt(cmdStart))) {
                        skipWS = true;
                    }
                    ++cmdStart;
                }

                String cmd;
                if (wordCount == 1) {
                    cmd = "";
                } else if (wordCount != 3) {
                    return -1;
                } else {
                    cmd = substitutedLine.substring(cmdStart);
                    // remove --operation=
                    int i = cmd.indexOf("=");
                    if (i > 0) {
                        if (i == cmd.length() - 1) {
                            cmd = "";
                        } else {
                            cmd = cmd.substring(i + 1);
                        }
                    }
                }

                int cmdResult = ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                if (cmdResult < 0) {
                    return cmdResult;
                }

                // escaping index correction
                int escapeCorrection = 0;
                int start = substitutedLine.length() - 1 - buffer.length();
                while (start - escapeCorrection >= 0) {
                    final char ch = substitutedLine.charAt(start - escapeCorrection);
                    if (Character.isWhitespace(ch) || ch == '=') {
                        break;
                    }
                    ++escapeCorrection;
                }

                return buffer.length() + escapeCorrection - (cmd.length() - cmdResult);
            }
        }, "--operation") {

            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
                String act = getAction(ctx);
                if (!(SAVE.equals(act) || DISPLAY.equals(act))) {
                    return false;
                }
                return super.canAppearNext(ctx);
            }
        };

        operation.addRequiredPreceding(action);
        final FilenameTabCompleter pathCompleter = FilenameTabCompleter.newCompleter(ctx);
        targetFile = new FileSystemPathArgument(this, pathCompleter, "--file") {
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
                if (!(SAVE.equals(getAction(ctx)))) {
                    return false;
                }
                return super.canAppearNext(ctx);
            }

        };
        targetFile.addRequiredPreceding(operation);
        createDirs = new ArgumentWithoutValue(this, "--createDirs") {
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
                if (!(SAVE.equals(getAction(ctx)))) {
                    return false;
                }
                return super.canAppearNext(ctx);
            }

        };

        overwrite = new ArgumentWithoutValue(this, "--overwrite") {
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
                if (!(SAVE.equals(getAction(ctx)))) {
                    return false;
                }
                return super.canAppearNext(ctx);
            }

        };
        headers.addRequiredPreceding(operation);
    }

    @Override
    protected void recognizeArguments(CommandContext ctx) throws CommandFormatException {
        String act = getAction(ctx);
        if (DISPLAY.equals(act)) {
            if (targetFile.isPresent(ctx.getParsedCommandLine())) {
                throw new CommandFormatException(targetFile.getFullName()
                        + " can't be used with display action");
            }
            if (overwrite.isPresent(ctx.getParsedCommandLine())) {
                throw new CommandFormatException(overwrite.getFullName()
                        + " can't be used with display action");
            }
            if (createDirs.isPresent(ctx.getParsedCommandLine())) {
                throw new CommandFormatException(createDirs.getFullName()
                        + " can't be used with display action");
            }
        }
        super.recognizeArguments(ctx);
    }

    private String getAction(CommandContext ctx) {
        final String originalLine = ctx.getParsedCommandLine().getOriginalLine();
        if (originalLine == null || originalLine.isEmpty()) {
            return null;
        }
        String[] words = originalLine.trim().split(" ");
        String action = null;
        boolean seenFirst = false;
        for (String w : words) {
            if (w.isEmpty()) {
                continue;
            }
            if (!w.isEmpty()) {
                if (seenFirst) {
                    action = w;
                    break;
                } else {
                    seenFirst = true;
                }
            }
        }
        return action;
    }

    @Override
    protected void handleResponse(CommandContext ctx, OperationResponse response,
            boolean composite) throws CommandLineException {
        ModelNode result = response.getResponseNode();
        String targetPath = targetFile.getValue(ctx.getParsedCommandLine());
        String act = action.getValue(ctx.getParsedCommandLine());
        if (act == null || act.isEmpty()) {
            throw new CommandFormatException("Action is missing");
        }
        AttachmentResponseHandler handler = new AttachmentResponseHandler(ctx,
                targetPath,
                act.equals(SAVE),
                overwrite.isPresent(ctx.getParsedCommandLine()),
                createDirs.isPresent(ctx.getParsedCommandLine()));
        handler.handleResponse(result, response);
    }

    @Override
    protected ModelNode buildRequestWithoutHeaders(CommandContext ctx)
            throws CommandFormatException {
        final String op = operation.getValue(ctx.getParsedCommandLine());
        if (op == null) {
            throw new CommandFormatException("Invalid operation");
        }
        ModelNode mn = ctx.buildRequest(op);
        return mn;
    }

    private static class AttachmentResponseHandler implements ResponseHandler {

        private final boolean save;
        private final String targetPath;
        private final CommandContext ctx;
        private final boolean overwrite;
        private final boolean createDirs;
        private AttachmentResponseHandler(CommandContext ctx, String targetPath,
                boolean save, boolean overwrite, boolean createDirs) {
            this.ctx = ctx;
            this.targetPath = targetPath;
            this.save = save;
            this.overwrite = overwrite;
            this.createDirs = createDirs;
        }

        @Override
        public void handleResponse(ModelNode step, OperationResponse response)
                throws CommandLineException {
            //First retrieve all uuid.
            Set<String> uuids = getStreams(response.getResponseNode());
            if (uuids.isEmpty()) {
                return;
            }
            //Then lookup the complete data structure for a possible match.
            Set<String> mystreams = new TreeSet<>();

            // In case of a non composite operation, the headers are located
            // inside the step ModelNode.
            // So, although the operation wouldn't return the uuid in the result,
            // we find the uuid in the headers.
            // Obviously this would not work in batch mode (composite).
            retrieveStreams(step, uuids, mystreams);

            int index = 0;
            for (String uuid : mystreams) {
                if (save) {
                    index = saveStream(uuid, response, index);
                } else {
                    displayStream(uuid, response);
                }
            }
        }

        private int saveStream(String uuid, OperationResponse response,
                int index) throws CommandLineException {
            String target = targetPath == null ? uuid : targetPath;
            if (index > 0) {
                target = target + "(" + index + ")";
            }
            OperationResponse.StreamEntry entry = response.getInputStream(uuid);
            File targetFile = new File(target);
            if (!overwrite) {
                while (targetFile.exists()) {
                    String name = targetFile.getName();
                    int indexed = name.lastIndexOf("(");
                    if (indexed > 0) {
                        try {
                            String num = name.substring(indexed + 1, name.length() - 1);
                            index = Integer.valueOf(num);
                            index += 1;
                        } catch (NumberFormatException ex) {
                            // XXX OK, not a number.
                        }
                    } else {
                        index += 1;
                    }
                    targetFile = new File(targetFile.getAbsolutePath()
                            + "(" + index + ")");
                }
            } else {
                index += 1;
            }
            try {
              if(createDirs) {
                Files.createDirectories(targetFile.toPath().getParent());
              }
                Files.copy(
                        entry.getStream(),
                        targetFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
                ctx.printLine("File saved to " + targetFile.getCanonicalPath());
            } catch (IOException ex) {
                throw new CommandLineException("Exception saving stream ", ex);
            }
            return index;
        }

        private void displayStream(String uuid, OperationResponse response)
                throws CommandLineException {
            OperationResponse.StreamEntry entry = response.getInputStream(uuid);
            byte[] buffer = new byte[8 * 1024];
            int bytesRead;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                while ((bytesRead = entry.getStream().read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                ctx.printLine("ATTACHMENT " + uuid + ":");
                ctx.printLine(new String(out.toByteArray()));
            } catch (IOException ex) {
                throw new CommandLineException("Exception reading stream ", ex);
            }
        }
    }

    private static void retrieveStreams(ModelNode step, Set<String> uuids,
            Set<String> mystreams) {
        switch (step.getType()) {
            case STRING: {
                if (uuids.contains(step.asString())) {
                    mystreams.add(step.asString());
                }
                break;
            }
            case OBJECT: {
                for (String key : step.keys()) {
                    ModelNode mn = step.get(key);
                    retrieveStreams(mn, uuids, mystreams);
                }
                break;
            }
            case LIST: {
                for (int i = 0; i < step.asInt(); i++) {
                    ModelNode mn = step.get(i);
                    retrieveStreams(mn, uuids, mystreams);
                }
                break;
            }
        }
    }

    private static Set<String> getStreams(ModelNode response) {
        Set<String> ret = new HashSet<>();
        if (response.hasDefined(Util.RESPONSE_HEADERS)) {
            ModelNode respHeaders = response.get(Util.RESPONSE_HEADERS);
            if (respHeaders.hasDefined(Util.ATTACHED_STREAMS)) {
                ModelNode attachments = respHeaders.get(Util.ATTACHED_STREAMS);
                for (int i = 0; i < attachments.asInt(); i++) {
                    ModelNode attachment = attachments.get(i);
                    if (attachment.hasDefined(Util.UUID)) {
                        ret.add(attachment.get(Util.UUID).asString());
                    }
                }
            }
        }
        return ret;
    }

    @Override
    public HandledRequest buildHandledRequest(CommandContext ctx,
            Attachments attachments) throws CommandFormatException {
        String targetPath = targetFile.getValue(ctx.getParsedCommandLine());
        String act = action.getValue(ctx.getParsedCommandLine());
        if (act == null || act.isEmpty()) {
            throw new CommandFormatException("Action is missing");
        }
        AttachmentResponseHandler handler = new AttachmentResponseHandler(ctx,
                targetPath,
                act.equals(SAVE),
                overwrite.isPresent(ctx.getParsedCommandLine()),
                createDirs.isPresent(ctx.getParsedCommandLine()));
        return new HandledRequest(buildRequest(ctx, attachments), handler);
    }
}
