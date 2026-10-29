# Задача №3 — настоящий GLB loader и минимальный полигональный backend

29 октября 2026. Bobin Craft 2.7.7-clean, Fabric 1.20.1, Java 17.

**Статус:** Blender → GLB → Java CPU-данные проверено фактически. Исходники Minecraft backend подготовлены, но его компиляция и отображение в игре **не подтверждены**: Fabric Maven недоступен через сетевую политику среды. Minecraft не был запущен. Изображение Blender не является игровым скриншотом.

## 1. Что поставляется

- [Тестовый GLB](../../src/main/resources/assets/bobin_craft/armament/debug/technical_fixture.glb) — файл экспортирован Blender, не собран из заранее заданных Java-вершин.
- [Исходник Blender](../../art/armament/technical_fixture.blend), [текстура](../../art/armament/uv_checker.png), [предпросмотр Blender](../../art/armament/technical_fixture.blender-preview.png), [эталон координат](../../art/armament/technical_fixture.reference.json).
- [Воспроизводимый экспорт](../../scripts/armament/export_glb_fixture.py).
- `client/model/glb`: immutable CPU scene, JglTF adapter, GLB preflight, преобразование статических узлов и owned-resource cache.
- `client/render/fabric`, `client/resource/fabric`, `client/debug`: текстуры Minecraft, собственный GLSL shader, TRIANGLES BufferBuilder backend, resource reload и opt-in world preview.
- Два новых JUnit test-класса и отдельный [CPU Gradle target](../../tools/armament-glb-check/build.gradle), использующий реальные JglTF/Jackson jars.
- [Полный список изменений](TASK3_CHANGES.txt), [точный diff трёх существующих файлов](task3/EXISTING_FILES.diff), [результаты JSON](task3/validation/results.json).

Ссылки на исходники и ассеты выше указывают от `docs/armament` к корню проекта.

## 2. Зависимости и лицензии

Проверен `de.javagl:jgltf-model:3.0.1`. POM задаёт Jackson databind 2.22.1 и core 2.18.8. Версии выровнены официальным `jackson-bom:2.22.1`, фактический runtime graph получен Gradle из Maven Central:

| Артефакт | Версия |
|---|---|
| `de.javagl:jgltf-model` | 3.0.1 |
| `de.javagl:jgltf-impl-v1` | 3.0.1 |
| `de.javagl:jgltf-impl-v2` | 3.0.1 |
| `com.fasterxml.jackson.core:jackson-databind` | 2.22.1 |
| `com.fasterxml.jackson.core:jackson-core` | 2.22.1 |
| `com.fasterxml.jackson.core:jackson-annotations` | 2.22 |

Impl-v1 приходит как реальная зависимость JglTF, но loader принимает только GLB/glTF 2.0. Никаких GeckoLib, JOGL, Forge, Assimp, natives или библиотек анимации не добавлено. Gson 2.10 и JUnit 5.10.3 в CPU harness соответствуют уже используемому Minecraft/API-тестам; runtime Gson не заменяется.

Все шесть jars сверены с опубликованными Maven Central SHA-1; локальные SHA-256 и координаты сохранены в [verified-artifacts.json](task3/validation/verified-artifacts.json). Base classes имеют major 52/53 и исполняются на Java 17. У jackson-core есть multi-release реализации для Java 9/11/17/21; Java 21 class-файлы не являются требованием Java 21, стандартный Java 17 выбирает подходящую ветку. Реальное исполнение reader/Jackson подтверждено тестами на Temurin 17.0.21+9.

JglTF — MIT; Jackson — Apache-2.0, с дополнительными встроенными notices для числовых парсеров. Полные обнаруженные LICENSE/NOTICE, включая встроенные third-party notices, сохранены в `src/main/resources/META-INF/licenses/armament`. JglTF MIT добавлен отдельно, поскольку в проверенных JglTF jars текста лицензии нет. TaCZ-код/ассеты не использовались.

Пины общие для mod build и CPU harness: `gradle/armament-libraries.gradle`. В основном `build.gradle` каждая из шести библиотек явно добавлена в Loom `include`, поскольку он не включает transitive graph автоматически. Упаковка итогового remapped mod jar ещё не проверена из-за блока Loom.

Совместимость reader с Java 17 доказана. Подключение client API написано под Fabric 1.20.1 и сверено с официальными исходниками Fabric и Yarn ветки 1.20.1, commit `9672e1ffb4f7c7059e7a0715a971729c54dfb7d2`. Это статическая проверка API, **не успешная компиляция Minecraft backend**. Новые классы находятся только в armament/client; common API не зависит от них.

