/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.impl;

import static org.wildfly.common.Assert.checkNotNullParam;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.cli.parsing.CharacterHandler;
import org.jboss.as.cli.parsing.DefaultParsingState;
import org.jboss.as.cli.parsing.GlobalCharacterHandlers;
import org.jboss.as.cli.parsing.ParsingContext;
import org.jboss.as.cli.parsing.ParsingStateCallbackHandler;
import org.jboss.as.cli.parsing.StateParser;
import org.jboss.as.cli.parsing.WordCharacterHandler;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.logging.Logger;
import org.jboss.logging.Logger.Level;

/**
 *
 * @author Alexey Loubyansky
 */
public class AttributeNamePathCompleter implements CommandLineCompleter {

    private static final Logger LOG = Logger.getLogger(AttributeNamePathCompleter.class);

    /**
     * A filter to accept or not completion candidates.
     */
    public interface AttributeFilter {
        boolean accept(ModelNode descr);
    }

    /**
     * Accep all candidates.
     */
    private static final AttributeFilter DEFAULT_FILTER = (ModelNode descr) -> {
        return true;
    };
    /**
     * A list can be: an attribute of type LIST or inside an attribute of type
     * OBJECT (complex or Map of list)
     */
    public static final AttributeFilter LIST_FILTER = (descr) -> {
        ModelNode mn = descr.has(Util.TYPE) ? descr.get(Util.TYPE) : null;
        if (mn == null) {
            // We don't know, invalid case, keep the attribute.
            return true;
        }
        ModelType type;
        try {
            type = mn.asType();
        } catch (Exception ex) {
            // Invalid. TYPE is a complexType, keep the attribute.
            return true;
        }
        // LIST
        if (type == ModelType.LIST) {
            return true;
        }
        // Can be a path to a LIST inside a Map of LIST
        // or a complexType.
        if (type == ModelType.OBJECT) {
            if (descr.has(Util.VALUE_TYPE)) {
                ModelNode vt = descr.get(Util.VALUE_TYPE);
                try {
                    ModelType mt = vt.asType();
                    // A map of LIST
                    if (mt.equals(ModelType.LIST)) {
                        return true;
                    }
                } catch (Exception ex) {
                    // This is a complex.
                    // Would be too much to analyze complete data
                    // structure. Keep it simple, we could
                    // have a list somewhere.
                    return true;
                }
            } else {
                // An OBJECT without value type, can't do
                // a lot with it. Anyway, keep it.
                return true;
            }
        }
        return false;
    };

    /**
     * A map can be: an attribute of type OBJECT or inside an attribute of type
     * LIST.
     */
    public static final AttributeFilter MAP_FILTER = (descr) -> {
        ModelNode mn = descr.has(Util.TYPE) ? descr.get(Util.TYPE) : null;
        if (mn == null) {
            // We don't know, invalid case, keep the attribute.
            return true;
        }
        ModelType type;
        try {
            type = mn.asType();
        } catch (Exception ex) {
            // Invalid. TYPE is a complexType, keep the attribute.
            return true;
        }
        // A map or inside a complex type
        if (type == ModelType.OBJECT) {
            return true;
        }
        // A List of complexType or LIST?
        if (type == ModelType.LIST) {
            if (descr.has(Util.VALUE_TYPE)) {
                ModelNode vt = descr.get(Util.VALUE_TYPE);
                try {
                    ModelType mt = vt.asType();
                    // A map of LIST
                    if (mt.equals(ModelType.LIST)) {
                        return true;
                    }
                } catch (Exception ex) {
                    // This is a complex.
                    // Would be too much to analyze complete data
                    // structure. Keep it simple, we could
                    // have a map somewhere.
                    return true;
                }
            } else {
                // A LIST without value type, can't do
                // a lot with it. Anyway, keep it.
                return true;
            }
        }
        return false;
    };

    public static final AttributeNamePathCompleter INSTANCE = new AttributeNamePathCompleter();

    private final OperationRequestAddress address;
    private final ModelNode attrDescr;
    private final boolean writeOnly;

    private final AttributeFilter filter;
    private AttributeNamePathCompleter() {
        this.address = null;
        this.attrDescr = null;
        writeOnly = false;
        filter = DEFAULT_FILTER;
    }

    public AttributeNamePathCompleter(OperationRequestAddress address, AttributeFilter filter) {
        this(address, false, filter);
    }

    public AttributeNamePathCompleter(OperationRequestAddress address) {
        this(address, null);
    }

    public AttributeNamePathCompleter(OperationRequestAddress address, boolean writeOnly) {
        this(address, writeOnly, null);
    }

