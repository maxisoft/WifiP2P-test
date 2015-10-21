package android.dristributed.wifip2p.gameproto;

import android.dristributed.wifip2p.CommBase;
import android.dristributed.wifip2p.model.Game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Created by maxime on 21/10/2015.
 */
public class GameComm {
    private final CommBase commBase;
    private List<UUID> uuids;
    private Game game;
    private UUID myUuid;
    private final int playerNbr;

    public GameComm(CommBase commBase, int playerNbr) {
        this.commBase = commBase;
        myUuid = UUID.randomUUID();
        this.playerNbr = playerNbr;
        uuids = new ArrayList<>(playerNbr);
        addPlayer(myUuid);
    }

    public boolean addPlayer(UUID uuid){
        if (uuids.size() == playerNbr - 1){
            return false;
        }
        int index = Collections.binarySearch(uuids, uuid);
        if (index < 0) {
            uuids.add(index, uuid);
            return true;
        }
        return false;
    }
}
