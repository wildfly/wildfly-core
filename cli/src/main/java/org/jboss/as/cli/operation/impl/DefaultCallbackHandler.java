/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.operation.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineFormat;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.as.cli.operation.ParsedOperationRequestHeader;
import org.jboss.as.cli.parsing.ParserUtil;
import org.jboss.as.cli.parsing.ParsingStateCallbackHandler;
import org.jboss.as.cli.parsing.StateParser;
import org.jboss.as.cli.parsing.operation.OperationFormat;
import org.jboss.as.cli.parsing.operation.header.RolloutPlanHeaderCallbackHandler;
import org.jboss.dmr.ModelNode;

/**
*
* @author Alexey Loubyansky
*/
public class DefaultCallbackHandler extends ValidatingCallbackHandler implements ParsedCommandLine {

    private static final int SEPARATOR_NONE = 0;
    private static final int SEPARATOR_NODE_TYPE_NAME = 1;
    private static final int SEPARATOR_NODE = 2;
    private static final int SEPARATOR_ADDRESS_OPERATION = 3;
    private static final int SEPARATOR_OPERATION_ARGUMENTS = 4;
    private static final int SEPARATOR_ARG_NAME_VALUE = 5;
    private static final int SEPARATOR_ARG = 6;
    private static final int SEPARATOR_ARG_LIST_END = 7;
    private static final int SEPARATOR_HEADERS_START = 8;
    private static final int SEPARATOR_HEADER = 9;
    private static final int SEPARATOR_NOT_OPERATOR = 10;

    private static final DefaultOperationRequestAddress EMPTY_ADDRESS = new DefaultOperationRequestAddress();

    private int separator = SEPARATOR_NONE;
    private int lastSeparatorIndex = -1;
    private int lastNotOperatorIndex = -1;
    private int lastChunkIndex = 0;

    private boolean operationComplete;
    private String operationName;
    private OperationRequestAddress address;
    private boolean addressChanged;
    private Map<String, String> props = Collections.emptyMap();
    private List<String> otherArgs = Collections.emptyList();
    private String outputTarget;
    private boolean hasOperator;

    private String lastPropName;
    private String lastPropValue;
    private String lastHeaderName;

    private CommandLineFormat format;

    private boolean validation;

    private String originalLine;
    private StateParser.SubstitutedLine substitutedLine;

    private LinkedHashMap<String,ParsedOperationRequestHeader> headers;
    private ParsedOperationRequestHeader lastHeader;

    public DefaultCallbackHandler() {
        this(true);
    }

    public DefaultCallbackHandler(boolean validate) {
        this.validation = validate;
    }

    public DefaultCallbackHandler(OperationRequestAddress prefix) {
        address = prefix;
    }

    @Deprecated
    public void parse(OperationRequestAddress initialAddress, String argsStr) throws CommandFormatException {
        parse(initialAddress, argsStr, null);
    }

    public void parse(OperationRequestAddress initialAddress, String argsStr, CommandContext ctx) throws CommandFormatException {
        parse(initialAddress, argsStr, ctx, false);
    }

    public void parse(OperationRequestAddress initialAddress, String argsStr, CommandContext ctx, boolean disableResolutionException) throws CommandFormatException {
        reset();
        if(initialAddress != null) {
            address = new DefaultOperationRequestAddress(initialAddress);
        }
        this.originalLine = argsStr;
        substitutedLine = ParserUtil.parseLine(argsStr, this, validation, ctx, disableResolutionException);
    }

    @Deprecated
    public void parse(OperationRequestAddress initialAddress, String argsStr, boolean validation) throws CommandFormatException {
        parse(initialAddress, argsStr, validation, null);
    }

    public void parse(OperationRequestAddress initialAddress, String argsStr, boolean validation, CommandContext ctx) throws CommandFormatException {
        final boolean defaultValidation = this.validation;
        this.validation = validation;
        try {
            parse(initialAddress, argsStr, ctx);
        } finally {
            this.validation = defaultValidation;
        }
    }

