/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.bytecode;

import com.google.common.collect.ImmutableList;
import com.google.common.reflect.Reflection;
import io.airlift.bytecode.instruction.BootstrapMethod;

import java.io.Writer;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import static io.airlift.bytecode.Access.PRIVATE;
import static io.airlift.bytecode.Access.STATIC;
import static io.airlift.bytecode.ClassInfoLoader.createClassInfoLoader;
import static io.airlift.bytecode.ParameterizedType.type;
import static io.airlift.bytecode.expression.BytecodeExpressions.constantClass;
import static io.airlift.bytecode.expression.BytecodeExpressions.constantString;
import static io.airlift.bytecode.expression.BytecodeExpressions.newArray;

public class SingleClassGenerator
{
    private final Lookup lookup;
    private final ByteCodeGenerator byteCodeGenerator;

    public static SingleClassGenerator singleClassGenerator(Lookup lookup)
    {
        return new SingleClassGenerator(lookup, ByteCodeGenerator.byteCodeGenerator());
    }

    private SingleClassGenerator(Lookup lookup, ByteCodeGenerator byteCodeGenerator)
    {
        this.lookup = lookup;
        this.byteCodeGenerator = byteCodeGenerator;
    }

    public SingleClassGenerator fakeLineNumbers(boolean fakeLineNumbers)
    {
        return new SingleClassGenerator(lookup, byteCodeGenerator.fakeLineNumbers(fakeLineNumbers));
    }

    public SingleClassGenerator runAsmVerifier(boolean runAsmVerifier)
    {
        return new SingleClassGenerator(lookup, byteCodeGenerator.runAsmVerifier(runAsmVerifier ? new LookupClassLoader(lookup) : null));
    }

    public SingleClassGenerator dumpRawBytecode(boolean dumpRawBytecode)
    {
        return new SingleClassGenerator(lookup, byteCodeGenerator.dumpRawBytecode(dumpRawBytecode));
    }

    public SingleClassGenerator outputTo(Writer output)
    {
        return new SingleClassGenerator(lookup, byteCodeGenerator.outputTo(output));
    }

    public SingleClassGenerator dumpClassFilesTo(Path dumpClassPath)
    {
        return dumpClassFilesTo(Optional.of(dumpClassPath));
    }

    public SingleClassGenerator dumpClassFilesTo(Optional<Path> dumpClassPath)
    {
        return new SingleClassGenerator(lookup, byteCodeGenerator.dumpClassFilesTo(dumpClassPath));
    }

    public <T> Class<? extends T> defineStandardClass(ClassDefinition classDefinition, Class<T> superType, Optional<Object> classData)
    {
        ClassInfoLoader classInfoLoader = createClassInfoLoader(classDefinition, lookup);
        byte[] bytecode = byteCodeGenerator.generateByteCode(classInfoLoader, classDefinition);

        Class<?> clazz = StandardClassLoader.defineSingleClass(lookup, classDefinition.getType().getJavaClassName(), bytecode, classData);

        try {
            Reflection.initialize(clazz);
        }
        catch (VerifyError e) {
            throw new RuntimeException(e);
        }
        return clazz.asSubclass(superType);
    }

    public <T> Class<? extends T> defineHiddenClass(ClassDefinition classDefinition, Class<T> superType, Optional<Object> classData)
    {
        ClassInfoLoader classInfoLoader = createClassInfoLoader(classDefinition, lookup);
        byte[] bytecode = byteCodeGenerator.generateByteCode(classInfoLoader, classDefinition);

        Lookup definedClassLookup;
        try {
            if (classData.isEmpty()) {
                definedClassLookup = lookup.defineHiddenClass(bytecode, true);
            }
            else {
                definedClassLookup = lookup.defineHiddenClassWithClassData(bytecode, classData.get(), true);
            }
        }
        catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return definedClassLookup.lookupClass().asSubclass(superType);
    }

    /**
     * Creates a bootstrap method that can load class data for a standard class. This is has the same
     * behavior as {@link java.lang.invoke.MethodHandles#classDataAt(Lookup, String, Class, int)} except
     * that the it works for standard classed created by {@link #defineStandardClass(ClassDefinition, Class, Optional)}
     */
    public static BootstrapMethod declareStandardClassDataAtBootstrapMethod(ClassDefinition definition)
    {
        // Generate a bootstrap method that loads constants from the StandardClassLoader
        // The generated code uses reflection to call StandardClassLoader.classDataAt(),
        // so the generated code does not need access to the StandardClassLoader class
        Parameter lookupParam = Parameter.arg("lookup", Lookup.class);
        Parameter nameParam = Parameter.arg("name", String.class);
        Parameter typeParam = Parameter.arg("type", Class.class);
        Parameter indexParam = Parameter.arg("index", int.class);

        String randomName = "$$bootstrap_" + ThreadLocalRandom.current().nextInt(1_000_000);
        MethodDefinition bootstrap = definition.declareMethod(EnumSet.of(PRIVATE, STATIC), randomName, type(Object.class), lookupParam, nameParam, typeParam, indexParam);
        bootstrap.addException(Throwable.class);
        Scope scope = bootstrap.getScope();
        Variable classLoader = scope.declareVariable("classLoader", bootstrap.getBody(), lookupParam.invoke("lookupClass", Class.class).invoke("getClassLoader", ClassLoader.class));
        bootstrap
                .getBody()
                .append(classLoader.invoke("getClass", Class.class)
                        .invoke("getMethod", Method.class, constantString("classDataAt"), newArray(type(Class[].class), ImmutableList.of(constantClass(int.class))))
                        .invoke("invoke", Object.class, classLoader.cast(Object.class), newArray(type(Object[].class), ImmutableList.of(indexParam.cast(Integer.class))))
                        .ret());

        return BootstrapMethod.from(bootstrap);
    }

    private static class LookupClassLoader
            extends ClassLoader
    {
        private final Lookup lookup;

        public LookupClassLoader(Lookup lookup)
        {
            this.lookup = lookup;
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException
        {
            try {
                return lookup.findClass(name);
            }
            catch (IllegalAccessException e) {
                throw new ClassNotFoundException(name, e);
            }
        }
    }

    public static class StandardClassLoader
            extends ClassLoader
    {
        public static Class<?> defineSingleClass(Lookup lookup, String className, byte[] bytecode, Optional<Object> classData)
        {
            return new StandardClassLoader(lookup.lookupClass().getClassLoader(), classData).defineClass(className, bytecode);
        }

        private final Object classData;

        private StandardClassLoader(ClassLoader parentClassLoader, Optional<Object> classData)
        {
            super(parentClassLoader);
            this.classData = classData.orElse(null);
        }

        private Class<?> defineClass(String className, byte[] bytecode)
        {
            return defineClass(className, bytecode, 0, bytecode.length);
        }

        public Object classData()
        {
            return classData;
        }

        public Object classDataAt(int index)
        {
            // this is the same behavior as Lookup.classDataAt
            @SuppressWarnings("unchecked") List<Object> classData = (List<Object>) classData();
            if (classData == null) {
                return null;
            }

            return classData.get(index);
        }
    }
}
