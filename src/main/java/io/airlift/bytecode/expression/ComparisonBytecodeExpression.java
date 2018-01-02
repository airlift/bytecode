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
import io.airlift.bytecode.instruction.JumpInstruction;
import io.airlift.bytecode.instruction.LabelNode;

import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static io.airlift.bytecode.OpCode.DCMPG;
import static io.airlift.bytecode.OpCode.DCMPL;
import static io.airlift.bytecode.OpCode.FCMPG;
import static io.airlift.bytecode.OpCode.FCMPL;
import static io.airlift.bytecode.OpCode.IFEQ;
import static io.airlift.bytecode.OpCode.IFGE;
import static io.airlift.bytecode.OpCode.IFGT;
import static io.airlift.bytecode.OpCode.IFLE;
import static io.airlift.bytecode.OpCode.IFLT;
import static io.airlift.bytecode.OpCode.IFNE;
import static io.airlift.bytecode.OpCode.IF_ACMPEQ;
import static io.airlift.bytecode.OpCode.IF_ACMPNE;
import static io.airlift.bytecode.OpCode.IF_ICMPEQ;
import static io.airlift.bytecode.OpCode.IF_ICMPGE;
import static io.airlift.bytecode.OpCode.IF_ICMPGT;
import static io.airlift.bytecode.OpCode.IF_ICMPLE;
import static io.airlift.bytecode.OpCode.IF_ICMPLT;
import static io.airlift.bytecode.OpCode.IF_ICMPNE;
import static io.airlift.bytecode.OpCode.LCMP;
import static io.airlift.bytecode.ParameterizedType.type;
import static java.util.Objects.requireNonNull;