    @Deprecated
    public void parseOperation(OperationRequestAddress prefix, String argsStr) throws CommandFormatException {
        reset();
        this.setFormat(OperationFormat.INSTANCE);
        if(prefix != null) {
            address = new DefaultOperationRequestAddress(prefix);
        }
        this.setFormat(OperationFormat.INSTANCE);
        this.originalLine = argsStr;
        substitutedLine = ParserUtil.parseOperationRequestLine(argsStr, this);
    }

    public void parseOperation(OperationRequestAddress prefix, String argsStr, CommandContext ctx) throws CommandFormatException {
        reset();
        this.setFormat(OperationFormat.INSTANCE);
        if (prefix != null) {
            address = new DefaultOperationRequestAddress(prefix);
        }
        this.setFormat(OperationFormat.INSTANCE);
        this.originalLine = argsStr;
        substitutedLine = ParserUtil.parseOperationRequestLine(argsStr, this, ctx);
    }

    public void parseHeaders(String argsStr, CommandContext ctx) throws CommandFormatException {
        reset();
        this.originalLine = argsStr;
        substitutedLine = ParserUtil.parseHeadersLine(argsStr, this, ctx);
    }

    public void reset() {
        operationComplete = false;
        operationName = null;
        address = null;
        addressChanged = false;
        props = Collections.emptyMap();
        otherArgs = Collections.emptyList();
        outputTarget = null;
        lastPropName = null;
        lastPropValue = null;
        separator = SEPARATOR_NONE;
        lastSeparatorIndex = -1;
        lastChunkIndex = 0;
        format = null;
        originalLine = null;
        substitutedLine = null;
        headers = null;
        lastHeaderName = null;
        lastHeader = null;
        hasOperator = false;
    }

    @Override
    public String getOriginalLine() {
        return originalLine;
    }

    @Override
    public String getSubstitutedLine() {
        return substitutedLine.getSubstitued();
    }

    @Override
    public StateParser.SubstitutedLine getSubstitutions() {
        return substitutedLine;
    }

    public List<String> getOtherProperties() {
        return otherArgs;
    }

    @Override
    public boolean isRequestComplete() {
        return operationComplete;
    }

    @Override
    public boolean endsOnPropertySeparator() {
        return separator == SEPARATOR_ARG;
    }

    @Override
    public boolean endsOnPropertyValueSeparator() {
        return separator == SEPARATOR_ARG_NAME_VALUE;
    }

    @Override
    public boolean endsOnPropertyListStart() {
        return separator == SEPARATOR_OPERATION_ARGUMENTS;
    }

    @Override
    public boolean endsOnPropertyListEnd() {
        return separator == SEPARATOR_ARG_LIST_END;
    }

    @Override
    public boolean endsOnNotOperator() {
        return separator == SEPARATOR_NOT_OPERATOR;
    }

    @Override
    public boolean endsOnHeaderListStart() {
        return separator == SEPARATOR_HEADERS_START;
    }

    @Override
    public boolean endsOnAddressOperationNameSeparator() {
        return separator == SEPARATOR_ADDRESS_OPERATION;
    }

    @Override
    public boolean endsOnNodeSeparator() {
        return separator == SEPARATOR_NODE;
    }

    @Override
    public boolean endsOnNodeTypeNameSeparator() {
        return separator == SEPARATOR_NODE_TYPE_NAME;
    }

    @Override
    public boolean endsOnSeparator() {
        return separator != SEPARATOR_NONE;
    }

    @Override
    public boolean hasAddress() {
        return address != null;
    }

    @Override
    public OperationRequestAddress getAddress() {
        return address == null ? EMPTY_ADDRESS : address;
    }

    @Override
    public boolean hasOperationName() {
        return operationName != null;
    }

    @Override
    public String getOperationName() {
        return operationName;
    }

