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

import io.airlift.bytecode.BytecodeBlock;
import io.airlift.bytecode.BytecodeNode;
import io.airlift.bytecode.Scope;
import io.airlift.bytecode.Variable;
import org.testng.annotations.Test;

import java.awt.Point;
import java.util.function.Function;

import static io.airlift.bytecode.ParameterizedType.type;
import static io.airlift.bytecode.expression.BytecodeExpressionAssertions.assertBytecodeNode;
import static io.airlift.bytecode.expression.BytecodeExpressions.constantInt;
import static io.airlift.bytecode.expression.BytecodeExpressions.constantLong;
import static io.airlift.bytecode.expression.BytecodeExpressions.newInstance;
import static org.testng.Assert.assertEquals;

public class TestSetVariableBytecodeExpression
{
    @Test
    public void testIncrement()
            throws Exception
    {
        assertBytecodeNode(scope -> {
            Variable byteValue = scope.declareVariable(byte.class, "byte");
            assertEquals(byteValue.increment().toString(), "byte++;");
            return new BytecodeBlock()
                    .append(byteValue.set(constantInt(0)))
                    .append(byteValue.increment())
                    .append(byteValue.ret());
        }, type(byte.class), (byte) 1);

        assertBytecodeNode(scope -> {
            Variable shortValue = scope.declareVariable(short.class, "short");
            assertEquals(shortValue.increment().toString(), "short++;");
            return new BytecodeBlock()
                    .append(shortValue.set(constantInt(0)))
                    .append(shortValue.increment())
                    .append(shortValue.ret());
        }, type(short.class), (short) 1);

        assertBytecodeNode(scope -> {
            Variable intValue = scope.declareVariable(int.class, "int");
            assertEquals(intValue.increment().toString(), "int++;");
            return new BytecodeBlock()
                    .append(intValue.set(constantInt(0)))
                    .append(intValue.increment())
                    .append(intValue.ret());
        }, type(int.class), 1);

        assertBytecodeNode(scope -> {
            Variable longValue = scope.declareVariable(long.class, "long");
            assertEquals(longValue.increment().toString(), "long = (long + 1L);");
            return new BytecodeBlock()
                    .append(longValue.set(constantLong(0)))
                    .append(longValue.increment())
                    .append(longValue.ret());
        }, type(long.class), 1L);
    }

    @Test
    public void testGetField()
            throws Exception
    {
        Function<Scope, BytecodeNode> nodeGenerator = scope -> {
            Variable point = scope.declareVariable(Point.class, "point");
            BytecodeExpression setPoint = point.set(newInstance(Point.class, constantInt(3), constantInt(7)));

            assertEquals(setPoint.toString(), "point = new Point(3, 7);");

            return new BytecodeBlock()
                    .append(setPoint)
                    .append(point.ret());
        };

        assertBytecodeNode(nodeGenerator, type(Point.class), new Point(3, 7));
    }
}
