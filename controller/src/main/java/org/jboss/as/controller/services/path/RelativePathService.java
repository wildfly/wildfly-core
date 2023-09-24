/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.services.path;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELATIVE_TO;

import java.io.File;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.wildfly.common.Assert;

/**
 * {@link AbstractPathService} implementation for paths that are relative
 * to other paths.
 *
 * @author Brian Stansberry
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class RelativePathService extends AbstractPathService {

    private final String relativePath;
    private final Supplier<String> pathSupplier;

    private RelativePathService(final String relativePath, final Consumer<String> pathConsumer, final Supplier<String> pathSupplier) {
        super(pathConsumer);
        this.relativePath = convertPath(relativePath);
        this.pathSupplier = pathSupplier;
    }

    public static ServiceController<?> addService(final String name, final String relativePath,
            final String relativeTo, final ServiceTarget serviceTarget) {
        return addService(pathNameOf(name), relativePath, false, relativeTo, serviceTarget);
    }

    public static ServiceController<?> addService(final ServiceName name, final String relativePath,
            final String relativeTo, final ServiceTarget serviceTarget) {
        return addService(name, relativePath, false, relativeTo, serviceTarget);
    }

    /**
     * Installs a path service.
     *
     * @param name  the name to use for the service
     * @param path the relative portion of the path
     * @param possiblyAbsolute {@code true} if {@code path} may be an {@link #isAbsoluteUnixOrWindowsPath(String) absolute path}
     *                         and should be {@link AbsolutePathService installed as such} if it is, with any
     *                         {@code relativeTo} parameter ignored
     * @param relativeTo the name of the path that {@code path} may be relative to
     * @param serviceTarget the {@link ServiceTarget} to use to install the service
     * @return the ServiceController for the path service
     */
    public static ServiceController<?> addService(final ServiceName name, final String path,
                                                       boolean possiblyAbsolute, final String relativeTo, final ServiceTarget serviceTarget) {

        if (possiblyAbsolute && isAbsoluteUnixOrWindowsPath(path)) {
            return AbsolutePathService.addService(name, path, serviceTarget);
        }

        final ServiceBuilder<?> builder = serviceTarget.addService(name);
        final Consumer<String> pathConsumer = builder.provides(name);
        final Supplier<String> injectedPath = builder.requires(pathNameOf(relativeTo));
        builder.setInstance(new RelativePathService(path, pathConsumer, injectedPath));
        return builder.install();
}

    public static void addService(final ServiceName name, final ModelNode element, final ServiceTarget serviceTarget) {
        final String relativePath = element.require(PATH).asString();
        final String relativeTo = element.require(RELATIVE_TO).asString();
        addService(name, relativePath, false, relativeTo, serviceTarget);
    }

    static String convertPath(String relativePath) {
        Assert.checkNotNullParam("relativePath", relativePath);
        Assert.checkNotEmptyParam("relativePath", relativePath);
        if (relativePath.charAt(0) == '/') {
            if (relativePath.length() == 1) {
                throw ControllerLogger.ROOT_LOGGER.invalidRelativePathValue(relativePath);
            }
            return relativePath.substring(1);
        }
        else if (relativePath.indexOf(":\\") == 1) {
            throw ControllerLogger.ROOT_LOGGER.pathIsAWindowsAbsolutePath(relativePath);
        }
        else {
            if(isWindows()) {
                return relativePath.replace("/", File.separator);
            } else {
                return relativePath.replace("\\", File.separator);
            }
        }
    }

    static String doResolve(String base, String relativePath) {
        base = base.endsWith(File.separator) ? base.substring(0, base.length() -1) : base;
        return base + File.separatorChar + relativePath;
    }

    @Override
    protected String resolvePath() {
        return doResolve(pathSupplier.get(), relativePath);
    }

    private static boolean isWindows(){
        return File.separatorChar == '\\';
    }

}
