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
package io.airlift.bytecode.expression;

import org.junit.jupiter.api.Test;

import static io.airlift.bytecode.expression.BytecodeExpressionAssertions.assertBytecodeExpression;
import static io.airlift.bytecode.expression.BytecodeExpressions.invokeStatic;
import static org.assertj.core.api.Assertions.assertThat;

public class TestPopBytecodeExpression
{
    @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
    @Test
    void testGetField()
            throws Exception
    {
        intCount = 0;
        assertBytecodeExpression(invokeStatic(getClass(), "incrementAndGetIntCount", int.class).pop(), null, getClass().getSimpleName() + ".incrementAndGetIntCount();");
        assertThat(intCount).isEqualTo(1);
        longCount = 0;
        assertBytecodeExpression(invokeStatic(getClass(), "incrementAndGetLongCount", long.class).pop(), null, getClass().getSimpleName() + ".incrementAndGetLongCount();");
        assertThat(longCount).isEqualTo(1);
    }

    private static int intCount;

    @SuppressWarnings("unused")
    public static int incrementAndGetIntCount()
    {
        return intCount++;
    }

    private static long longCount;

    @SuppressWarnings("unused")
    public static long incrementAndGetLongCount()
    {
        return longCount++;
    }
}
