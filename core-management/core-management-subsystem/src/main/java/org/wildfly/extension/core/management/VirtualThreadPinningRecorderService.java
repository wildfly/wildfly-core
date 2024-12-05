/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.core.management;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.stream.Collectors;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingStream;
import org.jboss.as.controller.CapabilityServiceBuilder;
import org.jboss.as.controller.CapabilityServiceTarget;
import org.jboss.logging.Logger;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;
import org.wildfly.extension.core.management.logging.CoreManagementLogger;

/**
 * Service that initiates a JFR {@link RecordingStream} to listen for {@code jdk.VirtualThreadPinned}
 * events. When events are received it logs them and records their duration for reporting via the
 * subsystem metrics.
 */
final class VirtualThreadPinningRecorderService implements Service {

    static void install(CapabilityServiceTarget target,
                        VirtualThreadPinningResourceDefinition.StartMode mode,
                        VirtualThreadPinningResourceDefinition.PinningRecorderData pinningRecorderData) {
        VirtualThreadPinningRecorderService service = new VirtualThreadPinningRecorderService(pinningRecorderData);
        CapabilityServiceBuilder<?> builder = target.addService()
                .setInstance(service)
                .setInitialMode(mode == VirtualThreadPinningResourceDefinition.StartMode.ON_DEMAND
                        ? ServiceController.Mode.ON_DEMAND : ServiceController.Mode.ACTIVE);
        builder.provides(VirtualThreadPinningResourceDefinition.RUNTIME_CAPABILITY);
        builder.install();
    }

    private final VirtualThreadPinningResourceDefinition.PinningRecorderData pinningRecorderData;
    private volatile RecordingStream recordingStream;

    VirtualThreadPinningRecorderService(VirtualThreadPinningResourceDefinition.PinningRecorderData pinningRecorderData) {
        this.pinningRecorderData = pinningRecorderData;
    }

    @Override
    public void start(StartContext startContext) {

        // If we are not in an SE runtime that can support jdk.VirtualThreadPinning notifications, log and return.
        // Note: We could test this in 'install' and not install, but that would break other services that
        // depend on this one to get it to start if it's configured to start ON_DEMAND.
        int vmVersion = Runtime.version().feature();
        if (vmVersion < 21) {
            // This is at INFO as this service not doing anything is harmless in a VM that doesn't support virtual threads
            CoreManagementLogger.ROOT_LOGGER.virtualThreadsUnsupported(vmVersion);
            return;
        } else {
            // Not all JREs include jdk.jfr. Don't fail if we're in such an env; just WARN
            try {
                VirtualThreadPinningRecorderService.class.getClassLoader().loadClass("jdk.jfr.consumer.RecordingStream");
            } catch (ClassNotFoundException cnfe) {
                CoreManagementLogger.ROOT_LOGGER.virtualThreadPinningNotificationUnsupported();
                return;
            }
        }

        RecordingStream rs = new RecordingStream();
        rs.enable("jdk.VirtualThreadPinned").withStackTrace();
        rs.onEvent("jdk.VirtualThreadPinned", VirtualThreadPinningRecorderService.this::onEvent);
        rs.setMaxAge(Duration.ofSeconds(10));

        try {
            rs.startAsync();
            recordingStream = rs;
        } catch (RuntimeException e) {
            rs.close();
            throw e;
        }
    }

    @Override
    public void stop(StopContext stopContext) {
        if (recordingStream != null) {
            recordingStream.close();
        }
    }

    private void onEvent(RecordedEvent event) {
        pinningRecorderData.metrics.recordPinning(event.getDuration());

        Logger.Level level = pinningRecorderData.level.get();
        if (CoreManagementLogger.VIRTUAL_THREAD_LOGGER.isEnabled(level)) {
            CoreManagementLogger.VIRTUAL_THREAD_LOGGER.log(level, getEventLogMessage(event, pinningRecorderData.stackDepth.get()));
        }

    }

    private static String getEventLogMessage(RecordedEvent event, int maxStackDepth) {
        return CoreManagementLogger.VIRTUAL_THREAD_LOGGER.threadPinningDetected(
                formatThreadName(event),
                event.getDuration().toMillis(),
                LocalDateTime.ofInstant(event.getStartTime(), ZoneId.systemDefault()),
                getStackTrace(event, maxStackDepth)
        );
    }

    private static String formatThreadName(RecordedEvent event) {
        RecordedThread recordedThread = event.getThread();
        if (recordedThread == null) {
            return "<unknown>";
        }
        String javaName = recordedThread.getJavaName();
        javaName = javaName == null || javaName.isEmpty() ? "<unnamed>" : javaName;
        return javaName + " (javaThreadId = " + recordedThread.getJavaThreadId() + ")";
    }

    private static String getStackTrace(RecordedEvent event, int maxStackDepth) {
        int depth = maxStackDepth < 0 ? Integer.MAX_VALUE : maxStackDepth;
        RecordedStackTrace trace = event.getStackTrace();
        String formatted;
        if (trace != null && depth > 0) {
            formatted = "\n\t" + trace.getFrames().stream()
                    .limit(depth)
                    .map(VirtualThreadPinningRecorderService::formatStackTraceFrame)
                    .collect(Collectors.joining("\n\t\t at "));
            if (depth < trace.getFrames().size()) {
                formatted += "\n\t\t(...)";
            }
        } else {
            formatted = "<unavailable>";
        }
        return formatted;
    }

    private static String formatStackTraceFrame(RecordedFrame frame) {
        return frame.getMethod().getType().getName() + "#" + frame.getMethod().getName() + ": " + frame.getLineNumber();
    }

}
