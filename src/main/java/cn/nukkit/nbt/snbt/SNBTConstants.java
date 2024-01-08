package cn.nukkit.nbt.snbt;


public interface SNBTConstants {

    /**
     * The various token types. The first type EOF
     * and the last type INVALID are auto-generated.
     * They represent the end of input and invalid input
     * respectively.
     */
    public enum TokenType {
        EOF, WHITESPACE, COLON, COMMA, OPEN_BRACKET, CLOSE_BRACKET, OPEN_BRACE, CLOSE_BRACE, BOOLEAN, FLOAT, DOUBLE, INTEGER, LONG, BYTE, SHORT, STRING, B, _TOKEN_17, I, INVALID
    }


    /**
     * Lexical States
     */
    public enum LexicalState {
        SNBT
    }

}


