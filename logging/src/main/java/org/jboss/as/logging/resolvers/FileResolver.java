/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging.resolvers;

import static org.jboss.as.controller.services.path.PathResourceDefinition.PATH;
import static org.jboss.as.logging.CommonAttributes.RELATIVE_TO;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;

/**
 * Used to resolve an absolute path for a file.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class FileResolver implements ModelNodeResolver<String> {

    public static final FileResolver INSTANCE = new FileResolver();

    private FileResolver() {
    }

    @Override
    public String resolveValue(final OperationContext context, final ModelNode value) throws OperationFailedException {
        final ModelNode pathNode = PATH.resolveModelAttribute(context, value);
        final ModelNode relativeToNode = RELATIVE_TO.resolveModelAttribute(context, value);
        String path = pathNode.asString();
        String result = path;
        if (relativeToNode.isDefined()) {
            result = resolve(context, relativeToNode.asString(), path);
        }
        if (result == null) {
            throw new IllegalStateException(LoggingLogger.ROOT_LOGGER.pathManagerServiceNotStarted());
        }
        final Path file = Paths.get(result);
        if (Files.exists(file) && Files.isDirectory(file)) {
            throw LoggingLogger.ROOT_LOGGER.invalidLogFile(file.normalize().toString());
        }
        return result;
    }

    /**
     * Resolves the path based on the relative to and the path. May return {@code null} if the service is not up.
     *
     * @param context        the operation context.
     * @param relativeToPath the relative to path, may be {@code null}.
     * @param path           the path to append to the relative to path or the absolute path if the relative to path is
     *                       {@code null}.
     *
     * @return the full path or {@code null} if the services were not started.
     */
    public static String resolvePath(final OperationContext context, final String relativeToPath, final String path) {
        return INSTANCE.resolve(context, relativeToPath, path);
    }

    private String resolve(final OperationContext context, final String relativeToPath, final String path) {
        // TODO it would be better if this came via the ExtensionContext
        ServiceName pathMgrSvc = context.getCapabilityServiceName("org.wildfly.management.path-manager", PathManager.class);
        @SuppressWarnings("unchecked") final ServiceController<PathManager> controller = (ServiceController<PathManager>) context.getServiceRegistry(false).getService(pathMgrSvc);
        if (controller == null) {
            return null;
        }
        return controller.getValue().resolveRelativePathEntry(path, relativeToPath);
    }
}
