/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import org.jboss.as.controller.client.ModelControllerClient;

/**
 * A {@link ModelControllerClient} subinterface that does not throw {@link java.io.IOException}.
 * Used for clients that operate in the same VM as the target {@link ModelController} and hence
 * are not subject to IO failures associated with remote calls.
 *
 * @author Brian Stansberry
 */
public interface LocalModelControllerClient extends org.jboss.as.controller.client.LocalModelControllerClient {
}
