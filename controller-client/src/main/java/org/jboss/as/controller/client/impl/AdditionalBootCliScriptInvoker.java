/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.impl;

import java.io.File;

import org.jboss.as.controller.client.ModelControllerClient;

/**
 * This is for internal use only.
 *
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public interface AdditionalBootCliScriptInvoker {
    String CLI_SCRIPT_PROPERTY = "org.wildfly.internal.cli.boot.hook.script";
    String MARKER_DIRECTORY_PROPERTY = "org.wildfly.internal.cli.boot.hook.marker.dir";
    String SKIP_RELOAD_PROPERTY = "org.wildfly.internal.cli.boot.hook.reload.skip";

    void runCliScript(ModelControllerClient client, File file);
}