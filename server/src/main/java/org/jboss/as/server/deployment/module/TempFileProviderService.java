/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.module;

import java.io.IOException;
import java.security.PrivilegedAction;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.jboss.as.server.logging.ServerLogger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.threads.JBossThreadFactory;
import org.jboss.vfs.TempFileProvider;
import org.jboss.vfs.VFSUtils;

import static java.security.AccessController.doPrivileged;

/**
 * Service responsible for managing the life-cycle of a TempFileProvider.
 *
 * @author John E. Bailey
 */
public class TempFileProviderService implements Service<TempFileProvider> {
    public static final ServiceName SERVICE_NAME = ServiceName.JBOSS.append("TempFileProvider");

    private static final TempFileProvider PROVIDER;
    static {
       try {
           final JBossThreadFactory threadFactory = doPrivileged(new PrivilegedAction<JBossThreadFactory>() {
               public JBossThreadFactory run() {
                   return new JBossThreadFactory(new ThreadGroup("TempFileProviderService-temp-threads"), Boolean.TRUE, null, "%G - %t", null, null);
               }
           });
           ScheduledThreadPoolExecutor ex = new ScheduledThreadPoolExecutor(0, threadFactory);
           ex.setKeepAliveTime(60,TimeUnit.SECONDS);
           PROVIDER = TempFileProvider.create("deployment", ex, true);
       }
       catch (final IOException ioe) {
          throw ServerLogger.ROOT_LOGGER.failedToCreateTempFileProvider(ioe);
       }
    }

    /**
     * {@inheritDoc}
     */
    public void start(StartContext context) throws StartException {
    }

    /**
     * {@inheritDoc}
     */
    public void stop(StopContext context) {
        VFSUtils.safeClose(PROVIDER);
    }

    /**
     * {@inheritDoc}
     */
    public TempFileProvider getValue() throws IllegalStateException {
        return provider();
    }

    public static TempFileProvider provider() {
        return PROVIDER;
    }
}
