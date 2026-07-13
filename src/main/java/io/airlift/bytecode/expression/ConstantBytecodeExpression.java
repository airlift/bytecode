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
import io.airlift.bytecode.BytecodeNode;
import io.airlift.bytecode.MethodGenerationContext;
import io.airlift.bytecode.ParameterizedType;
import io.airlift.bytecode.instruction.Constant;

import java.util.List;

import static io.airlift.bytecode.ParameterizedType.type;

class ConstantBytecodeExpression
        extends BytecodeExpression
{
    private final Constant value;

    public ConstantBytecodeExpression(Class<?> type, Constant value)
    {
        this(type(type), value);
    }

    public ConstantBytecodeExpression(ParameterizedType type, Constant value)
    {
        super(type);
        this.value = value;
    }

    @Override
    public Constant getBytecode(MethodGenerationContext generationContext)
    {
        return value;
    }

    @Override
    protected String formatOneLine()
    {
        return renderConstant(value.getValue());
    }

    public static String renderConstant(Object value)
    {
        return switch (value) {
            case Long longValue -> longValue + "L";
            case Float floatValue -> floatValue + "f";
            case ParameterizedType parameterizedType -> parameterizedType.getSimpleName() + ".class";
            // todo escape string
            case String string -> "\"" + string + "\"";
            case null, default -> String.valueOf(value);
        };
    }

    @Override
    public List<BytecodeNode> getChildNodes()
    {
        return ImmutableList.of();
    }
}