    @Override
    public boolean hasProperties() {
        return !props.isEmpty() || !otherArgs.isEmpty();
    }

    @Override
    public boolean hasProperty(String propertyName) {
        return props.containsKey(propertyName);
    }

    @Override
    public void validatedNodeType(int index, String nodeType) throws OperationFormatException {

        if(address == null) {
            address = new DefaultOperationRequestAddress();
        } else if (address.endsOnType()) {
            throw new OperationFormatException(
                    "Can't proceed with node type '"
                            + nodeType
                            + "' until the node name for the previous node type has been specified.");
        }

        address.toNodeType(nodeType);
        separator = SEPARATOR_NONE;
        lastChunkIndex = index;
    }

    @Override
    public void nodeTypeNameSeparator(int index) {
        separator = SEPARATOR_NODE_TYPE_NAME;
        this.lastSeparatorIndex = index;
    }

    @Override
    public void validatedNodeName(int index, String nodeName) throws OperationFormatException {

        if(address == null) {
            address = new DefaultOperationRequestAddress();
        }

        if(!address.endsOnType()) {
            throw new OperationFormatException("Node path format is wrong around '" + nodeName + "' (index=" + index + ").");
        }

        address.toNode(nodeName);
        separator = SEPARATOR_NONE;
        lastChunkIndex = index;
    }

    @Override
    public void nodeSeparator(int index) {
        separator = SEPARATOR_NODE;
        this.lastSeparatorIndex = index;
    }

    @Override
    public void addressOperationSeparator(int index) throws CommandFormatException {
        if(separator == SEPARATOR_NODE_TYPE_NAME) {
            throw new CommandFormatException("Node type is not complete at index " + index);
        }
        separator = SEPARATOR_ADDRESS_OPERATION;
        this.lastSeparatorIndex = index;
    }

    @Override
    public void operationName(int index, String operationName) throws OperationFormatException {
        if(validation) {
            super.operationName(index, operationName);
        } else {
            validatedOperationName(index, operationName);
        }
    }

    public void validatedOperationName(int index, String operationName) throws OperationFormatException {
        this.operationName = operationName;
        separator = SEPARATOR_NONE;
        lastChunkIndex = index;
    }

    @Override
    public void propertyListStart(int index) {
        separator = SEPARATOR_OPERATION_ARGUMENTS;
        this.lastSeparatorIndex = index;
    }

    @Override
    public void propertyName(int index, String propertyName) throws OperationFormatException {
        if(validation) {
            super.propertyName(index, propertyName);
        } else {
            validatedPropertyName(index, propertyName);
        }
    }

    @Override
    public boolean isLastPropertyNegated() {
        return lastPropName != null
                && lastNotOperatorIndex + 1 == lastChunkIndex;
    }

    @Override
    public void propertyNoValue(int index, String name) throws CommandFormatException {
        String value = Util.TRUE;
        if (name.startsWith(Util.NOT_OPERATOR)) {
            name = name.substring(1);
            value = Util.FALSE;
            notOperator(index);
            index += 1;
        }
        if (name.length() > 0) {
            if (validation) {
                property(name, value, index);
            } else { // Completion, can't set default value/
                propertyName(index, name);
            }
        }
    }

    @Override
    protected void validatedPropertyName(int index, String propertyName) throws OperationFormatException {
        putProperty(propertyName, null);
        lastPropName = propertyName;
        lastPropValue = null;
        separator = SEPARATOR_NONE;
        lastChunkIndex = index;
    }

    @Override
    public void propertyNameValueSeparator(int index) {
        separator = SEPARATOR_ARG_NAME_VALUE;
        this.lastSeparatorIndex = index;
    }

    @Override
    public void property(String name, String value, int nameValueSeparatorIndex)
            throws OperationFormatException {

/*        if (value.isEmpty()) {
            throw new OperationFormatException(
                    "The argument value is missing or the format is wrong for argument '"
                            + value + "'");
        }
*/
        if(validation) {
            super.property(name, value, nameValueSeparatorIndex);
        } else {
            validatedProperty(name, value, nameValueSeparatorIndex);
        }
    }

