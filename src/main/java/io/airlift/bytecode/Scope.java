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
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static io.airlift.bytecode.ParameterizedType.type;
import static java.util.Objects.requireNonNull;

public class Scope
{
    private final Map<String, Variable> variables = new TreeMap<>();
    private final Map<String, Variable> tempVariables = new TreeMap<>();
    private final List<Variable> allVariables = new ArrayList<>();
    private final Map<ParameterizedType, Deque<Variable>> releasedTempVariables = new HashMap<>();

    private final Variable thisVariable;

    private int nextTempVariableId;

    // This can only be constructed by a method definition
    Scope(Optional<ParameterizedType> thisType, Iterable<Parameter> parameters)
    {
        if (thisType.isPresent()) {
            thisVariable = new Variable("this", thisType.get());
            variables.put("this", thisVariable);
            allVariables.add(thisVariable);
        }
        else {
            thisVariable = null;
        }

        for (Parameter parameter : parameters) {
            variables.put(parameter.getName(), parameter);
            allVariables.add(parameter);
        }
    }

    public List<Variable> getVariables()
    {
        return ImmutableList.copyOf(allVariables);
    }

    public Variable createTempVariable(Class<?> type)
    {
        // reserve a slot for this variable
        Variable variable = new Variable("temp_" + nextTempVariableId, type(type));
        nextTempVariableId += Type.getType(type(type).getType()).getSize();

        tempVariables.put(variable.getName(), variable);
        allVariables.add(variable);

        return variable;
    }

    public Variable getOrCreateTempVariable(Class<?> type)
    {
        Deque<Variable> typeVariables = releasedTempVariables.get(type(type));
        if (typeVariables == null || typeVariables.isEmpty()) {
            return createTempVariable(type);
        }
        return typeVariables.pop();
    }

    public void releaseTempVariableForReuse(Variable tempVariable)
    {
        requireNonNull(tempVariable, "tempVariable is null");
        checkArgument(tempVariable == tempVariables.get(tempVariable.getName()), "invalid tempVariable release: %s", tempVariable);
        releasedTempVariables.computeIfAbsent(tempVariable.getType(), ignored -> new LinkedList<>()).push(tempVariable);
    }

    public Variable getTempVariable(String name)
    {
        Variable variable = tempVariables.get(name);
        checkArgument(variable != null, "Temp variable '%s' not defined", name);
        return variable;
    }

    public Variable getThis()
    {
        checkState(thisVariable != null, "Static methods do not have a 'this' variable");
        return thisVariable;
    }

    public Variable getVariable(String name)
    {
        Variable variable = variables.get(name);
        checkArgument(variable != null, "Variable '%s' not defined", name);
        return variable;
    }

    public Variable declareVariable(Class<?> type, String variableName)
    {
        return declareVariable(type(type), variableName);
    }

    public Variable declareVariable(ParameterizedType type, String variableName)
    {
        requireNonNull(type, "type is null");
        requireNonNull(variableName, "variableName is null");
        checkArgument(!variables.containsKey(variableName), "There is already a variable named %s", variableName);
        checkArgument(!variableName.equals("this"), "The 'this' variable can not be declared");

        Variable variable = new Variable(variableName, type);

        variables.put(variableName, variable);
        allVariables.add(variable);

        return variable;
    }

    public Variable declareVariable(String variableName, BytecodeBlock block, BytecodeExpression initialValue)
    {
        Variable variable = declareVariable(initialValue.getType(), variableName);
        block.append(variable.set(initialValue));
        return variable;
    }
}
