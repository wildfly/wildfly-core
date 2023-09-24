/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.mgmt.domain;

/**
 * @author John Bailey
 */
public interface DomainServerProtocol {

    byte REGISTER_REQUEST = 0x00;
    byte SERVER_STARTED_REQUEST = 0x02;
    byte SERVER_RECONNECT_REQUEST = 0x03;
    byte SERVER_INSTABILITY_REQUEST = 0x04;


    byte PARAM_SERVER_NAME = 0x01;
    byte PARAM_OK = 0x21;
    byte PARAM_ERROR = 0x22;
    byte PARAM_RESTART_REQUIRED = 0x22;
    byte GET_FILE_REQUEST = 0x24;
    byte PARAM_FILE_PATH = 0x25;
    byte PARAM_ROOT_ID_FILE = 0x26;
    byte PARAM_ROOT_ID_CONFIGURATION = 0x27;
    byte PARAM_ROOT_ID_DEPLOYMENT = 0x28;
    byte PARAM_NUM_FILES = 0x29;
    byte FILE_START = 0x30;
    byte PARAM_FILE_SIZE = 0x31;
    byte FILE_END = 0x32;
    byte PARAM_ROOT_ID = 0x33;
}
