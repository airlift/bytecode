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
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.Reflection;

import java.io.Writer;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.google.common.collect.MoreCollectors.onlyElement;
import static io.airlift.bytecode.ClassInfoLoader.createClassInfoLoader;
import static java.util.Objects.requireNonNull;

public class ClassGenerator
{
    private final DynamicClassLoader classLoader;
    private final ByteCodeGenerator byteCodeGenerator;

    public static ClassGenerator classGenerator(ClassLoader parentClassLoader)
    {
        return classGenerator(parentClassLoader, ImmutableMap.of());
    }

    public static ClassGenerator classGenerator(ClassLoader parentClassLoader, Map<Long, MethodHandle> callSiteBindings)
    {
        return classGenerator(new DynamicClassLoader(parentClassLoader, callSiteBindings));
    }

    public static ClassGenerator classGenerator(DynamicClassLoader classLoader)
    {
        return new ClassGenerator(classLoader, ByteCodeGenerator.byteCodeGenerator());
    }

    private ClassGenerator(DynamicClassLoader classLoader, ByteCodeGenerator byteCodeGenerator)
    {
        this.classLoader = requireNonNull(classLoader, "classLoader is null");
        this.byteCodeGenerator = requireNonNull(byteCodeGenerator, "byteCodeGenerator is null");
    }

    public ClassGenerator fakeLineNumbers(boolean fakeLineNumbers)
    {
        return new ClassGenerator(classLoader, byteCodeGenerator.fakeLineNumbers(fakeLineNumbers));
    }

    public ClassGenerator runAsmVerifier(boolean runAsmVerifier)
    {
        return new ClassGenerator(classLoader, byteCodeGenerator.runAsmVerifier(runAsmVerifier ? classLoader : null));
    }

    public ClassGenerator dumpRawBytecode(boolean dumpRawBytecode)
    {
        return new ClassGenerator(classLoader, byteCodeGenerator.dumpRawBytecode(dumpRawBytecode));
    }

    public ClassGenerator outputTo(Writer output)
    {
        return new ClassGenerator(classLoader, byteCodeGenerator.outputTo(output));
    }

    public ClassGenerator dumpClassFilesTo(Path dumpClassPath)
    {
        return dumpClassFilesTo(Optional.of(dumpClassPath));
    }

    public ClassGenerator dumpClassFilesTo(Optional<Path> dumpClassPath)
    {
        return new ClassGenerator(classLoader, byteCodeGenerator.dumpClassFilesTo(dumpClassPath));
    }

    public <T> Class<? extends T> defineClass(ClassDefinition classDefinition, Class<T> superType)
    {
        Map<String, Class<?>> classes = defineClasses(ImmutableList.of(classDefinition));
        return classes.values().stream().collect(onlyElement()).asSubclass(superType);
    }

    public Map<String, Class<?>> defineClasses(List<ClassDefinition> classDefinitions)
    {
        ClassInfoLoader classInfoLoader = createClassInfoLoader(classDefinitions, classLoader);
        Map<String, byte[]> bytecodes = new LinkedHashMap<>();

        for (ClassDefinition classDefinition : classDefinitions) {
            byte[] bytecode = byteCodeGenerator.generateByteCode(classInfoLoader, classDefinition);
            bytecodes.put(classDefinition.getType().getJavaClassName(), bytecode);
        }

        Map<String, Class<?>> classes = classLoader.defineClasses(bytecodes);

        try {
            for (Class<?> clazz : classes.values()) {
                Reflection.initialize(clazz);
            }
        }
        catch (VerifyError e) {
            throw new RuntimeException(e);
        }

        return classes;
    }
}
