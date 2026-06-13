
# Emojis
[emo]

x = 
warn = ⚠
play = 
gplay = 
ok = 
admin = 
discord = 
team = 
plane = 
telegram = 
settings = 
book-open = 
filter = 
zoom = 

# Colors
[col]

ok = #26d657
warn = #fce174
err = #ec5784
cmd = #ec5784
cmdarg = yellow
mdc = #4c59e0
sysmsg = #adadad

[generic]

close = [{col.ok}]{emo.gplay} Close
cancel = [{col.err}]{emo.x} Cancel
ok = [{col.ok}]{emo.ok} Ok
server = Mindurka
discord = https://mindurka.cc/discord
special-settings-error = [{col.err}]Flags on this map are not set correctly:
                       =
                       = {errors}
                       =
                       = Map may not function correctly.

# spam Spam SPAM
welcome-message-title = Welcome message
welcome-message = [accent]We are in the process of completely rewriting server plugins.
                - Expect bugs!
                =
                = [orange]<<< ATTENTION >>>
                = [{col.err}]This server bans cheating, and may automatically ban you on notice.
                - If you'll get banned for cheating and can't prove you did not cheat, [yellow]you will
                - not be unbanned[] even if you promise to not use cheats anymore.
                =
                = Yes, hardware autoclickers and so-called "QOL" features like bypassing schemes
                - count too.
gamemode-uses-special-settings = [accent]Some of this gamemode's features don't work in vanilla.[]
                               =
                               = Install [{col.mdc}]MindurkaCompat[] from mod explorer to gain access to them.
map-uses-special-settings = [accent]Some of this map's features don't work in vanilla.[]
                          =
                          = Install [{col.mdc}]MindurkaCompat[] from mod explorer to gain access to them.
