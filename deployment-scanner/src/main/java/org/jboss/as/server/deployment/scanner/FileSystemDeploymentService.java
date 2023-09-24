/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment.scanner;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ARCHIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CANCELLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT_DEPLOYED_NOTIFICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT_UNDEPLOYED_NOTIFICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ENABLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OWNER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PERSISTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELATIVE_TO;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLBACK_ON_RUNTIME_FAILURE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.URL;
import static org.jboss.as.server.deployment.scanner.logging.DeploymentScannerLogger.ROOT_LOGGER;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ProcessStateNotifier;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.notification.Notification;
import org.jboss.as.controller.notification.NotificationHandler;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.server.deployment.DeploymentAddHandler;
import org.jboss.as.server.deployment.DeploymentDeployHandler;
import org.jboss.as.server.deployment.DeploymentFullReplaceHandler;
import org.jboss.as.server.deployment.DeploymentRedeployHandler;
import org.jboss.as.server.deployment.DeploymentRemoveHandler;
import org.jboss.as.server.deployment.DeploymentUndeployHandler;
import org.jboss.as.server.deployment.scanner.ZipCompletionScanner.NonScannableZipException;
import org.jboss.as.server.deployment.scanner.api.DeploymentOperations;
import org.jboss.as.server.deployment.scanner.api.DeploymentScanner;
import org.jboss.as.server.deployment.scanner.logging.DeploymentScannerLogger;
import org.jboss.as.server.deployment.transformation.DeploymentTransformer;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

/**
 * Service that monitors the filesystem for deployment content and if found deploys it.
 *
 * @author Brian Stansberry
 */
class FileSystemDeploymentService implements DeploymentScanner, NotificationHandler {

    static final Pattern ARCHIVE_PATTERN = Pattern.compile("^.*\\.(?:(?:[SsWwJjEeRr][Aa][Rr])|(?:[Ww][Aa][Bb])|(?:[Ee][Ss][Aa]))$");

    static final String DEPLOYED = ".deployed";
    static final String FAILED_DEPLOY = ".failed";
    static final String DO_DEPLOY = ".dodeploy";
    static final String DEPLOYING = ".isdeploying";
    static final String UNDEPLOYING = ".isundeploying";
    static final String UNDEPLOYED = ".undeployed";
    static final String SKIP_DEPLOY = ".skipdeploy";
    static final String PENDING = ".pending";

    static final String WEB_INF = "WEB-INF";
    static final String META_INF = "META-INF";

    /**
     * Max period an incomplete auto-deploy file can have no change in content
     */
    static final long MAX_NO_PROGRESS = 60000;

    /**
     * Default timeout for deployments to execute in seconds
     */
    static final long DEFAULT_DEPLOYMENT_TIMEOUT = 600;

    private File deploymentDir;
    private long scanInterval = 0;
    private volatile boolean scanEnabled = false;
    private volatile boolean firstScan = true;
    private volatile boolean deployedContentEstablished = false;
    private ScheduledFuture<?> scanTask;
    private ScheduledFuture<?> rescanIncompleteTask;
    private ScheduledFuture<?> rescanUndeployTask;
    private final Lock scanLock = new ReentrantLock();

    private final Map<String, DeploymentMarker> deployed = new HashMap<String, DeploymentMarker>();
    private final HashSet<String> ignoredMissingDeployments = new HashSet<String>();
    private final HashSet<String> noticeLogged = new HashSet<String>();
    private final HashSet<String> illegalDirLogged = new HashSet<String>();
    private final HashSet<String> prematureExplodedContentDeletionLogged = new HashSet<String>();
    private final HashSet<File> nonscannableLogged = new HashSet<File>();
    private final Map<File, IncompleteDeploymentStatus> incompleteDeployments = new HashMap<File, IncompleteDeploymentStatus>();

    private final ScheduledExecutorService scheduledExecutor;
    private volatile ProcessStateNotifier processStateNotifier;
    private volatile DeploymentOperations.Factory deploymentOperationsFactory;
    private volatile DeploymentOperations deploymentOperations;

    private Filter<Path> filter = new ExtensibleFilter();
    private volatile boolean autoDeployZip;
    private volatile boolean autoDeployExploded;
    private volatile boolean autoDeployXml;
    private volatile long maxNoProgress = MAX_NO_PROGRESS;
    private volatile boolean rollbackOnRuntimeFailure;
    private volatile long deploymentTimeout = DEFAULT_DEPLOYMENT_TIMEOUT;

    private final ModelNode resourceAddress;
    private final String relativeTo;
    private final String relativePath;
    private volatile PropertyChangeListener propertyChangeListener;
    private Future<?> undeployScanTask;

    private volatile boolean deploymentDirAccessible = true;
    private volatile boolean lastScanSuccessful = true;

    @SuppressWarnings("deprecation")
    private final DeploymentTransformer deploymentTransformer;


    @Override
    public void handleNotification(Notification notification) {
        if (scanEnabled && acquireScanLock()) {
            try {
                switch (notification.getType()) {
                    case DEPLOYMENT_DEPLOYED_NOTIFICATION: {
                        String runtimeName = notification.getData().get(DEPLOYMENT).asString();
                        if (!deployed.containsKey(runtimeName)) {
                            updateStatusAfterDeploymentNotification(deploymentDir.toPath(), runtimeName);
                        }
                        break;
                    }
                    case DEPLOYMENT_UNDEPLOYED_NOTIFICATION: {
                        String runtimeName = notification.getData().get(DEPLOYMENT).asString();
                        if (deployed.containsKey(runtimeName)) {
                            clearMarkers(deployed.get(runtimeName).parentFolder.toPath(), runtimeName);
                            updateStatusAfterUndeploymentNotification(deploymentDir.toPath(), runtimeName);
                            deployed.remove(runtimeName);
                        }
                        break;
                    }
                    default:
                    //ignore
                }
            } finally {
                releaseScanLock();
            }
        }
    }
    private void updateStatusAfterUndeploymentNotification(Path dir, String runtimeName) {
        ROOT_LOGGER.debugf("Updating status after undeployment notification for %s", runtimeName);
        Path undeployedMarker = dir.resolve(runtimeName + UNDEPLOYED);
        final Path deploymentFile = dir.resolve(runtimeName);
        if (!Files.exists(undeployedMarker) && Files.exists(deploymentFile)) {
            try {
                Files.createFile(undeployedMarker);
            } catch (IOException ioex) {
                ROOT_LOGGER.errorWritingDeploymentMarker(ioex, undeployedMarker.toString());
            }
        }
    }
    private void updateStatusAfterDeploymentNotification(Path dir, String runtimeName) {
        ROOT_LOGGER.debugf("Updating status after deployment notification for %s", runtimeName);
        Path undeployedMarker = dir.resolve(runtimeName + UNDEPLOYED);
        Path deployedMarker = dir.resolve(runtimeName + DEPLOYED);
        if (Files.exists(undeployedMarker)) {
            try {
                Files.delete(undeployedMarker);
            } catch (IOException ioex) {
                ROOT_LOGGER.cannotRemoveDeploymentMarker(undeployedMarker.toFile());
            }
        }
        final Path deploymentFile = dir.resolve(runtimeName);
        if (!Files.exists(deployedMarker) && Files.exists(deploymentFile)) {
            try {
                deployedMarker = Files.createFile(deployedMarker);
                boolean isArchive = Files.isRegularFile(deploymentFile);
                if (Files.exists(deploymentFile)) {
                    Files.setLastModifiedTime(deployedMarker, Files.getLastModifiedTime(deploymentFile));
                }
                deployed.put(runtimeName, new DeploymentMarker(Files.getLastModifiedTime(deployedMarker).toMillis(), isArchive, dir.toFile()));
            } catch (IOException ioex) {
                ROOT_LOGGER.errorWritingDeploymentMarker(ioex, deployedMarker.toString());
            }
        }
    }

