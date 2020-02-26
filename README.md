## ModMode ##
*Keeping staff and players separate since 1869.*

### Configuration
#### Permissions

ModMode is dependent on LuckPerms 4.x in the [4.1.x series releases](https://github.com/NerdNu/ModMode/releases/tag/v4.1.1) or LuckPerms 5.x in the [4.2.x series releases and above](https://github.com/NerdNu/ModMode/releases/tag/v4.2.0).

The way ModMode manages permission groups is tightly coupled to the current NerdNu group hierarchy. To configure ModMode and LuckPerms, it is therefore necessary to understand that hierarchy, which for the PvE server is as follows:

 * `default`
   * `Moderators`
     * `ForeignServerAdmins`
       * `CAdmins`
     * `ModMode`
       * `super`
         * `HeadAdmins`
         * `PAdmins`
         * `TechAdmins`

In addition, both `ForeignServerAdmins` and `super` groups inherit the `AdminChat` group for in-game, admin-level private group chat.

The salient features of the main hierarchy are:

 * The `ModMode` group adds the extra permissions that `Moderators` need to do their staff duties, such as teleporting to ModReqs. Some of those permissions are explicitly negated in the `Moderators` group, so `ModMode` has a higher weight/priority to ensure that its permission nodes override those negated permissions.
 * `HeadAdmins`, `PAdmins` and `TechAdmins` inherit from `super` which adds `WorldEdit` and other power-user commands to `ModMode` permissions. This means that these groups *can perform staff tasks without entering ModMode*.
 * Administrators from "foreign" (other) servers inherit from `ForeignServerAdmins` and so can't perform staff tasks (except admin chat) without first entering `ModMode`.
 
When a staff member enters ModMode, the ModMode plugin must ensure that player receives the `ModMode` group. There are three cases:

 1. For `HeadAdmins`, local server admins (e.g. `PAdmins` on PvE) and `TechAdmins` no permission changes are required; the staff member already inherits the `ModMode` group.
 1. Staff in the `Moderators` group need to be switched into the `ModMode` group, which has a superset of the `Moderators` permissions, plus some overrides.
 1. Staff in a descendent of the `ForeignServerAdmins` group need the `ModMode` group in addition to their current group so that they can still use admin private chat and show up with their correct designation in `/list`, which looks for a permission node to detect a staff member's official title.

Case 1 is trivial. Cases 2 and 3 are handled by the following LuckPerms permission tracks, respectively:

 * `plugins/LuckPerms/yaml-storage/tracks/modmode-track.yml`:
```
name: modmode-track
groups:
- moderators
- modmode
```
 * `plugins/LuckPerms/yaml-storage/tracks/foreign-server-admins-modmode-track.yml`:
```
name: foreign-server-admins-modmode-track
groups:
- modmode
- modmode
```

These track names are referenced in the default configuration of ModMode. The `modmode-track` track is a straightforward realisation of Case 2 permission changes. The `foreign-server-admins-modmode-track` track is a bit of a hack. Since `ForeignServerAdmins` (e.g. `CAdmins` on PvE) don't have the `ModMode` group, LuckPerms adds the `ModMode` group in order to put them on the track when promoting the admin along the track to enter ModMode, and removes that group when they leave ModMode. The second `- modmode` entry in the track file is never "used", but is there to keep LuckPerms satisfied that the track meets the minimum requirement for promotion, which is that at least two groups are listed.

An alternate way to express the `foreign-server-admins-modmode-track` would be to list the groups as `foreignserveradmins` and `modmode`, however, for this to work, all such admins would need to be explicitly granted `ForeignServerAdmins` in addition to their main group (e.g. `CAdmins`) because LuckPerms does not consider inherited groups for promotion. The problem with this solution is that it makes assigning a player to a group no longer a single group change (e.g. `/lp user someguy parent set CAdmins`).

A further wrinkle is that LuckPerms apparently uses the weight/priority of descendant groups to determine the priority of a node. If a CAdmin is in ModMode, then they are in both the `CAdmins` and `ModMode` groups. Whether they can teleport to ModReqs or not (permission node `modreq.teleport`) depends on which of those two groups has the higher priority, because LuckPerms seems to look for the permission (or its negation) starting at the higher priority group. So if `CAdmins` has the higher priority, then LuckPerms works up the hierarchy to the `Moderators` group, finds the negated `modreq.teleport` permission and calls it a day; the player cannot teleport, even though `ModMode` has a higher priority than `Moderators`. *In practice, that means that the `ModMode` group should have a higher priority than any group descended from `ForeignServerAdmins`.*


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
1. `org.spigotmc:spigot-api:1.13.1-R0.1-SNAPSHOT`
2. `me.lucko.luckperms:luckperms-api 4.3` or `net.luckperms:api:5.0`
3. `org.kitteh:VanishNoPacket:3.19.1`
4. `de.diddiz:logblock:dev-SNAPSHOT`
5. `nu.nerd:NerdBoard:1.0.0`

If you are unable to locate a Maven repository for any of the dependencies you may install the dependency to your local repository by downloading the source code for the appropriate project and running `mvn clean install`.
    
#### Configuring Other Plugins

In order to log ModMode edits under a different player name, LogBlock must be configured to fire custom events. This is achieved by ensuring the `consumer.fireCustomEvents` option in in LogBlock's `config.yml` is set to `true`:

    consumer:
      fireCustomEvents: true
