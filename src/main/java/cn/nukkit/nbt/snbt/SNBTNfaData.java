package cn.nukkit.nbt.snbt;

import java.util.BitSet;
import java.util.EnumSet;

import static cn.nukkit.nbt.snbt.SNBTConstants.TokenType.*;


/**
 * Holder class for the data used by SNBTLexer
 * to do the NFA thang
 */
class SNBTNfaData implements SNBTConstants {

    static private NfaFunction[] nfaFunctions;

    // Initialize the various NFA method tables
    static {
        SNBT.NFA_FUNCTIONS_init();
    }

    // This data holder class is never instantiated
    private SNBTNfaData() {
    }

    /**
     * @param lexicalState the lexical state
     * @return the table of function pointers that implement the lexical state
     */
    static final NfaFunction[] getFunctionTableMap(LexicalState lexicalState) {
        // We only have one lexical state in this case, so we return that!
        return nfaFunctions;
    }

    // The functional interface that represents
    // the acceptance method of an NFA state
    interface NfaFunction {

        TokenType apply(int ch, BitSet bs, EnumSet<TokenType> validTypes);

    }

    /**
     * Holder class for NFA code related to SNBT lexical state
     */
    private static class SNBT {

        static private TokenType NFA_SNBT_0(int ch, BitSet nextStates, EnumSet<TokenType> validTypes) {
            TokenType type = null;
            if (ch == '"') {
                nextStates.set(12);
            } else if (ch == '\'') {
                nextStates.set(1);
            } else if (ch == '-') {
                nextStates.set(21);
                nextStates.set(28);
                nextStates.set(19);
                nextStates.set(38);
                nextStates.set(31);
                nextStates.set(16);
            } else if (ch == '0') {
                nextStates.set(33);
                nextStates.set(11);
                nextStates.set(26);
                nextStates.set(17);
                nextStates.set(32);
            } else if (ch >= '1' && ch <= '9') {
                nextStates.set(37);
                nextStates.set(23);
                nextStates.set(3);
                nextStates.set(34);
                nextStates.set(10);
            } else if (ch == 'f') {
                nextStates.set(20);
            } else if (ch == 't') {
                nextStates.set(6);
            } else if (ch == 'I') {
                if (validTypes.contains(I))
                    type = I;
            } else if (ch == ';') {
                if (validTypes.contains(_TOKEN_17))
                    type = _TOKEN_17;
            } else if (ch == 'B') {
                if (validTypes.contains(B))
                    type = B;
            }
            if (ch == '0') {
                if (validTypes.contains(INTEGER))
                    type = INTEGER;
            } else if (ch >= '1' && ch <= '9') {
                nextStates.set(5);
                if (validTypes.contains(INTEGER))
                    type = INTEGER;
            } else if (ch == '}') {
                if (validTypes.contains(CLOSE_BRACE))
                    type = CLOSE_BRACE;
            } else if (ch == '{') {
                if (validTypes.contains(OPEN_BRACE))
                    type = OPEN_BRACE;
            } else if (ch == ']') {
                if (validTypes.contains(CLOSE_BRACKET))
                    type = CLOSE_BRACKET;
            } else if (ch == '[') {
                if (validTypes.contains(OPEN_BRACKET))
                    type = OPEN_BRACKET;
            } else if (ch == ',') {
                if (validTypes.contains(COMMA))
                    type = COMMA;
            } else if (ch == ':') {
                if (validTypes.contains(COLON))
                    type = COLON;
            } else if (ch == '\t') {
                nextStates.set(9);
                if (validTypes.contains(WHITESPACE))
                    type = WHITESPACE;
            } else if (ch == '\n') {
                nextStates.set(9);
                if (validTypes.contains(WHITESPACE))
                    type = WHITESPACE;
            } else if (ch == '\r') {
                nextStates.set(9);
                if (validTypes.contains(WHITESPACE))
                    type = WHITESPACE;
            } else if (ch == ' ') {
                nextStates.set(9);
                if (validTypes.contains(WHITESPACE))
                    type = WHITESPACE;
            }
            return type;
        }