    private void clearMarkers(Path dir, String runtimeName) {
        String fileName = runtimeName + DO_DEPLOY;
        try {
            Files.deleteIfExists(dir.resolve(fileName));
            fileName = runtimeName + FAILED_DEPLOY;
            Files.deleteIfExists(dir.resolve(fileName));
            fileName = runtimeName + SKIP_DEPLOY;
            Files.deleteIfExists(dir.resolve(fileName));
            fileName = runtimeName + DEPLOYED;
            Files.deleteIfExists(dir.resolve(fileName));
        } catch (IOException ioex) {
            ROOT_LOGGER.cannotRemoveDeploymentMarker(fileName);
        }

    }

    private class DeploymentScanRunnable implements Runnable {

        @Override
        public void run() {
            try {
                scan();
            } catch (RejectedExecutionException e) {
                //Do nothing as this happens if a scan occurs during a reload of shutdown of a server.
            } catch (Exception e) {
                ROOT_LOGGER.scanException(e, deploymentDir.getAbsolutePath());
            }
        }
    }

    private final DeploymentScanRunnable scanRunnable = new DeploymentScanRunnable();

    FileSystemDeploymentService(final PathAddress resourceAddress, final String relativeTo, final File deploymentDir, final File relativeToDir,
                                final DeploymentOperations.Factory deploymentOperationsFactory,
                                final ScheduledExecutorService scheduledExecutor) {

        assert resourceAddress != null;
        assert resourceAddress.size() > 0;
        assert scheduledExecutor != null;
        assert deploymentDir != null;

        this.resourceAddress = resourceAddress.toModelNode().asObject();
        this.resourceAddress.protect();
        this.relativeTo = relativeTo;
        this.deploymentDir = deploymentDir;
        this.deploymentOperationsFactory = deploymentOperationsFactory;
        this.scheduledExecutor = scheduledExecutor;
        if (relativeToDir != null) {
            String fullDir = deploymentDir.getAbsolutePath();
            String relDir = relativeToDir.getAbsolutePath();
            String sub = fullDir.substring(relDir.length());
            if (sub.startsWith(File.separator)) {
                sub = sub.length() == 1 ? "" : sub.substring(1);
            }
            this.relativePath = sub.length() > 0 ? sub + File.separator : sub;
        } else {
            relativePath = null;
        }
        this.deploymentTransformer = loadDeploymentTransformer();
    }

    @Override
    public boolean isAutoDeployZippedContent() {
        return autoDeployZip;
    }

    @Override
    public void setAutoDeployZippedContent(boolean autoDeployZip) {
        this.autoDeployZip = autoDeployZip;
    }

    @Override
    public boolean isAutoDeployExplodedContent() {
        return autoDeployExploded;
    }

    @Override
    public void setAutoDeployExplodedContent(boolean autoDeployExploded) {
        if (autoDeployExploded && !this.autoDeployExploded) {
            ROOT_LOGGER.explodedAutoDeploymentContentWarning(DO_DEPLOY, CommonAttributes.AUTO_DEPLOY_EXPLODED);
        }
        this.autoDeployExploded = autoDeployExploded;
    }

    @Override
    public void setAutoDeployXMLContent(final boolean autoDeployXML) {
        this.autoDeployXml = autoDeployXML;
    }

    @Override
    public boolean isAutoDeployXMLContent() {
        return autoDeployXml;
    }

    @Override
    public boolean isEnabled() {
        return scanEnabled;
    }

    @Override
    public long getScanInterval() {
        return scanInterval;
    }

    @Override
    public synchronized void setScanInterval(long scanInterval) {
        if (scanInterval != this.scanInterval) {
            cancelScan();
        }
        this.scanInterval = scanInterval;
        startScan();
    }

    @Override
    public void setDeploymentTimeout(long deploymentTimeout) {
        this.deploymentTimeout = deploymentTimeout;
    }

    @Override
    public synchronized void startScanner() {
        assert deploymentOperationsFactory != null : "deploymentOperationsFactory is null";
        startScanner(deploymentOperationsFactory.create());
    }

    @Override
    public void setRuntimeFailureCausesRollback(boolean rollbackOnRuntimeFailure) {
        this.rollbackOnRuntimeFailure = rollbackOnRuntimeFailure;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void startScanner(final DeploymentOperations deploymentOperations) {
        this.deploymentOperations = deploymentOperations;
        final boolean scanEnabled = this.scanEnabled;
        if (scanEnabled) {
            return;
        }
        this.scanEnabled = true;
        startScan();
        ROOT_LOGGER.started(getClass().getSimpleName(), deploymentDir.getAbsolutePath());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void stopScanner() {
        this.scanEnabled = false;
        cancelScan();
        safeClose(deploymentOperations);
        this.deploymentOperations = null;
        if (undeployScanTask != null) {
            undeployScanTask.cancel(true);
        }
        this.undeployScanTask = null;
    }

    /** Allow DeploymentScannerService to set the factory on the boot-time scanner */
    void setDeploymentOperationsFactory(final DeploymentOperations.Factory factory) {
        assert factory != null : "factory is null";
        this.deploymentOperationsFactory = factory;
    }

    /** Set the ProcessStateNotifier to allow this object to trigger cleanup tasks when
     * the process reaches {@code RUNNING} state. We use a setter instead
     * of constructor injection to allow DeploymentScannerService to set it on the boot-time scanner */
    void setProcessStateNotifier(ProcessStateNotifier notifier) {
        assert this.processStateNotifier == null;
        this.processStateNotifier = notifier;
        if (notifier != null) {
            this.propertyChangeListener = new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    if (ControlledProcessState.State.RUNNING == evt.getNewValue()) {
                        synchronized (FileSystemDeploymentService.this) {
                            if (scanEnabled) {
                                undeployScanTask = scheduledExecutor.submit(new UndeployScanRunnable());
                            }
                        }
                    } else if (ControlledProcessState.State.STOPPING == evt.getNewValue()) {
                        //let's prevent the starting of a new scan
                        synchronized (FileSystemDeploymentService.this) {
                            scanEnabled = false;
                            if (undeployScanTask != null) {
                                undeployScanTask.cancel(true);
                                undeployScanTask = null;
                            }
                        }
                        processStateNotifier.removePropertyChangeListener(this);
                    }
                }
            };
            this.processStateNotifier.addPropertyChangeListener(propertyChangeListener);
        } else {
            this.propertyChangeListener = null;
        }
    }

    /**
     * Hook solely for unit test to control how long deployments with no progress can exist without failing
     */
    void setMaxNoProgress(long max) {
        this.maxNoProgress = max;
    }

    private void establishDeployedContentList(File dir, final DeploymentOperations deploymentOperations) {
        final Set<String> deploymentNames = deploymentOperations.getDeploymentsStatus().keySet();
        final List<File> children = listDirectoryChildren(dir);
        for (File child : children) {
            final String fileName = child.getName();
            if (child.isDirectory()) {
                if (!isEEArchive(fileName)) {
                    establishDeployedContentList(child, deploymentOperations);
                }
            } else if (fileName.endsWith(DEPLOYED)) {
                final String deploymentName = fileName.substring(0, fileName.length() - DEPLOYED.length());
                if (deploymentNames.contains(deploymentName)) {
                    File deployment = new File(dir, deploymentName);
                    deployed.put(deploymentName, new DeploymentMarker(child.lastModified(), !deployment.isDirectory(), dir));
                } else {
                    if (!child.delete()) {
                        ROOT_LOGGER.cannotRemoveDeploymentMarker(fileName);
                    }
                    // AS7-1130 Put down a marker so we deploy on first scan
                    File skipDeploy = new File(dir, deploymentName + SKIP_DEPLOY);
                    if (!skipDeploy.exists()) {
                        final File deployedMarker = new File(dir, deploymentName + DO_DEPLOY);
                        createMarkerFile(deployedMarker, deploymentName);
                    }
                }
            }
        }
    }

    /** Perform a one-off scan during boot to establish deployment tasks to execute during boot */
    void bootTimeScan(final DeploymentOperations deploymentOperations) {
        // WFCORE-1579: skip the scan if deployment dir is not available
        if (!checkDeploymentDir(this.deploymentDir)) {
            DeploymentScannerLogger.ROOT_LOGGER.bootTimeScanFailed(deploymentDir.getAbsolutePath());
            return;
        }

        this.establishDeployedContentList(this.deploymentDir, deploymentOperations);
        deployedContentEstablished = true;
        if (acquireScanLock()) {
            try {
                scan(true, deploymentOperations);
            } finally {
                releaseScanLock();
            }
        }
    }

