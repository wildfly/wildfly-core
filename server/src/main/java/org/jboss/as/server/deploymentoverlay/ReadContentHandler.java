/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deploymentoverlay;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.vfs.VirtualFile;

/**
 * @author Stuart Douglas
 */
public class ReadContentHandler implements OperationStepHandler {


    protected final ContentRepository contentRepository;

    public ReadContentHandler(final ContentRepository contentRepository) {
        this.contentRepository = contentRepository;
    }

    @Override
    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
        final PathAddress address = PathAddress.pathAddress(operation.get(OP_ADDR));
        Resource resource = context.getOriginalRootResource();
        for(final PathElement element : address) {
            resource = resource.getChild(element);
        }
        byte[] content = resource.getModel().get(CONTENT).asBytes();
        final VirtualFile file = contentRepository.getContent(content);
        try {
            context.getResult().set(readFile(file));
        } catch (IOException e) {
            throw ServerLogger.ROOT_LOGGER.failedToLoadFile(file, e);
        }
    }


    public static String readFile(VirtualFile file) throws IOException {
        BufferedInputStream stream = null;
        try {
            stream = new BufferedInputStream(file.openStream());
            byte[] buff = new byte[1024];
            StringBuilder builder = new StringBuilder();
            int read = -1;
            while ((read = stream.read(buff)) != -1) {
                builder.append(new String(buff, 0, read, StandardCharsets.UTF_8));
            }
            return builder.toString();
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    //ignore
                }
            }
        }
    }

}
