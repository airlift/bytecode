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
import static io.airlift.bytecode.BytecodeUtils.fitsMethodSizeLimit;
import static io.airlift.bytecode.BytecodeUtils.isInlineable;
import static io.airlift.bytecode.BytecodeUtils.isInlineableWhenHot;
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

    @Test
    void testFitsMethodSizeLimit()
    {
        ClassDefinition classDefinition = new ClassDefinition(
                a(PUBLIC, FINAL),
                "test/MethodSizeLimit",
                type(Object.class));

        // 32767 push/pop pairs plus RETURN generate exactly 65535 bytes, the JVM limit
        MethodDefinition atLimit = classDefinition.declareMethod(
                a(PUBLIC, STATIC),
                "atLimit",
                type(void.class),
                ImmutableList.of());
        BytecodeBlock atLimitBody = atLimit.getBody();
        for (int i = 0; i < 32_767; i++) {
            atLimitBody.push(0).pop();
        }
        atLimitBody.ret();
        assertThat(fitsMethodSizeLimit(atLimitBody, atLimit.getScope())).isTrue();

        MethodDefinition overLimit = classDefinition.declareMethod(
                a(PUBLIC, STATIC),
                "overLimit",
                type(void.class),
                ImmutableList.of());
        BytecodeBlock overLimitBody = overLimit.getBody();
        for (int i = 0; i < 32_768; i++) {
            overLimitBody.push(0).pop();
        }
        overLimitBody.ret();
        assertThat(fitsMethodSizeLimit(overLimitBody, overLimit.getScope())).isFalse();
    }

    @Test
    void testFitsMethodSizeLimitOfClassInitializer()
    {
        // 32767 push/pop pairs plus the implicit RETURN generate exactly 65535 bytes, the JVM limit
        ClassDefinition atLimitClass = new ClassDefinition(
                a(PUBLIC, FINAL),
                "test/ClassInitializerAtLimit",
                type(Object.class));
        MethodDefinition atLimit = atLimitClass.getClassInitializer();
        BytecodeBlock atLimitBody = atLimit.getBody();
        for (int i = 0; i < 32_767; i++) {
            atLimitBody.push(0).pop();
        }
        assertThat(fitsMethodSizeLimit(atLimit)).isTrue();

        // one more NOP puts the generated method one byte over the limit, which
        // only the MethodDefinition overload detects
        ClassDefinition overLimitClass = new ClassDefinition(
                a(PUBLIC, FINAL),
                "test/ClassInitializerOverLimit",
                type(Object.class));
        MethodDefinition overLimit = overLimitClass.getClassInitializer();
        BytecodeBlock overLimitBody = overLimit.getBody();
        for (int i = 0; i < 32_767; i++) {
            overLimitBody.push(0).pop();
        }
        overLimitBody.append(OpCode.NOP);
        assertThat(fitsMethodSizeLimit(overLimitBody, overLimit.getScope())).isTrue();
        assertThat(fitsMethodSizeLimit(overLimit)).isFalse();
    }

    @Test
    void testIsInlineable()
    {
        ClassDefinition classDefinition = new ClassDefinition(
                a(PUBLIC, FINAL),
                "test/Inlineable",
                type(Object.class));

        // 17 push/pop pairs plus RETURN generate exactly 35 bytes, the MaxInlineSize
        MethodDefinition small = classDefinition.declareMethod(
                a(PUBLIC, STATIC),
                "small",
                type(void.class),
                ImmutableList.of());
        BytecodeBlock smallBody = small.getBody();
        for (int i = 0; i < 17; i++) {
            smallBody.push(0).pop();
        }
        smallBody.ret();
        assertThat(isInlineable(smallBody, small.getScope())).isTrue();
        assertThat(isInlineableWhenHot(smallBody, small.getScope())).isTrue();

        // 162 push/pop pairs plus RETURN generate exactly 325 bytes, the FreqInlineSize
        MethodDefinition medium = classDefinition.declareMethod(
                a(PUBLIC, STATIC),
                "medium",
                type(void.class),
                ImmutableList.of());
        BytecodeBlock mediumBody = medium.getBody();
        for (int i = 0; i < 162; i++) {
            mediumBody.push(0).pop();
        }
        mediumBody.ret();
        assertThat(isInlineable(mediumBody, medium.getScope())).isFalse();
        assertThat(isInlineableWhenHot(mediumBody, medium.getScope())).isTrue();

        MethodDefinition large = classDefinition.declareMethod(
                a(PUBLIC, STATIC),
                "large",
                type(void.class),
                ImmutableList.of());
        BytecodeBlock largeBody = large.getBody();
        for (int i = 0; i < 163; i++) {
            largeBody.push(0).pop();
        }
        largeBody.ret();
        assertThat(isInlineable(largeBody, large.getScope())).isFalse();
        assertThat(isInlineableWhenHot(largeBody, large.getScope())).isFalse();
    }
}
