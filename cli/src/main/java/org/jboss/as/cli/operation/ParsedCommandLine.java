/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.operation;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.jboss.as.cli.CommandLineFormat;
import org.jboss.as.cli.parsing.StateParser.SubstitutedLine;


/**
*
* @author Alexey Loubyansky
*/
public interface ParsedCommandLine {

    String getOriginalLine();

    /**
     * The original line with all the necessary substitution
     * (of commands, variables, system properties, etc) performed.
     * @return  the original line with all the necessary substitution
     * (of commands, variables, system properties, etc) performed
     */
    String getSubstitutedLine();

    SubstitutedLine getSubstitutions();

    boolean isRequestComplete();

    boolean endsOnPropertySeparator();

    boolean endsOnPropertyValueSeparator();

    boolean endsOnPropertyListStart();

    boolean endsOnPropertyListEnd();

    boolean endsOnNotOperator();

    boolean isLastPropertyNegated();

    boolean endsOnAddressOperationNameSeparator();

    boolean endsOnNodeSeparator();

    boolean endsOnNodeTypeNameSeparator();

    boolean endsOnSeparator();

    boolean hasAddress();

    OperationRequestAddress getAddress();

    boolean hasOperationName();

    String getOperationName();

    boolean hasProperties();

    boolean hasProperty(String propertyName);

    Set<String> getPropertyNames();

    String getPropertyValue(String name);

    List<String> getOtherProperties();

    boolean endsOnHeaderListStart();

    boolean endsOnHeaderSeparator();

    int getLastSeparatorIndex();

    int getLastChunkIndex();

    int getLastSeparatorOriginalIndex();

    int getLastChunkOriginalIndex();

    int getOriginalOffset(int offset);

    String getLastParsedPropertyName();

    String getLastParsedPropertyValue();

    String getOutputTarget();

    boolean hasHeaders();

    boolean hasHeader(String header);

    Collection<ParsedOperationRequestHeader> getHeaders();

    String getLastHeaderName();

    ParsedOperationRequestHeader getLastHeader();

    CommandLineFormat getFormat();

    boolean hasOperator();
}
