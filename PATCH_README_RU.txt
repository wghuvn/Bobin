Исправление зависания на 100% при загрузке мира (Mixin InvalidInjectionException).
Minecraft 1.20.1 / Bobin Craft 2.7.7

Распаковать архив в корень E:\GDownload\Bobin_craft с заменой src/main/java/com/bobincraft/mixin/ClientChunkManagerDroneMixin.java.
Собрать: gradlew.bat clean build -x test
Установить новый JAR из build/libs, убрав старую версию мода из mods.

Изменена сигнатура инъекции ClientChunkManager.unload с ChunkPos на int x,int z (Minecraft 1.20.1).
Прочие механики и файлы не изменены.
Полная компиляция и игровой тест в данной среде не выполнялись.