## 3. Реальный образец Blender

Blender **4.3.2**, экспорт встроенным glTF exporter в GLB 2.0. Пять полигональных частей: восьмигранная платформа, шестигранная колонна, гранёная вершина, полигональное кольцо и треугольный указатель направления. Есть UV, один встроенный PNG 64×64, четыре материала и вложенный узел с translation/rotation/non-uniform scale. Это технический объект, не оружие.

Результат: **7 nodes, 5 meshes/primitives, 172 triangles**, габариты **1×1×1 метр**. Эталонный JSON рассчитан самим Blender из `matrix_world @ vertex.co` до экспорта. Java-тест сравнивает каждую загруженную вершину с этим независимым набором с допуском 3e-5. Проверены единичные нормали, inverse-transpose при неравномерном scale и winding при отражении.

Контракт единиц: 1 Blender unit = 1 метр = 1 Minecraft block. Blender exporter выполняет `(x,y,z) → (x,z,-y)`; GLB loader и renderer не применяют эту конверсию повторно. Никаких `/16`, ручной компенсации масштаба или перевода в ModelPart. Указатель Blender +Y экспортируется в -Z модели (Minecraft north); нормали и winding остаются согласованными.

PNG имеет асимметричные маркеры ориентации: красную верхнюю и зелёную левую полосы. Тест проверяет decoded pixel positions и UV-данные. В shader нет дополнительного V-flip. Это проверка данных/кода, а не визуальная проверка UV в Minecraft.

Khronos `gltf-validator@2.0.0-dev.3.10`: **0 errors, 0 warnings**, 3 info (`UNUSED_OBJECT` для UV у трёх материалов без текстуры). Полный [отчёт валидатора](task3/validation/gltf-validator-report.json). Валидатор установлен только как внешний development tool, в мод не добавлен.

Экспорт:

```sh
blender -b --python scripts/armament/export_glb_fixture.py
```

Скрипт сохраняет `.blend`, packed texture, GLB, reference JSON и CPU-render Blender. Первый запуск выявил отсутствие OpenImageDenoise в установленном Blender; denoising выключен в скрипте, затем экспорт и CPU-render успешно выполнены. Проверки целостности не отключались.

## 4. Профиль loader и обработка ошибок

Реальный `GltfModelReader.readWithoutReferences(InputStream)` читает GLB после preflight. URI-based overload не используется. Поступивший ResourceSource открывает только основной resource-pack файл; stream закрывается на успехе и отказе. Внешние, локальные и data URI отвергаются до вызова JglTF. GLB header/version/length/chunks, UTF-8, JSON и все accessor/view окна проверяются заранее.

Поддерживается:

- выбранная scene, meshes/primitives и индексы UBYTE/USHORT/UINT; unindexed TRIANGLES;
- FLOAT POSITION/NORMAL/TEXCOORD_0, interleaved accessors;
- affine node matrix либо TRS, полная статическая hierarchy;
- нормали через inverse-transpose с нормализацией, исправление winding при отрицательном determinant;
- embedded PNG/JPEG, baseColorFactor/baseColorTexture, UV0, alpha OPAQUE/MASK/BLEND, double-sided;
- чтение sampler wrap/filter и metallic/roughness scalar metadata;
- bounds и неизменяемые CPU-структуры с defensive copies.

Минимальный shader делает base-color + normals/diffuse + Minecraft lightmap с явной sRGB/linear конверсией. **Физический PBR не реализован:** metallic/roughness численно сохранены, но не влияют на shading. Normal/emissive/occlusion и metallic-roughness maps отвергаются; их эффект не подменяется молчаливым игнорированием.

Отсутствующий material использует стандартный белый fallback; отсутствующий UV допустим только для нетекстурированного primitive. Отсутствующий NORMAL/POSITION, ошибочный index, NaN/Infinity, несогласованные counts, повреждённое изображение, singular transform, cycle/invalid parent и неподдержанный профиль дают IOException с ID ресурса и диагностикой. Нет возврата к Java-кубам.

