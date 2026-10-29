# Задача №4 — GLB Animation Runtime

Bobin Craft 2.7.7-clean / Fabric 1.20.1 / Java 17. 29 октября 2026.

**Результат:** импорт рига, sampling, blending, CPU skinning и sockets фактически
скомпилированы и проверены на Java 17. Численные результаты совпадают с
независимыми позами Blender. Интеграция с Minecraft renderer написана, но полная
компиляция мода и работа в игре **не подтверждены**: Fabric Maven отвечает HTTP 403,
Minecraft не запущен. Наличие исходников backend не является игровым тестом.

## Импорт и данные

`GlbPreflight` теперь разрешает ограниченные skins/animations и MAT4 accessors,
сохраняя проверку контейнера, JSON, byte windows, иерархии и запрет внешних URI.
`GlbRigLoader` преобразует реальные JglTF-данные в собственный immutable `RigModel`:
nodes, локальные TRS/матрицы, parent indices, meshes, skin palettes и clips.
JglTF-объекты после импорта не удерживаются. Новых библиотек нет.

Поддерживаются translation/rotation/scale, timestamps и режимы STEP, LINEAR,
CUBICSPLINE. LINEAR rotation использует shortest-path quaternion SLERP. Cubic —
Hermite с умножением tangents на интервал между ключами и нормализацией результата
rotation; знаки экспортированных cubic tangents не меняются. Есть проверка
возрастающих времён, unit quaternion keys, output counts, повторных targets,
недопустимых matrix targets и конечности чисел.

Skin читает joints и FLOAT MAT4 inverseBindMatrices; при отсутствии IBM используется
identity согласно glTF. JOINTS_0 принимает unsigned BYTE/SHORT, WEIGHTS_0 — FLOAT
или normalized unsigned BYTE/SHORT. Проверяются counts, индексы, веса и skeleton
ancestor. Небольшая ошибка integer quantization нормализуется; нулевые,
отрицательные и повреждённые веса отвергаются.

`RigFrame` рассчитывает local/global transforms в порядке родителей, а также
палитру `inverse(meshWorld) * jointWorld * inverseBindMatrix`. Позиции выводятся
через эквивалентную мировую палитру `jointWorld * inverseBindMatrix`, без повторного
применения mesh transform. Это учитывает перебазирование skinned vertices
экспортёром Blender. Нормали используют inverse-transpose blended affine matrix
с нормализацией. Rigid meshes следуют своему node целиком.

`RigSockets` принимает существующие определения этапа №2. Вариант с
`WeaponRigDefinition` разрешает canonical aliases через `node_index`, независимо
от отображаемого имени Blender; проверяет rig_id, индексы и родителей. Socket
возвращает `nodeWorld * localOffset` в предоставленный вызывающим кодом буфер.

## Контроллер

`GltfAnimationController` реализует существующий `AnimationController<RigFrame>`:

- Idle / Playing / Transition, запуск, stop, LOOP / ONCE / HOLD;
- до 8 независимых ordered layers, до 8 weighted clips в каждом;
- node/bone masks, override и bind-relative additive TRS;
- crossfade с продолжающимися outgoing/incoming clips; прерванный переход
  сохраняет текущую смешанную позу, ограничивая историю и сохраняя C0 continuity;
- bind pose как начало каждой выборки, без накопления transforms;
- отдельные pose, playheads, transitions и marker cursors у каждого экземпляра.

Время — simulation seconds: `(tick + tickDelta) / 20`. Нет накопления dt от FPS.
Sampling одной и той же точки повторно использует готовый frame. ONCE после конца
плавно отпускает слой к нижележащей позе за fade duration, минимум 0.05s; HOLD
сохраняет последнюю позу, LOOP оборачивает время. Additive reference — bind pose;
отдельные reference clips, IK и retargeting не реализованы.

Обычный API вызова, без подключения к оружию или NPC:

```java
var controller = new GltfAnimationController(asset.rig());
controller.markers(eventsDefinition); // До запуска клипов; проверяет длительности.
controller.play("combo", GltfAnimationController.Mode.LOOP, simulationSeconds, 0.25);
RigFrame frame = controller.sample(simulationTick, tickDelta);
```

Frame — read-only borrowed view до следующего sample того же controller.
Буферы принадлежат экземпляру и переиспользуются; сохранять frame как независимый
снимок нельзя. Комментарий общего интерфейса уточнён; сигнатуры его методов не
изменены. Контроллер рассчитан на один simulation/render thread.

