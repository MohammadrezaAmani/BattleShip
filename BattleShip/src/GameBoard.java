import java.util.List;
import java.util.ArrayList;
import java.awt.Color;;

public class GameBoard {

    private int GmaeBoardSize;
    private List<Thing> things;

    public GameBoard() {
    }

    public int getGmaeBoardSize() {
        return GmaeBoardSize;
    }

    public void setGmaeBoardSize(int GmaeBoardSize) {
        this.GmaeBoardSize = GmaeBoardSize;
    }

    public List<Thing> getThings() {
        return things;
    }

    public void setThings(List<Thing> things) {
        this.things = things;
    }

    public void addThing(Thing thing) {
        // if its not overlap other things(if not water)
        for (Thing t : things) {
            if (t.getCharacter().equals(" ")) {
                return;
            }
            if (t.getOrientation().equals("H")) {
                if (thing.getOrientation().equals("H")) {
                    if (t.getX() <= thing.getX() && thing.getX() <= t.getX() + t.getWidth()) {
                        if (t.getY() <= thing.getY() && thing.getY() <= t.getY() + t.getHeight()) {
                            return;
                        }
                    }
                } else {
                    if (t.getX() <= thing.getX() && thing.getX() <= t.getX() + t.getWidth()) {
                        if (t.getY() <= thing.getY() && thing.getY() <= t.getY() + t.getHeight()) {
                            return;
                        }
                    }
                }
            } else {
                if (thing.getOrientation().equals("H")) {
                    if (t.getX() <= thing.getX() && thing.getX() <= t.getX() + t.getWidth()) {
                        if (t.getY() <= thing.getY() && thing.getY() <= t.getY() + t.getHeight()) {
                            return;
                        }
                    }
                } else {
                    if (t.getX() <= thing.getX() && thing.getX() <= t.getX() + t.getWidth()) {
                        if (t.getY() <= thing.getY() && thing.getY() <= t.getY() + t.getHeight()) {
                            return;
                        }
                    }
                }
            }
        }
        things.add(thing);
    }

    public void createGameBoard(int GmaeBoardSize) {
        this.GmaeBoardSize = GmaeBoardSize;
        this.things = new ArrayList<>();
        for (int i = 0; i < GmaeBoardSize; i++) {
            for (int j = 0; j < GmaeBoardSize; j++) {
                Thing thing = new Thing("water", 1, 1, "H", i, j, " ", Color.WHITE);
                things.add(thing);
            }
        }
    }
}