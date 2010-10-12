#!/usr/bin/env python
import re

def to_camelcase(function):
    return re.sub('(((?<=[a-z])[A-Z])|([A-Z](?![A-Z]|$)))', '_\\1', function).lower().strip('_')

def print_function_grammar(functions):
    result = []

    for function in functions:
        result.append("""
                {    name = 'support.function.mathematica.system.%s';
                     match = '\\b(%s)\\b';
                },""" % (to_camelcase(function), function))
    
    print "".join(result)

def print_symbol_grammar(symbols):
    result = []
    
    for symbol in symbols:
        symbol = symbol.strip("$")
        result.append("""
                {   name = 'support.variable.mathematica.system.%s';
                    match = '(\\$%s)\\b';
                },""" % (to_camelcase(symbol), symbol))
    
    print "".join(result)

def main():
    fp = open('symbols.json', 'r')
    functions, symbols = eval(fp.read())
    
    print_symbol_grammar(symbols)

if __name__ == '__main__':
    main()