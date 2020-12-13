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
package io.airlift.bytecode.instruction;

import com.google.common.collect.ImmutableList;
import io.airlift.bytecode.BytecodeNode;
import io.airlift.bytecode.BytecodeVisitor;
import io.airlift.bytecode.MethodGenerationContext;
import io.airlift.bytecode.Variable;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.util.List;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static io.airlift.bytecode.OpCode.ILOAD;
import static io.airlift.bytecode.OpCode.ISTORE;

public abstract class VariableInstruction
        implements InstructionNode
{
    public static InstructionNode loadVariable(Variable variable)
    {
        return new LoadVariableInstruction(variable);
    }

    public static InstructionNode storeVariable(Variable variable)
    {
        return new StoreVariableInstruction(variable);
    }

    public static InstructionNode incrementVariable(Variable variable, byte increment)
    {
        return new IncrementVariableInstruction(variable, increment);
    }

    private final Variable variable;

    private VariableInstruction(Variable variable)
    {
        this.variable = variable;
    }

    public Variable getVariable()
    {
        return variable;
    }

    @Override
    public List<BytecodeNode> getChildNodes()
    {
        return ImmutableList.of();
    }

    @Override
    public <T> T accept(BytecodeNode parent, BytecodeVisitor<T> visitor)
    {
        return visitor.visitVariableInstruction(parent, this);
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("variable", variable)
                .toString();
    }

    public static class LoadVariableInstruction
            extends VariableInstruction
    {
        public LoadVariableInstruction(Variable variable)
        {
            super(variable);
        }

        @Override
        public void accept(MethodVisitor visitor, MethodGenerationContext generationContext)
        {
            visitor.visitVarInsn(Type.getType(getVariable().getType().getType()).getOpcode(ILOAD.getOpCode()), generationContext.getVariableSlot(getVariable()));
        }

        @Override
        public <T> T accept(BytecodeNode parent, BytecodeVisitor<T> visitor)
        {
            return visitor.visitLoadVariable(parent, this);
        }
    }

    public static class StoreVariableInstruction
            extends VariableInstruction
    {
        public StoreVariableInstruction(Variable variable)
        {
            super(variable);
        }

        @Override
        public void accept(MethodVisitor visitor, MethodGenerationContext generationContext)
        {
            visitor.visitVarInsn(Type.getType(getVariable().getType().getType()).getOpcode(ISTORE.getOpCode()), generationContext.getVariableSlot(getVariable()));
        }

        @Override
        public <T> T accept(BytecodeNode parent, BytecodeVisitor<T> visitor)
        {
            return visitor.visitStoreVariable(parent, this);
        }
    }

    public static class IncrementVariableInstruction
            extends VariableInstruction
    {
        private final byte increment;

        public IncrementVariableInstruction(Variable variable, byte increment)
        {
            super(variable);
            String type = variable.getType().getClassName();
            checkArgument(ImmutableList.of("byte", "short", "int").contains(type), "variable must be an byte, short or int, but is %s", type);
            this.increment = increment;
        }

        public byte getIncrement()
        {
            return increment;
        }

        @Override
        public void accept(MethodVisitor visitor, MethodGenerationContext generationContext)
        {
            visitor.visitIincInsn(generationContext.getVariableSlot(getVariable()), increment);
        }

        @Override
        public <T> T accept(BytecodeNode parent, BytecodeVisitor<T> visitor)
        {
            return visitor.visitIncrementVariable(parent, this);
        }
    }
}
