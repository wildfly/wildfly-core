/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.cli.embedded;

import org.jboss.as.embedded.StandaloneServer;

/**
 * Simple data structure to record information related to a launch of an embedded server.
 *
 * @author Brian Stansberry (c) 2015 Red Hat Inc.
 */
class EmbeddedServerLaunch {

    private final StandaloneServer server;

    private final EnvironmentRestorer restorer;

    EmbeddedServerLaunch(StandaloneServer server, EnvironmentRestorer restorer) {
        this.server = server;
        this.restorer = restorer;
    }

    EnvironmentRestorer getEnvironmentRestorer() {
        return restorer;
    }

    StandaloneServer getServer() {
        return server;
    }
}
