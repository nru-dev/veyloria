## Inventory Update Addendum

1. Open the custom inventory with `E` and verify the rebuilt screen shows a top-right copper counter, a player paper-doll, and a stat card for `Power`, `Vitality`, `Armor`, `Crit`, and `Haste`.
2. Verify the player loadout slots exist for `1/2/3` weapons, `4/5/6/7` consumables, `ammo`, helmet, chest, legs, boots, pendant, two rings, and two accessories.
3. Put arrows into the new ammo slot and verify they stack there instead of behaving like single-count equipment.
4. Put stackable consumables into the `4/5/6/7` slots and verify they stay stackable instead of collapsing to single-count equipment behavior.
5. Close the inventory and verify the bottom-right HUD still shows weapon rows `1/2/3` plus consumable quick slots `4/5/6/7`.
6. Verify that empty HUD slots do not render item icons, while filled slots render the correct icon and stack count.
7. Change active weapons with `1`, `2`, and `3` and confirm the weapon HUD keeps the normal fixed `1/2/3` order.
8. Press `4`, `5`, `6`, and `7` in gameplay and confirm the consumable moves to offhand, starts the standard vanilla eating/drinking animation immediately, and finishes without requiring RMB hold.
9. Put a stat-bearing weapon into a non-active slot such as `2` or `3` and verify the stat card updates immediately even before switching to that weapon.
10. Shift-click and drag items between backpack and loadout, then verify the slot frame positions and actual click targets are aligned and no rows appear shifted.

## Target Lock + Homing Addendum

1. Equip a bow in weapon slot `3`, stand near multiple mobs, and verify only one best target in front FOV gets the active lock highlight.
2. Move so the candidate is outside FOV or behind a wall and verify the marker disappears (lock requires LOS on selection).
3. Shoot while a marker is active and verify spawned arrow uses soft homing toward the marked target without instant speed boost.
4. During arrow flight, briefly break LOS with a wall and verify homing still follows last known target point for about `8` ticks, then continues as non-homing straight flight if LOS is not restored.
5. Verify arrows still collide with world blocks normally and never pass through solid blocks.
6. With `targetOnlyHit=true`, verify the homing arrow ignores other living entities and only collides with its assigned target.

## Server Target Sync Addendum

1. Stand between a managed RPG mob and a non-managed living entity and verify target lock marker appears only on the managed RPG mob.
2. Keep the marker on one RPG mob, hold LMB for 2-3 seconds, and verify melee damage is applied consistently to that marked mob.
3. Move crosshair away from mobs until marker disappears, then hold LMB and verify melee attacks do not randomly damage nearby mobs.
4. Force a mob to evade/reset (leave leash zone and return), then verify marker clears during evade immunity and melee does not register until the mob is attackable again.

## Starter Bow Addendum

1. Join with a fresh player profile and verify starter loadout grant includes both the existing test sword and the new legendary test bow.
2. Verify the same first-login grant also provides arrow stacks (`4 x 64`) in inventory or dropped nearby if inventory is full.
3. Reconnect with the same profile and verify sword/bow/arrows are not granted again (one-time persistent grant behavior).

## Target Visual + Arrow Flight Addendum

1. Lock onto a managed RPG mob and verify there is no center HUD square and no target text panel (name/distance/HP).
2. Verify the locked target receives only a red in-world outline.
3. Move lock to another mob and verify red outline transfers cleanly (previous target outline is removed).
4. Lose lock (turn away/out of range) and verify the red outline is cleared.
5. Shoot a locked target with bow and verify the arrow trajectory has no gravity drop (flat flight), while still homing toward lock target.

## Target Priority + Stuck Arrow Addendum

1. Place two valid targets in view, one crossing the exact crosshair ray and one slightly closer to cursor center by angle, then verify lock picks the one directly under look ray.
2. Place several valid targets without direct ray intersection and verify lock picks the target with the smallest center-ray offset (closest to cursor), with nearer target winning ties.
3. Verify lock can acquire candidates up to wider FOV (about `135°`) while still respecting LOS and range.
4. Verify homing turns tighter than before (default steering turn rate `0.20`, about 2x previous).
5. Shoot a homing arrow into a block and verify the embedded arrow no longer keeps trying to steer/lift after impact.

## Weapon Range + Ammo Capacity Addendum

