#!/usr/bin/env python2
"""
Extracts log statements from source tree.

@author Ph4r05
"""

import sys
import re
import plyj.parser
import plyj.model as m
import argparse
import traceback

class MyVisitor(m.Visitor):
    tranTable={'v':'vf', 'd':'df', 'i':'inf', 'w':'wf', 'e':'ef'}
    logMethods=['vf', 'df', 'inf', 'wf', 'ef']
    desiredLogMethods=['v', 'd', 'i', 'w', 'e', 'vf', 'df', 'inf', 'wf', 'ef']
    
    meth=0
    logAcc = []

    def __init__(self):
        super(MyVisitor, self).__init__()

        self.first_field = True
        self.first_method = True

    def visit_ClassDeclaration(self, class_decl):
        return self.visit_type_declaration(class_decl)

    def visit_InterfaceDeclaration(self, interface_decl):
        return self.visit_type_declaration(interface_decl)

    def visit_type_declaration(self, type_decl):
        return True

    def visit_FieldDeclaration(self, field_decl):
        return True

    def visit_MethodDeclaration(self, method_decl):
        return True

    def visit_VariableDeclaration(self, var_declaration):
        return True

    def visit_IfThenElse(self, ife):
        if ife.if_true != None:
            ife.if_true.accept(self)
        if ife.if_false != None:
            ife.if_false.accept(self)
        return True

    def visit_While(self, wh):
        if wh.body != None:
            wh.body.accept(self)
        return True

    def visit_For(self, fh):
        return True

    def visit_ForEach(self, feh):
        return True

    def visit_Switch(self, sw):
        return True

    def visit_SwitchCase(self, swc):
        return True

    def visit_DoWhile(self, dwh):
        return True

    def visit_Synchronized(self, sh):
        return True

    def visit_Try(self, tr):
        return True

    def visit_Catch(self, cth):
        return True

    def visit_MethodInvocation(self, meth):
        if not isinstance(meth.target, m.Name): return
        if not (meth.target.value=="Log"): return
        if not (meth.name in self.desiredLogMethods): return

        if len(meth.arguments)<2:
            raise Exception("Method has invalid number of arguments: %s" % (str(meth)))

        hasException = len(meth.arguments) > 2

        lines = str(meth.linespan) + " | " + str(meth.lexspan)
        tag = meth.arguments[0]
        args=""
        if self.meth==0:
            args = argToString(meth.arguments[1])
            self.logAcc.append(args)
        elif self.meth==1:
            args = extractString(meth.arguments[1])
            self.logAcc += args
        else:
            raise Exception("Unknown parsing method, meth=" + self.meth)
        return True

def extractString(arg):
    '''Extracts string literals from recursive lexical representation.'''
    # End of the recursion.
    if arg==None: return ""
    if isinstance(arg, str):       return [] #[arg]
    if isinstance(arg, m.Name):    return []
    if isinstance(arg, m.Literal):
        if (len(arg.value)) >= 3: return [arg.value[1:-1]]
        else: return [arg.value]
    
    # Recursive constructions.
    if isinstance(arg, m.Type):
        ret = extractString(arg.name)
        return ret
    if isinstance(arg, m.MethodInvocation):
        acc = []
        for x in arg.arguments: acc = acc + extractString(x)
        return acc
    if isinstance(arg, m.Additive):
        ret = [] + extractString(arg.lhs) + extractString(arg.rhs)
        return ret
    if isinstance(arg, m.Conditional):
        return []
    if isinstance(arg, m.BinaryExpression):
        ret = [] + extractString(arg.lhs) + extractString(arg.rhs)
        return ret
    if isinstance(arg, m.ArrayAccess):
        return []
    if isinstance(arg, m.FieldAccess):
        return []
    if isinstance(arg, m.Unary):
        return []
    if isinstance(arg, m.Cast):
        return []
    if isinstance(arg, m.InstanceCreation):
        return []
    
    raise Exception("Sorry, not working 2 string, obj=" + str(arg))
    return arg

