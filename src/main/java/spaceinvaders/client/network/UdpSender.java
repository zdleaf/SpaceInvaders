package spaceinvaders.client.network;

import static java.util.logging.Level.SEVERE;
import static spaceinvaders.command.ProtocolEnum.UDP;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.logging.Logger;
import spaceinvaders.command.Command;
import spaceinvaders.utility.Chain;
import spaceinvaders.server.network.udp.UdpHandler;

import java.util.ArrayList; // for commandBucket;

/** Send commands using the UDP protocol. */
class UdpSender implements Chain<Command> {
  private static final Logger LOGGER = Logger.getLogger(UdpSender.class.getName());

  private final DatagramSocket socket;
  private Chain<Command> nextChain;
  private final Integer ping;

  /**
   * Construct a sender that will communicate through the open {@code socket}.
   *
   * @throws NullPointerException if the specified socket is {@code null}.
   */
  public UdpSender(DatagramSocket socket, Integer ping) throws IOException {
    if (socket == null) {
      throw new NullPointerException();
    }
    this.socket = socket;
    this.ping = ping;
  }

  /**
   * @throws NullPointerException if {@code command} is {@code null}.
   */
  @Override
  public void handle(Command command) {
    if (command == null) {
      throw new NullPointerException();
    }
    if (command.getProtocol().equals(UDP)) {
      String data = command.toJson();
      data += "~" + data;
      LOGGER.info("JSON " + data);
      DatagramPacket packet = new DatagramPacket(data.getBytes(),data.length());
      sendPacket(packet);
    } else {
      if (nextChain == null) {
        // This should never happen.
        throw new AssertionError();
      }
      nextChain.handle(command);
    }
  }
  
/*   
    handleBucket() 
    - recursively splits the commandBucket into packets smaller than MAX_INCOMING_PACKET_SIZE (See UDPHandler) and sends the packets
*/
  @Override
  public void handleBucket(ArrayList<Command> commandBucket){
    if(commandBucket.size() == 0){ return; } // base case for recursion
    String data = "";
    int idx;
    for(idx = 0; idx < commandBucket.size(); idx++){
      if (commandBucket.get(idx) == null) {
        throw new NullPointerException();
      }
      if (commandBucket.get(idx).getProtocol().equals(UDP)) {
        if(data.length() + commandBucket.get(idx).toJson().length() < UdpHandler.getPacketSize()){
          data += commandBucket.get(idx).toJson() + "~";
        } else { break; }
      }
    } 

    // send the bucket
    DatagramPacket packet = new DatagramPacket(data.getBytes(),data.length());
    sendPacket(packet);

    // recursively call handleBucket on the tail
    ArrayList<Command> overflow = new ArrayList<Command>(commandBucket.subList(idx, commandBucket.size()));
    handleBucket(overflow);
  }

  @Override
  public void sendPacket(DatagramPacket packet){
    try {
      Thread.sleep(ping); // delay artifically by ping (ms)
      socket.send(packet);
    } catch (Exception exception) {
      // Do not stop the game in case one packet fails.
      LOGGER.log(SEVERE,exception.toString(),exception);
    }
  }

  @Override
  public void setNext(Chain<Command> nextChain) {
    this.nextChain = nextChain;
  }
}
