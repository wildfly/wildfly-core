/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.domain.autoignore;


import java.io.File;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class TestClass implements TestClassMBean {

    String path;

    @Override
    public void setPath(String path) {
        this.path = path;
    }

    @Override
    public void start() {
        final File file = new File(path);
        try (final Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            System.out.println("--- writing");
            writer.write("Test\n");
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }


}
