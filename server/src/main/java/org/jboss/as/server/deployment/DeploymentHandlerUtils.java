/*
 * JBoss, Home of Professional Open Source.
 * Copyright (c) 2011, Red Hat, Inc., and individual contributors
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
package org.jboss.as.server.deployment;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ARCHIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HASH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELATIVE_TO;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.controller.resources.DeploymentAttributes;
import org.jboss.dmr.ModelNode;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public abstract class DeploymentHandlerUtils {

    public static final Pattern ARCHIVE_PATTERN = Pattern.compile("^.*\\.(?:(?:[SsWwJjEeRr][Aa][Rr])|(?:[Ww][Aa][Bb])|(?:[Ee][Ss][Aa]))$");

    protected static String asString(final ModelNode node, final String name) {
        return node.has(name) ? node.require(name).asString() : null;
    }

    protected static OperationFailedException createFailureException(String msg) {
        return new OperationFailedException(msg);
    }

    protected static OperationFailedException createFailureException(Throwable cause, String msg) {
        return new OperationFailedException(cause, new ModelNode(msg));
    }

    protected static DeploymentHandlerUtil.ContentItem[] getContents(ModelNode contentNode) {
        final List<ModelNode> nodes = contentNode.asList();
        final DeploymentHandlerUtil.ContentItem[] contents = new DeploymentHandlerUtil.ContentItem[nodes.size()];
        for(int i = 0; i < contents.length; i++) {
            final ModelNode node = nodes.get(i);
            if (node.has(HASH)) {
                contents[i] = new DeploymentHandlerUtil.ContentItem(node.require(HASH).asBytes(), DeploymentHandlerUtil.isArchive(node));
            } else {
                contents[i] = new DeploymentHandlerUtil.ContentItem(node.require(PATH).asString(), asString(node, RELATIVE_TO), node.require(ARCHIVE).asBoolean());
            }
        }
        return contents;
    }

    public static InputStream getInputStream(OperationContext context, ModelNode contentItem) throws OperationFailedException {
        InputStream in = null;
        if(!contentItem.isDefined()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try ( ZipOutputStream zout = new ZipOutputStream(out);) {
                zout.putNextEntry(new ZipEntry(context.getCurrentAddressValue() + '/'));
                zout.closeEntry();
                out.close();
            } catch(IOException ioex) {
                throw new OperationFailedException(ioex);
            }
            return new ByteArrayInputStream(out.toByteArray());
        }
        if (contentItem.hasDefined(DeploymentAttributes.CONTENT_INPUT_STREAM_INDEX.getName())) {
            int streamIndex = DeploymentAttributes.CONTENT_INPUT_STREAM_INDEX.resolveModelAttribute(context, contentItem).asInt();
            int maxIndex = context.getAttachmentStreamCount();
            if (streamIndex > maxIndex) {
                throw ServerLogger.ROOT_LOGGER.invalidStreamIndex(DeploymentAttributes.CONTENT_INPUT_STREAM_INDEX.getName(), streamIndex, maxIndex);
            }
            in = context.getAttachmentStream(streamIndex);
        } else if (contentItem.hasDefined(DeploymentAttributes.CONTENT_BYTES.getName())) {
            try {
                in = new ByteArrayInputStream(DeploymentAttributes.CONTENT_BYTES.resolveModelAttribute(context, contentItem).asBytes());
            } catch (IllegalArgumentException iae) {
                throw ServerLogger.ROOT_LOGGER.invalidStreamBytes(DeploymentAttributes.CONTENT_BYTES.getName());
            }
        } else if (contentItem.hasDefined(DeploymentAttributes.CONTENT_URL.getName())) {
            final String urlSpec = DeploymentAttributes.CONTENT_URL.resolveModelAttribute(context, contentItem).asString();
            try {
                in = new URL(urlSpec).openStream();
            } catch (MalformedURLException e) {
                throw ServerLogger.ROOT_LOGGER.invalidStreamURL(e, urlSpec);
            } catch (IOException e) {
                throw ServerLogger.ROOT_LOGGER.invalidStreamURL(e, urlSpec);
            }
        }
        if (in == null) {
            // Won't happen, as we call hasValidContentAdditionParameterDefined first
            throw new IllegalStateException();
        }
        return in;
    }

    /**
     * Checks to see if a valid deployment parameter has been defined.
     *
     * @param operation the operation to check.
     *
     * @return {@code true} of the parameter is valid, otherwise {@code false}.
     */
    public static boolean hasValidContentAdditionParameterDefined(ModelNode operation) {
        for (String s : DeploymentAttributes.MANAGED_CONTENT_ATTRIBUTES.keySet()) {
            if (operation.hasDefined(s)) {
                return true;
            }
        }
        return false;
    }

    protected static void validateOnePieceOfContent(final ModelNode content) throws OperationFailedException {
        // TODO: implement overlays
        if (content.asList().size() != 1)
            throw ServerLogger.ROOT_LOGGER.multipleContentItemsNotSupported();
    }

    public static InputStream emptyStream() {
        return new InputStream() {
            @Override
            public int read() throws IOException {
                return -1;
            }
        };
    }
}
