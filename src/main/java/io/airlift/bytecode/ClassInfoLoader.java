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
/***
 * ASM tests
 * Copyright (c) 2002-2005 France Telecom
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holders nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */
package io.airlift.bytecode;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles.Lookup;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static io.airlift.bytecode.ParameterizedType.typeFromPathName;
import static java.util.Objects.requireNonNull;

public class ClassInfoLoader
{
    public static ClassInfoLoader createClassInfoLoader(ClassDefinition classDefinition, Lookup lookup)
    {
        ClassNode classNode = new ClassNode();
        classDefinition.visit(classNode);

        return new ClassInfoLoader(ImmutableMap.of(classDefinition.getType(), classNode), ImmutableMap.of(), new LookupLoader(lookup), true);
    }

    public static ClassInfoLoader createClassInfoLoader(Iterable<ClassDefinition> classDefinitions, ClassLoader classLoader)
    {
        ImmutableMap.Builder<ParameterizedType, ClassNode> classNodes = ImmutableMap.builder();
        for (ClassDefinition classDefinition : classDefinitions) {
            ClassNode classNode = new ClassNode();
            classDefinition.visit(classNode);
            classNodes.put(classDefinition.getType(), classNode);
        }
        return new ClassInfoLoader(classNodes.build(), ImmutableMap.of(), new ClassLoaderLoader(classLoader), true);
    }

    private final Map<ParameterizedType, ClassNode> classNodes;
    private final Map<ParameterizedType, byte[]> bytecodes;
    private final Loader loader;
    private final Map<ParameterizedType, ClassInfo> classInfoCache = new HashMap<>();
    private final boolean loadMethodNodes;

    private ClassInfoLoader(Map<ParameterizedType, ClassNode> classNodes, Map<ParameterizedType, byte[]> bytecodes, Loader loader, boolean loadMethodNodes)
    {
        this.classNodes = ImmutableMap.copyOf(classNodes);
        this.bytecodes = ImmutableMap.copyOf(bytecodes);
        this.loader = loader;
        this.loadMethodNodes = loadMethodNodes;
    }

    public ClassInfo loadClassInfo(ParameterizedType type)
    {
        ClassInfo classInfo = classInfoCache.get(type);
        if (classInfo == null) {
            classInfo = readClassInfoQuick(type);
            classInfoCache.put(type, classInfo);
        }
        return classInfo;
    }

    private ClassInfo readClassInfoQuick(ParameterizedType type)
    {
        // check for user supplied class node
        ClassNode classNode = classNodes.get(type);
        if (classNode != null) {
            return new ClassInfo(this, classNode);
        }

        // check for user supplied byte code
        ClassReader classReader;
        byte[] bytecode = bytecodes.get(type);
        if (bytecode != null) {
            classReader = new ClassReader(bytecode);
        }
        else {
            // load class file from class loader
            classReader = loader.createByteCodeClassReader(type)
                    .orElse(null);
            if (classReader == null) {
                // load class directly and extract class info from loaded class
                return new ClassInfo(this, loader.loadClass(type));
            }
        }

        if (loadMethodNodes) {
            // slower version that loads all operations
            classNode = new ClassNode();
            classReader.accept(new CheckClassAdapter(classNode, false), ClassReader.SKIP_DEBUG);

            return new ClassInfo(this, classNode);
        }
        else {
            // optimized version
            int header = classReader.header;
            int access = classReader.readUnsignedShort(header);

            char[] buf = new char[2048];

            // read super class name
            int superClassIndex = classReader.getItem(classReader.readUnsignedShort(header + 4));
            ParameterizedType superClass;
            if (superClassIndex == 0) {
                superClass = null;
            }
            else {
                superClass = typeFromPathName(classReader.readUTF8(superClassIndex, buf));
            }

            // read each interface name
            int interfaceCount = classReader.readUnsignedShort(header + 6);
            ImmutableList.Builder<ParameterizedType> interfaces = ImmutableList.builder();
            header += 8;
            for (int i = 0; i < interfaceCount; ++i) {
                interfaces.add(typeFromPathName(classReader.readClass(header, buf)));
                header += 2;
            }
            return new ClassInfo(this, type, access, superClass, interfaces.build(), null);
        }
    }

    private interface Loader
    {
        Optional<ClassReader> createByteCodeClassReader(ParameterizedType type);

        Class<?> loadClass(ParameterizedType type);
    }

    private static class LookupLoader
            implements Loader
    {
        private final Lookup lookup;

        public LookupLoader(Lookup lookup)
        {
            this.lookup = requireNonNull(lookup, "lookup is null");
        }

        @Override
        public Optional<ClassReader> createByteCodeClassReader(ParameterizedType type)
        {
            return Optional.empty();
        }

        @Override
        public Class<?> loadClass(ParameterizedType type)
        {
            try {
                return lookup.findClass(type.getJavaClassName());
            }
            catch (ClassNotFoundException | IllegalAccessException e) {
                throw new RuntimeException("Class not found " + type, e);
            }
        }
    }

    private static class ClassLoaderLoader
            implements Loader
    {
        private final ClassLoader classLoader;

        public ClassLoaderLoader(ClassLoader classLoader)
        {
            this.classLoader = requireNonNull(classLoader, "classLoader is null");
        }

        @Override
        public Optional<ClassReader> createByteCodeClassReader(ParameterizedType type)
        {
            String classFileName = type.getClassName() + ".class";
            try (InputStream inputStream = classLoader.getResourceAsStream(classFileName)) {
                if (inputStream == null) {
                    return Optional.empty();
                }
                return Optional.of(new ClassReader(inputStream));
            }
            catch (IOException ignored) {
                return Optional.empty();
            }
        }

        @Override
        public Class<?> loadClass(ParameterizedType type)
        {
            try {
                return classLoader.loadClass(type.getJavaClassName());
            }
            catch (ClassNotFoundException e) {
                throw new RuntimeException("Class not found " + type, e);
            }
        }
    }
}
