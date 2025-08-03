## ModMode ##
*Keeping staff and players separate since 1869.*

### Dependencies

| Dependency     | ModMode 4.1.x series releases | ModMode 4.2.x series releases | ModMode 5.0.x series releases |
|----------------|-------------------------------|-------------------------------|-------------------------------|
| Server         | Spigot                        | Spigot                        | PaperMC                       |
| LuckPerms      | 4.x                           | 5.x                           | 5.3                           |
| VanishNoPacket | 3.19.1                        | 3.19.1                        | 3.22                          |
| LogBlock       | dev-SNAPSHOT                  | dev-SNAPSHOT                  | Not necessary                 |
| CoreProtect    | Not necessary                 | Not necessary                 | 22.4                          |
| NerdBoard      | 1.0.0                         | 1.0.0                         | Not necessary                 |
| TAB            | Not necessary                 | Not necessary                 | 5.2.4                         |

### Configuration

ModMode groups are handled through the config.yml and have a number of customizable options. These are explained pretty decently in the config.yml, but *poof*, a table!

| Group Config Option          | What it does                                                                                                                                          |
|------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| commandMeta.description      | The description of the group's command in /help                                                                                                       |
| commandMeta.permission       | The permission node required to enter this group/run its commands                                                                                     |
| commandMeta.aliases          | A list of other commands that can also be used for this group. Don't include a slash (/). Example: /mm                                                |
| actions.activate.before      | A list of commands the player will run BEFORE entering this group. Don't include a slash (/)                                                          |
| actions.activate.after       | A list of commands the player will run AFTER entering this group. Don't include a slash (/)                                                           |
| actions.deactivate.before    | A list of commands the player will run BEFORE leaving this group. Don't include a slash (/)                                                           |
| actions.deactivate.after     | A list of commands the player will run AFTER leaving this group. Don't include a slash (/)                                                            |
| details.trackName            | The name of the LuckPerms track this group will slide along.                                                                                          |
| details.prefix               | The prefix that will appear before the names of members of this group above their heads and in the tab list.                                          |
| details.allowFlight          | Boolean to determine if members of this group are able to fly.                                                                                        |
| details.allowCollisions      | Boolean to determine if members of this group collide with other entities.                                                                            |
| details.suppressJoinMessages | Boolean to determine if members of this group can quit/join the server silently. No quit/join messages will be displayed until they leave this group. |
| details.interactWithItems    | Boolean to determine of members of this group can toggle the ability to pick up/drop items.                                                           |

ModMode and LuckPerms will handle the permissions themselves as long as the track and players are set up so that each potential member has the first permission group in the track.

### Commands

The commands are generated automatically based on the group name specified in the config file. For example, the group named "modmode" will create the command /modmode.

The plugin will also tie the aliases specified to the main command.

This is the command structure of the plugin, where `modmode` is a placeholder for whatever groups are present in the config.yml file.

```
/modmode - Toggles modmode for the executing player.
│
├── /modmode on
│   └── Enables ModMode for the executing player.
│
├── /modmode off
│   └── Disables ModMode for the executing player.
│
└── /modmode iteminteract - Toggles item interactions while in ModMode.
    ├── /modmode iteminteract on
    │   └── Allows item interactions while in ModMode.
    │
    └── /modmode iteminteract off
        └── Prevents item interactions while in ModMode.

/vanish
└── Vanishes the player.
    • Permission: `modmode.vanish`
    
/unvanish
└── Unvanishes the player.
    • Permission: `modmode.unvanish`
    
/reloadmodmode
└── Reloads the ModMode configuration.
    • Permission: `modmode.reload`

```

### "State change failed" Error

Sometimes LuckPerms fails to properly move a player and this error will appear. Worry not, as this is the plugin setting itself back up for success.

When this happens, the plugin removes this player from all groups and demotes them from all group-owned tracks to reset their status. This gives LuckPerms the right starting point to try again. If this error continues to appear more than once, there's likely something wrong with your LuckPerms setup.

### Developers
#### Packaging

Note the following dependencies in `pom.xml`:
1. `org.spigotmc:spigot-api:1.13.1-R0.1-SNAPSHOT`
2. `me.lucko.luckperms:luckperms-api 4.3` or `net.luckperms:api:5.3`
3. `org.kitteh:VanishNoPacket:3.22`
4. `de.diddiz:logblock:dev-SNAPSHOT` or `net.coreprotect:coreprotect:22.4`
5. `nu.nerd:NerdBoard:1.0.0` or `com.github.NEZNAMY:TAB-API:5.2.4`

If you are unable to locate a Maven repository for any of the dependencies you may install the dependency to your local repository by downloading the source code for the appropriate project and running `mvn clean install`.
    
#### Configuring Other Plugins

In order to log ModMode edits under a different player name, LogBlock must be configured to fire custom events. This is achieved by ensuring the `consumer.fireCustomEvents` option in in LogBlock's `config.yml` is set to `true`:

    consumer:
      fireCustomEvents: true
