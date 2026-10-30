# First-person animation: чистый Fabric 1.20.1

База Bobin: `69a008e`. Реализация использует существующие GLB, 36-костный риг,
профили и backend Bobin. Готовые классы находятся в исходниках проекта;
заглушек, Forge EventBus, Forge capabilities или Forge item extensions нет.

## Готовые классы

| Файл | Назначение |
|---|---|
| [FirstPersonActionStateMachine.java](../../src/main/java/com/bobincraft/armament/client/presentation/FirstPersonActionStateMachine.java) | Независимая от платформы политика действий и завершения клипов. |
| [FirstPersonWeaponPresenter.java](../../src/main/java/com/bobincraft/armament/client/presentation/FirstPersonWeaponPresenter.java) | Связывает стейт-машину, GLB-позы, инерцию, контакты и матрицы. |
| [GltfAnimationController.java](../../src/main/java/com/bobincraft/armament/client/animation/gltf/GltfAnimationController.java) | Смешивание слоёв, прерывание crossfade, quaternion SLERP, новый optional smoothstep-вес. |
| [FirstPersonHeldItemMixin.java](../../src/main/java/com/bobincraft/armament/client/mixin/FirstPersonHeldItemMixin.java) | SpongePowered Mixin в общий first-person проход HeldItemRenderer. |
| [GameRendererFovAccess.java](../../src/main/java/com/bobincraft/armament/client/mixin/GameRendererFovAccess.java) | Invoker ванильного FOV GameRenderer. |
| [FirstPersonGlbRenderer.java](../../src/main/java/com/bobincraft/armament/client/presentation/fabric/FirstPersonGlbRenderer.java) | Полный render-адаптер: hand projection, матрицы, skin binding, flush и вызов существующего GLB backend. |
| [FirstPersonDiagnostic.java](../../src/main/java/com/bobincraft/armament/client/presentation/fabric/FirstPersonDiagnostic.java) | Fabric lifecycle/tick/commands, eligibility и управление экземпляром представления. |
| [ArmamentGlbClient.java](../../src/main/java/com/bobincraft/armament/client/ArmamentGlbClient.java) | Существующий ClientModInitializer: shader callback, resource reload, регистрация режима. |

Эти классы предназначены для Bobin, где уже существуют модели/ресурсы,
загрузчик GLB, наблюдения травм/управления и GPU backend. Полный проект включает
все эти зависимости. Отдельный Java-архив нужен для просмотра/переноса в этот
проект; самостоятельным модом без остального Bobin он не объявляется.

## Правила состояний

| Действие | После завершения | Аддитивное движение |
|---|---|---|
| Статичная калибровка | Остаётся на `idle@0` | Выключено |
| `idle` | Зациклено | Разрешено при ходьбе |
| `inspect` | Возвращается в `idle` | Плавно убирается |
| `raise` | Возвращается в `idle` | Плавно убирается |
| `lower` | `HOLDING`, удерживает последнюю позу до следующей команды | Выключено, включая удержание |
| `move` | Зациклено по явной диагностической команде | Второй экземпляр gait не накладывается |

При прерывании переход начинается с текущей смешанной позы, а не с bind pose.
Обычный crossfade продолжает обе участвующие анимации. Вес перехода первого
лица равен `t*t*(3-2*t)`; translation/scale смешиваются в TRS, вращение — SLERP.
У веса нулевая производная на границах. Для произвольного прерванного движения
гарантируется непрерывность положения (C0), не заявляется C1 всех скоростей.
Старый API контроллера сохраняет линейный crossfade по умолчанию, поэтому
прежние static GLB / animation preview не меняют политику смешивания.

## Два исправления

**Ходьба и опущенные руки.** `allowsMovement()` допускает слой gait только
для активного `idle`. При `lower`, `raise`, `inspect` слой плавно уходит через
тот же mixer. После завершения `lower` он остаётся выключенным. После
автоматического возвращения в `idle` учитывается последнее движение игрока.

**Инерция и статичный режим.** `inertiaRequested` хранит предпочтение отдельно
от фактического включения. Калибровка временно отключает spring, сохраняя
предпочтение. Первый запрос анимации восстанавливает его. Повторный `on` при
уже включённой инерции не сбрасывает накопленное движение spring.

## Fabric hooks и рендер

`fabric.mod.json` уже содержит client entrypoint `ArmamentGlbClient` и
client-only `bobin_craft.armament.client.mixins.json`. В mixin-конфиге включены
`FirstPersonHeldItemMixin`, `GameRendererFovAccess`, Java 17 и `defaultRequire=1`.

- `ClientTickEvents.END_CLIENT_TICK` ведёт отдельный монотонный `visualTick`
  и обновляет состояние/инерцию один раз на клиентский тик, с учётом паузы.
  Коррекции server world time не перематывают косметическую анимацию.
- `HeldItemRenderer#renderItem(float, MatrixStack, VertexConsumerProvider.Immediate,
  ClientPlayerEntity, int)` вызывается через Mixin `@Inject(HEAD)`. Ванильный
  проход отменяется только после успешной собственной отрисовки.
- `GameRenderer#getFov(Camera,float,boolean)` вызывается через `@Invoker` с
  `changingFov=false`, как в hand projection ванили. Готовая матрица получается
  через `getBasicProjectionMatrix(double)`.
- `FirstPersonGlbRenderer` читает переданный MatrixStack, не меняя его. Он
  выбирает позу по `(visualTick + tickDelta)/20`, проверяет контакты/near plane,
  обновляет skin binding и вызывает действующий `FabricGlbRenderer`.
  Завершение действий, продвижение часов и переключение gait здесь не выполняются.
