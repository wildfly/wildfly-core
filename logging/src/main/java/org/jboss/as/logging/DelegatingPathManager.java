/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

package org.jboss.as.logging;

import org.jboss.as.controller.services.path.PathEntry;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.controller.services.path.PathManager.Callback.Handle;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

/**
 * A path manager that delegates to the path manager injected in this service.
 * <p/>
 * The delegate path manager is initialized when this service is {@link #start(org.jboss.msc.service.StartContext)
 * stared}. When the service is {@link #stop(org.jboss.msc.service.StopContext) stopped} the delegate path manager is
 * set to {@code null} and subsequent calls to the {@link org.jboss.as.controller.services.path.PathManager path
 * manager} methods will fail.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class DelegatingPathManager implements PathManager, Service<PathManager> {

    static final ServiceName SERVICE_NAME = PathManagerService.SERVICE_NAME.append("logging");

    private static class Holder {
        static final DelegatingPathManager INSTANCE = new DelegatingPathManager();
    }

    private final InjectedValue<PathManager> pathManagerInjector = new InjectedValue<>();
    private volatile PathManager pathManager;

    private DelegatingPathManager() {
    }

    public static DelegatingPathManager getInstance() {
        return Holder.INSTANCE;
    }

    @Override
    public String resolveRelativePathEntry(final String path, final String relativeTo) {
        checkState();
        return pathManager.resolveRelativePathEntry(path, relativeTo);
    }

    @Override
    public PathEntry getPathEntry(final String name) {
        checkState();
        return pathManager.getPathEntry(name);
    }

    @Override
    public Handle registerCallback(final String name, final Callback callback, final Event... events) {
        checkState();
        return pathManager.registerCallback(name, callback, events);
    }

    @Override
    public void start(final StartContext context) throws StartException {
        pathManager = pathManagerInjector.getValue();
    }

    @Override
    public void stop(final StopContext context) {
        pathManager = null;
    }

    @Override
    public PathManager getValue() throws IllegalStateException, IllegalArgumentException {
        return pathManager;
    }

    protected InjectedValue<PathManager> getPathManagerInjector() {
        return pathManagerInjector;
    }

    private void checkState() {
        if (pathManager == null) {
            throw LoggingLogger.ROOT_LOGGER.pathManagerServiceNotStarted();
        }
    }
}
