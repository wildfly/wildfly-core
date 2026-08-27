/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.jboss.as.network.NetworkUtils;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.mgmt.UndertowHttpManagementService;
import org.jboss.as.server.mgmt.domain.HttpManagement;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StabilityMonitor;
import org.jboss.msc.service.StabilityStatistics;

/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class BootstrapListener {

    public static final String MARKER_FILE = "startup-marker";
    public static final String RUNNING_LOCK_FILE = "running.lock";
    private static final String INSTALLATION_DIR = ".installation";

    private final StabilityMonitor monitor = new StabilityMonitor();
    private final ServiceContainer serviceContainer;
    private final ServiceTarget serviceTarget;
    private final ElapsedTime elapsedTime;
    private final String prettyVersion;
    private final FutureServiceContainer futureContainer;
    private final File tempDir;
    private String startedCleanMessage;
    private String startedWitErrorsMessage;
    // Static so the lock survives server reloads (JVM lifetime) and is never released manually.
    // The OS releases all advisory locks when the JVM exits or crashes.
    private static volatile FileChannel lockFileChannel;
    private static volatile FileLock runningLock;

    public BootstrapListener(final ServiceContainer serviceContainer, final ElapsedTime elapsedTime, final ServiceTarget serviceTarget, final FutureServiceContainer futureContainer, final String prettyVersion, final File tempDir) {
        this.serviceContainer = serviceContainer;
        this.elapsedTime = elapsedTime;
        this.serviceTarget = serviceTarget;
        this.prettyVersion = prettyVersion;
        this.futureContainer = futureContainer;
        this.tempDir = tempDir;
        serviceTarget.addMonitor(monitor);
    }

    public StabilityMonitor getStabilityMonitor() {
        return monitor;
    }

    /**
     * Generate the boot statistics messages.
     *
     * @param messages additional messages to be appended in the boot statistics messages.
     */
    public void generateBootStatistics(String... messages) {
        final StabilityStatistics statistics = new StabilityStatistics();
        try {
            monitor.awaitStability(statistics);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            serviceTarget.removeMonitor(monitor);
            final long bootstrapTime = elapsedTime.getElapsedTime();
            done(bootstrapTime, statistics, messages);
            monitor.clear();
        }
    }

    public void printBootStatisticsMessage() {
        if (startedCleanMessage != null) {
            ServerLogger.AS_ROOT_LOGGER.startedClean(startedCleanMessage);
        } else if (startedWitErrorsMessage != null) {
            ServerLogger.AS_ROOT_LOGGER.startedWitErrors(startedWitErrorsMessage);
        }
        startedCleanMessage = null;
        startedWitErrorsMessage = null;
    }

    public void bootFailure(Throwable throwable) {
        futureContainer.failed(throwable);
    }

    private void done(final long bootstrapTime, final StabilityStatistics statistics, String... messages) {
        futureContainer.done(serviceContainer);
        if (serviceContainer.isShutdown()) {
            // Do not print boot statistics because server
            // received shutdown signal during the boot process.
            return;
        }

        final int active = statistics.getActiveCount();
        final int failed = statistics.getFailedCount();
        final int lazy = statistics.getLazyCount();
        final int never = statistics.getNeverCount();
        final int onDemand = statistics.getOnDemandCount();
        final int passive = statistics.getPassiveCount();
        final int problem = statistics.getProblemsCount();
        final int started = statistics.getStartedCount();
        String appendMessage = "";
        if (messages != null) {
            appendMessage = String.join(" ", messages);
        }
        if (failed == 0 && problem == 0) {
            startedCleanMessage = ServerLogger.AS_ROOT_LOGGER.startedCleanMessage(prettyVersion, bootstrapTime, started, active + passive + onDemand + never + lazy, onDemand + passive + lazy, appendMessage);
            createStartupMarker("success", elapsedTime.getStartTime());
        } else {
            startedWitErrorsMessage = ServerLogger.AS_ROOT_LOGGER.startedWitErrorsMessage(prettyVersion, bootstrapTime, started, active + passive + onDemand + never + lazy, failed + problem, onDemand + passive + lazy, appendMessage);
            createStartupMarker("error", elapsedTime.getStartTime());
        }
    }

    private void createStartupMarker(String result, long startTime) {
        File file = new File(tempDir, MARKER_FILE);
        try {
            Files.deleteIfExists(file.toPath());
            if (file.createNewFile()) {
                try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8, StandardOpenOption.WRITE)) {
                    writer.append(result).append(":").append(String.valueOf(startTime));
                    writer.flush();
                } catch (IOException e) {
                    // ignore
                }
            }
        } catch (IOException e) {
            // ignore
        }
    }

    public static void deleteStartupMarker(File tempDir) {
        File file = new File(tempDir, BootstrapListener.MARKER_FILE);
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException e) {
            // ignore
        }
    }

    public void acquireRunningLock(File homeDir) {
        if (lockFileChannel != null) {
            // Lock already held from a previous start (e.g. server reload); keep it for JVM lifetime.
            return;
        }
        try {
            openLockFile(homeDir.toPath().resolve(INSTALLATION_DIR).resolve(RUNNING_LOCK_FILE));
        } catch (IOException e) {
            // ignore
        }
    }

    private void openLockFile(Path lockPath) throws IOException {
        Files.createDirectories(lockPath.getParent());
        FileChannel channel = FileChannel.open(lockPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.READ);
        FileLock lock = channel.tryLock();
        if (lock != null) {
            lockFileChannel = channel;
            runningLock = lock;
        } else {
            channel.close();
        }
    }

    public void logAdminConsole() {
        ServiceController<?> controller = serviceContainer.getService(UndertowHttpManagementService.SERVICE_NAME);
        if (controller != null) {
            HttpManagement mgmt = (HttpManagement)controller.getValue();

            boolean hasHttp = mgmt.getHttpNetworkInterfaceBinding() != null;
            boolean hasHttps = mgmt.getHttpsNetworkInterfaceBinding() != null;
            if (hasHttp && hasHttps) {
                ServerLogger.AS_ROOT_LOGGER.logHttpAndHttpsManagement(NetworkUtils.formatIPAddressForURI(mgmt.getHttpNetworkInterfaceBinding().getAddress()), mgmt.getHttpPort(), NetworkUtils.formatIPAddressForURI(mgmt.getHttpsNetworkInterfaceBinding().getAddress()), mgmt.getHttpsPort());
                if (mgmt.hasConsole()) {
                    ServerLogger.AS_ROOT_LOGGER.logHttpAndHttpsConsole(NetworkUtils.formatIPAddressForURI(mgmt.getHttpNetworkInterfaceBinding().getAddress()), mgmt.getHttpPort(), NetworkUtils.formatIPAddressForURI(mgmt.getHttpsNetworkInterfaceBinding().getAddress()), mgmt.getHttpsPort());
                } else {
                    ServerLogger.AS_ROOT_LOGGER.logNoConsole();
                }
            } else if (hasHttp) {
                ServerLogger.AS_ROOT_LOGGER.logHttpManagement(NetworkUtils.formatIPAddressForURI(mgmt.getHttpNetworkInterfaceBinding().getAddress()), mgmt.getHttpPort());
                if (mgmt.hasConsole()) {
                    ServerLogger.AS_ROOT_LOGGER.logHttpConsole(NetworkUtils.formatIPAddressForURI(mgmt.getHttpNetworkInterfaceBinding().getAddress()), mgmt.getHttpPort());
                } else {
                    ServerLogger.AS_ROOT_LOGGER.logNoConsole();
                }
            } else if (hasHttps) {
                ServerLogger.AS_ROOT_LOGGER.logHttpsManagement(NetworkUtils.formatIPAddressForURI(mgmt.getHttpsNetworkInterfaceBinding().getAddress()), mgmt.getHttpsPort());
                if (mgmt.hasConsole()) {
                    ServerLogger.AS_ROOT_LOGGER.logHttpsConsole(NetworkUtils.formatIPAddressForURI(mgmt.getHttpsNetworkInterfaceBinding().getAddress()), mgmt.getHttpsPort());
                } else {
                    ServerLogger.AS_ROOT_LOGGER.logNoConsole();
                }
            } else {
                ServerLogger.AS_ROOT_LOGGER.logNoHttpManagement();
                ServerLogger.AS_ROOT_LOGGER.logNoConsole();
            }
        }
    }
}