    /** Perform a one-off scan to establish deployment tasks */
    void singleScan() {
        assert deploymentOperationsFactory != null : "deploymentOperationsFactory is null";
        ManualScanCallable manualScan = new ManualScanCallable();
        scheduledExecutor.submit(manualScan);
    }

    /** Perform a normal scan */
    void scan() {
        if (acquireScanLock()) {
            boolean scheduleRescan = false;
            try {
                scheduleRescan = scan(false, deploymentOperations);
            } finally {
                try {
                    if (scheduleRescan) {
                        synchronized (this) {
                            if (scanEnabled) {
                                rescanIncompleteTask = scheduledExecutor.schedule(scanRunnable, 200, TimeUnit.MILLISECONDS);
                            }
                        }
                    }
                } finally {
                    releaseScanLock();
                }
            }
        }
    }

    /**
     * Perform a post-boot scan to remove any deployments added during boot that failed to deploy properly.
     * This method isn't private solely to allow a unit test in the same package to call it.
     */
    void forcedUndeployScan() {

        if (acquireScanLock()) {
            try {
                ROOT_LOGGER.tracef("Performing a post-boot forced undeploy scan for scan directory %s", deploymentDir.getAbsolutePath());
                ScanContext scanContext = new ScanContext(deploymentOperations);

                // Add remove actions to the plan for anything we count as
                // deployed that we didn't find on the scan
                for (Map.Entry<String, DeploymentMarker> missing : scanContext.toRemove.entrySet()) {
                    // remove successful deployment and left will be removed
                    if (scanContext.registeredDeployments.containsKey(missing.getKey())) {
                        scanContext.registeredDeployments.remove(missing.getKey());
                    }
                }
                Set<String> scannedDeployments = new HashSet<String>(scanContext.registeredDeployments.keySet());
                scannedDeployments.removeAll(scanContext.persistentDeployments);

                List<ScannerTask> scannerTasks = scanContext.scannerTasks;
                for (String toUndeploy : scannedDeployments) {
                    scannerTasks.add(new UndeployTask(toUndeploy, deploymentDir, scanContext.scanStartTime, true));
                }
                try {
                    executeScannerTasks(scannerTasks, deploymentOperations, true);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                ROOT_LOGGER.tracef("Forced undeploy scan complete");
            } catch (Exception e) {
                ROOT_LOGGER.scanException(e, deploymentDir.getAbsolutePath());
            } finally {
                releaseScanLock();
            }
        }
    }

    private boolean acquireScanLock() {
        try {
            scanLock.lockInterruptibly();
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void releaseScanLock() {
        scanLock.unlock();
    }

    private boolean scan(boolean oneOffScan, final DeploymentOperations deploymentOperations) {

        boolean scheduleRescan = false;

        if (scanEnabled || oneOffScan) { // confirm the scan is still wanted
            ROOT_LOGGER.tracef("Scanning directory %s for deployment content changes", deploymentDir.getAbsolutePath());

            // WFCORE-1579: skip the scan if deployment dir is not available
            if (!checkDeploymentDir(deploymentDir)) {
                if (lastScanSuccessful) {
                    lastScanSuccessful = false;
                    ROOT_LOGGER.scanFailed(deploymentDir.getAbsolutePath());
                }
                return scheduleRescan;
            }
            // if deployed content list was not established during scanner start (due to inaccessible deployment dir),
            // do it now
            if (!deployedContentEstablished) {
                establishDeployedContentList(deploymentDir, deploymentOperations);
                deployedContentEstablished = true;
            }

            ScanContext scanContext = null;
            try {
                scanContext = new ScanContext(deploymentOperations);
            } catch (RuntimeException ex) {
                //scanner has stoppped in the meanwhile so we don't need to pursue
                if (!scanEnabled) {
                    return scheduleRescan;
                }
                throw ex;
            }

            scanDirectory(deploymentDir, relativePath, scanContext);

            // WARN about markers with no associated content. Do this first in case any auto-deploy issue
            // is due to a file that wasn't meant to be auto-deployed, but has a misspelled marker
            ignoredMissingDeployments.retainAll(scanContext.ignoredMissingDeployments);
            for (String deploymentName : scanContext.ignoredMissingDeployments) {
                if (ignoredMissingDeployments.add(deploymentName)) {
                    ROOT_LOGGER.deploymentNotFound(deploymentName);
                }
            }

            // Log INFO about non-auto-deploy files that have no marker files
            noticeLogged.retainAll(scanContext.nonDeployable);
            for (String fileName : scanContext.nonDeployable) {
                if (noticeLogged.add(fileName)) {
                    ROOT_LOGGER.deploymentTriggered(fileName, DO_DEPLOY);
                }
            }

            // Log ERROR about META-INF and WEB-INF dirs outside a deployment
            illegalDirLogged.retainAll(scanContext.illegalDir);
            for (String fileName : scanContext.illegalDir) {
                if (illegalDirLogged.add(fileName)) {
                    ROOT_LOGGER.invalidExplodedDeploymentDirectory(fileName, deploymentDir.getAbsolutePath());
                }
            }

            // Log about deleting exploded deployments without first triggering undeploy by deleting .deployed
            prematureExplodedContentDeletionLogged.retainAll(scanContext.prematureExplodedDeletions);
            for (String fileName : scanContext.prematureExplodedDeletions) {
                if (prematureExplodedContentDeletionLogged.add(fileName)) {
                    ROOT_LOGGER.explodedDeploymentContentDeleted(fileName, DEPLOYED);
                }
            }

            // Deal with any incomplete or non-scannable auto-deploy content
            ScanStatus status = handleAutoDeployFailures(scanContext);
            if (status != ScanStatus.PROCEED) {
                if (status == ScanStatus.RETRY && scanInterval > 1000) {
                    // schedule a non-repeating task to try again more quickly
                    scheduleRescan = true;
                }
            } else {

                List<ScannerTask> scannerTasks = scanContext.scannerTasks;

                // Add remove actions to the plan for anything we count as
                // deployed that we didn't find on the scan
                for (Map.Entry<String, DeploymentMarker> missing : scanContext.toRemove.entrySet()) {
                    scannerTasks.add(new UndeployTask(missing.getKey(), missing.getValue().parentFolder, scanContext.scanStartTime, false));
                }
                try {
                    executeScannerTasks(scannerTasks, deploymentOperations, oneOffScan);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                ROOT_LOGGER.tracef("Scan complete");
                firstScan = false;
            }
        }

        return scheduleRescan;
    }

    private void executeScannerTasks(List<ScannerTask> scannerTasks, DeploymentOperations deploymentOperations,
                                     boolean oneOffScan) throws InterruptedException {
        // Process the tasks
        if (!scannerTasks.isEmpty()) {
            List<ModelNode> updates = new ArrayList<ModelNode>(scannerTasks.size());

            for (ScannerTask task : scannerTasks) {
                task.recordInProgress(); // puts down .isdeploying, .isundeploying
                final ModelNode update = task.getUpdate();
                if (ROOT_LOGGER.isDebugEnabled()) {
                    ROOT_LOGGER.debugf("Deployment scan of [%s] found update action [%s]", deploymentDir, update);
                }
                updates.add(update);
            }

            boolean first = true;
            while (!updates.isEmpty() && (first || !oneOffScan)) {
                first = false;
                final ModelNode results;
                try {
                    final Future<ModelNode> futureResults = deploymentOperations.deploy(getCompositeUpdate(updates), scheduledExecutor);
                    try {
                        results = futureResults.get(deploymentTimeout, TimeUnit.SECONDS);
                    } catch (TimeoutException e) {
                        futureResults.cancel(true);
                        final ModelNode failure = new ModelNode();
                        failure.get(OUTCOME).set(FAILED);
                        failure.get(FAILURE_DESCRIPTION).set(DeploymentScannerLogger.ROOT_LOGGER.deploymentTimeout(deploymentTimeout));
                        for (ScannerTask task : scannerTasks) {
                            task.handleFailureResult(failure);
                        }
                        break;
                    } catch (InterruptedException e) {
                        futureResults.cancel(true);
                        throw e;
                    } catch (Exception e) {
                        ROOT_LOGGER.fileSystemDeploymentFailed(e);
                        futureResults.cancel(true);
                        final ModelNode failure = new ModelNode();
                        failure.get(OUTCOME).set(FAILED);
                        failure.get(FAILURE_DESCRIPTION).set(e.getMessage());
                        for (ScannerTask task : scannerTasks) {
                            task.handleFailureResult(failure);
                        }
                        break;
                    }
                } catch(RejectedExecutionException ex) { //The executor was closed and no task could be submitted.
                    for (ScannerTask task : scannerTasks) {
                        task.removeInProgressMarker();
                    }
                    break;
                }
                final List<ModelNode> toRetry = new ArrayList<ModelNode>();
                final List<ScannerTask> retryTasks = new ArrayList<ScannerTask>();
                if (results.hasDefined(RESULT)) {
                    final List<Property> resultList = results.get(RESULT).asPropertyList();
                    for (int i = 0; i < resultList.size(); i++) {
                        final ModelNode result = resultList.get(i).getValue();
                        final ScannerTask task = scannerTasks.get(i);
                        final ModelNode outcome = result.get(OUTCOME);
                        StringBuilder failureDesc = new StringBuilder();
                        if (outcome.isDefined() && SUCCESS.equals(outcome.asString()) && handleCompositeResult(result, failureDesc)){
                            task.handleSuccessResult();
                        } else if (outcome.isDefined() && CANCELLED.equals(outcome.asString())) {
                            toRetry.add(updates.get(i));
                            retryTasks.add(task);
                        } else {
                            if (failureDesc.length() > 0) {
                                result.get(FAILURE_DESCRIPTION).set(failureDesc.toString());
                            }
                            task.handleFailureResult(result);
                        }
                    }
                    updates = toRetry;
                    scannerTasks = retryTasks;
                } else {
                    for (ScannerTask current : scannerTasks) {
                        current.handleFailureResult(results);
                    }
                }
            }
        }
    }

    private class ManualScanCallable implements Runnable {
        @Override
        public void run() {
            DeploymentOperations operations = deploymentOperations;
            if (operations == null) {
                operations = deploymentOperationsFactory.create();
            }
            if (acquireScanLock()) {
                try {
                    DeploymentScannerLogger.ROOT_LOGGER.debug("Manual scan launched");
                    scan(true, operations);
                } catch (Exception e) {
                    ROOT_LOGGER.scanException(e, deploymentDir.getAbsolutePath());
                } finally {
                    releaseScanLock();
                }
            }
        }
    }

    private class UndeployScanRunnable implements Runnable {

        @Override
        public void run() {
            if(rollbackOnRuntimeFailure) {
                forcedUndeployScan();
            }
            processStateNotifier.removePropertyChangeListener(propertyChangeListener);
        }
    }

    private boolean handleCompositeResult(ModelNode resultNode, StringBuilder failureDesc) {
        // WFLY-1305, regardless rollback-on-runtime-failure option, check each composite step result
        ModelNode outcome = resultNode.get(OUTCOME);
        boolean success = true;
        if (resultNode.get(OUTCOME).isDefined() && SUCCESS.equals(outcome.asString())) {
            if (resultNode.get(RESULT).isDefined()) {
                List<Property> results = resultNode.get(RESULT).asPropertyList();
                for (int i = 0; i < results.size(); i++) {
                    if (!handleCompositeResult(results.get(i).getValue(), failureDesc)) {
                        success = false;
                        break;
                    }
                }
            }
        } else {
            success = false;
            if (resultNode.get(FAILURE_DESCRIPTION).isDefined()) {
                failureDesc.append(resultNode.get(FAILURE_DESCRIPTION).toString());
            }
        }

        return success;
    }

    /**
     * Checks that given directory if readable & writable and prints a warning if the check fails. Warning is only
     * printed once and is not repeated until the condition is fixed and broken again.
     *
     * @param directory deployment directory
     * @return does given directory exist and is readable and writable?
     */
    private boolean checkDeploymentDir(File directory) {
        if (!directory.exists()) {
            if (deploymentDirAccessible) {
                deploymentDirAccessible = false;
                ROOT_LOGGER.directoryIsNonexistent(deploymentDir.getAbsolutePath());
            }
        }
        else if (!directory.isDirectory()) {
            if (deploymentDirAccessible) {
                deploymentDirAccessible = false;
                ROOT_LOGGER.isNotADirectory(deploymentDir.getAbsolutePath());
            }
        }
        else if (!directory.canRead()) {
            if (deploymentDirAccessible) {
                deploymentDirAccessible = false;
                ROOT_LOGGER.directoryIsNotReadable(deploymentDir.getAbsolutePath());
            }
        }
        else if (!directory.canWrite()) {
            if (deploymentDirAccessible) {
                deploymentDirAccessible = false;
                ROOT_LOGGER.directoryIsNotWritable(deploymentDir.getAbsolutePath());
            }
        } else {
            deploymentDirAccessible = true;
        }

        return deploymentDirAccessible;
    }

    /**
     * Scan the given directory for content changes.
     *
     * @param directory   the directory to scan
     * @param scanContext context of the scan
     */
    private void scanDirectory(final File directory, final String relativePath, final ScanContext scanContext) {
        final List<File> children = listDirectoryChildren(directory, filter);
        for (File child : children) {
            final String fileName = child.getName();
            if (fileName.endsWith(DEPLOYED)) {
                final String deploymentName = fileName.substring(0, fileName.length() - DEPLOYED.length());
                DeploymentMarker deploymentMarker = deployed.get(deploymentName);
                if (deploymentMarker == null) {
                    scanContext.toRemove.remove(deploymentName);
                    removeExtraneousMarker(child, fileName);
                } else {
                    final File deploymentFile = new File(directory, deploymentName);
                    if (deploymentFile.exists()) {
                        scanContext.toRemove.remove(deploymentName);
                        if (deployed.get(deploymentName).lastModified != child.lastModified()) {
                            scanContext.scannerTasks.add(new RedeployTask(deploymentName, child.lastModified(), directory,
                                    !child.isDirectory()));
                        } else {
                            // AS7-784 check for undeploy or removal of the deployment via another management client
                            Boolean isDeployed = scanContext.registeredDeployments.get(deploymentName);
                            if (isDeployed == null || !isDeployed) {
                                // It was undeployed or removed; get rid of the .deployed marker and
                                // put down a .undeployed marker so we don't process this again
                                deployed.remove(deploymentName);
                                removeExtraneousMarker(child, fileName);
                                final File marker = new File(directory, deploymentName + UNDEPLOYED);
                                createMarkerFile(marker, deploymentName);
                                if (isDeployed == null) {
                                    DeploymentScannerLogger.ROOT_LOGGER.scannerDeploymentRemovedButNotByScanner(deploymentName, marker);
                                } else {
                                    DeploymentScannerLogger.ROOT_LOGGER.scannerDeploymentUndeployedButNotByScanner(deploymentName, marker);
                                }
                            }
                        }
                    } else {
                        boolean autoDeployable = deploymentMarker.archive ? autoDeployZip : autoDeployExploded;
                        if (!autoDeployable) {
                            // Don't undeploy but log a warn if this is exploded content
                            scanContext.toRemove.remove(deploymentName);
                            if (!deploymentMarker.archive) {
                                scanContext.prematureExplodedDeletions.add(deploymentName);
                            }
                        }
                        // else AS7-1240 -- content is gone, leave deploymentName in scanContext.toRemove to trigger undeploy
                    }
                }
            } else if (fileName.endsWith(DO_DEPLOY) || (fileName.endsWith(FAILED_DEPLOY) && firstScan)) {
                // AS7-2581 - attempt to redeploy failed deployments on restart.
                final String markerStatus = fileName.endsWith(DO_DEPLOY) ? DO_DEPLOY : FAILED_DEPLOY;
                final String deploymentName = fileName.substring(0, fileName.length() - markerStatus.length());

                if (FAILED_DEPLOY.equals(markerStatus)) {
                    if (!scanContext.firstScanDeployments.add(deploymentName)) {
                        continue;
                    }
                    ROOT_LOGGER.reattemptingFailedDeployment(deploymentName);
                }

                final File deploymentFile = new File(directory, deploymentName);
                if (!deploymentFile.exists()) {
                    scanContext.ignoredMissingDeployments.add(deploymentName);
                    continue;
                }
                long timestamp = getDeploymentTimestamp(deploymentFile);
                final String path = relativeTo == null ? deploymentFile.getAbsolutePath() : relativePath + deploymentName; // TODO:
                // sub-directories
                // in
                // the
                // deploymentDir
                final boolean archive = deploymentFile.isFile();
                addContentAddingTask(path, archive, deploymentName, deploymentFile, timestamp, scanContext);
            } else if (fileName.endsWith(FAILED_DEPLOY)) {
                final String deploymentName = fileName.substring(0, fileName.length() - FAILED_DEPLOY.length());
                scanContext.toRemove.remove(deploymentName);
                if (!deployed.containsKey(deploymentName) && !(new File(child.getParent(), deploymentName).exists())) {
                    removeExtraneousMarker(child, fileName);
                }
            } else if (isEEArchive(fileName)) {
                boolean autoDeployable = child.isDirectory() ? autoDeployExploded : autoDeployZip;
                if (autoDeployable) {
                    if (!isAutoDeployDisabled(child)) {
                        long timestamp = getDeploymentTimestamp(child);
                        synchronizeScannerStatus(scanContext, directory, fileName, timestamp);
                        if (isFailedOrUndeployed(scanContext, directory, fileName, timestamp) || scanContext.firstScanDeployments.contains(fileName)) {
                            continue;
                        }

                        DeploymentMarker marker = deployed.get(fileName);
                        if (marker == null || marker.lastModified != timestamp) {
                            try {
                                if (isZipComplete(child)) {
                                    final String path = relativeTo == null ? child.getAbsolutePath() : relativePath + fileName;
                                    final boolean archive = child.isFile();
                                    if(firstScan){
                                        scanContext.firstScanDeployments.add(fileName);
                                    }
                                    addContentAddingTask(path, archive, fileName, child, timestamp, scanContext);
                                } else {
                                    //we need to make sure that the file was not deleted while
                                    //the scanner was running
                                    if (child.exists()) {
                                        scanContext.incompleteFiles.put(child, new IncompleteDeploymentStatus(child, timestamp));
                                    }
                                }
                            } catch (NonScannableZipException e) {
                                // Track for possible logging in scan()
                                scanContext.nonscannable.put(child, new NonScannableStatus(e, timestamp));
                            }
                        }
                    }
                } else if (!deployed.containsKey(fileName) && !new File(fileName + DO_DEPLOY).exists()
                        && !new File(fileName + FAILED_DEPLOY).exists()) {
                    // Track for possible INFO logging of the need for a marker
                    scanContext.nonDeployable.add(fileName);
                }
            } else if (isXmlFile(fileName)) {
                if (autoDeployXml) {
                    if (!isAutoDeployDisabled(child)) {
                        long timestamp = getDeploymentTimestamp(child);
                        if (isFailedOrUndeployed(scanContext, directory, fileName, timestamp) || scanContext.firstScanDeployments.contains(fileName)) {
                            continue;
                        }

                        DeploymentMarker marker = deployed.get(fileName);
                        if (marker == null || marker.lastModified != timestamp) {
                            if (isXmlComplete(child)) {
                                final String path = relativeTo == null ? child.getAbsolutePath() : relativePath + fileName;
                                if(firstScan){
                                    scanContext.firstScanDeployments.add(fileName);
                                }
                                addContentAddingTask(path, true, fileName, child, timestamp, scanContext);
                            } else {
                                //we need to make sure that the file was not deleted while
                                //the scanner was running
                                if (child.exists()) {
                                    scanContext.incompleteFiles.put(child, new IncompleteDeploymentStatus(child, timestamp));
                                }
                            }
                        }
                    }
                } else if (!deployed.containsKey(fileName) && !new File(fileName + DO_DEPLOY).exists()
                        && !new File(fileName + FAILED_DEPLOY).exists()) {
                    // Track for possible INFO logging of the need for a marker
                    scanContext.nonDeployable.add(fileName);
                }
            } else if (fileName.endsWith(DEPLOYING) || fileName.endsWith(UNDEPLOYING)) {
                // These markers should not survive a scan
                removeExtraneousMarker(child, fileName);
            } else if (fileName.endsWith(PENDING)) {
                // Do some housekeeping if the referenced deployment is gone
                final String deploymentName = fileName.substring(0, fileName.length() - PENDING.length());
                File deployment = new File(child.getParent(), deploymentName);
                if (!deployment.exists()) {
                    removeExtraneousMarker(child, fileName);
                }
            } else if (child.isDirectory()) { // exploded deployments would have been caught by isEEArchive(fileName) above

                if (WEB_INF.equalsIgnoreCase(fileName) || META_INF.equalsIgnoreCase(fileName)) {
                    // Looks like someone unzipped an archive in the scanned dir
                    // Track for possible ERROR logging
                    scanContext.illegalDir.add(fileName);
                } else {
                    scanDirectory(child, relativePath + child.getName() + File.separator, scanContext);
                }
            }
        }
    }

    private boolean isXmlComplete(final File xmlFile) {
        try {
            return XmlCompletionScanner.isCompleteDocument(xmlFile);
        } catch (Exception e) {
            ROOT_LOGGER.failedCheckingXMLFile(e, xmlFile.getPath());
            return false;
        }
    }

    private boolean isFailedOrUndeployed(final ScanContext scanContext, final File directory, final String fileName, final long timestamp) {
        final File failedMarker = new File(directory, fileName + FAILED_DEPLOY);
        if (failedMarker.exists() && timestamp <= failedMarker.lastModified()) {
            return true;
        }
        final File undeployedMarker = new File(directory, fileName + UNDEPLOYED);
        if (isMarkedUndeployed(undeployedMarker, timestamp) && !isRegisteredDeployment(scanContext, fileName)) {
            return true;
        }
        return false;
    }

    private void synchronizeScannerStatus(ScanContext scanContext, File directory, String fileName, long timestamp) {
        if (isRegisteredDeployment(scanContext, fileName)) {
            final File undeployedMarker = new File(directory, fileName + UNDEPLOYED);
            if (isMarkedUndeployed(undeployedMarker, timestamp) && !scanContext.persistentDeployments.contains(fileName)) {
                try {
                    ROOT_LOGGER.scannerDeploymentRedeployedButNotByScanner(fileName, undeployedMarker);
                    //We have a deployed app with an undeployed marker
                    undeployedMarker.delete();
                    final File deployedMarker = new File(directory, fileName + DEPLOYED);
                    deployedMarker.createNewFile();
                    boolean isArchive = false;
                    if (deployed.containsKey(fileName)) {
                        isArchive = deployed.get(fileName).archive;
                        deployedMarker.setLastModified(deployed.get(fileName).lastModified);
                    } else {
                        final File deploymentFile = new File(directory, fileName);
                        isArchive = deploymentFile.exists() && deploymentFile.isFile();
                        if(deploymentFile.exists()) {
                            deployedMarker.setLastModified(deploymentFile.lastModified());
                        }
                    }
                    deployed.put(fileName, new DeploymentMarker(deployedMarker.lastModified(), isArchive, directory));
                } catch (IOException ex) {
                    ROOT_LOGGER.failedStatusSynchronization(ex, fileName);
                }
            }
        }
    }

    private long addContentAddingTask(final String path, final boolean archive, final String deploymentName,
                                      final File deploymentFile, final long timestamp, final ScanContext scanContext) {
        if (deploymentTransformer != null) {
            try {
                deploymentTransformer.transform(deploymentFile.toPath(), deploymentFile.toPath());
                deploymentFile.setLastModified(timestamp);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }
        if (scanContext.registeredDeployments.containsKey(deploymentName)) {
            scanContext.scannerTasks.add(new ReplaceTask(path, archive, deploymentName, deploymentFile, timestamp));
        } else {
            scanContext.scannerTasks.add(new DeployTask(path, archive, deploymentName, deploymentFile, timestamp));
        }
        scanContext.toRemove.remove(deploymentName);
        return timestamp;
    }

    private boolean isRegisteredDeployment(final ScanContext scanContext, final String fileName) {
        if(!scanContext.persistentDeployments.contains(fileName)) {//check that we are talking about the deployment in the scanned folder
            return scanContext.registeredDeployments.get(fileName) == null ? false : scanContext.registeredDeployments.get(fileName);
        }
        return false;
    }

    private boolean isMarkedUndeployed(final File undeployedMarker, final long timestamp) {
        return undeployedMarker.exists() && timestamp <= undeployedMarker.lastModified();
    }

    private boolean isZipComplete(File file) throws NonScannableZipException {
        if (file.isDirectory()) {
            for (File child : listDirectoryChildren(file)) {
                if (!isZipComplete(child)) {
                    return false;
                }
            }
            return true;
        } else if (isEEArchive(file.getName())) {
            try {
                return ZipCompletionScanner.isCompleteZip(file);
            } catch (IOException e) {
                ROOT_LOGGER.failedCheckingZipFile(e, file.getPath());
                return false;
            }
        } else {
            // A non-zip child
            return true;
        }
    }

    private boolean isAutoDeployDisabled(File file) {
        final File parent = file.getParentFile();
        final String name = file.getName();
        return new File(parent, name + SKIP_DEPLOY).exists() || new File(parent, name + DO_DEPLOY).exists();
    }

    private long getDeploymentTimestamp(File deploymentFile) {
        if (deploymentFile.isDirectory()) {
            // Scan for most recent file
            long latest = deploymentFile.lastModified();
            for (File child : listDirectoryChildren(deploymentFile)) {
                long childTimestamp = getDeploymentTimestamp(child);
                if (childTimestamp > latest) {
                    latest = childTimestamp;
                }
            }
            return latest;
        } else {
            return deploymentFile.lastModified();
        }
    }

    private boolean isEEArchive(String fileName) {
        return ARCHIVE_PATTERN.matcher(fileName).matches();
    }

    private boolean isXmlFile(String fileName) {
        return fileName.endsWith(".xml");
    }

    private void removeExtraneousMarker(File child, final String fileName) {
        if (!child.delete()) {
            ROOT_LOGGER.cannotRemoveDeploymentMarker(fileName);
        }
    }

    /**
     * Handle incompletely copied or non-scannable auto-deploy content and then abort scan
     *
     * @return true if the scan should be aborted
     */
    private ScanStatus handleAutoDeployFailures(ScanContext scanContext) {

        ScanStatus result = ScanStatus.PROCEED;
        boolean warnLogged = false;

        Set<File> noLongerIncomplete = new HashSet<File>(incompleteDeployments.keySet());
        noLongerIncomplete.removeAll(scanContext.incompleteFiles.keySet());

        int oldIncompleteCount = incompleteDeployments.size();
        incompleteDeployments.keySet().retainAll(scanContext.incompleteFiles.keySet());
        if (scanContext.incompleteFiles.size() > 0) {

            result = ScanStatus.RETRY;

            // If user dealt with some incomplete stuff but others remain, log everything again
            boolean logAll = incompleteDeployments.size() != oldIncompleteCount;

            long now = System.currentTimeMillis();
            for (Map.Entry<File, IncompleteDeploymentStatus> entry : scanContext.incompleteFiles.entrySet()) {
                File incompleteFile = entry.getKey();
                String deploymentName = incompleteFile.getName();
                IncompleteDeploymentStatus status = incompleteDeployments.get(incompleteFile);
                if (status == null || status.size < entry.getValue().size) {
                    status = entry.getValue();
                }

                if (now - status.timestamp > maxNoProgress) {
                    if (!status.warned) {
                        // Treat no progress for an extended period as a failed deployment
                        String suffix = deployed.containsKey(deploymentName) ? DeploymentScannerLogger.ROOT_LOGGER.previousContentDeployed() : "";
                        String msg = DeploymentScannerLogger.ROOT_LOGGER.deploymentContentIncomplete(incompleteFile, suffix);
                        writeFailedMarker(incompleteFile, msg, status.timestamp);
                        ROOT_LOGGER.error(msg);
                        status.warned = true;
                        warnLogged = true;

                        result = ScanStatus.ABORT;
                    }

                    // Clean up any .pending file
                    new File(incompleteFile.getParentFile(), deploymentName + PENDING).delete();
                } else {
                    boolean newIncomplete = incompleteDeployments.put(incompleteFile, status) == null;
                    if (newIncomplete || logAll) {
                        ROOT_LOGGER.incompleteContent(entry.getKey().getPath());
                    }
                    if (newIncomplete) {
                        File pending = new File(incompleteFile.getParentFile(), deploymentName + PENDING);
                        createMarkerFile(pending, deploymentName);
                    }
                }
            }
        }

        // Clean out any old "pending" files
        for (File complete : noLongerIncomplete) {
            File pending = new File(complete.getParentFile(), complete.getName() + PENDING);
            removeExtraneousMarker(pending, pending.getName());
        }

        int oldNonScannableCount = nonscannableLogged.size();
        nonscannableLogged.retainAll(scanContext.nonscannable.keySet());
        if (scanContext.nonscannable.size() > 0) {

            result = (result == ScanStatus.PROCEED ? ScanStatus.RETRY : result);

            // If user dealt with some nonscannable stuff but others remain, log everything again
            boolean logAll = nonscannableLogged.size() != oldNonScannableCount;

            for (Map.Entry<File, NonScannableStatus> entry : scanContext.nonscannable.entrySet()) {
                File nonScannable = entry.getKey();
                String fileName = nonScannable.getName();
                if (nonscannableLogged.add(nonScannable) || logAll) {
                    NonScannableStatus nonScannableStatus = entry.getValue();
                    NonScannableZipException e = nonScannableStatus.exception;
                    String msg = DeploymentScannerLogger.ROOT_LOGGER.unsafeAutoDeploy2(e.getLocalizedMessage(), fileName, DO_DEPLOY);
                    writeFailedMarker(nonScannable, msg, nonScannableStatus.timestamp);
                    ROOT_LOGGER.error(msg);
                    warnLogged = true;

                    result = ScanStatus.ABORT;
                }
            }
        }

        if (warnLogged) {

            Set<String> allProblems = new HashSet<String>();
            for (File f : scanContext.nonscannable.keySet()) {
                allProblems.add(f.getName());
            }
            for (File f : scanContext.incompleteFiles.keySet()) {
                allProblems.add(f.getName());
            }

            ROOT_LOGGER.unsafeAutoDeploy(DO_DEPLOY, SKIP_DEPLOY, allProblems);
        }

        return result;
    }

    private synchronized void startScan() {
        if (scanEnabled) {
            if (scanInterval > 0) {
                scanTask = scheduledExecutor.scheduleWithFixedDelay(scanRunnable, 0, scanInterval, TimeUnit.MILLISECONDS);
            } else {
                scanTask = scheduledExecutor.schedule(scanRunnable, scanInterval, TimeUnit.MILLISECONDS);
            }
        }
    }

    /**
     * Invoke with the object monitor held
     */
    private void cancelScan() {
        if (rescanIncompleteTask != null) {
            rescanIncompleteTask.cancel(true);
            rescanIncompleteTask = null;
        }
        if (rescanUndeployTask != null) {
            rescanUndeployTask.cancel(true);
            rescanUndeployTask = null;
        }
        if (scanTask != null) {
            scanTask.cancel(true);
            scanTask = null;
        }
    }

    private ModelNode getCompositeUpdate(final List<ModelNode> updates) {
        final ModelNode op = Util.getEmptyOperation(COMPOSITE, new ModelNode());
        final ModelNode steps = op.get(STEPS);
        for (ModelNode update : updates) {
            steps.add(update);
        }
        op.get(OPERATION_HEADERS, ROLLBACK_ON_RUNTIME_FAILURE).set(rollbackOnRuntimeFailure);
        return op;
    }

    private ModelNode getCompositeUpdate(final ModelNode... updates) {
        final ModelNode op = Util.getEmptyOperation(COMPOSITE, new ModelNode());
        final ModelNode steps = op.get(STEPS);
        for (ModelNode update : updates) {
            steps.add(update);
        }
        op.get(OPERATION_HEADERS, ROLLBACK_ON_RUNTIME_FAILURE).set(rollbackOnRuntimeFailure);
        return op;
    }

    private void safeClose(final Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void createMarkerFile(final File marker, String deploymentName) {
        FileOutputStream fos = null;
        try {
            // marker.createNewFile(); - Don't create before the write as there is a potential race condition where
            // the file is deleted between the two calls.
            fos = new FileOutputStream(marker);
            fos.write(deploymentName.getBytes(StandardCharsets.UTF_8));
        } catch (IOException io) {
            ROOT_LOGGER.errorWritingDeploymentMarker(io, marker.getAbsolutePath());
        } finally {
            safeClose(fos);
        }
    }

    private void writeFailedMarker(final File deploymentFile, final String failureDescription, long failureTimestamp) {
        final File failedMarker = new File(deploymentFile.getParent(), deploymentFile.getName() + FAILED_DEPLOY);
        final File deployMarker = new File(deploymentFile.getParent(), deploymentFile.getName() + DO_DEPLOY);
        if (deployMarker.exists() && !deployMarker.delete()) {
            ROOT_LOGGER.cannotRemoveDeploymentMarker(deployMarker);
        }
        final File deployedMarker = new File(deploymentFile.getParent(), deploymentFile.getName() + DEPLOYED);
        if (deployedMarker.exists() && !deployedMarker.delete()) {
            ROOT_LOGGER.cannotRemoveDeploymentMarker(deployedMarker);
        }
        final File undeployedMarker = new File(deploymentFile.getParent(), deploymentFile.getName() + UNDEPLOYED);
        if (undeployedMarker.exists() && !undeployedMarker.delete()) {
            ROOT_LOGGER.cannotRemoveDeploymentMarker(undeployedMarker);
        }
        FileOutputStream fos = null;
        try {
            // failedMarker.createNewFile();
            fos = new FileOutputStream(failedMarker);
            fos.write(failureDescription.getBytes(StandardCharsets.UTF_8));
        } catch (IOException io) {
            ROOT_LOGGER.errorWritingDeploymentMarker(io, failedMarker.getAbsolutePath());
        } finally {
            safeClose(fos);
        }
    }

    private static DeploymentTransformer loadDeploymentTransformer() {
        Iterator<DeploymentTransformer> iter = ServiceLoader.load(DeploymentTransformer.class, DeploymentAddHandler.class.getClassLoader()).iterator();
        return iter.hasNext() ? iter.next() : null;
    }

    private static List<File> listDirectoryChildren(File directory) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory.toPath())) {
            final List<File> result = new ArrayList<>();
            for (Path entry : stream) {
                result.add(entry.toFile());
            }
            return result;
        } catch (SecurityException | IOException ex) {
            throw DeploymentScannerLogger.ROOT_LOGGER.cannotListDirectoryFiles(ex, directory);
        }
    }

    private static List<File> listDirectoryChildren(File directory, DirectoryStream.Filter<Path> filter) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory.toPath(), filter)) {
            final List<File> result = new ArrayList<>();
            for (Path entry : stream) {
                result.add(entry.toFile());
            }
            return result;
        } catch (SecurityException | IOException ex) {
            throw DeploymentScannerLogger.ROOT_LOGGER.cannotListDirectoryFiles(ex, directory);
        }
    }

