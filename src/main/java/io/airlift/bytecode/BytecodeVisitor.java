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

import io.airlift.bytecode.control.DoWhileLoop;
import io.airlift.bytecode.control.FlowControl;
import io.airlift.bytecode.control.ForLoop;
import io.airlift.bytecode.control.IfStatement;
import io.airlift.bytecode.control.SwitchStatement;
import io.airlift.bytecode.control.TryCatch;
import io.airlift.bytecode.control.WhileLoop;
import io.airlift.bytecode.debug.DebugNode;
import io.airlift.bytecode.debug.LineNumberNode;
import io.airlift.bytecode.debug.LocalVariableNode;
import io.airlift.bytecode.expression.BytecodeExpression;
import io.airlift.bytecode.instruction.Constant;
import io.airlift.bytecode.instruction.Constant.BooleanConstant;
import io.airlift.bytecode.instruction.Constant.BoxedBooleanConstant;
import io.airlift.bytecode.instruction.Constant.BoxedDoubleConstant;
import io.airlift.bytecode.instruction.Constant.BoxedFloatConstant;
import io.airlift.bytecode.instruction.Constant.BoxedIntegerConstant;
import io.airlift.bytecode.instruction.Constant.BoxedLongConstant;
import io.airlift.bytecode.instruction.Constant.ClassConstant;
import io.airlift.bytecode.instruction.Constant.DoubleConstant;
import io.airlift.bytecode.instruction.Constant.FloatConstant;
import io.airlift.bytecode.instruction.Constant.IntConstant;
import io.airlift.bytecode.instruction.Constant.LongConstant;
import io.airlift.bytecode.instruction.Constant.StringConstant;
import io.airlift.bytecode.instruction.FieldInstruction;
import io.airlift.bytecode.instruction.FieldInstruction.GetFieldInstruction;
import io.airlift.bytecode.instruction.FieldInstruction.PutFieldInstruction;
import io.airlift.bytecode.instruction.InstructionNode;
import io.airlift.bytecode.instruction.InvokeInstruction;
import io.airlift.bytecode.instruction.InvokeInstruction.InvokeDynamicInstruction;
import io.airlift.bytecode.instruction.JumpInstruction;
import io.airlift.bytecode.instruction.LabelNode;
import io.airlift.bytecode.instruction.VariableInstruction;
import io.airlift.bytecode.instruction.VariableInstruction.IncrementVariableInstruction;
import io.airlift.bytecode.instruction.VariableInstruction.LoadVariableInstruction;
import io.airlift.bytecode.instruction.VariableInstruction.StoreVariableInstruction;

public class BytecodeVisitor<T>
{
    public T visitClass(ClassDefinition classDefinition)
    {
        for (AnnotationDefinition annotationDefinition : classDefinition.getAnnotations()) {
            visitAnnotation(classDefinition, annotationDefinition);
        }
        for (FieldDefinition fieldDefinition : classDefinition.getFields()) {
            visitField(classDefinition, fieldDefinition);
        }
        for (MethodDefinition methodDefinition : classDefinition.getMethods()) {
            visitMethod(classDefinition, methodDefinition);
        }
        return null;
    }

    public T visitAnnotation(Object parent, AnnotationDefinition annotationDefinition)
    {
        return null;
    }

    public T visitField(ClassDefinition classDefinition, FieldDefinition fieldDefinition)
    {
        for (AnnotationDefinition annotationDefinition : fieldDefinition.getAnnotations()) {
            visitAnnotation(fieldDefinition, annotationDefinition);
        }
        return null;
    }

    public T visitMethod(ClassDefinition classDefinition, MethodDefinition methodDefinition)
    {
        for (AnnotationDefinition annotationDefinition : methodDefinition.getAnnotations()) {
            visitAnnotation(methodDefinition, annotationDefinition);
        }
        methodDefinition.getBody().accept(null, this);
        return null;
    }

    public T visitNode(BytecodeNode parent, BytecodeNode node)
    {
        for (BytecodeNode child : node.getChildNodes()) {
            child.accept(node, this);
        }
        return null;
    }

    //
    // Comment
    //

    public T visitComment(BytecodeNode parent, Comment node)
    {
        return visitNode(parent, node);
    }

    //
    // Block
    //

    public T visitBlock(BytecodeNode parent, BytecodeBlock block)
    {
        return visitNode(parent, block);
    }

    //
    // Bytecode Expression
    //
    public T visitBytecodeExpression(BytecodeNode parent, BytecodeExpression expression)
    {
        return visitNode(parent, expression);
    }

    //
    // Flow Control
    //

    public T visitFlowControl(BytecodeNode parent, FlowControl flowControl)
    {
        return visitNode(parent, flowControl);
    }

    public T visitTryCatch(BytecodeNode parent, TryCatch tryCatch)
    {
        return visitFlowControl(parent, tryCatch);
    }

    public T visitIf(BytecodeNode parent, IfStatement ifStatement)
    {
        return visitFlowControl(parent, ifStatement);
    }

    public T visitFor(BytecodeNode parent, ForLoop forLoop)
    {
        return visitFlowControl(parent, forLoop);
    }

