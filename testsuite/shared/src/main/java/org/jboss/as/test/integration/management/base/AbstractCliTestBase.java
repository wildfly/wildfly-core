/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.management.base;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import org.jboss.as.test.integration.common.HttpRequest;
import org.jboss.as.test.integration.management.util.CLIOpResult;
import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.jboss.dmr.ModelNode;


/**
 *
 * @author Dominik Pospisil <dpospisi@redhat.com>
 */
public class AbstractCliTestBase {
    public static final long WAIT_TIMEOUT = 30000;
    public static final long WAIT_LINETIMEOUT = 1500;
    protected static CLIWrapper cli;

    public static void initCLI() throws Exception {
        initCLI(true);
    }

    public static void initCLI(boolean connect) throws Exception {
        if (cli == null) {
            cli = new CLIWrapper(connect);
        }
    }

    public static void initCLI(String cliAddress) throws Exception {
        if (cli == null) {
            cli = new CLIWrapper(true, cliAddress);
        }
    }

    public static void initCLI(int connectionTimeout) throws Exception {
        if (cli == null) {
            cli = new CLIWrapper(true, null, null, connectionTimeout);
        }
    }

    public static void initCLI(String cliAddress, int connectionTimeout) throws Exception {
        if (cli == null) {
            cli = new CLIWrapper(true, cliAddress, null, connectionTimeout);
        }
    }

    public static void closeCLI() throws Exception {
        try {
            if (cli != null) cli.quit();
        } finally {
            cli = null;
        }
    }

    protected final String getBaseURL(URL url) throws MalformedURLException {
        return new URL(url.getProtocol(), url.getHost(), url.getPort(), "/").toString();
    }

    protected boolean checkUndeployed(String spec) {
        try {
            final long firstTry = System.currentTimeMillis();
            HttpRequest.get(spec, 10, TimeUnit.SECONDS);
            while (System.currentTimeMillis() - firstTry <= 1000) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    break;
                } finally {
                    HttpRequest.get(spec, 10, TimeUnit.SECONDS);
                }
            }
            return false;
        } catch (Exception e) {
        }
        return true;
    }

    protected void assertState(String expected, int timeout, String readOperation) throws IOException, InterruptedException {
        long done = timeout < 1 ? 0 : System.currentTimeMillis() + timeout;
        StringBuilder history = new StringBuilder();
        String state = null;
        do {
            try {
                cli.sendLine(readOperation, true);
                CLIOpResult result = cli.readAllAsOpResult();
                ModelNode resp = result.getResponseNode();
                ModelNode stateNode = result.isIsOutcomeSuccess() ? resp.get(RESULT) : resp.get(FAILURE_DESCRIPTION);
                state = stateNode.asString();
                history.append(state).append("\n");
            } catch (Exception ignored) {
                //
                history.append(ignored.toString()).append("--").append(cli.readOutput()).append("\n");
            }
            if (expected.equals(state)) {
                return;
            } else {
                Thread.sleep(20);
            }
        } while (timeout > 0 && System.currentTimeMillis() < done);
        assertEquals(history.toString(), expected, state);
    }
}
