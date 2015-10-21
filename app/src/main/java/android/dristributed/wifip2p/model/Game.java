package android.dristributed.wifip2p.model;


public class Game implements Const {
    private final Board board;
    private final GameLogic gameLogic;
    private final int playerNbr;
    private int currentPlayer;

    public Game(int playerNbr) {
        assert playerNbr > 0 && playerNbr < Byte.MAX_VALUE;
        this.playerNbr = playerNbr;
        board = new Board();
        gameLogic = new GameLogic(board);
    }

    public int dropDisc(byte row) {
        if (gameLogic.dropDisc(row, (byte) currentPlayer)) {
            int tmpPlayer = currentPlayer;
            nextPlayer();
            return tmpPlayer;
        }
        return FORBIDDEN_MOVE;
    }


    private void nextPlayer() {
        currentPlayer = getNextPlayer();
    }

    public int getCurrentPlayer() {
        return currentPlayer;
    }

    public int getNextPlayer() {
        return (currentPlayer + 1) % playerNbr;
    }

    public int getPlayerNbr() {
        return playerNbr;
    }

    public Board getBoard() {
        return board;
    }

    public GameLogic getGameLogic() {
        return gameLogic;
    }
}
