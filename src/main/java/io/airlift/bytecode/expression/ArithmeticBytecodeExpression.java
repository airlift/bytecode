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

import com.google.common.collect.ImmutableList;
import io.airlift.bytecode.BytecodeBlock;
import io.airlift.bytecode.BytecodeNode;
import io.airlift.bytecode.MethodGenerationContext;
import io.airlift.bytecode.OpCode;
import io.airlift.bytecode.ParameterizedType;

import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public class ArithmeticBytecodeExpression
        extends BytecodeExpression
{
    public static BytecodeExpression createArithmeticBytecodeExpression(OpCode baseOpCode, BytecodeExpression left, BytecodeExpression right)
    {
        requireNonNull(baseOpCode, "baseOpCode is null");
        String name = getName(baseOpCode);
        String infixSymbol = getInfixSymbol(baseOpCode);

        checkArgumentTypes(baseOpCode, name, left, right);

        OpCode opCode = getNumericOpCode(name, baseOpCode, left.getType().getPrimitiveType());
        return new ArithmeticBytecodeExpression(infixSymbol, left.getType(), opCode, left, right);
    }

    private static String getName(OpCode baseOpCode)
    {
        return switch (baseOpCode) {
            case IAND -> "Bitwise AND";
            case IOR -> "Bitwise OR";
            case IXOR -> "Bitwise XOR";
            case IADD -> "Add";
            case ISUB -> "Subtract";
            case IMUL -> "Multiply";
            case IDIV -> "Divide";
            case IREM -> "Remainder";
            case ISHL -> "Shift left";
            case ISHR -> "Shift right";
            case IUSHR -> "Shift right unsigned";
            default -> throw new IllegalArgumentException("Unsupported OpCode " + baseOpCode);
        };
    }

    private static String getInfixSymbol(OpCode baseOpCode)
    {
        return switch (baseOpCode) {
            case IAND -> "&";
            case IOR -> "|";
            case IXOR -> "^";
            case IADD -> "+";
            case ISUB -> "-";
            case IMUL -> "*";
            case IDIV -> "/";
            case IREM -> "%";
            case ISHL -> "<<";
            case ISHR -> ">>";
            case IUSHR -> ">>>";
            default -> throw new IllegalArgumentException("Unsupported OpCode " + baseOpCode);
        };
    }

    private static void checkArgumentTypes(OpCode baseOpCode, String name, BytecodeExpression left, BytecodeExpression right)
    {
        Class<?> leftType = getPrimitiveType(left, "left");
        Class<?> rightType = getPrimitiveType(right, "right");
        switch (baseOpCode) {
            case IAND, IOR, IXOR -> {
                checkArgument(leftType == rightType, "left and right must be the same type");
                checkArgument(leftType == int.class || leftType == long.class, "%s argument must be int or long, but is %s", name, leftType);
            }
            case IADD, ISUB, IMUL, IDIV, IREM -> {
                checkArgument(leftType == rightType, "left and right must be the same type");
                checkArgument(leftType == int.class || leftType == long.class || leftType == float.class || leftType == double.class,
                        "%s argument must be int, long, float, or double, but is %s",
                        name,
                        leftType);
            }
            case ISHL, ISHR, IUSHR -> {
                checkArgument(leftType == int.class || leftType == long.class, "%s left argument be int or long, but is %s", name, leftType);
                checkArgument(rightType == int.class, "%s right argument be and int, but is %s", name, rightType);
            }
            default -> throw new IllegalArgumentException("Unsupported OpCode " + baseOpCode);
        }
    }

    static OpCode getNumericOpCode(String name, OpCode baseOpCode, Class<?> type)
    {
        // Arithmetic OpCodes are laid out int, long, float and then double
        if (type == int.class) {
            return baseOpCode;
        }
        else if (type == long.class) {
            return OpCode.getOpCode(baseOpCode.getOpCode() + 1);
        }
        else if (type == float.class) {
            return OpCode.getOpCode(baseOpCode.getOpCode() + 2);
        }
        else if (type == double.class) {
            return OpCode.getOpCode(baseOpCode.getOpCode() + 3);
        }
        else {
            throw new IllegalArgumentException(name + " does not support " + type);
        }
    }

    private static Class<?> getPrimitiveType(BytecodeExpression expression, String name)
    {
        requireNonNull(expression, name + " is null");
        Class<?> leftType = expression.getType().getPrimitiveType();
        checkArgument(leftType != null, name + " is not a primitive");
        checkArgument(leftType != void.class, name + " is void");
        return leftType;
    }

    private final String infixSymbol;
    private final OpCode opCode;
    private final BytecodeExpression left;
    private final BytecodeExpression right;

    private ArithmeticBytecodeExpression(
            String infixSymbol,
            ParameterizedType type,
            OpCode opCode,
            BytecodeExpression left,
            BytecodeExpression right)
    {
        super(type);
        this.infixSymbol = infixSymbol;
        this.opCode = opCode;
        this.left = left;
        this.right = right;
    }

    @Override
    public BytecodeNode getBytecode(MethodGenerationContext generationContext)
    {
        return new BytecodeBlock()
                .append(left)
                .append(right)
                .append(opCode);
    }

    @Override
    public List<BytecodeNode> getChildNodes()
    {
        return ImmutableList.of(left, right);
    }

    @Override
    protected String formatOneLine()
    {
        return "(" + left + " " + infixSymbol + " " + right + ")";
    }
}