class ComparisonBytecodeExpression
        extends BytecodeExpression
{
    static BytecodeExpression lessThan(BytecodeExpression left, BytecodeExpression right)
    {
        checkArgumentTypes(left, right);

        OpCode comparisonInstruction;
        OpCode noMatchJumpInstruction;

        Class<?> type = left.getType().getPrimitiveType();
        if (type == int.class) {
            comparisonInstruction = null;
            noMatchJumpInstruction = IF_ICMPGE;
        }
        else if (type == long.class) {
            comparisonInstruction = LCMP;
            noMatchJumpInstruction = IFGE;
        }
        else if (type == float.class) {
            comparisonInstruction = FCMPG;
            noMatchJumpInstruction = IFGE;
        }
        else if (type == double.class) {
            comparisonInstruction = DCMPG;
            noMatchJumpInstruction = IFGE;
        }
        else {
            throw new IllegalArgumentException("Less than does not support " + type);
        }

        return new ComparisonBytecodeExpression("<", comparisonInstruction, noMatchJumpInstruction, left, right);
    }

    static BytecodeExpression greaterThan(BytecodeExpression left, BytecodeExpression right)
    {
        checkArgumentTypes(left, right);

        OpCode comparisonInstruction;
        OpCode noMatchJumpInstruction;

        Class<?> type = left.getType().getPrimitiveType();
        if (type == int.class) {
            comparisonInstruction = null;
            noMatchJumpInstruction = IF_ICMPLE;
        }
        else if (type == long.class) {
            comparisonInstruction = LCMP;
            noMatchJumpInstruction = IFLE;
        }
        else if (type == float.class) {
            comparisonInstruction = FCMPL;
            noMatchJumpInstruction = IFLE;
        }
        else if (type == double.class) {
            comparisonInstruction = DCMPL;
            noMatchJumpInstruction = IFLE;
        }
        else {
            throw new IllegalArgumentException("Greater than does not support " + type);
        }
        return new ComparisonBytecodeExpression(">", comparisonInstruction, noMatchJumpInstruction, left, right);
    }

    static BytecodeExpression lessThanOrEqual(BytecodeExpression left, BytecodeExpression right)
    {
        checkArgumentTypes(left, right);

        OpCode comparisonInstruction;
        OpCode noMatchJumpInstruction;

        Class<?> type = left.getType().getPrimitiveType();
        if (type == int.class) {
            comparisonInstruction = null;
            noMatchJumpInstruction = IF_ICMPGT;
        }
        else if (type == long.class) {
            comparisonInstruction = LCMP;
            noMatchJumpInstruction = IFGT;
        }
        else if (type == float.class) {
            comparisonInstruction = FCMPG;
            noMatchJumpInstruction = IFGT;
        }
        else if (type == double.class) {
            comparisonInstruction = DCMPG;
            noMatchJumpInstruction = IFGT;
        }
        else {
            throw new IllegalArgumentException("Less than or equal does not support " + type);
        }
        return new ComparisonBytecodeExpression("<=", comparisonInstruction, noMatchJumpInstruction, left, right);
    }

    static BytecodeExpression greaterThanOrEqual(BytecodeExpression left, BytecodeExpression right)
    {
        checkArgumentTypes(left, right);

        OpCode comparisonInstruction;
        OpCode noMatchJumpInstruction;

        Class<?> type = left.getType().getPrimitiveType();
        if (type == int.class) {
            comparisonInstruction = null;
            noMatchJumpInstruction = IF_ICMPLT;
        }
        else if (type == long.class) {
            comparisonInstruction = LCMP;
            noMatchJumpInstruction = IFLT;
        }
        else if (type == float.class) {
            comparisonInstruction = FCMPL;
            noMatchJumpInstruction = IFLT;
        }
        else if (type == double.class) {
            comparisonInstruction = DCMPL;
            noMatchJumpInstruction = IFLT;
        }
        else {
            throw new IllegalArgumentException("Greater than or equal does not support " + type);
        }
        return new ComparisonBytecodeExpression(">=", comparisonInstruction, noMatchJumpInstruction, left, right);
    }

    static BytecodeExpression equal(BytecodeExpression left, BytecodeExpression right)
    {
        requireNonNull(left, "left is null");
        requireNonNull(right, "right is null");
        checkArgument(left.getType().equals(right.getType()), "left and right must be the same type");

        OpCode comparisonInstruction;
        OpCode noMatchJumpInstruction;

        Class<?> type = left.getType().getPrimitiveType();
        if (type == int.class) {
            comparisonInstruction = null;
            noMatchJumpInstruction = IF_ICMPNE;
        }
        else if (type == long.class) {
            comparisonInstruction = LCMP;
            noMatchJumpInstruction = IFNE;
        }
        else if (type == float.class) {
            comparisonInstruction = FCMPL;
            noMatchJumpInstruction = IFNE;
        }
        else if (type == double.class) {
            comparisonInstruction = DCMPL;
            noMatchJumpInstruction = IFNE;
        }
        else if (type == null) {
            comparisonInstruction = null;
            noMatchJumpInstruction = IF_ACMPNE;
        }
        else {
            throw new IllegalArgumentException("Equal does not support " + type);
        }
        return new ComparisonBytecodeExpression("==", comparisonInstruction, noMatchJumpInstruction, left, right);
    }

    static BytecodeExpression notEqual(BytecodeExpression left, BytecodeExpression right)
    {
        requireNonNull(left, "left is null");
        requireNonNull(right, "right is null");
        checkArgument(left.getType().equals(right.getType()), "left and right must be the same type");

        OpCode comparisonInstruction;
        OpCode noMatchJumpInstruction;

        Class<?> type = left.getType().getPrimitiveType();
        if (type == int.class) {
            comparisonInstruction = null;
            noMatchJumpInstruction = IF_ICMPEQ;
        }
        else if (type == long.class) {
            comparisonInstruction = LCMP;
            noMatchJumpInstruction = IFEQ;
        }
        else if (type == float.class) {
            comparisonInstruction = FCMPL;
            noMatchJumpInstruction = IFEQ;
        }
        else if (type == double.class) {
            comparisonInstruction = DCMPL;
            noMatchJumpInstruction = IFEQ;
        }
        else if (type == null) {
            comparisonInstruction = null;
            noMatchJumpInstruction = IF_ACMPEQ;
        }
        else {
            throw new IllegalArgumentException("Not equal than does not support " + type);
        }
        return new ComparisonBytecodeExpression("!=", comparisonInstruction, noMatchJumpInstruction, left, right);
    }

    private static void checkArgumentTypes(BytecodeExpression left, BytecodeExpression right)
    {
        Class<?> leftType = getPrimitiveType(left, "left");
        Class<?> rightType = getPrimitiveType(right, "right");
        checkArgument(leftType == rightType, "left and right must be the same type");
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
    private final OpCode comparisonInstruction;
    private final OpCode noMatchJumpInstruction;
    private final BytecodeExpression left;
    private final BytecodeExpression right;

    private ComparisonBytecodeExpression(
            String infixSymbol,
            OpCode comparisonInstruction,
            OpCode noMatchJumpInstruction,
            BytecodeExpression left,
            BytecodeExpression right)
    {
        super(type(boolean.class));
        this.infixSymbol = infixSymbol;
        this.comparisonInstruction = comparisonInstruction;
        this.noMatchJumpInstruction = noMatchJumpInstruction;
        this.left = left;
        this.right = right;
    }

    @Override
    public BytecodeNode getBytecode(MethodGenerationContext generationContext)
    {
        BytecodeBlock block = new BytecodeBlock()
                .append(left)
                .append(right);

        if (comparisonInstruction != null) {
            block.append(comparisonInstruction);
        }

        LabelNode noMatch = new LabelNode("no_match");
        LabelNode end = new LabelNode("end");
        return block
                .append(new JumpInstruction(noMatchJumpInstruction, noMatch))
                .push(true)
                .gotoLabel(end)
                .append(noMatch)
                .push(false)
                .append(end);
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
