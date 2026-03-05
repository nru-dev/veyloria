## 2026-03-05 Common/Elite AI Patch Service Addendum

- Added a separate server-side `CommonMobAiService` that patches AI behavior onto existing spawned mobs (`PathfinderMob`) instead of replacing entity types or introducing custom mob classes.
- AI patching is applied once per mob via persistent tags and restores deterministic behavior on entity join using custom goal/target goal installation with duplicate-safe replacement.
- Common/Elite mobs now use unified rules:
  - disposition matrix `allied / neutral / hostile`,
  - home anchor + spawn group metadata (`homePos`, `groupId`, `leaderId`),
  - idle bounded to `HOME_WANDER_RADIUS = 15`,
  - combat leash `COMBAT_LEASH_RADIUS = 30`,
  - return-stop radius `RETURN_STOP_RADIUS = 3`,
  - unreachable timeout `CANNOT_REACH_TICKS = 100`,
  - evade regen at `REGEN_PER_TICK = 1/40 max HP`.
- During evade, patched mobs stop target acquisition, clear combat target, regenerate, and return to `homePos`; incoming damage to them is canceled until they return within stop radius.
- Neutral/allied/hostile interaction checks moved to a unified `canTarget(...)` gate with recent-memory timers for conditional neutral cases (`neutral attacked player/hostile`, `neutral was provoked/attacked`).
- Player damage to allied mobs is now blocked through incoming-damage guard and melee entry checks.
- Legacy manual AI loops in `MobSpawnService` were restricted to `BOSS` mobs only; Common/Elite now rely on the new patched goal pipeline.

## 2026-03-04 Inventory Addendum

- The custom loadout was expanded from the first 11 slots to 17 slots by appending new equipment entries instead of renumbering existing ones. This preserves already-saved player loadouts and avoids NBT migration for weapon, armor, boots, and consumable items that were already stored.
- The new loadout shape is: three weapon slots, four consumable slots, helmet/chest/legs/boots, pendant, two rings, two accessories, and one ammo slot for arrows or future `EquipSlot.AMMO` items.
- Weapon selection remains on `1/2/3`, while consumables on `4/5/6/7` are instant-use actions executed through a server-authoritative offhand auto-use path. The server validates the slot and mirrors the item into offhand, while the client keeps vanilla `use` held long enough for eating/drinking to complete without manual RMB hold.
- Passive stat aggregation now reads directly from equipped loadout slots instead of only the currently held weapon plus mirrored armor. This makes sword, bow, and other equipped weapon bonuses apply immediately even when that weapon is not the active one in hand.
- The custom inventory menu and screen now share one layout definition so slot frames, click targets, and storage rows stay aligned as the screen evolves.

## 2026-03-04 Target Lock Addendum

- Added a dedicated targeting subsystem (`TargetingProfile`, `TargetingService`, `PlayerTargetState`) instead of embedding targeting logic directly in weapon handlers.
- Current target selection is auto-driven by FOV + range + LOS; lock requires LOS at selection time and stores target UUID in a player attachment for both client HUD and server systems.
- Added a server-authoritative `HomingArrowEntity` with target UUID sync, soft steering that preserves vanilla arrow speed, and LOS memory (`lastKnownPos` + `memoryTicks`) without any wall clipping or noClip behavior.
- Bow shots now preserve vanilla launch trajectory and are upgraded to homing arrows only when a valid current lock target exists.
- Target marker rendering is implemented as a HUD overlay with world-to-screen projection and strict visibility checks (valid target + on-screen + LOS when required).

## 2026-03-04 Server Target Sync Addendum

- Melee targeting is now server-authoritative end-to-end: the server selects attackable lock candidates and exposes them to the client HUD via marker channel `[veyloria:target]`.
- Client no longer computes lock targets locally each tick; it only renders the current server-provided target UUID.
- Melee intent resolution now prioritizes the current server lock target before ray fallback, so the target under marker and the target receiving melee damage are aligned.
- Server lock candidate filtering is restricted to managed RPG mobs (`MobTemplate` exists, hostility is not friendly, mob is not in evade immunity).

## 2026-03-04 Starter Bow Addendum

- New players now receive a one-time starter legendary test bow (`test_best_bow`) in addition to the existing test sword.
- The same onboarding grant also gives starter arrows (`4 x 64`) so bow gameplay can be tested immediately without crafting or loot dependency.

