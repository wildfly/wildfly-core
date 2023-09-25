/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.host.controller.mgmt;

import java.util.concurrent.Executor;

import org.jboss.as.repository.RemoteFileRequestAndHandler;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class DomainRemoteFileRequestAndHandler extends RemoteFileRequestAndHandler {

    public static final RemoteFileProtocolIdMapper MAPPER = new RemoteFileProtocolIdMapper() {
        public byte paramRootId() {
            return DomainControllerProtocol.PARAM_ROOT_ID;
        }

        public byte paramFilePath() {
            return DomainControllerProtocol.PARAM_FILE_PATH;
        }

        public byte paramNumFiles() {
            return DomainControllerProtocol.PARAM_NUM_FILES;
        }

        public byte fileStart() {
            return DomainControllerProtocol.FILE_START;
        }

        public byte paramFileSize() {
            return DomainControllerProtocol.PARAM_FILE_SIZE;
        }

        public byte fileEnd() {
            return DomainControllerProtocol.FILE_END;
        }
    };

    public static final DomainRemoteFileRequestAndHandler INSTANCE = new DomainRemoteFileRequestAndHandler(null);

    public DomainRemoteFileRequestAndHandler(Executor asyncExecutor) {
        super(MAPPER, asyncExecutor);
    }

}
