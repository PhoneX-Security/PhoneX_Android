#!/usr/bin/env python2
"""
Changes Log statements from string concatenation
to the formatting string format.

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
    desiredLogMethods=['v', 'd', 'i', 'w', 'e']
    
    content=""
    offset=0
    statementsChanged=0

    def __init__(self):
        super(MyVisitor, self).__init__()

        self.first_field = True
        self.first_method = True

    def visit_ClassDeclaration(self, class_decl):
        return self.visit_type_declaration(class_decl)

    def visit_InterfaceDeclaration(self, interface_decl):
        return self.visit_type_declaration(interface_decl)

    def visit_type_declaration(self, type_decl):
        #print(str(type_decl.name))
        #if type_decl.extends is not None:
        #    print(' -> extending ' + type_decl.extends.name.value)
        #if len(type_decl.implements) is not 0:
        #    print(' -> implementing ' + ', '.join([type.name.value for type in type_decl.implements]))
        #print
        return True

    def visit_FieldDeclaration(self, field_decl):
        return True
        #if self.first_field:
        #    print('fields:')
        #    self.first_field = False
        #for var_decl in field_decl.variable_declarators:
        #    if type(field_decl.type) is str:
        #        type_name = field_decl.type
        #    else:
        #        type_name = field_decl.type.name.value
        #    print('    ' + type_name + ' ' + var_decl.variable.name)

    def visit_MethodDeclaration(self, method_decl):
        #if self.first_method:
        #    print
        #    print('methods:')
        #    self.first_method = False
        #
        #param_strings = []
        #for param in method_decl.parameters:
        #    if type(param.type) is str:
        #        param_strings.append(param.type + ' ' + param.variable.name)
        #    else:
        #        param_strings.append(param.type.name.value + ' ' + param.variable.name)
        #print('    ' + method_decl.name + '(' + ', '.join(param_strings) + ')')
        return True

    def visit_VariableDeclaration(self, var_declaration):
        return True
        #for var_decl in var_declaration.variable_declarators:
        #    if type(var_declaration.type) is str:
        #        type_name = var_declaration.type
        #    else:
        #        type_name = var_declaration.type.name.value
        #    print('        ' + type_name + ' ' + var_decl.variable.name)
    
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
        doFmting = doFormatting(meth.arguments[1])
        
        lines = str(meth.linespan) + " | " + str(meth.lexspan)
        tag = meth.arguments[0]
        args = argToString(meth.arguments[1])
        
        # Computing code positions/lengths.
        constCodePart = argToString(meth.target)+"."+argToString(meth.name)
        oldCodeLen=meth.lexspan[1] - (meth.lexspan[0]-len(constCodePart))  
        codeStart=self.offset+meth.lexspan[0]-len(constCodePart)
        codeEnd=self.offset+meth.lexspan[1]
        
        # Find whitespace prefix of the method, if valid.
        wlPrefix=None
        codeTillMethod = self.content[:codeStart]
        nlIdx = str.rfind(codeTillMethod, "\n")
        if nlIdx!=-1:
            nlStr = self.content[nlIdx+1:codeStart]
            if re.match(r'^[\s\t]*$', nlStr):
                wlPrefix = nlStr
        
        # Old logging code part
        codePart = self.content[codeStart:codeEnd]
        
        # Reconstructing text representation for log statement if no formatting change is needed.
        newCode = ""
        if not doFmting:
            if hasException:
                newCode = "Log." + meth.name + "(" + argToString(tag) + ", " + args + ", "+argToString(meth.arguments[2])+");"
            else:
                newCode = "Log." + meth.name + "(" + argToString(tag) + ", " + args + ");"
            return True
        
        # Formating change is required here.
        # If there is some white space prefix detected and formating string is longer than 100 characters
        # split it.
        fmtObj = toFmtStr(meth.arguments[1])
        fmtString = str(fmtObj)
        if len(fmtString) > 100 and wlPrefix!=None:
            fmtString = ""+fmtObj.fmtStr+", \n"+wlPrefix+(2*4*" ")+((", " + "\n" + wlPrefix+(2*4*" ")).join(fmtObj.args))
        
        # Create a formating string representation.
        if hasException:
            newCode = "Log." + self.tranTable[meth.name] + "(" + argToString(tag) + ", " + argToString(meth.arguments[2]) + ", " + fmtString + ");"
        else:
            newCode = "Log." + self.tranTable[meth.name] + "(" + argToString(tag) + ", " + fmtString + ");"
        newCodeLen = len(newCode)
        
        # Change the content, replace with the new version.
        newContent = self.content[:codeStart] + newCode + self.content[codeEnd:]
        self.content = newContent
        self.offset += newCodeLen - oldCodeLen 
        self.statementsChanged+=1
        
        # Debugging statement.
        #print lines + " " + newCode
        #print meth
        return True
    
def getBeforeNewLine(s):
    '''Returns string after last new line character'''
    pass
    
class FmtingObj(object):
    '''Object represents formatting string representation for string concatenation'''
    fmtStr=""
    args=[]
    
    def __init__(self, fmt, arg):
        self.fmtStr = fmt
        self.args = arg
    def __str__(self):
        return ""+self.fmtStr+", "+(", ".join(self.args))

def doFormatting(arg):
    '''Whether to change to formatting string representation. Root should be provided.
    Returns true if there is a string concatenation.'''
    if arg==None: return False
    if isinstance(arg, m.Literal): return False
    if isinstance(arg, m.Name):    return False
    if isinstance(arg, m.MethodInvocation): return False
    if isinstance(arg, m.Additive): return True
    return False

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

def toFmtStr(arg):
    '''Converts recursive lexical representation to the formatting string representation recursively.'''
    if arg == None: 
        return FmtingObj('""', [])
    
    if isinstance(arg, str):
        return FmtingObj('"%s"', [arg])
    
    if isinstance(arg, m.Literal):
        return FmtingObj(escapeFmtStr(arg.value), [])
     
    if isinstance(arg, m.Name):
        return FmtingObj('"%s"', [arg.value])
    
    # Recursive constructions.
    # Additive is a child of the BinaryExpression, special case of joining
    # text literals. 
    if isinstance(arg, m.Additive) and arg.operator=="+" and isString(arg):
        lfmt = toFmtStr(arg.lhs)
        rfmt = toFmtStr(arg.rhs)
        return FmtingObj(lfmt.fmtStr[:-1] + rfmt.fmtStr[1:], lfmt.args + rfmt.args)
    
    if isinstance(arg, m.MethodInvocation):
        ret=""
        if arg.target!=None:
            ret+=argToString(arg.target) + "."
        ret+=arg.name + "("
        newArgs = [argToString(x) for x in arg.arguments]
        ret+= ", ".join(newArgs)
        ret+=")"
        return FmtingObj('"%s"', [ret])
    
    if isinstance(arg, m.Conditional):
        ret = "("
        ret += argToString(arg.predicate) + " ? "
        ret += argToString(arg.if_true) + " : "
        ret += argToString(arg.if_false) + ")"
        return FmtingObj('"%s"', [ret])
    
    if isinstance(arg, m.BinaryExpression):
        ret = "("
        ret += "(" + argToString(arg.lhs) + ") "
        ret += arg.operator
        ret += " (" + argToString(arg.rhs) + ")"
        ret += ")"
        return FmtingObj('"%s"', [ret])
    
    if isinstance(arg, m.ArrayAccess):
        ret = argToString(arg.target) + "[" + argToString(arg.index) + "]"
        return FmtingObj('"%s"', [ret])
    
    if isinstance(arg, m.FieldAccess):
        ret = argToString(arg.target) + "." + argToString(arg.name)
        return FmtingObj('"%s"', [ret])
    
    if isinstance(arg, m.Unary):
        ret = argToString(arg.sign) + argToString(arg.expression)
        return FmtingObj('"%s"', [ret])
        
    if isinstance(arg, m.Cast):
        ret = "(" + argToString(arg.target) + ")" + argToString(arg.expression)
        return FmtingObj('"%s"', [ret])
    
    raise Exception("Sorry, not working fmt, obj=" + str(arg))
    pass
       
# Main executable code
if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Log statements formating string converter.', formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    parser.add_argument('-i','--in-place',  help='Overwrites provided file with the new content', required=False, default=False, dest='inplace')
    parser.add_argument('-o','--out-file',  help='Output file to write output to', required=False, default=None, dest='outfile')
    parser.add_argument('-e','--echo',      help='Writes output to the standard output', required=False, default=False)
    parser.add_argument('-v','--verbose',   help='Writes output to the standard output', required=False, default=0, type=int)
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
        with open(args.file, 'r') as content_file:
            v.content = content_file.read()
        
        tree = p.parse_file(args.file)
        tree.accept(v)
        
        # If here, probably no exception occurred.
        if args.echo:
            print v.content
        if args.outfile != None and len(args.outfile)>0 and v.statementsChanged>0:
            with open(args.outfile, 'w') as f:
                f.write(v.content)
        if args.inplace and v.statementsChanged>0:
            with open(args.file, 'w') as f:
                f.write(v.content)
                
        if args.verbose>0:
            print " [-] Processing finished, changed=%d" % v.statementsChanged
    except Exception as e:
        print "    Error occurred! file[%s]" % (args.file), e
        if args.verbose>1:
            print '-'*60
            traceback.print_exc(file=sys.stdout)
            print '-'*60
        sys.exit(1)
        
