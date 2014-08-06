package org.jboss.as.server.shutdown;

/**
 *
 * Listener that is invoked to notify on success or failure of the
 *
 * @author Stuart Douglas
 */
public interface OperationListener {

    /**
     * Invoked when a suspend operation is started.
     */
    void suspendStarted();

    /**
     * Invoked when a suspend operation is complete
     */
    void complete();

    /**
     * Invoked when a suspend operation is cancelled
     */
    void cancelled();

    /**
     * Invoked when a suspend operation times out.
     */
    void timeout();

}
