# Veyloria MVP

MVP RPG-мод для Minecraft `1.21.1` на `NeoForge`, где сервер хранит аккаунты, профили, мобов, спавны, лут и прогрессию 1-80.

## Что реализовано

- серверная и клиентская части в одном моде с разделением по пакетам `common`, `server`, `client`
- SQLite БД, миграции и seed-данные
- регистрация и логин через клиентское auth-окно
- блокировка игрока до авторизации
- RPG-профиль: уровень, `xp_current`, `xp_total`, copper, базовые статы
- формула уровней 1-80 и модификаторы EXP по разнице уровней
- отдельные серверные рейты из `config/veyloria/rates.yml`
- data-driven шаблоны предметов, мобов, loot tables и spawn groups
- серверный спавн обычных мобов, элиты и босса рядом с авторизованными игроками с chunk-индексацией spawn groups
- серверный расчёт урона по мобам, серверное распределение EXP/валюты/персонального лута
- учёт `hostility_type` в бою (friendly/neutral/hostile), применение `aggro_radius`, `leash_radius`, `pack_spread`
- базовые кастомные предметы через vanilla item stacks + `CustomData`
- клиентский HUD для copper/маны и уведомлений, tooltip RPG-предметов
- надголовные полоски ресурсов у игроков: `HP` + `MP` в одном неймплейте
- тестовая world-layout схема: 7 северных зон `1-80`, дорога шириной 5 блоков, безопасная полоса 15 блоков, границы зон из белой шерсти
- отключён ванильный спавн мобов (`doMobSpawning=false`) и запрещено появление некастомных мобов
- seed-контент из ресурсов: 38 кастомных мобов, 93 spawn groups, 22 loot tables
- дроп экипировки реализован серверно (редкости, уровни предметов, оружие/броня, спецэффекты)
- мана активна только когда у игрока экипировано оружие, которое реально её использует (например, палочка); иначе `MP=0`
- для не-OP игроков запрещены строительство/ломание блоков (Adventure + серверные блокировки)
- добавлены команды: `/veyloria rates ...` (OP only, runtime-only) и `/party <nickname>` (для шаринга XP внутри зоны)

## Требования

- Java `21`
- Windows PowerShell / `cmd`

## Быстрый запуск

1. Собрать мод:

```powershell
.\gradlew.bat build
```

2. Запустить локальный сервер:

```powershell
.\start-server.bat
```

3. Запустить клиент из dev-среды:

```powershell
.\gradlew.bat runClient
```

> Если локальный мир уже был создан до перехода на superflat-пресет, удалите папку `run/world` и запустите сервер снова.

4. При первом входе откроется окно авторизации.
5. Зарегистрироваться новым паролем или войти существующим.

## Конфиги и данные

- серверный конфиг: `config/veyloria/server.yml`
- рейты: `config/veyloria/rates.yml`
- БД: `data/veyloria/rpg.db`
- миграции в ресурсах: `src/main/resources/data/veyloria/migrations`
- seed-данные в ресурсах: `src/main/resources/data/veyloria/seeds`
- layout и координаты тестовых зон: `docs/world-layout.md`

## Runtime-команды

- `OP`: `/veyloria rates show`
- `OP`: `/veyloria rates set <xp|currency|resource|equipment|consumable|boss_respawn> <value>`
- `OP`: `/veyloria rates reset`
- `все игроки`: `/party <nickname>`

## Запуск с друзьями

1. Собрать `jar` через `.\gradlew.bat build`.
2. Установить NeoForge `1.21.1` на машине-хосте и клиентам.
3. Положить собранный mod jar в `mods/` на сервер и на каждый клиент.
4. На сервере первый старт создаст `config/veyloria` и `data/veyloria`.
5. При необходимости поднять `xp_rate` в `config/veyloria/rates.yml`, например до `100`, чтобы быстро прогнать поздние уровни.

## Ограничения MVP

- нет квестов, классов, талантов, PvP, аукциона, гильдий и инстансов
- кастомные постройки с `structure_id` и отдельными правилами спавна в локациях пока не активированы в runtime
- runtime-проверка полного Minecraft-цикла в этой среде не автоматизирована; см. чеклист в `docs/manual-checklist.md`
