
import java.awt.*;

import java.awt.event.KeyListener;
import java.awt.event.KeyEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JFrame;
import javax.swing.JTextArea;
import javax.swing.Timer;

import java.util.Random;


// This is the two player version of the code
// Two player mode was hacked onto the game,
// so expect to see some 'less than optimal' code


public class Tetris extends JTextArea implements KeyListener {
	
	///
	///CONSTANTS 
	///
	
	static private final byte TILE_I = 0;			//handled as a 4x4 shape rotation (https://tetris.wiki/Super_Rotation_System)
	static private final byte TILE_O = 1;			//doesn't get rotated
	static private final byte TILE_T = 2;			//the rest are 3x3 shapes
	static private final byte TILE_J = 3;
	static private final byte TILE_L = 4;
	static private final byte TILE_S = 5;
	static private final byte TILE_Z = 6; 
	static private final byte TILE_BLANK = 7;		//blank tile
	static private final byte TILE_WALL = 8;		//wall tile
	static private final byte TILE_CLEARED = 9;		//set when a tile is cleared, and removed on the next screen update
	
	static private final int GAME_STATE_GAMEOVER = 0;		//game over state
	static private final int GAME_STATE_RUNNING	 = 1;		//normal gameplay
	static private final int GAME_STATE_TITLE 	 = 2;		//title screen
	static private final int GAME_STATE_PAUSED 	 = 3;		//game paused
	static private final int GAME_STATE_ASK_RESTART = 4;	//the 'do you want to restart' screen
	
	static private final int PLAYER_1 = 0;
	static private final int PLAYER_2 = 1;


	static private final int PLAYFIELD_WIDTH = 12;
	static private final int PLAYFIELD_HEIGHT = 18;
		
	static private final int tetrominos[] = {
			
		//An array of 7 tetrominos, either stored as 16 or 9 bits, depending on the tile size
		//Tiles I and O are 4x4,
		//and the others are 3x3
			
		//I would like to lay this out in a more readable fashion, but Java doesn't support 
		//a backslash as a line continuation character
		
		//The tetrominos are stored so bit 15/8 (MSB) is the bottom right, and bit 0 (LSB) is the top left
		//this to simplify the code later on
			
		//0: I
		0b0000000011110000,		//4x4
		
		//1: O
		0b0000000000110011,		//4x4	
	
		//2: T
		0b0000000000111010,		//3x3
	
		//3: J
		0b0000000000111001,		//3x3
	
		//4: L
		0b0000000000111100,		//3x3
	
		//5: S
		0b0000000000011110,		//3x3

		//6: Z
		0b0000000000110011,		//3x3
	};
	
	
	static int scoreLookup[] = {0, 40, 100, 300, 1200};			//score lookup table based on rows cleared (from the NES version)
	
	//delay between the block moving downward based on current level
	//stored in frames (60hz), copied from the NES version of tetris)
	//this gets converted to milliseconds later
	static int speedLookup[] = {48, 43, 38, 33, 28, 23, 18, 13, 8, 6, 5, 5, 5, 4, 4, 4, 3, 3, 3, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1};
	
	
	///
	/// GAME STATE
	///
	
	//in the 2P version, each player gets a state
	class PlayerState {
		private int currentX;					//current tetromino X position
		private int currentY;					//current tetromino Y position
		private int currentShape;				//current tetromino type (0-6)
		private int currentDirection;			//current tetromino direction (in 90 degree increments, e.g. 3 = 270 degrees)
		private int nextShape;					//the next tetromino type to appear
		
		private int dropCounter;				//keeps track of how long the down key is held to give bonus points after the piece lands
		
		private boolean delayTileDropFromTop;	//used to make the tile not move down on its first drop when the playfield is nearly full
				
		private int levelOnRestart = 0;			//the level to go to when you start/restart
		private int score;						//the player's score
		private int top = 21519;				//the player's top	
		private int level;						//starts at zero and goes up every 10 lines
		private int linesCleared;
		private int linesClearedThisLevel;		
		
		private boolean lost = false;			//set to true if you lose (used in 2 player mode)
		
		private boolean displayGuide = true;	//shows you where the piece will land
		
		private int gameSpeed;					//delay in ms between block falling
		
		private Timer timer;					//a timer which controls the downward movement of blocks
	};
	
	//stores both players
	private PlayerState players[] = null;
	
	//overall game state
	private int gameState;						//see the constants above 'TILE_XXX'
	
	//holds a playfield for each player
	private byte playfields[][] = null;			//holds the internal playfield layout

	//RNG is shared
	private Random rng;							//random number generator
	
	//stores if 1 or 2 player mode selected 
	private boolean twoPlayers = false;

	
	///
	/// CODE
	///
	
	private void setSpeed(int player) {
		//speed calcuations same as the NES game
		
		int frames = 0;
		
		if (players[player].level >= 29) { 		//maximum level?
			frames = 1;
			
		} else {
			//otherwise use a lookup table
			frames = speedLookup[players[player].level];
		}
		
		//convert frames to milliseconds
		if (twoPlayers) {
			//NEW FEATURE: in two player mode, you set the speed of your opponent
			player ^= 1;
		}
		players[player].timer.setDelay((int) (1000.0 / 60.0 * ((double) frames)));
	}
	
