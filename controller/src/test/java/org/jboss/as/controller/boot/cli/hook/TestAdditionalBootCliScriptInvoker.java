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

package org.jboss.as.controller.boot.cli.hook;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;

import java.io.File;
import java.io.IOException;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.impl.AdditionalBootCliScriptInvoker;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.dmr.ModelNode;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class TestAdditionalBootCliScriptInvoker implements AdditionalBootCliScriptInvoker {
    static String commands;
    static boolean shouldError;
    static boolean restartRequired;
    @Override
    public void runCliScript(ModelControllerClient client, File file) {
        try {
            commands = BootCliHookTestCase.readFile(file);
            if (shouldError) {
                throw new RuntimeException("Our CLI commands threw an error");
            }
            if (restartRequired) {
                ModelNode op = Util.createOperation(ForceRestartRequiredHandler.NAME, PathAddress.EMPTY_ADDRESS);
                ModelNode result = client.execute(op);
                if (result.get(OUTCOME).asString().equals(FAILED)) {
                    throw new RuntimeException(result.get(FAILURE_DESCRIPTION).asString());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
