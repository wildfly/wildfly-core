/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.logging.resolvers;

import static org.jboss.as.controller.services.path.PathResourceDefinition.PATH;
import static org.jboss.as.controller.services.path.PathResourceDefinition.RELATIVE_TO;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.logging.DelegatingPathManager;
import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.dmr.ModelNode;

/**
 * Used to resolve an absolute path for a file.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class FileResolver implements ModelNodeResolver<String> {

    @Override
    public String resolveValue(final OperationContext context, final ModelNode value) throws OperationFailedException {
        final ModelNode pathNode = PATH.resolveModelAttribute(context, value);
        final ModelNode relativeToNode = RELATIVE_TO.resolveModelAttribute(context, value);
        String path = pathNode.asString();
        String result = path;
        if (relativeToNode.isDefined()) {
            result = DelegatingPathManager.getInstance().resolveRelativePathEntry(path, relativeToNode.asString());
        }
        final Path file = Paths.get(result);
        if (Files.exists(file) && Files.isDirectory(file)) {
            throw LoggingLogger.ROOT_LOGGER.invalidLogFile(file.normalize().toString());
        }
        return result;
    }
}
