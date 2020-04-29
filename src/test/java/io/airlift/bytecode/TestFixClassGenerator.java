package io.airlift.bytecode;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import org.testng.annotations.Test;

import java.io.InputStream;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.nio.file.Path;

import static com.google.common.io.MoreFiles.deleteRecursively;
import static com.google.common.io.RecursiveDeleteOption.ALLOW_INSECURE;
import static io.airlift.bytecode.Access.FINAL;
import static io.airlift.bytecode.Access.PUBLIC;
import static io.airlift.bytecode.Access.STATIC;
import static io.airlift.bytecode.Access.a;
import static io.airlift.bytecode.ClassGenerator.classGenerator;
import static io.airlift.bytecode.Parameter.arg;
import static io.airlift.bytecode.ParameterizedType.type;
import static io.airlift.bytecode.expression.BytecodeExpressions.add;
import static java.nio.file.Files.createTempDirectory;
import static org.testng.Assert.assertEquals;

/**
 * @author richie
 * @version 10.0
 * Created by richie on 2020/4/29
 */
public class TestFixClassGenerator {
    @Test
    public void testGenerator() throws Exception {
        InputStream in = TestFixClassGenerator.class.getResourceAsStream("/test/Example.class");
        byte[] bytes = ByteStreams.toByteArray(in);
        in.close();
        ClassDefinition classDefinition = new ClassDefinition(
                a(PUBLIC, FINAL),
                "test/Example",
                type(Object.class)).withClassBytes(bytes);

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

            Class<?> clazz = classGenerator(getClass().getClassLoader())
                    .fakeLineNumbers(true)
                    .runAsmVerifier(true)
                    .dumpRawBytecode(true)
                    .outputTo(writer)
                    .dumpClassFilesTo(tempDir)
                    .defineClass(classDefinition, Object.class);

            Method add = clazz.getMethod("add", int.class, int.class);
            assertEquals(add.invoke(null, 13, 42), 55);

            Method plus = clazz.getMethod("plus", int.class, int.class);
            assertEquals(plus.invoke(null, 13, 42), 55);
        } finally {
            deleteRecursively(tempDir, ALLOW_INSECURE);
        }
    }
}