    private abstract class ScannerTask {
        protected final String deploymentName;
        protected final String parent;
        private final String inProgressMarkerSuffix;

        private ScannerTask(final String deploymentName, final File parent, final String inProgressMarkerSuffix) {
            this.deploymentName = deploymentName;
            this.parent = parent.getAbsolutePath();
            this.inProgressMarkerSuffix = inProgressMarkerSuffix;
            File marker = new File(parent, deploymentName + PENDING);
            if (!marker.exists()) {
                createMarkerFile(marker, deploymentName);
            }
        }

        protected void recordInProgress() {
            File marker = new File(parent, deploymentName + inProgressMarkerSuffix);
            createMarkerFile(marker, deploymentName);
            deleteUndeployedMarker();
            deletePendingMarker();
        }

        protected abstract ModelNode getUpdate();

        protected abstract void handleSuccessResult();

        protected abstract void handleFailureResult(final ModelNode result);

        protected void deletePendingMarker() {
            final File pendingMarker = new File(parent, deploymentName + PENDING);
            if (pendingMarker.exists() && !pendingMarker.delete()) {
                ROOT_LOGGER.cannotRemoveDeploymentMarker(pendingMarker);
            }
        }

        protected void deleteUndeployedMarker() {
            final File undeployedMarker = new File(parent, deploymentName + UNDEPLOYED);
            if (undeployedMarker.exists() && !undeployedMarker.delete()) {
                ROOT_LOGGER.cannotRemoveDeploymentMarker(undeployedMarker);
            }
        }

