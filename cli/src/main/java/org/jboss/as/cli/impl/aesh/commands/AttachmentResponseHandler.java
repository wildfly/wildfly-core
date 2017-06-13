/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.cli.impl.aesh.commands;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.Util;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author jdenise@redhat.com
 */
public class AttachmentResponseHandler {

    private final boolean save;
    private final File targetPath;
    private final Consumer<String> consumer;
    private final boolean overwrite;

    public AttachmentResponseHandler(Consumer<String> consumer, File targetPath,
            boolean save, boolean overwrite) {
        this.consumer = consumer;
        this.targetPath = targetPath;
        this.save = save;
        this.overwrite = overwrite;
    }

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
        File f = targetPath == null ? new File(uuid) : targetPath;
        String target = f.getAbsolutePath();
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
            Files.copy(
                    entry.getStream(),
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
            consumer.accept("File saved to " + targetFile.getCanonicalPath());
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
            consumer.accept("ATTACHMENT " + uuid + ":");
            consumer.accept(new String(out.toByteArray()));
        } catch (IOException ex) {
            throw new CommandLineException("Exception reading stream ", ex);
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
}
