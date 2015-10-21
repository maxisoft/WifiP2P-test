package android.dristributed.wifip2p.model;

import java.util.Arrays;

public class Board implements Const {
    private final byte cells[];

    public Board() {
        cells = new byte[ROW_NBR * LINE_NBR];
        Arrays.fill(cells, EMPTY_CELL);
    }

    public byte getCellAt(int line, int row) {
        return getCellAt(line * ROW_NBR + row);
    }

    public byte getCellAt(int position) {
        return cells[position];
    }

    public void setCellAt(int line, int row, byte cellValue) {
        setCellAt(line * ROW_NBR + row, cellValue);
    }

    public void setCellAt(int position, byte cellValue) {
        cells[position] = cellValue;
    }
}
