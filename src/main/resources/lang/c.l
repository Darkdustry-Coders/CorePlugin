# Generic

[generic]

close = [lime] Close
server = Mindurka
discord = https://discord.gg/uPUZHp7xQn
special-settings-error = [scarlet]Flags on this map are not set correctly:
                       =
                       = {errors}
                       =
                       = Map may not function correctly.
gamemode-uses-special-settings = [yellow]Some of this gamemode's features don't work in vanilla.[]
                               =
                               = Install [royal]MindurkaCompat[] from mod explorer to gain access to them.
map-uses-special-settings = [yellow]Some of this map's features don't work in vanilla.[]
                          =
                          = Install [royal]MindurkaCompat[] from mod explorer to gain access to them.
can-use-public-key = [accent][royal]MindurkaCompat[] supports public key authorization.
                   = Public key authorization is more secure than traditional uuid-based authorization.
                   = To switch to use public key instead of uuid, type [green]/setkey[].
                   = See [scarlet]/help setkey[] for more information.

[generic.kick]
merged = [scarlet]This account was merged into another and thus no longer accessible.
       =
       = [accent]Merge action is irreversible. For more information see the FAQ section in [cyan]#help[] channel on our
       - [royal]Discord Server[].
       = [cyan]{generic.discord}
disconnected = [scarlet]This account is no longer assigned to any users.
             =
             = [accent]If you'd like to reconnect it, create a post in [cyan]#help[] channel on our [royal]Discord Server[].
             = [cyan]{generic.discord}
key-validation-failure-mdc = [scarlet]Key validation failure.
                           =
                           = [accent]The key on your device does not match the key on the server.
                           = Make sure you have imported a save from your old device. If you don't have access to
                           - it, create a post in [cyan]#help[] channel on our [royal]Discord Server[].
                           = {generic.discord}
key-validation-failure-nomdc = [scarlet]Key validation failure.
                             =
                             = [accent]This account is protected. In order to log in, you must have
                             - [royal]MindurkaCompat[] installed. If you have any questions, create a port in
                             - [cyan]#help[] channel on our [royal]Discord Server[].
                             = [cyan]{generic.discord}
disabled = [scarlet]This account was disabled.
         =
         = [accent]If you'd like to appeal this decision, create a post in [cyan]#appeals[] channel on our [royal]Discord Server[].
         = [cyan]{generic.discord}
shared = [scarlet]This account was found to be shared between several players and thus no longer accessible.
       =
       = [accent]This decision cannot be appealed. For more information see the FAQ section in [cyan]#help[] channel on our
       - [royal]Discord Server[].
       = [cyan]{generic.discord}

[generic.gameover]

pvp = Game over! Team {team} has emerged victorious.
    =
    = Next map: [yellow]{map}[]

waves = Game over! Reached wave {wave}.
      =
      = Next map: [yellow]{map}[]

tie = Game over! Teams reached a tie.
    =
    = Next map: [yellow]{map}[]

attackWin = Game over! Map has been completed.
          =
          = Next map: [yellow]{map}[]

attackLose = Game over! All cores have been destroyed.
           =
           = Next map: [yellow]{map}[]

unexpected = Game over! All cores have been destroyed.
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
        = MindurkaCompat generates a key pair upon launch, which a compatible server can verify
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
title = Do you want to enable public key authorization?
message = This action is irreversible.

ok = [lime] Successfully enabled public key authorization.
error = [scarlet] Already enabled.
