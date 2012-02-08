// original source:
// http://droidcake.com/2010/12/30/how-to-create-classic-game-on-android/
// no author listed

package com.isthisonthetest.breakout;

import java.util.ArrayList;
import java.util.List;

import org.anddev.andengine.audio.sound.Sound;
import org.anddev.andengine.audio.sound.SoundFactory;
import org.anddev.andengine.engine.Engine;
import org.anddev.andengine.engine.camera.Camera;
import org.anddev.andengine.engine.handler.IUpdateHandler;
import org.anddev.andengine.engine.options.EngineOptions;
import org.anddev.andengine.engine.options.EngineOptions.ScreenOrientation;
import org.anddev.andengine.engine.options.resolutionpolicy.RatioResolutionPolicy;
import org.anddev.andengine.entity.scene.Scene;
import org.anddev.andengine.entity.scene.background.ColorBackground;
import org.anddev.andengine.entity.sprite.AnimatedSprite;
import org.anddev.andengine.entity.sprite.Sprite;
import org.anddev.andengine.entity.text.ChangeableText;
import org.anddev.andengine.entity.util.FPSLogger;
import org.anddev.andengine.input.touch.TouchEvent;
import org.anddev.andengine.opengl.font.Font;
import org.anddev.andengine.opengl.texture.Texture;
import org.anddev.andengine.opengl.texture.TextureOptions;
import org.anddev.andengine.opengl.texture.region.TextureRegion;
import org.anddev.andengine.opengl.texture.region.TextureRegionFactory;
import org.anddev.andengine.opengl.texture.region.TiledTextureRegion;
import org.anddev.andengine.ui.activity.BaseGameActivity;
import org.anddev.andengine.util.Debug;

import android.graphics.Color;
import android.graphics.Typeface;

public class Breakout extends BaseGameActivity
{

	// the width of the game screen
	private static final int CAMERA_WIDTH = 720;
	// the height of the game screen
	private static final int CAMERA_HEIGHT = 480;
	// the speed of the game
	private static final float DEMO_VELOCITY = 200.0f;

	// variable used to manipulate the camera
	private Camera mCamera;
	// texture for the entire screen
	private Texture mTexture;
	// texture region for the ball
	private TextureRegion mPongTextureRegion;
	// texture region for the paddle
	private TextureRegion mBoxTextureRegion;
	// texture region for the tiles
	private TiledTextureRegion mBallTextureRegion;
	// List that holds all of the boxes
	private List<Sprite> boxSpritesList = new ArrayList<Sprite>();
	// holds the font for the scoreboard
	private Font mFont;
	// holds the texture for the scoreboard
	private Texture mFontTexture;
	// the actual text of the scoreboard
	ChangeableText scoreText;
	// initialize score to 0 when the game loads
	private int score = 0;

	// sounds used in the program
	private Sound mTileHit;
	private Sound mPaddleHit;
	private Sound mDeath;

	// the point where the engine starts drawing the tiles
	int startX;
	// the width of the blocks to be drawn
	final int blockWidth = 32;
	// number of rows to draw on the scene
	final int numRows = 5;

	// this is run when the game is first loaded.
	// i.e. when the application is launched
	@Override
	public Engine onLoadEngine()
	{
		// sets the camera to the dimensions declared above
		this.mCamera = new Camera(0, 0, CAMERA_WIDTH, CAMERA_HEIGHT);
		// returns an engine with the properties set by the passed arguments
		// below
		// true: fullscreen
		// ScreenOrientation.LANDSCAPE: sets to landscape orientation
		// RatioResolutionPolicy({width},{height}): sets the software renderer's
		// resolution policy
		// this.mCamera: sets to this class' instantiation of the camera
		return new Engine(new EngineOptions(true, ScreenOrientation.LANDSCAPE,
				new RatioResolutionPolicy(CAMERA_WIDTH, CAMERA_HEIGHT),
				this.mCamera).setNeedsSound(true));
	}

	// runs when resources are loaded
	// that is, when the actual pictures and textures are being loaded into the
	// game
	@Override
	public void onLoadResources()
	{
		initTextures();

		initSounds();

		setupScoreBoard();
	}

