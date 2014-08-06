package org.jboss.as.server.shutdown;

/**
 * Listener interface that is invoked when a system has suspended
 *
 * @author Stuart Douglas
 */
public interface ServerActivityListener {

    /**
     * Method that is invoked when all paused requests have finished
     */
    void requestsComplete();

    /**
     * Method that is invoked if the pause operation is cancelled.
     */
    void unPaused();

}
