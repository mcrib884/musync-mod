f = r'd:\devstuff\musicsync\src\main\kotlin\dev\mcrib884\musync\command\MuSyncCommand.kt'
with open(f, 'r', encoding='utf-8') as fh:
    lines = fh.readlines()

new_lines = []
i = 0
replaced = 0
while i < len(lines):
    line = lines[i]
    stripped = line.rstrip('\n')
    
    # Detect sendSuccess stonecutter guard: //? if >=1.20 {
    if '//? if >=1.20 {' in stripped:
        # Look ahead to see if the next non-whitespace is ctx.source.sendSuccess(
        j = i + 1
        if j < len(lines) and 'ctx.source.sendSuccess(' in lines[j]:
            # Found a sendSuccess block - collect the 1.20 version
            indent_line = lines[j]
            base_indent = indent_line[:len(indent_line) - len(indent_line.lstrip())]
            
            # Collect lines until //?} else {
            success_lines = []
            k = j
            while k < len(lines) and '//?} else {' not in lines[k]:
                success_lines.append(lines[k])
                k += 1
            # k is now at //?} else {
            # Skip the else block until //?}
            end_k = k + 1
            while end_k < len(lines):
                if '//?}' in lines[end_k] and 'else' not in lines[end_k]:
                    break
                end_k += 1
            # end_k is at the closing //?}
            
            # Now rewrite: ctx.source.sendSuccess(\n  { Component... },\n  broadcast\n)
            # into: ctx.source.sendSuccessCompat(\n  { Component... },\n  broadcast\n)
            for sl in success_lines:
                new_lines.append(sl.replace('ctx.source.sendSuccess(', 'ctx.source.sendSuccessCompat(', 1))
            
            replaced += 1
            i = end_k + 1
            continue
        else:
            # Not a sendSuccess guard, keep as-is
            new_lines.append(line)
            i += 1
            continue
    
    new_lines.append(line)
    i += 1

with open(f, 'w', encoding='utf-8') as fh:
    fh.writelines(new_lines)
print(f'Replaced {replaced} sendSuccess blocks')