## Cosmetic markers

Используется существующий `AnimationEventsDefinition` с `kind: cosmetic`.
Семантический event ID позволяет будущим потребителям различать звук, contact,
движение детали, VFX и presentation state. Реальных звуков и gameplay callbacks нет.

`advance(simulationTick, consumer)` вызывается на simulation ticks отдельно от draw.
Событие содержит UUID экземпляра, playback serial, layer, clip, marker и loop.
Повторный тик/render pass не повторяет события. Seek не воспроизводит пропущенные
маркеры; high-water cursor не понижается при перемотке назад. При переходе события
outgoing playback прекращаются; входящий имеет новый serial. Интервал потребляется
до callback, поэтому исключение consumer не вызывает повторного воспроизведения.

Catch-up ограничен 32 последними циклами и 256 событиями на advance. Более старые
cosmetic события отбрасываются, а не выдаются неограниченной очередью после паузы.
Это осознанная политика представления; серверных действий здесь нет.

## Настоящий Blender-образец

- [Исходник .blend](../../art/armament/animation_fixture.blend).
- [GLB](../../src/main/resources/assets/bobin_craft/armament/debug/animation_fixture.glb).
- [Независимые эталоны Blender](../../art/armament/animation_fixture.reference.json).
- [Скрипт воспроизведения экспорта](../../scripts/armament/export_animation_fixture.py).

Blender 4.3.2: fixed base, rotor с указателем, slider, skinned column, 3 связанные
кости и socket_debug. Всего 11 nodes, 4 meshes, 214 vertices, 132 triangles.
Пять клипов по 2 секунды: `spin`, `slide`, `curve`, `bend`, `combo`.
Реальный экспорт содержит LINEAR, STEP и CUBICSPLINE. UV и packed checker texture
сохранены. Единицы: 1 metre = 1 block; Blender exporter выполняет `(x,y,z) → (x,z,-y)`
один раз, runtime не применяет второй flip или произвольный множитель.

Экспорт: `blender -b --python scripts/armament/export_animation_fixture.py`.
Actions объединяются по именам NLA tracks в режиме ACTIONS; .blend сохранён с
активным combo track на среднем кадре. Экспортёр сам формирует GLB; JSON/бинарные
анимации в поставляемом GLB вручную не редактировались.

Эталоны вычисляет Blender из evaluated objects/Armature modifier, а не GLB reader:
10 времён каждого из 5 клипов, включая дробный кадр. Java сравнивает все позиции
экспортированных вершин, world deformation matrices, rigid nodes и socket с допуском
6e-5 (позиции — в метрах). Нормали дополнительно проверены аналитическим non-uniform scale тестом
и на единичную длину; совпадение normals с Blender по каждому полигону не заявляется.

Три sidecars `animation_fixture.rig.json`, `.sockets.json`, `.events.json` соответствуют
схемам этапа №2. Роль `weapon_root` в rig — обязательное имя существующей схемы,
не создание оружия или предмета.

Khronos glTF Validator: **0 errors, 1 warning, 3 info**. Warning
`NODE_SKINNED_MESH_NON_ROOT` сообщает, что skinned mesh под родителем не наследует
его transform обычным rigid способом; runtime учитывает это через joint palette,
что подтверждается эталонами. Info относятся к UV без texture и пустому socket node.

## Minecraft и проверка вручную

`FabricGlbRenderer` принимает optional `RigFrame`; static null-pose путь сохранён.
Новые вершины поступают в тот же TRIANGLES BufferBuilder, с прежними materials,
UV, lightmap, shaders и восстановлением RenderSystem state. Преобразования renderer
не вычисляют движения сами и не вызывают animation events. Debug resource cache
владеет static и animated asset отдельно; неудачная factory сохраняет последний
успешный asset, успешная замена закрывает старые GPU textures.

Диагностика по-прежнему выключена без `-Dbobin.armament.glbDebug=true`.
При доступных Fabric/Minecraft dependencies и графической среде:

```sh
./gradlew runGlbDebugClient
# Windows: gradlew.bat runGlbDebugClient
```

Войти в мир и выполнить:

1. `/bobin_glb anim list` — пять импортированных клипов и длительности.
2. `/bobin_glb anim play combo` — две модели перед игроком. Вторая имеет независимую
   фазу +0.5s, shared asset/textures и собственные pose/cursors.
