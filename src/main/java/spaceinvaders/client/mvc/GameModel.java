package spaceinvaders.client.mvc;

import static java.util.logging.Level.SEVERE;

import java.util.Observable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TransferQueue;
import java.util.logging.Logger;
import spaceinvaders.client.network.NetworkConnection;
import spaceinvaders.command.Command;
import spaceinvaders.command.CommandDirector;
import spaceinvaders.command.client.ClientCommandBuilder;
import spaceinvaders.exceptions.IllegalPortNumberException;
import spaceinvaders.exceptions.SocketOpeningException;
import spaceinvaders.utility.Service;
import spaceinvaders.utility.ServiceState;

import java.lang.System; // for currentTimeMillis()
import java.util.ArrayList; // for commandBucket;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Provides the game data.
 *
 * <p>This is a gateway to the server. It is used to get data out of the server and feed it into
 * the controller. Every command that is sent to the server goes through here.
 */
public class GameModel implements Model {
  private static final Logger LOGGER = Logger.getLogger(GameModel.class.getName());
  private static final int BUCKET_DELAY = 3000;
  private final TransferQueue<String> incomingQueue = new LinkedTransferQueue<>();
  private final CommandDispatcher dispatcher = new CommandDispatcher();
  private final ExecutorService connectionExecutor = Executors.newSingleThreadExecutor();
  private final ExecutorService dispatcherExecutor = Executors.newSingleThreadExecutor();
  private final ServiceState connectionState = new ServiceState();
  private final ServiceState gameState = new ServiceState();
  private NetworkConnection connection;

  private static ArrayList<Command> commandBucket = new ArrayList<Command>();

  // schedule the bucket to be sent every X seconds with ScheduledExecutorService.scheduleAtFixedRate - see also sendBucket()
  ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

  Runnable sendBucket = new Runnable() {
    public void run() {
        if(!commandBucket.isEmpty()){
          System.out.println("Sending bucket: " + commandBucket);
          connection.send(commandBucket);
          commandBucket.clear();
        }
    }
  };

  public GameModel() {
    dispatcherExecutor.submit(dispatcher);
  }

  /**
   * Initialize a new game.
   *
   * @throws SocketOpeningException if the connection could not be established.
   * @throws ExecutionException if an exception occurs during execution.
   * @throws InterruptedException if the service is interrupted prior to shutdown.
   * @throws IllegalPortNumberException if the port parameter is not a valid port value.
   * @throws RejectedExecutionException if a task cannot be scheduled for execution.
   */
  @Override
  public Void call() throws SocketOpeningException, ExecutionException, InterruptedException {

    // send our command buckets every BUCKET_DELAY ms
    executor.scheduleAtFixedRate(sendBucket, 0, BUCKET_DELAY, TimeUnit.MILLISECONDS);

    // This will open up a network connection, and might throw exceptions.
    connection = new NetworkConnection(incomingQueue);
    Future<?> connectionFuture = connectionExecutor.submit(connection);
    final long checkingRateMilliseconds = 500;
    connectionState.set(true);
    while (connectionState.get()) {
      try {
        if (connectionFuture.isDone()) {
          connectionState.set(false);
          gameState.set(false);
          connectionFuture.get();
        }
        Thread.sleep(checkingRateMilliseconds);
      } catch (CancellationException | InterruptedException exception) {
        if (connectionState.get()) {
          gameState.set(false);
          throw new InterruptedException();
        }
        break;
      }
    }
    return null;
  }

  /**
   * Couple {@code contoller} with this model.
   *
   * <p>The controller will receive updates from this model.
   *
   * @throws NullPointerException if argument is {@code null}.
   */
  @Override
  public void addController(Controller controller) {
    if (controller == null) {
      throw new NullPointerException();
    }
    dispatcher.addObserver(controller);
  }

  /**
   * Add a command to the commandBucket to be sent every BUCKET_DELAY ms - see sendBucket()
   *
   * @throws NullPointerException if there is no connection.
   */
  @Override
  public void doCommand(Command command) {
    if (connection == null) {
      throw new NullPointerException();
    }
    if (command.getName() == "spaceinvaders.command.server.ConfigurePlayerCommand"){ // do not bucket the initial ConfigurePlayerCommand - we must respond within 1s or connection is closed
      connection.send(command);
    } else {
      commandBucket.add(command);
    }
  }

  @Override
  public void exitGame() {
    connectionState.set(false);
    gameState.set(false);
    if (connection != null) {
      connection.shutdown();
    }
    connection = null;
    incomingQueue.clear();
  }

  @Override
  public boolean getGameState() {
    return gameState.get();
  }

  @Override
  public void setGameState(boolean state) {
    gameState.set(state);
  }

  @Override
  public void shutdown() {
    exitGame();
    dispatcher.shutdown();
    connectionExecutor.shutdownNow();
    dispatcherExecutor.shutdownNow();
  }

  /** Takes data out of the incoming queue and forwards it to the controller. */
  private class CommandDispatcher extends Observable implements Service<Void> {
    private final ServiceState state = new ServiceState();

    /** 
     * Start deserializing and forwarding data.
     *
     * @throws InterruptedException if the service is interrupted prior to shutdown.
     */
    @Override
    public Void call() throws InterruptedException {
      CommandDirector director = new CommandDirector(new ClientCommandBuilder());
      state.set(true);
      while (state.get()) {
        String data = null;
        try {
          data = incomingQueue.take();
        } catch (InterruptedException interruptedException) {
          connectionState.set(false);
          gameState.set(false);
          if (state.get()) {
            state.set(false);
            throw new InterruptedException();
          }
          break;
        }
        if (data.equals("EOF")) {
          // Connection over.
          connectionState.set(false);
          gameState.set(false);
        } else {
          try {
            director.makeCommand(data);
            setChanged();
            // Notify the controller.
            notifyObservers(director.getCommand());
          } catch (Exception ex) {
            LOGGER.log(SEVERE,ex.toString(),ex);
          }
        }
      }
      return null;
    }

    @Override
    public void shutdown() {
      state.set(false);
    }
  }
}