        static private TokenType NFA_SNBT_1(int ch, BitSet nextStates, EnumSet<TokenType> validTypes) {
            TokenType type = null;
            if ((ch >= 0x0 && ch <= '&') || (ch >= '(')) {
                nextStates.set(1);
            }
            if (ch == '\\') {
                nextStates.set(36);
            } else if (ch == '\'') {
                if (validTypes.contains(STRING))
                    type = STRING;
            }
            return type;
        }

        static private TokenType NFA_SNBT_2(int ch, BitSet nextStates, EnumSet<TokenType> validTypes) {
            TokenType type = null;
            if (ch >= '1' && ch <= '9') {
                nextStates.set(39);
            }
            return type;
        }

        static private TokenType NFA_SNBT_3(int ch, BitSet nextStates, EnumSet<TokenType> validTypes) {
            TokenType type = null;
            if (ch >= '0' && ch <= '9') {
                nextStates.set(3);
            } else if (ch == 'L') {
                if (validTypes.contains(LONG))
                    type = LONG;
            } else if (ch == 'l') {
                if (validTypes.contains(LONG))
                    type = LONG;
            }
            return type;
        }

        static private TokenType NFA_SNBT_4(int ch, BitSet nextStates, EnumSet<TokenType> validTypes) {
            TokenType type = null;
            if (ch >= '0' && ch <= '9') {
                nextStates.set(29);
            }
            return type;
        }

        static private TokenType NFA_SNBT_5(int ch, BitSet nextStates, EnumSet<TokenType> validTypes) {
            TokenType type = null;
            if (ch >= '0' && ch <= '9') {
                nextStates.set(5);
                if (validTypes.contains(INTEGER))
                    type = INTEGER;
            }
            return type;
        }

        static private TokenType NFA_SNBT_6(int ch, BitSet nextStates, EnumSet<TokenType> validTypes) {
            TokenType type = null;
            if (ch == 'r') {
                nextStates.set(14);
            }
            return type;
        }

        static private TokenType NFA_SNBT_7(int ch, BitSet nextStates, EnumSet<TokenType> validTypes) {
            TokenType type = null;
            if ((ch == '+') || (ch == '-')) {
                nextStates.set(18);
            } else if (ch >= '1' && ch <= '9') {
                nextStates.set(24);
            }
            return type;
        }

        static private TokenType NFA_SNBT_8(int ch, BitSet nextStates, EnumSet<TokenType> validTypes) {
            TokenType type = null;
            if (ch >= '0' && ch <= '9') {
                nextStates.set(27);
            }
            return type;
        }

        static private TokenType NFA_SNBT_9(int ch, BitSet nextStates, EnumSet<TokenType> validTypes) {
            TokenType type = null;
            if (ch == '\t') {
                nextStates.set(9);
                if (validTypes.contains(WHITESPACE))
                    type = WHITESPACE;
            } else if (ch == '\n') {
                nextStates.set(9);
                if (validTypes.contains(WHITESPACE))
                    type = WHITESPACE;
            } else if (ch == '\r') {
                nextStates.set(9);
                if (validTypes.contains(WHITESPACE))
                    type = WHITESPACE;
            } else if (ch == ' ') {
                nextStates.set(9);
                if (validTypes.contains(WHITESPACE))
                    type = WHITESPACE;
            }
            return type;
        }

        static private TokenType NFA_SNBT_10(int ch, BitSet nextStates, EnumSet<TokenType> validTypes) {
            TokenType type = null;
            if (ch == '.') {
                nextStates.set(8);
            } else if (ch >= '0' && ch <= '9') {
                nextStates.set(10);
            } else if ((ch == 'E') || (ch == 'e')) {
                nextStates.set(7);
            } else if (ch == 'D') {
                if (validTypes.contains(DOUBLE))
                    type = DOUBLE;
            } else if (ch == 'd') {
                if (validTypes.contains(DOUBLE))
                    type = DOUBLE;
            }
            return type;
        }

        static private TokenType NFA_SNBT_11(int ch, BitSet nextStates, EnumSet<TokenType> validTypes) {
            TokenType type = null;
            if (ch == 'B') {
                if (validTypes.contains(BYTE))
                    type = BYTE;
            } else if (ch == 'b') {
                if (validTypes.contains(BYTE))
                    type = BYTE;
            }
            return type;
        }

