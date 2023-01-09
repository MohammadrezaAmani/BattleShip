import java.util.ArrayList;
import java.awt.Color;

public class Thing {
    // any ship must have a name and a size
    private String name;
    private int width;
    private int height;
    private String orientation;
    private int x;
    private int y;
    private String character;
    private Color color;
    private boolean is_destoryed;

    // this is the constructor for the ship class
    public Thing(String name, int width, int height, String orientation, int x, int y, String character, Color color) {
        this.name = name;
        this.width = width;
        this.height = height;
        this.orientation = orientation;
        this.x = x;
        this.y = y;
        this.character = character;
        this.color = color;
        this.is_destoryed = false;
    }

    // this is the getter for the name
    public String getName() {
        return name;
    }

    // this is the setter for the name
    public void setName(String name) {
        this.name = name;
    }

    // this is the getter for the width
    public int getWidth() {
        return width;
    }

    // this is the setter for the width
    public void setWidth(int width) {
        this.width = width;
    }

    // this is the getter for the height
    public int getHeight() {
        return height;
    }
    // this is the setter for the height

    public void setHeight(int height) {
        this.height = height;
    }

    // this is the getter for the orientation
    public String getOrientation() {
        return orientation;
    }

    // this is the setter for the orientation
    public void setOrientation(String orientation) {
        this.orientation = orientation;
    }

    // this is the getter for the x
    public int getX() {
        return x;
    }

    // this is the setter for the x
    public void setX(int x) {
        this.x = x;
    }

    // this is the getter for the y
    public int getY() {
        return y;
    }

    // this is the setter for the y
    public void setY(int y) {
        this.y = y;
    }

    // this is the getter for the picture path
    public String getCharacter() {
        return character;
    }

    public void setCharacter(String character) {
        this.character = character;
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }
    public boolean getIs_destoryed() {
        return is_destoryed;
    }
    public void setIs_destoryed(boolean is_destoryed) {
        this.is_destoryed = is_destoryed;
    }

    // this is a method of is ship hit
    public boolean isShipHit(int x, int y) {
        // if the ship is horizontal
        if (orientation.equals("horizontal")) {
            // if the x is between the x and the x + width
            if (x >= this.x && x <= this.x + width) {
                // if the y is between the y and the y + height
                if (y >= this.y && y <= this.y + height) {
                    // return true
                    this.is_destoryed = true;
                    return true;
                }
            }
        }
        // if the ship is vertical
        else if (orientation.equals("vertical")) {
            // if the x is between the x and the x + width
            if (x >= this.x && x <= this.x + width) {
                // if the y is between the y and the y + height
                if (y >= this.y && y <= this.y + height) {
                    // return true
                    this.is_destoryed = true;
                    return true;
                }
            }
        }
        // return false
        return false;
    }

    public boolean isWater(int x, int y) {
        // if the name == water
        if (name.equals("water")) {
            // if the x is between the x and the x + width
            return true;
        } else {
            return false;
        }
    }

    // return all the areas that the ship occupies
    public ArrayList<int[]> getShipArea() {
        // create an array list of int array
        ArrayList<int[]> shipArea = new ArrayList<int[]>();
        // if the ship is horizontal
        if (orientation.equals("horizontal")) {
            // for the width
            for (int i = 0; i < width; i++) {
                // create an int array
                int[] area = new int[2];
                // set the x
                area[0] = x + i;
                // set the y
                area[1] = y;
                // add the area to the ship area
                shipArea.add(area);
            }
        }
        // if the ship is vertical
        else if (orientation.equals("vertical")) {
            // for the height
            for (int i = 0; i < height; i++) {
                // create an int array
                int[] area = new int[2];
                // set the x
                area[0] = x;
                // set the y
                area[1] = y + i;
                // add the area to the ship area
                shipArea.add(area);
            }
        }
        // return the ship area
        return shipArea;
    }

}