	@Override
	public Scene onLoadScene()
	{
		this.mEngine.registerUpdateHandler(new FPSLogger());

		// creates a Scene with 1 player
		final Scene scene = new Scene(1);
		// this is an example of how to place a background image rather than a
		// solid color
		// scene.setBackground(new RepeatingSpriteBackground(CAMERA_WIDTH,
		// CAMERA_HEIGHT, this.mEngine.getTextureManager(), new
		// AssetTextureSource(this, "gfx/background.png")));
		scene.setBackground(new ColorBackground(0.1f, 0.5f, 0.5f));

		// sets the score display with the given font
		scoreText = new ChangeableText(0, CAMERA_HEIGHT - 50, this.mFont,
				"Score: ", "Score: xxxxx".length());

		// adds the score display to the screen
		scene.getTopLayer().addEntity(scoreText);

		// these are used to set the position for the paddle sprite
		final int centerX = (CAMERA_WIDTH - this.mPongTextureRegion.getWidth()) / 2;
		final int centerY = CAMERA_HEIGHT - this.mPongTextureRegion.getHeight();
		final Sprite paddle = setupPaddle(centerX, centerY);

		// creates the ball object that bounces around
		final Ball ball = new Ball(CAMERA_WIDTH / 2, CAMERA_HEIGHT / 3,
				this.mBallTextureRegion);
		// sets the speed of the ball according to the demo velocity
		ball.setVelocity(DEMO_VELOCITY, DEMO_VELOCITY);

		// adds the paddle and the ball to the current scene
		scene.getTopLayer().addEntity(paddle);
		scene.getTopLayer().addEntity(ball);

		fillBoxArea(scene);

		// adds a listener for the paddle in order to receive input
		scene.registerTouchArea(paddle);
		scene.setTouchAreaBindingEnabled(true);

		// handles all updates (frames) for the game
		scene.registerUpdateHandler(new IUpdateHandler()
		{
			@Override
			public void reset()
			{
			}

			@Override
			public void onUpdate(final float pSecondsElapsed)
			{
				// detects if the ball falls below the paddle
				// i.e. lose condition
				// this subtracts 1 point from the score
				// this may be changed to have more drastic consequences such as
				// restarting the entire game
				if (ballBelowPaddle(paddle, ball))
				{
					playerDeath(ball);
				}

				// reflect the ball when it hits the paddle
				if (paddle.collidesWith(ball))
				{
					Breakout.this.mPaddleHit.play();
					ball.setVelocityY(-DEMO_VELOCITY);
				}

				final List<Sprite> toRemoved = checkForCollisions(ball);

				removeSprite(scene, toRemoved);
			}

		});

		return scene;
	}

	private List<Sprite> checkForCollisions(final Ball ball)
	{
		final List<Sprite> toRemoved = new ArrayList<Sprite>();
		boxCheckLoop: for (Sprite box : boxSpritesList)
		{
			// if the ball collides with the box
			// AND it hits the bottom specifically
			if (ball.collidesWith(box))
			{
				// increment the score and change the text
				score++;
				scoreText.setText("Score: " + score);

				// add the box to the list of sprites to remove
				toRemoved.add(box);
				// play the "tile hit" sound
				Breakout.this.mTileHit.play();
				checkCollisionDirection(ball, box);

				break boxCheckLoop;
			}
		}
		return toRemoved;
	}

	private void removeSprite(final Scene scene, final List<Sprite> toRemoved)
	{
		// this runs once per update/frame
		Breakout.this.runOnUpdateThread(new Runnable()
		{

			@Override
			public void run()
			{
				for (Sprite sprite : toRemoved)
				{
					// remove all of the sprites in the "toRemoved" list
					scene.getTopLayer().removeEntity(sprite);
					boxSpritesList.remove(sprite);
				}
			}
		});
	}

	private void fillBoxArea(final Scene scene)
	{
		// this places blocks until there is no more room on the screen
		for (int h = 0; h < numRows * blockWidth; h += blockWidth)
		{
			startX = 10;
			for (int i = 0; i < Math.floor(CAMERA_WIDTH / blockWidth); i++)
			{
				Sprite boxSprite = new Sprite(startX, 10 + h,
						this.mBoxTextureRegion);
				boxSpritesList.add(boxSprite);
				startX += blockWidth;
			}
		}

		// adds all boxes to the screen
		for (Sprite boxSprite : boxSpritesList)
		{
			scene.getTopLayer().addEntity(boxSprite);
		}
	}

	private Sprite setupPaddle(final int centerX, final int centerY)
	{
		final Sprite paddle = new Sprite(centerX, centerY,
				this.mPongTextureRegion)
		{
			@Override
			public boolean onAreaTouched(final TouchEvent pSceneTouchEvent,
					final float pTouchAreaLocalX, final float pTouchAreaLocalY)
			{
				// this gets the X coordinate of the finger touch,
				// then subtracts the width of the paddle,
				// setting the paddle location to the bottom even if
				// the touch is not directly on top of the paddle.
				this.setPosition(pSceneTouchEvent.getX() - this.getWidth() / 2,
						centerY);
				return true;
			}
		};
		return paddle;
	}

	@Override
	public void onLoadComplete()
	{

	}

