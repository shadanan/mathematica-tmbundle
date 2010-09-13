#!/usr/bin/env python
import sys
import os
import string

def count_indents(line):
    count = 0
    space_count = 0
    
    for char in line.rstrip():
        if char == "\t":
            count += 1
        elif char == " ":
            space_count = ((space_count + 1) % 2)
            if space_count == 0:
                count += 1
        else:
            break
    return count

def main():
    INDENT = "  "
    if os.environ.get('TM_SOFT_TABS') == "YES":
        INDENT = " " * int(os.environ['TM_TAB_SIZE'])
    elif os.environ.get('TM_SOFT_TABS') == "NO":
        INDENT = "\t"

    indent_level = None
    scope = "source"
    if 'TM_SCOPE' in os.environ:
        scopes = os.environ['TM_SCOPE'].split()
        if scopes[-1].startswith("string"):
            scope = "string"
        elif scopes[-1].startswith("comment"):
            scope = "comment"
        else:
            scope = "source"
    
    while True:
        line = sys.stdin.readline()
        if not line:
            break
    
        if indent_level is None:
            indent_level = count_indents(line)
        
        # Parse line
        pos = 0
        result = []
        
        while pos < len(line):
            c1 = line[pos]
            c2 = line[pos:pos+2]
            c3 = line[pos:pos+3]
            
            pc = line[pos-1] if pos > 0 else None
            
            nnsc = None
            for i in range(pos, len(line)):
                if line[i] not in (" ", "\t"):
                    nnsc = line[i]
                    break
            
            if scope == "string":
                if c2 == '\\"':
                    result += c2
                    pos += 2
                    continue
                
                if c1 == '"':
                    scope = "source"
                    result += c1
                    pos += 1
                    continue
                
                result += c1
                pos += 1
                continue
            
            if scope == "comment":
                if c3 == "\\*)":
                    result += c3
                    pos += 3
                    continue
                    
                if c2 == "*)":
                    scope = "source"
                    result += c2
                    pos += 2
                    continue
                
                result += c1
                pos += 1
                continue
            
            if scope == "source":
                if pos == 0:
                    if len(line.strip()) > 0 and line.strip()[0] in ("]", "}", ")"):
                        result += (INDENT * (indent_level - 1))
                    else:
                        result += (INDENT * indent_level)
                
                if c1 in (" ", "\t"):
                    if pc in (string.ascii_letters + string.digits) and nnsc in (string.ascii_letters + string.digits):
                        result += " "
                    pos += 1
                    continue
                
                if c3 in ("^:=", "===", ">>>"):
                    result += " ", c3, " "
                    pos += 3
                    continue
                
                if c2 in ("*^", ":=", "^=", "&&", "||", "==", ">=", "<=", ";;", "/.", "->", ":>", "@@", "<>", ">>", "/@", "/;", "//", "~~"):
                    result += " ", c2, " "
                    pos += 2
                    continue
                
                if c2 == "..":
                    result += c2, " "
                    pos += 2
                    continue
                
                if c2 == "(*":
                    scope = "comment"
                    result += c2
                    pos += 2
                    continue
                
                if c1 in ("[", "{", "("):
                    result += c1
                    indent_level += 1
                    pos += 1
                    continue
                
                if c1 in ("]", "}", ")"):
                    result += c1
                    indent_level -= 1
                    pos += 1
                    continue
                
                if c1 in (","):
                    result += c1, " "
                    pos += 1
                    continue
                
                if c1 in ("+", "-", "*", "/", "^", "!", ">", "<", "|", "?", "="):
                    result += " ", c1, " "
                    pos += 1
                    continue
                
                if c1 == "&":
                    result += " ", c1
                    pos += 1
                    continue
                
                if c1 in ("@", ";", "#"):
                    result += c1
                    pos += 1
                    continue
                
                if c1 == '"':
                    scope = "string"
                    result += c1
                    pos += 1
                    continue
            
                result += c1
                pos += 1
                continue
        
        sys.stdout.write("".join(result))
    
if __name__ == '__main__':
    main()