# Обычные руки Minecraft в первом лице

База: `7712f1e`. Основа заменена: текущий проход `/bobin_glb fp` использует
**настоящие `PlayerEntityModel.rightArm/leftArm/rightSleeve/leftSleeve`**, полученные
из `PlayerEntityRenderer` самого игрока. Новая геометрия рук в Java не создаётся.

В активном GLB находятся **только 7 деталей тестового предмета, 0 skins и точки
привязки**. Кастомные ладони, пальцы, локти, деформируемые предплечья и прежние
плечевые продолжения не участвуют в этом проходе. Это не изменение масштаба
старой анатомической модели и не наложение скина на неё.

## Где изменена основа

- [VanillaPlayerArmRenderer.java](../../src/main/java/com/bobincraft/armament/client/presentation/fabric/VanillaPlayerArmRenderer.java)
  берёт штатные части из `PlayerEntityRenderer.getModel()`, рисует их через
  `ModelPart.render()` с актуальным `player.getSkinTexture()` и ванильными
  `RenderLayer`. Рукава включаются независимо через настройки частей скина.
  Временные pivot/углы и `visible` восстанавливаются в `finally`; MatrixStack
  также восстанавливается. Сторонние сущности и обычная модель игрока не меняются.
- [VanillaArmAttachments.java](../../src/main/java/com/bobincraft/armament/client/presentation/VanillaArmAttachments.java)
  подводит жёсткие штатные руки к двум хватам предмета. Длина 12 пикселей,
  глубина 4; ширина Steve 4, Alex 3. Масштабирования, пальцев и сегментов локтя нет.
  Учитывается локальный центр конца настоящего vanilla ModelPart, а не pivot
  прежней кастомной кисти.
- [FirstPersonResources.java](../../src/main/java/com/bobincraft/armament/client/presentation/fabric/FirstPersonResources.java)
  загружает новые профили `vanilla_first_person_fixture{,_slim}.profile.json`.
  Старый `first_person_hands.appearance.json` для этого прохода больше не нужен.
  Загрузка GLB со skinned-руками или любым мешем кроме деталей калибра
  отвергается `VanillaArmAttachments`: скрытого возврата к кастомным кистям нет.
- [FirstPersonGlbRenderer.java](../../src/main/java/com/bobincraft/armament/client/presentation/fabric/FirstPersonGlbRenderer.java)
  рисует отдельно предмет существующим GLB backend и отдельно родные руки
  Minecraft обычным entity pipeline. Матрица размещения применяется один раз.

Сохранены предыдущие исправления инерции, стейт-машина, crossfade и оригинальные
клипы `idle/inspect/raise/lower/move`. Анимация двигает точки привязки и предмет;
ванильные руки остаются обычными прямоугольными частями модели. Forge EventBus
и классы TACZ не добавлялись. Подход TACZ — использовать части модели игрока —
применён к Fabric API / SpongePowered Mixin и Yarn 1.20.1.

Исторические кастомные fixtures и их проверки оставлены для совместимости
старых GLB-инструментов, но новые `FirstPersonResources.PROFILE` и
`VanillaArmAttachments.PROFILES` на них не ссылаются. Старые Blender-превью не
являются результатом новой реализации.

## Проверка

- **1072 CPU-теста, 0 failures/errors/skipped**, включая 101 проверку нового
  пути: отсутствие hand meshes/skins в активных GLB; отказ загрузить старый
  skinned fixture; размеры прямоугольников, отсутствие растяжения/сужения;
  независимые Blender-матрицы и углы кубоидов; реальные плоскости хвата;
  near plane и торцы рук при FOV 70/90/110 во всех пяти клипах.
- Кожа достигает выбранной реальной плоскости хвата с зазором 1 мм.
  Рукав сохраняет обычное ванильное расширение на четверть пикселя — это
  штатная оболочка скина, её отдельная геометрия не переделана.
- Сигнатуры `ModelPart.getTransform/setTransform/render`, `ModelTransform.NONE`
  и штатные поля рукавов сверены с официальными Yarn mappings ветки 1.20.1.
- Полная Fabric-сборка и фактическая игровая отрисовка здесь пока не подтверждены:
  загрузка Fabric Loom останавливается на HTTP 403 для Fabric Maven.
  CPU-проверки и Blender-схемы не заменяют Minecraft-тест.

В `art/armament/vanilla_first_person_fixture{,_slim}.blend` находятся **только
калибровочные прокси стандартных кубоидов** для проверки камеры. Они создаются
после GLB-экспорта и в runtime GLB не входят. В игре геометрию и UV предоставляет
сама Minecraft-модель; используется скин игрока, не диагностическая текстура.

Схемы расстановки кубоидов, не скриншоты Minecraft:
[Steve 70°](../../art/armament/vanilla_first_person_fixture.fov70.png),
[Steve 110°](../../art/armament/vanilla_first_person_fixture.fov110.png),
[Alex 70°](../../art/armament/vanilla_first_person_fixture_slim.fov70.png).

## Как увидеть новый проход

Собрать обновлённый проект для Java 17 / Fabric 1.20.1 и заменить старый JAR
Bobin. Затем запустить `./gradlew runGlbDebugClient` или использовать существующий
режим с `-Dbobin.armament.glbDebug=true`. В первом лице, с пустыми руками:

```text
/bobin_glb fp show
/bobin_glb fp status
/bobin_glb fp anim inspect
```

В status должно присутствовать **`fpHands=vanillaModelParts`**. Это отличает
новый рендер от прежней сборки с кастомной геометрией. Проверить Steve/Alex,
свой скин, независимое выключение двух рукавов, FOV 70/90/110, паузу и F3+T.
`fp static`, `fp inertia on/off`, остальные клипы и `fp hide` сохранены.

Воспроизведение калибровки:

```sh
blender -b -t 4 --python scripts/armament/export_vanilla_first_person_fixture.py -- --variant default
blender -b -t 4 --python scripts/armament/export_vanilla_first_person_fixture.py -- --variant slim
./gradlew --no-daemon --max-workers=4 -p tools/armament-glb-check test
```
