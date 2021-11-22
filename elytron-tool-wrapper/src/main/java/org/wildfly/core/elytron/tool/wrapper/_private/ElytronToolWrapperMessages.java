/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2021 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.core.elytron.tool.wrapper._private;

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
    ElytronToolWrapperMessages ROOT_LOGGER = Logger.getMessageLogger(ElytronToolWrapperMessages.class, "org.wildfly.core.elytron.tool.wrapper");

    @Message(id = Message.NONE, value = "To make use of the WildFly Elytron Tool, the elytron-tool script should be used instead of wildfly-elytron-tool.jar.\n\n" +
            "Run elytron-tool.sh, elytron-tool.bat, or elytron-tool.ps1 with the same arguments that were previously passed when using wildfly-elytron-tool.jar.\n\n" +
            "For example, run:\n%s")
    String redirectToScript(String command);

    @Message(id = Message.NONE, value = "To make use of the WildFly Elytron Tool, the elytron-tool script should be used instead of wildfly-elytron-tool.jar.\n" +
            "Run elytron-tool.sh, elytron-tool.bat, or elytron-tool.ps1 with the same arguments that were previously passed when using wildfly-elytron-tool.jar.")
    String redirectToScriptSimple();

}


