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

import java.io.Writer;
import java.lang.invoke.MethodHandles.Lookup;
import java.nio.file.Path;
import java.util.Optional;

import static io.airlift.bytecode.ClassInfoLoader.createClassInfoLoader;

public class HiddenClassGenerator
{
    private final Lookup lookup;
    private final ByteCodeGenerator byteCodeGenerator;

    public static HiddenClassGenerator hiddenClassGenerator(Lookup lookup)
    {
        return new HiddenClassGenerator(lookup, ByteCodeGenerator.byteCodeGenerator());
    }

    private HiddenClassGenerator(Lookup lookup, ByteCodeGenerator byteCodeGenerator)
    {
        this.lookup = lookup;
        this.byteCodeGenerator = byteCodeGenerator;
    }

    public HiddenClassGenerator fakeLineNumbers(boolean fakeLineNumbers)
    {
        return new HiddenClassGenerator(lookup, byteCodeGenerator.fakeLineNumbers(fakeLineNumbers));
    }

    public HiddenClassGenerator runAsmVerifier(boolean runAsmVerifier)
    {
        return new HiddenClassGenerator(lookup, byteCodeGenerator.runAsmVerifier(runAsmVerifier ? new LookupClassLoader(lookup) : null));
    }

    public HiddenClassGenerator dumpRawBytecode(boolean dumpRawBytecode)
    {
        return new HiddenClassGenerator(lookup, byteCodeGenerator.dumpRawBytecode(dumpRawBytecode));
    }

    public HiddenClassGenerator outputTo(Writer output)
    {
        return new HiddenClassGenerator(lookup, byteCodeGenerator.outputTo(output));
    }

    public HiddenClassGenerator dumpClassFilesTo(Path dumpClassPath)
    {
        return dumpClassFilesTo(Optional.of(dumpClassPath));
    }

    public HiddenClassGenerator dumpClassFilesTo(Optional<Path> dumpClassPath)
    {
        return new HiddenClassGenerator(lookup, byteCodeGenerator.dumpClassFilesTo(dumpClassPath));
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
}
