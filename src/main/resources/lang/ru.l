[generic]

close = [{col.ok}]{emo.gplay} Закрыть
cancel = [{col.err}]{emo.x} Отменить
ok = [{col.ok}]{emo.ok} Ок
special-settings-error = [{col.err}]Произошли ошибки во время загрузки карты:
                       =
                       = {errors}
                       =
                       = Карта может не работать корректно.

# spam Spam SPAM
welcome-message-title = Приветственное сообщение
welcome-message = [accent]Мы в процессе переписывания плагина.
                - Ожидайте багов!
                =
                = [orange]<<< ATTENTION >>>
                = [{col.err}]This server bans cheating, and may automatically ban you on notice.
                - If you'll get banned for cheating and can't prove you did not cheat, [yellow]you will
                - not be unbanned[] even if you promise to not use cheats anymore.
                =
                = Yes, hardware autoclickers and so-called "QOL" features like bypassing schemes
                - count too.
gamemode-uses-special-settings = [accent]Некоторые функции этого режима не работают на ванильном клиенте.[]
                               =
                               = Установите [{col.mdc}]MindurkaCompat[] из браузера модов чтобы функции работали.
map-uses-special-settings = [accent]Некоторые функции этой карты не работают на ванильном клиенте.[]
                          =
                          = Установите [{col.mdc}]MindurkaCompat[] из браузера модов чтобы функции работали.
