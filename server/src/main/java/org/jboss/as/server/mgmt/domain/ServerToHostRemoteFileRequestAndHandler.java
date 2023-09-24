/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.mgmt.domain;

import org.jboss.as.repository.RemoteFileRequestAndHandler;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ServerToHostRemoteFileRequestAndHandler extends RemoteFileRequestAndHandler {

    public static final RemoteFileProtocolIdMapper MAPPER = new RemoteFileProtocolIdMapper() {
        public byte paramRootId() {
            return DomainServerProtocol.PARAM_ROOT_ID;
        }

        public byte paramFilePath() {
            return DomainServerProtocol.PARAM_FILE_PATH;
        }

        public byte paramNumFiles() {
            return DomainServerProtocol.PARAM_NUM_FILES;
        }

        public byte fileStart() {
            return DomainServerProtocol.FILE_START;
        }

        public byte paramFileSize() {
            return DomainServerProtocol.PARAM_FILE_SIZE;
        }

        public byte fileEnd() {
            return DomainServerProtocol.FILE_END;
        }
    };

    public static final ServerToHostRemoteFileRequestAndHandler INSTANCE = new ServerToHostRemoteFileRequestAndHandler(MAPPER);

    private ServerToHostRemoteFileRequestAndHandler(RemoteFileProtocolIdMapper mapper) {
        super(mapper);
    }

}
