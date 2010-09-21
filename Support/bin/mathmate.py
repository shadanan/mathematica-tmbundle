#!/usr/bin/env python
import sys
import os
import string
from optparse import OptionParser

class MathMate(object):
    def __init__(self):
        self.parse_tree_level = None
        self.doc = sys.stdin.read()
        
        self.indent_size = int(os.environ['TM_TAB_SIZE'])
        if os.environ.get('TM_SOFT_TABS') == "YES":
            self.indent = " " * self.indent_size
        elif os.environ.get('TM_SOFT_TABS') == "NO":
            self.indent = "\t"
        
        self.tmln = int(os.environ.get('TM_LINE_NUMBER'))
        self.tmli = int(os.environ.get('TM_LINE_INDEX'))
        self.tmcursor = self.get_pos(self.tmln, self.tmli)
        self.selected_text = os.environ.get('TM_SELECTED_TEXT')
        self.statements = self.parse(self.doc)
    
    def get_pos(self, line, column):
        line_index = 1
        line_pos = 0
    
        for pos, char in enumerate(self.doc):
            if line == line_index and column == pos - line_pos:
                return pos
        
            if char == "\n":
                line_index += 1
                line_pos = pos + 1
    
    def get_line_col(self, posq):
        line_index = 1
        line_pos = 0
        
        for pos, char in enumerate(self.doc):
            if posq == pos:
                return (line_index, pos - line_pos)
        
            if char == "\n":
                line_index += 1
                line_pos = pos + 1
        
        return (line_index, pos - line_pos)

    def count_indents(self, line):
        count = 0
        space_count = 0

        for char in line.rstrip():
            if char == "\t":
                count += 1
            elif char == " ":
                space_count = ((space_count + 1) % self.indent_size)
                if space_count == 0:
                    count += 1
            else:
                break
        return count
    
    def get_next_non_space_char(self, pos):
        for i in xrange(pos, len(self.doc)):
            if self.doc[i] in (" ", "\t"):
                continue
            if self.doc[i] == "\n":
                return None
            return self.doc[i]
        return None
    
    def get_prev_non_space_char(self, pos):
        for i in xrange(pos, -1, -1):
            if self.doc[i] in (" ", "\t"):
                continue
            if self.doc[i] == "\n":
                return None
            return self.doc[i]
        return None

    def is_end_of_line(self, pos):
        return self.get_next_non_space_char(pos) == None
    
    def parse(self, block, initial_indent_level = None):
        statements = []
        
        pos = 0
        ss_pos = 0
        current = []
        scope = []
        
        if initial_indent_level is None:
            initial_indent_level = self.count_indents(block)
        
        while pos < len(block):
            c1 = block[pos]
            c2 = block[pos:pos+2]
            c3 = block[pos:pos+3]
            pc = block[pos-1] if pos > 0 else None

            nnsc = None
            for i in xrange(pos + 1, len(block)):
                if block[i] == "\n":
                    break
                    
                if block[i] not in (" ", "\t"):
                    nnsc = block[i]
                    break

            if pos == self.tmcursor:
                self.parse_tree_level = ".".join(scope)

            if len(scope) == 0:
                if c1 not in (" ", "\t", "\n"):
                    if current != []:
                        statements.append((ss_pos, pos, "".join(current), block[ss_pos:pos]))
                        current = []

                    ss_pos = pos
                    scope.append("root")
                    continue

                current += c1
                pos += 1
                continue

            if scope[-1] == "string":
                if c2 == '\\"':
                    current += c2
                    pos += 2
                    continue

                if c1 == '"':
                    scope.pop()
                    current += c1
                    pos += 1
                    continue

                current += c1
                pos += 1
                continue

            if scope[-1] == "comment":
                if c3 == '\\*)':
                    current += c3
                    pos += 3
                    continue

                if c2 == '*)':
                    scope.pop()
                    current += c2
                    pos += 2
                    continue

                current += c1
                pos += 1
                continue

            if c1 in (" ", "\t"):
                vsc = string.ascii_letters + string.digits
                if pc is not None and pc in vsc and nnsc in vsc:
                    current += " "
                pos += 1
                continue
        
            if c3 == "^:=":
                scope.append("define")
                if self.is_end_of_line(pos + 3):
                    scope.append("start")
                current += " ", c3, " "
                pos += 3
                continue

            if c3 in ("===", ">>>"):
                current += " ", c3, " "
                pos += 3
                continue

            if c2 in ("*^", "&&", "||", "==", ">=", "<=", ";;", "/.", "->", ":>", "@@", "<>", ">>", "/@", "/;", "//", "~~"):
                current += " ", c2, " "
                pos += 2
                continue

            if c2 == "..":
                current += c2, " "
                pos += 2
                continue

            if c2 in (":=", "^="):
                scope.append("define")
                if self.is_end_of_line(pos + 2):
                    scope.append("start")
                current += " ", c2, " "
                pos += 2
                continue

            if c2 == "(*":
                scope.append("comment")
                current += c2
                pos += 2
                continue

            if c2 == "[[":
                scope.append("part")
                current += c2
                pos += 2
                continue
        
            if c2 == "]]" and scope[-1] == "part":
                scope.pop()
                current += c2
                pos += 2
                continue
        
            if c1 == "[":
                scope.append("function")
                current += c1
                pos += 1
                continue
        
            if c1 == "]":
                scope.pop()
                current += c1
                pos += 1
                continue
        
            if c1 == "{":
                scope.append("list")
                current += c1
                pos += 1
                continue
        
            if c1 == "}":
                if scope[-1] == "define":
                    scope.pop()
                scope.pop()
                current += c1
                pos += 1
                continue
        
            if c1 == "(":
                scope.append("group")
                current += c1
                pos += 1
                continue
        
            if c1 == ")":
                # if scope[-1] == "define":
                #     scope.pop()
                scope.pop()
                current += c1
                pos += 1
                continue
        
            if c1 in ("+", "*", "/", "^", "!", ">", "<", "|", "?"):
                current += " ", c1, " "
                pos += 1
                continue
            
            if c1 == "-":
                if self.get_prev_non_space_char(pos-1) not in ("{", "(", "["):
                    current += " ", c1, " "
                else:
                    current += c1
                pos += 1
                continue

            if c1 == "&":
                current += " ", c1
                pos += 1
                continue
            
            if c1 == "=":
                scope.append("define")
                if self.is_end_of_line(pos + 1):
                    scope.append("start")
                current += " ", c1, " "
                pos += 1
                continue
        
            if c1 == ",":
                if scope[-1] == "define":
                    scope.pop()
                current += c1, " "
                pos += 1
                continue

            if c1 == ";":
                if scope[-1] == "define":
                    scope.pop()
                if scope[-1] == "start":
                    scope.pop()
                if scope[-1] == "root":
                    scope.pop()
                current += c1
                pos += 1
                continue

            if c1 == "\n":
                if scope[-1] == "define":
                    scope.pop()
                if scope[-1] == "start":
                    scope.pop()
                if scope[-1] == "root":
                    scope.pop()
                current += c1
                pos += 1

                indent_level = len(scope) + initial_indent_level - 1
                if nnsc in ("]", "}", ")"):
                    current += (self.indent * (indent_level - 1))
                else:
                    current += (self.indent * indent_level)

                continue
                
            if c1 == '"':
                scope.append("string")
                current += c1
                pos += 1
                continue
            
            current += c1
            pos += 1
            continue

        if current != []:
            statements.append((ss_pos, pos, "".join(current), block[ss_pos:pos]))
        return statements
    
    def get_current_statement_index(self):
        for index, (ssp, esp, reformatted_statement, current_statement) in enumerate(self.statements):
            if self.tmcursor >= ssp and self.tmcursor < esp:
                return index
            
    def get_current_statement(self):
        return self.statements[self.get_current_statement_index()]
    
    def reformat(self):
        if self.selected_text is not None:
            for ssp, esp, reformatted_statement, current_statement in self.statements:
                sys.stdout.write(reformatted_statement)
        else:
            ssp, esp, reformatted_statement, current_statement = self.get_current_statement()
            sys.stdout.write(self.doc[0:ssp])
            sys.stdout.write(reformatted_statement)
            sys.stdout.write(self.doc[esp:])

    def show(self):
        print "Cursor: (Line: %d, Index: %d, Tree: %s)" % (self.tmln, self.tmli, self.parse_tree_level)

        if self.selected_text is None:
            ssp, esp, reformatted_statement, current_statement = self.get_current_statement()
            ssln, ssli = self.get_line_col(ssp)
            esln, esli = self.get_line_col(esp)
            print "Statement Boundaries: (Line: %d, Index: %d) -> (Line: %d, Index: %d)" % (ssln, ssli, esln, esli)
            print reformatted_statement,
        else:
            for index, (ssp, esp, reformatted_statement, current_statement) in enumerate(self.statements):
                ssln, ssli = self.get_line_col(ssp)
                esln, esli = self.get_line_col(esp)
                print "Statement %d Boundaries: (Line: %d, Index: %d) -> (Line: %d, Index: %d)" % (index, ssln, ssli, esln, esli)
                if len(reformatted_statement.strip()) != 0:
                    print reformatted_statement.rstrip()
                else:
                    print "*** Empty Statement ***"
                print

def main():
    parser = OptionParser()
    (options, args) = parser.parse_args()
    command = args[0]
    
    mm = MathMate()

    if command == "show":
        mm.show()
        return
    
    if command == "reformat":
        mm.reformat()
        return
    
    print "Command not recognized: %s" % command

if __name__ == '__main__':
    main()