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

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static com.google.common.io.CharStreams.nullWriter;
import static io.airlift.bytecode.ParameterizedType.typeFromJavaClassName;
import static java.nio.file.Files.createDirectories;
import static java.util.Objects.requireNonNull;

class ByteCodeGenerator
{
    private final boolean fakeLineNumbers;
    private final ClassLoader runAsmVerifierClassLoader;
    private final boolean dumpRawBytecode;
    private final Writer output;
    private final Optional<Path> dumpClassPath;

    public static ByteCodeGenerator byteCodeGenerator()
    {
        return new ByteCodeGenerator(false, null, false, nullWriter(), Optional.empty());
    }

    private ByteCodeGenerator(
            boolean fakeLineNumbers,
            ClassLoader runAsmVerifierClassLoader,
            boolean dumpRawBytecode,
            Writer output,
            Optional<Path> dumpClassPath)
    {
        this.fakeLineNumbers = fakeLineNumbers;
        this.runAsmVerifierClassLoader = runAsmVerifierClassLoader;
        this.dumpRawBytecode = dumpRawBytecode;
        this.output = requireNonNull(output, "output is null");
        this.dumpClassPath = requireNonNull(dumpClassPath, "dumpClassPath is null");
    }

    public ByteCodeGenerator fakeLineNumbers(boolean fakeLineNumbers)
    {
        return new ByteCodeGenerator(fakeLineNumbers, runAsmVerifierClassLoader, dumpRawBytecode, output, dumpClassPath);
    }

    public ByteCodeGenerator runAsmVerifier(ClassLoader runAsmVerifierClassLoader)
    {
        return new ByteCodeGenerator(fakeLineNumbers, runAsmVerifierClassLoader, dumpRawBytecode, output, dumpClassPath);
    }

    public ByteCodeGenerator dumpRawBytecode(boolean dumpRawBytecode)
    {
        return new ByteCodeGenerator(fakeLineNumbers, runAsmVerifierClassLoader, dumpRawBytecode, output, dumpClassPath);
    }

    public ByteCodeGenerator outputTo(Writer output)
    {
        return new ByteCodeGenerator(fakeLineNumbers, runAsmVerifierClassLoader, dumpRawBytecode, output, dumpClassPath);
    }

    public ByteCodeGenerator dumpClassFilesTo(Optional<Path> dumpClassPath)
    {
        return new ByteCodeGenerator(fakeLineNumbers, runAsmVerifierClassLoader, dumpRawBytecode, output, dumpClassPath);
    }

    public byte[] generateByteCode(ClassInfoLoader classInfoLoader, ClassDefinition classDefinition)
    {
        ClassWriter writer = new SmartClassWriter(classInfoLoader);

        try {
            classDefinition.visit(fakeLineNumbers ? new AddFakeLineNumberClassVisitor(writer) : writer);
        }
        catch (IndexOutOfBoundsException | NegativeArraySizeException e) {
            StringWriter out = new StringWriter();
            classDefinition.visit(new TraceClassVisitor(null, new Textifier(), new PrintWriter(out)));
            throw new IllegalArgumentException("Error processing class definition:\n" + out, e);
        }

        byte[] bytecode;
        try {
            bytecode = writer.toByteArray();
        }
        catch (RuntimeException e) {
            throw new CompilationException("Error compiling class: " + classDefinition.getName(), e);
        }

        dumpClassPath.ifPresent(path -> {
            String className = classDefinition.getType().getJavaClassName();
            String name = typeFromJavaClassName(className).getClassName() + ".class";
            Path file = path.resolve(name).toAbsolutePath();
            try {
                createDirectories(file.getParent());
                Files.write(file, bytecode);
            }
            catch (IOException e) {
                throw new UncheckedIOException("Failed to write generated class file: " + file, e);
            }
        });

        if (dumpRawBytecode) {
            ClassReader classReader = new ClassReader(bytecode);
            classReader.accept(new TraceClassVisitor(new PrintWriter(output)), ClassReader.EXPAND_FRAMES);
        }

        if (runAsmVerifierClassLoader != null) {
            ClassReader reader = new ClassReader(bytecode);
            CheckClassAdapter.verify(reader, runAsmVerifierClassLoader, true, new PrintWriter(output));
        }

        return bytecode;
    }
}
