package spaceinvaders.server.player;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.logging.Logger;
import spaceinvaders.command.Command;
import spaceinvaders.server.network.Connection;

/** A player which has established a connection and is ready to join a game. */
public class Player implements Comparable<Player> {
  private static final Logger LOGGER = Logger.getLogger(Player.class.getName());

  private final Connection connection;
  private final Future<Void> connectionFuture;
  private String name;
  private Integer teamSize;
  private Integer ping;
  private Integer delay = 0;

  // implements Comparable<Player> to enable us to order/sort Player objects by their delay
  @Override
  public int compareTo(Player player) {
      int compareDelay=((Player)player).getDelay();
      return this.delay-compareDelay; // ascending order
  }

  /**
   * Wrap a player around the specified connection.
   *
   * @param connection the part of this player used for network communication.
   * @param connectionExecutor used to run tasks needed by the {@code connection}.
   *
   * @throws RejectedExecutionException if a subtask cannot be executed.
   * @throws NullPointerException if an arguments is {@code null}.
   */
  public Player(Connection connection, ExecutorService connectionExecutor) {
    if (connection == null || connectionExecutor == null) {
      throw new NullPointerException();
    }
    this.connection = connection;
    connectionFuture = connectionExecutor.submit(connection);
  }

  /**
   * Push a command to the client.
   *
   * <p>The command is not sent over the network until {@link #flush() flush} is called.
   *
   * @throws NullPointerException if the command is {@code null}.
   */
  public void push(Command command) {
    if (command == null) {
      throw new NullPointerException();
    }
    connection.send(command);
  }

  /**
   * Pull all commands received by the player.
   *
   * @return a list containing all commands received or an empty list if there are none.
   */
  public List<Command> pull() {
    return connection.readCommands();
  }

  /**
   * Flush commands to the client.
   *
   * <p>All pushed commands are sent over the network.
   */
  public void flush() {
    connection.flush();
  }

  /** Close the connection. */
  public void close() {
    LOGGER.info("Connection " + getId() + " closed");
    connection.shutdown();
    connectionFuture.cancel(true);
  }

  public boolean isOnline() {
    return !connectionFuture.isDone();
  }

  public int getId() {
    return connection.hashCode();
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public int getTeamSize() {
    return teamSize;
  }

  public void setTeamSize(int teamSize) {
    this.teamSize = teamSize;
  }

  public Integer getPing() {
    return ping;
  }

  public void setPing(Integer ping) {
    this.ping = ping;
  }

  public Integer getDelay() {
    return delay;
  }

  public void setDelay(Integer delay) {
    this.delay = delay;
  }

  /**
   * Set the remote port to which UDP packets should be sent.
   *
   * <p>This remove port represents the UDP port on which the client is listening. It is not the
   * same port as the one from which the client is sending UDP packets.
   */
  public void setUdpDestinationPort(int port) {
    connection.setUdpChain(port);
  }
}
