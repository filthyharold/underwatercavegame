package com.me.cavegenerator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.maps.MapLayers;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer.Cell;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.Shape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.physics.box2d.BodyDef.BodyType;
import com.me.cavegenerator.Cell.CellType;
import com.me.cavegenerator.Cell.WallType;
import common.GameResources;
import common.Globals;

public class MapManager extends InputAdapter {
	private CaveMap caveMap;
	private TiledMap map;
	private OrthogonalTiledMapRenderer mapRenderer;
	AtlasRegion tmpRegion;
	int numOfLayers = 1;
	boolean flipX;
	boolean flipY;
	boolean mapGenerationDone;
	boolean mapCreationDone;

	private Random rnd;

	private SpriteBatch mapBatch;

	private Color tileColor;

	private ArrayList<Miner> miners = new ArrayList<Miner>();
	private ArrayList<Miner> newMiners = new ArrayList<Miner>();
	private Miner startMiner;
	private Miner currentMiner;
	private Iterator<Miner> minerIter;
	private int digCounter;
	private int createdMiners = 1;
	private int minersResetCounter;

	private int mapWidth, mapHeight;
	private Vector2 minCamPos, maxCamPos;

	private Vector2 playerStartPos;

	private OrthographicCamera tiledMapCamera;

	// BOX2D tmp stuff
	Body body;
	PolygonShape boxShape;
	BodyDef bodyDef;

	public MapManager(int mapWidth, int mapHeight, Vector2 camPos) {
		rnd = new Random();

		boxShape = new PolygonShape();
		// boxShape.setAsBox(Globals.TILE_SIZE/8, Globals.TILE_SIZE/8);
		bodyDef = new BodyDef();

		flipX = false;
		flipY = false;
		mapGenerationDone = false;
		mapCreationDone = false;

		digCounter = 0;
		minersResetCounter = 0;

		this.mapWidth = mapWidth;
		this.mapHeight = mapHeight;

		int w = Gdx.graphics.getWidth();
		int h = Gdx.graphics.getHeight();

		playerStartPos = new Vector2(this.mapWidth / 2, 0);

		caveMap = new CaveMap(mapWidth, mapHeight, 0);

		tileColor = new Color(0.20f, 0.10f, 0, 1.0f);

		mapBatch = new SpriteBatch();
		mapBatch.setColor(tileColor);

		startMiner = new Miner(new Vector2(mapWidth / 2, 0));
		miners.add(startMiner);

		this.map = new TiledMap();

		float unitScale = 1f / (float) Globals.TILE_SIZE;
		mapRenderer = new OrthogonalTiledMapRenderer(map, unitScale, mapBatch);

		tiledMapCamera = new OrthographicCamera();
		tiledMapCamera.setToOrtho(true, Gdx.graphics.getWidth()
				/ Globals.TILE_SIZE, Gdx.graphics.getHeight()
				/ Globals.TILE_SIZE);

		tiledMapCamera.update();
	}

	public void generateMap(World world) {
		// TODO: prevent the minerslist from being be emptied infinitely
		minerIter = miners.iterator();
		while (createdMiners < 350) {
			while (minerIter.hasNext()) {
				currentMiner = minerIter.next();

				// If dig is successful, increment digCounter, else the dig
				// fails, remove miner
				caveMap = currentMiner.dig(caveMap, false, 0);
				if (currentMiner.digSucccess)
					digCounter++;
				else
					minerIter.remove();

				int rndInt = rnd.nextInt(100);

				if (rndInt < 4) {
					newMiners.add(new Miner(currentMiner.getCurrentPos()));
					createdMiners++;
				}

				if (miners.size() == 0) {
					minersResetCounter++;
					ArrayList<com.me.cavegenerator.Cell> adjacentCells = caveMap
							.get8AdjacentCells(currentMiner.getCurrentPos());
					Vector2 rndPos = adjacentCells.get(
							rnd.nextInt(adjacentCells.size())).getPos();
					newMiners.add(new Miner(rndPos));
				}
			}

			miners.addAll(newMiners);
			minerIter = miners.iterator();
			newMiners.clear();
		}

		caveMap.cleanUp(5);
		createTileMap(world);
		mapGenerationDone = true;
	}