Лимиты: GLB 64 MiB, JSON 1 MiB и глубина 64, nodes 256, hierarchy depth 64, 500000 expanded vertices, 1500000 indices. Изображения не более 4096×4096 и 16 MiB compressed каждое; суммарно не более 16M decoded pixels и 16M pixels в upload descriptors. Это ограничивает также дублирование одного изображения разными samplers. Есть дополнительные лимиты на количество accessors, views, meshes, materials/images/textures.

Сейчас намеренно не поддерживаются внешний `.gltf`, URI references, sparse accessors, normalized integer UV, дополнительные attributes/UV sets/vertex colors, extensions, Draco/Meshopt, skins, morphs и animations. Они отвергаются явно. Это ограниченный статический GLB профиль, не полная реализация всей спецификации.

## 5. Backend и изолированный просмотр

`FabricGlbRenderer` использует `VertexFormat.DrawMode.TRIANGLES` и `POSITION_COLOR_TEXTURE_LIGHT_NORMAL`. В buffer поступают вершины по индексам загруженного GLB; production Java не содержит геометрию образца. Собственные core shader `.vsh/.fsh/.json` регистрируются стандартным `CoreShaderRegistrationCallback`.

Placement применяется один раз: явно переданная `projection * world-event matrices * camera-relative root translation`. Renderer не добавляет глобальный RenderSystem model-view второй раз. В debug view root только переносится, baked normals остаются в мировых осях, lighting стабилен относительно мира. Произвольные root rotations/views 1P/3P/NPC не являются проверенным контрактом этого диагностического backend.

В коде включены depth LEQUAL, depth writes для opaque/mask, alpha cutoff, backface policy и отдельный проход blend без depth writes. Blend primitives сортируются по центру, не отдельные треугольники. Состояния blend/depth/cull/shader/texture units восстанавливаются в finally. Пересекающаяся прозрачная геометрия, world transparency, fog, fabulous/shader mods и итоговая цветопередача в Minecraft остаются непроверенными; это не полный PBR/прозрачный pipeline.

Тестовый режим **выключен по умолчанию**. Новый client entrypoint возвращается без регистрации callbacks, если нет `-Dbobin.armament.glbDebug=true`. Предметы/блоки/entities не регистрируются; сервер и существующие renderer не заменяются.

После восстановления доступа и успешной сборки запускать на Java 17:

```sh
bash gradlew runGlbDebugClient
# Windows: gradlew.bat runGlbDebugClient
```

Затем открыть новый обычный тестовый мир и выполнить локальные client-команды:

```text
/bobin_glb show
/bobin_glb status
/bobin_glb reload
/bobin_glb hide
```

`show` ставит виртуальный образец в 3 блоках перед игроком, на уровне его ног. Мир не меняется. Проверить соответствие метровой сетке блоков, обход со всех сторон, указатель -Z, текстуру, геометрические нормали и закрытие образца непрозрачным блоком. `/reload` вызывает resource reload, как F3+T, не команду серверного datapack reload.

Показ прекращается при disconnect. Resource listener зависит от vanilla textures и меняет поколение только после успешного чтения/upload. При ошибке сохраняется последняя корректная версия и видимая диагностика. Factory закрывает частично созданные textures; замена закрывает старые, остановка клиента закрывает текущие NativeImage и GL names. Shader lifetime принадлежит GameRenderer, собственных persistent VBO не создаётся; BufferBuilder/Tessellator управляются Minecraft.

Для проверки GPU lifecycle повторить F3+T или `/bobin_glb reload` 20–50 раз: при этом образце `assets=1` и наш owned-texture counter должен оставаться **2** (PNG и белый fallback), после client stopping — 0. Это инструкция для будущей проверки, **она ещё не выполнена в Minecraft**. Counter учитывает наши owned handles, а не измеряет VRAM драйвера.

## 6. Фактические проверки и внешняя блокировка

| Проверка | Результат |
|---|---|
| Blender scene/export/CPU reference render | PASS, Blender 4.3.2 |
| Khronos glTF Validator | PASS: 0 errors, 0 warnings, 3 info |
| Java 17 CPU target: настоящий JglTF/Jackson loader + API | PASS, compileJava/compileTestJava/test |
| JUnit | **191 executed, 191 passed, 0 skipped/errors/failures** |
| Старый отдельный API-only target | PASS, 164 tests |
| JSON schemas предыдущего этапа | PASS, 6 schemas / 83 cases |
| Вершины, hierarchy, normals, reflection, image/materials | PASS, сравнение с реальным Blender export |
| Повреждённые headers/chunks/accessors/indices/images/transforms | PASS, контролируемые отказы |
| CPU ownership cache | PASS, 200 замен с закрытием предыдущего handle, отказ фабрики сохраняет last-good; streams закрываются |
| Полная сборка мода | BLOCKED до compileJava |
| Полные Gradle tests мода | BLOCKED до выполнения тестов |
| Запуск Minecraft | BLOCKED до запуска JVM клиента |
| Компиляция клиентского backend / GLSL в Minecraft | НЕ ПОДТВЕРЖДЕНА |
| Реальное изображение, depth/UV/lighting и GPU leaks | НЕ ПРОВЕРЕНЫ |