can-use-public-key = [accent][{col.mdc}]MindurkaCompat[] supports public key authorization.
                   = Public key authorization is more secure than traditional uuid-based authorization.
                   = To switch to use public key instead of uuid, type [green]/setkey[].
                   = See [#ec5784]/help setkey[] for more information.
mdc-outdated = [accent]Your version of [{col.mdc}]MindurkaCompat[] is out of date.[]
             =
             = [accent]Update the mod to the latest version from mod explorer.
restart-scheduled = [{col.warn}]Server will be restarted after the end of the round.

# Vars:
# - service (service name, empty if none)
# - isteam (whether the message is sent in team chat)
# - isadmin (whether the message is sent in admin chat)
# - tlstyle (translator style. none if message was not translated)
# - lang (original language message was in)
# - origmessage (original message)
# - teamcolor (color of the player team)
# - player (display name of the sender)
chat = {if {service} != () then ({generic.chat.service.{service}} )}
     : {if {isteam} = true then ({generic.chat.team} )}
     : {if {isadmin} = true then ({generic.chat.admin} )}
     : [scarlet][[{player}]
     : {if {tlstyle} = short then ( [gray]({lang})[])}
     : : [white]{if {tlstyle} = classic then {origmessage} else {message}}
     : {if {tlstyle} = classic then ( [lightgray]({message}))}
     : {if {tlstyle} = multiline then (\n{generic.chat.original})}

chat.original = [gray]Original ({lang}): []{origmessage}
chat.team = [{teamcolor}][[{emo.team} Team]
chat.admin = [scarlet][{emo.admin} Admins]
chat.service.discord = [#4c59e0][{emo.discord} Discord]
chat.service.telegram = [#76d0f8][{emo.telegram} Telegram]

vote.global = [{col.sysmsg}]Type [{col.ok}]y[] or [{col.err}]n[] to vote. {if {votes} < 0 then [{col.err}] else [{col.ok}]}{votes}/{threshold}
vote.team = [{col.sysmsg}]Type [{col.ok}]/vote y[] or [{col.err}]/vote n[] to vote. {if {votes} < 0 then [{col.err}] else [{col.ok}]}{votes}/{threshold}
vote.left = [{col.sysmsg}]{player} has left the game and their vote was removed.

vote.global.cancelled = [{col.err}]⚠ Vote was cancelled by admin {admin}.
vote.team.cancelled = [{col.err}]⚠ Team vote was cancelled by admin {admin}.

vote.try-global = [{col.err}]There's a global vote running. Use [{col.ok}]y[]/[{col.err}]n[] to vote.
vote.try-team = [{col.err}]There's a team vote running. Use [{col.ok}]/vote y[]/[{col.err}]/vote n[] to vote.

admin.ban = [{col.err}]{emo.warn} {target} was banned by {admin} for [orange]{reason}.
admin.kick = [{col.err}]{emo.warn} {target} was kicked by {admin} for [orange]{reason}.
admin.wave = [{col.ok}]A wave was skipped by admin {admin}.

spectating = [{col.ok}]You are now spectating.
no-longer-spectating = [{col.ok}]You are no longer spectating.

[generic.checks]
same-vote = [{col.err}] Your vote has not changed.
vote-filter = [{col.err}] You cannot vote in this poll.
cooldown = [{col.err}] Slow down! This command is on cooldown.
vote = [{col.err}] There's already a vote ongoing.
vote-spectator = [{col.err}] Spectators cannot start team votes.
unknown-command = [{col.err}] Could not find command [yellow]{command}[]. {if {suggestion} = () then (Check [yellow]/help[].) else (Did you mean [yellow]/{suggestion}[]?)}
unknown-command-console =  Could not find command [yellow]{command}[]. {if {suggestion} = () then (Check /help) else (Did you mean /{suggestion}?)}
invalid-arguments = [{col.err}] {cause}. Try [yellow]/help {command}[]!
invalid-arguments-console = {cause}. Usage: {command} {params}
permission = [{col.err}] You don't have permission to execute this command.
admin-action-permission = [{col.err}] You don't have permission to perform this action.
admin-action-no-target = [{col.err}] You must specify a target to perform an action.
admin-action-target-admin = [{col.err}] You cannot perform this action against another admin.

[generic.command]
disabled = Command [yellow]'/{command}'[] is disabled
disabled-console = Command '{command}' is disabled
missing = Missing parameter [yellow]'{commands.{command}.params.{param}}'[]
missing-console = Missing parameter '{param}'
invalid = Invalid parameter [yellow]'{commands.{command}.params.{param}}'[]
invalid-console = Missing parameter '{param}'
string-termination = Parameter [yellow]'{commands.{command}.params.{param}}'[] is not properly enclosed
string-termination-console = Parameter '{param}' is not properly enclosed
end-of-input = Not enough input
end-of-input-console = Not enough input
too-many-arguments = Too many arguments
too-many-arguments-console = Too many arguments
not-a-number = Parameter [yellow]'{commands.{command}.params.{param}}'[] must be a number
not-a-number-console = Parameter '{param}' must be a number
not-a-boolean = Parameter [yellow]'{commands.{command}.params.{param}}'[] must be [#26d657]yes[] or [#EC5784]no[]
not-a-boolean-console = Parameter '{param}' must be a boolean
invalid-map-id = Unknown map ID
invalid-map-id-console = Unknown map ID
zero-map-id = But it refused
zero-map-id-console = But it refused
unknown-map = Could not find the map
unknown-map-console = Could not find the map
unknown-player = Could not find the player
unknown-player-console = Could not find the player
unknown-unit = Unknown unit
unknown-unit-console = Unknown unit
unknown-unit-type = Unknown unit type
unknown-unit-type-console = Unknown unit type
unknown-status-effect = Unknown unit type
unknown-status-effect-console = Unknown status effect
invalid-item = Invalid item
invalid-item-console = Invalid item
invalid-team = Invalid team
invalid-team-console = Invalid team
invalid-duration = Not a valid duration for parameter [yellow]'{commands.{command}.params.{param}}'[]
invalid-duration-console = Not a valid duration for parameter {param}

[generic.kick]
merged = [{col.err}]This account was merged into another and thus no longer accessible.
       =
       = [accent]Merge action is irreversible. For more information see the FAQ section in [cyan]#help[] channel on our
       - [#4c59e0]Discord Server[].
       = [cyan]{generic.discord}
disconnected = [{col.err}]This account is no longer assigned to any users.
             =
             = [accent]If you'd like to reconnect it, create a post in [cyan]#help[] channel on our [royal]Discord Server[].
             = [cyan]{generic.discord}
key-validation-failure-mdc = [{col.err}]Key validation failure.
                           =
                           = [accent]The key on your device does not match the key on the server.
                           = Make sure you have imported a save from your old device. If you don't have access to
                           - it, create a post in [cyan]#help[] channel on our [#4c59e0]Discord Server[].
                           = [cyan]{generic.discord}
key-validation-failure-no-mdc = [{col.err}]Key validation failure.
                              =
                              = [accent]This account is protected. In order to log in, you must have
                              - [#4c59e0]MindurkaCompat[] installed. If you have any questions, create a port in
                              - [cyan]#help[] channel on our [#4c59e0]Discord Server[].
                              = [cyan]{generic.discord}
disabled = [{col.err}]This account was disabled.
         =
         = [accent]If you'd like to appeal this decision, create a post in [cyan]#appeals[] channel on our [#4c59e0]Discord Server[].
         = [cyan]{generic.discord}
shared = [{col.err}]This account was found to be shared between several players and thus no longer accessible.
       =
       = [accent]This decision cannot be appealed. For more information see the FAQ section in [cyan]#help[] channel on our
       - [#4c59e0]Discord Server[].
       = [cyan]{generic.discord}
another-location = [{col.err}]You are already logged in from another location.
         =
         = [accent]If you believe this is a mistake, create a post in [cyan]#help[] channel on our
         - [#4c59e0]Discord Server[].
         = [cyan]{generic.discord}
ban = [{col.err}]You were banned from this server.
    =
    = [accent]Ban ID: [yellow]{id}
    = [accent]User ID: [yellow]{userid}
    = [accent]Admin: {admin}
    = [accent]Reason: [#EC5784]{reason}
    = [accent]Remaining: [yellow]{remaining}
    =
    = [accent]If you believe there was a mistake, create a post in [cyan]#appeals[] channel on our
    - [#4c59e0]Discord Server[].
    = [cyan]{generic.discord}
kick = [{col.err}]You were kicked from this server.
     =
     = [accent]Kick ID: [yellow]{id}
     = [accent]User ID: [yellow]{userid}
     = [accent]Admin: {admin}
     = [accent]Reason: [#EC5784]{reason}
     = [accent]Remaining: [yellow]{remaining}
     =
     = [accent]If you believe there was a mistake, create a post in [cyan]#appeals[] channel on our
     - [#4c59e0]Discord Server[].
     = [cyan]{generic.discord}
votekick = [{col.err}]You were votekicked from this server.
         =
         = [accent]Votekick ID: [yellow]{id}
         = [accent]User ID: [yellow]{userid}
         = [accent]Initiator: {initiator}
         = [accent]Reason: [#EC5784]{reason}
         = [accent]Remaining: [yellow]{remaining}
         =
         = [accent]Votes for:
         = {each {vote} in {votes} ([gray]- {vote}) split (\n) join (\n)}
         =
         = [accent]If you believe there was a mistake, create a post in [cyan]#appeals[] channel on our
         - [#4c59e0]Discord Server[].
         = [cyan]{generic.discord}

[generic.gameover]

pvp = [accent]Game over! Team {team} has emerged victorious.
    =
    = Next map: [yellow]{map}[]

waves = [accent]Game over! Reached wave {wave}.
      =
      = Next map: [yellow]{map}[]

tie = [accent]Game over! Teams reached a tie.
    =
    = Next map: [yellow]{map}[]

attackWin = [accent]Game over! Map has been completed.
          =
          = Next map: [yellow]{map}[]

attackLose = [accent]Game over! All cores have been destroyed.
           =
           = [accent]Next map: [yellow]{map}[]

unexpected = [accent]Game over! All cores have been destroyed.
           =
           = Next map: [yellow]{map}[]

[generic.duration]
years = years
year = year
weeks = weeks
week = week
days = days
day = day
hours = hours
hour = hour
minutes = minutes
minute = minute
seconds = seconds
second = second
millis = milliseconds
milli = millisecond
subsecond = less than a second
inf = infinity

# Commands

[commands.help]

description = Get a list of available commands.
manpage = Get a list of available commands.
        =
        = If [blue][[page][] is specified, open the specified page.
        =
        = If [blue][[command][] is specified, give its extended description.
params = [page-or-command]
params.page = page
params.command = command

select-page.title =  Select a page
select-page.message = Currently on page {page}
title-page =  List of available commands
title-cmd =  Command help
          = [accent]/{command} [white]{commands.{command}.params}
man.title =  Command help
man.message = [accent]/{command} [white]{commands.{command}.params}
            =
            = {commands.{command}.manpage}
command = [accent]/{command} [white]{commands.{command}.params}
        = [lightgray]{commands.{command}.description}
invalid-page = [{col.err}] Page must be an integer between [yellow]1[] and [yellow]{max}[]. There are no secret pages behind there. We promise.
negative-page = [{col.err}] Page must be an integer between [yellow]1[] and [yellow]{max}[]. What are you looking at, anyway?
unknown-command = [{col.err}] [yellow]/{command} [{col.err}]is not a known command. I'm a teapot.

[commands.maps]

description = Get a list of available maps.
manpage = Get a list of available maps.
        =
        = If [blue][[page][] is specified, open the specified page.

params = [page]
params.page = page

select-page.title =  Select a page
select-page.message = Currently on page {page}
title-page =  List of available maps
man.title =  Command maps
map = [orange]{id}. [accent]{map} [white]by [accent]{author}
    = [white]{width}x{height}
invalid-page = [{col.err}] Page must be an integer between [yellow]1[] and [yellow]{max}[]. There are no secret pages behind there. We promise.
negative-page = [{col.err}] Page must be an integer between [yellow]1[] and [yellow]{max}[]. What are you looking at, anyway?

[commands.setkey]

description = Switch to using public key authorization.
manpage = Switch to using public key authorization.
        =
        = [#4c59e0]MindurkaCompat[] generates a key pair upon launch, which a compatible server can verify
        - in order to confirm that the owner of the key is authentic.
        =
        = Unlike with UUID, leaking which allows a malicious server admin to impersonate a player on any server,
        - leaking a public key is safe, as it is useless without its private key counterpart. Private key
        - is never sent other the network.
        =
        = After enabling public key authorization, you must import a new save on all devices you wish to access the
        - account from. Unlike with UUID, however, this should work out of the box when importing a save on steam.
        =
        = Public key authorization is as secure as is the private key on the device. Saves must not be shared anywhere
        - where a third party may access them. If you want to report a Mindustry bug, first reset all game data, and
        - then reproduce the bug. That way the keys remain safe.
        =
        = Public key authorization only works with compatible servers, and for others UUID-based authorization is used.
params =
title = Confirmation dialog
message = [accent]Do you want to enable public key authorization?
        =
        = [{col.err}]This action is irreversible.

ok = [#26d657] Successfully enabled public key authorization.
error = [{col.err}] Already enabled.
admin-protection = [#EC5784] You must log in before enabling key authorization on an admin account.
no-mdc = [{col.err}] No public key was shared.
       =
       = Install or enable [{col.mdc}]MindurkaCompat[] and repeat the command.

[commands.stats]

description = See player stats.
manpage = See player stats.
        =
        = This will also show all the IDs associated with a player.
params = [player]

dialog-title =  Stats
dialog-content = [accent] [lightgray]Name: {player}
               =
               = [{col.ok}] [lightgray]Blocks placed: [{col.ok}]{blocks-placed}
               = [{col.err}] [lightgray]Blocks broken: [{col.err}]{blocks-broken}
               = [orange] [lightgray]Games played: [orange]{games-played}
               = [cyan] [lightgray]Waves survived: [cyan]{waves}
               =
               = [accent]<<< WINS >>>
               = {gamemode-stats}
               =
               = [accent] [lightgray]Play time:
               = [accent]{playtime}

dialog-data-title =  Stats [lightgray]>> []{emoji} [{color}]{name}
dialog-data-wins = [accent] [lightgray]Wins: [accent]{wins}{if {ovas} != 0 then ( [red](1vas: [white]{ovas}[]))}
dialog-data-blocks-placed = [{col.ok}] [lightgray]Blocks placed: [{col.ok}]{blocks-placed}
dialog-data-blocks-broken = [{col.err}] [lightgray]Blocks broken: [{col.err}]{blocks-broken}
dialog-data-games = [orange] [lightgray]Games played: [orange]{games}
dialog-data-waves = [cyan] [lightgray]Waves survived: [cyan]{waves}
dialog-data-playtime = [accent] [gray]Play time:
                     = [accent]{playtime}

dialog-gamemode-stats = [{color}]{emoji} [lightgray]{name}: [{color}]{wins}{if {ovas} != 0 then ( [red](1vas: [white]{ovas}[]))}

dialog-profile-id = [cyan] Copy profile ID
dialog-user-id = [cyan] Copy user ID
dialog-more-data = [yellow] Info for nerds

whoops = [gray]Whoops! Nothing here!

[commands.vote]

description = Vote in a team-specific poll.
manpage = Vote in a team-specific poll.
        =
        = While global votes are to be responded to with y/n, team-specific polls are handled with /vote y/n to allow
        - both votes to proceed at the same time.
params = [y/n/c]
params.vote = y/n/c

no-vote = [{col.err}]There is no active vote.

[commands.rtv]

description = Start a vote for map change.
manpage = Start a vote for map change.
        =
        = If map is not specified, a random one is chosen. Map list can be viewed with [#EC5784]/maps[].
params = [map]
params.map = map

vote.update = [{col.sysmsg}]{player} wants to change the current map to [yellow]{map}[].
vote.for = [{col.sysmsg}]{voter} voted [{col.ok}]for[] changing the map to [yellow]{map}[].
vote.against = [{col.sysmsg}]{voter} voted [{col.err}]against[] changing the map to [yellow]{map}[].
vote.commit = [{col.ok}]Changing map to [yellow]{map}[].
vote.cancel = [{col.err}]Not enough votes to change the current map.

[commands.vnm]

description = Start a vote for next map.
manpage = Start a vote for next map.
        =
        = If map is not specified, a random one is chosen. Map list can be viewed with [#EC5784]/maps[].
params = [map]
params.map = map

vote.update = [{col.sysmsg}]{player} wants to play [yellow]{map}[] next.
vote.for = [{col.sysmsg}]{voter} voted [{col.ok}]for[] playing [yellow]{map}[] next.
vote.against = [{col.sysmsg}]{voter} voted [{col.err}]against[] playing [yellow]{map}[] next.
vote.commit = [{col.ok}]Next map is now set to [yellow]{map}[].
vote.cancel = [{col.err}]Not enough votes to set the next map.

[commands.votekick]

description = Start a vote for kicking a player.
manpage = Start a vote for kicking a player.
        =
        = If the vote succeeds, the player will be removed for 30 minutes.
        =
        = Specified reason must describe, shortly and concisely, what the issue is.
        - Starting a votekick without specifying a valid reason is a bannable offense.
params = <player> <reason..>
params.player = player
params.reason = reason

vote.update = [{col.sysmsg}]{player} wants to kick {target} for [yellow]{reason}[].
vote.for = [{col.sysmsg}]{voter} voted [{col.ok}]for[] kicking {target}.
vote.against = [{col.sysmsg}]{voter} voted [{col.err}]against[] kicking {target}.
vote.commit = [{col.ok}]Kicked player {target} for [yellow]{reason}[] for [yellow]30 minutes[].
vote.cancel = [{col.err}]Not enough votes to kick {target}.

errors.self = [{col.err}] You cannot kick yourself!
errors.self.special0 = [#49C9C7]u[#AE58C8]w[#49C9C7]u
errors.self.special1 = [{col.err}] Not here, thanks.
errors.self.special2 = [{col.err}] Try specifying somebody else!
errors.admin = [{col.err}] Admins are immune to [yellow]/votekick[].
errors.players = [{col.err}] There are not enough players to start a votekick.
errors.team = [{col.err}] You cannot start a votekick for a player from another team.

[commands.sync]

description = Reload the map.
manpage = Reload the map.
        =
        = This is usually used to force synchronize server and client.
params =

[commands.t]

description = Send message in team chat.
manpage = Send message in team chat.
params = <message...>
params.message = message

[commands.a]

description = Send message in admin chat.
manpage = Send message in admin chat.
params = <message...>
params.message = message

[commands.artv]

description = Force change map.
manpage = Force change map.
        =
        = If map is not specified, a random one is chosen. Map list can be viewed with [#EC5784]/maps[].
        =
        = Unlike [#EC5784]/rtv[], this does not start a vote and is only accessible to admins.
params = [map]
params.map = map

dialog.title = Force change map
dialog.contents = Are you sure you want to change map to {map}?

[commands.kick]

description = Kick a player out of the server.
manpage = Kick a player out of the server.
        =
        = This acts as a ban, but specific to this particular server.
params = <player> [duration] <reason...>
params.player = player
params.duration = duration
params.reason = reason

dialog.title = Kick {player}
dialog.reason = Kick reason
dialog.duration = Kick duration

success = [{col.ok}]{emo.ok}Kicked player {target}.

[commands.ban]

description = Ban a player on all servers.
manpage = Ban a player on all servers.
        =
        = This acts as a ban, but specific to this particular server.
params = <player> [duration] <reason...>
params.player = player
params.duration = duration
params.reason = reason

dialog.title = Ban {player}
dialog.reason = Ban reason
dialog.duration = Ban duration

success = [{col.ok}]{emo.ok}Banned player {target}.

[commands.mute]

description = Mute a player on all servers.
manpage = Mute a player on all servers.
params = <player> <duration> <reason...>
params.player = player
params.duration = duration
params.reason = reason

dialog.title = Mute {player}
dialog.reason = Mute reason
dialog.duration = Mute duration

success = [{col.ok}]{emo.ok}Muted player {target}.

[commands.fuck]

description = Run suggested command.
params =
manpage = Run suggested command.
        =
        = Command is cleared every time a new one have been executed.
        - A command will only be stored if there was no command matching the
        - input found. The command will be executed with previously provided arguments.

no-command-stored = [{col.err}] Unfortunately for you, there was no command.
whoops = [{col.warn}]⚠ Whoops! There is no command.

[commands.nick]

description = Set your display name.
params = [name]
params.nick = nick
manpage = Set your display name.
        =
        = Running the command on its own will reset your name.
        =
        = Set name will be displayed even if you change it in Mindustry's settings.

errors.too-long = [{col.err}] Your name is too long.
errors.colors-too-long = [{col.err}] Your name has too many colors.
warns.same-name = [{col.warn}] You already have this name set.
warns.nothing-reset = [{col.warn}] There is no name to remove.
success = [{col.ok}] Nickname changed to {name}.

[commands.hub]

description = Connect to hub server.
params =
manpage = Attempt to connect to the hub server.
        =
        = Server will automatically choose the hub server that is on the same version as
        - the server you're currently on.

errors.hub-down = [{col.err}]⚠ Hub server is offline.

[commands.surrender]

description = Start a vote for surrendering your team.
params =
manpage = Start a vote for surrendering your team.
        =
        = If the vote succeeds, all cores of your team will be destroyed.

vote.update = [{col.sysmsg}]{player} wants to surrender.
vote.for = [{col.sysmsg}]{voter} voted [{col.ok}]for[] surrendering.
vote.against = [{col.sysmsg}]{voter} voted [{col.err}]against[] surrendering.
vote.commit = [{col.ok}]Team has surrendered.
vote.cancel = [{col.err}]Not enough votes to surrender.

[commands.pardon]
description = Pardon a player.
params = <player>
params.player = player
manpage = Pardon a player on this server.
        =
        = Marks all kicks and votekicks on this specific server as pardoned.

fail = [{col.err}]Player {target} has no active kicks.
success = [{col.ok}]{emo.ok}Pardoned player {target}.

[commands.unban]
description = Unban a player.
params = <player>
params.player = player
manpage = Unbans a player.
        =
        = Marks all bans of the specified player as pardoned.

fail = [{col.err}]Player {target} has no active bans.
success = [{col.ok}]{emo.ok}Unbanned player {target}.

[commands.team]
description = Change team.
params = <team> [player]
params.team = team
params.player = player
manpage = Change team of a player.
        =
        = By default changes the team of the caller.

[commands.unit]
description = Change unit.
params = <unit> [player]
params.unit = unit
params.player = player
manpage = Change unit of a player.
        =
        = By default changes the unit of the caller.

[commands.despawn]
description = Despawn a unit.
params = <unit>
params.unit = unit
manpage = Despawn a unit.

[commands.tp]
description = Teleport a player.
params = <x> <y> [target]
params.x = x
params.y = y
params.target = target
manpage = Teleport a player.
        =
        = By default teleports the caller.

[commands.setrule]
description = Set a rule.
params = <rule> <value>
params.rule = rule
params.value = value
manpage = Assign value to a rule.

failed = [{col.err}] Failed to set the rule.

[commands.core]
description = Place a core.
params =
manpage = Place a core at unit position.
        =
        = If a core is already present, remove it.

no-unit = [{col.err}] Player has no unit.
no-tile = [{col.err}] Unit does not have a tile under it.

[commands.spawn]
description = Spawn units.
params = <kind> <count> [team]
param.kind = kind
param.count = count
param.team = team
manpage = Spawn units as unit position.
        =
        = Spawn conditions will obey unit count.

too-many-units = [{col.err}] Refusing to spawn more than 500 units.
no-tile = [{col.err}] Unit does not have a tile under it.
spawn-failed = [{col.err}] Could not spawn {remaining} unit{if {remaining} != 1 then s}.

[commands.effect]
description = Add status effect.
params = <effect> <duration> [unit]
param.effect = effect
param.duration = duration
param.unit = unit
manpage = Add a status effect to a unit.
        =
        = If duration is 0, clear all status effects. The duration of the effect is the maximum of
        - the new and existing durations.

no-unit = [{col.err}] No unit to target.

[commands.give]
description = Give items to a team.
params = <item> <count> [team]
param.item = item
param.count = count
param.team = team
manpage = Give an item to a team.
        =
        = If team is not specified, default to the team of the caller.

no-team = [{col.err}] Count not determine which team to give items to.
no-core = [{col.err}] Team has no cores.

[commands.spectate]
description = Toggle spectator mode.
params =
manpage = Toggle spectator mode.
        =
        = Spectator team is considered to a be a service team, thus disallowing voting.

[commands.settings]
description = Modify settings.
params = [path..]
manpage = Modify settings.
        =
        = If path is not specified, will open the GUI.

[commands.settings.maindialog]
title = {emo.settings} Settings
translator = {emo.book-open} Translator Settings

[commands.settings.translatordialog]
title = {emo.settings} Settings [lightgray]>>[] Translator
message = [lightgray]Current style: {commands.settings.translator.{style}.name}
        = [orange]{commands.settings.translator.{style}.description}
        - {if {style} != none then (
        =
        = [accent]<<< EXAMPLE >>>
        = {commands.settings.translator.{style}.example})}
filter = [white]{emo.filter} Skip Languages

[commands.settings.skiplangsdialog]
title = {emo.settings} Settings [lightgray]>>[] Translator [lightgray]>>[] Filter
search = [cyan]{emo.zoom} Search

[commands.settings.translator]
none.name = [{col.err}]Disabled
none.example = [scarlet]Whoops! You shouldn't be able to see this!
none.description = The translator is disabled.

multiline.name = [{col.ok}]Multiline
multiline.example = [scarlet][[{player}]: [white]Hello!
                  = [gray]Original (de): []Hallo!
multiline.description = Displaying both original and translated messages.

short.name = [{col.ok}]Short
short.example = [scarlet][[{player}] [gray](de)[]: [white]Hello!
short.description = A shorter variant for if you can't read the original language anyway.

classic.name = [{col.ok}]Classic
classic.example = [scarlet][[{player}]: [white]Hallo! [lightgray](Hello!)
classic.description = Ye olde look, no more, no less.
