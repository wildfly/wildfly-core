/*
Copyright 2017 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
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
