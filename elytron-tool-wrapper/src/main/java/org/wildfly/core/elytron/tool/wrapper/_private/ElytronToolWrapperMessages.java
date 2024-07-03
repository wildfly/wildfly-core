/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.elytron.tool.wrapper._private;

import java.lang.invoke.MethodHandles;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

/**
 * Messages for the Elytron Tool Wrapper.
 *
 * <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 */
@MessageLogger(projectCode = "WFLYELYTOOL", length = 5)
public interface ElytronToolWrapperMessages extends BasicLogger {

    /**
     * A root logger with the category of the package name.
     */
    ElytronToolWrapperMessages ROOT_LOGGER = Logger.getMessageLogger(MethodHandles.lookup(), ElytronToolWrapperMessages.class, "org.wildfly.core.elytron.tool.wrapper");

    @Message(id = Message.NONE, value = "To make use of the WildFly Elytron Tool, the elytron-tool script should be used instead of wildfly-elytron-tool.jar.\n\n" +
            "Run elytron-tool.sh, elytron-tool.bat, or elytron-tool.ps1 with the same arguments that were previously passed when using wildfly-elytron-tool.jar.\n\n" +
            "For example, run:\n%s")
    String redirectToScript(String command);

    @Message(id = Message.NONE, value = "To make use of the WildFly Elytron Tool, the elytron-tool script should be used instead of wildfly-elytron-tool.jar.\n" +
            "Run elytron-tool.sh, elytron-tool.bat, or elytron-tool.ps1 with the same arguments that were previously passed when using wildfly-elytron-tool.jar.")
    String redirectToScriptSimple();

}


