/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.test.jmx;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.channels.ClosedByInterruptException;
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
        try {
            if (!Files.exists(targetFile)) {
                Files.createDirectories(targetFile);
            }
            if (!Files.exists(targetFile.resolve(RUNTIME_CONFIGURATION_FILENAME))) {
                Files.createFile(targetFile.resolve(RUNTIME_CONFIGURATION_FILENAME));
            }
            if (!Files.exists(targetFile.resolve(RUNNING_FILENAME))) {
                Files.createFile(targetFile.resolve(RUNNING_FILENAME));
            }
        } catch (IOException ex) {
            Logger.getLogger(ControlledStateNotificationListener.class).error("Problem handling JMX Notification", ex);
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
        } catch (ClosedByInterruptException cbie) {
            /* This happens sometimes during the "reload" operation, the BufferedWriter won't write anymore
               because our thread was interrupted by the server.
               So we clear the interruption flag and try writing again and hope that it will work this time.
            */
            boolean wasInterrupted =  Thread.interrupted();
            try {
                Logger.getLogger(ControlledStateNotificationListener.class)
                        .error("We were interrupted and couldn't write into file, try again", cbie);
                try (BufferedWriter in = Files
                        .newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.APPEND)) {
                    in.write(String.format("%s %s %s %s", notification.getType(),
                            notification.getSequenceNumber(), notification.getSource().toString(),
                            notification.getMessage()));
                    in.newLine();
                    in.flush();
                } catch (IOException ex2) {
                    Logger.getLogger(ControlledStateNotificationListener.class)
                            .error("No success on second attempt either", ex2);
                }
            } finally {
                if(wasInterrupted)
                    Thread.currentThread().interrupt();
            }
        } catch (IOException ex) {
            Logger.getLogger(ControlledStateNotificationListener.class).error("Problem handling JMX Notification", ex);
        }
    }

}
