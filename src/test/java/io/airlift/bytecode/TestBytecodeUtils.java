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
import org.junit.jupiter.api.Test;

import static io.airlift.bytecode.Access.FINAL;
import static io.airlift.bytecode.Access.PUBLIC;
import static io.airlift.bytecode.Access.STATIC;
import static io.airlift.bytecode.Access.a;
import static io.airlift.bytecode.BytecodeUtils.estimateMaxCodeSize;
import static io.airlift.bytecode.BytecodeUtils.isJitCompilable;
import static io.airlift.bytecode.BytecodeUtils.toJavaIdentifierString;
import static io.airlift.bytecode.Parameter.arg;
import static io.airlift.bytecode.ParameterizedType.type;
import static io.airlift.bytecode.expression.BytecodeExpressions.add;
import static org.assertj.core.api.Assertions.assertThat;

class TestBytecodeUtils
{
    @Test
    void testToJavaIdentifierString()
    {
        assertThat(toJavaIdentifierString("HelloWorld")).isEqualTo("HelloWorld");
        assertThat(toJavaIdentifierString("Hello$World")).isEqualTo("Hello$World");
        assertThat(toJavaIdentifierString("Hello#World")).isEqualTo("Hello_World");
        assertThat(toJavaIdentifierString("A^B^C")).isEqualTo("A_B_C");
    }

    @Test
    void testEstimateMaxCodeSize()
    {
        ClassDefinition classDefinition = new ClassDefinition(
                a(PUBLIC, FINAL),
                "test/EstimateSize",
                type(Object.class));

        Parameter argA = arg("a", int.class);
        Parameter argB = arg("b", int.class);

        MethodDefinition addMethod = classDefinition.declareMethod(
                a(PUBLIC, STATIC),
                "add",
                type(int.class),
                ImmutableList.of(argA, argB));

        addMethod.getBody()
                .append(add(argA, argB))
                .retInt();

        // ILOAD, ILOAD, IADD, IRETURN
        assertThat(estimateMaxCodeSize(addMethod.getBody(), addMethod.getScope())).isEqualTo(4);

        MethodDefinition bigMethod = classDefinition.declareMethod(
                a(PUBLIC, STATIC),
                "big",
                type(void.class),
                ImmutableList.of());

        BytecodeBlock body = bigMethod.getBody();
        for (int i = 0; i < 40_000; i++) {
            body.push(0).pop();
        }
        body.ret();

        assertThat(estimateMaxCodeSize(bigMethod.getBody(), bigMethod.getScope())).isEqualTo(2 * 40_000 + 1);
    }

    @Test
    void testEstimateMaxCodeSizeOfClassInitializer()
    {
        ClassDefinition classDefinition = new ClassDefinition(
                a(PUBLIC, FINAL),
                "test/EstimateClassInitializer",
                type(Object.class));

        MethodDefinition classInitializer = classDefinition.getClassInitializer();
        classInitializer.getBody().push(0).pop();

        // the implicit RETURN appended at generation time is counted
        assertThat(estimateMaxCodeSize(classInitializer.getBody(), classInitializer.getScope())).isEqualTo(2);
        assertThat(estimateMaxCodeSize(classInitializer)).isEqualTo(3);

        // ordinary methods generate exactly their body
        MethodDefinition method = classDefinition.declareMethod(
                a(PUBLIC, STATIC),
                "method",
                type(void.class),
                ImmutableList.of());
        method.getBody().push(0).pop().ret();
        assertThat(estimateMaxCodeSize(method)).isEqualTo(3);
    }

    @Test
    void testIsJitCompilable()
    {
        ClassDefinition classDefinition = new ClassDefinition(
                a(PUBLIC, FINAL),
                "test/JitCompilable",
                type(Object.class));

        // 3999 push/pop pairs plus NOP and RETURN generate exactly 8000 bytes, the HugeMethodLimit
        MethodDefinition atLimit = classDefinition.declareMethod(
                a(PUBLIC, STATIC),
                "atLimit",
                type(void.class),
                ImmutableList.of());
        BytecodeBlock atLimitBody = atLimit.getBody();
        for (int i = 0; i < 3_999; i++) {
            atLimitBody.push(0).pop();
        }
        atLimitBody.append(OpCode.NOP);
        atLimitBody.ret();
        assertThat(isJitCompilable(atLimitBody, atLimit.getScope())).isTrue();

        // 4000 push/pop pairs plus RETURN generate 8001 bytes, one over the limit
        MethodDefinition overLimit = classDefinition.declareMethod(
                a(PUBLIC, STATIC),
                "overLimit",
                type(void.class),
                ImmutableList.of());
        BytecodeBlock overLimitBody = overLimit.getBody();
        for (int i = 0; i < 4_000; i++) {
            overLimitBody.push(0).pop();
        }
        overLimitBody.ret();
        assertThat(isJitCompilable(overLimitBody, overLimit.getScope())).isFalse();
    }
}
