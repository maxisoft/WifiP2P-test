package android.dristributed.wifip2p.model;


public class GameLogic implements Const {
    private final Board board;

    public GameLogic(Board board) {
        this.board = board;
    }

    public GameLogic() {
        this(new Board());
    }

    public boolean dropDisc(int row, byte discValue) {
        for (int line = LINE_NBR - 1; line >= 0; line--) {
            if (board.getCellAt(line, row) == EMPTY_CELL) {
                board.setCellAt(line, row, discValue);
                return true;
            }
        }
        return false;
    }

    public Byte whoWin() {
        return null; // TODO
    }

    public Board getBoard() {
        return board;
    }
}
