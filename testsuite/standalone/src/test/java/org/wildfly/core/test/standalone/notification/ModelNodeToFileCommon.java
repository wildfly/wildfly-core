/*
 * JBoss, Home of Professional Open Source
 * Copyright 2016, Red Hat, Inc., and individual contributors as indicated
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

package org.wildfly.core.test.standalone.notification;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

import org.jboss.dmr.ModelNode;

/**
 * @author Kabir Khan
 */
class ModelNodeToFileCommon {
    final File file;

    public ModelNodeToFileCommon(File file) {
        this.file = file;
    }

    void writeData(ModelNode data) {
        try {
            ModelNode output = readNotificationOutput(file);
            output.add(data);
            writeNotificationOutput(file, output);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static ModelNode readNotificationOutput(File file) throws IOException {
        if (!file.exists()) {
            return new ModelNode();
        }
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            return ModelNode.fromJSONStream(in);
        }
    }

    private static void writeNotificationOutput(File file, ModelNode output) throws IOException {
        file.delete();
        file.createNewFile();

        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(file)))) {
            output.writeJSONString(out, false);
        }
    }
}
