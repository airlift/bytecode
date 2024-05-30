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
import io.airlift.bytecode.MethodDefinition;
import io.airlift.bytecode.Parameter;
import io.airlift.bytecode.ParameterizedType;

import java.lang.reflect.Method;
import java.util.List;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.bytecode.ParameterizedType.type;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;

public class BootstrapMethod
{
    private final ParameterizedType ownerClass;
    private final String name;
    private final ParameterizedType returnType;
    private final List<ParameterizedType> parameterTypes;

    public BootstrapMethod(ParameterizedType ownerClass, String name, ParameterizedType returnType, List<ParameterizedType> parameterTypes)
    {
        this.ownerClass = requireNonNull(ownerClass, "ownerClass is null");
        this.name = requireNonNull(name, "name is null");
        this.returnType = requireNonNull(returnType, "returnType is null");
        this.parameterTypes = ImmutableList.copyOf(requireNonNull(parameterTypes, "parameterTypes is null"));
    }

    public ParameterizedType getOwnerClass()
    {
        return ownerClass;
    }

    public String getName()
    {
        return name;
    }

    public ParameterizedType getReturnType()
    {
        return returnType;
    }

    public List<ParameterizedType> getParameterTypes()
    {
        return parameterTypes;
    }

    public static BootstrapMethod from(Method method)
    {
        return new BootstrapMethod(
                type(method.getDeclaringClass()),
                method.getName(),
                type(method.getReturnType()),
                stream(method.getParameterTypes())
                        .map(ParameterizedType::type)
                        .collect(toImmutableList()));
    }

    public static BootstrapMethod from(MethodDefinition method)
    {
        return new BootstrapMethod(
                method.getDeclaringClass().getType(),
                method.getName(),
                method.getReturnType(),
                method.getParameters().stream()
                        .map(Parameter::getType)
                        .collect(toImmutableList()));
    }
}
