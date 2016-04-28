package org.wildfly.core.testrunner;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 */
public class ServerController {

    private static final AtomicBoolean started = new AtomicBoolean(false);
    private static volatile Server server;

    public void start() {
        if (started.compareAndSet(false, true)) {
            server = new Server();
            try {
                server.start();
            } catch (final Throwable t) {
                // failed to start
                server = null;
                started.set(false);
                throw t;
            }
        }
    }

    public void stop() {
        if (server != null) {
            try {
                server.stop();
            } finally {
                server = null;
                started.set(false);
            }
        }
    }

    public boolean isStarted() {
        return (server != null);
    }

    public ManagementClient getClient() {
        return server.getClient();
    }
}
