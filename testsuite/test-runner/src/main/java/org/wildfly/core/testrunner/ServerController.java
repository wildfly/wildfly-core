package org.wildfly.core.testrunner;

/**
 * An interface to control the lifecycle of a server
 *
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public interface ServerController {

    /**
     * Starts the server
     */
    void start();

    /**
     * Stops the server
     */
    void stop();

    /**
     * Checks the state of the server and returns {@code true} if the server is running
     *
     * @return {@code true} if the server is running, otherwise {@code false}
     */
    boolean isStarted();

    /**
     * Returns the management client used to connect to the running server
     *
     * @return the management client
     *
     * @throws java.lang.IllegalStateException if the server is stopped
     */
    ManagementClient getClient();
}