can-use-public-key = [accent][{col.mdc}]MindurkaCompat[] поддерживает публичный ключ авторизации.
                   = Публичный ключ авторизации безопасней базированной на uuid авторизации.
                   = Для перехода на публичный ключ напишите: [green]/setkey[].
                   = See [#ec5784]/help setkey[] для большей информации.
mdc-outdated = [accent]Ваша версия [{col.mdc}]MindurkaCompat[] устарела.[]
             =
             = [accent]Обновлите мод до последней версии из браузера модификаций.
restart-scheduled = [{col.warn}]Сервер будет перезапущен после конца раунда.

chat.original = [gray]Оригинал ({lang}): []{origmessage}
chat.team = [{teamcolor}][[{emo.team} Команда]
chat.admin = [scarlet][{emo.admin} Админы]
chat.service.discord = [#4c59e0][{emo.discord} Дискорд]
chat.service.telegram = [#76d0f8][{emo.telegram} Телеграм]

vote.global = [{col.sysmsg}]Напишите [{col.ok}]y[] или [{col.err}]n[] чтобы проголосовать. {if {votes} < 0 then [{col.err}] else [{col.ok}]}{votes}/{threshold}
vote.team = [{col.sysmsg}]Напишите [{col.ok}]/vote y[] или [{col.err}]/vote n[] чтобы проголосовать. {if {votes} < 0 then [{col.err}] else [{col.ok}]}{votes}/{threshold}
vote.left = [{col.sysmsg}]{player} вышел с сервера, голос удалён.

vote.global.cancelled = [{col.err}]⚠ Голосование отменено админом {admin}.
vote.team.cancelled = [{col.err}]⚠ Командное голосование отменено админом {admin}.

vote.try-global = [{col.err}]There's a global vote running. Use [{col.ok}]y[]/[{col.err}]n[] to vote.
vote.try-team = [{col.err}]There's a team vote running. Use [{col.ok}]/vote y[]/[{col.err}]/vote n[] to vote.

admin.ban = [{col.err}]⚠ {admin} забанил {target} за [orange]{reason}.
admin.kick = [{col.err}]⚠ {admin} кикнул {target} за [orange]{reason}.
admin.wave = [{col.ok}]Волна пропущена админом {admin}.

spectating = [{col.ok}]You are now spectating.
no-longer-spectating = [{col.ok}]You are no longer spectating.

[generic.checks]
same-vote = [{col.err}] Твой голос не изменился.
vote-filter = [{col.err}] Ты не можешь участвовать в данном голосовании.
cooldown = [{col.err}] Приостановись! Эта команда на кулдауне.
vote = [{col.err}] Голосование уже идёт.
vote-spectator = [{col.err}] Spectators cannot start team votes.
unknown-command = [{col.err}] Не найдена команда [yellow]{command}[]. {if {suggestion} = () then (Проверьте [yellow]/help[].) else (Возможно вы хотели написать [yellow]/{suggestion}[]?)}
invalid-arguments = [{col.err}] {cause}. Попробуйте [yellow]/help {command}[]!
permission = [{col.err}] У вас недостаточно прав для использования команды.
admin-action-permission = [{col.err}] У вас недостаточно прав для совершения данного действия.
admin-action-no-target = [{col.err}] Вы должны указать цель для исполнения данного действия.
admin-action-target-admin = [{col.err}] Вы не можете использовать это на другого админа.

[generic.command]
disabled = Команда [yellow]'/{command}'[] отключена
missing = Пропущен параметр [yellow]'{commands.{command}.params.{param}}'[]
invalid = Неправильный параметр [yellow]'{commands.{command}.params.{param}}'[]
string-termination = Параметр [yellow]'{commands.{command}.params.{param}}'[] не заключен должным образом.
end-of-input = Недостаточно аргументов
too-many-arguments = Слишком много аргументов
not-a-number = Параметр [yellow]'{commands.{command}.params.{param}}'[] Должен быть числом
not-a-boolean = Параметр [yellow]'{commands.{command}.params.{param}}'[] должен быть [#26d657]yes[] или [#EC5784]no[]
invalid-map-id = Неизвестный айди карты
zero-map-id = Но оно отказано
unknown-map = Неизвестная карта
unknown-player = Игрок не найден
unknown-unit = Unknown unit
unknown-unit-type = Unknown unit type
unknown-status-effect = Unknown status effect
invalid-item = Invalid item
invalid-team = Invalid team
invalid-duration = Неправильный параметр срока [yellow]'{commands.{command}.params.{param}}'[]

[generic.warn]
cheating.admin = [{col.err}]{emo.warn} {player} is cheating! ({generic.warn.cheating.admin.{reason}})
cheating.player = [{col.warn}]{emo.warn} {generic.warn.cheating.player.{reason}}

cheating.player._generic_warning = Cheating is banned on this server, so we highly recommend uninstalling them
                                 - or learning about all the cheaty features your mods add so you can not
                                 - use them.
cheating.admin.config-unconfigurable = Configured an unconfigurable building
cheating.player.config-unconfigurable = You tried configuring an unconfigurable building!
                                      =
                                      = This could either be due to high ping or if you're using cheats.
                                      =
                                      = {generic.warn.cheating.player._generic_warning}
cheating.admin.core-spawn = Selected a core to respawn from
cheating.player.core-spawn = You tried to spawn from a different core!
                           =
                           = Some cheats abuse a bug in Mindustry that bypasses "Possession allowed"
                           - map rule to allow respawning from non-main cores.
                           =
                           = {generic.warn.cheating.player._generic_warning}
cheating.admin.pre-configured-block = Placed a pre-configured block
cheating.player.pre-configured-block = You tried to place a pre-configured block!
                                     =
                                     = Some cheats let you place schemes despite having "Schematics Allowed"
                                     - map rule.
                                     =
                                     = {generic.warn.cheating.player._generic_warning}

[generic.kick]
merged = [{col.err}]Этот аккаунт был объединен с другим и, следовательно, больше недоступен.
       =
       = [accent]Объединение не может быть отменено. Для большей информации посмотрите FAQ секцию в канале [cyan]#help[] на нашем сервере
       - [#4c59e0]Discord Сервере[].
       = [cyan]{generic.discord}
disconnected = [{col.err}]Эта учетная запись больше не привязана ни к одному пользователю.
             =
             = [accent]Если бы вы хотели перезайти создайте публикацию в канале [cyan]#help[] на нашем
             -  [royal]Discord Сервере[].
             = [cyan]{generic.discord}
key-validation-failure-mdc = [{col.err}]Ошибка проверки ключа.
                           =
                           = [accent]Ключ на вашем устройстве не соответствует хранимому на сервере.
                           = Удостоверьтесь что вы импортировали сохранение с нужным ключом. Если у вас нет доступа к нему,
                           - создайте публикацию в канале [cyan]#help[] на нашем дискорд сервере [#4c59e0]Discord Сервере[].
                           = [cyan]{generic.discord}
key-validation-failure-no-mdc = [{col.err}]Ошибка проверки ключа.
                              =
                              = [accent]Этот аккаунт защищён. Для входа в аккаунт вам нужно чтобы
                              - [#4c59e0]MindurkaCompat[] был установлен. Если у вас есть вопросы, создайте публицию в канале
                              - [cyan]#help[] на нашем сервере [#4c59e0]Discord Сервере[].
                              = [cyan]{generic.discord}
disabled = [{col.err}]Этот аккаунт отключен.
         =
         = [accent]Если вы хотите апеллировать данное решение, создайте публикацию в канале [cyan]#appeals[] на нашем сервере [#4c59e0]Discord Сервере[].
         = [cyan]{generic.discord}
shared = [{col.err}]Этот аккаунт больше недоступен из-за утечки его uuid.
       =
       = [accent]Это решение не может быть апелировано. Для больше информации посмотрите секцию FAQ в канале [cyan]#help[] на нашем
       - [#4c59e0]Discord Сервере[].
       = [cyan]{generic.discord}
another-location = [{col.err}]Вы уже вошли на сервер.
         =
         = [accent]Если вы думаете, что это ошибка, создайте публикацию в канале [cyan]#help[] на нашем
         - [#4c59e0]Discord Сервере[].
         = [cyan]{generic.discord}
ban = [{col.err}]Вы забанены на данном сервере
    =
    = [accent]Ban ID: [yellow]{id}
    = [accent]User ID: [yellow]{userid}
    = [accent]Админ: {admin}
    = [accent]Причина: [#EC5784]{reason}
    = [accent]Осталось: [yellow]{remaining}
    =
    = [accent]Если вы думаете, что это ошибка, создайте публикацию в канале [cyan]#appeals[] на нашем
    - [#4c59e0]Discord Сервере[].
    = [cyan]{generic.discord}
kick = [{col.err}]Вы кикнуты с данного сервера.
     =
     = [accent]Kick ID: [yellow]{id}
     = [accent]User ID: [yellow]{userid}
     = [accent]Админ: {admin}
     = [accent]Причина: [#EC5784]{reason}
     = [accent]Осталось: [yellow]{remaining}
     =
     = [accent]Если вы думаете что это ошибка создайте публикацию в канале [cyan]#appeals[] на нашем
     - [#4c59e0]Discord Сервере[].
     = [cyan]{generic.discord}
votekick = [{col.err}]Вы выгнаны голосованием с данного сервера.
         =
         = [accent]Votekick ID: [yellow]{id}
         = [accent]User ID: [yellow]{userid}
         = [accent]Инициатор: {initiator}
         = [accent]Причина: [#EC5784]{reason}
         = [accent]Осталось: [yellow]{remaining}
         =
         = [accent]Голоса [lime]за[]:
         = {each {vote} in {votes} ([gray]- {vote}) split (\n) join (\n)}
         =
         = [accent]Если вы думаете что это ошибка создайте публикацию в канале [cyan]#appeals[] на нашем
         - [#4c59e0]Discord Сервере[].
         = [cyan]{generic.discord}

[generic.gameover]

pvp = [accent]Игра завершена! Команда {team} вышла победителем.
    =
    = Следующая карта: [yellow]{map}[]

waves = [accent]Игра завершена! Достигнута волна {wave}.
      =
      = Следующая карта: [yellow]{map}[]

tie = [accent]Игра завершена! Ничья.
    =
    = Следующая карта: [yellow]{map}[]

attackWin = [accent]Игра завершена! Карта выиграна.
          =
          = Следующая карта: [yellow]{map}[]

attackLose = [accent]Игра завершена! Все ядра были уничтожены.
           =
           = [accent]Следующая карта: [yellow]{map}[]

unexpected = [accent]Игра завершена! Все ядра были уничтожены.
           =
           = Следующая карта: [yellow]{map}[]

[generic.duration]
years = года
year = год
weeks = недель
week = неделя
days = дней
day = день
hours = часов
hour = час
minutes = минут
minute = минут
seconds = секунд
second = секунд
millis = миллисекунд
milli = миллисекунд
subsecond = меньше секунды
inf = вечность

# Commands

[commands.help]

description = Получить список доступных команд.
manpage = Получить список доступных команд.
        =
        = Если [blue][[страница][] уточнена, откроет указанную страницу.
        =
        = Если [blue][[команда][] уточнена, откроет указанную команду.
params = [страница-или-команды]
params.page = Страница
params.command = Команда

select-page.title =  Выберите страницу
select-page.message = Страница: {page}
title-page =  Список доступных команды
title-cmd =  Команда help
          = [accent]/{command} [white]{commands.{command}.params}
man.title =  Команда help
man.message = [accent]/{command} [white]{commands.{command}.params}
            =
            = {commands.{command}.manpage}
command = [accent]/{command} [white]{commands.{command}.params}
        = [lightgray]{commands.{command}.description}
invalid-page = [{col.err}] Страница должна быть целым числом в диапазоне [yellow]1[] - [yellow]{max}[]. Здесь нету скрытых страниц. Обещаем.
negative-page = [{col.err}] Страница должна быть целым числом в диапазоне [yellow]1[] - [yellow]{max}[]. Что ты вообще ищешь?
unknown-command = [{col.err}] [yellow]/{command} [{col.err}]неизвестная команда. Я чайник.

[commands.maps]

description = Получить список доступных карт.
manpage = Получить список доступных карт.
        =
        = Если [blue][[страница][] уточнена, откроет указанную страницу.

params = [страница]
params.page = страница

select-page.title =  Выбрать страницу
select-page.message = Страница: {page}
title-page =  Список доступных карт
man.title =  Команда maps
map = [orange]{id}. [accent]{map} [white]от [accent]{author}
    = [white]{width}x{height}
invalid-page = [{col.err}] Страница должна быть целым числом в диапазоне [yellow]1[] - [yellow]{max}[]. Здесь нету скрытых страниц. Обещаем.
negative-page = [{col.err}] Страница должна быть целым числом в диапазоне [yellow]1[] - [yellow]{max}[]. Что ты вообще ищешь?

[commands.setkey]

description = Переключить авторизацию на публичный ключ.
manpage = Переключить авторизацию на публичный ключ.
        =
        = [#4c59e0]MindurkaCompat[] генерирует пару ключей при запуске, которую совместимый сервер может проверить
        - для подтверждения подлинности владельца ключа.
        =
        = В отличие от UUID, утечка которого позволяет злоумышленнику-администратору выдать себя за игрока на любом сервере,
        - утечка публичного ключа безопасна, поскольку он бесполезен без своей приватной части. Приватный ключ
        - никогда не передаётся по сети.
        =
        = После включения авторизации по публичному ключу необходимо импортировать новое сохранение на всех устройствах,
        - с которых вы хотите получить доступ к аккаунту. Однако, в отличие от UUID, при импорте сохранения в Steam
        - это должно работать без дополнительных настроек.
        =
        = Безопасность авторизации по публичному ключу определяется защищённостью приватного ключа на устройстве.
        - Сохранения не должны публиковаться там, где третьи лица могут получить к ним доступ. Если вы хотите
        - сообщить об ошибке в Mindustry, сначала сбросьте все игровые данные, а затем воспроизведите ошибку.
        - Так ключи останутся в безопасности.
        =
        = Авторизация по публичному ключу работает только с совместимыми серверами; для остальных используется авторизация на основе UUID.
params =
title = Диалог подтвержения
message = [accent]Вы хотите использовать публичный ключ авторизации?
        =
        = [{col.err}]Это действие не может быть отменено.

ok = [#26d657] Успешно включена авторизация по публичному ключу.
error = [{col.err}] Уже включено
admin-protection = [#EC5784] Вы должны войти перед активацией авторизации по публичного ключу на админском аккаунте.
no-mdc = [{col.err}] Открытый ключ не был предоставлен.
       =
       = Установите или включите [{col.mdc}]MindurkaCompat[] и повторите попытку снова.

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

description = Проголосовать в командном голосовании.
manpage = Проголосовать в командном голосовании.
        =
        = Когда глобальные голоса работают по y/n, голосование команды работают через /vote y/n
        : чтобы сразу несколько команд могли голосовать разом.
params = [y/n/c]
params.vote = y/n/c

no-vote = [{col.err}]There is no active vote.

[commands.rtv]

description = Начать голосование за смену карты.
manpage = Начать голосование за смену карты.
        =
        = Если карта не уточнена выбирается рандомная. Список карт может быть просмотрен командой [#EC5784]/maps[].
params = [карта]
params.map = карта

vote.update = [{col.sysmsg}]{player} Хочет сменить карту на [yellow]{map}[].
vote.for = [{col.sysmsg}]{voter} Проголосовал [{col.ok}]за[] смену карты на [yellow]{map}[].
vote.against = [{col.sysmsg}]{voter} Проголосовал [{col.err}]against[] смену карты на [yellow]{map}[].
vote.commit = [{col.ok}]Смена карты на [yellow]{map}[].
vote.cancel = [{col.err}]Недостаточно голосов для смены карты.

[commands.vnm]

description = Начать голосование за следующую карту.
manpage = Начать голосование за следующую карту.
        =
        = Если карта не уточнена выбирается рандомная. Список карт может быть просмотрен командой[#EC5784]/maps[].
params = [карта]
params.map = карта

vote.update = [{col.sysmsg}]{player} Хочет сыграть на [yellow]{map}[] после данной игры.
vote.for = [{col.sysmsg}]{voter} проголосовал [{col.ok}]за[] игру на [yellow]{map}[] после данной игры.
vote.against = [{col.sysmsg}]{voter} проголосовал [{col.err}]против[] игры на [yellow]{map}[] после данной игры.
vote.commit = [{col.ok}]Следующая карта: [yellow]{map}[].
vote.cancel = [{col.err}]Недостаточно голосов для установки следуйщей карты.

[commands.votekick]

description = Начать голосование за кик игрока.
manpage = Начать голосование за кик игрока.
        =
        = Если голосование пройдет успешно, игрок будет удален с игры на 30 минут.
        =
        = Причина должна быть короткой и верной.
        - Голосование без причины наказуемо.
params = <игрок> <причина..>
params.player = игрок
params.reason = причина

vote.update = [{col.sysmsg}]{player} хочет кик игрока {target} по причине [yellow]{reason}[].
vote.for = [{col.sysmsg}]{voter} проголосовал [{col.ok}]за[] кик {target}.
vote.against = [{col.sysmsg}]{voter} проголосовал [{col.err}]против[] кика {target}.
vote.commit = [{col.ok}]Кикнут {target} за [yellow]{reason}[] на [yellow]30 минут[].
vote.cancel = [{col.err}]Недостаточно голосов за кик {target}.

errors.self = [{col.err}] Ты не можешь кикнуть себя!
errors.self.special1 = [{col.err}] Не здесь, спасибо.
errors.self.special2 = [{col.err}] Попробуй указать кого-нибудь другого!
errors.admin = [{col.err}] Админы иммунны к [yellow]/votekick[].
errors.players = [{col.err}] Недостаточно игроков за начало голосования.
errors.team = [{col.err}] Ты не можешь начать голосование за кик игрока из другой команды.

[commands.sync]
# i really do not like how it translating
description = Синхронизировать карту с сервером.
manpage = Синхронизировать карту с сервером.
params =

[commands.t]

description = Отправить сообщение в чат команды.
manpage = Отправить сообщение в чат команды.
params = <сообщение...>
params.message = сообщение

[commands.a]

description = Отправить сообщение в чат администрации.
manpage = Отправить сообщение в чат администрации.
params = <сообщение...>
params.message = сообщение

[commands.artv]

description = Сменить карту без голосования.
manpage = Сменить карту без голосования.
        =
        = Если карта не уточнена выбирается рандомная. Список карт может быть посмотрен командой [#EC5784]/maps[].
        =
        = Нежели как [#EC5784]/rtv[], эта команда работает только у админов.
params = [карта]
params.map = карта

dialog.title = Force change map
dialog.contents = Are you sure you want to change map to {map}?

[commands.kick]

description = Кикнуть игрока с сервера.
manpage = Кикнуть игрока с сервера.
        =
        = Действует как бан но только на данный сервер.
params = <игрок> <длительность> <причина...>
params.player = игрок
params.duration = длительность
params.reason = причина

dialog.title = Кик {player}
dialog.reason = Причина кика
dialog.duration = Длительность кика

success = [{col.ok}]{emo.ok}Kicked player {target}.

[commands.ban]

description = Забанить игрока на всех серверах.
manpage = Забанить игрока на всех серверах.
params = <игрок> <длительность> <причина...>
params.player = игрок
params.duration = длительность
params.reason = причина

dialog.title = Бан {player}
dialog.reason = Причина бана
dialog.duration = Длительность бана

success = [{col.ok}]{emo.ok}Banned player {target}.

[commands.mute]

description = Замутить игрока на всех серверах.
manpage = Замутить игрока на всех серверах.
params = <игрок> <длительность> <причина...>
params.player = игрок
params.duration = длительность
params.reason = причина

dialog.title = Мут {player}
dialog.reason = Причина мута
dialog.duration = Длительность мута

success = [{col.ok}]{emo.ok}Muted player {target}.

[commands.fuck]

description = Использовать предложенную команду.
params =
manpage = Использовать предложенную команду.
        =
        = Команда чистятся каждый раз когда новая запускается.
        - Команда будет хранится только если действующая не будет найдена.
        - Команда запустится с ранее указанными аргументами.

no-command-stored = [{col.err}] К сожалению для тебя здесь нет команды.
whoops = [{col.warn}]⚠ Упс! Здесь нет команды.

[commands.nick]

description = Установить отображаемый никнейм.
params = [никнейм]
params.nick = ник
manpage = Установить отображаемый никнейм.
        =
        = Использование команды ресетнем ваш никнейм.
        =
        = Установленный никнейм будет установлен даже после смены его в настройках Mindustry.

errors.too-long = [{col.err}] Ваш никнейм слишком длинен.
errors.colors-too-long = [{col.err}] У вашего ника слишком много цветов.
warns.same-name = [{col.warn}] У вас уже установлен данный никнейм.
warns.nothing-reset = [{col.warn}] Нет никнейма для ресета.
success = [{col.ok}] Никнейм изменен на {name}.

[commands.hub]

description = Подключиться к хабу.
params =
manpage = Подключиться к хабу.
        =
        = Сервер выберет хаб соответствующей вашей версии

errors.hub-down = [{col.err}]⚠ Хаб оффлайн.

[commands.surrender]

description = Начать голосование за сдачу команды.
params =
manpage = Начать голосование за сдачу команды.
        =
        = Если голосование пройдет успешно все ядра вашей команды будут уничтожены.

vote.update = [{col.sysmsg}]{player} Хочет сдатся.
vote.for = [{col.sysmsg}]{voter} проголосовал [{col.ok}]за[] сдачу.
vote.against = [{col.sysmsg}]{voter} проголосовал [{col.err}]против[] сдачи.
vote.commit = [{col.ok}]Команда сдалась.
vote.cancel = [{col.err}]Недостаточно голосов для сдачи.

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
        : {if {style} != none then (
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

# The example probably should be different to be more obvious to Russian players.aaaa
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