        protected void deleteDeployedMarker() {
            final File deployedMarker = new File(parent, deploymentName + DEPLOYED);
            if (deployedMarker.exists() && !deployedMarker.delete()) {
                ROOT_LOGGER.cannotRemoveDeploymentMarker(deployedMarker);
            }
        }

        protected void removeInProgressMarker() {
            File marker = new File(new File(parent), deploymentName + inProgressMarkerSuffix);
            if (marker.exists() && !marker.delete()) {
                ROOT_LOGGER.cannotDeleteDeploymentProgressMarker(marker);
            }
        }
    }

    private abstract class ContentAddingTask extends ScannerTask {
        private final String path;
        private final boolean archive;
        protected final File deploymentFile;
        protected final long doDeployTimestamp;

        protected ContentAddingTask(final String path, final boolean archive, final String deploymentName,
                                    final File deploymentFile, long markerTimestamp) {
            super(deploymentName, deploymentFile.getParentFile(), DEPLOYING);
            this.path = path;
            this.archive = archive;
            this.deploymentFile = deploymentFile;
            this.doDeployTimestamp = markerTimestamp;
        }

        protected ModelNode createContent() {
            final ModelNode content = new ModelNode();
            final ModelNode contentItem = content.get(0);
            if (archive) {
                try {
                    contentItem.get(URL).set(deploymentFile.toURI().toURL().toString());
                } catch (MalformedURLException ex) {
                }
            }
            if (!contentItem.hasDefined(URL)) {
                contentItem.get(ARCHIVE).set(archive);
                contentItem.get(PATH).set(path);
                if (relativeTo != null) {
                    contentItem.get(RELATIVE_TO).set(relativeTo);
                }
            }
            return content;
        }

