/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.impl;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexey Loubyansky
 *
 */
public class CliShutdownHook {

    interface Handler {
        void shutdown();
    }

    private static volatile boolean shuttingDown = false;

    private static final List<Handler> handlers = new ArrayList<Handler>();

    static {
        SecurityActions.addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized(handlers) {
                    shuttingDown = true;
                    for (Handler h : handlers) {
                        try {
                            h.shutdown();
                        } catch (Throwable t) {
                        }
                    }
                }
            }
        }, "CLI Shutdown Hook"));

    }

    public static void add(Handler handler) {
        if (!shuttingDown) {
            synchronized (handlers) {
                handlers.add(handler);
            }
        }
    }

    public static void remove(Handler handler) {
        if (!shuttingDown) {
            synchronized (handlers) {
                handlers.remove(handler);
            }
        }
    }
}
