package spaceinvaders.utility;
import java.util.ArrayList;
import java.net.DatagramPacket;

/**
 * Chain of Responsibility.
 *
 * <p>A pattern for organizing the execution of processing flows.
 */
public interface Chain<T> {
  /** Either handles the task or passes it to the next in chain. */
  public void handle(T task);
  public void handleBucket(ArrayList<T> bucket);
  public void sendPacket(DatagramPacket packet);
  /**
   * @param next next in chain.
   */
  public void setNext(Chain<T> next);
}
