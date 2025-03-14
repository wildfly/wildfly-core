/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.test.standalone.virtualthread;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import org.jboss.logging.Logger;

public final class PinningHandler implements HttpHandler {

    private static final Logger log = Logger.getLogger(PinningHandler.class);

    static final int SLEEP_TIME_MS = 250;
    static final String OK = "OK";
    static final String FAILED = "VirtualThreadDispatch cannot run virtual";

    @Override
    public void handleRequest(HttpServerExchange exchange) {
        VirtualThreadDispatch.runVirtual(PinningHandler.this::pin);

        exchange.getResponseSender().send(VirtualThreadDispatch.canRunVirtual() ? OK : FAILED);
    }

    private void pin() {
        addFramesAndPin(0);
    }

    private void addFramesAndPin(int stackDepth) {
        if (stackDepth < 20) {
            // add more frames to the stack so we can verify the logging depth limitation behavior
            addFramesAndPin(stackDepth + 1);
        } else {

            // NOTE: this will no longer pin when JEP 491 is integrated in an SE version.
            // Unless there is some other reasonable-to-implement way to induce pinning,
            // once that is in the tests using this will need to be ignored on those SE releases.
            synchronized (this) {
                try {
                    Thread.sleep(SLEEP_TIME_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            log.info("Ended pinning");
        }
    }
}
