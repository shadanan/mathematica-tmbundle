#!/usr/bin/env python
import re

def to_camelcase(function):
    return re.sub('(((?<=[a-z])[A-Z])|([A-Z](?![A-Z]|$)))', '_\\1', function).lower().strip('_')

def print_function_grammar(functions):
    print """
        {   name = 'support.function.mathematica.system';
            match = '\\b(%s)\\b';
        },""" % "|".join(functions)
    

def print_symbol_grammar(symbols):
    print """
        {   name = 'support.variable.mathematica.system';
            match = '(\\%s)\\b';
        },""" % "|".join(symbols)

def main():
    fp = open('symbols.json', 'r')
    functions, symbols = eval(fp.read())
    
    # print_function_grammar(functions)
    print_symbol_grammar(symbols)

if __name__ == '__main__':
    main()