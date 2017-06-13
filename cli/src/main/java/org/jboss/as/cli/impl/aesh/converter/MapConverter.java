/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017, Red Hat, Inc., and individual contributors
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
package org.jboss.as.cli.impl.aesh.converter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.aesh.command.converter.Converter;
import org.aesh.command.validator.OptionValidatorException;
import org.wildfly.core.cli.command.aesh.CLIConverterInvocation;

/**
 *
 * @author jdenise@redhat.com
 */
public class MapConverter implements Converter<Map<String, String>, CLIConverterInvocation> {

    public static final MapConverter INSTANCE = new MapConverter();

    @Override
    public Map<String, String> convert(CLIConverterInvocation converterInvocation) throws OptionValidatorException {
        List<String> entries = Arrays.asList(converterInvocation.getInput().split(","));
        Map<String, String> map = new HashMap<>();
        for (String entry : entries) {
            String[] kv = entry.split("=");
            if (kv.length != 2) {
                throw new OptionValidatorException("Invalid format " + converterInvocation.getInput());
            }
            map.put(kv[0], kv[1]);
        }
        return map;
    }
}
