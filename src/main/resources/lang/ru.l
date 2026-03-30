# Colors

[col]

ok = #26d657
warn = #fce174
err = #ec5784
cmd = #ec5784
cmdarg = yellow
mdc = #4c59e0
sysmsg = #adadad

# Generic

[generic]

close = [{col.ok}] Закрыть
cancel = [{col.err}] Отменить
ok = [{col.ok}] Ок
server = Mindurka
discord = https://mindurka.online/discord
special-settings-error = [{col.err}]Флаги на данной карте не настроены корректно:
                       =
                       = {errors}
                       =
                       = Карта может не работать корректно.

# spam Spam SPAM
welcome-message-title = Приветственное сообщение
welcome-message = [accent]Мы в процессе переписывания плагина.
                : Ожидайте багов!
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

chat = [scarlet][[{player}]: [white]{message}
chat.team = [[ Team]
chat.admin = [scarlet][[ Admins]

vote.global = [{col.sysmsg}]Напишите [{col.ok}]y[] или [{col.err}]n[] чтобы проголосовать. {if {votes} < 0 then [{col.err}] else [{col.ok}]}{votes}/{threshold}
vote.team = [{col.sysmsg}]Напишите [{col.ok}]/vote y[] или [{col.err}]/vote n[] чтобы проголосовать. {if {votes} < 0 then [{col.err}] else [{col.ok}]}{votes}/{threshold}
vote.left = [{col.sysmsg}]{player} вышел с сервера, его голос удалён.

vote.global.cancelled = [{col.err}]⚠ Голосование отменено админом {admin}.
vote.team.cancelled = [{col.err}]⚠ Командное голосование отменено админом {admin}.

admin.ban = [{col.err}]⚠ {target} Был забанен {admin}.
admin.kick = [{col.err}]⚠ {target} Был кикнут {admin}.
admin.wave = [{col.ok}]Волна пропущена админом {admin}.

[generic.checks]
same-vote = [{col.err}] Твой голос не изменился.
vote-filter = [{col.err}] Ты не можешь участвовать в данном голосовании.
cooldown = [{col.err}] Приостановись! Эта команда на кулдауне.
vote = [{col.err}] Голосование уже идёт.
unknown-command = [{col.err}] Не найдена команда [yellow]{command}[]. {if {suggestion} = () then (Проверьте [yellow]/help[].) else (Возможно вы хотели написать [yellow]/{suggestion}[]?)}
unknown-command-console =  Не найдена команда [yellow]{command}[]. {if {suggestion} = () then (Проверьте /help) else (Возможно вы хотели написать /{suggestion}?)}
invalid-arguments = [{col.err}] {cause}. Попробуйте [yellow]/help {command}[]!
invalid-arguments-console = {cause}. Использование: {command} {params}
permission = [{col.err}] У вас недостаточно прав для использования команды.
admin-action-permission = [{col.err}] У вас недостаточно прав для совершения данного действия.
admin-action-no-target = [{col.err}] Вы должны указать цель для исполнения данного действия.
admin-action-target-admin = [{col.err}] Вы не можете использовать это на другого админа.

[generic.command]
disabled = Команда [yellow]'/{command}'[] отключена
disabled-console = команда '{command}' отключена
missing = Пропущен параметр [yellow]'{commands.{command}.params.{param}}'[]
missing-console = Пропущен параметр '{param}'
invalid = Неправильный параметр [yellow]'{commands.{command}.params.{param}}'[]
invalid-console = Пропущен параметр '{param}'
string-termination = Параметр [yellow]'{commands.{command}.params.{param}}'[] не заключен должным образом.
string-termination-console = Параметр '{param}' не заключен должным образом.
end-of-input = Not enough input
end-of-input-console = Not enough input
too-many-arguments = Слишком много аргументов
too-many-arguments-console = Слишком много аргументов
not-a-number = Параметр [yellow]'{commands.{command}.params.{param}}'[] Должен быть числом
not-a-number-console = Параметр '{param}' Должен быть числом
not-a-boolean = Параметр [yellow]'{commands.{command}.params.{param}}'[] должен быть [#26d657]yes[] или [#EC5784]no[]
not-a-boolean-console = Параметр '{param}' Должен быть булевым
invalid-map-id = Неизвестный айди карты
invalid-map-id-console = Неизвестный айди карты
zero-map-id = Но оно отказано
zero-map-id-console = Но оно отказано
unknown-map = Неизвестная карта
unknown-map-console = Неизвестная карта
unknown-player = Игрок не найден
unknown-player-console = Игрок не найден
invalid-duration = Неправильный параметр срока [yellow]'{commands.{command}.params.{param}}'[]
invalid-duration-console = Неправильный параметр срока {param}

[generic.kick]
merged = [{col.err}]Этот аккаунт был объединен с другим и, следовательно, больше недоступен.
       =
       = [accent]Объединение не может быть отменено. Для большей информации посмотрите FAQ секцию в канале [cyan]#help[] на нашем сервере
       : [#4c59e0]Discord Сервере[].
       = [cyan]{generic.discord}
disconnected = [{col.err}]Эта учетная запись больше не привязана ни к одному пользователю.
             =
             = [accent]Если бы вы хотели перезайти создайте пост в канале [cyan]#help[] на нашем сервере
             :  [royal]Discord Сервере[].
             = [cyan]{generic.discord}
key-validation-failure-mdc = [{col.err}]Ошибка проверки ключа.
                           =
                           = [accent]Ключ на вашем устройстве не соответсвует хранимому на сервере.
                           = Удостоверьтесь что вы импортировали сохранения с нужным ключом. Если у вас нет доступа к нему,
                           : создайте публикацию в канале [cyan]#help[] на нашем дискорд сервере [#4c59e0]Discord Сервере[].
                           = [cyan]{generic.discord}
key-validation-failure-no-mdc = [{col.err}]Ошибка проверки ключа.
                              =
                              = [accent]Этот аккаунт защищён. Для вхрда в аккаунт вам нужно чтобы
                              : [#4c59e0]MindurkaCompat[] был установлен. Если у вас есть вопросы, создайте публицию в канале
                              : [cyan]#help[] на нашем сервере [#4c59e0]Discord Сервере[].
                              = [cyan]{generic.discord}
disabled = [{col.err}]Этот аккаунт отключен.
         =
         = [accent]Если вы хотите апеллировать данное решение, создайте пост в канале [cyan]#appeals[] на нашем сервере [#4c59e0]Discord Сервере[].
         = [cyan]{generic.discord}
shared = [{col.err}]Этот аккаунт больше недоступен из-за утечки его uuid.
       =
       = [accent]Это решение не может быть апелировано. Для больше информации посмотрите секцию FAQ в канале [cyan]#help[] на нашем сервере
       : [#4c59e0]Discord Сервере[].
       = [cyan]{generic.discord}
another-location = [{col.err}]Вы уже вошли на сервер.
         =
         = [accent]Если вы думаете что это ошибка создайте пост в канале [cyan]#help[] на нашем сервере
         : [#4c59e0]Discord Сервере[].
         = [cyan]{generic.discord}
ban = [{col.err}]Вы забанены на данном серверею
    =
    = [accent]Ban ID: [yellow]{id}
    = [accent]Админ: {admin}
    = [accent]Причина: [#EC5784]{reason}
    = [accent]Осталось: [yellow]{remaining}
    =
    = [accent]Если вы думаете что это ошибка создайте пост в канале [cyan]#appeals[] на нашем сервере
    : [#4c59e0]Discord Сервере[].
    = [cyan]{generic.discord}
kick = [{col.err}]Вы кикнуты с данного сервера.
     =
     = [accent]Kick ID: [yellow]{id}
     = [accent]Админ: {admin}
     = [accent]Причина: [#EC5784]{reason}
     = [accent]Осталось: [yellow]{remaining}
     =
     = [accent]Если вы думаете что это ошибка создайте пост в канале [cyan]#appeals[] на нашем сервере
     : [#4c59e0]Discord Сервере[].
     = [cyan]{generic.discord}
votekick = [{col.err}]Вы выгнаны голосование с данного сервера.
         =
         = [accent]Votekick ID: [yellow]{id}
         = [accent]Инициатор: {initiator}
         = [accent]Причина: [#EC5784]{reason}
         = [accent]Осталось: [yellow]{remaining}
         =
         = [accent]Голоса [lime]за[]:
         = {each {vote} in {votes} ([gray]- {vote}) split (\n) join (\n)}
         =
         = [accent]Если вы думаете что это ошибка создайте пост в канале [cyan]#appeals[] на нашем сервере
         : [#4c59e0]Discord Сервере[].
         = [cyan]{generic.discord}

[generic.gameover]

pvp = [accent]Игра завершена! Команда {team} вышла победителем.
    =
    = Следуйщая карта: [yellow]{map}[]

waves = [accent]Игра завершена! Достигнута волна {wave}.
      =
      = Следуйщая карта: [yellow]{map}[]

tie = [accent]Игра завершена! Ничья.
    =
    = Следуйщая карта: [yellow]{map}[]

attackWin = [accent]Игра завершена! Карта выиграна.
          =
          = Следуйщая карта: [yellow]{map}[]

attackLose = [accent]Игра завершена! Все ядра были уничтожены.
           =
           = [accent]Следуйщая карта: [yellow]{map}[]

unexpected = [accent]Игра завершена! Все ядра были уничтожены.
           =
           = Следуйщая карта: [yellow]{map}[]

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
        = если [blue][[страница][] уточнена, откроет написанную страницу.
        =
        = если [blue][[команда][] уточнена, откроет уточненую страницу.
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
        = если [blue][[страница][] уточнена, откроет написанную страницу.

params = [страница]
params.page = страница

select-page.title =  Выбрать страницу
select-page.message Страница: {page}
title-page =  Список доступных карт
man.title =  Команда maps
map = [orange]{id}. [accent]{map} [white]от [accent]{author}
    = [white]{width}x{height}
invalid-page = [{col.err}] Страница должна быть целым числом в диапазоне [yellow]1[] - [yellow]{max}[]. Здесь нету скрытых страниц. Обещаем.
negative-page = [{col.err}] Страница должна быть целым числом в диапазоне [yellow]1[] - [yellow]{max}[]. Что ты вообще ищешь?

[commands.setkey]

description = Переключение на использование публичного ключа
manpage = Переключение на использование публичного ключа
        =
        = [#4c59e0]MindurkaCompat[] генерирует пару ключей при запуске, которую совместимый сервер может проверить
        : для подтверждения подлинности владельца ключа.
        =
        = В отличие от UUID, утечка которого позволяет злоумышленнику-администратору выдать себя за игрока на любом сервере,
        : утечка публичного ключа безопасна, поскольку он бесполезен без своей приватной части. Приватный ключ
        : никогда не передаётся по сети.
        =
        = После включения авторизации по публичному ключу необходимо импортировать новое сохранение на всех устройствах,
        : с которых вы хотите получить доступ к аккаунту. Однако, в отличие от UUID, при импорте сохранения в Steam
        : это должно работать без дополнительных настроек.
        =
        = Безопасность авторизации по публичному ключу определяется защищённостью приватного ключа на устройстве.
        : Сохранения не должны публиковаться там, где третьи лица могут получить к ним доступ. Если вы хотите
        : сообщить об ошибке в Mindustry, сначала сбросьте все игровые данные, а затем воспроизведите ошибку.
        : Так ключи останутся в безопасности.
        =
        = Авторизация по публичному ключу работает только с совместимыми серверами; для остальных используется авторизация на основе UUID.
params =
title = Диалог подтвержения
message = [accent]Вы хотите использовать публичный ключ авторизации?
        =
        = [{col.err}]Это действие не может быть отменено.

ok = [#26d657] Успешно включено авторизация по публичному ключу.
error = [{col.err}] Уже включено
admin-protection = [#EC5784] Вы должны войти перед авторизацией публичному ключа на админском аккаунту.
no-mdc = [{col.err}] Открытый ключ не был предоставлен.
       =
       = Установите или включите [{col.mdc}]MindurkaCompat[] и повторите попытку снова.

[commands.vote]

description = Голосование в командном голосовании.
manpage = Голосование в командном голосовании.
        =
        = когда глобальные голоса работают по y/n, голосование команды работают через /vote y/n
        : чтобы сразу несколько команд могли голосовать разом.
params = [y/n/c]
params.vote = y/n/c

[commands.rtv]

description = Начать голосование за смену карты.
manpage = Начать голосование за смену карты.
        =
        = Если карта не уточнена выбирается рандомная. Список карт может быт посмотрен командой [#EC5784]/maps[].
params = [карта]
params.map = карта

vote.update = [{col.sysmsg}]{player} Хочет сменить карту на [yellow]{map}[].
vote.for = [{col.sysmsg}]{voter} Проголосовал [{col.ok}]за[] смену карты на [yellow]{map}[].
vote.against = [{col.sysmsg}]{voter} Проголосовал [{col.err}]against[] смену карты на [yellow]{map}[].
vote.commit = [{col.ok}]Смена карты на [yellow]{map}[].
vote.cancel = [{col.err}]Недостаточно голосов для смены карты.

[commands.vnm]

description = Начать голосование за следуйщую карту.
manpage = Начать голосование за следуйщую карту.
        =
        = Если карта не уточнена выбирается рандомная. Список карт может быт посмотрен командой[#EC5784]/maps[].
params = [карта]
params.map = карта

vote.update = [{col.sysmsg}]{player} Хочет сыграть на [yellow]{map}[] после данной игры.
vote.for = [{col.sysmsg}]{voter} проголосовал [{col.ok}]за[] игру на [yellow]{map}[] после данной игры.
vote.against = [{col.sysmsg}]{voter} проголосовал [{col.err}]против[] игры на [yellow]{map}[] после данной игры.
vote.commit = [{col.ok}]Следуйщая карта: [yellow]{map}[].
vote.cancel = [{col.err}]Недостаточно голосов для установления следуйщей карты.

[commands.votekick]

description = начать голосование за кик игрока.
manpage = начать голосование за кик игрока.
        =
        = если голосование пройдет успешно, игрок будет удален с игры на 30 минут.
        =
        = Причина должна быть короткой и верной.
        : Голосование без причины наказуемо.
params = <игрок> <причина..>
params.player = игрок
params.reason = причина

vote.update = [{col.sysmsg}]{player} хочет кик игрока {target} по причине [yellow]{reason}[].
vote.for = [{col.sysmsg}]{voter} проголосовал [{col.ok}]за[] кик {target}.
vote.against = [{col.sysmsg}]{voter} проголосовал [{col.err}]против[] кика {target}.
vote.commit = [{col.ok}]Кикнут {target} за [yellow]{reason}[] на [yellow]30 минут[].
vote.cancel = [{col.err}]Недостаточно голосов за кик {target}.

errors.self = [{col.err}] Ты не можешь кикнуть себя!
errors.self.special0 = [#49C9C7]u[#AE58C8]w[#49C9C7]u
errors.self.special1 = [{col.err}] Не здесь, спасибо.
errors.self.special2 = [{col.err}] Попробуй указать кого-нибудь другого!
errors.admin = [{col.err}] Админы иммунны к [yellow]/votekick[].
errors.players = [{col.err}] Недостаточно игроков за начало голосования.
errors.team = [{col.err}] Ты не можешь начать голосование за кик игрока из другой команды.

[commands.sync]
# i really do not like how it translating
description = Синхронизрует вас с сервером.
manpage = Синхронизрует вас с сервером.
params =

[commands.t]

description = Отправляет сообщение в чат команды.
manpage = Отправляет сообщение в чат команды.
params = <сообщение...>
params.message = сообщение

[commands.a]

description = Отправляет сообщение в чат администрации.
manpage = Отправляет сообщение в чат администрации.
params = <сообщение...>
params.message = сообщение

[commands.artv]

description = Смена карты без голосования.
manpage = Смена карты без голосования
        =
        = Если карта не уточнена выбирается рандомная. Список карт может быт посмотрен командой [#EC5784]/maps[].
        =
        = Нежели как [#EC5784]/rtv[], эта команда работает только у админов.
params = [карта]
params.map = карта

[commands.kick]

description = Кик игрока с сервера.
manpage = Кик игрока с сервера.
        =
        = Действует как бан но только на данный сервер.
params = <игрок> <длительность> <причина...>
params.player = игрок
params.duration = длительность
params.reason = причина

dialog.title = Кик {player}
dialog.reason = Причина кика
dialog.duration = Длительность кика

[commands.ban]

description = Бан игрока на всём сервере.
manpage = Бан игрока на всём сервере.
        =
        = Банит игрока на всём сервере.
params = <игрок> <длительность> <причина...>
params.player = игрок
params.duration = длительность
params.reason = причина

dialog.title = Бан {player}
dialog.reason = Причина бана
dialog.duration = Длительность бана

[commands.mute]

description = Мутит игрока на всём сервере.
manpage = Мутит игрока на всём сервере.
params = <игрок> <длительность> <причина...>
params.player = игрок
params.duration = длительность
params.reason = причина

dialog.title = Мут {player}
dialog.reason = Причина мута
dialog.duration = Длительность мута

[commands.fuck]

description = Использует предложенную команду.
params =
manpage = Использует предложенную команду.
        =
        = Команда чистятся каждый раз когда новая запускается
        : Команда будет хранится только если действующая не будет найдена.
        : Команда запустится с ранее указанными аргументами.

no-command-stored = [{col.err}] К сожалению для тебя здесь нет команды.
whoops = [{col.warn}]⚠ Упс! Здесь нет команды.

[commands.nick]

description = Устанавливает отображаемый никнейм.
params = [никнейм]
manpage = Устанавливает отображаемый никнейм
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

description = Подключится к хабу.
params =
manpage = Попытается подключить вас к хабу.
        =
        = Сервер выберет хаб соответсвующей вашей версии

errors.hub-down = [{col.err}]⚠ Хаб оффлайн.

[commands.surrender]

description = Начинает голосование за сдачу вашей команды.
params =
manpage = Голосование за сдачу вашей команды.
        =
        = Если голосование пройдет успешно все ядра вашей команды будут уничтожены.

vote.update = [{col.sysmsg}]{player} Хочет сдатся.
vote.for = [{col.sysmsg}]{voter} проголосовал [{col.ok}]за[] сдачу.
vote.against = [{col.sysmsg}]{voter} проголосовал [{col.err}]против[] сдачи.
vote.commit = [{col.ok}]Команда сдалась.
vote.cancel = [{col.err}]Недостаточно голосов для сдачи.
