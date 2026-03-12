import re

f = r'd:\devstuff\musicsync\src\main\kotlin\dev\mcrib884\musync\server\MusicManager.kt'
with open(f, 'r', encoding='utf-8') as fh:
    lines = fh.readlines()

# Find and replace the onServerStarted block
new_lines = []
skip_until_close = False
i = 0
while i < len(lines):
    line = lines[i]
    
    # Fix onServerStarted serverDir block
    if 'fun onServerStarted()' in line:
        new_lines.append('    fun onServerStarted() {\n')
        new_lines.append('        CustomTrackManager.scan(serverDir(currentServer()))\n')
        # Skip until loadDimensionDelays
        i += 1
        while i < len(lines) and 'loadDimensionDelays()' not in lines[i]:
            i += 1
        new_lines.append('        loadDimensionDelays()\n')
        i += 1
        continue
    
    # Fix onPlayerLeave - leavingPlayer references
    if 'val leavingPlayer = event.entity as? ServerPlayer ?: return' in line:
        # Skip this line - leavingPlayer is now a parameter
        i += 1
        continue
    
    # Fix all serverDirectory stonecutter blocks (getDelaysFile, hotloadTracks, readNextFile)
    if '//? if >=1.21 {' in line:
        # Check if it's a serverDirectory block
        if i + 1 < len(lines) and 'serverDirectory' in lines[i+1] and 'toFile()' in lines[i+1]:
            # This is a serverDirectory guard - replace with serverDir helper
            # Find the variable name from the else branch
            else_idx = i + 2  # //? } else {
            var_line_idx = else_idx + 1  # val serverDir = ...
            while var_line_idx < len(lines) and '//?}' not in lines[var_line_idx]:
                if 'serverDirectory' in lines[var_line_idx] and '=' in lines[var_line_idx]:
                    # Extract: val xxx = server?.serverDirectory ?: ...
                    stripped = lines[var_line_idx].strip()
                    if stripped.startswith('val '):
                        var_name = stripped.split('=')[0].replace('val ', '').strip()
                        indent = len(lines[var_line_idx]) - len(lines[var_line_idx].lstrip())
                        # Find the server var name
                        if 'currentServer()' in stripped:
                            new_lines.append(' ' * indent + f'val {var_name} = serverDir(currentServer())\n')
                        elif 'server?' in stripped or 'server.' in stripped:
                            new_lines.append(' ' * indent + f'val {var_name} = serverDir(server)\n')
                        else:
                            new_lines.append(' ' * indent + f'val {var_name} = serverDir(currentServer())\n')
                        break
                var_line_idx += 1
            # Skip past //? }
            end_idx = var_line_idx + 1
            while end_idx < len(lines) and '//?' not in lines[end_idx]:
                end_idx += 1
            if end_idx < len(lines) and '//?}' in lines[end_idx]:
                end_idx += 1
            i = end_idx
            continue
        # Check for runDir pattern
        elif i + 1 < len(lines) and 'runDir' in lines[i+1] and 'serverDirectory' in lines[i+1]:
            stripped_next = lines[i+1].strip()
            indent = len(lines[i+1]) - len(lines[i+1].lstrip())
            new_lines.append(' ' * indent + 'val runDir = serverDir(currentServer())\n')
            # Skip to end of guard
            end_idx = i + 2
            while end_idx < len(lines):
                if '//?}' in lines[end_idx]:
                    end_idx += 1
                    break
                end_idx += 1
            i = end_idx
            continue
    
    new_lines.append(line)
    i += 1

with open(f, 'w', encoding='utf-8') as fh:
    fh.writelines(new_lines)

print(f'Processed {len(lines)} -> {len(new_lines)} lines')