    @Override
    protected void validatedProperty(String name, String value, int nameValueSeparatorIndex) throws OperationFormatException {
        if(name == null) {
            addArgument(value);
        } else {
            putProperty(name, value);
        }
        lastPropName = name;
        lastPropValue = value;
        separator = SEPARATOR_NONE;
        if(nameValueSeparatorIndex >= 0) {
            this.lastSeparatorIndex = nameValueSeparatorIndex;
        }
        lastChunkIndex = nameValueSeparatorIndex;
    }

    @Override
    public void propertySeparator(int index) {
        separator = SEPARATOR_ARG;
        this.lastSeparatorIndex = index;
        this.lastPropName = null;
        this.lastPropValue = null;
    }

    @Override
    public void notOperator(int index) {
        separator = SEPARATOR_NOT_OPERATOR;
        this.lastNotOperatorIndex = index;
        this.lastPropName = null;
        this.lastPropValue = null;
    }

    @Override
    public void propertyListEnd(int index) {
        separator = SEPARATOR_ARG_LIST_END;
        this.lastSeparatorIndex = index;
        this.lastPropName = null;
        this.lastPropValue = null;
    }

    @Override
    public void headerListStart(int index) {
        separator = SEPARATOR_HEADERS_START;
        this.lastSeparatorIndex = index;
    }

    @Override
    public void headerListEnd(int index) {
        separator = SEPARATOR_NONE;
        //operationComplete = true;
        this.lastSeparatorIndex = index;
        //this.lastPropName = null;
        //this.lastPropValue = null;
        operationComplete = true;
    }

    public void headerSeparator(int index) {
        this.separator = SEPARATOR_HEADER;
        this.lastSeparatorIndex = index;
    }

    public void headerNameValueSeparator(int index) {
        this.separator = SEPARATOR_ARG_NAME_VALUE;
        this.lastSeparatorIndex = index;
    }

    @Override
    public ParsingStateCallbackHandler headerName(int index, String headerName) throws CommandFormatException {
        this.separator = SEPARATOR_NONE;
        lastChunkIndex = index;
        this.lastHeaderName = headerName;
        lastHeader = null;
        if(headerName.equals("rollout")) {
            return new RolloutPlanHeaderCallbackHandler(this);
        }
        return null;
    }

    @Override
    public void header(String name, String value, int valueIndex) throws CommandFormatException {
        if(headers == null) {
            headers = new LinkedHashMap<String,ParsedOperationRequestHeader>();
        }
        lastHeader = new SimpleParsedOperationRequestHeader(name, value);
        headers.put(name, lastHeader);
        separator = SEPARATOR_NONE;
        this.lastSeparatorIndex = valueIndex -1;
        this.lastChunkIndex = valueIndex;
        this.lastHeaderName = null;
    }

    public void header(ParsedOperationRequestHeader header) {
        if(headers == null) {
            headers = new LinkedHashMap<String,ParsedOperationRequestHeader>();
        }
        lastHeader = header;
        headers.put(header.getName(), header);
        separator = SEPARATOR_NONE;
        this.lastHeaderName = null;
    }

    @Override
    public boolean hasHeaders() {
        return headers != null || lastHeaderName != null;
    }

    @Override
    public boolean hasHeader(String name) {
        return headers != null && headers.containsKey(name);
    }

    @Override
    public String getLastHeaderName() {
        return lastHeaderName;
    }

    @Override
    public Collection<ParsedOperationRequestHeader> getHeaders() {
        return headers == null ? Collections.<ParsedOperationRequestHeader>emptyList() : headers.values();
    }

    @Override
    public ParsedOperationRequestHeader getLastHeader() {
        return lastHeader;
    }