        @Override
        protected void handleSuccessResult() {
            final File parentFolder = new File(parent);
            final File doDeployMarker = new File(parentFolder, deploymentFile.getName() + DO_DEPLOY);
            if (doDeployMarker.exists() && !doDeployMarker.delete()) {
                ROOT_LOGGER.cannotRemoveDeploymentMarker(doDeployMarker.getAbsolutePath());
            }

            // Remove any previous failure marker
            final File failedMarker = new File(deploymentFile.getParent(), deploymentFile.getName() + FAILED_DEPLOY);
            if (failedMarker.exists() && !failedMarker.delete()) {
                ROOT_LOGGER.cannotRemoveDeploymentMarker(failedMarker);
            }

            final File deployedMarker = new File(parent, deploymentFile.getName() + DEPLOYED);
            createMarkerFile(deployedMarker, deploymentName);
            deployedMarker.setLastModified(doDeployTimestamp);
            if (deployed.containsKey(deploymentName)) {
                deployed.remove(deploymentName);
            }
            deployed.put(deploymentName, new DeploymentMarker(doDeployTimestamp, archive, parentFolder));

            // Remove the in-progress marker - save this until the deployment is really complete.
            removeInProgressMarker();
        }
    }

    private final class DeployTask extends ContentAddingTask {
        private DeployTask(final String path, final boolean archive, final String deploymentName, final File deploymentFile,
                           long markerTimestamp) {
            super(path, archive, deploymentName, deploymentFile, markerTimestamp);
        }

