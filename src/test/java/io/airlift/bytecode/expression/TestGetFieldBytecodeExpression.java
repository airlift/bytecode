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

import java.awt.Point;

import static io.airlift.bytecode.ParameterizedType.type;
import static io.airlift.bytecode.expression.BytecodeExpressionAssertions.assertBytecodeExpression;
import static io.airlift.bytecode.expression.BytecodeExpressions.constantInt;
import static io.airlift.bytecode.expression.BytecodeExpressions.getStatic;
import static io.airlift.bytecode.expression.BytecodeExpressions.newInstance;

class TestGetFieldBytecodeExpression
{
    @Test
    void testGetField()
            throws Exception
    {
        assertBytecodeExpression(newInstance(Point.class, constantInt(3), constantInt(7)).getField("x", int.class), new Point(3, 7).x, "new Point(3, 7).x");
        assertBytecodeExpression(newInstance(Point.class, constantInt(3), constantInt(7)).getField(Point.class, "x"), new Point(3, 7).x, "new Point(3, 7).x");
        assertBytecodeExpression(newInstance(Point.class, constantInt(3), constantInt(7)).getField(Point.class.getField("x")), new Point(3, 7).x, "new Point(3, 7).x");
    }

    @Test
    void testGetStaticField()
            throws Exception
    {
        assertBytecodeExpression(getStatic(Long.class, "MIN_VALUE"), Long.MIN_VALUE, "Long.MIN_VALUE");
        assertBytecodeExpression(getStatic(Long.class.getField("MIN_VALUE")), Long.MIN_VALUE, "Long.MIN_VALUE");
        assertBytecodeExpression(getStatic(type(Long.class), "MIN_VALUE", type(long.class)), Long.MIN_VALUE, "Long.MIN_VALUE");
    }
}
