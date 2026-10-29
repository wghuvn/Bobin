# Полный проект в репозитории Bobin

29 октября 2026. Bobin Craft 2.7.7-clean, Minecraft Fabric 1.20.1, Java 17.

## Состав

Предыдущий коммит `f2941ee` содержал только 61 файл изменений задачи №3.
Этот коммит добавляет исходную базу и все 45 файлов этапа №2, сохраняя
актуальные версии этапа №3. Все 61 ранее опубликованных файла сохранены
побайтно; исходники и ресурсы восстановлены из полного актуального архива
`Bobin_craft-2.7.7-clean-armament-task3-source.zip`
(SHA-256 `dc355800f9016bafd551f76957445503a3c7e6f8dda54ea53aa3c56e5b8fd165`).

В корне находятся `settings.gradle`, `build.gradle`, `gradle.properties`,
`gradlew`, `gradlew.bat` и Gradle wrapper 8.8 с JAR и checksum дистрибутива.
`gradlew` имеет executable bit для Linux. Версии мода, Fabric, Minecraft и
зависимостей сохранены. Добавлен ранее согласованный документ
[ARCHITECTURE_RU.md](ARCHITECTURE_RU.md).

Все исходные Java-классы существующих систем и их ресурсы сохранены.
Относительно исходного ZIP изменены только ранее утверждённые в задаче №3
`build.gradle` и `fabric.mod.json`, а также расширен `.gitignore`.
Блок удаления `retiredFiles` и Java source exclusions в `build.gradle`
побайтно совпадают с базой этапа №2.

Из 2484 файлов актуального архива исключён только рабочий `logs/latest.log`.
Кеши `.gradle`, каталоги `build`, `run`, временные файлы Blender/Python и другие
рабочие результаты не включены. Проверочные отчёты в `docs`, включая
исторические `.log` этапов №2–3, сохранены как документация; новые рабочие
логи текущей проверки находятся вне репозитория. `.gitignore` защищает от
случайного добавления кешей и разрешает опубликованные отчёты `docs/**/*.log`.

## Проверки объединённого checkout

| Проверка | Результат |
|---|---|
| Наличие всех 45 файлов этапа №2 | PASS |
| Сохранность всех 61 файла этапа №3 | PASS, побайтное сравнение |
| Java 17 syntax parsing всех исходников | PASS: 341 production + 20 test, всего 361 файл |
| Внутренние импорты | PASS: 409 импортов, отсутствующих локальных типов не обнаружено |
| Полностью квалифицированные локальные ссылки | PASS: 414 различных ссылок, с учётом вложенных типов |
| Entrypoints и mixin-классы из metadata | PASS: 4 entrypoints, 27 mixins существуют |
| CPU Gradle target API/GLB, реальные JglTF/Jackson | PASS: компиляция Java 17, 191 тест, 0 ошибок/пропусков |
| Граница common/client | PASS в составе API-тестов |
| JSON Schema | PASS: 6 схем, 83 structural cases |
| Существующий `scripts/verify_stage2.py` | PASS: 1518 JSON resources, Java 17 contract с 4196 assertions |
| Полная Gradle-сборка мода | BLOCKED: HTTP 403 при разрешении Fabric Loom 1.7.4, до `compileJava` |

CPU target использует тот же `gradle/armament-libraries.gradle`, что и мод:
JglTF 3.0.1, Jackson BOM 2.22.1 и соответствующие runtime jars успешно
разрешены. Gson 2.10 и JUnit 5.10.3 соответствуют проверкам предыдущего этапа.
Пропущенных файлов API или локальных импортов после объединения не найдено.

Синтаксический разбор и проверка локальных ссылок не проверяют сигнатуры
Minecraft/Fabric API. CPU target намеренно исключает Minecraft renderer,
GPU resource classes, debug preview и client entrypoint. Полная компиляция
мода, упаковка зависимостей в remapped JAR, запуск Minecraft и регрессия
действующих систем в игре остаются неподтверждёнными.

## Сборка

Полная сборка фактически запущена на Temurin 17.0.21+9 и Gradle wrapper 8.8:

```sh
./gradlew build --no-daemon --console=plain --stacktrace --info
```

Завершилась с exit code 1 при конфигурации проекта:

```text
HTTP 403 Forbidden:
https://maven.fabricmc.net/fabric-loom/fabric-loom.gradle.plugin/1.7.4/fabric-loom.gradle.plugin-1.7.4.pom
Plugin [id: 'fabric-loom', version: '1.7.4'] was not found
```

`maven.fabricmc.net` отсутствует в разрешённых доменах сетевой политики
окружения. Использованы штатный proxy и системный Java truststore; сетевые
ограничения не обходились. Для продолжения нужен доступ среды к Fabric Maven;
следующие загрузки Minecraft/Fabric и требования среды ещё не проверены.

Независимые проверки можно повторить на Java 17:

```sh
./gradlew -p tools/armament-glb-check clean test dependencyReport --no-daemon
python3 scripts/verify_armament_schemas.py
python3 scripts/verify_stage2.py
```

Для Python-проверки схем нужны development-пакеты `jsonschema` и `referencing`;
это не зависимости мода. Новых функций и изменений игровых систем нет.
