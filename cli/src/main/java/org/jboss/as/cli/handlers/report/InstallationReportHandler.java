/*
 * Copyright (C) 2015 Red Hat, inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package org.jboss.as.cli.handlers.report;

import static org.jboss.as.controller.client.helpers.ClientConstants.ATTACHED_STREAMS;
import static org.jboss.as.controller.client.helpers.ClientConstants.FILE;
import static org.jboss.as.controller.client.helpers.ClientConstants.RESPONSE_HEADERS;
import static org.jboss.as.controller.client.helpers.ClientConstants.UUID;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.handlers.BaseOperationCommand;
import org.jboss.as.cli.impl.ArgumentWithValue;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestBuilder;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.controller.client.OperationResponse.StreamEntry;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

/**
 * CLI command to download the installation report.
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
public class InstallationReportHandler extends BaseOperationCommand {

    private final ArgumentWithValue formatArg;
    private final ArgumentWithValue targetFileArg;
    private Path targetPath;
    private String format;

    public InstallationReportHandler(CommandContext ctx) {
        super(ctx, "installation-report", true);
        formatArg = new ArgumentWithValue(this, "--format");
        targetFileArg = new ArgumentWithValue(this, "--target-file");
    }

    @Override
    protected ModelNode buildRequestWithoutHeaders(CommandContext ctx) throws CommandFormatException {
        format = getFormat(ctx);
        targetPath = new File(targetFileArg.getValue(ctx.getParsedCommandLine(), true)).toPath();
        if (Files.notExists(targetPath.getParent()) || !Files.isDirectory(targetPath.getParent())) {
            throw new OperationFormatException("Incorrect destination directory " + targetPath.getParent());
        }
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName("product-info");
        builder.getModelNode().get(FILE).set(true);
        builder.getModelNode().get("format").set(format);
        ModelNode request = builder.buildRequest();
        return request;
    }

    @Override
    protected void handleAttachedFile(CommandContext ctx, OperationResponse operationResponse) throws CommandLineException {
        ModelNode globalResponse = operationResponse.getResponseNode();
        if (Util.isSuccess(globalResponse)) {
            Set<String> uuids = listStreams(operationResponse);
            if (!uuids.isEmpty()) {
                if (uuids.size() == 1) {
                    try {
                        String uuid = uuids.iterator().next();
                        saveContent(ctx, operationResponse, uuid, targetPath);
                        uuids.remove(uuid);
                    } catch (IOException ex) {
                        throw new CommandLineException(ex);
                    }
                } else {
                    String targetFileName = targetPath.getFileName().toString();
                    int index = targetFileName.lastIndexOf('.');
                    String prefix;
                    String extension = "";
                    if (index > 0 && (index + 1) < targetFileName.length()) {
                        prefix = targetFileName.substring(0, index);
                        extension = targetFileName.substring(index);
                    } else {
                        prefix = targetFileName;
                    }
                    List<ModelNode> responses = Operations.readResult(globalResponse).asList();
                    for (ModelNode response : responses) {
                        if (Util.isSuccess(response) && response.hasDefined(RESPONSE_HEADERS, ATTACHED_STREAMS)) {
                            List<ModelNode> streamNodes = response.get(RESPONSE_HEADERS, ATTACHED_STREAMS).asList();
                            if (streamNodes.size() == 1) {
                                try {
                                    String uuid = streamNodes.get(0).get(UUID).asString();
                                    String fileName = prefix + '-' + getName(Operations.getOperationAddress(response).asPropertyList()) + extension;
                                    saveContent(ctx, operationResponse, uuid, targetPath.resolveSibling(fileName));
                                    uuids.remove(uuid);
                                } catch (IOException ex) {
                                    throw new CommandLineException(ex);
                                }
                            }
                        }
                    }
                    //last case
                    if (uuids.size() == 1) {
                        try {
                            String uuid = uuids.iterator().next();
                            saveContent(ctx, operationResponse, uuid, targetPath);
                            uuids.remove(uuid);
                        } catch (IOException ex) {
                            throw new CommandLineException(ex);
                        }
                    }
                }
            }
        }
    }

    private String getName(List<Property> address) {
        if (address != null && !address.isEmpty()) {
            return address.get(address.size() - 1).getValue().asString();
        }
        return "";
    }

    private Set<String> listStreams(OperationResponse operationResponse) {
        List<StreamEntry> streams = operationResponse.getInputStreams();
        Set<String> uuids = new HashSet<>(streams.size());
        for (StreamEntry stream : streams) {
            uuids.add(stream.getUUID());
        }
        return uuids;
    }

    private void saveContent(CommandContext ctx, OperationResponse operationResponse, String uuid, Path filepath) throws IOException {
        OperationResponse.StreamEntry stream = operationResponse.getInputStream(uuid);
        if (stream.getMimeType() != null && stream.getMimeType().contains(format)) {
            try (InputStream in = stream.getStream()) {
                Files.copy(in, filepath, StandardCopyOption.REPLACE_EXISTING);
                ctx.printLine("Report saved in " + filepath);
            }
        }
    }

    private String getFormat(CommandContext ctx) throws CommandFormatException {
        String reportFormat = formatArg.getValue(ctx.getParsedCommandLine(), false);
        if (reportFormat == null) {
            reportFormat = "xml";
        }
        return reportFormat;
    }

    @Override
    protected void displayResponseHeaders(CommandContext ctx, ModelNode response) {
        //We have everything to hide
    }

}
