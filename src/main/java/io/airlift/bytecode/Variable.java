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
import io.airlift.bytecode.expression.BytecodeExpression;
import io.airlift.bytecode.instruction.VariableInstruction;

import java.util.List;

import static io.airlift.bytecode.ParameterizedType.type;
import static io.airlift.bytecode.expression.BytecodeExpressions.add;
import static io.airlift.bytecode.expression.BytecodeExpressions.constantLong;
import static java.util.Objects.requireNonNull;

public class Variable
        extends BytecodeExpression
{
    private final String name;

    public Variable(String name, ParameterizedType type)
    {
        super(type);
        this.name = requireNonNull(name, "name is null");
    }

    public String getName()
    {
        return name;
    }

    public BytecodeExpression set(BytecodeExpression value)
    {
        return new SetVariableBytecodeExpression(this, value);
    }

    public BytecodeExpression increment()
    {
        if (IntegerIncrementVariableBytecodeExpression.isSupportedType(getType())) {
            return new IntegerIncrementVariableBytecodeExpression(this);
        }
        else if (getType().getPrimitiveType() == long.class) {
            return new SetVariableBytecodeExpression(this, add(this, constantLong(1)));
        }
        else {
            throw new UnsupportedOperationException("Variable %s of type %s does not support incrementing".formatted(getName(), getType()));
        }
    }

    @Override
    public BytecodeNode getBytecode(MethodGenerationContext generationContext)
    {
        return VariableInstruction.loadVariable(this);
    }

    @Override
    protected String formatOneLine()
    {
        return name;
    }

    @Override
    public List<BytecodeNode> getChildNodes()
    {
        return ImmutableList.of();
    }

    private static final class IntegerIncrementVariableBytecodeExpression
            extends BytecodeExpression
    {
        private final Variable variable;

        public IntegerIncrementVariableBytecodeExpression(Variable variable)
        {
            super(type(void.class));
            this.variable = requireNonNull(variable, "variable is null");
            if (!isSupportedType(variable.getType())) {
                throw new IllegalArgumentException("Variable %s of type %s is not supported for integer increment".formatted(variable.getName(), variable.getType()));
            }
        }

        @Override
        public BytecodeNode getBytecode(MethodGenerationContext generationContext)
        {
            return VariableInstruction.incrementVariable(variable, (byte) 1);
        }

        @Override
        public List<BytecodeNode> getChildNodes()
        {
            return ImmutableList.of();
        }

        @Override
        protected String formatOneLine()
        {
            return variable.getName() + "++";
        }

        public static boolean isSupportedType(ParameterizedType type)
        {
            if (!type.isPrimitive()) {
                return false;
            }
            Class<?> primitiveType = type.getPrimitiveType();
            return primitiveType == byte.class || primitiveType == short.class || primitiveType == int.class;
        }
    }

    private static final class SetVariableBytecodeExpression
            extends BytecodeExpression
    {
        private final Variable variable;
        private final BytecodeExpression value;

        public SetVariableBytecodeExpression(Variable variable, BytecodeExpression value)
        {
            super(type(void.class));
            this.variable = requireNonNull(variable, "variable is null");
            this.value = requireNonNull(value, "value is null");
        }

        @Override
        public BytecodeNode getBytecode(MethodGenerationContext generationContext)
        {
            return new BytecodeBlock()
                    .append(value)
                    .putVariable(variable);
        }

        @Override
        public List<BytecodeNode> getChildNodes()
        {
            return ImmutableList.of(value);
        }

        @Override
        protected String formatOneLine()
        {
            return variable.getName() + " = " + value;
        }
    }
}
