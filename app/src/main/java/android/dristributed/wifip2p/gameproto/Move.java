package android.dristributed.wifip2p.gameproto;


public class Move implements CommBaseInterface {
    private final byte player;
    private final byte row;

    public Move(byte row, byte player) {
        this.player = player;
        this.row = row;
    }

    public byte getPlayer() {
        return player;
    }

    public byte getRow() {
        return row;
    }
}
