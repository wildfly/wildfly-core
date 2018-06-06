/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.remoting.logging;

import static org.jboss.logging.Logger.Level.INFO;

import java.io.IOException;
import java.net.BindException;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;
import org.jboss.msc.service.StartException;

/**
 * Date: 05.11.2011
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@MessageLogger(projectCode = "WFLYRMT", length = 4)
public interface RemotingLogger extends BasicLogger {

    /**
     * A logger with a category of the package name.
     */
    RemotingLogger ROOT_LOGGER = Logger.getMessageLogger(RemotingLogger.class, "org.jboss.as.remoting");

    @LogMessage(level = INFO)
    @Message(id = 1, value = "Listening on %s")
    void listeningOnSocket(String address);

    @Message(id = 2, value = "Could not start channel listener")
    StartException couldNotStartChanelListener(@Cause Exception e);

    // @Message(id = 3, value = "Shutting down")
    // IllegalStateException channelShuttingDown();

    @Message(id = 4, value = "%s")
    StartException couldNotBindToSocket(String message, @Cause BindException e);

    @Message(id = 5, value = "Failed to start service")
    StartException couldNotStart(@Cause Exception e);

    @Message(id = 6, value = "Endpoint is null")
    IllegalStateException endpointEmpty();

    // id = 7; redundant parameter null check message
    // id = 8; redundant parameter null check message
    // id = 9; redundant parameter null check message
    // id = 10; redundant parameter null check message

//    @Message(id = 11, value = "A security realm has been specified but no supported mechanism identified")
//    IllegalStateException noSupportingMechanismsForRealm();

//    @Message(id = 12, value = "ANONYMOUS mechanism so not expecting a callback")
//    UnsupportedCallbackException anonymousMechanismNotExpected(@Param Callback current);

    //@Message(id = 13, value = "Unable to create tmp dir for auth tokens as file already exists.")
    //StartException unableToCreateTempDirForAuthTokensFileExists();

    //@Message(id = 14, value = "Unable to create auth dir %s.")
    //StartException unableToCreateAuthDir(String dir);

//    @Message(id = 15, value = "Could not connect")
//    RuntimeException couldNotConnect(@Cause URISyntaxException e);

    @Message(id = 16, value = "Invalid QOP value: %s")
    IllegalStateException invalidQOPV(String qop);

    @Message(id = 17, value = "Invalid Strength value: %s")
    IllegalStateException invalidStrength(String strengthValue);

    @Message(id = 18, value = "Cannot create a valid URI from %s -- %s")
    OperationFailedException couldNotCreateURI(String uri, String msg);

//    @Message(id = 19, value = "Unsupported Callback")
//    UnsupportedCallbackException unsupportedCallback(@Param Callback current);

    @Message(id = 20, value = "Invalid Strength '%s' string given")
    IllegalArgumentException illegalStrength(String strength);

    @Message(id = 21, value = "HTTP Upgrade request missing Sec-JbossRemoting-Key header")
    IOException upgradeRequestMissingKey();

    @Message(id = 22, value = "Worker configuration is no longer used, please use endpoint worker configuration")
    OperationFailedException workerConfigurationIgnored();

    @Message(id = 23, value = "Only one of '%s' configuration or '%s' configuration is allowed")
    String workerThreadsEndpointConfigurationChoiceRequired(String workerThreads, String endpoint);

    @LogMessage(level = INFO)
    @Message(id = 24, value = "The remoting subsystem is present but no io subsystem was found. An io subsystem " +
            "was not required when remoting schema '%s' was current but now is, so a default subsystem is being added.")
    void addingIOSubsystem(String legacyNS);

    @Message(id = 25, value = "Can't remove %s as JMX uses it as a remoting endpoint")
    OperationFailedException couldNotRemoveResource(PathAddress address);

    @Message(id = 26, value = "Change of worker to '%s' in remoting might require the same change in resources depending on remoting.")
    String warningOnWorkerChange(String worker);

    @Message(id = 27, value = "Failed to obtain SSLContext")
    StartException failedToObtainSSLContext(@Cause Exception cause);

    @Message(id = 28, value = "Invalid option '%s'.")
    OperationFailedException invalidOption(String message);
}
