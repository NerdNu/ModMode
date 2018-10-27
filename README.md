## ModMode ##
*Keeping staff and players separate since 1869*

### Configurables

#### Permissions

This plugin now depends on LuckPerms. A future update will bring Vault compatibility. The following options should be defined in `config.yml`:

    permissions:
        modmode-group: ModMode
        moderator-group: moderators
        persistent-groups:
        - group.example

The `modmode-group` and `moderator-group` should describe your server's permission groups for ModMode and moderators. The `persistent-groups` is a string list describing groups that should be preserved when a player enters ModMode, i.e. they will not lose that group.

#### Commands

Commands can be configured to run before and after the activation and deactivation of ModMode by a player. For example, the default configuration will run `/we cui` and `/lb toolblock on` immediately after a player activates ModMode.

    commands:
      activate:
        before: []
        after:
        - /we cui
        - /lb toolblock on
      deactivate:
        before: []
        after: []
        
#### Miscellaneous

There are two miscellaneous options: `allow.flight` and `allow.collisions`. If `flight` is true, then players in ModMode will be able to fly (a la creative mode); if `collisions` is true, then players in ModMode will be able to physically collide with the hit boxes of other entities.

    allow:
       flight: true
       collisions: false

Note that any other lines in the `config.yml` are used internally and should not be manually edited.

### Developers

#### Packaging

Note the following dependencies in `pom.xml`:
1. `org.spigotmc.spigot-api 1.13.1-R0.1-SNAPSHOT`
2. `me.lucko.luckperms.luckperms-api 4.3`
3. `org.kitteh.VanishNoPacket 3.19.1`
4. `de.diddiz.logblock dev-SNAPSHOT`
5. `nu.nerd.NerdBoard 1.0.0`

If you are unable to locate a Maven repository for any of the dependencies you may install the dependency to your local repository by downloading the source code for the appropriate project and running `mvn clean install`.
    
#### Configuring Other Plugins

In order to log ModMode edits under a different player name, LogBlock must be configured to fire custom events. This is achieved by ensuring the `consumer.fireCustomEvents` option in in LogBlock's `config.yml` is set to `true`:

    consumer:
      fireCustomEvents: true

#### Permissions Setup

In addition to the permissions specific to this plugin (described in `plugin.yml`), the VanishNoPacket plugin requires at least the `vanish.vanish` permission, granted to the ModMode group, and probably also `vanish.see` and `vanish.silentjoin`. 
