Coursework for Distributed Systems module as part of MSc Computer Science degree. The goal was to add some functionality to handle networking and consistency issues in existing multiplayer games. 

Original code forked from github.com/apetenchea/SpaceInvaders

See CHANGES.txt and diff.diff file to see the updates/changes made to the game as below:

The additional functionality required was as follows:

- Introduce an artificial network delay to simulate some of the challenges which take place in networked games.
- Implement bucket synchronization so that all clients have the same frame rate so the game can be considered "fair"

- Implement dead reckoning so that players continue to update in the event that network problems cause delays in updates received about player position
- Implement smooth corrections so that players move towards the correct position after drops in network communication to prevent jerky animation

- Introduce some form of cheat which allows one client to perform a game action that cannot be done by other clients. This can take multiple forms. One example would be to increase the movement speed of one player
- Introduce some checking on the server side which allows the cheating to be detected

- Implement interest management so that a large map exists on the primary copy of the game while information on objects which are sent to secondary copies should only contain information relavent to that client

