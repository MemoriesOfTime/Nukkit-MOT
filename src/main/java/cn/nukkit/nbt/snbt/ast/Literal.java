package cn.nukkit.nbt.snbt.ast;

import cn.nukkit.nbt.snbt.SNBTLexer;
import cn.nukkit.nbt.snbt.Token;


public class Literal extends Token {

    public Literal(TokenType type, SNBTLexer tokenSource, int beginOffset, int endOffset) {
        super(type, tokenSource, beginOffset, endOffset);
    }

}


