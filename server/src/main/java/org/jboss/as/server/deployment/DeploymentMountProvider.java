/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
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


import static java.security.AccessController.doPrivileged;

import java.io.Closeable;
import java.io.IOException;
import java.security.PrivilegedAction;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;

import org.jboss.as.server.Utils;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.threads.JBossThreadFactory;
import org.jboss.vfs.TempFileProvider;
import org.jboss.vfs.VFS;
import org.jboss.vfs.VirtualFile;

/**
 * Provides VFS mounts of deployment content.
 *
 * @author Brian Stansberry
 */
public interface DeploymentMountProvider {

    /**
     * Standard ServiceName under which a service controller for an instance of
     * {@code Service<ServerDeploymentRepository> would be registered.
     */
    ServiceName SERVICE_NAME = ServiceName.JBOSS.append("deployment-mount-provider");

    /**
     * Requests that the given content be mounted in VFS at the given {@code mountPoint}.
     *
     *
     * @param deploymentContents the deployment contents. Cannot be <code>null</code>
     * @param mountPoint VFS location where the content should be mounted. Cannot be <code>null</code>
     * @param mountType The type of mount to perform
     * @return {@link java.io.Closeable} that can be used to close the mount
     *
     * @throws IOException  if there is an IO problem while mounting
     */
    Closeable mountDeploymentContent(VirtualFile deploymentContents, VirtualFile mountPoint, MountType mountType) throws IOException;

    static class Factory {
        public static void addService(final ServiceTarget serviceTarget) {
            ServerDeploymentRepositoryImpl service = new ServerDeploymentRepositoryImpl();
            org.jboss.as.server.Services.addServerExecutorDependency(
                    serviceTarget.addService(DeploymentMountProvider.SERVICE_NAME, service),
                    service.injectedExecutorService, false)
                    .install();
        }

        /**
         * Default implementation of {@link DeploymentMountProvider}.
         */
        private static class ServerDeploymentRepositoryImpl implements DeploymentMountProvider, Service<DeploymentMountProvider> {

            private final InjectedValue<ExecutorService> injectedExecutorService = new InjectedValue<ExecutorService>();
            private volatile TempFileProvider tempFileProvider;
            private volatile ScheduledExecutorService scheduledExecutorService;

            /**
             * Creates a new ServerDeploymentRepositoryImpl.
             */
            private ServerDeploymentRepositoryImpl() {
            }

            @Override
            public Closeable mountDeploymentContent(final VirtualFile contents, VirtualFile mountPoint, MountType type) throws IOException {
                // according to the javadoc contents can not be null
                assert contents != null : "null contents";
                switch (type) {
                    case ZIP:
                        return VFS.mountZip(contents, mountPoint, tempFileProvider);
                    case EXPANDED:
                        return VFS.mountZipExpanded(contents, mountPoint, tempFileProvider);
                    case REAL:
                        return VFS.mountReal(contents.getPhysicalFile(), mountPoint);
                    default:
                        throw ServerLogger.ROOT_LOGGER.unknownMountType(type);
                }
            }

            @Override
            public void start(StartContext context) throws StartException {
                try {
                    final JBossThreadFactory threadFactory = doPrivileged(new PrivilegedAction<JBossThreadFactory>() {
                        public JBossThreadFactory run() {
                            return new JBossThreadFactory(new ThreadGroup("ServerDeploymentRepository-temp-threads"), true, null, "%G - %t", null, null);
                        }
                    });
                    scheduledExecutorService =  Executors.newScheduledThreadPool(2, threadFactory);
                    tempFileProvider = TempFileProvider.create("temp", scheduledExecutorService, true);
                } catch (IOException e) {
                    throw ServerLogger.ROOT_LOGGER.failedCreatingTempProvider(e);
                }
                ServerLogger.ROOT_LOGGER.debugf("%s started", DeploymentMountProvider.class.getSimpleName());
            }

            @Override
            public void stop(final StopContext context) {
                Runnable r = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Utils.safeClose(tempFileProvider);
                        } finally {
                            try {
                                ScheduledExecutorService ses = scheduledExecutorService;
                                scheduledExecutorService = null;
                                if (ses != null) {
                                    ses.shutdown();
                                }
                                ServerLogger.ROOT_LOGGER.debugf("%s stopped", DeploymentMountProvider.class.getSimpleName());
                            } finally {
                                context.complete();
                            }
                        }
                    }
                };
                final ExecutorService executorService = injectedExecutorService.getValue();
                try {
                    try {
                        executorService.execute(r);
                    } catch (RejectedExecutionException e) {
                        r.run();
                    }
                } finally {
                    context.asynchronous();
                }
            }


            @Override
            public DeploymentMountProvider getValue() throws IllegalStateException {
                return this;
            }

        }
    }
}
