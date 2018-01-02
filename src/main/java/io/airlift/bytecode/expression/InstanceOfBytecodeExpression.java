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

import java.util.List;

import static io.airlift.bytecode.ParameterizedType.type;
import static java.util.Objects.requireNonNull;

class InstanceOfBytecodeExpression
        extends BytecodeExpression
{
    private final BytecodeExpression instance;
    private final Class<?> type;

    public InstanceOfBytecodeExpression(BytecodeExpression instance, Class<?> type)
    {
        super(type(boolean.class));

        this.instance = requireNonNull(instance, "instance is null");
        this.type = requireNonNull(type, "type is null");
    }

    public static BytecodeExpression instanceOf(BytecodeExpression instance, Class<?> type)
    {
        return new InstanceOfBytecodeExpression(instance, type);
    }

    @Override
    public BytecodeNode getBytecode(MethodGenerationContext generationContext)
    {
        return new BytecodeBlock()
                .append(instance)
                .isInstanceOf(type);
    }

    @Override
    protected String formatOneLine()
    {
        return instance + " instanceof " + type;
    }

    @Override
    public List<BytecodeNode> getChildNodes()
    {
        return ImmutableList.of();
    }
}
