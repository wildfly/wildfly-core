/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.scanner.api;

import org.jboss.msc.service.ServiceName;

/**
 * The deployment scanner.
 *
 * @author Emanuel Muckenhuber
 */
public interface DeploymentScanner {

    ServiceName BASE_SERVICE_NAME = ServiceName.JBOSS.append("server", "deployment", "scanner");

    /**
     * Check whether the scanner is enabled.
     *
     * @return true if enabled, false otherwise
     */
    boolean isEnabled();

    /**
     * Get the current scan interval
     *
     * @return the scan interval in ms
     */
    long getScanInterval();

    /**
     * Set the scan interval.
     *
     * @param scanInterval the scan interval in ms
     */
    void setScanInterval(long scanInterval);

    /**
     * Start the scanner, if not already started, using a default {@link DeploymentOperations}.
     *
     * @see #startScanner(DeploymentOperations)
     */
    void startScanner();

    /**
     * Start the scanner, if not already started.
     */
    void startScanner(final DeploymentOperations deploymentOperations);

    /**
     * Stop the scanner, if not already stopped.
     */
    void stopScanner();

    /**
     * Gets whether the scanner will attempt to deploy zipped content based solely
     * on detecting a change in the content; i.e. without requiring a
     * marker file to trigger the deployment.
     *
     * @return true if auto-deployment of zipped content is enabled
     */
    boolean isAutoDeployZippedContent();

    /**
     * Sets whether the scanner will attempt to deploy zipped content based solely
     * on detecting a change in the content; i.e. without requiring a
     * marker file to trigger the deployment.
     *
     * param autoDeployZip true if auto-deployment of zipped content is enabled
     */
    void setAutoDeployZippedContent(boolean autoDeployZip);

    /**
     * Gets whether the scanner will attempt to deploy exploded content based solely
     * on detecting a change in the content; i.e. without requiring a
     * marker file to trigger the deployment.
     *
     * @return true if auto-deployment of exploded content is enabled
     */
    boolean isAutoDeployExplodedContent() ;

    /**
     * Sets whether the scanner will attempt to deploy exploded content based solely
     * on detecting a change in the content; i.e. without requiring a
     * marker file to trigger the deployment.
     *
     * param autoDeployZip true if auto-deployment of exploded content is enabled
     */
    void setAutoDeployExplodedContent(boolean autoDeployExploded);

    /**
     * Gets whether the scanner will attempt to deploy XML content based solely
     * on detecting a change in the content; i.e. without requiring a
     * marker file to trigger the deployment.
     *
     * @return true if auto-deployment of XML content is enabled
     */
    boolean isAutoDeployXMLContent();

    /**
     * Sets whether the scanner will attempt to deploy XML content based solely
     * on detecting a change in the content; i.e. without requiring a
     * marker file to trigger the deployment.
     *
     * param autoDeployXML true if auto-deployment of XML content is enabled
     */
    void setAutoDeployXMLContent(boolean autoDeployXML);

    /**
     * Set the timeout used for deployments.
     *
     * @param timeout The deployment timeout
     */
    void setDeploymentTimeout(long timeout);

    /**
     * Sets whether a runtime failure of a deployment causes a rollback of the deployment as well as all other (maybe
     * unrelated) deployments as part of the scan operation.
     *
     * @param rollback true if runtime failures should trigger a rollback
     */
    void setRuntimeFailureCausesRollback(boolean rollback);

}
