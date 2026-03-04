## 2026-03-04 Inventory Addendum

- The custom loadout was expanded from the first 11 slots to 17 slots by appending new equipment entries instead of renumbering existing ones. This preserves already-saved player loadouts and avoids NBT migration for weapon, armor, boots, and consumable items that were already stored.
- The new loadout shape is: three weapon slots, four consumable slots, helmet/chest/legs/boots, pendant, two rings, two accessories, and one ammo slot for arrows or future `EquipSlot.AMMO` items.
- Weapon selection remains on `1/2/3`, while consumables on `4/5/6/7` are instant-use actions executed through a server-authoritative offhand auto-use path. The server validates the slot and mirrors the item into offhand, while the client keeps vanilla `use` held long enough for eating/drinking to complete without manual RMB hold.
- Passive stat aggregation now reads directly from equipped loadout slots instead of only the currently held weapon plus mirrored armor. This makes sword, bow, and other equipped weapon bonuses apply immediately even when that weapon is not the active one in hand.
- The custom inventory menu and screen now share one layout definition so slot frames, click targets, and storage rows stay aligned as the screen evolves.

# Veyloria MVP Decisions

## 2026-03-04

- Проект оставлен одним Gradle-модулем NeoForge, но код жёстко разделён по пакетам `common`, `server`, `client`. Это сокращает инфраструктурную сложность без потери сервер-авторитетной модели.
- Для текущего тестового цикла ручные `/register` и `/login` отключены: сервер автоматически создаёт аккаунт и сессию при входе игрока по UUID, чтобы не блокировать боевое тестирование.
- Вместо отдельной хост-службы Windows реализованы `start-server.bat` и `stop-server.bat`, а инициализация БД, миграций и seed-данных встроена в серверный runtime мода.
- Папки `config/`, `data/` и `logs/` создаются автоматически внутри игрового каталога NeoForge при старте сервера.
- Для предметов MVP используются обычные vanilla item stacks с RPG-метаданными в item attachment. Это даёт переносимость, обычный инвентарь Minecraft и клиентскую синхронизацию без отдельной инвентарной подсистемы.
- Спавн-группы индексируются по чанкам и затем отбираются только в активной зоне около онлайн-игроков с загруженным профилем; это уменьшает стоимость тика при росте количества групп.
- Friendly/neutral/hostile реализованы как серверные правила поверх vanilla `Mob`: friendly не получают/не наносят урон игрокам и приоритизируют hostile-мобов, neutral начинают наносить урон игроку только после агра, hostile при отсутствии цели могут переключаться на friendly.
- Для контроля боевых зон применяются `pack_spread_min/max` при пачечном спавне, `aggro_radius` через `FOLLOW_RANGE` и `leash_radius` через возврат моба к центру spawn group.
- Для отладки выделены лог-каналы `veyloria.auth`, `veyloria.db`, `veyloria.spawn`, `veyloria.combat`, `veyloria.loot`.
- Отображение уровня/типа моба сделано через серверно выставляемое кастомное имя сущности. Это минималистично, прозрачно для тестеров и не требует тяжёлого клиентского рендера в MVP.
- Клиентская HUD/feedback-синхронизация идёт через скрытые системные маркеры сервера (`profile`, `bars`, `gain`, `loot`, `error`) без отдельного сетевого протокола.
- Для тестового цикла прогрессии 1-80 введена фиксированная геометрия мира: 7 зон по оси `-Z` (север), каждая по 640 блоков длиной и 480 блоков шириной. Дорога (`stone_bricks`) и разделители зон (`white_wool`) накладываются серверным layout-сервисом по мере загрузки чанков.
- Безопасная полоса вдоль дороги задана как `X ∈ [-7, 7]`; спавн кастомных групп в этой полосе и на линиях-разделителях зон запрещается на уровне spawn-сервиса.
- Чтобы убрать ванильных существ в тестовом режиме, при старте сервера принудительно применяется `doMobSpawning=false`, а сущности типа `Mob` без маркера Veyloria блокируются при входе в мир.
- Для стабильного дневного PvE у кастомных hostile-мобов отключён урон от солнечного выгорания: спавн и жизнь hostile не зависят от времени суток.
- Для редких групп (элиты/боссы с единичным лимитом и долгим респавном) добавлен стартовый bootstrap в загруженных чанках, чтобы ключевые мобы стартовой зоны гарантированно присутствовали после запуска сервера.
- Loot строится как server-side генерация дропа и seed-таблиц; экипировка и её статы рассчитываются на сервере и вшиваются в предмет через `CustomData`.
- Добавлены runtime-команды для OP-администраторов `/veyloria rates ...`: изменения рейтов действуют только до рестарта, после рестарта загружаются значения из `rates.yml`.
- Для тестового PvE отключён системный голод (food/saturation/exhaustion принудительно поддерживаются на серверном тике).
- World restrictions для не-OP игроков реализованы через принудительный Adventure-режим и отмену block-interaction событий: нельзя ломать/ставить блоки.
- Лут экипировки включён как server-side drop в мир (`ItemEntity`) с таймером исчезновения; XP и валюта по-прежнему выдаются сервером по правилам деления на участников.
- Добавлен party-runtime (`/party <nickname>`): XP шэрится на участников пати в той же зоне, даже если не все члены пати били моба напрямую.
- Party-команды расширены алиасом `/p` и подкомандами `add/kick/leave/help`; добавление/исключение доступно только лидеру, лимит группы — 5 участников, при выходе лидера лидерство передаётся случайному участнику.
- Распределение EXP в пати привязано к зоне: опыт получают участники пати только в той же зоне, где погиб моб (без проверки дистанции 32 блока).
- Оптимизация тиков: в spawn-сервисе убраны повторные полные обходы `trackedMobs` на каждую группу (alive-счётчики считаются один раз за тик/измерение), а поиск целей friendly/hostile переведён на локальный spatial-поиск в AABB.
- Оптимизация сети/клиента: рассылка надголовных `HP/MP` баров стала дельта-ориентированной (кэш по паре viewer/subject + heartbeat), на клиенте убраны лишние копии списка уведомлений и ускорен парсинг marker-сообщений.
- В бою добавлены threat-сумма по игрокам и retarget на лидера угрозы; при выходе моба за leash-radius включается evade (возврат на спавн + полный heal).
- Для чтения состояния группы без отдельного GUI добавлены надголовные бары ресурсов (`HP` + `MP`) через расширение name-tag рендера на клиенте; значения синхронизируются сервером скрытыми системными маркерами.
- Мана ограничена по экипировке: сервер учитывает/регенерирует `MP` только если в руках есть оружие с `manaCost > 0` (например, палочка). При смене на немана-оружие `MP` принудительно становится `0`.
- Vanilla inventory/hotbar выведены из пользовательского UX: вместо них используется собственный screen/menu с loadout-слотами (`main`, `secondary`, `ranged`, `helmet`, `chest`, `legs`, `boots`) и правой секцией storage. Для совместимости с vanilla боёвкой и рендером активное оружие зеркалится в скрытый `inventory slot 0`, а броня — в стандартные armor slots.