    public T visitWhile(BytecodeNode parent, WhileLoop whileLoop)
    {
        return visitFlowControl(parent, whileLoop);
    }

    public T visitDoWhile(BytecodeNode parent, DoWhileLoop doWhileLoop)
    {
        return visitFlowControl(parent, doWhileLoop);
    }

    public T visitSwitch(BytecodeNode parent, SwitchStatement switchStatement)
    {
        return visitFlowControl(parent, switchStatement);
    }

    //
    // Instructions
    //

    public T visitInstruction(BytecodeNode parent, InstructionNode node)
    {
        return visitNode(parent, node);
    }

    public T visitLabel(BytecodeNode parent, LabelNode labelNode)
    {
        return visitInstruction(parent, labelNode);
    }

    public T visitJumpInstruction(BytecodeNode parent, JumpInstruction jumpInstruction)
    {
        return visitInstruction(parent, jumpInstruction);
    }

    //
    // Constants
    //

    public T visitConstant(BytecodeNode parent, Constant constant)
    {
        return visitInstruction(parent, constant);
    }

    public T visitBoxedBooleanConstant(BytecodeNode parent, BoxedBooleanConstant boxedBooleanConstant)
    {
        return visitConstant(parent, boxedBooleanConstant);
    }

    public T visitBooleanConstant(BytecodeNode parent, BooleanConstant booleanConstant)
    {
        return visitConstant(parent, booleanConstant);
    }

    public T visitIntConstant(BytecodeNode parent, IntConstant intConstant)
    {
        return visitConstant(parent, intConstant);
    }

    public T visitBoxedIntegerConstant(BytecodeNode parent, BoxedIntegerConstant boxedIntegerConstant)
    {
        return visitConstant(parent, boxedIntegerConstant);
    }

    public T visitFloatConstant(BytecodeNode parent, FloatConstant floatConstant)
    {
        return visitConstant(parent, floatConstant);
    }

    public T visitBoxedFloatConstant(BytecodeNode parent, BoxedFloatConstant boxedFloatConstant)
    {
        return visitConstant(parent, boxedFloatConstant);
    }

    public T visitLongConstant(BytecodeNode parent, LongConstant longConstant)
    {
        return visitConstant(parent, longConstant);
    }

    public T visitBoxedLongConstant(BytecodeNode parent, BoxedLongConstant boxedLongConstant)
    {
        return visitConstant(parent, boxedLongConstant);
    }

    public T visitDoubleConstant(BytecodeNode parent, DoubleConstant doubleConstant)
    {
        return visitConstant(parent, doubleConstant);
    }

    public T visitBoxedDoubleConstant(BytecodeNode parent, BoxedDoubleConstant boxedDoubleConstant)
    {
        return visitConstant(parent, boxedDoubleConstant);
    }

    public T visitStringConstant(BytecodeNode parent, StringConstant stringConstant)
    {
        return visitConstant(parent, stringConstant);
    }

    public T visitClassConstant(BytecodeNode parent, ClassConstant classConstant)
    {
        return visitConstant(parent, classConstant);
    }

    //
    // Local Variable Instructions
    //

    public T visitVariableInstruction(BytecodeNode parent, VariableInstruction variableInstruction)
    {
        return visitInstruction(parent, variableInstruction);
    }

    public T visitLoadVariable(BytecodeNode parent, LoadVariableInstruction loadVariableInstruction)
    {
        return visitVariableInstruction(parent, loadVariableInstruction);
    }

    public T visitStoreVariable(BytecodeNode parent, StoreVariableInstruction storeVariableInstruction)
    {
        return visitVariableInstruction(parent, storeVariableInstruction);
    }

    public T visitIncrementVariable(BytecodeNode parent, IncrementVariableInstruction incrementVariableInstruction)
    {
        return visitVariableInstruction(parent, incrementVariableInstruction);
    }

    //
    // Field Instructions
    //

    public T visitFieldInstruction(BytecodeNode parent, FieldInstruction fieldInstruction)
    {
        return visitInstruction(parent, fieldInstruction);
    }

    public T visitGetField(BytecodeNode parent, GetFieldInstruction getFieldInstruction)
    {
        return visitFieldInstruction(parent, getFieldInstruction);
    }

    public T visitPutField(BytecodeNode parent, PutFieldInstruction putFieldInstruction)
    {
        return visitFieldInstruction(parent, putFieldInstruction);
    }

    //
    // Invoke
    //

    public T visitInvoke(BytecodeNode parent, InvokeInstruction invokeInstruction)
    {
        return visitInstruction(parent, invokeInstruction);
    }

    public T visitInvokeDynamic(BytecodeNode parent, InvokeDynamicInstruction invokeDynamicInstruction)
    {
        return visitInvoke(parent, invokeDynamicInstruction);
    }

    //
    // Debug
    //

    public T visitDebug(BytecodeNode parent, DebugNode debugNode)
    {
        return visitNode(parent, debugNode);
    }

    public T visitLineNumber(BytecodeNode parent, LineNumberNode lineNumberNode)
    {
        return visitDebug(parent, lineNumberNode);
    }

    public T visitLocalVariable(BytecodeNode parent, LocalVariableNode localVariableNode)
    {
        return visitDebug(parent, localVariableNode);
    }
}
