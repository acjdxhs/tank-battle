import java.util.ArrayList;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

/**
 * Separate the game code from some of the boilerplate code.
 * 
 * @author Bill Yu
 */

enum Status {
	Wait, Play, Lost, Win, ToLose, Between;
}

class Game {
	public static final String TITLE = "Fight for Your Home";
	public static Status status = Status.Wait;
	public static int currentLevel = 0;

	private static long toLoseTime = System.nanoTime();
	private static final long LOSE_DELAY = 500 * 1000000;

	public static long deadTime = System.nanoTime();
	private static final long DIE_DELAY = 1 * 1000000000L;

	private static long passLevelTime = System.nanoTime();
	private static final long LEVEL_DELAY = 3 * 1000000000L;

	private int lives;
	private int score;
	private static final int INITIAL_LIVES = 3;

	private Scene myScene;
	private GraphicsContext gc;
	private PlayerTank playerTank;
	private Text livesHud;
	private Text timeHud;
	private int width, height;

	private GameMap map;
	private int numLevels;

	private static final long GAME_TIME = 30 * 1000000000L;
	private static long startTime = System.nanoTime();

	public static ArrayList<Sprite> elements = new ArrayList<Sprite>();

	/**
	 * Returns name of the game.
	 */
	public String getTitle () {
		return TITLE;
	}

	/**
	 * Create the game's scene
	 */
	public Scene init (int width, int height) {
		status = Status.Play;
		this.width = width;
		this.height = height;
		elements = new ArrayList<Sprite>();
		startTime = System.nanoTime();
		lives = INITIAL_LIVES;
		score = 0;
		currentLevel = 0;

		BorderPane root = new BorderPane();
		root.setStyle("-fx-background-color: black;");
		root.setTop(initHud());

		map = new GameMap(width, height);
		map.init(elements);
		numLevels = map.numLevels();

		nextLevel();
		
		gc = initGraphicsContext(root);

		myScene = new Scene(root, width, height + livesHud.getLayoutBounds().getHeight(), Color.BLACK);
		// Respond to input
		myScene.setOnKeyPressed(e -> handleKeyInput(e.getCode()));
		return myScene;
	}

	private Node initHud() {
		livesHud = new Text();
		livesHud.setFont(new Font(20));
		livesHud.setFill(Color.WHITE);
		timeHud = new Text();
		timeHud.setFont(new Font(20));
		timeHud.setFill(Color.WHITE);
		HBox box = new HBox();
		box.getChildren().addAll(livesHud, timeHud);
		box.setSpacing(300);
		BorderPane.setAlignment(box, Pos.CENTER);
		return box;
	}

	/**
	 * game step
	 */
	public void step (double elapsedTime) {

		if (lives <= 0 && status != Status.ToLose) {
			setToLose();
			return;
		}
		if (status == Status.Play && System.nanoTime() - startTime > GAME_TIME) {
			if (currentLevel == numLevels - 1) {
				status = Status.Win;
				return;
			}
			gc.clearRect(0, 0, width, height);
			showScore();
			setBetween();
		}
		if (status == Status.Between) {
			if (System.nanoTime() - passLevelTime > LEVEL_DELAY) {
				currentLevel++;
				nextLevel();
			}
			return;
		}
		if (status == Status.ToLose 
				&& System.nanoTime() - toLoseTime > LOSE_DELAY) {
			status = Status.Lost;
			return;
		}
		
		updateElements(elapsedTime);
		detectCollisions();
		renderElements();
		updateHud();
		map.spawnTank();
	}

	private void nextLevel() {
		elements.removeAll(elements);
		startTime = System.nanoTime();
		map.buildMap();
		playerTank = map.getPlayerTank();
		status = Status.Play;
	}

	private void handleKeyInput (KeyCode code) {
		if (System.nanoTime() - deadTime < DIE_DELAY) {
			return;
		}
		if (code == KeyCode.SPACE) {
			playerTank.fireMissile();
			return;
		}
		switch (code) {
		case RIGHT:
		case D:
			playerTank.direction = Direction.RIGHT;
			break;
		case LEFT:
		case A:
			playerTank.direction = Direction.LEFT;
			break;
		case UP:
		case W:
			playerTank.direction = Direction.UP;
			break;
		case DOWN:
		case S:
			playerTank.direction = Direction.DOWN;
			break;
			//cheats
		case C:
			clearEnemies();
			break;
		case B:
			playerTank.buffImmortal();
			break;
		case L:
			lives++;
		default:
			break;
		}
	}

	private void detectCollisions() {
		for (int i = 0; i < elements.size(); i++) {
			for (int j = i + 1; j < elements.size(); j++) {
				Sprite e1 = elements.get(i);
				Sprite e2 = elements.get(j);
				if (!e1.intersects(e2)) continue;
				if ((e1.BITMASK & e2.BITMASK) != 0) {
					e1.handleCollision(e2);;
					e2.handleCollision(e1);;
				}
			}
		}
	}

	public void clearEnemies() {
		for (int i = 0; i < elements.size(); i++) {
			Sprite e = elements.get(i);
			if (e.BITMASK == ENEMY_TANK_MASK) {
				e.alive = false;
				elements.remove(i);
				i--;
			}
		}
	}

	private void updateHud() {
		updateLivesHud();
		updateTimeHud();
	}
	
	private void updateLivesHud() {
		livesHud.setText(String.format("Lives: %d", lives));
	}
	
	private void updateTimeHud() {
		timeHud.setText("Time: " + (30 - (System.nanoTime() - startTime) / 1000000000L));
	}

	private GraphicsContext initGraphicsContext(BorderPane root) {
		Canvas canvas = new Canvas(width, height);
		canvas.setStyle("-fx-background-color: black;");
		root.setCenter(canvas);
		return canvas.getGraphicsContext2D();
	}

	private void renderElements() {
		elements.sort(null);
		for (Sprite e: elements) {
			e.render(gc);
		}
	}

	private void updateElements(double elapsedTime) {
		gc.clearRect(0, 0, width, height);
		int i = 0;
		while (i < elements.size()) {
			Sprite e = elements.get(i);
			if (e.isAlive()) {
				e.update(elapsedTime);
				i++;
			}
			else {
				elements.remove(i);
				if (e.BITMASK == playerTank.BITMASK) {
					playerTank = map.revivePlayerTank();
					lives--;
					deadTime = System.nanoTime();
				}
				else if (e.BITMASK == Game.ENEMY_TANK_MASK) {
					score += 100;
				}
			}
		}
	}

	public static void setToLose() {
		status = Status.ToLose;
		toLoseTime = System.nanoTime();
	}

	public int getScore() {
		return score;
	}

	private void showScore() {
		gc.setFill(Color.WHITE);
		gc.setFont(new Font(20));
		gc.fillText("Current Score: " + getScore(), width / 2 - 80, height / 2);
	}

	private void setBetween() {
		passLevelTime = System.nanoTime();
		status = Status.Between;
	}

	public static final int PLAYER_TANK_MASK = 1;
	public static final int ENEMY_TANK_MASK = 3;
	public static final int PLAYER_MISSILE_MASK = 6;
	public static final int ENEMY_MISSILE_MASK = 9;
	public static final int STABLE_MASK = 15;
}