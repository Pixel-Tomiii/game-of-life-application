package gui;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.*;

import java.util.ArrayList;

public class MainWindow {

    public final Stage window;

    public static int width;
    public static int height;

    public static int gridWidth;
    public static int gridHeight;

    public boolean isPlaying = false;
    public final long interval = 25000000;
    public long last = System.nanoTime();

    public int cellSize = 16; // Stores the size of a cell in pixels.

    public Cell[][] grid;
    public Canvas canvas;
    public int X_PADDING;
    public int Y_PADDING;
    public int CANVAS_WIDTH;
    public int CANVAS_HEIGHT;

    public int generation = 0;
    public Text generationNumber;

    public Pane display = new Pane();
    public Scene scene = new Scene(display);

    public AnimationTimer tick;
    public ArrayList<Cell> aliveCells = new ArrayList<Cell>(0);


    public MainWindow(Stage window) {
        this.window = window;

        width = (int) Screen.getPrimary().getBounds().getWidth();
        height = (int) Screen.getPrimary().getBounds().getHeight();

        scene.getStylesheets().add("styles.css");

        // Setting up the canvas's dimensions.
        final int modifiedHeight = (int) (0.92 * height); // Stores the valid height of the canvas (excludes bottom bar).
        final int base = 10;

        // Setting the x and y bounds for the grid. The - 2 allows for padding around the edges.
        gridWidth = (width / cellSize) - 2; // The number of cells in the grid (horizontal).
        gridHeight = (modifiedHeight / cellSize) - 2; // The number of cells in the grid (vertical).

        grid = new Cell[gridHeight][gridWidth];

        // Canvas Dimensions.
        CANVAS_WIDTH = gridWidth * cellSize;
        CANVAS_HEIGHT = gridHeight * cellSize;

        X_PADDING = (width / 2) - (CANVAS_WIDTH / 2); // Padding for around the edges of the canvas.
        Y_PADDING = (modifiedHeight / 2) - (CANVAS_HEIGHT / 2); // Padding for around the edges of the canvas.

        for (int y = 0; y < gridHeight; y++) {
            for (int x = 0; x < gridWidth; x++) {
                grid[y][x] = new Cell(x, y);
            }
        }

    }


    /* -----------------------------------------------------------------------------------------------------------------
    Creates the menu for the "game of life". It creates the buttons required for increasing/decreasing the size of the
    grid and it also displays the grid area in the centre of the screen. Users can place "cells", after everything has
    been loaded. This will be handled by a separate thread.
    ----------------------------------------------------------------------------------------------------------------- */
    public void loadMenu() {

        setupWindow();

        ToggleGroup toggleButtons = new ToggleGroup(); // Will house the play, pause and stop buttons.

        // Creating the canvas.
        canvas = new Canvas(0, 0);
        canvas.setWidth(width);
        canvas.setHeight(height);
        canvas.getGraphicsContext2D().setFill(Color.BLACK);
        canvas.getGraphicsContext2D().fillRect(X_PADDING - 1, Y_PADDING - 1, CANVAS_WIDTH, CANVAS_HEIGHT);

        render(canvas);


        // Buttons.

        int size = (int) (0.08 * height); // How big the buttons will be.
        int middle = width / 2; // The middle of the screen.
        int y = (int) height - (int) (size); // The y level of each button.

        Button exit = createButton("exit", size, size, X_PADDING, y); // Exits the application.
        exit.setOnAction(ExitEvent -> Platform.exit());
        render(exit);

        Button clear = createButton("clear", size, size, width - size - X_PADDING, y); // Resets the board.
        clear.setOnAction(ClearEvent -> reset());
        render(clear);

        Button increaseSize = createButton("plus", size / 2, size / 2, middle + (int)(1.5 * size), y + (size / 4));
        increaseSize.setOnAction(increaseSizeEvent -> changeSize(1));
        render(increaseSize);

        Button decreaseSize = createButton("minus", size / 2, size / 2, middle + (int)(2 * size), y + (size / 4));
        decreaseSize.setOnAction(decreaseSizeEvent -> changeSize(-1));
        render(decreaseSize);

        generationNumber = new Text();
        generationNumber.setLayoutX(0.15 * width);
        generationNumber.setLayoutY(y + (size / 1.5));
        generationNumber.setText("Generation: 0");
        generationNumber.setFont(new Font(0.05 * height));
        generationNumber.setFill(Color.web("#a0ff46"));
        render(generationNumber);


        // Toggle Buttons.

        ToggleButton play = createToggleButton("play", size, size, middle - (int) (1.5 * size), y, toggleButtons); // Starts the game of life.
        play.setOnAction(startGameEvent -> start());

        ToggleButton pause = createToggleButton("pause", size, size, middle - (int) (0.5 * size), y, toggleButtons); // Pauses the game of life.
        pause.setOnAction(pauseGameEvent -> pause());

        ToggleButton stop = createToggleButton("stop", size, size, middle + (int) (0.5 * size), y, toggleButtons); // Stops it completely.
        stop.setOnAction(stopGameEvent -> stop());

        render(new Group(stop, play, pause));

        setupMouseListener();
        setupTick();
    }


