/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.http.server.logging;

import static org.jboss.logging.Logger.Level.ERROR;
import static org.jboss.logging.Logger.Level.INFO;
import static org.jboss.logging.Logger.Level.WARN;

import java.net.InetAddress;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;
import org.jboss.modules.ModuleNotFoundException;

/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@MessageLogger(projectCode = "WFLYDMHTTP", length = 4)

public interface HttpServerLogger extends BasicLogger {
    HttpServerLogger ROOT_LOGGER = Logger.getMessageLogger(HttpServerLogger.class, "org.jboss.as.domain.http.api.undertow");

    @LogMessage(level = ERROR)
    @Message(id = 1, value = "Unexpected error executing model request")
    void modelRequestError(@Cause Throwable cause);

    @LogMessage(level = ERROR)
    @Message(id = 2, value = "Unexpected error executing deployment upload request")
    void uploadError(@Cause Throwable cause);

    @LogMessage(level = WARN)
    @Message(id = 3, value = "Unable to load console module for slot %s, disabling console")
    void consoleModuleNotFound(String slot);

    @LogMessage(level = ERROR)
    @Message(id = 4, value = "Unable to load error context for slot %s, disabling error context.")
    void errorContextModuleNotFound(String slot);

    @Message(id = 5, value = "Invalid operation '%s'")
    IllegalArgumentException invalidOperation(@Cause Throwable cause, String value);

    /**
     * An error message indicating that the security realm is not ready to process requests and a URL that can be viewed for
     * additional information.
     *
     * @param url - the url clients should visit for further information.
     * @return the error message.
     */
    @Message(id = 6, value = "The security realm is not ready to process requests, see %s")
    String realmNotReadyMessage(final String url);

    @Message(id = 7, value = "No console module available with module name %s")
    ModuleNotFoundException consoleModuleNotFoundMsg(final String moduleName);

//    @Message(id = 8, value = "Failed to read %s")
//    RuntimeException failedReadingResource(@Cause Throwable cause, String resource);

//    @Message(id = 9, value = "Invalid resource")
//    String invalidResource();

    @Message(id = 10, value = "Invalid Credential Type '%s'")
    IllegalArgumentException invalidCredentialType(String value);

    @LogMessage(level = INFO)
    @Message(id = 11, value = "Management interface is using different addresses for HTTP (%s) and HTTPS (%s). Redirection of HTTPS requests from HTTP socket to HTTPS socket will not be supported.")
    void httpsRedirectNotSupported(InetAddress bindAddress, InetAddress secureBindAddress);

    @Message(id = 12, value = "A secure socket has been defined for the HTTP interface, however the referenced security realm is not supplying a SSLContext.")
    IllegalArgumentException sslRequestedNoSslContext();

    @Message(id = 13, value = "Invalid useStreamIndex value '%d'. The operation response had %d streams attached.")
    String invalidUseStreamAsResponseIndex(int index, int available);

    @Message(id = 14, value = "The ManagementHttpServer has already been built using this Builder.")
    IllegalStateException managementHttpServerAlreadyBuild();

    @Message(id = 15, value = "No SecurityRealm or SSLContext has been provided.")
    IllegalStateException noRealmOrSSLContext();

    @Message(id = 16, value = "Your Application Server is running. However you have not yet added any users to be able " +
            "to access the HTTP management interface. To add a new user execute the %s script within the bin folder of " +
            "your WildFly installation and enter the requested information. By default the realm name used by WildFly is" +
            " 'ManagementRealm' and this is already selected by default by the add-user tool.")
    String realmNotReadyForSecuredManagementHandler(String scriptFile);

}
