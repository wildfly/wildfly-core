/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.batch;

import java.util.List;
import org.jboss.as.cli.Attachments;

import org.jboss.dmr.ModelNode;

/**
 *
 * @author Alexey Loubyansky
 */
public interface Batch {

    /**
     * Adds a command or an operation to the batch.
     * @param cmd  command or operation to add to the batch
     */
    void add(BatchedCommand cmd);

    /**
     * Returns all the commands and operations in the batch as a list.
     * @return  list of commands and operations in the batch
     */
    List<BatchedCommand> getCommands();

    /**
     * Removes all the commands and the operations from the batch.
     */
    void clear();

    /**
     * Removes command or operation corresponding to its index in the list.
     * The indexes start with 0.
     * @param index  the index of the command or operation to be removed from the batch
     */
    void remove(int index);

    /**
     * Move the command or operation corresponding to the currentIndex to the newIndex position,
     * shifting the commands/operations in between the indexes.
     * The indexes start with 0.
     * @param currentIndex  the index of the command or operation to move the new position
     * @param newIndex  the new position for the command/operation
     */
    void move(int currentIndex, int newIndex);

    /**
     * Replaces the command or operation at the specified index with the new one.
     * The indexes start with 0.
     * @param index  the position for the new command or operation.
     * @param cmd  the new command or operation
     */
    void set(int index, BatchedCommand cmd);

    /**
     * Returns the number of the commands and operations in the batch.
     * @return  the number of the commands and operations in the batch
     */
    int size();

    /**
     * Generates a composite operation request from all the commands and operations
     * in the batch.
     * @return  operation request that includes all the commands and operations in the batch
     */
    ModelNode toRequest();

    default Attachments getAttachments() {
        return Attachments.IMMUTABLE_ATTACHMENTS;
    }
}
