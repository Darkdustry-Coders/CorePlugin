# Generic

[generic]

close = [#26d657] Close
cancel = [#EC5784] Cancel
ok = [#26d657] Ok
server = Mindurka
discord = https://mindurka.online/discord
special-settings-error = [#EC5784]Flags on this map are not set correctly:
                       =
                       = {errors}
                       =
                       = Map may not function correctly.

# spam Spam SPAM
gamemode-uses-special-settings = [accent]Some of this gamemode's features don't work in vanilla.[]
                               =
                               = Install [#4c59e0]MindurkaCompat[] from mod explorer to gain access to them.
map-uses-special-settings = [accent]Some of this map's features don't work in vanilla.[]
                          =
                          = Install [#4c59e0]MindurkaCompat[] from mod explorer to gain access to them.
can-use-public-key = [accent][#4c59e0]MindurkaCompat[] supports public key authorization.
                   = Public key authorization is more secure than traditional uuid-based authorization.
                   = To switch to use public key instead of uuid, type [green]/setkey[].
                   = See [#EC5784]/help setkey[] for more information.

chat = [scarlet][[{player}]: [white]{message}
chat.team = [[ Team]
chat.admin = [scarlet][[ Admins]

vote.global = [#adadad]Type [#26d657]y[] or [#EC5784]n[] to vote. {if {votes} < 0 then [#EC5784] else [#26d657]}{votes}/{threshold}
vote.team = [#adadad]Type [#26d657]/vote y[] or [#EC5784]/vote n[] to vote. {if {votes} < 0 then [#EC5784] else [#26d657]}{votes}/{threshold}
vote.left = [#adadad]{player} has left the game and their vote was removed.

[generic.checks]
same-vote = [#EC5784] Your vote has not changed.
cooldown = [#EC5784] Slow down! This command is on cooldown.
vote = [#EC5784] There's already a vote ongoing.
unknown-command = [#EC5784] Could not find command [yellow]{command}[]. Check [yellow]/help[].
invalid-arguments = [#EC5784] Arguments provided for [yellow]{command}[] are incorrect. Try [yellow]/help {command}[]!
permission = [#EC5784] You don't have permission to execute this command.

[generic.kick]
merged = [#EC5784]This account was merged into another and thus no longer accessible.
       =
       = [accent]Merge action is irreversible. For more information see the FAQ section in [cyan]#help[] channel on our
       : [#4c59e0]Discord Server[].
       = [cyan]{generic.discord}
disconnected = [#EC5784]This account is no longer assigned to any users.
             =
             = [accent]If you'd like to reconnect it, create a post in [cyan]#help[] channel on our [royal]Discord Server[].
             = [cyan]{generic.discord}
key-validation-failure-mdc = [#EC5784]Key validation failure.
                           =
                           = [accent]The key on your device does not match the key on the server.
                           = Make sure you have imported a save from your old device. If you don't have access to
                           : it, create a post in [cyan]#help[] channel on our [#4c59e0]Discord Server[].
                           = [cyan]{generic.discord}
key-validation-failure-no-mdc = [#EC5784]Key validation failure.
                              =
                              = [accent]This account is protected. In order to log in, you must have
                              : [#4c59e0]MindurkaCompat[] installed. If you have any questions, create a port in
                              : [cyan]#help[] channel on our [#4c59e0]Discord Server[].
                              = [cyan]{generic.discord}
disabled = [#EC5784]This account was disabled.
         =
         = [accent]If you'd like to appeal this decision, create a post in [cyan]#appeals[] channel on our [#4c59e0]Discord Server[].
         = [cyan]{generic.discord}
shared = [#EC5784]This account was found to be shared between several players and thus no longer accessible.
       =
       = [accent]This decision cannot be appealed. For more information see the FAQ section in [cyan]#help[] channel on our
       : [#4c59e0]Discord Server[].
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

# Commands

[commands.help]

description = Get a list of available commands.
manpage = Get a list of available commands.
        =
        = If [blue][[page][] is specified, open the specified page.
        =
        = If [blue][[command][] is specified, give its extended description.
params = [page-or-command]
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
invalid-page = [scarlet]⚠ Page must be an integer between [orange]1[] and [orange]{max}[]. There are no secret commands behind there. We promise.

[commands.maps]

description = List of server maps.
manpage = List of server maps.
        =
        = If [blue][[page][] is specified, open the specified page.
params = [page]
map = [orange]{id}. [accent]{map}[lightgray] by [accent]{author}\n[lightgray]{width}x{height}

[commands.setkey]

description = Switch to using public key authorization.
manpage = Switch to using public key authorization.
        =
        = [#4c59e0]MindurkaCompat[] generates a key pair upon launch, which a compatible server can verify
        : in order to confirm that the owner of the key is authentic.
        =
        = Unlike with UUID, leaking which allows a malicious server admin to impersonate a player on any server,
        : leaking a public key is safe, as it is useless without its private key counterpart. Private key
        : is never sent other the network.
        =
        = After enabling public key authorization, you must import a new save on all devices you wish to access the
        : account from. Unlike with UUID, however, this should work out of the box when importing a save on steam.
        =
        = Public key authorization is as secure as is the private key on the device. Saves must not be shared anywhere
        : where a third party may access them. If you want to report a Mindustry bug, first reset all game data, and
        : then reproduce the bug. That way the keys remain safe.
        =
        = Public key authorization only works with compatible servers, and for others UUID-based authorization is used.
params =
title = Confirmation dialog
message = [accent]Do you want to enable public key authorization?
        =
        = [#EC5784]This action is irreversible.

ok = [#26d657] Successfully enabled public key authorization.
error = [#EC5784] Already enabled.
no-mdc = [#EC5784] No public key was shared.
       =
       = Install or enable [#4c59e0]MindurkaCompat[] and repeat the command.

[commands.vote]

description = Vote in a team-specific poll.
manpage = Vote in a team-specific poll.
        =
        = While global votes are to be responded to with y/n, team-specific polls are handled with /vote y/n to allow
        : both votes to proceed at the same time.
params = [map]

[commands.rtv]

description = Start a vote for map change.
manpage = Start a vote for map change.
        =
        = If map is not specified, a random one is chosen. Map list can be viewed with [#EC5784]/maps[].
params = [map]

vote.update = [#adadad]{player} wants to change the current map to [yellow]{map}[].
vote.for = [#adadad]{player} voted [#26d657]for[] changing the map to [yellow]{map}[].
vote.against = [#adadad]{player} voted [#EC5784]against[] changing the map to [yellow]{map}[].
vote.commit = [#26d657]Changing map to [yellow]{map}[].
vote.cancel = [#EC5784]Not enough votes to change the current map.

[commands.vnm]

description = Start a vote for next map.
manpage = Start a vote for next map.
        =
        = If map is not specified, a random one is chosen. Map list can be viewed with [#EC5784]/maps[].
params = [map]

vote.update = [#adadad]{player} wants to play [yellow]{map}[] next.
vote.for = [#adadad]{player} voted [#26d657]for[] playing [yellow]{map}[] next.
vote.against = [#adadad]{player} voted [#EC5784]against[] playing [yellow]{map}[] next.
vote.commit = [#26d657]Next map is now set to [yellow]{map}[].
vote.cancel = [#EC5784]Not enough votes to set the next map.

[commands.artv]

description = Force change map.
manpage = Force change map.
        =
        = If map is not specified, a random one is chosen. Map list can be viewed with [#EC5784]/maps[].
        =
        = Unlike [#EC5784]/rtv[], this does not start a vote and is only accessible to admins.
params = [map]

[commands.sync]

description = Reload the map.
manpage = Reload the map.
        =
        = This is usually used to force synchronize server and client.
params =

[commands.t]

description = Send message in team chat.
manpage = Send message in team chat.
params = [message...]

[commands.a]

description = Send message in admin chat.
manpage = Send message in admin chat.
params = [message...]
