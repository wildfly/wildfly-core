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
package org.wildfly.test.jmx;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import javax.management.AttributeChangeNotification;
import javax.management.Notification;
import javax.management.NotificationListener;
import org.jboss.logging.Logger;

/**
 *
 * @author Emmanuel Hugonnet (c) 2016 Red Hat, inc.
 */
public class ControlledStateNotificationListener implements NotificationListener {

    public static final String RUNTIME_CONFIGURATION_FILENAME = "runtime-configuration-notifications.txt";
    public static final String RUNNING_FILENAME = "running-notifications.txt";
    public static final String JMX_FACADE_FILE = "jmx-facade";
    private final Path targetFile;

    public ControlledStateNotificationListener() {
        this.targetFile = Paths.get("target/notifications/data").resolve(JMX_FACADE_FILE).toAbsolutePath();
        init(targetFile);
    }

    protected Path getRuntimeConfigurationTargetFile() {
        return this.targetFile.resolve(RUNTIME_CONFIGURATION_FILENAME);
    }

    protected Path getRunningConfigurationTargetFile() {
        return this.targetFile.resolve(RUNNING_FILENAME);
    }

    protected final void init(Path targetFile) {
        if (!Files.exists(targetFile)) {
            try {
                Files.createDirectories(targetFile);
                Files.createFile(targetFile.resolve(RUNTIME_CONFIGURATION_FILENAME));
                Files.createFile(targetFile.resolve(RUNNING_FILENAME));
            } catch (IOException ex) {
                Logger.getLogger(ControlledStateNotificationListener.class).error("Problem handling JMX Notification", ex);
            }
        }
    }

    @Override
    public void handleNotification(Notification notification, Object handback) {
        AttributeChangeNotification attributeChangeNotification = (AttributeChangeNotification) notification;
        if ("RuntimeConfigurationState".equals(attributeChangeNotification.getAttributeName())) {
            writeNotification(attributeChangeNotification, getRuntimeConfigurationTargetFile());
        } else {
            writeNotification(attributeChangeNotification, getRunningConfigurationTargetFile());
        }
    }

    private void writeNotification(AttributeChangeNotification notification, Path path) {
        try (BufferedWriter in = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.APPEND)) {
            in.write(String.format("%s %s %s %s", notification.getType(), notification.getSequenceNumber(), notification.getSource().toString(), notification.getMessage()));
            in.newLine();
            in.flush();
        } catch (IOException ex) {
            Logger.getLogger(ControlledStateNotificationListener.class).error("Problem handling JMX Notification", ex);
        }
    }

}
