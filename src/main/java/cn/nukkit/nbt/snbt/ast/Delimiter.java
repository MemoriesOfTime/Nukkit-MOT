package cn.nukkit.nbt.snbt.ast;

import cn.nukkit.nbt.snbt.SNBTLexer;
import cn.nukkit.nbt.snbt.Token;


public class Delimiter extends Token {

    public Delimiter(TokenType type, SNBTLexer tokenSource, int beginOffset, int endOffset) {
        super(type, tokenSource, beginOffset, endOffset);
    }

}


