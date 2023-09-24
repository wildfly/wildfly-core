/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd;

import org.aesh.command.converter.Converter;
import org.aesh.command.converter.ConverterInvocation;
import org.aesh.command.impl.converter.FileConverter;
import org.aesh.command.validator.OptionValidatorException;

/**
 * A converter that builds a RelativeFile that contains both absolute and
 * relative paths.
 *
 * @author jdenise@redhat.com
 */
public class RelativeFilePathConverter implements Converter<RelativeFile, ConverterInvocation> {

    private FileConverter converter = new FileConverter();

    @Override
    public RelativeFile convert(ConverterInvocation converterInvocation) throws OptionValidatorException {
        return new RelativeFile(converterInvocation.getInput(), converter.convert(converterInvocation));
    }
}
