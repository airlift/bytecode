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
package io.airlift.bytecode.control;

import com.google.common.collect.ImmutableList;
import io.airlift.bytecode.BytecodeBlock;
import io.airlift.bytecode.BytecodeNode;
import io.airlift.bytecode.BytecodeVisitor;
import io.airlift.bytecode.MethodGenerationContext;
import io.airlift.bytecode.ParameterizedType;
import io.airlift.bytecode.instruction.LabelNode;
import org.objectweb.asm.MethodVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Objects.requireNonNull;

public class TryCatch
        implements FlowControl
{
    private final String comment;
    private final BytecodeNode tryNode;
    private final List<CatchBlock> catchBlocks;

    public TryCatch(BytecodeNode tryNode, List<CatchBlock> catchBlocks)
    {
        this(null, tryNode, catchBlocks);
    }

    public TryCatch(String comment, BytecodeNode tryNode, List<CatchBlock> catchBlocks)
    {
        this.comment = comment;
        this.tryNode = requireNonNull(tryNode, "tryNode is null");
        this.catchBlocks = ImmutableList.copyOf(requireNonNull(catchBlocks, "catchBlocks is null"));
    }

    @Override
    public String getComment()
    {
        return comment;
    }

    public BytecodeNode getTryNode()
    {
        return tryNode;
    }

    public List<CatchBlock> getCatchBlocks()
    {
        return catchBlocks;
    }

    @Override
    public void accept(MethodVisitor visitor, MethodGenerationContext generationContext)
    {
        LabelNode tryStart = new LabelNode("tryStart");
        LabelNode tryEnd = new LabelNode("tryEnd");
        List<LabelNode> handlers = new ArrayList<>();
        LabelNode done = new LabelNode("done");

        BytecodeBlock block = new BytecodeBlock();

        // try block
        block.visitLabel(tryStart)
                .append(tryNode)
                .visitLabel(tryEnd)
                .gotoLabel(done);

        // catch blocks
        for (int i = 0; i < catchBlocks.size(); i++) {
            BytecodeNode handlerBlock = catchBlocks.get(i).getHandler();
            LabelNode handler = new LabelNode("handler" + i);
            handlers.add(handler);
            block.visitLabel(handler)
                    .append(handlerBlock);
        }

        // all done
        block.visitLabel(done);

        block.accept(visitor, generationContext);

        // exception table
        for (int i = 0; i < catchBlocks.size(); i++) {
            LabelNode handler = handlers.get(i);
            List<ParameterizedType> exceptionTypes = catchBlocks.get(i).getExceptionTypes();
            for (ParameterizedType type : exceptionTypes) {
                visitor.visitTryCatchBlock(
                        tryStart.getLabel(),
                        tryEnd.getLabel(),
                        handler.getLabel(),
                        type.getClassName());
            }
            if (exceptionTypes.isEmpty()) {
                visitor.visitTryCatchBlock(
                        tryStart.getLabel(),
                        tryEnd.getLabel(),
                        handler.getLabel(),
                        null);
            }
        }
    }

    @Override
    public List<BytecodeNode> getChildNodes()
    {
        return Stream.concat(
                Stream.of(tryNode),
                catchBlocks.stream().map(CatchBlock::getHandler))
                .collect(toImmutableList());
    }

    @Override
    public <T> T accept(BytecodeNode parent, BytecodeVisitor<T> visitor)
    {
        return visitor.visitTryCatch(parent, this);
    }

    public static class CatchBlock
    {
        private final BytecodeNode handler;
        private final List<ParameterizedType> exceptionTypes;

        public CatchBlock(BytecodeNode handler, List<ParameterizedType> exceptionTypes)
        {
            this.handler = requireNonNull(handler, "handler is null");
            this.exceptionTypes = ImmutableList.copyOf(requireNonNull(exceptionTypes, "exceptionTypes is null"));
        }

        public BytecodeNode getHandler()
        {
            return handler;
        }

        public List<ParameterizedType> getExceptionTypes()
        {
            return exceptionTypes;
        }
    }
}
