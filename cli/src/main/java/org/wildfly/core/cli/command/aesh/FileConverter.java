/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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
package org.wildfly.core.cli.command.aesh;

import java.io.File;
import org.aesh.command.converter.Converter;
import org.aesh.command.validator.OptionValidatorException;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.handlers.DefaultFilenameTabCompleter;
import org.jboss.as.cli.handlers.FilenameTabCompleter;
import org.jboss.as.cli.handlers.WindowsFilenameTabCompleter;

/**
 *
 * @author jdenise@redhat.com
 */
public class FileConverter implements Converter<File, CLIConverterInvocation> {

    @Override
    public File convert(CLIConverterInvocation converterInvocation) throws OptionValidatorException {
        String value = converterInvocation.getInput();
        CommandContext ctx = converterInvocation.getCommandContext();
        FilenameTabCompleter pathCompleter = Util.isWindows() ? new WindowsFilenameTabCompleter(ctx) : new DefaultFilenameTabCompleter(ctx);

        if (value != null) {
            if (value.length() >= 0 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
                value = value.substring(1, value.length() - 1);
            }
            value = pathCompleter.translatePath(value);
        }
        File f = new File(value);
        if (!f.exists()) {
            throw new OptionValidatorException("File " + f.getAbsolutePath() + " does not exist.");
        }
        return f;
    }

}
