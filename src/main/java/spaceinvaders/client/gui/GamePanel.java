package spaceinvaders.client.gui;

import static spaceinvaders.game.EntityEnum.INVADER;
import static spaceinvaders.game.EntityEnum.PLAYER;
import static spaceinvaders.game.EntityEnum.PLAYER_BULLET;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.swing.JPanel;
import spaceinvaders.client.gui.entities.Drawable;
import spaceinvaders.client.gui.entities.GraphicalEntity;
import spaceinvaders.client.gui.entities.GraphicalEntityVisitor;
import spaceinvaders.client.gui.entities.GraphicsFactory;
import spaceinvaders.client.gui.entities.PaintingVisitor;
import spaceinvaders.client.gui.entities.Player;
import spaceinvaders.command.server.MovePlayerRightCommand;
import spaceinvaders.game.Entity;
import spaceinvaders.game.EntityEnum;
import spaceinvaders.game.GameConfig;
import spaceinvaders.utility.Couple;

import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Main panel of the game.
 *
 * <p>Contains all the visible elements of the game environment. Controls painting and repainting.
 */
@SuppressWarnings("serial")
class GamePanel extends JPanel {
  private final GraphicsFactory factory = GraphicsFactory.getInstance();
  private final NavigableMap<Integer,GraphicalEntity> entityMap = new TreeMap<>();
  private final ReadWriteLock entitiesLock = new ReentrantReadWriteLock();
  private final SoundManager sound = new SoundManager();
  private final GameConfig config = GameConfig.getInstance();
  private Integer playerAvatarNumber;
  private BufferedImage centerImg;
  private Couple<Integer,Integer> centerImgPos;
  private Boolean gameOn;

  // Dead Reckoning
  private HashMap<Integer, Integer> prevEntityPos = new HashMap<Integer, Integer>(); // store the previous position to calculate dead reck
  // schedule the position of each player to be updated every X ms
  private final int DEADRECK_DELAY = 500; // milliseconds
  ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

  Runnable deadReckon = new Runnable() {
    public void run() {
        if(!prevEntityPos.isEmpty()){
          System.out.println("Dead reckoning: ");
          for(Map.Entry<Integer, Integer> entry : prevEntityPos.entrySet()){
            Integer id = entry.getKey(); Integer direction = entry.getValue();
            final GraphicalEntity entity = entityMap.get(id);
            if (entity == null) {
              throw new NullPointerException();
            }
            if(direction == 0) { // not moved
              System.out.println("No movement detected");
              return; 
            } 
            else if(direction == 1){ // previous move was right
              System.out.println("Moving right: ");
              entity.relocate(entity.getX()+config.speed().player().getDistance(), entity.getY()); // increase x by player move speed
            } else if (direction == 2){ // previous move was left
              System.out.println("Moving left: ");
              entity.relocate(entity.getX()-config.speed().player().getDistance(), entity.getY());
            }
          }
        }
    }
  };
  
  public GamePanel() {
    setBackground(Color.BLACK);
  setForeground(Color.BLACK);
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    super.paintComponent(graphics);
    if (gameOn) {
      final GraphicalEntityVisitor painter = new PaintingVisitor(graphics,this);
      graphics.setColor(Color.WHITE);
      graphics.setFont(new Font("Courier",Font.BOLD,15));
      entitiesLock.readLock().lock();
      for (Drawable entity : entityMap.values()) {
        entity.draw(painter);
      }
      entitiesLock.readLock().unlock();
    } else {
      graphics.drawImage(centerImg,centerImgPos.getFirst(),centerImgPos.getSecond(),this);
    }
  }

  /** Prepare the panel for a new game. */
  public void init() {
    gameOn = true;
    playerAvatarNumber = 0;
    entitiesLock.writeLock().lock();
    entityMap.clear();
    entitiesLock.writeLock().unlock();
  }

  /**
   * End the game and draw an image in the center.
   *
   * <p>Game entities are being wiped out, and the image will remain in the center of the panel
   * until {@link #init() init} is called.
   *
   * @throws NullPointerException if argument is {@code null}.
   */
  public void showImage(BufferedImage centerImg) {
    if (centerImg == null) {
      throw new NullPointerException();
    }
    this.centerImg = centerImg;
    int width = centerImg.getWidth();
    int height = centerImg.getHeight();
    int posX = (config.frame().getWidth() - width) / 2;
    int posY = (config.frame().getHeight() - height) / 2;
    centerImgPos = new Couple<>(posX,posY);
    gameOn = false;
    paintComponent(getGraphics());
  }

