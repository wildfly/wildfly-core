/*
 * Copyright 2016 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.test.jmx.events;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.jboss.as.test.module.util.TestModule;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetupTask;

/**
 *
 * @author Emmanuel Hugonnet (c) 2016 Red Hat, inc.
 */
public class ProcessStateJmxModuleSetupTask implements ServerSetupTask {
    private TestModule processStateJmxModule;

    @Override
    public void setup(ManagementClient managementClient) throws Exception {
        Path tempFile = Files.createTempFile("module", ".xml");
        try (InputStream in = ProcessStateJmxMBean.class.getResourceAsStream("module.xml")) {
            Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }
        File moduleFile = tempFile.toFile();
        processStateJmxModule = new TestModule("org.wildfly.test.jmx.events", moduleFile);
        processStateJmxModule.addResource("jmx-notifier.jar")
                .addClass(ProcessStateJmxMBean.class)
                .addClass(ProcessStateJmx.class);
        processStateJmxModule.create();
        Files.deleteIfExists(tempFile);
    }

    @Override
    public void tearDown(ManagementClient managementClient) throws Exception {
        processStateJmxModule.remove();
    }
}
