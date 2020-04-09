package spaceinvaders.command;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import spaceinvaders.command.Command;
import spaceinvaders.exceptions.CommandNotFoundException;
import java.util.ArrayList;

/** Builds commands. */
public abstract class CommandBuilder {
  private static final Gson GSON = new Gson();
  private static final JsonParser PARSER = new JsonParser();
  private Map<String,Command> commandMap;
  private Command command;
  private ArrayList<Command> commandArray = new ArrayList<Command>(); // Create an ArrayList object

  /** Create a builder capable of building the specified commands. */
  public CommandBuilder(Command ... commands) {
    commandMap = new HashMap<>();
    for (Command command : commands) {
      commandMap.put(command.getName(),command);
    }
    commandMap = Collections.unmodifiableMap(commandMap);
  }

  /**
   * Handling incoming buckets
   *
   * @throws JsonSyntaxException if the specified JSON is not valid.
   * @throws CommandNotFoundException if the command could not be recognized.
   * @throws NullPointerException if argument is {@code null}.
   */
  public void buildCommand(String json) throws JsonSyntaxException, CommandNotFoundException {
    if (json == null) {
      throw new NullPointerException();
    }
    // System.out.println("FULL JSON: " + json);
    String[] jsonObjects = json.split("~", 0); // handle multiple incoming JSON commands in one network packet - split by "~" token
    int shootCounter = 0;
    for(String item: jsonObjects){ // parse each JSON object
      // System.out.println("JSON SPLIT: " + item);
      JsonObject jsonObj = PARSER.parse(item).getAsJsonObject();
      String key = jsonObj.get("name").getAsString();
      Command value = commandMap.get(key);
      if (value == null) {
        throw new CommandNotFoundException();
      }

      // CHEAT DETECTION
      if (value.getName().equals("spaceinvaders.command.server.PlayerShootCommand")) { shootCounter++; }
      if (shootCounter > 7){ System.out.println("CHEAT DETECTED"); commandArray.clear(); return; }

      command = GSON.fromJson(item,value.getClass());
      commandArray.add(command);
      // System.out.println("Added command to commandArray");
    }
  }

  /** Get the last command built. */
  public Command getCommand() {
    return command;
  }

  // get the commandArray
  public ArrayList<Command> getCommandArray() {
    System.out.println("getCommandArray(): " + commandArray.size() + " " + commandArray);
    return commandArray;
  }

  public void clearCommandArray(){
    commandArray.clear();
  }
}
