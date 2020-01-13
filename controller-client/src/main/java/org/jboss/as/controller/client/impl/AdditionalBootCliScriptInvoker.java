/*
 * JBoss, Home of Professional Open Source
 * Copyright 2019, JBoss Inc., and individual contributors as indicated
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