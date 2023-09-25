/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.process;

import java.io.IOException;
import java.util.Map;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface ProcessMessageHandler {

    void handleProcessAdded(ProcessControllerClient client, String processName);

    void handleProcessStarted(ProcessControllerClient client, String processName);

    void handleProcessStopped(ProcessControllerClient client, String processName, long uptimeMillis);

    void handleProcessRemoved(ProcessControllerClient client, String processName);

    void handleProcessInventory(ProcessControllerClient client, Map<String, ProcessInfo> inventory);

    void handleConnectionShutdown(ProcessControllerClient client);

    void handleConnectionFailure(ProcessControllerClient client, IOException cause);

    void handleConnectionFinished(ProcessControllerClient client);

    void handleOperationFailed(ProcessControllerClient client, OperationType operation, String processName);

    public enum OperationType {

        ADD(Protocol.ADD_PROCESS),
        INVENTORY(Protocol.REQUEST_PROCESS_INVENTORY),
        REMOVE(Protocol.REMOVE_PROCESS),
        RECONNECT(Protocol.RECONNECT_PROCESS),
        SEND_STDIN(Protocol.SEND_STDIN),
        START(Protocol.START_PROCESS),
        STOP(Protocol.STOP_PROCESS),
        ;

        final int code;

        private OperationType(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }

        static OperationType fromCode(int code) {
            for(OperationType type : values()) {
                if( type.getCode() == code) {
                    return type;
                }
            }
            return null;
        }
    }

}