        @Override
        protected ModelNode getUpdate() {
            final ModelNode address = new ModelNode().add(DEPLOYMENT, deploymentName);
            final ModelNode addOp = Util.getEmptyOperation(DeploymentAddHandler.OPERATION_NAME, address);
            addOp.get(CONTENT).set(createContent());
            addOp.get(PERSISTENT).set(false);
            addOp.get(OWNER).set(resourceAddress);
            final ModelNode deployOp = Util.getEmptyOperation(DeploymentDeployHandler.OPERATION_NAME, address);
            deployOp.get(OWNER).set(resourceAddress);
            return getCompositeUpdate(addOp, deployOp);
        }

        @Override
        protected void handleFailureResult(final ModelNode result) {
            // Remove the in-progress marker
            removeInProgressMarker();
            writeFailedMarker(deploymentFile, result.get(FAILURE_DESCRIPTION).toString(), doDeployTimestamp);
        }
    }

    private final class ReplaceTask extends ContentAddingTask {
        private ReplaceTask(final String path, final boolean archive, String deploymentName, File deploymentFile,
                            long markerTimestamp) {
            super(path, archive, deploymentName, deploymentFile, markerTimestamp);
        }

        @Override
        protected ModelNode getUpdate() {
            final ModelNode replaceOp = Util.getEmptyOperation(DeploymentFullReplaceHandler.OPERATION_NAME, new ModelNode());
            replaceOp.get(NAME).set(deploymentName);
            replaceOp.get(CONTENT).set(createContent());
            replaceOp.get(PERSISTENT).set(false);
            replaceOp.get(OWNER).set(resourceAddress);
            replaceOp.get(ENABLED).set(true);
            return replaceOp;
        }

