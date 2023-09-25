/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.elytron.tool.wrapper;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Arrays;

import org.wildfly.core.elytron.tool.wrapper._private.ElytronToolWrapperMessages;

/**
 * Wrapper that will let users know to use the Elytron Tool scripts instead
 * of using the wildfly-elytron-tool JAR directly.
 *
 * @author <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 */
public class ElytronToolScriptWrapper {

    private static final String ELYTRON_TOOL_SCRIPT = "elytron-tool.[sh|bat|ps1]";

    public static void main(String[] args) {
        try {
            File jarPath = new File(ElytronToolScriptWrapper.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
            File parentDir = jarPath.getParentFile();
            if (parentDir != null) {
                String parentDirPath = parentDir.getAbsolutePath();
                StringBuilder elytronToolScriptCommand = new StringBuilder(parentDirPath + File.separator + ELYTRON_TOOL_SCRIPT + " ");
                Arrays.stream(args).forEach(arg -> elytronToolScriptCommand.append(arg + " "));
                System.out.println(ElytronToolWrapperMessages.ROOT_LOGGER.redirectToScript(elytronToolScriptCommand.toString()));
                return;
            }
        } catch (URISyntaxException e) {
            // ignored, simple message will be displayed instead
        }
        System.out.println(ElytronToolWrapperMessages.ROOT_LOGGER.redirectToScriptSimple());
    }
}
