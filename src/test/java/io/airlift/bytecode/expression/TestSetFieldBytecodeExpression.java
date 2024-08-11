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
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.lang.reflect.Field;
import java.util.function.Function;

import static io.airlift.bytecode.ParameterizedType.type;
import static io.airlift.bytecode.expression.BytecodeExpressionAssertions.assertBytecodeExpression;
import static io.airlift.bytecode.expression.BytecodeExpressionAssertions.assertBytecodeNode;
import static io.airlift.bytecode.expression.BytecodeExpressions.constantInt;
import static io.airlift.bytecode.expression.BytecodeExpressions.constantString;
import static io.airlift.bytecode.expression.BytecodeExpressions.newInstance;
import static io.airlift.bytecode.expression.BytecodeExpressions.setStatic;
import static org.assertj.core.api.Assertions.assertThat;

public class TestSetFieldBytecodeExpression
{
    @SuppressWarnings({"WeakerAccess", "PublicField"})
    public static String testField;

    @Test
    void testSetField()
            throws Exception
    {
        assertSetPoint(point -> point.setField("x", constantInt(42)));
        assertSetPoint(point -> point.setField(field(Point.class, "x"), constantInt(42)));
    }

    private static void assertSetPoint(Function<BytecodeExpression, BytecodeExpression> setX)
            throws Exception
    {
        Function<Scope, BytecodeNode> nodeGenerator = scope -> {
            Variable point = scope.declareVariable(Point.class, "point");

            BytecodeExpression setExpression = setX.apply(point);
            assertThat(setExpression.toString()).isEqualTo("point.x = 42;");

            return new BytecodeBlock()
                    .append(point.set(newInstance(Point.class, constantInt(3), constantInt(7))))
                    .append(setExpression)
                    .append(point.ret());
        };

        assertBytecodeNode(nodeGenerator, type(Point.class), new Point(42, 7));
    }

    @Test
    void testSetStaticField()
            throws Exception
    {
        assertSetStaticField(setStatic(getClass(), "testField", constantString("testValue")));
        assertSetStaticField(setStatic(getClass().getField("testField"), constantString("testValue")));
        assertSetStaticField(setStatic(type(getClass()), "testField", constantString("testValue")));
    }

    @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
    private void assertSetStaticField(BytecodeExpression setStaticField)
            throws Exception
    {
        testField = "fail";
        assertBytecodeExpression(setStaticField, null, getClass().getSimpleName() + ".testField = \"testValue\";");
        assertThat(testField).isEqualTo("testValue");
    }

    private static Field field(Class<?> clazz, String name)
    {
        try {
            return clazz.getField(name);
        }
        catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
}
