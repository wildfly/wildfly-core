/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.core.instmgr;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Tracks the status of the candidate installation by using the installation-manager.properties file and configures the values that are
 * passed to the installation-manager.sh/bat scripts to apply or revert an installation.
 */
class InstMgrCandidateStatus {
    private Path properties;
    private Path prepareServerPath;

    public static final String INST_MGR_STATUS_KEY = "INST_MGR_STATUS";
    public static final String INST_MGR_COMMAND_KEY = "INST_MGR_COMMAND";

    public static final String INST_MGR_PREPARED_SERVER_DIR_KEY = "INST_MGR_PREPARED_SERVER_DIR";

    public enum Status {ERROR, CLEAN, PREPARING, PREPARED}

    void initialize(Path properties, Path prepareServerPath) {
        this.properties = properties.normalize().toAbsolutePath();
        this.prepareServerPath = prepareServerPath;
    }

    Status getStatus() throws IOException {
        try (FileInputStream in = new FileInputStream(properties.toString())) {
            final Properties prop = new Properties();
            prop.load(in);
            String current = (String) prop.get(INST_MGR_STATUS_KEY);
            current = current == null ? "CLEAN" : current.trim();
            return Status.valueOf(current);
        }
    }

    void begin() {
        setStatus(Status.PREPARING);
    }

    void reset() {
        setStatus(Status.CLEAN);
    }

    void setFailed() {
        setStatus(Status.ERROR);
    }

    void commit(String command) {
        setStatus(Status.PREPARED, command);
    }

    private void setStatus(Status status) {
        setStatus(status, "");
    }

    private void setStatus(Status status, String command) {
        try (FileInputStream in = new FileInputStream(properties.toString())) {
            final Properties prop = new Properties();
            prop.load(in);
            in.close();

            try (FileOutputStream out = new FileOutputStream(properties.toString())) {
                prop.setProperty(INST_MGR_COMMAND_KEY, command);
                prop.setProperty(INST_MGR_STATUS_KEY, status.name());
                prop.setProperty(INST_MGR_PREPARED_SERVER_DIR_KEY, this.prepareServerPath.toFile().getAbsolutePath());
                prop.store(out, null);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