	public void startGame() {
		//initialise game state
		rng = new Random();					//must be called before 'createNewBlock'
		gameState = GAME_STATE_RUNNING;
		
		//set up both players
		for (int i = 0; i < 2; ++i) {
			players[i].level = players[i].levelOnRestart;
			players[i].nextShape = rng.nextInt(7);		//7 tetrominoes to choose from, must load one in to start with
			players[i].score = 0;
			players[i].dropCounter = 0;
			players[i].linesCleared = 0;
			players[i].linesClearedThisLevel = 0;
			players[i].delayTileDropFromTop = false;
			players[i].lost = false;
			
			createNewBlock(i);
		}
		
		//fill the playfield with spaces, except for the edges, which will be walls
		int index = 0;
		
		for (int y = 0; y < PLAYFIELD_HEIGHT; ++y) {
			for (int x = 0; x < PLAYFIELD_WIDTH; ++x) {		
				//set up both playfields
				playfields[PLAYER_1][index] = (y == PLAYFIELD_HEIGHT - 1 || x == 0 || x == PLAYFIELD_WIDTH - 1) ? TILE_WALL : TILE_BLANK;
				playfields[PLAYER_2][index] = playfields[PLAYER_1][index];
				++index;		//cannot go on the line above as 'index' is used twice and would cause undefined behaviour
			}
		}
		
		//setup the timer at the current speed		
		players[PLAYER_1].timer.start();
		setSpeed(0);

		if (twoPlayers) {
			players[PLAYER_2].timer.start();
			setSpeed(1);
		}
		
		//draw the game
		redrawGame();
	}
	
