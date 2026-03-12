import re

f = r'd:\devstuff\musicsync\src\main\kotlin\dev\mcrib884\musync\command\MuSyncCommand.kt'
with open(f, 'r', encoding='utf-8') as fh:
    c = fh.read()

# 1) Replace imports - remove loader-specific registry imports, add Compat imports
old_imports = """import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
//? if neoforge {
/*import net.minecraft.core.registries.BuiltInRegistries*/
//? } else {
import net.minecraftforge.registries.ForgeRegistries
//? }"""

# More flexible - find and replace the pattern
c = c.replace("""import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
//? if neoforge {
/*import net.minecraft.core.registries.BuiltInRegistries*/
//?} else {
import net.minecraftforge.registries.ForgeRegistries
//?}""", """import dev.mcrib884.musync.sendSuccessCompat
import dev.mcrib884.musync.soundEventKeys
import dev.mcrib884.musync.soundEventContains
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer""")

# 2) Remove the soundEventKeys/soundEventContains private functions (they're now in Compat)
c = c.replace("""    //? if neoforge {
    /*private fun soundEventKeys() = BuiltInRegistries.SOUND_EVENT.keySet()
    private fun soundEventContains(key: ResourceLocation) = BuiltInRegistries.SOUND_EVENT.containsKey(key)*/
    //?} else {
    private fun soundEventKeys() = ForgeRegistries.SOUND_EVENTS.keys
    private fun soundEventContains(key: ResourceLocation) = ForgeRegistries.SOUND_EVENTS.containsKey(key)
    //?}""", "")

# 3) Replace all sendSuccess Stonecutter guards with sendSuccessCompat
# Pattern: //? if >=1.20 { \n  ctx.source.sendSuccess(\n  { Component.literal(...) },\n  ...\n  )\n  //?} else {\n  /*ctx.source.sendSuccess(\n  Component.literal(...),\n  ...\n  )*/\n  //?}

# Use regex to find and replace the sendSuccess stonecutter blocks
pattern = re.compile(
    r'//\? if >=1\.20 \{\n'
    r'(\s+)ctx\.source\.sendSuccess\(\n'
    r'\s+\{ (Component\.literal\([^)]+\)) \},\n'
    r'\s+(true|false)\n'
    r'\s+\)\n'
    r'\s+//\?\} else \{\n'
    r'\s+/\*ctx\.source\.sendSuccess\(\n'
    r'\s+Component\.literal\([^)]+\),\n'
    r'\s+(?:true|false)\n'
    r'\s+\)\*/\n'
    r'\s+//\?\}',
    re.MULTILINE
)

def repl_send_success(m):
    indent = m.group(1)
    component = m.group(2)
    broadcast = m.group(3)
    return f'{indent}ctx.source.sendSuccessCompat(\n{indent}    {{ {component} }},\n{indent}    {broadcast}\n{indent})'

count_before = len(pattern.findall(c))
c = pattern.sub(repl_send_success, c)
print(f'Replaced {count_before} sendSuccess blocks')

# 4) Also fix the "the_end" track name guard
c = c.replace("""        //? if >=1.21 {
        /*"the_end" to "minecraft:music.end|music/game/end/the_end",*/
        //?} else {
        "the_end" to "minecraft:music.end|music/game/end/end",
        //?}""", '        "the_end" to "minecraft:music.end|music/game/end/end",')

with open(f, 'w', encoding='utf-8') as fh:
    fh.write(c)
print('OK')