    /* -----------------------------------------------------------------------------------------------------------------
    Sets up the mouse listener so that the user can draw live cells/remove live cells from the grid.
    TO DO ----
    Currently only supports clicking to draw/remove cells and does not support mouse dragging.
    ----------------------------------------------------------------------------------------------------------------- */
    public void setupMouseListener() {
        scene.setOnMousePressed(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {

                if (!isPlaying) {
                    int mouseX = (int) mouseEvent.getX();
                    int mouseY = (int) mouseEvent.getY();

                    updateSquare(mouseX, mouseY);
                }
            }
        });

        scene.setOnMouseDragged(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {

                if (!isPlaying) {
                    int mouseX = (int) mouseEvent.getX();
                    int mouseY = (int) mouseEvent.getY();

                    updateSquare(mouseX, mouseY);
                }
            }
        });

        scene.setOnMouseReleased(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {

                for (int y = 0; y < gridHeight; y++) {
                    for (int x = 0; x < gridWidth; x++) {
                        grid[y][x].hasUpdated = false;
                    }
                }
            }
        });
    }


    /* -----------------------------------------------------------------------------------------------------------------
    Find the square based on the mouse position and then updates the colour. If the colour is green, set it to black. If
    it's black, set it to green.
    ----------------------------------------------------------------------------------------------------------------- */
    public void updateSquare(int x, int y) {

        int gridXPosition = -1;
        int gridYPosition = -1;

        // Only calculates the padding if the mouse is on the canvas (right and bottom sides do not matter).
        if (x >= X_PADDING) {
            gridXPosition = (x - X_PADDING) / cellSize;
        }

        if (y >= Y_PADDING) {
            gridYPosition = (y - Y_PADDING) / cellSize;
        }

        // Determines if the mouse is on the canvas grid and then does stuff with the cell.
        if (0 <= gridXPosition && gridXPosition < gridWidth) {
            if (0 <= gridYPosition && gridYPosition < gridHeight) {
                if (!grid[gridYPosition][gridXPosition].hasUpdated) {

                    if (grid[gridYPosition][gridXPosition].id == 0) {
                        grid[gridYPosition][gridXPosition].id = 1;
                        aliveCells.add(grid[gridYPosition][gridXPosition]);
                        drawCell(1, gridXPosition, gridYPosition);

                    } else if (grid[gridYPosition][gridXPosition].id == 1) {
                        grid[gridYPosition][gridXPosition].id = 2;
                        removeDeadCells(aliveCells);
                        drawCell(2, gridXPosition, gridYPosition);

                    } else {
                        grid[gridYPosition][gridXPosition].id = 0;
                        drawCell(0, gridXPosition, gridYPosition);
                    }
                    grid[gridYPosition][gridXPosition].hasUpdated = true;
                }
            }
        }
    }


    /* -----------------------------------------------------------------------------------------------------------------
    Creates the window, sets the window to full screen mode and disables the exit full screen key.
    It sets the display Pane to the root of the scene so that it can be updated whenever.
    ----------------------------------------------------------------------------------------------------------------- */
    public void setupWindow() {

        scene.setRoot(display);
        window.setScene(scene);
        window.setFullScreen(true);
        window.setFullScreenExitKeyCombination(KeyCombination.NO_MATCH);
        window.show();
    }


    /* -----------------------------------------------------------------------------------------------------------------
    Creates a button with a given width and height an then sets the position of the button. It also gives the button an
    id so that it can be styled with the styles sheet.
    ----------------------------------------------------------------------------------------------------------------- */
    public Button createButton(String id, int width, int height, int posx, int posy) {

        Button button = new Button();

        button.setId(id);
        button.setPrefSize(width, height);
        button.setLayoutX(posx);
        button.setLayoutY(posy - 1);

        return button;
    }


    /* -----------------------------------------------------------------------------------------------------------------
    Creates a *toggle* button with a given width and height an then sets the position of the button. It also gives the
    button an id so that it can be styled with the styles sheet.
    ----------------------------------------------------------------------------------------------------------------- */
    public ToggleButton createToggleButton(String id, int width, int height, int posx, int posy, ToggleGroup toggleButtons) {

        ToggleButton button = new ToggleButton();

        button.setId(id);
        button.setPrefSize(width, height);
        button.setLayoutX(posx);
        button.setLayoutY(posy);

        button.setToggleGroup(toggleButtons);
        return button;
    }


    /* -----------------------------------------------------------------------------------------------------------------
    Runs whenever the + or - button is pressed. It either increments or decrements the grid's width or height.
    ----------------------------------------------------------------------------------------------------------------- */
    public void changeSize(int size) {

        if (cellSize + size >= 8 && cellSize + size <= 32) {
            cellSize += size;
            reset();
        }
    }


    /* -----------------------------------------------------------------------------------------------------------------
    Renders and objects but adding them to the display pane.
    ----------------------------------------------------------------------------------------------------------------- */
    public void render(Object object) {
        display.getChildren().add((Node) object);
    }


    /* -----------------------------------------------------------------------------------------------------------------
    Draws a cell to the grid. Cells are either alive or dead.
    ----------------------------------------------------------------------------------------------------------------- */
    public void drawCell(int state, int x, int y) {

        // Colouring the cell green if it's alive.
        if (state == 1) {
            canvas.getGraphicsContext2D().setFill(Color.web("#a0ff46"));
        }

        // Colouring the cell dark grey if it's dead.
        else if (state == 2) {
            canvas.getGraphicsContext2D().setFill(Color.web("#222222"));
        }

        // Colouring black.
        else {
            canvas.getGraphicsContext2D().setFill(Color.BLACK);
        }

        // The absolute position of the cell's image.
        int xPos = X_PADDING + (cellSize * x);
        int yPos = Y_PADDING + (cellSize * y);

        // Draw the cell.
        canvas.getGraphicsContext2D().fillRect(xPos, yPos, cellSize - 2, cellSize - 2);
    }


    /* -----------------------------------------------------------------------------------------------------------------
    Resets the entire grid.
    ----------------------------------------------------------------------------------------------------------------- */
    public void reset(){

        // Setting up the canvas's dimensions.
        final int modifiedHeight = (int) (0.92 * height); // Stores the valid height of the canvas (excludes bottom bar).
        final int base = 10;

        // Setting the x and y bounds for the grid. The - 2 allows for padding around the edges.
        gridWidth = (width / cellSize) - 2; // The number of cells in the grid (horizontal).
        gridHeight = (modifiedHeight / cellSize) - 2; // The number of cells in the grid (vertical).

        grid = new Cell[gridHeight][gridWidth];

        // Canvas Dimensions.
        CANVAS_WIDTH = gridWidth * cellSize;
        CANVAS_HEIGHT = gridHeight * cellSize;

        X_PADDING = (width / 2) - (CANVAS_WIDTH / 2); // Padding for around the edges of the canvas.
        Y_PADDING = (modifiedHeight / 2) - (CANVAS_HEIGHT / 2); // Padding for around the edges of the canvas.

        generation = 0;
        generationNumber.setText("Generation: 0");

        for (int y = 0; y < gridHeight; y++) {
            for (int x = 0; x < gridWidth; x++) {
                grid[y][x] = new Cell(x, y);
            }
        }

        aliveCells = new ArrayList<Cell>(0);

        // Resetting the canvas.
        canvas.getGraphicsContext2D().setFill(Color.web("#3d3d3d"));
        canvas.getGraphicsContext2D().fillRect(0, 0, width, height);
        canvas.getGraphicsContext2D().setFill(Color.BLACK);
        canvas.getGraphicsContext2D().fillRect(X_PADDING - 1, Y_PADDING - 1, CANVAS_WIDTH, CANVAS_HEIGHT);
    }


    /* -----------------------------------------------------------------------------------------------------------------
    Sets up the game of life loop which will do all the required things for the game of life. It will run all the time
    but it will only come into effect when the isPlaying variable is true. If it is not true, it does nothing.
    ----------------------------------------------------------------------------------------------------------------- */
    public void setupTick() {

        tick = new AnimationTimer() {
            @Override
            public void handle(long now) {

                if (isPlaying && now > last + interval) {

                    ArrayList<Cell> toUpdate = getCellsToCheck();
                    aliveCells = update(toUpdate);

                    removeDeadCells(toUpdate);

                    last = now;
                    generation++;
                    generationNumber.setText("Generation: " + Integer.toString(generation));

                }
            }
        };
        tick.start();
    }

    public void start() {
        isPlaying = true;
    }

    public void pause() {
        isPlaying = false;
    }

    public void stop() {
        isPlaying = false;
        reset();
    }


    /* -----------------------------------------------------------------------------------------------------------------
    Fetches every cell around the alive cells and adds it to a list.
    ----------------------------------------------------------------------------------------------------------------- */
    public ArrayList<Cell> getCellsToCheck() {

        ArrayList<Cell> toCheck = new ArrayList<Cell>(0);

        for (Cell cell : aliveCells) {

            int x = cell.x;
            int y = cell.y;

            if (!contains(toCheck, x, y)) {
                Cell toAdd = new Cell(x, y);
                toAdd.id = 1;

                toCheck.add(toAdd);
            }

            else {
                // Looping through the current cells.
                for (int i = 0; i < toCheck.size(); i ++) {

                    // Once found the duplicate, replace with current cell.
                    if (toCheck.get(i).x == x && toCheck.get(i).y == y) {
                        Cell toUpdate = new Cell(x, y);
                        toUpdate.id = 1;

                        toCheck.set(i, toUpdate);
                        break;
                    }
                }
            }



            // The upper row of cells.
            if (y - 1 >= 0) {

                // Top left.
                if (x - 1 >= 0) {
                    if (!contains(toCheck, x - 1, y - 1)) {
                        toCheck.add(new Cell(x - 1, y - 1));
                    }
                }


                // Top middle.
                if (!contains(toCheck, x, y - 1)) {
                    toCheck.add(new Cell(x, y - 1));
                }


                // Top right
                if (x + 1 < gridWidth) {
                    if (!contains(toCheck, x + 1, y - 1)) {
                        toCheck.add(new Cell(x + 1, y - 1));
                    }
                }
            }

            // The middle row of cells.
            // Left
            if (x - 1 >= 0) {
                if (!contains(toCheck, x - 1, y)) {
                    toCheck.add(new Cell(x - 1, y));
                }
            }

            // Right.
            if (x + 1 < gridWidth) {
                if (!contains(toCheck, x + 1, y)) {
                    toCheck.add(new Cell(x + 1, y));
                }
            }


            // The bottom row of cells.
            if (y + 1 < gridHeight) {

                // Bottom left.
                if (x - 1 >= 0) {
                    if (!contains(toCheck, x - 1, y + 1)) {
                        toCheck.add(new Cell(x - 1, y + 1));
                    }
                }


                // Bottom middle.
                if (!contains(toCheck, x, y + 1)) {
                    toCheck.add(new Cell(x, y + 1));
                }


                // Bottom right
                if (x + 1 < gridWidth) {
                    if (!contains(toCheck, x + 1, y + 1)) {
                        toCheck.add(new Cell(x + 1, y + 1));
                    }
                }
            }
        }

        return toCheck;
    }

    /* -----------------------------------------------------------------------------------------------------------------
    Searches the given list to see if the cell is already in the array.
    ----------------------------------------------------------------------------------------------------------------- */
    public boolean contains(ArrayList<Cell> toCheck, int x, int y) {

        for (Cell cellToCheck : toCheck) {
            if (cellToCheck.x == x && cellToCheck.y == y) {
                return true;
            }
        }

        return false;
    }


    /* -----------------------------------------------------------------------------------------------------------------
    Fetches the number of alive cells around the current cell.
    ----------------------------------------------------------------------------------------------------------------- */
    public int getSurroundingLiveCells(int x, int y) {

        int total = 0;

        // The upper row of cells.
        if (y - 1 >= 0) {

            // Top left.
            if (x - 1 >= 0) {
                if (grid[y - 1][x - 1].id == 1) {
                    total++;
                }
            }

            // Top middle.
            if (grid[y - 1][x].id == 1) {
                total++;
            }

            // Top right
            if (x + 1 < gridWidth) {
                if (grid[y - 1][x + 1].id == 1) {
                    total++;
                }
            }
        }

        // The middle row of cells.
        // Left
        if (x - 1 >= 0) {
            if (grid[y][x - 1].id == 1) {
                total++;
            }
        }

        // Right.
        if (x + 1 < gridWidth) {
            if (grid[y][x + 1].id == 1) {
                total++;
            }
        }

        // The bottom row of cells.
        if (y + 1 < gridHeight) {

            // Bottom left.
            if (x - 1 >= 0) {
                if (grid[y + 1][x - 1].id == 1) {
                    total++;
                }
            }

            // Bottom middle.
            if (grid[y + 1][x].id == 1) {
                total++;
            }

            // Bottom right
            if (x + 1 < gridWidth) {
                if (grid[y + 1][x + 1].id == 1) {
                    total++;
                }
            }
        }

        return total;
    }

    public ArrayList<Cell> update(ArrayList<Cell> cells) {

        // Looping through each cell that needs to be checked.
        for (int i = 0; i < cells.size(); i ++) {

            // Grabbing the current cell.
            Cell current = cells.get(i);
            int liveCells = getSurroundingLiveCells(current.x, current.y);

            // If the cell is already alive.
            if (current.id == 1) {

                // If the cell should die.
                if (liveCells != 2 && liveCells != 3) {
                    current.id = 2;
                    drawCell(2, current.x, current.y);
                    cells.set(i, current);
                }
            }

            // If the cell is dead.
            else {

                // If the cell is dead but there are 3 alive cells around it or if the cell is alive.
                if (liveCells == 3) {
                    current.id = 1;
                    drawCell(1, current.x, current.y);
                    cells.set(i, current);
                }
            }
        }

        return cells;
    }


    public void removeDeadCells(ArrayList<Cell> cells) {

        aliveCells = new ArrayList<Cell>(0);

        for (Cell cell:cells) {
            if (cell.id == 1) {
                aliveCells.add(cell);
                grid[cell.y][cell.x].id = 1;
            }

            else {
                grid[cell.y][cell.x].id = 2;
            }
        }
    }
}