        @Override
        protected void handleFailureResult(ModelNode result) {

            // Remove the in-progress marker
            removeInProgressMarker();

            writeFailedMarker(deploymentFile, result.get(FAILURE_DESCRIPTION).toString(), doDeployTimestamp);
        }
    }

    private final class RedeployTask extends ScannerTask {
        private final long markerLastModified;
        private final boolean archive;

        private RedeployTask(final String deploymentName, final long markerLastModified, final File parent, boolean archive) {
            super(deploymentName, parent, DEPLOYING);
            this.markerLastModified = markerLastModified;
            this.archive = archive;
        }

        @Override
        protected ModelNode getUpdate() {
            final ModelNode address = new ModelNode().add(DEPLOYMENT, deploymentName);
            final ModelNode redployOp = Util.getEmptyOperation(DeploymentRedeployHandler.OPERATION_NAME, address);
            redployOp.get(OWNER).set(resourceAddress);
            return redployOp;
        }

        @Override
        protected void handleSuccessResult() {

            // Remove the in-progress marker
            removeInProgressMarker();

            deployed.remove(deploymentName);
            deployed.put(deploymentName, new DeploymentMarker(markerLastModified, archive, new File(parent)));

        }

        @Override
        protected void handleFailureResult(ModelNode result) {

            // Remove the in-progress marker
            removeInProgressMarker();

            writeFailedMarker(new File(parent, deploymentName), result.get(FAILURE_DESCRIPTION).toString(), markerLastModified);
        }
    }

    private final class UndeployTask extends ScannerTask {

        private final long scanStartTime;
        private boolean forcedUndeploy;

        private UndeployTask(final String deploymentName, final File parent, final long scanStartTime, boolean forcedUndeploy) {
            super(deploymentName, parent, UNDEPLOYING);
            this.scanStartTime = scanStartTime;
            this.forcedUndeploy = forcedUndeploy;
        }

        @Override
        protected ModelNode getUpdate() {
            final ModelNode address = new ModelNode().add(DEPLOYMENT, deploymentName);
            final ModelNode undeployOp = Util.getEmptyOperation(DeploymentUndeployHandler.OPERATION_NAME, address);
            undeployOp.get(OWNER).set(resourceAddress);
            final ModelNode removeOp = Util.getEmptyOperation(DeploymentRemoveHandler.OPERATION_NAME, address);
            return getCompositeUpdate(undeployOp, removeOp);
        }

        @Override
        protected void handleSuccessResult() {

            // Remove the in-progress marker and any .deployed marker
            removeInProgressMarker();
            if (!forcedUndeploy) {
                deleteDeployedMarker();

                final File undeployedMarker = new File(parent, deploymentName + UNDEPLOYED);
                createMarkerFile(undeployedMarker, deploymentName);
                undeployedMarker.setLastModified(scanStartTime);
            }

            deployed.remove(deploymentName);
            noticeLogged.remove(deploymentName);
        }

        @Override
        protected void handleFailureResult(ModelNode result) {

            // Remove the in-progress marker
            removeInProgressMarker();

            if (!forcedUndeploy) {
                writeFailedMarker(new File(parent, deploymentName), result.get(FAILURE_DESCRIPTION).toString(), scanStartTime);
            }
        }
    }

    private class DeploymentMarker {
        private final long lastModified;
        private final boolean archive;
        private final File parentFolder;

        private DeploymentMarker(final long lastModified, boolean archive, File parentFolder) {
            this.lastModified = lastModified;
            this.archive = archive;
            this.parentFolder = parentFolder;
        }
    }

    private class ScanContext {
        /**
         * Existing deployments
         */
        private final Map<String, Boolean> registeredDeployments;
        /**
         * Existing persistent deployments
         */
        private final Set<String> persistentDeployments;
        /**
         * Tasks generated by the scan
         */
        private final List<ScannerTask> scannerTasks = new ArrayList<ScannerTask>();
        /**
         * Files to undeploy at the end of the scan
         */
        private final Map<String, DeploymentMarker> toRemove = new HashMap<String, DeploymentMarker>(deployed);
        /**
         * Marker files with no corresponding content
         */
        private final HashSet<String> ignoredMissingDeployments = new HashSet<String>();
        /**
         * Partially copied files detected by the scan
         */
        private Map<File, IncompleteDeploymentStatus> incompleteFiles = new HashMap<File, IncompleteDeploymentStatus>();
        /**
         * Non-auto-deployable files detected by the scan without an appropriate marker
         */
        private final HashSet<String> nonDeployable = new HashSet<String>();
        /**
         * WEB-INF and META-INF dirs not enclosed by a deployment
         */
        private final HashSet<String> illegalDir = new HashSet<String>();
        /**
         * Exploded deployment content removed without first removing the .deployed marker
         */
        private final HashSet<String> prematureExplodedDeletions = new HashSet<String>();
        /**
         * Deployments to attempt in next firstScan during boot
         */
        private final HashSet<String> firstScanDeployments = new HashSet<String>();
        /**
         * Auto-deployable files detected by the scan where ZipScanner threw a NonScannableZipException
         */
        private final Map<File, NonScannableStatus> nonscannable = new HashMap<File, NonScannableStatus>();
        /**
         * Timestamp when the scan started
         */
        private final long scanStartTime = System.currentTimeMillis();

        private ScanContext(final DeploymentOperations deploymentOperations) {
            registeredDeployments = deploymentOperations.getDeploymentsStatus();
            persistentDeployments = deploymentOperations.getUnrelatedDeployments(resourceAddress);
        }
    }

    private static class IncompleteDeploymentStatus {
        private final long timestamp;
        private final long size;
        private boolean warned;

        IncompleteDeploymentStatus(final File file, final long timestamp) {
            this.size = file.length();
            this.timestamp = timestamp;
        }
    }

    private static class NonScannableStatus {
        private final long timestamp;
        private final NonScannableZipException exception;

        public NonScannableStatus(NonScannableZipException exception, long timestamp) {
            this.exception = exception;
            this.timestamp = timestamp;
        }
    }

    /**
     * Possible overall scan behaviors following return from handling auto-deploy failures
     */
    private enum ScanStatus {
        ABORT, RETRY, PROCEED
    }
}
