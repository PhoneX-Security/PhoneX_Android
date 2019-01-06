#!/usr/bin/env python2
"""
Reflection interface wrapper generator.

Uses Python yacc implementation with Java grammar to 
extract a public interface of a provided class.
This script is optimized to generate an interface
using reflection for particular class - pjsuaJNI.

@author Ph4r05
"""


import sys
import plyj.parser
import plyj.model as m

class ParamRecord(object):
    specifiers=''
    ptype=''
    name=''
    dimension=0
    pass

class MethodRecord(object):
    specifiers=''
    return_type=''
    name=''
    parameters=[]
    def __repr__(self):
        return str(self.specifiers) + ' ' + str(self.return_type) + ' ' + str(self.name) + '(' + ')'
    pass

def getMethodVar(meth):
    return "m_" + meth

# Definition of java primitive and wrapper types.
primitiveTypes = ['void', 'enum', 'byte', 'short', 'int',     'long', 'char',      'float', 'double', 'boolean']
wrapperTypes   = ['Void', 'Enum', 'Byte', 'Short', 'Integer', 'Long', 'Character', 'Float', 'Double', 'Boolean']

p = plyj.parser.Parser()
tree = p.parse_file(sys.argv[1])

tab="    "
methNames=[]
methods=[]

fieldBlock=['private static Class<?> cls;']
staticBlock=['cls = Class.forName("org.pjsip.pjsua.pjsuaJNI", true, classLoader);']
methodBlock=[]
deinitBlock=[]

# Currently this script supports only one main class, no
# subclasses/inner classes are supported.
for type_decl in tree.type_declarations:
    #print(type_decl.name)
    #if type_decl.extends is not None:
    #    print(' -> extending ' + type_decl.extends.name.value)
    #if len(type_decl.implements) is not 0:
    #    print(' -> implementing ' + ', '.join([type.name.value for type in type_decl.implements]))

    for method_decl in [decl for decl in type_decl.body if type(decl) is m.MethodDeclaration]:
        #print "Method: ", method_decl
        curMethod = MethodRecord()
        curMethod.name = method_decl.name
        curMethod.specifiers = ' '.join(method_decl.modifiers)
	
	# Skip private and protected methods, those are not
	# part of the public interface.
        if not ("public" in method_decl.modifiers):
            continue

        # Parsing input parameters.
        param_strings = []
        reflParams = []
        paramNames=[]
        for param in method_decl.parameters:
            curParam = ""
            curParam = " ".join(param.modifiers)
            if ((type(param.type) is str)):
                curParam += param.type + ' ' + param.variable.name
                reflParams.append(param.type+'.class')
                paramNames.append(param.variable.name)
            elif isinstance(param.type.name, str):
		curParam += param.type.name + ' ' + param.variable.name
	        curParam += "[]"*param.type.dimensions
                reflParams.append(param.type.name+"[]"*param.type.dimensions+'.class')
                paramNames.append(param.variable.name)
            else:
                curParam += param.type.name.value + ' ' + param.variable.name
                reflParams.append(param.type.name.value+'.class')
                paramNames.append(param.variable.name)
            param_strings.append(curParam)
 	
        # Parsing output parameter
        ret_string = ''
        ret_type = ''
        ret_dimension=0
        if ((type(method_decl.return_type) is str)):
            ret_string  += method_decl.return_type
            ret_type    += method_decl.return_type
        elif isinstance(method_decl.return_type.name, str):
            ret_string  += method_decl.return_type.name
            ret_type    += method_decl.return_type.name
            ret_dimension = method_decl.return_type.dimensions
	    if (method_decl.return_type.dimensions>0):
	        ret_string  += "[]"*method_decl.return_type.dimensions
        else:
            ret_string += method_decl.return_type.name.value
            ret_type   += method_decl.return_type.name.value
            ret_dimension = method_decl.return_type.dimensions
        curMethod.return_type = ret_string

	# Generating reflection wrapper.
        # Drop native in modifiers if present
        newModifiers = [x for x in method_decl.modifiers if x!="native"]
        methodDef = tab + ' '.join(newModifiers) + ' ' + ret_string + ' ' + method_decl.name + '(' + ', '.join(param_strings) + ')'
        #print methodDef
        
        # Method wrapper using reflection.
        methodParams=', '.join(paramNames)
        if not methodParams:
            methodParams="(Object[]) null"
        methodDef += "{\n"+(2*tab)+"try {\n"+(3*tab)
        curReflCall = getMethodVar(method_decl.name)+'.invoke(null, ' + methodParams + ');'
        
        # Handle returning a value from the reflection invocation. 
        if (("void"==ret_string) or ("null"==ret_string) or (not ret_string)):
            methodDef += curReflCall
        else:
            # If it is a primitive type, use wrapper types, but no array is allowed.
            if (ret_dimension==0):
                # If it is a primitive type, use conversion
                if (ret_type in primitiveTypes):
                    methodDef += 'return (' + (wrapperTypes[primitiveTypes.index(ret_type)]) + ')' + curReflCall 
                else:
                    methodDef += 'return (' + (ret_type) + ')' + curReflCall 
            else:
                # This is an array of something.
                methodDef += 'return (' + ret_string + ')' + curReflCall

        methodDef += "\n"+(2*tab)+"} catch(Exception excc) {\n"+(3*tab)
        methodDef += "Log.e(\"JNIW\", \"Exception in reflection call\", excc);\n"+(3*tab)
        methodDef += "throw new RuntimeException(\"JNIW-Exception in reflection call\", excc);\n"+(2*tab)
        methodDef += "}\n"+tab+"}"
        methodBlock.append(methodDef)

        # Static block.
        fieldBlock.append('Method ' + getMethodVar(method_decl.name) + ';')
	curStatic = getMethodVar(method_decl.name) + ' = cls.getMethod("' + method_decl.name + '", new Class<?>[] {'+(', '.join(reflParams))+'});'
        staticBlock.append(curStatic)	
        deinitBlock.append(getMethodVar(method_decl.name) + ' = null;')
        methods.append(curMethod)

# Generate class definition, internal static variables, initialization block.	
print "package org.pjsip.pjsua;\n"
print "import java.lang.reflect.Method;\n"
print "import net.phonex.legacy.utils.Log;"
print "public class pjsuaJNIW {\n"
print tab + (("\n"+tab+"private static ").join(fieldBlock))
print "\n"

# Initialization of the reflection code
print tab+"public static void initClass(ClassLoader classLoader){\n"+tab+"try{"
print (2*tab)+(("\n"+(2*tab)).join(staticBlock))
print (2*tab)+"Log.i(\"JNIW\", \"Reflection callback initialized\");"
print (2*tab)+"} catch(Exception exc){"
print (3*tab)+"Log.e(\"JNIW\", \"Exception in reflective initialization\", exc);"
print (2*tab)+"}\n" + tab + "}\n"

# Deinitialization of the reflection code - free class loader
print tab+"public static void deinitClass(){\n"+(2*tab)+"try{"
print (3*tab)+"cls=null;"
print (3*tab)+("\n"+(3*tab)).join(deinitBlock)
print (2*tab)+"} catch(Exception exc){"
print (3*tab)+"Log.e(\"JNIW\", \"Exception in reflective deinitialization\", exc);"
print (2*tab)+"}\n" + tab + "}\n"

# Dump reflection method wrappers.
print ("\n").join(methodBlock)
print "\n}\n"