	public Tetris() {

		//basic textbox setup
		setLineWrap(false);
		setWrapStyleWord(false);
		setEditable(false);
		
		//add the key listener
		addKeyListener(this);
		
		//set it up as 80x25 like VGA text mode
		setColumns(80);
		setRows(25);
		setFont(new Font("Courier", Font.PLAIN, 16));
		setText("");
		
		//get both players ready
		players = new PlayerState[2];
		players[PLAYER_1] = new PlayerState();
		players[PLAYER_2] = new PlayerState();
		
		//I don't think I actually need this, but just in case
		players[PLAYER_1].levelOnRestart = 0;
		players[PLAYER_2].levelOnRestart = 0;

		//allocate playfield memory
		playfields = new byte[2][PLAYFIELD_HEIGHT * PLAYFIELD_WIDTH];	

		//get both timers ready
		players[PLAYER_1].timer = new Timer(players[PLAYER_1].gameSpeed / 2, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (gameState == GAME_STATE_RUNNING) { 
					
					//this is used to delay falling by 1 unit of time when the block first appears
					//when the tiles are high in the playfield. This allows the player to have a bit
					//more time to move it
					
					if (players[PLAYER_1].delayTileDropFromTop) {
						//still clean up cleared rows, otherwise it gets a bit hard to play
						cleanupClearedTiles(PLAYER_1);
						players[PLAYER_1].delayTileDropFromTop = false;

					} else {
						moveBlockDown(PLAYER_1);
					}
					
					redrawGame();
				}
			}
        });
		
		players[PLAYER_2].timer = new Timer(players[PLAYER_2].gameSpeed / 2, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (gameState == GAME_STATE_RUNNING && twoPlayers) {
					
					//same code as above
					if (players[PLAYER_2].delayTileDropFromTop) {
						//still clean up cleared rows, otherwise it gets a bit hard to play
						cleanupClearedTiles(PLAYER_2);
						players[PLAYER_2].delayTileDropFromTop = false;

					} else {
						moveBlockDown(PLAYER_2);
					}
					
					redrawGame();
				}
			}
        });
		
		drawTitleScreen();
	}
	
	
	private void drawTitleScreen() {
		//put in the title screen state
		gameState = GAME_STATE_TITLE;
		
		
		//http://patorjk.com/software/taag/#p=display&f=Varsity&t=TETRIS
		setText("\n\n" + 
				"                              An Illegal Ripoff of\n\n" +							//honesty is the best policy
				"           _________  ________  _________  _______     _____   ______   \n" + 
				"          |  _   _  ||_   __  ||  _   _  ||_   __ \\   |_   _|.' ____ \\  \n" + 
				"          |_/ | | \\_|  | |_ \\_||_/ | | \\_|  | |__) |    | |  | (___ \\_| \n" + 
				"              | |      |  _| _     | |      |  __ /     | |   _.____`.  \n" + 
				"             _| |_    _| |__/ |   _| |_    _| |  \\ \\_  _| |_ | \\____) | \n" + 
				"            |_____|  |________|  |_____|  |____| |___||_____| \\______.' \n" + 
				"                                                              \n\n\n\n");
		
		//alignment
		for (int i = 0; i < 30; ++i) {
			append(" ");
		}
		append("Press ENTER to start\n\n");
		
		//more alignment
		for (int i = 0; i < 18; ++i) {
			append(" ");
		}
		append("or press a number key to start from that level\n\n\n");
		
		//...
		for (int i = 0; i < 16; ++i) {
			append(" ");
		}
		
		if (twoPlayers) {
			append("Two player mode is selected (press space to toggle)\n");
		} else {
			append("One player mode is selected (press space to toggle)\n");
		}
	}
	
	public void keyPressed(KeyEvent e) {
		//after the two player mode was added, this code got quite long...
	
		//handle all keypresses
       switch (e.getKeyCode()) {
       
       case KeyEvent.VK_LEFT:		//left arrow
    	   if (gameState == GAME_STATE_RUNNING && doesPieceFit(PLAYER_1, players[PLAYER_1].currentShape, players[PLAYER_1].currentDirection, players[PLAYER_1].currentX - 1, players[PLAYER_1].currentY)) {
    		   --players[PLAYER_1].currentX;
    	   }
    	   break;
    	   
       case KeyEvent.VK_RIGHT:		//right arrow
    	   if (gameState == GAME_STATE_RUNNING && doesPieceFit(PLAYER_1, players[PLAYER_1].currentShape, players[PLAYER_1].currentDirection, players[PLAYER_1].currentX + 1, players[PLAYER_1].currentY)) {
    		   ++players[PLAYER_1].currentX;
    	   }
    	   break;
    	   
       case KeyEvent.VK_DOWN:		//down arrow
    	   if (gameState == GAME_STATE_RUNNING && doesPieceFit(PLAYER_1, players[PLAYER_1].currentShape, players[PLAYER_1].currentDirection, players[PLAYER_1].currentX, players[PLAYER_1].currentY + 1)) {
    		   ++players[PLAYER_1].dropCounter; 
    		   moveBlockDown(PLAYER_1);
    	   }
    	   
    	   break;
    	   
       case KeyEvent.VK_UP:			//up key, hard fall
    	   while (gameState == GAME_STATE_RUNNING && doesPieceFit(PLAYER_1, players[PLAYER_1].currentShape, players[PLAYER_1].currentDirection, players[PLAYER_1].currentX, players[PLAYER_1].currentY + 1)) {
    		   ++players[PLAYER_1].dropCounter; 
    		   moveBlockDown(PLAYER_1);
    	   }
    	       	   
    	   break;
    	   
       case KeyEvent.VK_N:			//Z key rotates left
    	   if (gameState == GAME_STATE_RUNNING && doesPieceFit(PLAYER_1, players[PLAYER_1].currentShape, players[PLAYER_1].currentDirection - 1, players[PLAYER_1].currentX, players[PLAYER_1].currentY)) {
    		   --players[PLAYER_1].currentDirection;
    	   }
    	   break;
    	   
       case KeyEvent.VK_M:			//X key rotates right
    	   if (gameState == GAME_STATE_RUNNING && doesPieceFit(PLAYER_1, players[PLAYER_1].currentShape, players[PLAYER_1].currentDirection + 1, players[PLAYER_1].currentX, players[PLAYER_1].currentY)) {
    		   ++players[PLAYER_1].currentDirection;
    	   }
    	   break;
       
    	   
    	   
       case KeyEvent.VK_A:		//left arrow P2
    	   if (gameState == GAME_STATE_RUNNING && doesPieceFit(PLAYER_2, players[PLAYER_2].currentShape, players[PLAYER_2].currentDirection, players[PLAYER_2].currentX - 1, players[PLAYER_2].currentY)) {
    		   --players[PLAYER_2].currentX;
    	   }
    	   break;
    	   
       case KeyEvent.VK_D:		//right arrow P2
    	   if (gameState == GAME_STATE_RUNNING && doesPieceFit(PLAYER_2, players[PLAYER_2].currentShape, players[PLAYER_2].currentDirection, players[PLAYER_2].currentX + 1, players[PLAYER_2].currentY)) {
    		   ++players[PLAYER_2].currentX;
    	   }
    	   break;
    	   
       case KeyEvent.VK_S:		//down arrow P2
    	   if (gameState == GAME_STATE_RUNNING && doesPieceFit(PLAYER_2, players[PLAYER_2].currentShape, players[PLAYER_2].currentDirection, players[PLAYER_2].currentX, players[PLAYER_2].currentY + 1)) {
    		   ++players[PLAYER_2].dropCounter; 
    		   moveBlockDown(PLAYER_2);
    	   }
    	   
    	   break;
    	   
       case KeyEvent.VK_W:			//up key, hard fall P2 
    	   while (gameState == GAME_STATE_RUNNING && doesPieceFit(PLAYER_2, players[PLAYER_2].currentShape, players[PLAYER_2].currentDirection, players[PLAYER_2].currentX, players[PLAYER_2].currentY + 1)) {
    		   ++players[PLAYER_2].dropCounter; 
    		   moveBlockDown(PLAYER_2);
    	   }
    	       	   
    	   break;
       
       case KeyEvent.VK_Z:			//Z key rotates left P2 (and P1 in 1 player mode)
									//this was originally the P1 rotation key, so I'll keep it there as an option in 1 player mode
       {   //required to declare variables in a case statement (in C at least...) 
    	   
    	   int player = twoPlayers ? 1 : 0;	//it is so stupid booleans can't be converted to ints
       
    	   if (gameState == GAME_STATE_RUNNING && doesPieceFit(player, players[player].currentShape, players[player].currentDirection - 1, players[player].currentX, players[player].currentY)) {
    		   --players[player].currentDirection;
    	   }
    	   break;
       }
       
       case KeyEvent.VK_X:			//X key rotates right P2 (and P1 in 1 player mode)
       {
    	   int player = twoPlayers ? 1 : 0;	//it is so stupid booleans can't be converted to ints

    	   if (gameState == GAME_STATE_RUNNING && doesPieceFit(player, players[player].currentShape, players[player].currentDirection + 1, players[player].currentX, players[player].currentY)) {
    		   ++players[player].currentDirection;
    	   }
    	   break;
       }
       
       case KeyEvent.VK_ENTER:
    	   //code here is probably readable enough without comments...
    	   if (gameState == GAME_STATE_GAMEOVER || gameState == GAME_STATE_TITLE) {
    		   startGame();

    	   } else if (gameState == GAME_STATE_RUNNING) {
    		   gameState = GAME_STATE_PAUSED;
    		   
    	   } else if (gameState == GAME_STATE_PAUSED) {
    		   gameState = GAME_STATE_RUNNING;
    		   
    	   } else if (gameState == GAME_STATE_ASK_RESTART) {
    		   gameState = GAME_STATE_RUNNING;
    		   
    	   } else {
    		   System.out.printf("Uh oh...\n");
    	   }
    	   
    	   break;
    	   
       case KeyEvent.VK_SPACE:
    	   //this case didn't work correctly in the previous version, it should
    	   //be fixed now
    	   
    	   players[PLAYER_1].levelOnRestart = 0;
    	   players[PLAYER_2].levelOnRestart = 0;
    	   if (gameState == GAME_STATE_TITLE) {    		   
    		   twoPlayers = !twoPlayers;
        	   drawTitleScreen();
    	   }
    	   
    	   break;
    	   
       case KeyEvent.VK_R:
    	   //'r' is for 'restart'
    	   
    	   if (gameState == GAME_STATE_GAMEOVER) {	    	   //restart game from the current level
    		   players[PLAYER_1].levelOnRestart = players[PLAYER_1].level;
    		   players[PLAYER_2].levelOnRestart = players[PLAYER_1].levelOnRestart;
    		   startGame();
    		   
    	   } else if (gameState == GAME_STATE_RUNNING || gameState == GAME_STATE_PAUSED) {		//ask 'do you want to restart'
    		   gameState = GAME_STATE_ASK_RESTART;
    		   
    	   } else if (gameState == GAME_STATE_ASK_RESTART) {		//actually restart
    		   startGame();
    	   }
    	   
    	   break;
    	   
       case KeyEvent.VK_T:
    	   //not explained in game, but 'T' means go to the title screen
    	   
    	   if (gameState == GAME_STATE_GAMEOVER) {	    	   		//go to the title screen if pressed on the GAMEOVER screen
    		   gameState = GAME_STATE_TITLE;
    		   players[PLAYER_1].levelOnRestart = 0;
        	   players[PLAYER_2].levelOnRestart = 0;
    		   drawTitleScreen();
    		   
    	   } else if (gameState == GAME_STATE_ASK_RESTART) {		//go to the title screen if pressed on the ask restart screen
    		   gameState = GAME_STATE_TITLE;
    		   players[PLAYER_1].levelOnRestart = 0;
        	   players[PLAYER_2].levelOnRestart = 0;
    		   drawTitleScreen();
    	   }
    	   
    	   break;
    	   
    	       	   
       default:
       {   //braces to create a scope for the 'num' variable
    	   
    	   //if on the title screen, or game over, you can start on levels 0-9 by pressing its number
    	   //
    	   if (gameState == GAME_STATE_GAMEOVER || gameState == GAME_STATE_TITLE) {
	    	  int num = e.getKeyCode() - KeyEvent.VK_0;		//sketchy stuff
	    	  
	    	  //check if the user typed a number
	    	  if (num >= 0 && num <= 9) {
	    		  
	    		  //add 10 if CTRL or ALT are held
	    		  //or 20 if both
	    		  //(Note to players: you may need to change your key repeat settings in order to move
	    		  // blocks fast enough to play at the faster level)
	    		  if (e.isControlDown()) {
	    			  num += 10;
	    		  }
	    		  if (e.isAltDown()) {
	    			  num += 10;
	    		  }
	    		  
	    		  players[PLAYER_1].levelOnRestart = num;
	    		  players[PLAYER_2].levelOnRestart = num;
	    		  startGame();
	    	  }
	    	  break;
    	   }
       }
    
       }
       
       //redraw the game to show the movement
       redrawGame();
    }
    
	public void keyReleased(KeyEvent e) {
		//reset the length of time the down/up key has been held
		//(only if the piece hasn't yet hit the ground)
		//this is to add extra points depending on how long the key is held
				
		if (e.getKeyCode() == KeyEvent.VK_DOWN || e.getKeyCode() == KeyEvent.VK_UP) {
			if (doesPieceFit(PLAYER_1, players[PLAYER_1].currentShape, players[PLAYER_1].currentDirection, players[PLAYER_1].currentX, players[PLAYER_1].currentY + 1)) {
				players[PLAYER_1].dropCounter = 0;
			}
			
		} else if (e.getKeyCode() == KeyEvent.VK_S || e.getKeyCode() == KeyEvent.VK_W) {
			if (doesPieceFit(PLAYER_2, players[PLAYER_2].currentShape, players[PLAYER_2].currentDirection, players[PLAYER_2].currentX, players[PLAYER_2].currentY + 1)) {
				players[PLAYER_2].dropCounter = 0;
			}
		}
    }
		
	//this must be here to implement KeyListener
	public void keyTyped(KeyEvent e) {
		
    }
	
	private void doScoring (int player, int rowsCleared) {
		//scoring system found here (copied from the NES game)
		//https://www.codewars.com/kata/tetris-series-number-1-scoring-system
		
		players[player].score += scoreLookup[rowsCleared  ] * (players[player].level + 1);
		
		players[player].score += players[player].dropCounter;
		players[player].dropCounter = 0;
		
		//set top score if needed
		if (players[player].score > players[player].top) {
			players[player].top = players[player].score;
		}
		
		//add thre lines cleared
		players[player].linesCleared += rowsCleared;
		
		//add the lines done and move up a level if needed
		//even if you don't start on level 0, you still need the same number
		//of points to reach the next level
		players[player].linesClearedThisLevel += rowsCleared;
		if (players[player].linesClearedThisLevel >= 10 && players[player].linesCleared >= (players[player].level + 1) * 10) {
			players[player].linesClearedThisLevel %= 10;
			++players[player].level;
			setSpeed(player);
		}
	}
	
	private void createNewBlock(int player) {
		//get the next block ready (set X, Y, shape, direction, etc.)
		
		players[player].currentX = PLAYFIELD_WIDTH / 2 - 1;		//seems to be more centered by subtracting 1
		players[player].currentY = 0;
		players[player].currentDirection = 0;
		players[player].currentShape = players[player].nextShape;
		
		//again, for that classic Tetris feel I'm going to 
		//rip off the NES version of Tetris by copying its RNG system
		//https://www.reddit.com/r/Tetris/comments/6o6tvv/what_is_the_block_algorithm_for_classic_tetris/
		
		players[player].nextShape = rng.nextInt(8);		//7 tetrominos plus the 'reroll'
		
		if (players[player].nextShape == players[player].currentShape || players[player].nextShape == 7) {		//if it's the same, or the reroll...
			players[player].nextShape = rng.nextInt(7);							//draw again. This reduces the odds of getting two in a row.
			
			//Same piece 3.5%
			//All others 16%
		}
	}
	
	//D6
	//1A
	
	private void cleanupClearedTiles(int player) {
		//this function properly deletes a row that's been set to 'clear'
		//(this code is a bit questionable)

		//allows us to stop after finding a 4th cleared row
		int numCleared = 0;
		
		//scan all rows, except the last which is the floor
		for (int y = 0; y < PLAYFIELD_HEIGHT - 1; ++y) {
			
			//if the first (non wall) tile is clear on a given row, the entire row will be clear
			if (playfields[player][y * PLAYFIELD_WIDTH + 1] == TILE_CLEARED) {
				//keep track of how many cleared so we can stop at 4
				++numCleared;
				
				//shift the rows forward
				int row = y;
				do {
					//Java doesn't have memcpy()
					for (int x = 1; x < PLAYFIELD_WIDTH - 1; ++x) {
						playfields[player][row * PLAYFIELD_WIDTH + x] = playfields[player][(row - 1) * PLAYFIELD_WIDTH + x];
					}
					--row;
					
				} while (row > 0);
				
				//now clear the top row, except for the walls
				for (int x = 1; x < PLAYFIELD_WIDTH - 1; ++x) {
					playfields[player][x] = TILE_BLANK;
				}
				
				//there can only be four lines cleared, so stop if needed
				if (numCleared == 4) {
					break;
				}
			}
		}
	}
	
	private void moveBlockDown(int player) {
		//remove any half-cleared tiles
		cleanupClearedTiles(player);
		
		//check if the piece can fall
		if (doesPieceFit(player, players[player].currentShape, players[player].currentDirection, players[player].currentX, players[player].currentY + 1)) {
			++players[player].currentY;			//pre increment is better
			
		} else { 				//the piece is stuck
			
			//copy the block onto the playfield
			for (int y = 0; y < 4; ++y) {
				for (int x = 0; x < 4; ++x) {	
					
					//get the index of the piece
					int pieceIndex = getRotatedIndex(x, y, players[player].currentDirection, players[player].currentShape);

					//check if the bit at that index is set (why can't integers be used as booleans?)
					if (((tetrominos[players[player].currentShape] >> pieceIndex) & 1) == 1) {
						playfields[player][(players[player].currentY + y) * PLAYFIELD_WIDTH + players[player].currentX + x] = (byte) players[player].currentShape;
					}
					
				}
			}
			
			//Now clear the lines
			
			//For visual effect, lines will change symbols first, and then disappear
			//To do this, we set the symbols here, and later on (next timer or keyboard interrupt) they will disappear
			
			//find new rows to clear
			int linesCleared = 0;		
			
			//only check lines near the current piece
			for (int y = players[player].currentY; (y < players[player].currentY + 4) && (y < PLAYFIELD_HEIGHT - 1); ++y) { //skip the final row, as it is the floor
				
				//check if clear
				boolean isLineClear = true;
				for (int x = 1; x < PLAYFIELD_WIDTH - 1; ++x) {					//start at 1 and skip the last one because they are walls
					if (playfields[player][y * PLAYFIELD_WIDTH + x] == TILE_BLANK) {
						isLineClear = false;
						break;
					}
				}
				
				//clear it if needed
				if (isLineClear) {
					++linesCleared;
					for (int x = 1; x < PLAYFIELD_WIDTH - 1; ++x) {
						playfields[player][y * PLAYFIELD_WIDTH + x] = TILE_CLEARED;
					}
				}
			}
			
			//give a score based on number of lines cleared
			doScoring(player, linesCleared);
		
			if (!twoPlayers) {		//global delays will not happen in two player mode
				
				/*//delay a bit longer if lines have been cleared
				if (linesCleared != 0) {
					try {Thread.sleep(150);}
					catch(InterruptedException ex) {}
				}*/
				
				//delay so the player has a moment to get ready (e.g. release the down / up key)
				try {Thread.sleep(200);}
				catch(InterruptedException ex) {}
			}
			
			//if the playfield is almost full...
			if (players[player].currentY < 6) {
				//prevent it from falling for 1 unit of time
				//to allow the player to actually move/rotate the piece before it lands
				players[player].delayTileDropFromTop = true;		
																	
			} else {
				players[player].delayTileDropFromTop = false;
			}
			
			//get the next block ready
			createNewBlock(player);
			
			//does the new piece actually fit?
			if (!doesPieceFit(player, players[player].currentShape, players[player].currentDirection, players[player].currentX, players[player].currentY)) {
				gameState = GAME_STATE_GAMEOVER;		//stops the game
				players[player].lost = true;
				return;
			}
		}
	}
	
	private boolean doesPieceFit(int player, int shape, int rotation, int posX, int posY) {
		for (int y = 0; y < 4; ++y) {
			for (int x = 0; x < 4; ++x) {
				//get the index into the piece 
				int pieceIndex = getRotatedIndex(x, y, rotation, players[player].currentShape);
				
				//get the index into the field
				int fieldIndex = (y + posY) * PLAYFIELD_WIDTH + x + posX;
				
				//bounds checking
				if (x + posX >= 0 && x + posX < PLAYFIELD_WIDTH && y + posY >= 0 && y + posY < PLAYFIELD_HEIGHT) {
					
					//check if the bit at that index is set and the playfield location isn't clear
					
					if ((((tetrominos[shape] >> pieceIndex) & 1) == 1) && playfields[player][fieldIndex] != TILE_BLANK) {
						return false;		//no need to continue checking
					}
															
				}
			}
		}
		
		return true;
	}
	
	private void redrawGame() {
		//I apologise about how badly written this function is
		
		//leave the title screen alone...
		if (gameState == GAME_STATE_TITLE) {
			return;
		}
		
		//Allocate enough room for all of the playfield tiles
		//this is done so we can draw the current tile over the top
		//in java this automatically initialises to zero
		char[][][] screenData = new char[2][PLAYFIELD_HEIGHT][PLAYFIELD_WIDTH + 1];		//+ 1 for the null character	

		//again, an index used to save multiplications in the main drawing loop
		//I know it saves hardly any time, but it's still better 
		int index = 0;
		
		//when the game is paused, the tetrominos won't display
		for (int y = 0; y < PLAYFIELD_HEIGHT; ++y) { 
			for (int x = 0; x < PLAYFIELD_WIDTH; ++x, ++index) {		//increments X and index
				
				//draw things for player 1
				if (playfields[PLAYER_1][index] == TILE_WALL) {
					screenData[PLAYER_1][y][x] = '#';	

				} else if (playfields[PLAYER_1][index] == TILE_CLEARED) {
					screenData[PLAYER_1][y][x] = '=';					
				
				} else if (playfields[PLAYER_1][index] == TILE_BLANK){			//if paused or a blank tile
					screenData[PLAYER_1][y][x] = ' ';							

				} else if (gameState != GAME_STATE_PAUSED && gameState != GAME_STATE_ASK_RESTART) {	//don't display when paused
					screenData[PLAYER_1][y][x] = (char) ('A' + playfields[PLAYER_1][index]);		//convert shape number to displayable character
				
				} else {
					screenData[PLAYER_1][y][x] = ' ';			//displays when the game is paused, or if something strange happens
				}
				
				
				//do things for player 2
				if (playfields[PLAYER_2][index] == TILE_WALL) {
					screenData[PLAYER_2][y][x] = '#';	

				} else if (playfields[PLAYER_2][index] == TILE_CLEARED) {
					screenData[PLAYER_2][y][x] = '=';					
					
				} else if (playfields[PLAYER_2][index] == TILE_BLANK){						//if paused or a blank tile
					screenData[PLAYER_2][y][x] = ' ';							

				} else if (gameState != GAME_STATE_PAUSED && gameState != GAME_STATE_ASK_RESTART) {	//don't display when paused
					screenData[PLAYER_2][y][x] = (char) ('A' + playfields[PLAYER_2][index]);		//convert shape number to displayable character
				
				} else {
					screenData[PLAYER_2][y][x] = ' ';			//displays when the game is paused, or if something strange happens
				}
			}	
		}
		
		//for each player...
		for (int player = 0; player < 2; ++player) {
		
			//display a guide if needed (not that there's a way to disable it yet...)
			if (players[player].displayGuide && gameState != GAME_STATE_PAUSED && gameState != GAME_STATE_ASK_RESTART) {
				
				//if displaying guides are on
				//figure out where the piece will land
				//(rotation, X and shape will be the same as the current shape)
				
				int guideY = players[player].currentY;
				while (doesPieceFit(player, players[player].currentShape, players[player].currentDirection, players[player].currentX, guideY + 1)) {
					++guideY;
				}
				
				//draw it on
				for (int y = 0; y < 4; ++y) {
					for (int x = 0; x < 4; ++x) {
						
						//check if the tile has a bit set at the X, Y position (rotated)
						//again, why can't integers be used as booleans?
						if (((tetrominos[players[player].currentShape] >> getRotatedIndex(x, y, players[player].currentDirection, players[player].currentShape)) & 1) == 1) {
							screenData[player][guideY + y][players[player].currentX + x] = '.';		//'*'
						}
					}
				}
			}
			
			//now copy the current tile onto the buffer
			//this is done after the guide so it will override it if needed
			if (gameState != GAME_STATE_PAUSED && gameState != GAME_STATE_ASK_RESTART) {
				for (int y = 0; y < 4; ++y) {
					for (int x = 0; x < 4; ++x) {
						
						//check if the tile has a bit set at the X, Y position (rotated)
						//again, why can't integers be used as booleans?
						if (((tetrominos[players[player].currentShape] >> getRotatedIndex(x, y, players[player].currentDirection, players[player].currentShape)) & 1) == 1) {
							screenData[player][players[player].currentY + y][players[player].currentX + x] = (char) ('a' + players[player].currentShape);		//'*'
						}
					}
				}
			}
		}
		
		//
		//	WARNING: SCARY CODE AHEAD!
		//
		
		//next, display the left half of the screen
		
		//start with a newline because you don't want it crammed in the top corner
		setText("\n\n");
				
		for (int y = 0; y < PLAYFIELD_HEIGHT; ++y) {
			
			append("    ");									//a bit of padding
			append(new String(screenData[PLAYER_1][y]));	//draw in player 1's playfield
															//horray for garbage collection!
			
			//draw the score, next piece, etc.
			if (y == 0) {							//display the score to the right of the first line
				append("    Score: ");
				append(Integer.toString(players[PLAYER_1].score));
				
				//MUST be padded to exactly 30 characters after the edge of the first playfield
				for (int p = Integer.toString(players[PLAYER_1].score).length(); p < 19; ++p) append(" ");
				
			} else if (y == 1) {					//similar thing with top score
				append("    Top  : ");
				append(Integer.toString(players[PLAYER_1].top));
				
				for (int p = Integer.toString(players[PLAYER_1].top).length(); p < 19; ++p) append(" ");
				
			} else if (y == 3) {					//number of lines cleared
				append("    Lines: ");
				append(Integer.toString(players[PLAYER_1].linesCleared));
				
				for (int p = Integer.toString(players[PLAYER_1].linesCleared).length(); p < 19; ++p) append(" ");
				
			} else if (y == 5) {					//leave a line in between top score and level
				append("    Level: ");
				append(Integer.toString(players[PLAYER_1].level));
				
				for (int p = Integer.toString(players[PLAYER_1].level).length(); p < 19; ++p) append(" ");
				
			} else if (y == 7) {					//display the first row of the next tile
				append("    Next : ");
				
				//gets the next shape as a string (e.g. "A", "B")
				String displayString = new String(new char[] {(char) ('A' + players[PLAYER_1].nextShape)});
				
				for (int x = 0; x < 4; ++x) {
					//We ignore rotation (set to zero), because we don't want it to be rotated as the player
					//rotates the current tile. As we are getting the first row, we force Y to be zero
					if (((tetrominos[players[PLAYER_1].nextShape] >> getRotatedIndex(x, 0, 0, players[PLAYER_1].nextShape)) & 1) == 1) {
						append(displayString);
					} else {
						append(" ");
					}
				}
				
				//MUST be padded to exactly 30 characters after the edge of the first playfield
				append("               ");
				
			} else if (y == 8) {					//display the second row
				append("           ");				//padding where the 'Next:' message was on the last time
													//so it formats correctly
				
				//gets the next shape as a string (e.g. "A", "B")
				String displayString = new String(new char[] {(char) ('A' + players[PLAYER_1].nextShape)});

				for (int x = 0; x < 4; ++x) {
					//same thing, but with Y = 1 to get the second row
					if (((tetrominos[players[PLAYER_1].nextShape] >> getRotatedIndex(x, 1, 0, players[PLAYER_1].nextShape)) & 1) == 1) {
						append(displayString);
					} else {
						append(" ");
					}
				}
				
				//MUST be padded to exactly 30 characters after the edge of the first playfield
				append("               ");
				
				//only the first 2 rows of the next shape are required, as without rotation, only two rows are used

			} else if (y == 10) {		//gameplay messages
				if 		(gameState == GAME_STATE_GAMEOVER && !twoPlayers) 	append("    GAME OVER                 ");
				else if (gameState == GAME_STATE_PAUSED) 					append("    PAUSED                    ");
				else if (gameState == GAME_STATE_ASK_RESTART)				append("    Press R to restart.       ");
				else if (gameState == GAME_STATE_GAMEOVER && players[PLAYER_1].lost)	append("    PLAYER TWO WINS           ");
				else if (gameState == GAME_STATE_GAMEOVER && players[PLAYER_2].lost)	append("    PLAYER ONE WINS           ");
				else append("                              ");

			} else if (y == 12) {		//more gameplay messages
				if 		(gameState == GAME_STATE_GAMEOVER) 		append("    Press ENTER to restart.   ");
				else if (gameState == GAME_STATE_PAUSED) 		append("    Press ENTER to resume.    ");
				else if (gameState == GAME_STATE_TITLE) 		append("    Press ENTER to start.     ");
				else if (gameState == GAME_STATE_ASK_RESTART)	append("    Press ENTER to resume.    ");
				else append("                              ");
			
			} else {
				//MUST be padded to exactly 30 characters after the edge of the first playfield
				append("                              ");
			}
			
			if (twoPlayers) {
				append(new String(screenData[PLAYER_2][y]));		//draw in player 2
	
				//do the same for the second player, except the extra padding is not needed
				//as there is nothing to the right of the second playfield
				
				if (y == 0) {							//display the score to the right of the first line
					append("    Score: ");
					append(Integer.toString(players[PLAYER_2].score));
					
					for (int p = Integer.toString(players[PLAYER_2].score).length(); p < 19; ++p) append(" ");
					
				} else if (y == 1) {					//similar thing with top score
					append("    Top  : ");
					append(Integer.toString(players[PLAYER_2].top));
					
					for (int p = Integer.toString(players[PLAYER_2].top).length(); p < 19; ++p) append(" ");
					
				} else if (y == 3) {					//number of lines cleared
					append("    Lines: ");
					append(Integer.toString(players[PLAYER_2].linesCleared));
					
					for (int p = Integer.toString(players[PLAYER_2].linesCleared).length(); p < 19; ++p) append(" ");
					
				} else if (y == 5) {					//leave a line in between top score and level
					append("    Level: ");
					append(Integer.toString(players[PLAYER_2].level));
					
					for (int p = Integer.toString(players[PLAYER_2].level).length(); p < 19; ++p) append(" ");
					
				} else if (y == 7) {					//display the first row of the next tile
					append("    Next : ");
					
					//gets the next shape as a string (e.g. "A", "B")
					String displayString = new String(new char[] {(char) ('A' + players[PLAYER_2].nextShape)});
					
					for (int x = 0; x < 4; ++x) {
						//We ignore rotation (set to zero), because we don't want it to be rotated as the player
						//rotates the current tile. As we are getting the first row, we force Y to be zero
						if (((tetrominos[players[PLAYER_2].nextShape] >> getRotatedIndex(x, 0, 0, players[PLAYER_2].nextShape)) & 1) == 1) {
							append(displayString);
						} else {
							append(" ");
						}
					}
										
				} else if (y == 8) {					//display the second row
					append("           ");				//padding where the 'Next:' message was on the last time
														//so it formats correctly
					
					//gets the next shape as a string (e.g. "A", "B")
					String displayString = new String(new char[] {(char) ('A' + players[PLAYER_2].nextShape)});
	
					for (int x = 0; x < 4; ++x) {
						//same thing, but with Y = 1 to get the second row
						if (((tetrominos[players[PLAYER_2].nextShape] >> getRotatedIndex(x, 1, 0, players[PLAYER_2].nextShape)) & 1) == 1) {
							append(displayString);
						} else {
							append(" ");
						}
					}
										
					//only the first 2 rows of the next shape are required, as without rotation, only two rows are used
				} 
			}
			
			//finally, move onto the next line
			append("\n");
		}
	}
	
	//given an X and Y coordinate, it rotates it around a grid and returns the corrected value as an index into a 1D array
	//it handles certain shapes differently in order to create a smoother gameplay experience
	//see https://tetris.wiki/Super_Rotation_System
	static private int getRotatedIndex(int x, int y, int rotation, int shapeNumber) {
		
		if (shapeNumber == TILE_O) {
			//the square shape doesn't get rotated at all
			return y * 4 + x;
		
		} else if (shapeNumber == TILE_I) {
			//tile I is the only 4x4 tile
			
			//done as AND 3 instead of MOD 4 so we don't get negative numbers,
			//as Java wants everything to be signed
			switch (rotation & 3) {
			case 0:
				return (y * 4) + x;
			case 1:
				return 12 + y - (x * 4);
			case 2:
				return 15 - (y * 4) - x;
			case 3:
				return 3 - y + (x * 4);
			}
			
		} else {
			//all other shapes are 3x3
		
			//out of range, so we should return an index which has its bit cleared
			//I've just picked bit 15 in for this purpose
			//if this didn't happen, the maths below would be incorrect giving questionable indexes (like negatives)
			if (x > 2 || y > 2) {
				return 15;
			}
			
			//again, AND 3 instead of MOD 4 so we don't get negatives
			switch (rotation & 3) {
			case 0:
				return (y * 3) + x;
			case 1:
				return 6 + y - (x * 3);
			case 2:
				return 8 - (y * 3) - x;
			case 3:
				return 2 - y + (x * 3);
			}
		}
		
		return 0;
		
	}
	
	//creates a new game of Tetris in a barebones window
	private static void createTetrisGame() {
		JFrame tetrisWindow = new JFrame("Tetris");
		
		Tetris tetrisGame = new Tetris();
		tetrisWindow.getContentPane().add(tetrisGame, "North");

		tetrisWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		tetrisWindow.pack();
		tetrisWindow.setVisible(true); 
	}
	
	//starts a new game of Tetris
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
            public void run() {
            	createTetrisGame();
            }
		});
	}
	
	
}
