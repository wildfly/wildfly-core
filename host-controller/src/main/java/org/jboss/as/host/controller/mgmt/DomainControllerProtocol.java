/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.host.controller.mgmt;

import org.jboss.as.controller.client.impl.ModelControllerProtocol;


/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @version $Revision: 1.1 $
 */
public interface DomainControllerProtocol extends ModelControllerProtocol {
    byte REGISTER_HOST_CONTROLLER_REQUEST = 0x51;
    byte UNREGISTER_HOST_CONTROLLER_REQUEST = 0x53;
    byte GET_FILE_REQUEST = 0x55;
    byte SERVER_INSTABILITY_REQUEST = 0x56;
    byte FETCH_DOMAIN_CONFIGURATION_REQUEST = 0x57;
    byte COMPLETE_HOST_CONTROLLER_REGISTRATION = 0x58;
    byte REQUEST_SUBSYSTEM_VERSIONS = 0x59;

    byte PARAM_HOST_ID = 0x20;
    byte PARAM_OK = 0x21;
    byte PARAM_ERROR = 0x22;
    byte PARAM_ROOT_ID = 0x24;
    byte PARAM_FILE_PATH = 0x25;
    byte PARAM_ROOT_ID_FILE = 0x26;
    byte PARAM_ROOT_ID_CONFIGURATION = 0x27;
    byte PARAM_ROOT_ID_DEPLOYMENT = 0x28;
    byte PARAM_NUM_FILES = 0x29;
    byte FILE_START = 0x30;
    byte PARAM_FILE_SIZE = 0x31;
    byte FILE_END = 0x32;
    byte PARAM_SERVER_ID = 0x33;

}