3. `/bobin_glb anim play spin`, затем `bend`, `slide`, `curve` — проверить движение,
   deformation, STEP, плавную кривую и переходы 0.25s. Быстро сменить клип во время
   перехода и проверить отсутствие скачка позы.
4. `/bobin_glb anim status` — state, счётчик/идентичность cosmetic markers, socket
   position, shader и ресурсы. Звуки и серверные действия не исполняются.
5. `/bobin_glb anim stop` — возврат обоих экземпляров к bind pose за 0.25s.
6. `/bobin_glb show|hide|status|reload` — прежние static-команды сохранены. После
   успешного reload новая generation сбрасывает preview controller к bind pose;
   после неудачного reload остаётся последний хороший asset.

При reload вручную проверить возврат owner-счётчика liveTextures к одному значению
после каждой перезагрузки. Сам счётчик не доказывает отсутствие утечки VRAM;
графический profiler и реальный запуск ещё нужны. Preview не регистрирует entities,
items, блоки или server packets и не вмешивается в player/NPC rendering.

## Фактические проверки и ограничения

- **311 JUnit tests**, 11 suites, 0 failures/errors/skips на Java 17. Включены все
  191 проверки предыдущего этапа и 50 Blender golden poses.
- Отдельная API/common компиляция без Minecraft/client classpath: **164 passed**.
- 6 JSON Schema / **83 cases**, все три экспортированных sidecars проходят схемы.
- Java 17 syntax audit всех 374 Java sources и проверка локальных class references:
  PASS. Это не проверка сигнатур Minecraft API.
- Замер на этой CPU/Temurin: после прогрева 2000 pose/skinning samples дали
  **0 heap bytes**; фактическое время записано в JUnit XML/results.json. Это не FPS
  Minecraft. У renderer остаются ранее существовавшие draw-state/list allocations;
  zero-allocation относится к animation sampling/skinning.
- Проверены damaged timestamps/rotations/IBM, skeleton ancestry, joints/weights,
  integer weights, masks, quaternion/scale additive, interruptions, seek, markers,
  independent instances, repeated reload/import и cache ownership. GPU leak/reload
  в игре не проверен.

CPU limits: 256 nodes, hierarchy depth 64, 32 skins, 128 joints/skin, 128 clips,
100k keys/channel, 2M expanded animation scalars, 200k animated vertices и 50k
skinned vertices, 1.5M indices. Буферы выделяются при создании asset/instance/layer,
а не при каждом sample. Sparse/morph/extra influence sets/extensions/external URI
не поддержаны и отклоняются. Вырожденные матрицы/нулевой scale, для которых нет
определённого inverse-transpose normal transform, дают явную ошибку evaluation;
диагностика отключает проблемный preview.

Полная сборка `./gradlew build --no-daemon --console=plain --info` и настоящий
запуск через `./gradlew runGlbDebugClient --no-daemon --console=plain --info`
фактически попытаны. Оба exit 1 **до compileJava/client JVM**:

```text
HTTP 403 Forbidden:
https://maven.fabricmc.net/fabric-loom/fabric-loom.gradle.plugin/1.7.4/fabric-loom.gradle.plugin-1.7.4.pom
```

Домен отсутствует в разрешённых адресах среды. TLS/proxy не обходились. Поэтому
полная компиляция Fabric backend, упаковка mod JAR, отображение CPU-skinned meshes, UV/depth
в Minecraft, VRAM leaks и игровая регрессия не подтверждены. После открытия Fabric
Maven следующие загрузки/требования среды также могут потребовать проверки.

Для независимых CPU-тестов: `./gradlew -p tools/armament-glb-check clean test
dependencyReport --no-daemon`. В выполненной среде использован cached Gradle/JARs
и `--offline`. Это отдельный target, не замена сборке мода.

[Машинные результаты и проверки](task4/validation/results.json),
[полный список добавленных/изменённых файлов](TASK4_CHANGES.txt).
В каталоге validation сохранены фактические JUnit XML, логи сборки, запуска,
экспорта и glTF Validator.

`build.gradle`, его delete/exclude правила, зависимости и все действующие системы
Bobin вне armament остались побайтно неизменными. Оружие, стрельба, звуки,
weapon animations, NPC/player adapters, баллистика, AI и механики не добавлены.
Работа останавливается на animation foundation.
