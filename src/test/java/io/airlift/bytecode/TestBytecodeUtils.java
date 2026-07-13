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
}
