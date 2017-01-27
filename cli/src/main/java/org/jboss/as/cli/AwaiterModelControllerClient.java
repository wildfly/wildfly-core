/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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
package org.jboss.as.cli;

import java.io.IOException;
import org.jboss.dmr.ModelNode;

/**
 * ModelControllerClient set on CommandContext must implement this interface.
 * That is required for reload and shutdown commands to be used.
 *
 * @author jdenise@redhat.com
 */
public interface AwaiterModelControllerClient {

    ModelNode execute(ModelNode operation, boolean awaitClose) throws IOException;

    void awaitClose(boolean awaitClose) throws IOException;

    boolean isConnected();

    void ensureConnected(long timeoutMillis) throws CommandLineException, IOException;
}
