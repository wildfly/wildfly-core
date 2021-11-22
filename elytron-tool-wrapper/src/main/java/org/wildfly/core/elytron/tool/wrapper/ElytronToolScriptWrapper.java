/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2021, Red Hat, Inc., and individual contributors
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
