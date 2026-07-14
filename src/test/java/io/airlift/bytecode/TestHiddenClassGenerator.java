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
import io.airlift.bytecode.ClassDataBinder.BoundMethodHandle;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.lang.invoke.MethodHandle;
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
import static io.airlift.bytecode.HiddenClassGenerator.hiddenClassGenerator;
import static io.airlift.bytecode.Parameter.arg;
import static io.airlift.bytecode.ParameterizedType.type;
import static io.airlift.bytecode.expression.BytecodeExpressions.add;
import static io.airlift.bytecode.expression.BytecodeExpressions.constantClassData;
import static io.airlift.bytecode.expression.BytecodeExpressions.constantClassDataAt;
import static io.airlift.bytecode.expression.BytecodeExpressions.constantString;
import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodType.methodType;
import static java.nio.file.Files.createTempDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestHiddenClassGenerator
{
    @Test
    void testClassDataConstants()
            throws Exception
    {
        ClassDefinition classDefinition = new ClassDefinition(
                a(PUBLIC, FINAL),
                "io/airlift/bytecode/ClassDataExample",
                type(Object.class));

        // load the entire class data list
        classDefinition.declareMethod(a(PUBLIC, STATIC), "data", type(List.class))
                .getBody()
                .append(constantClassData(List.class).ret());

        // load a single element of the class data
        classDefinition.declareMethod(a(PUBLIC, STATIC), "message", type(String.class))
                .getBody()
                .append(constantClassDataAt(0, String.class).ret());

        // invoke a MethodHandle bound through the class data
        Parameter argA = arg("a", int.class);
        Parameter argB = arg("b", int.class);
        classDefinition.declareMethod(a(PUBLIC, STATIC), "add", type(int.class), ImmutableList.of(argA, argB))
                .getBody()
                .append(constantClassDataAt(1, MethodHandle.class)
                        .invoke("invokeExact", int.class, argA, argB)
                        .ret());

        MethodHandle addExact = lookup().findStatic(Math.class, "addExact", methodType(int.class, int.class, int.class));
        List<Object> classData = ImmutableList.of("hello", addExact);

        Class<?> clazz = hiddenClassGenerator(lookup())
                .defineHiddenClass(classDefinition, Object.class, Optional.of(classData));

        assertThat(clazz.getMethod("data").invoke(null)).isSameAs(classData);
        assertThat(clazz.getMethod("message").invoke(null)).isEqualTo("hello");
        assertThat(clazz.getMethod("add", int.class, int.class).invoke(null, 13, 42)).isEqualTo(55);
    }

    @Test
    void testClassDataBinder()
            throws Exception
    {
        ClassDefinition classDefinition = new ClassDefinition(
                a(PUBLIC, FINAL),
                "io/airlift/bytecode/BinderExample",
                type(Object.class));

        ClassDataBinder binder = new ClassDataBinder();
        String message = "hello";
        MethodHandle addExact = lookup().findStatic(Math.class, "addExact", methodType(int.class, int.class, int.class));

        classDefinition.declareMethod(a(PUBLIC, STATIC), "message", type(String.class))
                .getBody()
                .append(binder.bind(message, String.class).ret());

        // binding the same object again reuses its class data slot
        classDefinition.declareMethod(a(PUBLIC, STATIC), "sameMessage", type(String.class))
                .getBody()
                .append(binder.bind(message, String.class).ret());

        Parameter argA = arg("a", int.class);
        Parameter argB = arg("b", int.class);
        classDefinition.declareMethod(a(PUBLIC, STATIC), "add", type(int.class), ImmutableList.of(argA, argB))
                .getBody()
                .append(binder.bind(addExact, MethodHandle.class)
                        .invoke("invokeExact", int.class, argA, argB)
                        .ret());

        assertThat(binder.getBindings()).containsExactly(message, addExact);

        Class<?> clazz = hiddenClassGenerator(lookup())
                .defineHiddenClass(classDefinition, Object.class, binder);

        assertThat(clazz.getMethod("message").invoke(null)).isSameAs(message);
        assertThat(clazz.getMethod("sameMessage").invoke(null)).isSameAs(message);
        assertThat(clazz.getMethod("add", int.class, int.class).invoke(null, 13, 42)).isEqualTo(55);
    }

    @Test
    void testBoundMethodHandle()
            throws Exception
    {
        ClassDefinition classDefinition = new ClassDefinition(
                a(PUBLIC, FINAL),
                "io/airlift/bytecode/BoundHandleExample",
                type(Object.class));

        ClassDataBinder binder = new ClassDataBinder();
        MethodHandle addExact = lookup().findStatic(Math.class, "addExact", methodType(int.class, int.class, int.class));

        Parameter argA = arg("a", int.class);
        Parameter argB = arg("b", int.class);
        classDefinition.declareMethod(a(PUBLIC, STATIC), "add", type(int.class), ImmutableList.of(argA, argB))
                .getBody()
                .append(binder.bindHandle(addExact).invoke(argA, argB).ret());

        // binding the same handle again reuses its class data slot
        classDefinition.declareMethod(a(PUBLIC, STATIC), "addAgain", type(int.class), ImmutableList.of(argA, argB))
                .getBody()
                .append(binder.bindHandle(addExact).invoke(argA, argB).ret());

        // the raw handle can be loaded for callers that place it on the stack themselves
        classDefinition.declareMethod(a(PUBLIC, STATIC), "handle", type(MethodHandle.class))
                .getBody()
                .append(binder.bindHandle(addExact).handle().ret());

        assertThat(binder.getBindings()).containsExactly(addExact);

        // invocation arguments are checked against the handle type at generation time
        BoundMethodHandle bound = binder.bindHandle(addExact);
        assertThat(bound.type()).isEqualTo(methodType(int.class, int.class, int.class));
        assertThatThrownBy(() -> bound.invoke(argA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected 2 arguments");
        assertThatThrownBy(() -> bound.invoke(argA, constantString("wrong")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected argument 1 to have type");

        Class<?> clazz = hiddenClassGenerator(lookup())
                .defineHiddenClass(classDefinition, Object.class, binder);

        assertThat(clazz.getMethod("add", int.class, int.class).invoke(null, 13, 42)).isEqualTo(55);
        assertThat(clazz.getMethod("addAgain", int.class, int.class).invoke(null, 20, 22)).isEqualTo(42);
        assertThat(clazz.getMethod("handle").invoke(null)).isSameAs(addExact);
    }

    @Test
    void testGenerator()
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

            Class<?> clazz = hiddenClassGenerator(lookup())
                    .fakeLineNumbers(true)
                    .runAsmVerifier(true)
                    .dumpRawBytecode(true)
                    .outputTo(writer)
                    .dumpClassFilesTo(tempDir)
                    .defineHiddenClass(classDefinition, Object.class, Optional.of("class data"));

            Method add = clazz.getMethod("add", int.class, int.class);
            assertThat(add.invoke(null, 13, 42)).isEqualTo(55);

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
    void testOmitDebugInfo()
            throws Exception
    {
        ClassDefinition classDefinition = new ClassDefinition(
                a(PUBLIC, FINAL),
                "io/airlift/bytecode/DebugExample",
                type(Object.class));
        classDefinition.visitSource("Example.java", null);

        Parameter argA = arg("a", int.class);
        Parameter argB = arg("b", int.class);

        MethodDefinition method = classDefinition.declareMethod(
                a(PUBLIC, STATIC),
                "add",
                type(int.class),
                ImmutableList.of(argA, argB));

        Variable sum = method.getScope().declareVariable(int.class, "sum");
        method.getBody()
                .visitLineNumber(42)
                .append(sum.set(add(argA, argB)))
                .append(sum.ret());

        StringWriter writer = new StringWriter();
        Class<?> clazz = hiddenClassGenerator(lookup())
                .omitDebugInfo(true)
                .runAsmVerifier(true)
                .dumpRawBytecode(true)
                .outputTo(writer)
                .defineHiddenClass(classDefinition, Object.class, Optional.empty());

        assertThat(clazz.getMethod("add", int.class, int.class).invoke(null, 13, 42)).isEqualTo(55);
        assertThat(writer.toString())
                .doesNotContain("compiled from")
                .doesNotContain("LINENUMBER")
                .doesNotContain("LOCALVARIABLE");
    }
}