        static private TokenType NFA_SNBT_12(int ch, BitSet nextStates, EnumSet<TokenType> validTypes) {
            TokenType type = null;
            if ((ch >= 0x0 && ch <= '!') || (ch >= '#')) {
                nextStates.set(12);
            }
            if (ch == '\\') {
                nextStates.set(13);
            } else if (ch == '"') {
                if (validTypes.contains(STRING))
                    type = STRING;
            }
            return type;
        }

        static private TokenType NFA_SNBT_13(int ch, BitSet nextStates, EnumSet<TokenType> validTypes) {
            TokenType type = null;
            if (ch == '"') {
                nextStates.set(12);
            }
            return type;
        }

        static private TokenType NFA_SNBT_14(int ch, BitSet nextStates, EnumSet<TokenType> validTypes) {
            TokenType type = null;
            if (ch == 'u') {
                nextStates.set(22);
            }
            return type;
        }

        static private TokenType NFA_SNBT_15(int ch, BitSet nextStates, EnumSet<TokenType> validTypes) {
            TokenType type = null;
            if (ch == 's') {
                nextStates.set(35);
            }
            return type;
        }

        static private TokenType NFA_SNBT_16(int ch, BitSet nextStates, EnumSet<TokenType> validTypes) {
            TokenType type = null;
            if (ch == '0') {
                nextStates.set(11);
            } else if (ch >= '1' && ch <= '9') {
                nextStates.set(37);
            }
            return type;
        }

        static private TokenType NFA_SNBT_17(int ch, BitSet nextStates, EnumSet<TokenType> validTypes) {
            TokenType type = null;
            if (ch == 'S') {
                if (validTypes.contains(SHORT))
                    type = SHORT;
            } else if (ch == 's') {
                if (validTypes.contains(SHORT))
                    type = SHORT;
            }
            return type;
        }

        static private TokenType NFA_SNBT_18(int ch, BitSet nextStates, EnumSet<TokenType> validTypes) {
            TokenType type = null;
            if (ch >= '1' && ch <= '9') {
                nextStates.set(24);
            }
            return type;
        }

        static private TokenType NFA_SNBT_19(int ch, BitSet nextStates, EnumSet<TokenType> validTypes) {
            TokenType type = null;
            if (ch == '0') {
                if (validTypes.contains(INTEGER))
                    type = INTEGER;
            } else if (ch >= '1' && ch <= '9') {
                nextStates.set(5);
                if (validTypes.contains(INTEGER))
                    type = INTEGER;
            }
            return type;
        }

        static private TokenType NFA_SNBT_20(int ch, BitSet nextStates, EnumSet<TokenType> validTypes) {
            TokenType type = null;
            if (ch == 'a') {
                nextStates.set(25);
            }
            return type;
        }

        static private TokenType NFA_SNBT_21(int ch, BitSet nextStates, EnumSet<TokenType> validTypes) {
            TokenType type = null;
            if (ch == '0') {
                nextStates.set(33);
            } else if (ch >= '1' && ch <= '9') {
                nextStates.set(3);
            }
            return type;
        }

        static private TokenType NFA_SNBT_22(int ch, BitSet nextStates, EnumSet<TokenType> validTypes) {
            TokenType type = null;
            if (ch == 'e') {
                if (validTypes.contains(BOOLEAN))
                    type = BOOLEAN;
            }
            return type;
        }

        static private TokenType NFA_SNBT_23(int ch, BitSet nextStates, EnumSet<TokenType> validTypes) {
            TokenType type = null;
            if (ch >= '0' && ch <= '9') {
                nextStates.set(23);
            } else if (ch == 'S') {
                if (validTypes.contains(SHORT))
                    type = SHORT;
            } else if (ch == 's') {
                if (validTypes.contains(SHORT))
                    type = SHORT;
            }
            return type;
        }