1. Equip sword/axe as active weapon and verify lock range shrinks to melee reach only (distant mobs are not selected).
2. Equip the starter bow (`test_best_bow`) and verify lock can select valid targets up to about `50` blocks.
3. Shift-click multiple arrow stacks into ammo slot and verify total stack in that slot never exceeds `64`.
4. Verify normal arrow stacks in regular storage/inventory remain vanilla-sized (global stack size unchanged).

# Manual Checklist

## Вход и спавн

1. Подключиться к серверу новым аккаунтом.
2. Убедиться, что профиль создаётся автоматически без auth-окна и без команд `/register`/`/login`.
3. Проверить, что игрок появляется на стартовой дороге зоны `1-10`, а не в пещере/случайной точке.
4. Перезайти тем же UUID и убедиться, что загружается тот же профиль.

## Профиль и HUD

1. Нажать `E` и убедиться, что открывается кастомный инвентарь, а не vanilla inventory screen.
2. Проверить layout:
   - слева видны слоты `1/2/3` для оружия и слоты брони,
   - справа видны storage-слоты игрока,
   - vanilla hotbar внизу экрана отсутствует.
3. Положить melee-оружие в слот `1` или `2`, ranged-оружие (лук/палочка) в слот `3`.
4. Закрыть инвентарь и проверить переключение активного оружия клавишами `1`, `2`, `3`.
5. Убедиться, что при переключении меняется предмет в руке, а tooltip vanilla hotbar не появляется.
6. После входа проверить, что exp bar показывает RPG-уровень/прогресс.
7. Проверить отображение `Copper` в HUD.
8. Взять в руки палочку/мана-оружие и убедиться, что в HUD показывается `Мана: X/Y`.
9. Сменить оружие на немана-тип (меч/топор/лук) и убедиться, что мана в HUD скрывается.
10. Подойти к другому игроку и проверить над головой оба бара (`HP` и `MP`), обновление значений при получении урона/лечении.
11. Изменить `xp_rate` на `100` и перезапустить сервер.
12. Убедиться, что темп роста EXP изменился без изменения валюты/лута.

## Мобы и бой

1. Подойти к зоне `forest_wolf` у стартовой области.
2. Убедиться, что появляются серверно заспавненные мобы с уровнем в имени.
3. Убить моба и проверить уведомление `+EXP, +Copper`.
4. Проверить, что с мобов выпадает экипировка на землю.
5. Подождать 3 минуты и убедиться, что неподобранный предмет исчезает.
6. Проверить элитную и босс-зоны из seed-данных.
7. Дождаться дня и убедиться, что hostile-мобы продолжают спавниться и не получают урон от солнца.
8. Убить моба минимум на 6 уровней ниже и проверить, что EXP = 0.

## Лут и предметы

1. Навести на выпавший RPG-предмет в инвентаре.
2. Убедиться, что видны редкость, required level и базовые статы.
3. Надеть предметы weapon/armor и снова проверить урон/живучесть.
4. Поставить только `equipment_drop_rate = 10`, перезапустить сервер и проверить, что чаще падает экипировка, но EXP не растёт из-за этого.
5. Проверить ограничение уровня: предмет уровня выше уровня персонажа автоматически снимается и возвращается в инвентарь/дропается рядом.

## Кооп

1. Создать группу через `/party <nickname>` и проверить, что алиас `/p <nickname>` работает так же.
2. Добавить третьего игрока через `/party add <nickname>` от лидера и проверить лимит 5 участников.
3. Попробовать `/party add <nickname>` от не-лидера и убедиться, что добавление запрещено.
4. Проверить `/party kick <nickname>` от лидера и запрет кика от не-лидера.
5. Лидеру выполнить `/party leave` и убедиться, что новый лидер выбирается из оставшихся участников.
6. Выполнить `/party help` и `/p help`, проверить, что выводится актуальная справка по всем подкомандам.
7. Двум игрокам (в одной зоне) ударить одного и того же моба и проверить, что оба получают EXP.
8. Оставить одного участника пати в другой зоне и убить моба в текущей зоне.
9. Проверить, что участник из другой зоны EXP не получает.

## Hostility (дополнительно)

1. Добавить в seeds одного neutral и одного friendly моба и перезапустить сервер.
2. Проверить, что friendly не получает урон от игрока и не наносит урон игроку.
3. Проверить, что neutral не бьёт игрока первым, но отвечает после удара.
4. Проверить, что hostile при возможности переключается на nearby friendly.
