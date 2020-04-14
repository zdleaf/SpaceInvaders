For a diff file containing all amendments and changes to the code, see diff.diff file in root directory or clone the original repo and run the following Linux command:
diff -ENwbur -x ".git" -x "*.html" -x "*.css" -x "*target*" -x ".vscode*" ./ ../Original/SpaceInvaders/ > diff.diff

Artificial Network delay
    - In client/ClientConfig.java, each player is assigned a random ping with ThreadLocalRandom from a given range
    - In client/network/UdpSender.java:sendPacket() we Thread.sleep() by this ping value before sending each packet, simulating network latency
    - This ping integer is sent to the server along with the other Player information (e.g. username etc) via the SetPlayerIdCommand as the server requires it for bucket synchronisation
    - The server stores the ping with the player in their instance of Player.java

Bucket Synchro
    - Game.java - before we start the game, we compare every players ping, and set a delay for each Player. this is the difference between that players ping and the person with the highest ping. we can then delay execution of events for each player by this amount.
    - GameLoop.java - execution of events is done in GameLoop.java, we loop through each player in the list and execute their commands
        - If we were to delay one players commands with a Thread.Sleep(delay) here, all commands and players after this would also be affected by the delay. For example, if the first player when we loop through the players list had a 50ms delay before execution, the next player to have their commands executed would also have had to wait 50ms
        - To resolve this, in Player.java we have implemented Comparable to be able to sort our player list by ping delay (overriding the compareTo() function)
        - We sort the player list by ping delay (lowest or no delay first), and execute these commands first, and delaying by the appropriate amount
        - We then keep track of how much we've already delayed, so for each successive player we can deduct the delay that has already occurred

    - There is also an implementation of "buckets" similar to Nagles algorithm. The standard code sent an individual UDP packet for each command, rather than grouping multiple commands into one packet to save network traffic.
        - In client/network/UdpSender.java there is handleBucket() which is a recursive function that takes an array of commands and splits it into a single combined JSON packet to be sent over the network. if the JSON output is larger than MAX_PACKET_SIZE, we recursively call the same function on the tail of the command list after sending the first bucket.
            - We delimit JSON commands in the packet using the "~" token
        - In client/mvc/GameModel.java we add commands to the bucket and there is a scheduled executor that sends the command buckets every 250 ms (BUCKET_DELAY var). This limits the rate that clients can send updates to the server, so faster latency players have less of an advantage (and latency is also adjusted for as above).
        - There is corresponding SERVER code to handle multiple commands in one packet in command/CommandBuilder.java, saving incoming commands into an array to be later called and executed from Connection.java
        - To improve on this approach we could handle incoming buckets on the server side in rounds, however this has not currently been implemented.
    - Key files for client side buckets:
        - client/mvc/GameModel.java - adds command to the bucket and sends the bucket client side at specified interval
        - client/network/UdpSender.java - client code to send commands as a JSON packet
        - command/CommandBuilder.java - buildCommand() builds the commandArray from incoming JSON
        - server/network/Connection.java - executeCommandArray() executes the commandArray server side

Smooth Corrections
    - In client/gui/entities/GraphicalEntity.java we have amended relocate() to move to the new position from the old position in 1 pixel intervals, with a 10ms Thread.sleep() inbetween to further smooth out the movement
    - TEST SMOOTH CORRECTIONS
        - Connection.java - TEST_SMOOTH_CORRECTIONS = true
        - GameLoop.java - Set PLAYER_POS_UPDATE to 10000 to delay player updates from server
        - GamePanel.java - Set DEADRECK_ENABLED to false

Dead Reckoning 
    - default settings: DEADRECK_DELAY = 300 ms, PLAYER_POS_UPDATE 150 ms
    - SERVER SIDE: In GameLoop.java we have made a new AutoSwitch called playerPosUpdate that is called from entitiesMove(), this sends an update of player position to the client every 150 ms via a MoveEntityCommand.
    - CLIENT SIDE: in GamePanel.java we have an executor that executes deadReckon() every 300 ms. This gets the previous direction and position (saved in the hashmap's prevEntityDirection amd prevEntityPosition when relocateEntity() is called) and moves the player in that direction.

    - to test and see how ships automatically move in absence of server update
        - GameLoop.java - set PLAYER_POS_UPDATE in GameLoop to 5000 or higher
        - GamePanel.java - set DEADRECK_DEBUG to true to enable debugging messages

Cheat and detection
    - In client/mvc/GameController.java we check if the player name == CHEAT_NAME, and if so set the cheatEnabled flag for the player
    - In the same file, if cheats are enabled, every time the player shoots, 11 bullets entities are created on top of each other. This enables the player bullet to go through multiple invaders/invader bullets.
    - Cheat detection is handled in command/CommandBuilder.java - we detect if there are more than 7 PlayerShootCommand's in the commandArray. If this is detected, we display and message and do not execute any of the commands in this commandArray.

Interest Management
    - Game.java
        - RefreshEntitiesCommand() is a command that updates/pushes every single entity from the game server to the client
        - after the initial push, the comments in code suggests this was meant to be called every 8 seconds, but due to a bug in the game counter logic, the condition to execute it never was met
        - this was fixed to send a RefreshEntitiesCommand() to each player every 2 seconds
        - see refreshEntities() command - the entities that get updated/pushed to the player depends on the X position of the player, i.e. if player is in left half of screen, only push entities that are in the left half and vice versa. this is done with a call to getEntities() in World.java.
        
    - World.java
        - added getEntities(string flag) which will only add entities either on left or right based on the flag arguments passed ("left"/"right")
        - the only exception is PLAYER ships which we always want to display