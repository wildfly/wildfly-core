/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.persistence;

import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.jboss.as.controller.PathAddress;
import org.jboss.dmr.ModelNode;

/**
 * The configuration persister for a model.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface ConfigurationPersister {

    /**
     * Callback for use by callers to {@link ConfigurationPersister#store(org.jboss.dmr.ModelNode, java.util.Set)}
     * to control whether the stored model should be flushed to permanent storage.
     */
    interface PersistenceResource {

        /**
         * Flush the stored model to permanent storage.
         */
        void commit();

        /**
         * Discard the changes.
         */
        void rollback();
    }

    /**
     * Gets whether a call to {@link #store(ModelNode, Set)} will return a {@link PersistenceResource} that will actually
     * persist to persistent storage. Some implementations may return {@code false} until {@link #successfulBoot()} is
     * invoked. If this returns {@code false} the caller can safely omit any call to {@link #store(ModelNode, Set)}.
     * <p>
     * The default implementation always returns {@code true}
     *
     * @return {@code true} if a call to {@link #store(ModelNode, Set)} will return an object that actually writes
     */
    default boolean isPersisting() {
        return true;
    }

    /**
     * Gets whether a call persist to persistent storage has been successfully completed.
     * <p>
     * The default implementation always returns {@code false}
     *
     * @return {@code true} if a call to {@link #store(ModelNode, Set)} will return an object that actually writes
     */
    default boolean hasStored() {
        return false;
    }

    /**
     * Persist the given configuration model if {@link #isPersisting()} would return {@code true}, otherwise
     * return a no-op {@link PersistenceResource}.
     *
     * @param model the model to persist
     * @param affectedAddresses the addresses of the resources that were changed
     *
     * @return callback to use to control whether the stored model should be flushed to permanent storage. Will not be
     *          {@code null}
     * @throws ConfigurationPersistenceException if a configuration persistence problem occurs
     */
    PersistenceResource store(ModelNode model, Set<PathAddress> affectedAddresses) throws ConfigurationPersistenceException;

    /**
     * Marshals the given configuration model to XML, writing to the given stream.
     *
     * @param model  the model to persist
     * @param output the stream
     * @throws ConfigurationPersistenceException if a configuration persistence problem occurs
     */
    void marshallAsXml(final ModelNode model, final OutputStream output) throws ConfigurationPersistenceException;

    /**
     * Load the configuration model, returning it as a list of updates to be
     * executed by the controller.
     * @return the configuration model as a list of updates to be executed by the controller.
     * @throws ConfigurationPersistenceException if a configuration persistence problem occurs
     */
    List<ModelNode> load() throws ConfigurationPersistenceException;

    /**
     * Called once the xml has been successfully parsed, translated into updates, executed in the target controller
     * and all services have started successfully.
     *
     * @see #isPersisting()
     * @throws ConfigurationPersistenceException if a configuration persistence problem occurs
     */
    void successfulBoot() throws ConfigurationPersistenceException;

    /**
     * Take a snapshot of the current configuration.
     *
     * @param message optionnal message
     * @return the file location of the snapshot
     * @throws ConfigurationPersistenceException if a problem happened when creating the snapshot
     */
    default String snapshot(String name, String message) throws ConfigurationPersistenceException {
         return "";
    }

    /**
     * Publish the current configuration
     * @param target the target destination of the publication.
     * @return the location of the published configuration
     * @throws ConfigurationPersistenceException if a problem happened when publishing
     */
    default String publish(String target) throws ConfigurationPersistenceException {
         return null;
    }

    /**
     * Gets the names of the snapshots in the snapshots directory
     *
     * @return the snapshot info. This will never return null
     */
    SnapshotInfo listSnapshots();

    /**
     * Deletes a snapshot using its name.
     *
     * @param name the name of the snapshot (as returned by {@link SnapshotInfo#names()} returned from {@link #listSnapshots()}. The whole name is not
     * needed, just enough to uniquely identify it.
     * @throws IllegalArgumentException if there is no snapshot with the given name, or if the name resolves to more than one snapshot.
     */
    void deleteSnapshot(String name);

    /**
     * Contains the info about the configuration snapshots
     */
    interface SnapshotInfo {
        /**
         * Gets the snapshots directory
         *
         * @return the snapshots directory
         */
        String getSnapshotDirectory();

        /**
         * Gets the names of the snapshot files in the snapshots directory
         *
         * @return the snapshot names. If there are none, an empty list is returned
         */
        List<String> names();
    }

    SnapshotInfo NULL_SNAPSHOT_INFO = new SnapshotInfo() {

        @Override
        public List<String> names() {
            return Collections.emptyList();
        }

        @Override
        public String getSnapshotDirectory() {
            return "";
        }
    };

}