	public void createTileMap(World world) {
		if (this.caveMap.isReady()) {
			this.map = new TiledMap();
			MapLayers layers = map.getLayers();
			AtlasRegion tmpRegion;
			int numOfLayers = 1;
			boolean flipX = false;
			boolean flipY = false;

			Cell cell = new Cell();
			com.me.cavegenerator.Cell caveCell;
			MyTiledMapTile mapTile = null;

			if (GameResources.isReady()) {
				for (int i = 0; i < numOfLayers; i++) {
					TiledMapTileLayer layer = new TiledMapTileLayer(mapWidth,
							mapHeight, 32, 32);
					for (int x = 0; x < mapWidth; x++) {
						for (int y = 0; y < mapHeight; y++) {
							caveCell = caveMap.getCellAt(x, y);

							if (caveCell.getCellType() != CellType.EMPTY) {
								if (x >= 98 && x <= 102 && y >= 3 && y <= 7)
									createTileBody(world, x, y);
							}

							switch (caveCell.getCellType()) {
							case EMPTY:
								mapTile = null;
								break;
							case WALL:
								if (caveCell.getWallType() == WallType.LEFT) {
									flipX = true;
									flipY = rnd.nextBoolean();
									tmpRegion = GameResources.verticalTiles
											.get(rnd.nextInt(GameResources.verticalTiles.size));
								} else if (caveCell.getWallType() == WallType.LONELY_LEFT) {
									flipX = false;
									flipY = rnd.nextBoolean();
									tmpRegion = GameResources.lonelyVerticalTiles
											.get(rnd.nextInt(GameResources.lonelyVerticalTiles.size));
								} else if (caveCell.getWallType() == WallType.RIGHT) {
									flipX = false;
									flipY = rnd.nextBoolean();
									tmpRegion = GameResources.verticalTiles
											.get(rnd.nextInt(GameResources.verticalTiles.size));
								} else if (caveCell.getWallType() == WallType.LONELY_RIGHT) {
									flipX = true;
									flipY = rnd.nextBoolean();
									tmpRegion = GameResources.lonelyVerticalTiles
											.get(rnd.nextInt(GameResources.lonelyVerticalTiles.size));
								} else if (caveCell.getWallType() == WallType.CEILING) {
									flipX = rnd.nextBoolean();
									flipY = false;
									tmpRegion = GameResources.horizontalTiles
											.get(rnd.nextInt(GameResources.horizontalTiles.size));
								} else if (caveCell.getWallType() == WallType.LONELY_TOP) {
									flipX = rnd.nextBoolean();
									flipY = true;
									tmpRegion = GameResources.lonelyHorizontalTiles
											.get(rnd.nextInt(GameResources.lonelyHorizontalTiles.size));
								} else if (caveCell.getWallType() == WallType.GROUND) {
									flipX = rnd.nextBoolean();
									flipY = true;
									tmpRegion = GameResources.horizontalTiles
											.get(rnd.nextInt(GameResources.horizontalTiles.size));
								} else if (caveCell.getWallType() == WallType.LONELY_BOTTOM) {
									flipX = rnd.nextBoolean();
									flipY = false;
									tmpRegion = GameResources.lonelyHorizontalTiles
											.get(rnd.nextInt(GameResources.lonelyHorizontalTiles.size));
								} else if (caveCell.getWallType() == WallType.LEFT_RIGHT) {
									flipX = false;
									flipY = rnd.nextBoolean();
									tmpRegion = GameResources.thinVerticalTiles
											.get(rnd.nextInt(GameResources.thinVerticalTiles.size));
								} else if (caveCell.getWallType() == WallType.GROUND_CEILING) {
									flipX = rnd.nextBoolean();
									flipY = false;
									tmpRegion = GameResources.thinHorizontalTiles
											.get(rnd.nextInt(GameResources.thinHorizontalTiles.size));
								} else {
									tmpRegion = GameResources.wallRegion;
								}

								mapTile = new MyTiledMapTile(tmpRegion);

								cell = new Cell();
								cell.setFlipHorizontally(flipX);
								cell.setFlipVertically(flipY);
								cell.setTile(mapTile);
								layer.setCell(x, y, cell);
								break;
							case CORNER_WALL:
								if (caveCell.getWallType() == WallType.UPPER_LEFT_CONVEX) {
									flipX = false;
									flipY = false;
								} else if (caveCell.getWallType() == WallType.UPPER_RIGHT_CONVEX) {
									flipX = true;
									flipY = false;
								} else if (caveCell.getWallType() == WallType.LOWER_RIGHT_CONVEX) {
									flipX = true;
									flipY = true;
								} else if (caveCell.getWallType() == WallType.LOWER_LEFT_CONVEX) {
									flipX = false;
									flipY = true;
								}

								mapTile = new MyTiledMapTile(
										GameResources.cornerTiles.get(rnd
												.nextInt(GameResources.cornerTiles.size)));

								cell = new Cell();
								cell.setFlipHorizontally(flipX);
								cell.setFlipVertically(flipY);
								cell.setTile(mapTile);
								layer.setCell(x, y, cell);
								break;
							}
						}
					}
					layers.add(layer);

					float unitScale = 1 / (float) 32;
					mapRenderer = new OrthogonalTiledMapRenderer(map,
							unitScale, mapBatch);

					mapCreationDone = true;
				}
			}
		} else {
			System.out.println("The cave generation is not ready!");
		}
	}