    @Override
    public void rootNode(int index) throws OperationFormatException {
        if(addressChanged) {
            throw new OperationFormatException("Can't reset to root in the middle of the path @" + index);
        }
        if(address == null) {
            address = new DefaultOperationRequestAddress();
        } else {
            address.reset();
        }
        separator = SEPARATOR_NONE;
        lastChunkIndex = index;
        addressChanged = true;
    }

    @Override
    public void parentNode(int index) {
        if(address == null) {
            throw new IllegalStateException("The address hasn't been initialized yet.");
        }
        address.toParentNode();
        separator = SEPARATOR_NONE;
        lastChunkIndex = index;
        addressChanged = true;
    }

    @Override
    public void nodeType(int index) {
        if(address == null) {
            throw new IllegalStateException("The address hasn't been initialized yet.");
        }
        address.toNodeType();
        separator = SEPARATOR_NONE;
        lastChunkIndex = index;
        addressChanged = true;
    }

    @Override
    public void nodeName(int index, String nodeName) throws OperationFormatException {
        if(validation) {
            super.nodeName(index, nodeName);
        } else {
            this.validatedNodeName(index, nodeName);
        }
        addressChanged = true;
    }

    @Override
    public void nodeType(int index, String nodeType) throws OperationFormatException {
        if(validation) {
            super.nodeType(index, nodeType);
        } else {
            this.validatedNodeType(index, nodeType);
        }
        addressChanged = true;
    }

    @Override
    public void nodeTypeOrName(int index, String typeOrName) throws OperationFormatException {

        if(address == null) {
            address = new DefaultOperationRequestAddress();
        }

        if(address.endsOnType()) {
            nodeName(index, typeOrName);
        } else {
            nodeType(index, typeOrName);
        }
        separator = SEPARATOR_NONE;
        addressChanged = true;
    }

    @Override
    public Set<String> getPropertyNames() {
        return props.keySet();
    }

    @Override
    public String getPropertyValue(String name) {
        return props.get(name);
    }

    @Override
    public int getLastSeparatorIndex() {
        return lastSeparatorIndex;
    }

    @Override
    public int getLastChunkIndex() {
        return lastChunkIndex;
    }

    @Override
    public int getLastChunkOriginalIndex() {
        return substitutedLine.getOriginalOffset(lastChunkIndex);
    }

    @Override
    public int getLastSeparatorOriginalIndex() {
        return substitutedLine.getOriginalOffset(lastSeparatorIndex);
    }

    @Override
    public int getOriginalOffset(int offset) {
        return substitutedLine.getOriginalOffset(offset);
    }

    @Override
    public void outputTarget(int index, String outputTarget) {
        this.outputTarget = outputTarget;
        lastChunkIndex = index;
        operator(index);
    }

    @Override
    public String getOutputTarget() {
        return outputTarget;
    }

    @Override
    public void operator(int index) {
        this.hasOperator = true;
    }

    @Override
    public boolean hasOperator() {
        return hasOperator;
    }

    @Override
    public String getLastParsedPropertyName() {
        return lastPropName;
    }

    @Override
    public String getLastParsedPropertyValue() {
        return lastPropValue;
    }

    public ModelNode toOperationRequest(CommandContext ctx) throws CommandFormatException {
        return Util.toOperationRequest(ctx, this);
    }

    @Override
    public void setFormat(CommandLineFormat format) {
        this.format = format;
    }

    @Override
    public CommandLineFormat getFormat() {
        return format;
    }

    @Override
    public boolean endsOnHeaderSeparator() {
        return separator == SEPARATOR_HEADER;
    }

    private void putProperty(String key, String name) {
        if(props.isEmpty()) {
            props = new HashMap<String,String>();
        }
        props.put(key, name);
    }

    private void addArgument(String value) {
        if(otherArgs.isEmpty()) {
            otherArgs = new ArrayList<String>();
        }
        otherArgs.add(value);
    }
}
