# Этап 5.1 — First-Person Weapon Presentation Foundation

Bobin Craft 2.7.7-clean, Fabric 1.20.1, Java 17. 29 октября 2026.
База: Git `073f93dbd7505eeb14ff1419dedf5acc8ccca242`, этапы 2–4.

**Критерий готовности в Minecraft пока не выполнен/не подтверждён.** Подготовлены
исходники, настоящий Blender/GLB-риг и проверенная CPU-часть представления.
Полная сборка и запуск клиента попытаны, но остановились до `compileJava` и
client JVM на HTTP 403 от Fabric Maven. Новые Minecraft mixins, GLSL, изображение
в игре, глубина, реальные FOV/resize/F3+T и игровой FPS ещё не проверены.
Автономные Java-тесты не являются успешным запуском Minecraft.

## Точки интеграции и конфликты

Изучены существующие first-person handlers, камера дронов/техники, injury state,
bootstrap callbacks и исходники armament renderer/runtime. Для Yarn 1.20.1 сверены
официальные mappings:

- `HeldItemRenderer.renderItem(float, MatrixStack, VertexConsumerProvider.Immediate,
  ClientPlayerEntity, int)` — общий проход рук, `method_22976`.
- `renderFirstPersonItem` — другой, поэлементный метод, `method_3228`.
- `GameRenderer.getFov(Camera,float,boolean)` — `method_3196`.
- `GameRenderer.getBasicProjectionMatrix(double)` — `method_22973`.
- `HeldItemRenderer.mainHand/offHand` — кешированные стеки, отдельные от inventory.