        static private TokenType NFA_SNBT_24(int ch, BitSet nextStates, EnumSet<TokenType> validTypes) {
            TokenType type = null;
            if (ch >= '1' && ch <= '9') {
                nextStates.set(24);
            } else if (ch == 'D') {
                if (validTypes.contains(DOUBLE))
                    type = DOUBLE;
            } else if (ch == 'd') {
                if (validTypes.contains(DOUBLE))
                    type = DOUBLE;
            }
            return type;
        }

        static private TokenType NFA_SNBT_25(int ch, BitSet nextStates, EnumSet<TokenType> validTypes) {
            TokenType type = null;
            if (ch == 'l') {
                nextStates.set(15);
            }
            return type;
        }

        static private TokenType NFA_SNBT_26(int ch, BitSet nextStates, EnumSet<TokenType> validTypes) {
            TokenType type = null;
            if (ch == '.') {
                nextStates.set(4);
            } else if ((ch == 'E') || (ch == 'e')) {
                nextStates.set(30);
            } else if (ch == 'F') {
                if (validTypes.contains(FLOAT))
                    type = FLOAT;
            } else if (ch == 'f') {
                if (validTypes.contains(FLOAT))
                    type = FLOAT;
            }
            return type;
        }

        static private TokenType NFA_SNBT_27(int ch, BitSet nextStates, EnumSet<TokenType> validTypes) {
            TokenType type = null;
            if (ch >= '0' && ch <= '9') {
                nextStates.set(27);
            } else if ((ch == 'E') || (ch == 'e')) {
                nextStates.set(7);
            } else if (ch == 'D') {
                if (validTypes.contains(DOUBLE))
                    type = DOUBLE;
            } else if (ch == 'd') {
                if (validTypes.contains(DOUBLE))
                    type = DOUBLE;
            }
            return type;
        }

        static private TokenType NFA_SNBT_28(int ch, BitSet nextStates, EnumSet<TokenType> validTypes) {
            TokenType type = null;
            if (ch == '0') {
                nextStates.set(17);
            } else if (ch >= '1' && ch <= '9') {
                nextStates.set(23);
            }
            return type;
        }

        static private TokenType NFA_SNBT_29(int ch, BitSet nextStates, EnumSet<TokenType> validTypes) {
            TokenType type = null;
            if (ch >= '0' && ch <= '9') {
                nextStates.set(29);
            } else if ((ch == 'E') || (ch == 'e')) {
                nextStates.set(30);
            } else if (ch == 'F') {
                if (validTypes.contains(FLOAT))
                    type = FLOAT;
            } else if (ch == 'f') {
                if (validTypes.contains(FLOAT))
                    type = FLOAT;
            }
            return type;
        }

        static private TokenType NFA_SNBT_30(int ch, BitSet nextStates, EnumSet<TokenType> validTypes) {
            TokenType type = null;
            if ((ch == '+') || (ch == '-')) {
                nextStates.set(2);
            } else if (ch >= '1' && ch <= '9') {
                nextStates.set(39);
            }
            return type;
        }

        static private TokenType NFA_SNBT_31(int ch, BitSet nextStates, EnumSet<TokenType> validTypes) {
            TokenType type = null;
            if (ch == '0') {
                nextStates.set(32);
            } else if (ch >= '1' && ch <= '9') {
                nextStates.set(10);
            }
            return type;
        }

        static private TokenType NFA_SNBT_32(int ch, BitSet nextStates, EnumSet<TokenType> validTypes) {
            TokenType type = null;
            if (ch == '.') {
                nextStates.set(8);
            } else if ((ch == 'E') || (ch == 'e')) {
                nextStates.set(7);
            } else if (ch == 'D') {
                if (validTypes.contains(DOUBLE))
                    type = DOUBLE;
            } else if (ch == 'd') {
                if (validTypes.contains(DOUBLE))
                    type = DOUBLE;
            }
            return type;
        }

        static private TokenType NFA_SNBT_33(int ch, BitSet nextStates, EnumSet<TokenType> validTypes) {
            TokenType type = null;
            if (ch == 'L') {
                if (validTypes.contains(LONG))
                    type = LONG;
            } else if (ch == 'l') {
                if (validTypes.contains(LONG))
                    type = LONG;
            }
            return type;
        }

