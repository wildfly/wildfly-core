/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.instmgr;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

import org.wildfly.core.instmgr.logging.InstMgrLogger;

/**
 * Tracks the status of the candidate installation by using the installation-manager.properties file and configures the values that are
 * passed to the installation-manager.sh/bat scripts to apply or revert an installation.
 */
class InstMgrCandidateStatus {
    private Path properties;

    public static final String INST_MGR_STATUS_KEY = "INST_MGR_STATUS";
    public static final String INST_MGR_COMMAND_KEY = "INST_MGR_COMMAND";
    public static final String INST_MGR_LOCK_HELD_KEY = "INST_MGR_LOCK_HELD";

    public enum Status {ERROR, CLEAN, PREPARING, PREPARED}

    void initialize(Path properties) {
        this.properties = properties.normalize().toAbsolutePath();
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

    void begin() throws IOException {
        setStatus(Status.PREPARING);
    }

    void reset() throws IOException {
        setStatus(Status.CLEAN);
    }

    void setFailed() throws IOException {
        setStatus(Status.ERROR);
    }

    /**
     * Commits the candidate with the given command.
     * Maintains backward compatibility by not including lock status.
     */
    void commit(String command) throws IOException {
        commit(command, null);
    }

    /**
     * Commits the candidate with the given command and lock status.
     * @param command the command to execute
     * @param lockHeld whether the server running lock was acquired (null for unknown/not applicable)
     */
    void commit(String command, Boolean lockHeld) throws IOException {
        setStatus(Status.PREPARED, command, lockHeld);
    }

    private void setStatus(Status status) throws IOException {
        setStatus(status, "", null);
    }

    private void setStatus(Status status, String command) throws IOException {
        setStatus(status, command, null);
    }

    private void setStatus(Status status, String command, Boolean lockHeld) throws IOException {
        InstMgrLogger.ROOT_LOGGER.debugf("Setting Installation Manager Status to %s, command %s, lock held %s", status.name(), command, lockHeld);

        final Properties prop = new Properties();
        if (status != Status.CLEAN) {
            try (FileInputStream in = new FileInputStream(properties.toString())) {
                prop.load(in);
            }
        }

        try (FileOutputStream out = new FileOutputStream(properties.toString())) {
            prop.setProperty(INST_MGR_COMMAND_KEY, command);
            prop.setProperty(INST_MGR_STATUS_KEY, status.name());
            // Only set lock status if explicitly provided (not null)
            if (lockHeld != null) {
                prop.setProperty(INST_MGR_LOCK_HELD_KEY, String.valueOf(lockHeld));
            }
            prop.store(out, null);
        }
    }
}