def argToString(arg):
    '''Converts recursive lexical representation to the flat string representation recursively.'''
    # End of the recursion.
    if arg==None: return ""
    if isinstance(arg, str):       return arg
    if isinstance(arg, m.Literal): return arg.value
    if isinstance(arg, m.Name):    return arg.value

    # Recursive constructions.
    if isinstance(arg, m.Type):
        ret = argToString(arg.name)
        ret += "[]"*arg.dimensions
        return ret
    if isinstance(arg, m.MethodInvocation):
        ret=""
        if arg.target!=None:
            ret+=argToString(arg.target) + "."
        ret+=arg.name + "("
        newArgs = [argToString(x) for x in arg.arguments]
        ret+= ", ".join(newArgs)
        ret+=")"
        return ret
    if isinstance(arg, m.Additive):
        ret = argToString(arg.lhs)
        ret += arg.operator
        ret += argToString(arg.rhs)
        return ret
    if isinstance(arg, m.Conditional):
        ret = "("
        ret += argToString(arg.predicate) + " ? "
        ret += argToString(arg.if_true) + " : "
        ret += argToString(arg.if_false) + ") "
        return ret
    if isinstance(arg, m.BinaryExpression):
        ret = "("
        ret += "(" + argToString(arg.lhs) + ") "
        ret += arg.operator
        ret += " (" + argToString(arg.rhs) + ")"
        ret += ")"
        return ret
    if isinstance(arg, m.ArrayAccess):
        ret = argToString(arg.target) + "[" + argToString(arg.index) + "]"
        return ret
    if isinstance(arg, m.FieldAccess):
        ret = argToString(arg.target) + "." + argToString(arg.name)
        return ret
    if isinstance(arg, m.Unary):
        ret = argToString(arg.sign) + argToString(arg.expression)
        return ret
    if isinstance(arg, m.Cast):
        ret = "(" + argToString(arg.target) + ")" + argToString(arg.expression)
        return ret
    if isinstance(arg, m.InstanceCreation):
        ret = "new " + argToString(arg.type)
        ret+= "("
        newArgs = [argToString(x) for x in arg.arguments]
        ret+= ", ".join(newArgs)
        ret+=")"
        return ret

    raise Exception("Sorry, not working 2 string, obj=" + str(arg))
    return str(arg)

def escapeFmtStr(arg):
    '''Escapes formatting string from formatting parameters, substitutes odd
    number of % to even number of %. Used http://regex101.com/r/iJ6bG9'''

    return re.sub(r'(?<!%)(%(?:%%)*)(?!%)', r'%\1', arg)

def isString(arg):
    if isinstance(arg, m.Literal): return True
    if isinstance(arg, m.Additive) and arg.operator=="+":
        return (isString(arg.lhs) or isString(arg.rhs))
    return False

# Main executable code
if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Log statements extractor.', formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    parser.add_argument('-o','--out-file',  help='Output file to write output to', required=False, default=None, dest='outfile')
    parser.add_argument('-e','--echo',      help='Writes output to the standard output', required=False, default=False)
    parser.add_argument('-v','--verbose',   help='Writes output to the standard output', required=False, default=0, type=int)
    parser.add_argument('-m','--method',    help='Method for string extraction. 0=dump java log arguments, 1=dump string literals', required=False, default=1, type=int, dest='meth')
    parser.add_argument('file')
    args = parser.parse_args()

    # Load the file and instantiate the visitor object.
    p = plyj.parser.Parser()
    if args.verbose>0:
        print " [-] Processing file: %s" % (args.file)

    # Start the parsing.
    try:
        v = MyVisitor()
        v.offset = 0
        v.meth = args.meth
        with open(args.file, 'r') as content_file:
            v.content = content_file.read()

        tree = p.parse_file(args.file)
        tree.accept(v)

        # If here, probably no exception occurred.
        if args.echo:
            print v.logAcc
        if args.outfile != None and len(args.outfile)>0 and len(v.logAcc)>0:
            with open(args.outfile, 'a+') as f:
                for curLog in v.logAcc:
                    f.write(curLog + "\n")

        if args.verbose>0:
            print " [-] Processing finished, len=%d" % len(v.logAcc)
    except Exception as e:
        print "    Error occurred! file[%s]" % (args.file), e
        if args.verbose>1:
            print '-'*60
            traceback.print_exc(file=sys.stdout)
            print '-'*60
        sys.exit(1)

