package org.jboss.as.server.shutdown;

/**
 * A server activity that may have to finish before the server can shut down gracefully.
 *
 *
 * @author Stuart Douglas
 */
public interface ServerActivity {


    void pause(ServerActivityListener listener);

    void resume();

}
