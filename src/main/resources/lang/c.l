# Generic

[generic]

close = [lime] Close
server = Mindurka
special-settings-error = [scarlet]Flags on this map are not set correctly:
                       =
                       = {errors}
                       =
                       = Map may not function correctly.
gamemode-uses-special-settings = [yellow]Some of this gamemode's features don't work in vanilla.[]
                               =
                               = Install [cyan]MindurkaCompat[] from mod explorer to gain access to them.
map-uses-special-settings = [yellow]Some of this map's features don't work in vanilla.[]
                          =
                          = Install [cyan]MindurkaCompat[] from mod explorer to gain access to them.

[generic.team]

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
