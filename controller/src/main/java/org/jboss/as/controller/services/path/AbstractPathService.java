/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.services.path;

import static org.wildfly.common.Assert.checkNotNullParam;

import java.util.function.Consumer;

import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;

/**
 * Abstract superclass for services that return a path.
 *
 * @author Brian Stansberry
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public abstract class AbstractPathService implements Service<String> {

    private static final ServiceName SERVICE_NAME_BASE = ServiceName.JBOSS.append("server", "path");
    private final Consumer<String> pathConsumer;
    private volatile String path;

    AbstractPathService(final Consumer<String> pathConsumer) {
        this.pathConsumer = pathConsumer;
    }

    public static ServiceName pathNameOf(final String pathName) {
        checkNotNullParam("pathName", pathName);
        return SERVICE_NAME_BASE.append(pathName);
    }

    /**
     * Checks whether the given path looks like an absolute Unix or Windows filesystem pathname <strong>without
     * regard for what the filesystem is underlying the Java Virtual Machine</strong>. A UNIX pathname is
     * absolute if its prefix is <code>"/"</code>.  A Microsoft Windows pathname is absolute if its prefix is a drive
     * specifier followed by <code>"\\"</code>, or if its prefix is <code>"\\\\"</code>.
     * <p>
     * <strong>This method differs from simply creating a new {@code File} and calling {@link java.io.File#isAbsolute()} in that
     * its results do not change depending on what the filesystem underlying the Java Virtual Machine is. </strong>
     * </p>
     *
     * @param path the path
     *
     * @return  {@code true} if {@code path} looks like an absolute Unix or Windows pathname
     */
    public static boolean isAbsoluteUnixOrWindowsPath(final String path) {
        if (path != null) {
            int length = path.length();
            if (length > 0) {
                char c0 = path.charAt(0);
                if (c0 == '/') {
                    return true;   // Absolute Unix path
                } else if (length > 1) {
                    char c1 = path.charAt(1);
                    if (c0 == '\\' && c1 == '\\') {
                        return true;   // Absolute UNC pathname "\\\\foo"
                    } else if (length > 2 && c1 == ':' && path.charAt(2) == '\\' && isDriveLetter(c0) ) {
                        return true; // Absolute local pathname "z:\\foo"
                    }
                }

            }
        }
        return false;
    }

    private static boolean isDriveLetter(final char c) {
        return ((c >= 'a') && (c <= 'z')) || ((c >= 'A') && (c <= 'Z'));
    }

    // ------------------------------------------------------------  Service

    @Override
    public void start(final StartContext context) {
        path = resolvePath();
        pathConsumer.accept(path);
    }

    @Override
    public void stop(final StopContext context) {
        pathConsumer.accept(null);
        path = null;
    }

    @Override
    public String getValue() throws IllegalStateException {
        final String path = this.path;
        if (path == null) {
            throw new IllegalStateException();
        }
        return path;
    }

    // ------------------------------------------------------------  Protected

    protected abstract String resolvePath();

}
