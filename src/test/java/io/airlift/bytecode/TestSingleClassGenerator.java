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
import io.airlift.bytecode.SingleClassGenerator.StandardClassLoader;
import io.airlift.bytecode.instruction.BootstrapMethod;
import org.testng.annotations.Test;

import java.io.StringWriter;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static com.google.common.io.MoreFiles.deleteRecursively;
import static com.google.common.io.RecursiveDeleteOption.ALLOW_INSECURE;
import static io.airlift.bytecode.Access.FINAL;
import static io.airlift.bytecode.Access.PUBLIC;
import static io.airlift.bytecode.Access.STATIC;
import static io.airlift.bytecode.Access.a;
import static io.airlift.bytecode.Parameter.arg;
import static io.airlift.bytecode.ParameterizedType.type;
import static io.airlift.bytecode.SingleClassGenerator.declareStandardClassDataAtBootstrapMethod;
import static io.airlift.bytecode.SingleClassGenerator.singleClassGenerator;
import static io.airlift.bytecode.expression.BytecodeExpressions.add;
import static io.airlift.bytecode.expression.BytecodeExpressions.constantDynamic;
import static java.lang.invoke.MethodHandles.lookup;
import static java.nio.file.Files.createTempDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

public class TestSingleClassGenerator
{
    @Test
    public void testGenerator()
            throws Exception
    {
        ClassDefinition classDefinition = new ClassDefinition(
                a(PUBLIC, FINAL),
                "io/airlift/bytecode/Example",
                type(Object.class));

        Parameter argA = arg("a", int.class);
        Parameter argB = arg("b", int.class);

        MethodDefinition method = classDefinition.declareMethod(
                a(PUBLIC, STATIC),
                "add",
                type(int.class),
                ImmutableList.of(argA, argB));

        method.getBody()
                .append(add(argA, argB))
                .retInt();

        Path tempDir = createTempDirectory("test");

        try {
            StringWriter writer = new StringWriter();

            Class<?> clazz = singleClassGenerator(lookup())
                    .fakeLineNumbers(true)
                    .runAsmVerifier(true)
                    .dumpRawBytecode(true)
                    .outputTo(writer)
                    .dumpClassFilesTo(tempDir)
                    .defineHiddenClass(classDefinition, Object.class, Optional.of("class data"));

            Method add = clazz.getMethod("add", int.class, int.class);
            assertEquals(add.invoke(null, 13, 42), 55);

            assertThat(writer.toString())
                    .contains("00002 I I  : I I  :     IADD")
                    .contains("public final class io/airlift/bytecode/Example {")
                    .contains("// declaration: int add(int, int)")
                    .contains("LINENUMBER 2002 L1");

            assertThat(tempDir.resolve("io/airlift/bytecode/Example.class")).isRegularFile();
        }
        finally {
            deleteRecursively(tempDir, ALLOW_INSECURE);
        }
    }

    @Test
    public void testStandardClassData()
            throws Exception
    {
        ClassDefinition classDefinition = new ClassDefinition(
                a(PUBLIC, FINAL),
                "io/airlift/bytecode/Example",
                type(Object.class));

        BootstrapMethod bootstrapMethod = declareStandardClassDataAtBootstrapMethod(classDefinition);

        classDefinition.declareMethod(a(PUBLIC, STATIC), "first", type(Object.class))
                .getBody()
                .append(constantDynamic("_", type(Object.class), bootstrapMethod, ImmutableList.of(0)).ret());

        classDefinition.declareMethod(a(PUBLIC, STATIC), "second", type(Object.class))
                .getBody()
                .append(constantDynamic("_", type(Object.class), bootstrapMethod, ImmutableList.of(1)).ret());

        classDefinition.declareMethod(a(PUBLIC, STATIC), "third", type(Object.class))
                .getBody()
                .append(constantDynamic("_", type(Object.class), bootstrapMethod, ImmutableList.of(2)).ret());

        List<String> classData = List.of("a", "b");
        Class<?> clazz = singleClassGenerator(lookup())
                .defineStandardClass(classDefinition, Object.class, Optional.of(classData));

        assertTrue(clazz.getClassLoader() instanceof StandardClassLoader);
        StandardClassLoader standardClassLoader = (StandardClassLoader) clazz.getClassLoader();
        assertSame(standardClassLoader.classData(), classData);
        assertEquals(standardClassLoader.classDataAt(0), "a");
        assertEquals(standardClassLoader.classDataAt(1), "b");

        assertEquals(clazz.getMethod("first").invoke(null), "a");
        assertEquals(clazz.getMethod("second").invoke(null), "b");

        clazz = singleClassGenerator(lookup())
                .defineStandardClass(classDefinition, Object.class, Optional.empty());

        assertNull(clazz.getMethod("first").invoke(null));
        assertNull(clazz.getMethod("second").invoke(null));
    }

    @Test
    public void testHiddenClassData()
            throws Exception
    {
        ClassDefinition classDefinition = new ClassDefinition(
                a(PUBLIC, FINAL),
                "io/airlift/bytecode/Example",
                type(Object.class));

        BootstrapMethod bootstrapMethod = BootstrapMethod.from(MethodHandles.class.getMethod("classDataAt", MethodHandles.Lookup.class, String.class, Class.class, int.class));

        classDefinition.declareMethod(a(PUBLIC, STATIC), "first", type(Object.class))
                .getBody()
                .append(constantDynamic("_", type(Object.class), bootstrapMethod, ImmutableList.of(0)).ret());

        classDefinition.declareMethod(a(PUBLIC, STATIC), "second", type(Object.class))
                .getBody()
                .append(constantDynamic("_", type(Object.class), bootstrapMethod, ImmutableList.of(1)).ret());

        classDefinition.declareMethod(a(PUBLIC, STATIC), "third", type(Object.class))
                .getBody()
                .append(constantDynamic("_", type(Object.class), bootstrapMethod, ImmutableList.of(2)).ret());

        Class<?> clazz = singleClassGenerator(lookup())
                .defineHiddenClass(classDefinition, Object.class, Optional.of(List.of("a", "b")));

        assertEquals(clazz.getMethod("first").invoke(null), "a");
        assertEquals(clazz.getMethod("second").invoke(null), "b");

        clazz = singleClassGenerator(lookup())
                .defineHiddenClass(classDefinition, Object.class, Optional.empty());

        assertNull(clazz.getMethod("first").invoke(null));
        assertNull(clazz.getMethod("second").invoke(null));
    }
}
