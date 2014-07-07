package org.wildfly.core.testrunner;

/**
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 */
public class ServerController {

    private static volatile boolean started = false;
    private static volatile Server server;


    public void start() {
        if (!started) {
            started = true;
            server = new Server();
            server.start();
        }
    }

    //public void start(String containerQualifier, Map<String, String> config);

    public void stop() {
        if (server != null) {
            server.stop();
            server = null;
            started = false;
        }
    }

    //public void kill(String containerQualifier);

    public boolean isStarted() {
        return server != null;
    }


    public ManagementClient getClient() {
        return server.getClient();
    }

}