## 2026-03-05 Target Visual + Arrow Flight Addendum

- Removed the target HUD lock frame and textual target panel (name, distance, HP) to reduce on-screen clutter during combat.
- Current server-selected target is now highlighted directly in-world with a red outline (client-side glow + dedicated local scoreboard team color).
- `HomingArrowEntity` now ignores gravity so locked bow shots do not have ballistic drop while preserving the existing homing steering logic.

## 2026-03-05 Target Priority + Stuck Arrow Addendum

- Target lock selection now prioritizes entities directly intersected by the look ray (the entity the player is currently looking at).
- If no direct look-ray hit exists, lock falls back to a cursor score dominated by center-angle offset and lightly weighted by distance.
- Default lock FOV was increased from `90` to `135` degrees (about 1.5x wider).
- Default homing turn rate was increased from `0.10` to `0.20` (2x tighter steering).
- Homing arrows now stop homing when embedded in block/ground (`inGround`), preventing repeated failed lift attempts after impact.

## 2026-03-05 Weapon Range + Ammo Capacity Addendum

- Server lock range is now weapon-aware:
  - melee weapons (`sword_2h`, `axe`) use melee reach targeting range,
  - starter bow (`templateCode = test_best_bow`) uses `50` block target range.
- Ammo loadout slot capacity was reverted back to vanilla `64` (slot-local), because `9999` stacks caused unstable runtime/save behavior in practice.

## 2026-03-05 Combat Consistency + Docs Lock

- Custom inventory screen was removed from active UX; players use vanilla inventory and vanilla hotbar again. RPG loadout remains server-side data (`PlayerLoadoutData`) and equipment mirroring logic.
- Player knockback from managed mob hits is hard-suppressed server-side:
  - incoming mob damage sets short knockback suppression window,
  - `LivingKnockBackEvent` forcefully zeroes knockback vectors for this window,
  - horizontal player velocity is reset on hit as fallback.
- Managed mob relations were normalized:
  - only `friendly <-> hostile` managed mob combat is allowed,
  - other managed mob-vs-mob combinations are canceled.
- Neutral retaliation was hardened:
  - neutral mobs keep aggro target synchronized every tick (not only shard tick),
  - neutral mobs reacquire target from aggro state even if vanilla AI drops `target`,
  - incoming damage guard allows neutral damage when neutral currently targets that player and refreshes aggro state.
- Spawn group size for non-boss managed mobs is uniform random `1..6`; bosses remain single-instance units.
- Manual checklist and README were updated to match current runtime behavior and to remove outdated assumptions about the removed custom inventory UI.

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

## 2026-03-05 Structures Module Addendum

- Система кастомных структур выделена в отдельный модуль `server.structure` с изолированными классами генерации и размещения, чтобы изменения можно было переносить отдельным коммитом независимо от боевой/лут/party логики.
- Источник данных структур сделан data-driven и включён в общий bootstrap БД:
  - `structure_templates`
  - `structure_spawn_rules`
  - `structure_instances`
- Для файлов структур поддержаны оба формата:
  - WorldEdit `.schem` (через адаптер `WorldEditClipboardLoader`),
  - vanilla template `.nbt` (через `VanillaNbtClipboardLoader`).
- Runtime-логика структур:
  - на сиде мира генерируются точки структур по правилам зон (`count_min/max_per_zone`, дистанции до дороги, минимальные интервалы между постройками),
  - физическое размещение происходит лениво при загрузке чанков структуры,
  - перед вставкой область структуры подготавливается layout-сервисом, чтобы superflat-декорация не перетирала структуру.
- Добавлен locate-интерфейс для кастомных структур в стиле vanilla:
  - `/locate structure veyloria:<id>`
  - alias: `/locale structure veyloria:<id>`
  - при locate неразмещенной структуры мод размещает ее и возвращает координаты;
  - если структура помечена `placed`, но отсутствует в мире, locate восстанавливает ее;
  - locate возвращает опорную точку внутри схемы, а не только сырой origin инстанса.
- Добавлены административные команды:
  - `/veyloria structures status`
  - `/veyloria structures reload`
  - `/veyloria structures placeall`
- Для сохранения уже размещённых структур выравнивание тестового ландшафта переведено на одноразовое декорирование чанка (без повторного «перетирания» каждого активного чанка каждые N тиков).
