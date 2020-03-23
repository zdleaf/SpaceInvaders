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

import java.util.concurrent.ThreadLocalRandom;
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
  
/*   NEED TO CHECK MAXIMUM PACKET SIZE - JSON BUCKET IS BEING CUT SHORT
  See UDPHandler 
  private static final int MAX_INCOMING_PACKET_SIZE = 1024; 

  check if data + new cmd > 1024, if so, split into multiple packets

*/
  // if our bucket is longer than MAX_INCOMING_PACKET_SIZE, we need to split into separate buckets
  @Override
  public void handleBucket(ArrayList<Command> commandBucket){
    // WRONG - at the moment it's waiting for the bucket to fill up-  we do not want to wait till we reach MAX packet size, we can send packets less than this
    String data = "";

    for(int idx = 0; idx < commandBucket.size(); idx++){
      Command command = commandBucket.get(idx);
      if (command == null) {
        throw new NullPointerException();
      }
      if (command.getProtocol().equals(UDP)) {
        String combined = data + command.toJson() + "~";
        if(combined.length() < UdpHandler.getPacketSize()){ // MAX_INCOMING_PACKET_SIZE from SERVER
          data += command.toJson() + "~"; // construct our combined JSON string delimited by "~"
          // break;
        } else { // send the bucket and start preparing the next bucket
          System.out.print("handleBucket - MULTI: " + data + "\n");
          DatagramPacket packet = new DatagramPacket(data.getBytes(),data.length());
          sendPacket(packet);
          data = "";
        }
      }
    }

    // do we need if nextChain = handle if not UDP?
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