	// used to hold game logic information for the ball
	private static class Ball extends AnimatedSprite
	{
		public Ball(final float pX, final float pY,
				final TiledTextureRegion pTextureRegion)
		{
			super(pX, pY, pTextureRegion);
		}

		// occurs every update/frame
		// this handles the bouncing on the borders of the window
		@Override
		protected void onManagedUpdate(final float pSecondsElapsed)
		{
			// if the ball hits the left border
			if (this.mX < 0)
			{
				this.setVelocityX(DEMO_VELOCITY);
			} //
				// if the ball hits the right border
			else if (this.mX + this.getWidth() > CAMERA_WIDTH)
			{
				this.setVelocityX(-DEMO_VELOCITY);
			}

			// if the ball hits the bottom border
			if (this.mY < 0)
			{
				this.setVelocityY(DEMO_VELOCITY);
			} //
				// if the ball hits the top border
			else if (this.mY + this.getHeight() > CAMERA_HEIGHT)
			{
				this.setVelocityY(-DEMO_VELOCITY);
			}

			super.onManagedUpdate(pSecondsElapsed);
		}
	}

	private void initSounds()
	{
		// sets the root for the sound effects to the mfx folder
		SoundFactory.setAssetBasePath("mfx/");
		// initialize all the sounds
		try
		{
			this.mTileHit = SoundFactory.createSoundFromAsset(
					this.mEngine.getSoundManager(), this, "crush.ogg");
			this.mPaddleHit = SoundFactory.createSoundFromAsset(
					this.mEngine.getSoundManager(), this, "paddlehit.ogg");
			this.mDeath = SoundFactory.createSoundFromAsset(
					this.mEngine.getSoundManager(), this, "death.ogg");
		} catch (Exception e)
		{
			Debug.e("Error: " + e);
		}
	}

	private void initTextures()
	{
		// initializes all of the texture regions
		// do not change this area unless you know what you're doing
		this.mTexture = new Texture(256, 32,
				TextureOptions.BILINEAR_PREMULTIPLYALPHA);
		this.mPongTextureRegion = TextureRegionFactory.createFromAsset(
				this.mTexture, this, "gfx/pong.png", 0, 0);
		this.mBallTextureRegion = TextureRegionFactory.createTiledFromAsset(
				this.mTexture, this, "gfx/ball.png", 97, 0, 1, 1);
		this.mBoxTextureRegion = TextureRegionFactory.createFromAsset(
				this.mTexture, this, "gfx/box.png", 114, 0);
		// end initialize textures

		// loads the texture for the entire screen
		this.mEngine.getTextureManager().loadTexture(this.mTexture);
	}

	private void setupScoreBoard()
	{
		// sets up font for scoreboard
		this.mFontTexture = new Texture(256, 256, TextureOptions.BILINEAR);
		this.mFont = new Font(this.mFontTexture, Typeface.create(
				Typeface.DEFAULT, Typeface.BOLD), 48, true, Color.BLACK);
		this.mEngine.getTextureManager().loadTexture(this.mFontTexture);
		this.mEngine.getFontManager().loadFont(this.mFont);
	}

	private void checkCollisionDirection(final Ball ball, Sprite box)
	{
		// the tolerance for collision detection
		float eps = 0.2f;
		// coordinates for top left of the ball
		float aX = ball.getX();
		float aY = ball.getY();
		// height and width of the ball
		float aH = ball.getBaseHeight();
		float aW = ball.getBaseWidth();
		// coordinates for top left of the collided box
		float bX = box.getX();
		float bY = box.getY();
		// height and width of the collided box
		float bH = box.getBaseHeight();
		float bW = box.getBaseWidth();

		// bottom of ball hits the top of a block
		if (Math.abs((aY + aH) - bY) <= bH * eps)
		{
			ball.setVelocityY(-ball.getVelocityY());
		} // top of ball hits the bottom of a block
		else if (Math.abs(aY - (bH + bY)) <= bH * eps)
		{
			ball.setVelocityY(-ball.getVelocityY());
		} // right side of ball hits the left side of a block
		else if (Math.abs((aX + aW) - bX) <= bW * eps)
		{
			ball.setVelocityX(-ball.getVelocityX());
		} // left side of ball hits the right side of a block
		else if (Math.abs(aX - (bW + bX)) <= bW * eps)
		{
			ball.setVelocityX(-ball.getVelocityX());
		}
	}

	private void playerDeath(final Ball ball)
	{
		Breakout.this.mDeath.play();
		score -= 1;
		scoreText.setText("Score: " + score);
		ball.setPosition(CAMERA_WIDTH / 2, CAMERA_HEIGHT / 3);
	}

	private boolean ballBelowPaddle(final Sprite paddle, final Ball ball)
	{
		return ball.getY() + ball.getBaseHeight() > paddle.getY() * 1.05;
	}
}