  /**
   * Refresh all entities by checking them against a list of updates.
   *
   * <p>Entities which are not found in the list of updates are removed, and new entities which are
   * found only in the list of updates are added. The coordinates of entities are changed
   * accordingly. After this method is executed, the new set of entities is going to be equal with
   * the list of updates.
   *
   * @throws NullPointerException if argument is {@code null}.
   */
  public void refreshEntities(List<Entity> updates) {
    executor.scheduleAtFixedRate(deadReckon, 0, DEADRECK_DELAY, TimeUnit.MILLISECONDS); // move player based on their previous movement direction
    if (updates == null) {
      throw new NullPointerException();
    }
    final List<Integer> elim = new ArrayList<>();
    final boolean[] mark = new boolean[updates.size()];
    entitiesLock.readLock().lock();
    for (Map.Entry<Integer,GraphicalEntity> entry : entityMap.entrySet()) {
      boolean found = false;
      int index = 0;
      for (Entity it : updates) {
        if (entry.getKey().equals(it.getId())) {
          found = true;
          entry.getValue().relocate(it.getX(),it.getY());
          mark[index] = true;
          break;
        }
        ++index;
      }
      if (!found) {
        elim.add(entry.getKey());
      }
    }
    entitiesLock.readLock().unlock();
    
    /* Remove entities not found in the updates. */
    entitiesLock.writeLock().lock();
    for (int key : elim) {
      entityMap.remove(key);
    }

    /* Add entities not found in the map. */
    int index = 0;
    for (Entity it : updates) {
      if (!mark[index]) {
        GraphicalEntity spawned = factory.create(it);
        entityMap.put(it.getId(),spawned);
      }
      ++index;
    }
    entitiesLock.writeLock().unlock();
  }

  /**
   * @param id player ID.
   * @param name player name.
   *
   * @throws NullPointerException if an argument is {@code null} of if the {@code id} could not
   *     be found.
   */
  public void setPlayer(int id, String name) {
    if (name == null) {
      throw new NullPointerException();
    }
    final Player player = (Player) entityMap.get(id);
    if (player == null) {
      throw new NullPointerException();
    }
    player.setName(name);
    player.setAvatarNumber(playerAvatarNumber++);
  }

  /**
   * Change the spatial coordinates of an entity.
   *
   * @param id entity ID.
   * @param newX new coordinate on x-axis.
   * @param newY new coordinate on y-axis.
   *
   * @throws NullPointerException if {@code id} could not be found.
   */
  public void relocateEntity(int id, int newX, int newY) { // client side dead reckoning here
    final GraphicalEntity entity = entityMap.get(id);
    if (entity == null) {
      throw new NullPointerException();
    }
    Integer direction;
    if(entity.getX() - newX < -1){ direction = 1; } else if (entity.getX() - newX > 1 ){ direction = 2; } else { direction = 0; } // 0 = player not moving, 1 = moving right, 2 = moving left
    entity.relocate(newX,newY);
    prevEntityPos.put(id, direction); // store the last direction of the the entity
  }

  /**
   * Create a new entity.
   *
   * <p>If an entity already exists with the {@code id}, it is overwritten.
   *
   * @param id entity ID.
   * @param type type of the entity.
   * @param posX coordinate on x-axis.
   * @param posY coordinate on y-axis.
   */
  public void spawnEntity(int id, EntityEnum type, int posX, int posY) {
    entitiesLock.writeLock().lock();
    entityMap.put(id,factory.create(new Entity(type,id,posX,posY)));
    entitiesLock.writeLock().unlock();
    if (type.equals(PLAYER_BULLET)) {
      sound.shooting();
    }
  }

  /**
   * Remove an entity.
   *
   * <p>The entity is not going to be displayed any more, after the next repaint.
   *
   * @param id ID of the entity to be removed.
   *
   * @throws NullPointerException if the {@code id} could not be found.
   */
  public void wipeOutEntity(int id) {
    entitiesLock.writeLock().lock();
    GraphicalEntity entity = entityMap.remove(id);
    entitiesLock.writeLock().unlock();
    if (entity == null) {
      throw new NullPointerException();
    }
    if (entity.getType().equals(INVADER)) {
      sound.deadInvader();
    } else if (entity.getType().equals(PLAYER)) {
      sound.deadPlayer();
    }
  }

  /**
   * Translate an entire group of entities, all of the same {@code type}.
   *
   * @param type type of all entities in the group.
   * @param offsetX offset on X Axis.
   * @param offsetY offset on Y Axis.
   */
  public void translateGroup(EntityEnum type, int offsetX, int offsetY) {
    entitiesLock.readLock().lock();
    for (GraphicalEntity entity : entityMap.values()) {
      if (entity.getType().equals(type)) {
        entity.translate(offsetX,offsetY);
      }
    }
    entitiesLock.readLock().unlock();
  }
}
