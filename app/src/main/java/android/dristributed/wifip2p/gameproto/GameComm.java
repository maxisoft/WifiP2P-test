package android.dristributed.wifip2p.gameproto;

import android.dristributed.wifip2p.CommBase;
import android.dristributed.wifip2p.model.Game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class GameComm implements Runnable {
    private final CommBase commBase;
    private final int playerNbr;
    private final Lock moveLock = new ReentrantLock();
    private final Condition currentPlayerMove = moveLock.newCondition();
    private List<UUID> uuids;
    private Game game;
    private UUID myUuid;
    private boolean tokenOwner = false;
    private volatile boolean done = false;
    private byte lastRowMove = -1;
    private CallBack callBack;

    public GameComm(CommBase commBase, int playerNbr, CallBack callBack) {
        this.commBase = commBase;
        myUuid = commBase.getMyUuid();
        this.playerNbr = playerNbr;
        uuids = new ArrayList<>(playerNbr);
        addPlayer(myUuid);
        game = new Game(playerNbr);
        this.callBack = callBack;
    }

    public boolean ready() {
        return uuids.size() == playerNbr;
    }


    public boolean addPlayer(UUID uuid){
        if (uuids.size() == playerNbr) {
            return false;
        }
        int index = Collections.binarySearch(uuids, uuid);
        if (index < 0) {
            uuids.add(-index - 1, uuid);
            return true;
        }
        return false;
    }

    public boolean isTokenOwner() {
        return tokenOwner;
    }

    @Override
    public void run() {

        while (!ready()) {
            try {
                CommBase.RecvObjWrapper recv = (CommBase.RecvObjWrapper) commBase.recv();
                if (recv.getData() instanceof UuidRegister) {
                    addPlayer(((UuidRegister) recv.getData()).getUuid());
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }

        tokenOwner = uuids.get(0) == myUuid;

        while (!done) {
            if (!tokenOwner) {
                try {
                    CommBase.RecvObjWrapper recv = (CommBase.RecvObjWrapper) commBase.recv();
                    CommBaseInterface data = recv.getData();
                    if (data instanceof Token) {
                        tokenOwner = true;
                    } else if (data instanceof Move) {
                        Move move = (Move) data;
                        moveLock.lock();
                        try {
                            if (!game.getGameLogic().dropDisc(move.getRow(), move.getPlayer())) {
                                //TODO HANDLE CHEAT
                            }else{
                                callBack.onGameMove(move.getRow(), move.getPlayer());
                            }
                        } finally {
                            moveLock.unlock();
                        }

                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else {
                moveLock.lock();
                try {
                    currentPlayerMove.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    moveLock.unlock();
                }
                tokenOwner = false;
                UUID next = nextPlayer();

                commBase.send(next, new Move(lastRowMove, (byte) myPlayerIndex())); // TODO BROADCAST
                commBase.send(next, new Token());
            }
        }

    }

    public int myPlayerIndex() {
        return Collections.binarySearch(uuids, myUuid);
    }

    public UUID nextPlayer() {
        int index = myPlayerIndex();
        return uuids.get((index + 1) % uuids.size());
    }

    public void notifyGameMove(int row) {
        lastRowMove = (byte) row;
        moveLock.lock();
        try {
            currentPlayerMove.signal();
        } finally {
            moveLock.unlock();
        }
    }

    public Game getGame() {
        return game;
    }

    public interface CallBack{
        void onGameMove(byte row, byte player);
    }
}
