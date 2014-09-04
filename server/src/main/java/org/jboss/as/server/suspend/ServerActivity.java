package org.jboss.as.server.suspend;

/**
 * A server activity that may have to finish before the server can shut down gracefully.
 *
 *
 * @author Stuart Douglas
 */
public interface ServerActivity {

    /**
     * Invoked before the server is paused. This is the place where pause notifications should
     * be sent to external systems such as load balancers to tell them this node is about to go away.
     *
     * @param listener The listener to invoker when the pre-pause phase is done
     */
    void preSuspend(ServerActivityCallback listener);

    /**
     * Invoked once the suspend process has started. One this has been invoked
     * no new requests should be allowed to proceeed
     * @param listener The listener to invoke when suspend is done.
     */
    void suspended(ServerActivityCallback listener);

    /**
     * Invoked if the suspend or pre-suspened is cancelled
     */
    void resume();

}
