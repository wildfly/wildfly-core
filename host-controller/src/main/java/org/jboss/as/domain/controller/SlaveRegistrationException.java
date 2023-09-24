/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.controller;

import org.jboss.as.controller.RunningMode;
import org.jboss.as.domain.controller.logging.DomainControllerLogger;

/**
 * Used to propagate error codes as part of the error message when an error occurs registering a slave host controller.
 * I do not want to modify the protocol at the moment to support error codes natively.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class SlaveRegistrationException extends Exception {

    private static final String SEPARATOR = "-$-";

    private final ErrorCode errorCode;
    private final String errorMessage;

    public SlaveRegistrationException(ErrorCode errorCode, String errorMessage) {
        super(errorMessage);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public static SlaveRegistrationException parse(String raw) {
        int index = raw.indexOf("-$-");
        if (index == -1) {
            return new SlaveRegistrationException(ErrorCode.UNKNOWN, raw);
        }

        ErrorCode code = ErrorCode.parseCode(Byte.valueOf(raw.substring(0, index)));
        String msg = raw.substring(index + SEPARATOR.length());
        return new SlaveRegistrationException(code, msg);
    }

    public static SlaveRegistrationException forUnknownError(String msg) {
        return new SlaveRegistrationException(ErrorCode.UNKNOWN, msg);
    }

    public static SlaveRegistrationException forHostAlreadyExists(String slaveName) {
        return new SlaveRegistrationException(ErrorCode.HOST_ALREADY_EXISTS, DomainControllerLogger.ROOT_LOGGER.slaveAlreadyRegistered(slaveName));
    }

    public static SlaveRegistrationException forMasterInAdminOnlyMode(RunningMode runningMode) {
        return new SlaveRegistrationException(ErrorCode.MASTER_IS_ADMIN_ONLY, DomainControllerLogger.ROOT_LOGGER.adminOnlyModeCannotAcceptSlaves(runningMode));
    }

    public static SlaveRegistrationException forHostIsNotMaster() {
        return new SlaveRegistrationException(ErrorCode.HOST_ALREADY_EXISTS, DomainControllerLogger.ROOT_LOGGER.slaveControllerCannotAcceptOtherSlaves());
    }

    public String marshal() {
        return errorCode.getCode() + SEPARATOR + errorMessage;
    }

    public String toString() {
        return errorCode.getCode() + SEPARATOR + errorMessage;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public enum ErrorCode {
        UNKNOWN(0x01),
        HOST_ALREADY_EXISTS(0x02),
        MASTER_IS_ADMIN_ONLY(0x03),
        HOST_IS_NOT_MASTER(0x04),
        INCOMPATIBLE_VERSION(0x05),
        ;

        private final byte code;

        ErrorCode(int code) {
            this.code = (byte) code;
        }

        public byte getCode() {
            return code;
        }

        public static ErrorCode parseCode(byte code) {
            if (code == UNKNOWN.getCode()) {
                return UNKNOWN;
            } else if (code == HOST_ALREADY_EXISTS.getCode()) {
                return HOST_ALREADY_EXISTS;
            } else if (code == MASTER_IS_ADMIN_ONLY.getCode()) {
                return MASTER_IS_ADMIN_ONLY;
            } else if (code == HOST_IS_NOT_MASTER.getCode()) {
                return HOST_IS_NOT_MASTER;
            } else if (code == INCOMPATIBLE_VERSION.getCode()) {
                return INCOMPATIBLE_VERSION;
            }
            return UNKNOWN;
        }
    }
}
