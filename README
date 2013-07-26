## ModMode ##
*Keeping staff and players separate since 1869*

#### Configuration Example
    vanished: []
    modmode: []
    allow:
       flight: true

`vanished`, and `modmode` are used internally and should not be manually edited.

The config option `allow.flight` will allow ModMode players to fly. If not configured it will default to false.

This now requires LogBlock to build, we could not find a maven repo with LogBlock in it, so you will need to grab the source code from [here](https://github.com/LogBlock/LogBlock/) and then run

    mvn clean install

    
#### Configuring Other Plugins

In order to log ModMode edits under a different player name, LogBlock must be configured to fire custom events, in plugins/LogBlock/config.yml:

    consumer:
      fireCustomEvents: true


#### Permissions Setup

In addition to the permissions specific to the ModMode plugin (described in plugin.yml), the VanishNoPacket plugin requires at least the vanish.vanish permission, granted to the ModMode group, and probably also vanish.see and vanish.silentjoin. 
