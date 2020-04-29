package gui;

public class Cell {

    public int id;
    public boolean hasUpdated;

    public int x;
    public int y;

    public Cell(int x, int y) {
        id = 0; // 0 =
        hasUpdated = false;

        this.x = x;
        this.y = y;
    }
}
