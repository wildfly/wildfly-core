/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014, JBoss Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.test.syslogserver;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.productivity.java.syslog4j.server.SyslogServerEventHandlerIF;
import org.productivity.java.syslog4j.server.SyslogServerEventIF;
import org.productivity.java.syslog4j.server.SyslogServerIF;

/**
 * Implementation of {@link SyslogServerEventHandlerIF} which is backed by a static/final {@link java.util.concurrent.BlockingQueue} instance.
 *
 * @author Josef Cacek
 * @see #getQueue()
 */
public class BlockedSyslogServerEventHandler implements SyslogServerEventHandlerIF {

    private static final long serialVersionUID = -3814601581286016000L;
    private static final BlockingQueue<SyslogServerEventIF> queue = new LinkedBlockingQueue<SyslogServerEventIF>();

    public static BlockingQueue<SyslogServerEventIF> getQueue() {
        return queue;
    }

    public void event(SyslogServerIF syslogServer, SyslogServerEventIF event) {
        queue.offer(event);
    }
}