CPU target исключает Minecraft-specific файлы `client/render/fabric`, `client/resource/fabric`, `client/debug`, `ArmamentGlbClient`. Это сделано явно, его успех не выдаётся за сборку клиента. Все прежние API tests входят в число 191, отдельные 164 не следует суммировать с ним.

Выполнены все три попытки: `bash gradlew build -x test`, `bash gradlew test`, `bash gradlew runGlbDebugClient`, на Java 17. Каждая завершилась exit 1 при конфигурации проекта:

```text
HTTP 403 Forbidden
https://maven.fabricmc.net/fabric-loom/fabric-loom.gradle.plugin/1.7.4/fabric-loom.gradle.plugin-1.7.4.pom
```

Независимый запрос через штатный proxy подтверждает `Tunnel connection failed: 403 Forbidden`. Домен отсутствует в enforced restricted policy среды. TLS, checksum wrapper, исходные delete rules и версии Minecraft/Fabric не менялись для обхода ошибки.

Для продолжения полной проверки нужен разрешённый доступ к `maven.fabricmc.net` в настройках сети среды, затем повтор build/test/client. Дальнейшее скачивание Minecraft ещё не проверено. Обхода proxy или подмены Fabric Maven зеркалом нет.

Команды CPU-проверок (используется Gradle 8.8 из уже установленного wrapper distribution):

```sh
JAVA_HOME=/path/to/jdk17 gradle -p tools/armament-glb-check test dependencyReport
ARMAMENT_JAVA_HOME=/path/to/jdk17 ARMAMENT_TEST_LIB_DIR=/path/to/verified-test-jars bash scripts/test_armament_api.sh
python3 scripts/verify_armament_schemas.py
```

Если Gradle executable не установлен глобально, сначала получить wrapper distribution или указать его `bin/gradle`. В корневой проект за CPU target заходить не нужно, поэтому недоступный Fabric Loom ему не требуется. Это самостоятельный ограниченный тестовый target.

[Machine-readable итоги](task3/validation/results.json), [CPU Gradle log](task3/validation/cpu-tests.log), [GLB Validator](task3/validation/gltf-validator-report.json), [Blender log](task3/validation/blender-export.log), [full build](task3/validation/full-build.log), [full tests](task3/validation/full-test.log), [client attempt](task3/validation/client-launch.log).

## 7. Перечень и границы изменений

Из существующего состояния задачи №2 изменены только три файла:

1. `build.gradle`: подключены необходимые Java reader libraries и их jar-in-jar упаковка, добавлена отдельная opt-in Loom client run configuration. Delete/exclude блок побайтно сохранён.
2. `fabric.mod.json`: добавлен отдельный client entrypoint. Исходные main/client/GameTest entries сохранены.
3. `scripts/test_armament_api.sh`: прежний dependency-free API check явно исключает новые JglTF/Minecraft implementation files и новые GLB tests; они проверяются отдельным CPU target. Пустой test run по-прежнему считается ошибкой.

Остальные существующие файлы этапа №2 сверены по SHA-256 и не изменены; удаления отсутствуют. Классы NPC, дронов, гранат, GROM-7, RPG, техники, разрушений, генерации мира и старого оружия не изменены и не используются как основа. Существующие игровые функции не проверены в запущенном клиенте, поэтому игровая регрессия не заявляется.

Новые файлы перечислены в [TASK3_CHANGES.txt](TASK3_CHANGES.txt): loader/math/cache, client adapter/backend/debug, shaders, GLB, authoring assets/exporter, dependency pins, notices, tests, CPU target и документация/доказательства.

Оружие, стрельба, перезарядка, звуки, skinning и анимации не созданы. Цель «доказать работу реального полигонального рендера в Minecraft» остаётся неподтверждённой до реального запуска. На этом этапе работа остановлена; следующий этап без подтверждения не начинается.
