package cn.nukkit.nbt.snbt;

import cn.nukkit.nbt.snbt.ast.*;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CancellationException;

import static cn.nukkit.nbt.snbt.SNBTConstants.TokenType.*;


public class SNBTParserImplement implements SNBTConstants {
    static final int UNLIMITED = Integer.MAX_VALUE;
    // The last token successfully "consumed"
    Token lastConsumedToken;
    private TokenType nextTokenType;
    private Token currentLookaheadToken;
    private int remainingLookahead;
    private boolean hitFailure, passedPredicate;
    private String currentlyParsedProduction, currentLookaheadProduction;
    private int lookaheadRoutineNesting, passedPredicateThreshold = -1;
    private boolean legacyGlitchyLookahead = false;
    private boolean cancelled;

    public void cancel() {
        cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Generated Lexer.
     */
    public SNBTLexer token_source;

    public void setInputSource(String inputSource) {
        token_source.setInputSource(inputSource);
    }

    String getInputSource() {
        return token_source.getInputSource();
    }

    //=================================
    // Generated constructors
    //=================================
    public SNBTParserImplement(String inputSource, CharSequence content) {
        this(new SNBTLexer(inputSource, content));
    }

    public SNBTParserImplement(CharSequence content) {
        this("input", content);
    }

    /**
     * @param inputSource just the name of the input source (typically the filename) that
     *                    will be used in error messages and so on.
     * @param path        The location (typically the filename) from which to get the input to parse
     */
    public SNBTParserImplement(String inputSource, Path path) throws IOException {
        this(inputSource, SNBTLexer.stringFromBytes(Files.readAllBytes(path)));
    }

    public SNBTParserImplement(String inputSource, Path path, Charset charset) throws IOException {
        this(inputSource, SNBTLexer.stringFromBytes(Files.readAllBytes(path), charset));
    }

    /**
     * @param path The location (typically the filename) from which to get the input to parse
     */
    public SNBTParserImplement(Path path) throws IOException {
        this(path.toString(), path);
    }

    /**
     * Use the constructor that takes a #java.nio.files.Path or just
     * a String (i.e. CharSequence) directly.
     */
    @Deprecated
    public SNBTParserImplement(InputStream stream) {
        this(new InputStreamReader(stream));
    }

    /**
     * Use the constructor that takes a #java.nio.files.Path or just
     * a String (i.e. CharSequence) directly.
     */
    @Deprecated
    public SNBTParserImplement(Reader reader) {
        this(new SNBTLexer("input", reader));
    }

    /**
     * Constructor with user supplied Lexer.
     */
    public SNBTParserImplement(SNBTLexer lexer) {
        token_source = lexer;
        lastConsumedToken = lexer.DUMMY_START_TOKEN;
        lastConsumedToken.setTokenSource(lexer);
    }

    // If the next token is cached, it returns that
    // Otherwise, it goes to the token_source, i.e. the Lexer.
    final private Token nextToken(final Token tok) {
        Token result = token_source.getNextToken(tok);
        while (result.isUnparsed()) {
            result = token_source.getNextToken(result);
        }
        nextTokenType = null;
        return result;
    }

    /**
     * @return the next Token off the stream. This is the same as #getToken(1)
     */
    final public Token getNextToken() {
        return getToken(1);
    }

    /**
     * @param index how many tokens to look ahead
     * @return the specific regular (i.e. parsed) Token index ahead/behind in the stream.
     * If we are in a lookahead, it looks ahead from the currentLookaheadToken
     * Otherwise, it is the lastConsumedToken. If you pass in a negative
     * number it goes backward.
     */
    final public Token getToken(final int index) {
        Token t = currentLookaheadToken == null ? lastConsumedToken : currentLookaheadToken;
        for (int i = 0; i < index; i++) {
            t = nextToken(t);
        }
        for (int i = 0; i > index; i--) {
            t = t.getPrevious();
            if (t == null) break;
        }
        return t;
    }

    private final TokenType nextTokenType() {
        if (nextTokenType == null) {
            nextTokenType = nextToken(lastConsumedToken).getType();
        }
        return nextTokenType;
    }

    boolean activateTokenTypes(TokenType... types) {
        boolean result = false;
        for (TokenType tt : types) {
            result |= token_source.activeTokenTypes.add(tt);
        }
        if (result) {
            token_source.reset(getToken(0));
            nextTokenType = null;
        }
        return result;
    }

    boolean deactivateTokenTypes(TokenType... types) {
        boolean result = false;
        for (TokenType tt : types) {
            result |= token_source.activeTokenTypes.remove(tt);
        }
        if (result) {
            token_source.reset(getToken(0));
            nextTokenType = null;
        }
        return result;
    }

    private static HashMap<TokenType[], EnumSet<TokenType>> enumSetCache = new HashMap<>();

    private static EnumSet<TokenType> tokenTypeSet(TokenType first, TokenType... rest) {
        TokenType[] key = new TokenType[1 + rest.length];
        key[0] = first;
        if (rest.length > 0) {
            System.arraycopy(rest, 0, key, 1, rest.length);
        }
        Arrays.sort(key);
        if (enumSetCache.containsKey(key)) {
            return enumSetCache.get(key);
        }
        EnumSet<TokenType> result = (rest.length == 0) ? EnumSet.of(first) : EnumSet.of(first, rest);
        enumSetCache.put(key, result);
        return result;
    }

    //=================================
    // Start of methods for BNF Productions
    //This code is generated by the ParserProductions.java.ftl template. 
    //=================================
    static private final EnumSet<TokenType> Value_FIRST_SET = Value_FIRST_SET_init();

    static private EnumSet<TokenType> Value_FIRST_SET_init() {
        return tokenTypeSet(OPEN_BRACKET, OPEN_BRACE, BOOLEAN, FLOAT, DOUBLE, INTEGER, LONG, BYTE, SHORT, STRING);
    }

    // SNBT.javacc:55:1
    final public void Value() {
        if (cancelled) throw new CancellationException();
        String prevProduction = currentlyParsedProduction;
        this.currentlyParsedProduction = "Value";
        // Code for ExpansionChoice specified at SNBT.javacc:56:5
        Value Value1 = null;
        if (buildTree) {
            Value1 = new Value();
            openNodeScope(Value1);
        }
        ParseException parseException2 = null;
        int callStackSize3 = parsingStack.size();
        try {
            if (nextTokenType() == BYTE) {
                // Code for RegexpRef specified at SNBT.javacc:56:5
                consumeToken(BYTE);
            } else if (nextTokenType() == BOOLEAN) {
                // Code for RegexpRef specified at SNBT.javacc:58:5
                consumeToken(BOOLEAN);
            } else if (nextTokenType() == STRING) {
                // Code for RegexpRef specified at SNBT.javacc:60:5
                consumeToken(STRING);
            } else if (nextTokenType() == SHORT) {
                // Code for RegexpRef specified at SNBT.javacc:62:5
                consumeToken(SHORT);
            } else if (nextTokenType() == FLOAT) {
                // Code for RegexpRef specified at SNBT.javacc:64:5
                consumeToken(FLOAT);
            } else if (nextTokenType() == DOUBLE) {
                // Code for RegexpRef specified at SNBT.javacc:66:5
                consumeToken(DOUBLE);
            } else if (nextTokenType() == LONG) {
                // Code for RegexpRef specified at SNBT.javacc:68:5
                consumeToken(LONG);
            } else if (nextTokenType() == INTEGER) {
                // Code for RegexpRef specified at SNBT.javacc:70:5
                consumeToken(INTEGER);
            } else if (scan$SNBT_javacc$72$5()) {
                // Code for NonTerminal specified at SNBT.javacc:72:5
                pushOntoCallStack("Value", "SNBT.javacc", 72, 5);
                try {
                    ByteArrayNBT();
                } finally {
                    popCallStack();
                }
            } else if (scan$SNBT_javacc$74$5()) {
                // Code for NonTerminal specified at SNBT.javacc:74:5
                pushOntoCallStack("Value", "SNBT.javacc", 74, 5);
                try {
                    IntArrayNBT();
                } finally {
                    popCallStack();
                }
            } else if (nextTokenType() == OPEN_BRACE) {
                // Code for NonTerminal specified at SNBT.javacc:76:5
                pushOntoCallStack("Value", "SNBT.javacc", 76, 5);
                try {
                    CompoundNBT();
                } finally {
                    popCallStack();
                }
            } else if (nextTokenType() == OPEN_BRACKET) {
                // Code for NonTerminal specified at SNBT.javacc:78:5
                pushOntoCallStack("Value", "SNBT.javacc", 78, 5);
                try {
                    ListNBT();
                } finally {
                    popCallStack();
                }
            } else {
                pushOntoCallStack("Value", "SNBT.javacc", 56, 5);
                throw new ParseException(this, Value_FIRST_SET, parsingStack);
            }
        } catch (ParseException e) {
            parseException2 = e;
            throw e;
        } finally {
            restoreCallStack(callStackSize3);
            if (Value1 != null) {
                if (parseException2 == null) {
                    closeNodeScope(Value1, nodeArity() > 1);
                } else {
                    clearNodeScope();
                }
            }
            this.currentlyParsedProduction = prevProduction;
        }
    }

    // SNBT.javacc:81:1
    final public void KeyValuePair() {
        if (cancelled) throw new CancellationException();
        String prevProduction = currentlyParsedProduction;
        this.currentlyParsedProduction = "KeyValuePair";
        KeyValuePair KeyValuePair2 = null;
        if (buildTree) {
            KeyValuePair2 = new KeyValuePair();
            openNodeScope(KeyValuePair2);
        }
        ParseException parseException102 = null;
        int callStackSize103 = parsingStack.size();
        try {
            // Code for RegexpRef specified at SNBT.javacc:81:16
            consumeToken(STRING);
            // Code for RegexpRef specified at SNBT.javacc:81:25
            consumeToken(COLON);
            // Code for ZeroOrOne specified at SNBT.javacc:81:33
            if (first_set$SNBT_javacc$81$34.contains(nextTokenType())) {
                // Code for NonTerminal specified at SNBT.javacc:81:34
                pushOntoCallStack("KeyValuePair", "SNBT.javacc", 81, 34);
                try {
                    Value();
                } finally {
                    popCallStack();
                }
            }
        } catch (ParseException e) {
            parseException102 = e;
            throw e;
        } finally {
            restoreCallStack(callStackSize103);
            if (KeyValuePair2 != null) {
                if (parseException102 == null) {
                    closeNodeScope(KeyValuePair2, nodeArity() > 1);
                } else {
                    clearNodeScope();
                }
            }
            this.currentlyParsedProduction = prevProduction;
        }
    }

    // SNBT.javacc:83:1
    final public void ByteArrayNBT() {
        if (cancelled) throw new CancellationException();
        String prevProduction = currentlyParsedProduction;
        this.currentlyParsedProduction = "ByteArrayNBT";
        ByteArrayNBT ByteArrayNBT3 = null;
        if (buildTree) {
            ByteArrayNBT3 = new ByteArrayNBT();
            openNodeScope(ByteArrayNBT3);
        }
        ParseException parseException126 = null;
        int callStackSize127 = parsingStack.size();
        try {
            // Code for RegexpRef specified at SNBT.javacc:84:5
            consumeToken(OPEN_BRACKET);
            // Code for RegexpStringLiteral specified at SNBT.javacc:84:20
            consumeToken(B);
            // Code for RegexpStringLiteral specified at SNBT.javacc:84:24
            consumeToken(_TOKEN_17);
            // Code for ZeroOrOne specified at SNBT.javacc:84:32
            if (nextTokenType() == BOOLEAN || nextTokenType == BYTE) {
                if (nextTokenType() == BYTE) {
                    // Code for RegexpRef specified at SNBT.javacc:84:35
                    consumeToken(BYTE);
                } else if (nextTokenType() == BOOLEAN) {
                    // Code for RegexpRef specified at SNBT.javacc:84:44
                    consumeToken(BOOLEAN);
                } else {
                    pushOntoCallStack("ByteArrayNBT", "SNBT.javacc", 84, 35);
                    throw new ParseException(this, first_set$SNBT_javacc$84$35, parsingStack);
                }
                // Code for ZeroOrMore specified at SNBT.javacc:84:55
                while (true) {
                    if (!(nextTokenType() == COMMA)) break;
                    // Code for RegexpRef specified at SNBT.javacc:84:56
                    consumeToken(COMMA);
                    if (nextTokenType() == BYTE) {
                        // Code for RegexpRef specified at SNBT.javacc:84:65
                        consumeToken(BYTE);
                    } else if (nextTokenType() == BOOLEAN) {
                        // Code for RegexpRef specified at SNBT.javacc:84:74
                        consumeToken(BOOLEAN);
                    } else {
                        pushOntoCallStack("ByteArrayNBT", "SNBT.javacc", 84, 65);
                        throw new ParseException(this, first_set$SNBT_javacc$84$65, parsingStack);
                    }
                }
                // Code for ZeroOrOne specified at SNBT.javacc:84:87
                if (nextTokenType() == COMMA) {
                    // Code for RegexpRef specified at SNBT.javacc:84:88
                    consumeToken(COMMA);
                }
            }
            // Code for RegexpRef specified at SNBT.javacc:84:100
            consumeToken(CLOSE_BRACKET);
        } catch (ParseException e) {
            parseException126 = e;
            throw e;
        } finally {
            restoreCallStack(callStackSize127);
            if (ByteArrayNBT3 != null) {
                if (parseException126 == null) {
                    closeNodeScope(ByteArrayNBT3, nodeArity() > 1);
                } else {
                    clearNodeScope();
                }
            }
            this.currentlyParsedProduction = prevProduction;
        }
    }

    // SNBT.javacc:86:1
    final public void IntArrayNBT() {
        if (cancelled) throw new CancellationException();
        String prevProduction = currentlyParsedProduction;
        this.currentlyParsedProduction = "IntArrayNBT";
        IntArrayNBT IntArrayNBT4 = null;
        if (buildTree) {
            IntArrayNBT4 = new IntArrayNBT();
            openNodeScope(IntArrayNBT4);
        }
        ParseException parseException220 = null;
        int callStackSize221 = parsingStack.size();
        try {
            // Code for RegexpRef specified at SNBT.javacc:87:5
            consumeToken(OPEN_BRACKET);
            // Code for RegexpStringLiteral specified at SNBT.javacc:87:20
            consumeToken(I);
            // Code for RegexpStringLiteral specified at SNBT.javacc:87:24
            consumeToken(_TOKEN_17);
            // Code for ZeroOrOne specified at SNBT.javacc:87:32
            if (nextTokenType() == INTEGER) {
                // Code for RegexpRef specified at SNBT.javacc:87:33
                consumeToken(INTEGER);
                // Code for ZeroOrMore specified at SNBT.javacc:87:43
                while (true) {
                    if (!(nextTokenType() == COMMA)) break;
                    // Code for RegexpRef specified at SNBT.javacc:87:44
                    consumeToken(COMMA);
                    // Code for RegexpRef specified at SNBT.javacc:87:52
                    consumeToken(INTEGER);
                }
                // Code for ZeroOrOne specified at SNBT.javacc:87:64
                if (nextTokenType() == COMMA) {
                    // Code for RegexpRef specified at SNBT.javacc:87:65
                    consumeToken(COMMA);
                }
            }
            // Code for RegexpRef specified at SNBT.javacc:87:77
            consumeToken(CLOSE_BRACKET);
        } catch (ParseException e) {
            parseException220 = e;
            throw e;
        } finally {
            restoreCallStack(callStackSize221);
            if (IntArrayNBT4 != null) {
                if (parseException220 == null) {
                    closeNodeScope(IntArrayNBT4, nodeArity() > 1);
                } else {
                    clearNodeScope();
                }
            }
            this.currentlyParsedProduction = prevProduction;
        }
    }

    // SNBT.javacc:89:1
    final public void ListNBT() {
        if (cancelled) throw new CancellationException();
        String prevProduction = currentlyParsedProduction;
        this.currentlyParsedProduction = "ListNBT";
        ListNBT ListNBT5 = null;
        if (buildTree) {
            ListNBT5 = new ListNBT();
            openNodeScope(ListNBT5);
        }
        ParseException parseException280 = null;
        int callStackSize281 = parsingStack.size();
        try {
            // Code for RegexpRef specified at SNBT.javacc:90:5
            consumeToken(OPEN_BRACKET);
            // Code for ZeroOrOne specified at SNBT.javacc:90:20
            if (first_set$SNBT_javacc$90$21.contains(nextTokenType())) {
                // Code for NonTerminal specified at SNBT.javacc:90:21
                pushOntoCallStack("ListNBT", "SNBT.javacc", 90, 21);
                try {
                    Value();
                } finally {
                    popCallStack();
                }
                // Code for ZeroOrMore specified at SNBT.javacc:90:27
                while (true) {
                    if (!(nextTokenType() == COMMA)) break;
                    // Code for RegexpRef specified at SNBT.javacc:90:28
                    consumeToken(COMMA);
                    // Code for NonTerminal specified at SNBT.javacc:90:36
                    pushOntoCallStack("ListNBT", "SNBT.javacc", 90, 36);
                    try {
                        Value();
                    } finally {
                        popCallStack();
                    }
                }
            }
            // Code for RegexpRef specified at SNBT.javacc:90:46
            consumeToken(CLOSE_BRACKET);
        } catch (ParseException e) {
            parseException280 = e;
            throw e;
        } finally {
            restoreCallStack(callStackSize281);
            if (ListNBT5 != null) {
                if (parseException280 == null) {
                    closeNodeScope(ListNBT5, nodeArity() > 1);
                } else {
                    clearNodeScope();
                }
            }
            this.currentlyParsedProduction = prevProduction;
        }
    }

    // SNBT.javacc:92:1
    final public void CompoundNBT() {
        if (cancelled) throw new CancellationException();
        String prevProduction = currentlyParsedProduction;
        this.currentlyParsedProduction = "CompoundNBT";
        CompoundNBT CompoundNBT6 = null;
        if (buildTree) {
            CompoundNBT6 = new CompoundNBT();
            openNodeScope(CompoundNBT6);
        }
        ParseException parseException320 = null;
        int callStackSize321 = parsingStack.size();
        try {
            // Code for RegexpRef specified at SNBT.javacc:92:15
            consumeToken(OPEN_BRACE);
            // Code for ZeroOrOne specified at SNBT.javacc:92:28
            if (nextTokenType() == STRING) {
                // Code for NonTerminal specified at SNBT.javacc:92:29
                pushOntoCallStack("CompoundNBT", "SNBT.javacc", 92, 29);
                try {
                    KeyValuePair();
                } finally {
                    popCallStack();
                }
                // Code for ZeroOrMore specified at SNBT.javacc:92:42
                while (true) {
                    if (!(nextTokenType() == COMMA)) break;
                    // Code for RegexpRef specified at SNBT.javacc:92:43
                    consumeToken(COMMA);
                    // Code for NonTerminal specified at SNBT.javacc:92:51
                    pushOntoCallStack("CompoundNBT", "SNBT.javacc", 92, 51);
                    try {
                        KeyValuePair();
                    } finally {
                        popCallStack();
                    }
                }
            }
            // Code for RegexpRef specified at SNBT.javacc:92:68
            consumeToken(CLOSE_BRACE);
        } catch (ParseException e) {
            parseException320 = e;
            throw e;
        } finally {
            restoreCallStack(callStackSize321);
            if (CompoundNBT6 != null) {
                if (parseException320 == null) {
                    closeNodeScope(CompoundNBT6, nodeArity() > 1);
                } else {
                    clearNodeScope();
                }
            }
            this.currentlyParsedProduction = prevProduction;
        }
    }

    // SNBT.javacc:95:1
    final public void Root() {
        if (cancelled) throw new CancellationException();
        String prevProduction = currentlyParsedProduction;
        this.currentlyParsedProduction = "Root";
        Root Root7 = null;
        if (buildTree) {
            Root7 = new Root();
            openNodeScope(Root7);
        }
        ParseException parseException360 = null;
        int callStackSize361 = parsingStack.size();
        try {
            // Code for NonTerminal specified at SNBT.javacc:95:8
            pushOntoCallStack("Root", "SNBT.javacc", 95, 8);
            try {
                Value();
            } finally {
                popCallStack();
            }
            // Code for EndOfFile specified at SNBT.javacc:95:14
            consumeToken(EOF);
        } catch (ParseException e) {
            parseException360 = e;
            throw e;
        } finally {
            restoreCallStack(callStackSize361);
            if (Root7 != null) {
                if (parseException360 == null) {
                    closeNodeScope(Root7, nodeArity() > 1);
                } else {
                    clearNodeScope();
                }
            }
            this.currentlyParsedProduction = prevProduction;
        }
    }

    static private final EnumSet<TokenType> first_set$SNBT_javacc$81$34 = first_set$SNBT_javacc$81$34_init();

    static private EnumSet<TokenType> first_set$SNBT_javacc$81$34_init() {
        return tokenTypeSet(OPEN_BRACKET, OPEN_BRACE, BOOLEAN, FLOAT, DOUBLE, INTEGER, LONG, BYTE, SHORT, STRING);
    }

    static private final EnumSet<TokenType> first_set$SNBT_javacc$84$35 = tokenTypeSet(BOOLEAN, BYTE);
    static private final EnumSet<TokenType> first_set$SNBT_javacc$84$65 = tokenTypeSet(BOOLEAN, BYTE);
    static private final EnumSet<TokenType> first_set$SNBT_javacc$90$21 = first_set$SNBT_javacc$90$21_init();

    static private EnumSet<TokenType> first_set$SNBT_javacc$90$21_init() {
        return tokenTypeSet(OPEN_BRACKET, OPEN_BRACE, BOOLEAN, FLOAT, DOUBLE, INTEGER, LONG, BYTE, SHORT, STRING);
    }

    private final boolean scanToken(TokenType expectedType, TokenType... additionalTypes) {
        Token peekedToken = nextToken(currentLookaheadToken);
        TokenType type = peekedToken.getType();
        if (type != expectedType) {
            boolean matched = false;
            for (TokenType tt : additionalTypes) {
                if (type == tt) {
                    matched = true;
                    break;
                }
            }
            if (!matched) return false;
        }
        --remainingLookahead;
        currentLookaheadToken = peekedToken;
        return true;
    }

    private final boolean scanToken(EnumSet<TokenType> types) {
        Token peekedToken = nextToken(currentLookaheadToken);
        TokenType type = peekedToken.getType();
        if (!types.contains(type)) return false;
        --remainingLookahead;
        currentLookaheadToken = peekedToken;
        return true;
    }

    // scanahead routine for expansion at: 
    // SNBT.javacc:84:34
    // BuildScanRoutine macro
    private final boolean check$SNBT_javacc$84$34(boolean scanToEnd) {
        boolean $reachedScanCode$ = false;
        int passedPredicateThreshold = remainingLookahead - 1;
        try {
            lookaheadRoutineNesting++;
            // BuildPredicateCode macro
            // End BuildPredicateCode macro
            $reachedScanCode$ = true;
            if (hitFailure) return false;
            if (remainingLookahead <= 0) {
                return true;
            }
            // Lookahead Code for ExpansionChoice specified at SNBT.javacc:84:35
            if (!scanToken(BOOLEAN, BYTE)) return false;
            if (hitFailure) return false;
            if (remainingLookahead <= 0) {
                return true;
            }
            // Lookahead Code for ZeroOrMore specified at SNBT.javacc:84:55
            boolean passedPredicate385 = passedPredicate;
            try {
                while (remainingLookahead > 0 && !hitFailure) {
                    Token token386 = currentLookaheadToken;
                    passedPredicate = false;
                    if (!check$SNBT_javacc$84$56(false)) {
                        if (passedPredicate && !legacyGlitchyLookahead) return false;
                        currentLookaheadToken = token386;
                        break;
                    }
                }
            } finally {
                passedPredicate = passedPredicate385;
            }
            hitFailure = false;
            if (hitFailure) return false;
            if (remainingLookahead <= 0) {
                return true;
            }
            // Lookahead Code for ZeroOrOne specified at SNBT.javacc:84:87
            Token token388 = currentLookaheadToken;
            boolean passedPredicate388 = passedPredicate;
            passedPredicate = false;
            try {
                if (!scanToken(COMMA)) {
                    if (passedPredicate && !legacyGlitchyLookahead) return false;
                    currentLookaheadToken = token388;
                    hitFailure = false;
                }
            } finally {
                passedPredicate = passedPredicate388;
            }
        } finally {
            lookaheadRoutineNesting--;
            if ($reachedScanCode$ && remainingLookahead <= passedPredicateThreshold) {
                passedPredicate = true;
            }
        }
        passedPredicate = false;
        return true;
    }

    // scanahead routine for expansion at: 
    // SNBT.javacc:84:56
    // BuildScanRoutine macro
    private final boolean check$SNBT_javacc$84$56(boolean scanToEnd) {
        boolean $reachedScanCode$ = false;
        int passedPredicateThreshold = remainingLookahead - 1;
        try {
            lookaheadRoutineNesting++;
            // BuildPredicateCode macro
            // End BuildPredicateCode macro
            $reachedScanCode$ = true;
            if (hitFailure) return false;
            if (remainingLookahead <= 0) {
                return true;
            }
            // Lookahead Code for RegexpRef specified at SNBT.javacc:84:56
            if (!scanToken(COMMA)) return false;
            if (hitFailure) return false;
            if (remainingLookahead <= 0) {
                return true;
            }
            // Lookahead Code for ExpansionChoice specified at SNBT.javacc:84:65
            if (!scanToken(BOOLEAN, BYTE)) return false;
        } finally {
            lookaheadRoutineNesting--;
            if ($reachedScanCode$ && remainingLookahead <= passedPredicateThreshold) {
                passedPredicate = true;
            }
        }
        passedPredicate = false;
        return true;
    }

    // scanahead routine for expansion at: 
    // SNBT.javacc:87:33
    // BuildScanRoutine macro
    private final boolean check$SNBT_javacc$87$33(boolean scanToEnd) {
        boolean $reachedScanCode$ = false;
        int passedPredicateThreshold = remainingLookahead - 1;
        try {
            lookaheadRoutineNesting++;
            // BuildPredicateCode macro
            // End BuildPredicateCode macro
            $reachedScanCode$ = true;
            if (hitFailure) return false;
            if (remainingLookahead <= 0) {
                return true;
            }
            // Lookahead Code for RegexpRef specified at SNBT.javacc:87:33
            if (!scanToken(INTEGER)) return false;
            if (hitFailure) return false;
            if (remainingLookahead <= 0) {
                return true;
            }
            // Lookahead Code for ZeroOrMore specified at SNBT.javacc:87:43
            boolean passedPredicate396 = passedPredicate;
            try {
                while (remainingLookahead > 0 && !hitFailure) {
                    Token token397 = currentLookaheadToken;
                    passedPredicate = false;
                    if (!check$SNBT_javacc$87$44(false)) {
                        if (passedPredicate && !legacyGlitchyLookahead) return false;
                        currentLookaheadToken = token397;
                        break;
                    }
                }
            } finally {
                passedPredicate = passedPredicate396;
            }
            hitFailure = false;
            if (hitFailure) return false;
            if (remainingLookahead <= 0) {
                return true;
            }
            // Lookahead Code for ZeroOrOne specified at SNBT.javacc:87:64
            Token token399 = currentLookaheadToken;
            boolean passedPredicate399 = passedPredicate;
            passedPredicate = false;
            try {
                if (!scanToken(COMMA)) {
                    if (passedPredicate && !legacyGlitchyLookahead) return false;
                    currentLookaheadToken = token399;
                    hitFailure = false;
                }
            } finally {
                passedPredicate = passedPredicate399;
            }
        } finally {
            lookaheadRoutineNesting--;
            if ($reachedScanCode$ && remainingLookahead <= passedPredicateThreshold) {
                passedPredicate = true;
            }
        }
        passedPredicate = false;
        return true;
    }

    // scanahead routine for expansion at: 
    // SNBT.javacc:87:44
    // BuildScanRoutine macro
    private final boolean check$SNBT_javacc$87$44(boolean scanToEnd) {
        boolean $reachedScanCode$ = false;
        int passedPredicateThreshold = remainingLookahead - 1;
        try {
            lookaheadRoutineNesting++;
            // BuildPredicateCode macro
            // End BuildPredicateCode macro
            $reachedScanCode$ = true;
            if (hitFailure) return false;
            if (remainingLookahead <= 0) {
                return true;
            }
            // Lookahead Code for RegexpRef specified at SNBT.javacc:87:44
            if (!scanToken(COMMA)) return false;
            if (hitFailure) return false;
            if (remainingLookahead <= 0) {
                return true;
            }
            // Lookahead Code for RegexpRef specified at SNBT.javacc:87:52
            if (!scanToken(INTEGER)) return false;
        } finally {
            lookaheadRoutineNesting--;
            if ($reachedScanCode$ && remainingLookahead <= passedPredicateThreshold) {
                passedPredicate = true;
            }
        }
        passedPredicate = false;
        return true;
    }

    // BuildPredicateRoutine: expansion at SNBT.javacc:72:5
    private final boolean scan$SNBT_javacc$72$5() {
        remainingLookahead = UNLIMITED;
        currentLookaheadToken = lastConsumedToken;
        final boolean scanToEnd = false;
        try {
            // BuildPredicateCode macro
            // End BuildPredicateCode macro
            if (hitFailure) return false;
            if (remainingLookahead <= 0) {
                return true;
            }
            // Lookahead Code for NonTerminal specified at SNBT.javacc:72:5
            // NonTerminal ByteArrayNBT at SNBT.javacc:72:5
            pushOntoLookaheadStack("Value", "SNBT.javacc", 72, 5);
            currentLookaheadProduction = "ByteArrayNBT";
            try {
                if (!check$ByteArrayNBT(false)) return false;
            } finally {
                popLookaheadStack();
            }
            return true;
        } finally {
            lookaheadRoutineNesting = 0;
            currentLookaheadToken = null;
            hitFailure = false;
        }
    }

    // BuildPredicateRoutine: expansion at SNBT.javacc:74:5
    private final boolean scan$SNBT_javacc$74$5() {
        remainingLookahead = UNLIMITED;
        currentLookaheadToken = lastConsumedToken;
        final boolean scanToEnd = false;
        try {
            // BuildPredicateCode macro
            // End BuildPredicateCode macro
            if (hitFailure) return false;
            if (remainingLookahead <= 0) {
                return true;
            }
            // Lookahead Code for NonTerminal specified at SNBT.javacc:74:5
            // NonTerminal IntArrayNBT at SNBT.javacc:74:5
            pushOntoLookaheadStack("Value", "SNBT.javacc", 74, 5);
            currentLookaheadProduction = "IntArrayNBT";
            try {
                if (!check$IntArrayNBT(false)) return false;
            } finally {
                popLookaheadStack();
            }
            return true;
        } finally {
            lookaheadRoutineNesting = 0;
            currentLookaheadToken = null;
            hitFailure = false;
        }
    }

    // BuildProductionLookaheadMethod macro
    private final boolean check$ByteArrayNBT(boolean scanToEnd) {
        if (hitFailure) return false;
        if (remainingLookahead <= 0) {
            return true;
        }
        // Lookahead Code for RegexpRef specified at SNBT.javacc:84:5
        if (!scanToken(OPEN_BRACKET)) return false;
        if (hitFailure) return false;
        if (remainingLookahead <= 0) {
            return true;
        }
        // Lookahead Code for RegexpStringLiteral specified at SNBT.javacc:84:20
        if (!scanToken(B)) return false;
        if (hitFailure) return false;
        if (remainingLookahead <= 0) {
            return true;
        }
        // Lookahead Code for RegexpStringLiteral specified at SNBT.javacc:84:24
        if (!scanToken(_TOKEN_17)) return false;
        if (!scanToEnd && lookaheadStack.size() <= 1) {
            if (lookaheadRoutineNesting == 0) {
                remainingLookahead = 0;
            } else if (lookaheadStack.size() == 1) {
                passedPredicateThreshold = remainingLookahead;
            }
        }
        if (hitFailure) return false;
        if (remainingLookahead <= 0) {
            return true;
        }
        // Lookahead Code for ZeroOrOne specified at SNBT.javacc:84:32
        Token token449 = currentLookaheadToken;
        boolean passedPredicate449 = passedPredicate;
        passedPredicate = false;
        try {
            if (!check$SNBT_javacc$84$34(false)) {
                if (passedPredicate && !legacyGlitchyLookahead) return false;
                currentLookaheadToken = token449;
                hitFailure = false;
            }
        } finally {
            passedPredicate = passedPredicate449;
        }
        if (hitFailure) return false;
        if (remainingLookahead <= 0) {
            return true;
        }
        // Lookahead Code for RegexpRef specified at SNBT.javacc:84:100
        if (!scanToken(CLOSE_BRACKET)) return false;
        return true;
    }

    // BuildProductionLookaheadMethod macro
    private final boolean check$IntArrayNBT(boolean scanToEnd) {
        if (hitFailure) return false;
        if (remainingLookahead <= 0) {
            return true;
        }
        // Lookahead Code for RegexpRef specified at SNBT.javacc:87:5
        if (!scanToken(OPEN_BRACKET)) return false;
        if (hitFailure) return false;
        if (remainingLookahead <= 0) {
            return true;
        }
        // Lookahead Code for RegexpStringLiteral specified at SNBT.javacc:87:20
        if (!scanToken(I)) return false;
        if (hitFailure) return false;
        if (remainingLookahead <= 0) {
            return true;
        }
        // Lookahead Code for RegexpStringLiteral specified at SNBT.javacc:87:24
        if (!scanToken(_TOKEN_17)) return false;
        if (!scanToEnd && lookaheadStack.size() <= 1) {
            if (lookaheadRoutineNesting == 0) {
                remainingLookahead = 0;
            } else if (lookaheadStack.size() == 1) {
                passedPredicateThreshold = remainingLookahead;
            }
        }
        if (hitFailure) return false;
        if (remainingLookahead <= 0) {
            return true;
        }
        // Lookahead Code for ZeroOrOne specified at SNBT.javacc:87:32
        Token token456 = currentLookaheadToken;
        boolean passedPredicate456 = passedPredicate;
        passedPredicate = false;
        try {
            if (!check$SNBT_javacc$87$33(false)) {
                if (passedPredicate && !legacyGlitchyLookahead) return false;
                currentLookaheadToken = token456;
                hitFailure = false;
            }
        } finally {
            passedPredicate = passedPredicate456;
        }
        if (hitFailure) return false;
        if (remainingLookahead <= 0) {
            return true;
        }
        // Lookahead Code for RegexpRef specified at SNBT.javacc:87:77
        if (!scanToken(CLOSE_BRACKET)) return false;
        return true;
    }

    ArrayList<NonTerminalCall> parsingStack = new ArrayList<>();
    private ArrayList<NonTerminalCall> lookaheadStack = new ArrayList<>();

    /**
     * Inner class that represents entering a grammar production
     */
    class NonTerminalCall {
        final String sourceFile;
        final String productionName;
        final int line, column;

        NonTerminalCall(String sourceFile, String productionName, int line, int column) {
            this.sourceFile = sourceFile;
            this.productionName = productionName;
            this.line = line;
            this.column = column;
        }

        final SNBTLexer getTokenSource() {
            return SNBTParserImplement.this.token_source;
        }

        StackTraceElement createStackTraceElement() {
            return new StackTraceElement("SNBTParserImplement", productionName, sourceFile, line);
        }

        void dump(PrintStream ps) {
            ps.println(productionName + ":" + line + ":" + column);
        }

    }


    private final void pushOntoCallStack(String methodName, String fileName, int line, int column) {
        parsingStack.add(new NonTerminalCall(fileName, methodName, line, column));
    }

    private final void popCallStack() {
        NonTerminalCall ntc = parsingStack.remove(parsingStack.size() - 1);
        this.currentlyParsedProduction = ntc.productionName;
    }

    private final void restoreCallStack(int prevSize) {
        while (parsingStack.size() > prevSize) {
            popCallStack();
        }
    }

    private final void pushOntoLookaheadStack(String methodName, String fileName, int line, int column) {
        lookaheadStack.add(new NonTerminalCall(fileName, methodName, line, column));
    }

    private final void popLookaheadStack() {
        NonTerminalCall ntc = lookaheadStack.remove(lookaheadStack.size() - 1);
        this.currentLookaheadProduction = ntc.productionName;
    }

    void dumpLookaheadStack(PrintStream ps) {
        ListIterator<NonTerminalCall> it = lookaheadStack.listIterator(lookaheadStack.size());
        while (it.hasPrevious()) {
            it.previous().dump(ps);
        }
    }

    void dumpCallStack(PrintStream ps) {
        ListIterator<NonTerminalCall> it = parsingStack.listIterator(parsingStack.size());
        while (it.hasPrevious()) {
            it.previous().dump(ps);
        }
    }

    void dumpLookaheadCallStack(PrintStream ps) {
        ps.println("Current Parser Production is: " + currentlyParsedProduction);
        ps.println("Current Lookahead Production is: " + currentLookaheadProduction);
        ps.println("---Lookahead Stack---");
        dumpLookaheadStack(ps);
        ps.println("---Call Stack---");
        dumpCallStack(ps);
    }

    public boolean isParserTolerant() {
        return false;
    }

    public void setParserTolerant(boolean tolerantParsing) {
        if (tolerantParsing) {
            throw new UnsupportedOperationException("This parser was not built with that feature!");
        }
    }

    private Token consumeToken(TokenType expectedType) {
        Token nextToken = nextToken(lastConsumedToken);
        if (nextToken.getType() != expectedType) {
            nextToken = handleUnexpectedTokenType(expectedType, nextToken);
        }
        this.lastConsumedToken = nextToken;
        this.nextTokenType = null;
        if (buildTree && tokensAreNodes) {
            pushNode(lastConsumedToken);
        }
        return lastConsumedToken;
    }

    private Token handleUnexpectedTokenType(TokenType expectedType, Token nextToken) {
        throw new ParseException(this, nextToken, EnumSet.of(expectedType), parsingStack);
    }


    private class ParseState {
        Token lastConsumed;
        ArrayList<NonTerminalCall> parsingStack;
        NodeScope nodeScope;

        ParseState() {
            this.lastConsumed = SNBTParserImplement.this.lastConsumedToken;
            @SuppressWarnings("unchecked")
            ArrayList<NonTerminalCall> parsingStack = (ArrayList<NonTerminalCall>) SNBTParserImplement.this.parsingStack.clone();
            this.parsingStack = parsingStack;
            this.nodeScope = currentNodeScope.clone();
        }

    }

    private boolean buildTree = true;
    private boolean tokensAreNodes = true;
    private boolean unparsedTokensAreNodes = false;

    public boolean isTreeBuildingEnabled() {
        return buildTree;
    }

    public void setUnparsedTokensAreNodes(boolean unparsedTokensAreNodes) {
        this.unparsedTokensAreNodes = unparsedTokensAreNodes;
    }

    public void setTokensAreNodes(boolean tokensAreNodes) {
        this.tokensAreNodes = tokensAreNodes;
    }

    NodeScope currentNodeScope = new NodeScope();

    /**
     * @return the root node of the AST. It only makes sense to call
     * this after a successful parse.
     */
    public Node rootNode() {
        return currentNodeScope.rootNode();
    }

    /**
     * push a node onto the top of the node stack
     *
     * @param n the node to push
     */
    public void pushNode(Node n) {
        currentNodeScope.add(n);
    }

    /**
     * @return the node on the top of the stack, and remove it from the
     * stack.
     */
    public Node popNode() {
        return currentNodeScope.pop();
    }

    /**
     * @return the node currently on the top of the tree-building stack.
     */
    public Node peekNode() {
        return currentNodeScope.peek();
    }

    /**
     * Puts the node on the top of the stack. However, unlike pushNode()
     * it replaces the node that is currently on the top of the stack.
     * This is effectively equivalent to popNode() followed by pushNode(n)
     *
     * @param n the node to poke
     */
    public void pokeNode(Node n) {
        currentNodeScope.poke(n);
    }

    /**
     * @return the number of Nodes on the tree-building stack in the current node
     * scope.
     */
    public int nodeArity() {
        return currentNodeScope.size();
    }

    private void clearNodeScope() {
        currentNodeScope.clear();
    }

    private void openNodeScope(Node n) {
        new NodeScope();
        if (n != null) {
            Token next = nextToken(lastConsumedToken);
            n.setTokenSource(lastConsumedToken.getTokenSource());
            n.setBeginOffset(next.getBeginOffset());
            n.open();
        }
    }

    /* A definite node is constructed from a specified number of
     * children.  That number of nodes are popped from the stack and
     * made the children of the definite node.  Then the definite node
     * is pushed on to the stack.
     */
    private void closeNodeScope(Node n, int num) {
        n.setEndOffset(lastConsumedToken.getEndOffset());
        currentNodeScope.close();
        ArrayList<Node> nodes = new ArrayList<Node>();
        for (int i = 0; i < num; i++) {
            nodes.add(popNode());
        }
        Collections.reverse(nodes);
        for (Node child : nodes) {
            // FIXME deal with the UNPARSED_TOKENS_ARE_NODES case
            n.addChild(child);
        }
        n.close();
        pushNode(n);
    }

    /**
     * A conditional node is constructed if the condition is true.  All
     * the nodes that have been pushed since the node was opened are
     * made children of the conditional node, which is then pushed
     * on to the stack.  If the condition is false the node is not
     * constructed and they are left on the stack.
     */
    private void closeNodeScope(Node n, boolean condition) {
        if (n != null && condition) {
            n.setEndOffset(lastConsumedToken.getEndOffset());
            int a = nodeArity();
            currentNodeScope.close();
            ArrayList<Node> nodes = new ArrayList<Node>();
            while (a-- > 0) {
                nodes.add(popNode());
            }
            Collections.reverse(nodes);
            for (Node child : nodes) {
                if (unparsedTokensAreNodes && child instanceof Token) {
                    Token tok = (Token) child;
                    while (tok.previousCachedToken() != null && tok.previousCachedToken().isUnparsed()) {
                        tok = tok.previousCachedToken();
                    }
                    while (tok.isUnparsed()) {
                        n.addChild(tok);
                        tok = tok.nextCachedToken();
                    }
                }
                n.addChild(child);
            }
            n.close();
            pushNode(n);
        } else {
            currentNodeScope.close();
        }
    }

    public boolean getBuildTree() {
        return buildTree;
    }

    public void setBuildTree(boolean buildTree) {
        this.buildTree = buildTree;
    }

    /**
     * Just a kludge so that existing jjtree-based code that uses
     * parser.jjtree.foo can work without change.
     */
    SNBTParserImplement jjtree = this;

    @SuppressWarnings("serial")
    class NodeScope extends ArrayList<Node> {
        NodeScope parentScope;

        NodeScope() {
            this.parentScope = SNBTParserImplement.this.currentNodeScope;
            SNBTParserImplement.this.currentNodeScope = this;
        }

        boolean isRootScope() {
            return parentScope == null;
        }

        Node rootNode() {
            NodeScope ns = this;
            while (ns.parentScope != null) {
                ns = ns.parentScope;
            }
            return ns.isEmpty() ? null : ns.get(0);
        }

        Node peek() {
            if (isEmpty()) {
                return parentScope == null ? null : parentScope.peek();
            }
            return get(size() - 1);
        }

        Node pop() {
            return isEmpty() ? parentScope.pop() : remove(size() - 1);
        }

        void poke(Node n) {
            if (isEmpty()) {
                parentScope.poke(n);
            } else {
                set(size() - 1, n);
            }
        }

        void close() {
            parentScope.addAll(this);
            SNBTParserImplement.this.currentNodeScope = parentScope;
        }

        int nestingLevel() {
            int result = 0;
            NodeScope parent = this;
            while (parent.parentScope != null) {
                result++;
                parent = parent.parentScope;
            }
            return result;
        }

        public NodeScope clone() {
            NodeScope clone = (NodeScope) super.clone();
            if (parentScope != null) {
                clone.parentScope = parentScope.clone();
            }
            return clone;
        }

    }

}