	public void createBodies(World world) {
		// //// Rectangle camRect = new Rectangle(tiledMapCamera.position.x
		// //// - (tiledMapCamera.viewportWidth / 2), tiledMapCamera.position.y
		// //// - (tiledMapCamera.viewportHeight / 2),
		// //// tiledMapCamera.viewportWidth, tiledMapCamera.viewportHeight);
		// for (int x = 0; x < mapWidth; x++) {
		// for (int y = 0; y < mapHeight; y++) {
		// if (camRect.contains(x, y)) {
		// if (caveMap.getCellAt(x, y).getCellType() != CellType.EMPTY) {
		// bodyDef.type = BodyType.StaticBody;
		// bodyDef.position.set(15, 5);
		// boxShape.setAsBox(.5f, .5f);
		// world.createBody(bodyDef).createFixture(boxShape, 10f);
		// boxShape.dispose();
		// }
		// }
		// }
		// }
	}

	// go though entire map and create bodies for all collidable walls...
	public void createTileBody(World world, int x, int y) {
		bodyDef.type = BodyType.StaticBody;
		// for (int x = 0; x < mapWidth; x++) {
		// for (int y = 0; y < mapHeight; y++) {
		// if (caveMap.getCellAt(x, y).getCellType() != CellType.EMPTY) {
		System.out.println("Creating box2d body at: " + x + ":" + y);
		bodyDef.position.set(x + 0.5f, y + 0.5f);
		System.out.println(bodyDef.position);
		boxShape = new PolygonShape();
		boxShape.setAsBox(.5f, .5f);
		world.createBody(bodyDef).createFixture(boxShape, 100f);
		boxShape.dispose();

		// world.createBody(bodyDef).createFixture(boxShape,
		// 10f).getBody().setAwake(false);
		// }
		// }
		// }
	}

	public void render() {
		if (mapCreationDone) {
			tiledMapCamera.update();
			mapRenderer.setView(tiledMapCamera);
			mapRenderer.render();
		}
	}

	public void update(Vector2 newCameraPos) {
		tiledMapCamera.position.x = newCameraPos.x;
		tiledMapCamera.position.y = -newCameraPos.y;
		tiledMapCamera.update();
	}

	public void reset(Vector2 startPos) {
		miners.clear();
		map = null;
		caveMap = new CaveMap(mapWidth, mapHeight, 0);
		minersResetCounter = 0;
		digCounter = 0;
		createdMiners = 0;
		mapCreationDone = false;
		mapGenerationDone = false;
		startMiner = new Miner(startPos);
		miners.add(startMiner);
	}

	public void dispose() {
		mapBatch.dispose();
		map.dispose();
	}

	public SpriteBatch getSpriteBatch() {
		return (SpriteBatch) mapRenderer.getSpriteBatch();
	}

	public OrthographicCamera getCamera() {
		return tiledMapCamera;
	}

	public CaveMap getCaveMap() {
		return caveMap;
	}

	public float getZoom() {
		return tiledMapCamera.zoom;
	}

	public OrthogonalTiledMapRenderer getRenderer() {
		return mapRenderer;
	}

	public Vector2 getPlayerStartPos() {
		return playerStartPos;
	}

	@Override
	public boolean scrolled(int amount) {
		tiledMapCamera.zoom = Globals.cameraZoom;
		return false;
	}
}