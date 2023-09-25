/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.client.old.server.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 * @author Kabir Khan
 */
public class LargeDeploymentFile {
    private final File file;

    public LargeDeploymentFile(File file) {
        this.file = file;
    }

    public File getFile() {
        return file;
    }

    public InputStream getInputStream() throws FileNotFoundException {
        return new BufferedInputStream(new FileInputStream(file));
    }

    public String getName() {
        return file.getName();
    }
}