    public AttributeNamePathCompleter(OperationRequestAddress address, boolean writeOnly, AttributeFilter filter) {
        this.address = checkNotNullParam("address", address);
        this.attrDescr = null;
        this.writeOnly = writeOnly;
        this.filter = filter == null ? DEFAULT_FILTER : filter;
    }

    public AttributeNamePathCompleter(ModelNode typeDescr) {
        this(typeDescr, null);
    }

    public AttributeNamePathCompleter(ModelNode typeDescr, AttributeFilter filter) {
        this.attrDescr = checkNotNullParam("typeDescr", typeDescr);
        this.address = null;
        this.writeOnly = false;
        this.filter = filter == null ? DEFAULT_FILTER : filter;
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.CommandLineCompleter#complete(org.jboss.as.cli.CommandContext, java.lang.String, int, java.util.List)
     */
    @Override
    public int complete(CommandContext ctx, String buffer, int cursor, List<String> candidates) {

        final ModelNode descr = attrDescr == null ? getAttributeDescription(ctx) : attrDescr;
        if(descr == null) {
            return -1;
        }
        return complete(buffer, candidates, descr, writeOnly);
    }

    public int complete(String buffer, List<String> candidates, final ModelNode descr) {
        return complete(buffer, candidates, descr, writeOnly);
    }

    public int complete(String buffer, List<String> candidates, final ModelNode descr, boolean writeOnly) {

        final AttributeNamePathCallbackHandler handler;
        try {
            handler = parse(buffer);
        } catch (CommandFormatException e) {
            if (LOG.isEnabled(Level.WARN)) {
                LOG.log(Level.WARN, e.getLocalizedMessage(), e);
            }
            return -1;
        }
        final Collection<String> foundCandidates = handler.getCandidates(descr, writeOnly);
        if(foundCandidates.isEmpty()) {
            return -1;
        }
        candidates.addAll(foundCandidates);
        return handler.getCandidateIndex();
    }

    protected AttributeNamePathCallbackHandler parse(String line) throws CommandFormatException {
        final AttributeNamePathCallbackHandler namePathHandler = new AttributeNamePathCallbackHandler(false);
        StateParser.parse(line, namePathHandler, InitialValueState.INSTANCE);
        return namePathHandler;
    }

    private class AttributeNamePathCallbackHandler implements ParsingStateCallbackHandler {

        private static final String offsetStep = "  ";

        private final boolean logging;
        private int offset;

        private String lastEnteredState;
        private char lastStateChar;
        private int candidateIndex;

        private List<String> path = Collections.emptyList();
        private StringBuilder buf = new StringBuilder();

        AttributeNamePathCallbackHandler(boolean logging) {
            this.logging = logging;
        }

        public Collection<String> getCandidates(ModelNode attrsDescr, boolean writeOnly) {

            if(attrsDescr == null || !attrsDescr.isDefined()) {
                return Collections.emptyList();
            }

            ModelNode typeDescr = attrsDescr;
            for(String name : path) {
                if(!typeDescr.has(name)) {
                    return Collections.emptyList();
                }
                final ModelNode descr = typeDescr.get(name);
                if(writeOnly && !isWritable(descr)) {
                    return Collections.emptyList();
                }
                if(!descr.hasDefined(Util.VALUE_TYPE)) {
                    return Collections.emptyList();
                }
                typeDescr = descr.get(Util.VALUE_TYPE);
                if(typeDescr.getType() != ModelType.OBJECT &&
                        typeDescr.getType() != ModelType.PROPERTY) {
                    return Collections.emptyList();
                }
            }

            Collection<String> attrNames = typeDescr.keys();
            final List<String> candidates = new ArrayList<String>(attrNames.size());

            if(DotState.ID.equals(lastEnteredState)) {
                if(writeOnly) {
                    for(String name : attrNames) {
                        if (isWritable(typeDescr.get(name)) && filter.accept(typeDescr.get(name))) {
                            candidates.add(name);
                        }
                    }
                } else {
                    for (String name : attrNames) {
                        if (filter.accept(typeDescr.get(name))) {
                            candidates.add(name);
                        }
                    }
                }
                Collections.sort(candidates);
                return candidates;
            }

            if(OpenBracketState.ID.equals(lastEnteredState) || ListIndexState.ID.equals(lastEnteredState)) {
                return Collections.emptyList();
            }
            if(CloseBracketState.ID.equals(lastEnteredState)) {
                return Arrays.asList(".", "=");
            }

            final String chunk = buf.length() == 0 ? null : buf.toString();
            ModelNode chunkDescr = null;
            for (String candidate : attrNames) {
                if (chunk == null || candidate.startsWith(chunk)) {
                    if (writeOnly && !isWritable(typeDescr.get(candidate))) {
                        continue;
                    }
                    if (chunk != null && chunk.length() == candidate.length()) {
                        chunkDescr = typeDescr.get(candidate);
                        continue;
                    }
                    if (filter.accept(typeDescr.get(candidate))) {
                        candidates.add(candidate);
                    }
                }
            }
            if (chunkDescr != null) {
                if (chunkDescr.hasDefined(Util.TYPE)) {
                    final ModelType modelType = chunkDescr.get(Util.TYPE).asType();
                    if (modelType.equals(ModelType.OBJECT)) {
                        if (candidates.isEmpty()) {
                            candidateIndex += chunk.length();
                        }
                        candidates.add(".");
                    } else if (modelType.equals(ModelType.LIST)) {
                        if (candidates.isEmpty()) {
                            candidateIndex += chunk.length();
                        }
                        candidates.add("[");
                    } else if (modelType.equals(ModelType.STRING) || modelType.equals(ModelType.INT) || modelType.equals(ModelType.BOOLEAN)){
                        if (candidates.isEmpty()) {
                            candidateIndex += chunk.length();
                        }
                        candidates.add("");
                    }
                }
            }
            Collections.sort(candidates);
            return candidates;
        }

        private boolean isWritable(ModelNode attrDescr) {
            return !isReadOnly(attrDescr);
        }

        private boolean isReadOnly(ModelNode attrDescr) {
            return attrDescr.has(Util.ACCESS_TYPE) && (Util.READ_ONLY.equals(attrDescr.get(Util.ACCESS_TYPE).asString()) || Util.METRIC.equals(attrDescr.get(Util.ACCESS_TYPE).asString()));
        }

        public int getCandidateIndex() {
            switch(lastStateChar) {
                case '.':
                case '[':
                case ']':
                    return candidateIndex + 1;
            }
            return candidateIndex;
        }

        @Override
        public void enteredState(ParsingContext ctx) throws CommandFormatException {

            final String prevEnteredState = lastEnteredState;
            lastEnteredState = ctx.getState().getId();
            candidateIndex = ctx.getLocation();
            lastStateChar = ctx.getCharacter();

            if(logging) {
                final StringBuilder buf = new StringBuilder();
                for (int i = 0; i < offset; ++i) {
                    buf.append(offsetStep);
                }
                buf.append("entered '" + ctx.getCharacter() + "' " + ctx.getState().getId());
                System.out.println(buf.toString());
            }

            if(!CloseBracketState.ID.equals(prevEnteredState) && isNameSeparator()) {
                if(buf.length() == 0) {
                    throw new CommandFormatException("Attribute name is missing before " + lastStateChar + " at index " + ctx.getLocation() + " in '" + ctx.getInput() + "'");
                }
                switch(path.size()) {
                    case 0:
                        path = Collections.singletonList(buf.toString());
                        break;
                    case 1:
                        path = new ArrayList<String>(path);
                    default:
                        path.add(buf.toString());
                }
                buf.setLength(0);
            }
        }

        private boolean isNameSeparator() {
            return lastEnteredState.equals(DotState.ID) ||
                    lastEnteredState.equals(OpenBracketState.ID);
        }

        @Override
        public void leavingState(ParsingContext ctx) throws CommandFormatException {

            if (logging) {
                final StringBuilder buf = new StringBuilder();
                for (int i = 0; i < offset; ++i) {
                    buf.append(offsetStep);
                }
                buf.append("leaving '" + ctx.getCharacter() + "' " + ctx.getState().getId());
                System.out.println(buf.toString());
            }

            if(ctx.getState().getId().equals(ListIndexState.ID)) {
                buf.setLength(0);
            }
        }

        @Override
        public void character(ParsingContext ctx) throws CommandFormatException {

            if(logging) {
                final StringBuilder buf = new StringBuilder();
                for (int i = 0; i < offset; ++i) {
                    buf.append(offsetStep);
                }
                buf.append("char '" + ctx.getCharacter() + "' " + ctx.getState().getId());
                System.out.println(buf.toString());
            }

            buf.append(ctx.getCharacter());
        }
    }

    private static boolean isAttributeNameChar(final char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-';
    }

    public static class InitialValueState extends DefaultParsingState {
        public static final String ID = "INITVAL";

        public static final InitialValueState INSTANCE = new InitialValueState();

        public InitialValueState() {
            this(AttributeNameState.INSTANCE);
        }

        public InitialValueState(final AttributeNameState attrName) {
            super(ID);
            setDefaultHandler(new CharacterHandler() {
                @Override
                public void handle(ParsingContext ctx) throws CommandFormatException {
                    ctx.enterState(attrName);
                }});
            enterState('.', DotState.INSTANCE);
            enterState('[', OpenBracketState.INSTANCE);
            setReturnHandler(new CharacterHandler() {
                @Override
                public void handle(ParsingContext ctx) throws CommandFormatException {
                    if(!ctx.isEndOfContent()) {
                        final char c = ctx.getCharacter();
                        if(isAttributeNameChar(c) || c == '.' || c == '[' || c == ']') {
                            getHandler(c).handle(ctx);
                        }
                    }
                }});
        }
    }

    public static class AttributeNameState extends DefaultParsingState {
        public static final String ID = "ATTR_NAME";

        public static final AttributeNameState INSTANCE = new AttributeNameState();

        public AttributeNameState() {
            super(ID);
            this.setEnterHandler(new CharacterHandler(){
                @Override
                public void handle(ParsingContext ctx) throws CommandFormatException {
                    final char ch = ctx.getCharacter();
                    final CharacterHandler handler = getHandler(ch);
                    handler.handle(ctx);
                }});
            setDefaultHandler(new CharacterHandler() {
                @Override
                public void handle(ParsingContext ctx) throws CommandFormatException {
                    final char c = ctx.getCharacter();
                    WordCharacterHandler.IGNORE_LB_ESCAPE_ON.handle(ctx);
                }
            });
            putHandler('.', GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
            putHandler('[', GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
            putHandler(']', GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
        }
    }

    public static class DotState extends DefaultParsingState {
        public static final String ID = "DOT";

        public static final DotState INSTANCE = new DotState();

        public DotState() {
            super(ID);
            setDefaultHandler(GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
        }
    }

    public static class ListIndexState extends DefaultParsingState {
        public static final String ID = "LIST_IND";

        public static final ListIndexState INSTANCE = new ListIndexState();

        public ListIndexState() {
            super(ID);
            setDefaultHandler(WordCharacterHandler.IGNORE_LB_ESCAPE_ON);
            enterState(']', CloseBracketState.INSTANCE);
            setReturnHandler(GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
        }
    }

    public static class OpenBracketState extends DefaultParsingState {
        public static final String ID = "OPN_BR";

        public static final OpenBracketState INSTANCE = new OpenBracketState();

        OpenBracketState() {
            super(ID);
            setDefaultHandler(new CharacterHandler() {
                @Override
                public void handle(ParsingContext ctx) throws CommandFormatException {
                    ctx.enterState(ListIndexState.INSTANCE);
                }});
            setReturnHandler(GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
        }
    }

    public static class CloseBracketState extends DefaultParsingState {
        public static final String ID = "CLS_BR";

        public static final CloseBracketState INSTANCE = new CloseBracketState();

        CloseBracketState() {
            super(ID);
            setDefaultHandler(GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
        }
    }

    private ModelNode getAttributeDescription(CommandContext ctx) {
        final ModelNode req = new ModelNode();
        final ModelNode addrNode = req.get(Util.ADDRESS);
        for(OperationRequestAddress.Node node : address) {
            addrNode.add(node.getType(), node.getName());
        }
        req.get(Util.OPERATION).set(Util.READ_RESOURCE_DESCRIPTION);
        final ModelNode response;
        try {
            response = ctx.getModelControllerClient().execute(req);
        } catch (Exception e) {
            return null;
        }
        final ModelNode result = response.get(Util.RESULT);
        if(!result.isDefined()) {
            return null;
        }
        final ModelNode attrs;
        if(result.getType().equals(ModelType.LIST)) {
            ModelNode wildcardResult = null;
            // wildcard address
            for(ModelNode item : result.asList()) {
                final ModelNode addr = item.get(Util.ADDRESS);
                if(!addr.getType().equals(ModelType.LIST)) {
                    return null;
                }
                for(ModelNode node : addr.asList()) {
                    if(!node.getType().equals(ModelType.PROPERTY)) {
                        throw new IllegalArgumentException(node.getType().toString());
                    }
                    if("*".equals(node.asProperty().getValue().asString())) {
                        wildcardResult = item;
                        break;
                    }
                }
                if(wildcardResult != null) {
                    break;
                }
            }
            if(wildcardResult == null) {
                throw new IllegalStateException("Failed to locate the wildcard result.");
            }
            wildcardResult = wildcardResult.get(Util.RESULT);
            if(!wildcardResult.isDefined()) {
                return null;
            }
            attrs = wildcardResult.get(Util.ATTRIBUTES);
        } else {
            attrs = result.get(Util.ATTRIBUTES);
        }
        if(!attrs.isDefined()) {
            return null;
        }
        return attrs;
    }
}