        static private TokenType NFA_SNBT_34(int ch, BitSet nextStates, EnumSet<TokenType> validTypes) {
            TokenType type = null;
            if (ch == '.') {
                nextStates.set(4);
            } else if (ch >= '0' && ch <= '9') {
                nextStates.set(34);
            } else if ((ch == 'E') || (ch == 'e')) {
                nextStates.set(30);
            } else if (ch == 'F') {
                if (validTypes.contains(FLOAT))
                    type = FLOAT;
            } else if (ch == 'f') {
                if (validTypes.contains(FLOAT))
                    type = FLOAT;
            }
            return type;
        }

        static private TokenType NFA_SNBT_35(int ch, BitSet nextStates, EnumSet<TokenType> validTypes) {
            TokenType type = null;
            if (ch == 'e') {
                if (validTypes.contains(BOOLEAN))
                    type = BOOLEAN;
            }
            return type;
        }

        static private TokenType NFA_SNBT_36(int ch, BitSet nextStates, EnumSet<TokenType> validTypes) {
            TokenType type = null;
            if (ch == '\'') {
                nextStates.set(1);
            }
            return type;
        }

        static private TokenType NFA_SNBT_37(int ch, BitSet nextStates, EnumSet<TokenType> validTypes) {
            TokenType type = null;
            if (ch >= '0' && ch <= '9') {
                nextStates.set(37);
            } else if (ch == 'B') {
                if (validTypes.contains(BYTE))
                    type = BYTE;
            } else if (ch == 'b') {
                if (validTypes.contains(BYTE))
                    type = BYTE;
            }
            return type;
        }

        static private TokenType NFA_SNBT_38(int ch, BitSet nextStates, EnumSet<TokenType> validTypes) {
            TokenType type = null;
            if (ch == '0') {
                nextStates.set(26);
            } else if (ch >= '1' && ch <= '9') {
                nextStates.set(34);
            }
            return type;
        }

        static private TokenType NFA_SNBT_39(int ch, BitSet nextStates, EnumSet<TokenType> validTypes) {
            TokenType type = null;
            if (ch >= '1' && ch <= '9') {
                nextStates.set(39);
            } else if (ch == 'F') {
                if (validTypes.contains(FLOAT))
                    type = FLOAT;
            } else if (ch == 'f') {
                if (validTypes.contains(FLOAT))
                    type = FLOAT;
            }
            return type;
        }

        static private void NFA_FUNCTIONS_init() {
            nfaFunctions = new NfaFunction[]{SNBT::NFA_SNBT_0, SNBT::NFA_SNBT_1, SNBT::NFA_SNBT_2, SNBT::NFA_SNBT_3, SNBT::NFA_SNBT_4, SNBT::NFA_SNBT_5, SNBT::NFA_SNBT_6, SNBT::NFA_SNBT_7, SNBT::NFA_SNBT_8, SNBT::NFA_SNBT_9, SNBT::NFA_SNBT_10, SNBT::NFA_SNBT_11, SNBT::NFA_SNBT_12, SNBT::NFA_SNBT_13, SNBT::NFA_SNBT_14, SNBT::NFA_SNBT_15, SNBT::NFA_SNBT_16, SNBT::NFA_SNBT_17, SNBT::NFA_SNBT_18, SNBT::NFA_SNBT_19, SNBT::NFA_SNBT_20, SNBT::NFA_SNBT_21, SNBT::NFA_SNBT_22, SNBT::NFA_SNBT_23, SNBT::NFA_SNBT_24, SNBT::NFA_SNBT_25, SNBT::NFA_SNBT_26, SNBT::NFA_SNBT_27, SNBT::NFA_SNBT_28, SNBT::NFA_SNBT_29, SNBT::NFA_SNBT_30, SNBT::NFA_SNBT_31, SNBT::NFA_SNBT_32, SNBT::NFA_SNBT_33, SNBT::NFA_SNBT_34, SNBT::NFA_SNBT_35, SNBT::NFA_SNBT_36, SNBT::NFA_SNBT_37, SNBT::NFA_SNBT_38, SNBT::NFA_SNBT_39};
        }

    }

}