Mappings: [HeldItemRenderer](https://github.com/FabricMC/yarn/blob/1.20.1/mappings/net/minecraft/client/render/item/HeldItemRenderer.mapping),
[GameRenderer](https://github.com/FabricMC/yarn/blob/1.20.1/mappings/net/minecraft/client/render/GameRenderer.mapping).
Это статическая проверка сигнатур, не доказательство применения mixins в игре.

| Существующая система | Решение |
|---|---|
| InjuredFirstPersonMixin отменяет отдельную повреждённую руку | Не изменён. При травмах/ампутациях диагностический presenter отключается |
| GrenadeFirstPersonMixin рисует гранату/GROM-7 и свои руки | Не изменён. Проверяются реальные и кешированные стеки, GrenadeClient.ownsHands и GROM-7 animation ownership |
| GameRendererMixin скрывает руки в камерах дронов/техники | Не изменён. Дополнительный read-only gate проверяет эти режимы и возврат от дрона |
| CameraMixin и concussion/camera effects | Не изменены. Не вызываются camera setters и не заменяется мировая projection matrix |
| Player/NPC models, повреждения, ползание, HUD | Не изменены; NPC/third-person adapters не подключались |

Новый client-only `FirstPersonHeldItemMixin` работает в HEAD общего `renderItem`.
Он отменяет **один диагностический проход только после успешного GLB draw**,
когда обе руки пусты, GLB debug включён и игрок совместим. При любом отказе
продолжает работать vanilla/существующий renderer. Overwrite и global renderer
replacement нет. Второй mixin — только read-only invoker фактического FOV.
Они зарегистрированы отдельным config с `environment: client`; старый mixin JSON
остался побайтно неизменным.

## Компоненты

- [FirstPersonWeaponPresenter](../../src/main/java/com/bobincraft/armament/client/presentation/FirstPersonWeaponPresenter.java)
  — отдельная pose-instance, клипы, additive movement, camera-relative matrices,
  contact checks, camera clearance и bounded inertia.
- `FirstPersonProfile` — строгий JSON codec и semantic checks; неизвестные поля,
  coercions, невалидные ID/quaternions/диапазоны отвергаются.
- `GripAttachments`, `PresentationMath.attach` и существующий `RigSockets` —
  declarative grips, проверка положения/ориентации и generic socket alignment.
- `DampedViewInertia` — критически затухающий spring, обновляемый simulation ticks.
- `FirstPersonEligibility` — чистая read-only политика допуска.
- `FirstPersonAsset` — атомарная загрузка model/rig/sockets/profile до GPU allocation.
- `presentation/fabric/FirstPersonResources` и `FirstPersonDiagnostic` — только
  Minecraft resource lifecycle, команды, наблюдение состояния и вызов renderer.

CPU-часть не имеет Minecraft/Fabric/LWJGL imports. Common/server API этапов №2–4
не изменялся. Snapshot/weapon state, inventory, packets, input actions и серверные
registry не используются для регистрации нового оружия или механик.

## Blender и хват

- [Исходник .blend](../../art/armament/first_person_fixture.blend).
- [Экспортированный GLB](../../src/main/resources/assets/bobin_craft/armament/debug/first_person_fixture.glb).
- [Скрипт воспроизведения](../../scripts/armament/export_first_person_fixture.py).
- [Независимые позы и проверки пересечений Blender](../../art/armament/first_person_fixture.reference.json).
- [Предпросмотр Blender](../../art/armament/first_person_fixture.blender-preview.png) — **не Minecraft screenshot**.

Скрипт импортирует существующий `technical_fixture.glb`, не переписывает его
полигональную геометрию в Java и не меняет исходный GLB. Эквивалентность всех его
вершин в новой assembly отдельно проверяется Java-тестом. Размер исходного объекта
остаётся 1×1×1 метр; 1 Blender unit = 1 metre = 1 Minecraft block. `/16` и
масштабирования под размер окна нет.

Добавлен отдельный `view_hands_rig`: две skinned arm surfaces, плечо/локоть/кисть
и три сустава каждого из пяти пальцев каждой руки. Thumb имеет отдельную
противопоставленную цепь. Итого **36 joints**, один shared skin, 51 node, 7 meshes,
1542 exported vertices, 2492 triangles. Поверхность рук техническая, без финальных
player-skin/armor textures; исходный checker/materials объекта сохранён.

Обе кисти держат переднюю грань платформы снизу. `grip_primary/secondary` принадлежат
объекту, `hand_primary/secondary` — кистям; их ориентация задаётся Blender.
Все части имеют общий `presentation_root`. Кисти удерживают заданные контакты,
а локти получают художественный swivel вокруг оси плечо–кисть. Это запечённые
Blender-позы, не Java-движения и не покадровая ручная подгонка кистей.

Пять клипов: `idle` (2s), `inspect` (3s), `raise/lower` (1.25s), `move` (1s).
Все исходные ключевые позы хранятся в .blend. Dense export baking **96 Hz** выбран,
чтобы сравнение дробных времён с Blender сохраняло допуск 8e-5 m; при более редком
bake нелинейные authoring curves давали больший interpolation error. Runtime
использует timestamps GLB, а не FPS игры.

В Java проверяются оба grip contact в одной pose. Common root, JSON offset и
процедурный лаг применяются ко всей сборке одинаково. При несовместимом хвате
presenter отказывается рисовать; пальцы/кисти не сдвигаются магическими offsets.
Новые профили могут выбирать другие **подготовленные в Blender** socket pairs и
clips. Arbitrary-target IK/retargeting и автоматическая адаптация к любому предмету
не реализованы и к этапу 5.2 перехода нет.

## Камера, FOV, глубина и время

Presenter получает существующий MatrixStack прохода рук. Проекция рассчитывается
через фактический интерполированный world FOV Minecraft и его
`getBasicProjectionMatrix`; разрешение/aspect учитываются движком при каждом draw.
В shader передаётся собственная `projection * handView * metricOffset * inertia`,
без изменения глобальной камеры, HUD или RenderSystem projection.

`GlbRenderContext` получил optional normal transform, старый четырёхаргументный
конструктор сохранён с identity. Shader получает inverse-transpose model/camera
normal matrix, поэтому normals следуют за orientation. Старые world previews
сохраняют identity поведение. Depth LEQUAL, depth writes, culling/material policy
и восстановление render state остаются в существующем GLB backend.

Состояние инерции обновляется на 20 Hz simulation ticks и интерполируется при
draw; используется аналитический critically damped spring. Источник — реальные
yaw/pitch deltas, с обработкой wrap ±180°, а не периодическая Java-анимация.
По умолчанию пределы: yaw 1.5°, pitch 1°, translation 1.5 mm/degree. Слой
отключается JSON или `/bobin_glb fp inertia off`; Blender clips продолжаются.

`move` запускается additive layer существующего AnimationController при движении.
Явный выбор `anim move` не удваивает этот слой. `inspect` и `raise` возвращаются к
idle, lower удерживает последнюю позу; transitions/crossfades обрабатывает runtime
этапа №4. Sampling uses ticks+tickDelta, без accumulation frame dt.

Проверяется глубина каждой вершины после camera-relative transform. Если она
нарушает заданный camera clearance/near plane, отключается только текущий
диагностический draw. Модель и руки не проталкиваются через камеру скрытой
коррекцией. При узком FOV или узком/портретном окне края физически большого объекта
могут выходить за frustum; автоматического изменения масштаба нет. Framing можно
настроить метрическими offsets. Столкновения предмета со стенами мира не добавлены.

## Конфигурация и запуск

[Профиль](../../src/main/resources/assets/bobin_craft/armament/debug/first_person_fixture.profile.json)
содержит model/rig/sockets IDs, camera_offset в метрах, camera_rotation XYZW,
clearance, transitions, движение, две grip-связи и bounded inertia. Поля `scale`
нет; оно отвергается. [JSON Schema](../../schemas/armament/presentation/first-person.schema.json)
разделяет structural checks и runtime semantic/model checks.

При доступных Fabric/Minecraft dependencies, Java 17 и графической среде:

```sh
./gradlew runGlbDebugClient
# Windows: gradlew.bat runGlbDebugClient
```

Этот существующий run config устанавливает `-Dbobin.armament.glbDebug=true`.
Без флага callback не регистрируется, а render hook возвращает управление vanilla.
Войти в мир здоровым игроком, в первое лицо, с пустыми main/offhand, без mount и
активной grenade/GROM/drone/tank presentation. Команды:

```text
/bobin_glb fp show
/bobin_glb fp anim idle
/bobin_glb fp anim inspect
/bobin_glb fp anim raise
/bobin_glb fp anim lower
/bobin_glb fp anim move
/bobin_glb fp status
/bobin_glb fp inertia off
/bobin_glb fp inertia on
/bobin_glb fp hide
```

`status` сообщает policy, render reason, action, FOV, viewport, contacts, minDepth,
resource generation и texture ownership count. show не меняет inventory. При
предмете в любой руке, кешированном equip-переходе, травме, ампутации, ползании,
смерти, spectator, swinging/using, скрытом HUD или чужой камере работает прежний
renderer. После возврата к совместимому состоянию начинается новая raise.

Старые `/bobin_glb show|hide|status|reload` и `anim ...` сохранены. F3+T или
`/bobin_glb reload` атомарно обновляют first-person bundle; новая generation создаёт
новый presenter. Ошибка загрузки сохраняет последний хороший bundle.

После доступного запуска вручную проверить обе кисти при поворотах, ходьбе,
прыжках, быстрых inspect/lower/raise, FOV 30/70/110, смене окна, F5, предметах в
обеих руках и legacy grenade follow-through; затем F3+T несколько раз. Сравнить
texture ownership count после каждой reload. При всех debug assets он ожидается
стабильным (6 textures), но счётчик **не доказывает отсутствие VRAM leak**.

## Фактические результаты

| Проверка | Результат |
|---|---|
| CPU compilation Java 17 + JUnit всех этапов | **436 passed**, 14 suites, 0 failures/errors/skips |
| Новые Blender golden poses | **45**; все вершины и четыре socket matrices, допуск 8e-5 |
| Blender BVH surface intersections рук с объектом | **0 на 45 позах** |
| Java transitions/additive/camera samples | **480**, оба grip contact и camera guard проходят |
| Plate penetration при crossfades | На 120 simulation snapshots ни одна проверенная hand vertex не внутри платформы глубже 0.2 mm; это vertex check, отдельно от Blender BVH |
| Projection / FOV / aspect / yaw wrap / eligibility | PASS на CPU; не проверка Minecraft framebuffer |
| Steady-state presenter + skinning | Два последовательных окна по 300 samples: **0 heap bytes**, около 0.092 ms/sample на этой CPU/JDK; без GL draw |
| Isolated common/API compilation | **164 passed**, без Minecraft/client classpath |
| Schemas | **83** прежних + **30** новых cases; rig/sockets sidecars проходят |
| Java 17 syntax/local-reference audit | 388 sources, PASS; Minecraft type checking не выполнялся |
| Khronos glTF Validator | **0 errors, 2 warnings, 7 info** |
| Полная Gradle build / client JVM | BLOCKED до compileJava; Minecraft не запущен |

Warnings `NODE_SKINNED_MESH_NON_ROOT` относятся к двум skinned mesh под родителем:
glTF skinning использует jointWorld/IBM, а не обычное rigid наследование mesh node.
Runtime это учитывает; позы сверены численно. Info — unused UV и пустые socket nodes.
GLB validator не проверяет работу Minecraft shaders или mixins.

Полная сборка и реальный launcher фактически вызваны:

```sh
./gradlew build --no-daemon --console=plain --info
./gradlew runGlbDebugClient --no-daemon --console=plain --info
```

Оба exit 1 при разрешении Fabric Loom 1.7.4:

```text
HTTP 403 Forbidden:
https://maven.fabricmc.net/fabric-loom/fabric-loom.gradle.plugin/1.7.4/fabric-loom.gradle.plugin-1.7.4.pom
```

`maven.fabricmc.net` отсутствует в разрешённых доменах текущей enforced network
policy. Использованы штатный proxy и Java truststore, TLS/ограничения не обходились.
После открытия Fabric Maven последующие downloads/display requirements и сама
компиляция Minecraft backend ещё требуют проверки.

CPU-проверки: `./gradlew -p tools/armament-glb-check test dependencyReport --no-daemon`.
Фактический запуск использовал `--offline` с проверенными cached jars.
Схемы: `python3 scripts/verify_armament_schemas.py` и
`python3 scripts/verify_first_person_schema.py` (development Python jsonschema,
не runtime/mod dependency). Экспорт: `blender -b --python
scripts/armament/export_first_person_fixture.py`.

[Машинные результаты, JUnit XML и логи](task5_1/validation/results.json),
[список файлов](TASK5_1_CHANGES.txt).

## Границы изменений

Изменены девять существующих файлов только для bootstrap, scoped client mixins,
normal transform, проверки CPU и GPU texture IDs. Global allocation token в
`GlbGpuAsset` нужен, чтобы независимые reload listeners с одинаковым generation
не перезаписывали textures друг друга. Новые runtime dependencies не добавлены.

**2520 остальных файлов базы сохранены побайтно**, включая `build.gradle` с его
опасными delete/exclude правилами, старые mixins, cameras, HUD, NPC AI, дроны,
гранаты, технику, баллистику, разрушения и worldgen. Функциональная регрессия этих
систем в игре не проверялась. Новых items, оружия, АК-47, стрельбы, перезарядки,
звуков и gameplay mechanics нет.

Изменения доступны в рабочем проекте и полном исходном архиве. Игровой критерий
остаётся открытым; к этапу 5.2 или созданию оружия перехода нет.