- Существующий GPU backend восстанавливает изменённое GL-состояние в `finally`.
  При недоступном shader/buffer или несовместимом состоянии игрока остаётся
  ванильный проход; другие предметы и игровые механики не переписаны.

Точные дескрипторы сверены с официальными Yarn mappings ветки `1.20.1`;
[запись проверки](animation-rework/validation/yarn-hook-signatures.json).
Это проверка интерфейса mappings, а не замена компиляции с Minecraft.

## Новые оригинальные клипы

| Клип | Раньше | Сейчас |
|---|---:|---:|
| `idle` | 2 с, неподвижный | 4 с, сдержанное дыхание |
| `inspect` | 3 с, покачивание | 2,75 с: подготовка, две стороны с паузами, возврат |
| `raise` | 1,25 с | 0,625 с, подвод и стабилизация |
| `lower` | 1,25 с | 0,5 с, опускание и удержание |
| `move` | 1 с | 1 с, новая сдержанная траектория шагов |

Ключевые позы: `scripts/armament/first_person_animation_poses.py`. Экспорт
через Blender 4.3.2 в реальные GLB с частотой запекания 192 Гц. Частота повышена
после обнаруженного превышения старого допуска 0,08 мм на быстром `raise` при
запекании 96 Гц. Допуск тестов сохранён. Добавлены независимые Blender golden
позы во внеключевые моменты, чтобы проверка не сводилась к совпадению ключей.

Риг, размеры, bind pose, геометрия хватов и поверхности кистей сохранены.
`/bobin_glb fp static` по-прежнему абсолютно неподвижен. Отпускание хвата,
баллистика, NPC и новое оружие не добавлялись.

## Проверки и ограничения

- **971 Java CPU-тест, 0 failures/errors/skipped**, 22 suites.
- Пройдены регрессии двух ошибок; проверяется действительное изменение view
  matrix при восстановленной инерции, а не только значение флага.
- Пройдены плавные/прерванные crossfade, старые линейные переходы, quaternion
  interpolation, кадры 30/60/144 FPS, нормали, UV, контакты и проекции 70/90/110°.
- Blender: по 60 удерживающих/внеключевых поз на вариант, 0 проверенных
  взаимных и самопересечений; 7 крайних сгибов на вариант без самопересечений.
  Внеключевые вершины сравниваются с Java с прежним допуском 0,08 мм.
- Schema checks: 83 общих случая, 30 FP, 1+5 appearance; SAT: 6 тестов.
- Khronos glTF Validator для каждого GLB: 0 errors, прежние 4 warnings/11 infos.
- Полная Fabric-сборка останавливается **до `compileJava`** на недоступном
  Fabric Loom 1.7.4: политика proxy возвращает HTTP 403 для Fabric Maven.
  Поэтому компиляция Minecraft-адаптера, применение mixin в JVM игры,
  GPU-отрисовка и live skin пока не подтверждены.

Свежие XML, логи, метрики и SHA:
[animation-rework/validation](animation-rework/validation/).
Исторические отчёты Task 5.1.3 относятся к предыдущей версии; они не выданы
за результаты этой переработки.

## Проверка в игре

После устранения доступа к Fabric Maven:

```sh
./gradlew --no-daemon --max-workers=4 build
./gradlew runGlbDebugClient
```

Последняя команда включает `-Dbobin.armament.glbDebug=true`. Режим выключен
по умолчанию. В первом лице, со здоровым персонажем и пустыми обеими руками:

```text
/bobin_glb fp show
/bobin_glb fp inertia on
/bobin_glb fp anim idle
/bobin_glb fp status
```

В status должны быть `inertiaRequested=true`, `inertia=true`, `staticIdle=false`.
Повернуть камеру и проверить запаздывание. Затем выполнить `fp static`,
снова `fp anim idle`: настройка инерции должна сохраниться.

При ходьбе выполнить `fp anim lower`: после плавного выхода gait опущенные
руки остаются неподвижны, status показывает `phase=HOLDING` и
`movementLayer=false`. `fp anim raise` возвращает `idle` и актуальный gait.
Проверить `inspect`, его прерывание командой `lower`, повторное поднятие,
разные FPS, паузу, изменение времени мира и F3+T. `fp hide` возвращает vanilla.

Видеопревью Blender, 640×360 / FOV70 / 12 FPS, не игровая запись:
[до](../../art/armament/animation-rework/before/first_person_fixture.all-clips.mp4),
[Steve после](../../art/armament/animation-rework/after/first_person_fixture.all-clips.mp4),
[Alex после](../../art/armament/animation-rework/after/first_person_fixture_slim.all-clips.mp4).
Это показ авторских клипов, а не запись Java-переходов/инерции; Java-логика
проверена отдельно CPU-тестами.

## Источник архитектурных идей

Использован подтверждённый пользователем [MCModderAnchor/TACZ](https://github.com/MCModderAnchor/TACZ),
ветка `1.20.1`, commit `b482eff8c94a733ac8d0910193fca3893954027c`.
Сам ZIP превышал лимит инструмента 32 МиБ и не был прочитан.

Изучены [AnimationStateMachine](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/java/com/tacz/guns/api/client/animation/statemachine/AnimationStateMachine.java),
[AnimationController](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/java/com/tacz/guns/api/client/animation/AnimationController.java),
[ObjectAnimationRunner](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/java/com/tacz/guns/api/client/animation/ObjectAnimationRunner.java)
и правила default state machine. Применены общие идеи разделения действий,
аддитивного движения, завершения и прерывания. Код реализации и ключевые позы
написаны для Bobin самостоятельно. Код TACZ — GPLv3; ресурсы — CC BY-NC-ND4.0.
В проект не копировались их классы, модели, текстуры или анимационные keyframes.
