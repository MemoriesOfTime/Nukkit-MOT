package cn.nukkit.nbt.snbt;


/**
 * Token subclass to represent lexically invalid input
 */
public class InvalidToken extends Token {

    public InvalidToken(SNBTLexer tokenSource, int beginOffset, int endOffset) {
        super(TokenType.INVALID, tokenSource, beginOffset, endOffset);
    }

    @Override
    public String getNormalizedText() {
        return "Lexically Invalid Input:" + getImage();
    }

}


