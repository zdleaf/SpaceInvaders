package spaceinvaders.server.game;

import static spaceinvaders.game.EntityEnum.INVADER;
import static spaceinvaders.game.EntityEnum.PLAYER;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;
import spaceinvaders.command.Command;
import spaceinvaders.command.client.FlushScreenCommand;
import spaceinvaders.command.client.PlayersLostCommand;
import spaceinvaders.command.client.PlayersWonCommand;
import spaceinvaders.command.client.QuitGameCommand;
import spaceinvaders.command.client.RefreshEntitiesCommand;
import spaceinvaders.command.client.SetPlayerNamesCommand;
import spaceinvaders.command.client.StartGameCommand;
import spaceinvaders.game.GameConfig;
import spaceinvaders.server.game.world.ClassicWorldBuilder;
import spaceinvaders.server.game.world.World;
import spaceinvaders.server.game.world.WorldDirector;
import spaceinvaders.server.player.Player;
import spaceinvaders.utility.Couple;
import spaceinvaders.utility.Service;
import spaceinvaders.utility.ServiceState;

import spaceinvaders.server.game.world.LogicEntity;
import java.util.Iterator;
import spaceinvaders.game.EntityEnum;

/**
 * The actual gameplay.
 *
 * <p>The game loop is being run here.
 */
class Game implements Service<Void> {
  private static final Logger LOGGER = Logger.getLogger(Game.class.getName());
  private static final int FRAMES_PER_SECOND = 40;
  private static final boolean PREDICTABLE_GAME = GameConfig.getInstance().isPredictable();

  private final ServiceState state = new ServiceState();
  private final List<Player> team;
  private final World world;
  private final Random rng;
  private final GameLoop gameLoop;

  /**
   * Create a new game.
   *
   * @param team players joining this game.
   * @param threadPool used to for game threads.
   *
   * @throws NullPointerException if any of the arguments is {@code null}
   */
  public Game(List<Player> team, ExecutorService threadPool) {
    if (team == null || threadPool == null) {
      throw new NullPointerException();
    }
    this.team = team;

    /* Build world. */
    WorldDirector director = new WorldDirector(new ClassicWorldBuilder());
    List<Integer> idList = new ArrayList<>(team.size());

    // Bucket Synchronization
    // set delay for each player which is the difference between their ping, and the highest ping of all other players
    for(int i = 0; i < team.size(); i++){
      for(int j = i+1; j < team.size(); j++){
          Integer temp = 0;
          if(team.get(j).getPing() > team.get(i).getPing()){
              temp = team.get(j).getPing() - team.get(i).getPing();
              if(team.get(i).getDelay() < temp){
                team.get(i).setDelay(temp);
              }
              
          }
          else if(team.get(i).getPing() > team.get(j).getPing()){
              temp = team.get(i).getPing() - team.get(j).getPing();
              if(team.get(j).getDelay() < temp){
                team.get(j).setDelay(temp);
              }
          }
      }
    }

    for (Player player : team) {
      idList.add(player.getId());
      System.out.println("ID: " + player.getId() + ", name: " + player.getName() + ", ping: " + player.getPing() + ", delay: " + player.getDelay()); // print ping, delay and ID for each player
    }
    director.makeWorld(idList);
    world = director.getWorld();

    if (PREDICTABLE_GAME) {
      rng = new Random(1103515245);
    } else {
      rng = new Random();
    }

    gameLoop = new GameLoop(team,world,rng,threadPool);

    state.set(true);
  }

  // Interest Management - call RefreshEntitiesCommand only for entities within the players half
  final GameConfig config = GameConfig.getInstance();
  private void refreshEntities(){
    for (Player player : team) {
      Iterator<LogicEntity> playerIt;
      playerIt = world.getIterator(EntityEnum.PLAYER);
      while (playerIt.hasNext()) {
        LogicEntity playerEntity = playerIt.next();
        if (playerEntity.getId() == player.getId()) {
          System.out.println("Player: " + playerEntity.getId() + " X pos: " + playerEntity.getX());
          if(playerEntity.getX() < config.frame().getWidth()/2){
            player.push(new RefreshEntitiesCommand(world.getEntities("left")));
          }
          else if(playerEntity.getX() >= config.frame().getWidth()/2){
            player.push(new RefreshEntitiesCommand(world.getEntities("right")));
          }
        }
      }
    }
  }

  /**
   * Start the game.
   *
   * <p>The game loop is executed in the following manner:<br>
   * - process the user input<br>
   * - update the game state (advance the game simulation)<br>
   * - send the output
   *
   * @throws ExecutionException if an exception occurs during execution.
   * @throws InterruptedException if the service is interrupted prior to shutdown.
   */
  @Override
  public Void call() throws ExecutionException, InterruptedException {
    StringBuffer buf = new StringBuffer();
    for (Player player : team) {
      buf.append(player.getName() + "@" + player.getId() + " ");
    }
    LOGGER.info("Started game " + hashCode() + " with players: " + buf.toString());
    refreshEntities();
    flushCommands();
    List<Couple<Integer,String>> idToName = new ArrayList<>(team.size());
    for (Player player : team) {
      idToName.add(new Couple<Integer,String>(player.getId(),player.getName()));
    }
    distributeCommand(new SetPlayerNamesCommand(idToName));
    distributeCommand(new StartGameCommand());
    distributeCommand(new FlushScreenCommand());
    flushCommands();

    gameLoop.call();
    try {
      int frameCounter = 0;
      while (state.get()) {
        boolean commandsAvailable = false;
        gameLoop.processInput();
        gameLoop.update();
        Command[] commands = gameLoop.drainCommands();
        if (0 != commands.length) {
          commandsAvailable = true;
        }
        for (Command command : commands) {
          distributeCommand(command);
        }
        if (world.count(PLAYER) == 0) {
          distributeCommand(new PlayersLostCommand());
          state.set(false);
          flushCommands();
          break;
        } else if (world.count(INVADER) == 0) {
          distributeCommand(new PlayersWonCommand());
          state.set(false);
          flushCommands();
          break;
        }
        /* Do a complete refresh every 2 seconds. */
        frameCounter = frameCounter % (FRAMES_PER_SECOND * 2);
        // System.out.println("frameCounter=" + frameCounter);
        if (frameCounter == (FRAMES_PER_SECOND * 2) - 1) {
          //System.out.println("RefreshEntitiesCommand COMPLETE ENTITY REFRESH");
          refreshEntities();
          commandsAvailable = true;
          frameCounter = 0;
        } else {
          ++frameCounter;
        }
        if (commandsAvailable) {
          distributeCommand(new FlushScreenCommand());
          flushCommands();
        }
        Thread.sleep(1000 / FRAMES_PER_SECOND);
      }
    } catch (InterruptedException intException) {
      if (state.get()) {
        throw new InterruptedException();
      }
    } finally {
      distributeCommand(new QuitGameCommand());
      shutdown();
    }

    LOGGER.info("Game " + hashCode() + " terminated");

    return null; 
  }

  /** Close the connections of all players in the team and stop all running subtasks.*/
  @Override
  public void shutdown() {
    state.set(false);
    for (Player it : team) {
      it.close();
    }
    gameLoop.shutdown();
  }

  /**
   * Forward {@code command} to all the team.
   *
   * @throws NullPointerException if the argument is {@code null}.
   */
  private void distributeCommand(Command command) throws InterruptedException {
    if (command == null) {
      throw new NullPointerException();
    }
    for (Player player : team) {
      player.push(command);
    }
  }

  private void flushCommands() {
    for (Player player : team) {
      player.flush();
    }
  }
}
