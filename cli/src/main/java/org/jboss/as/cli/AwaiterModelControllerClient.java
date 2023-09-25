/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
