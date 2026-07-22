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
import com.google.errorprone.annotations.Immutable;
import jakarta.annotation.Nullable;
import org.objectweb.asm.Type;

import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

@Immutable
public class ParameterizedType
{
    // Raw types are immutable and are looked up repeatedly while generating a class, so they
    // are cached per class. ClassValue ties each entry to the lifetime of its class, which
    // keeps generated classes collectable.
    private static final ClassValue<ParameterizedType> RAW_TYPES = new ClassValue<>()
    {
        @Override
        protected ParameterizedType computeValue(Class<?> type)
        {
            return new ParameterizedType(type);
        }
    };

    public static ParameterizedType typeFromJavaClassName(String className)
    {
        requireNonNull(className, "type is null");
        return new ParameterizedType(className.replace('.', '/'));
    }

    public static ParameterizedType typeFromPathName(String className)
    {
        requireNonNull(className, "type is null");
        return new ParameterizedType(className);
    }

    public static ParameterizedType type(Type type)
    {
        requireNonNull(type, "type is null");
        return new ParameterizedType(type.getInternalName());
    }

    public static ParameterizedType type(Class<?> type)
    {
        requireNonNull(type, "type is null");
        return RAW_TYPES.get(type);
    }

    public static ParameterizedType type(Class<?> type, Class<?>... parameters)
    {
        requireNonNull(type, "type is null");
        return new ParameterizedType(type, parameters);
    }

    public static ParameterizedType type(Class<?> type, ParameterizedType... parameters)
    {
        requireNonNull(type, "type is null");
        return new ParameterizedType(type, parameters);
    }

    private final String type;
    private final String className;
    private final String simpleName;
    private final List<String> parameters;

    private final boolean isInterface;
    @Nullable
    private final Class<?> primitiveType;
    @Nullable
    private final ParameterizedType arrayComponentType;

    // caches of deterministic computations; the benign race is harmless
    @Nullable
    private String javaClassName;
    @Nullable
    private String genericSignature;

    public ParameterizedType(String className)
    {
        requireNonNull(className, "className is null");
        checkArgument(!className.contains("."), "Invalid class name %s", className);
        checkArgument(!className.endsWith(";"), "Invalid class name %s", className);

        this.className = className;
        this.simpleName = className.substring(className.lastIndexOf("/") + 1);
        this.type = "L" + className + ";";
        this.parameters = ImmutableList.of();

        this.isInterface = false;
        this.primitiveType = null;
        this.arrayComponentType = null;
    }

    private ParameterizedType(Class<?> type)
    {
        requireNonNull(type, "type is null");
        this.type = toInternalIdentifier(type);
        this.className = getPathName(type);
        this.simpleName = type.getSimpleName();
        this.parameters = ImmutableList.of();

        this.isInterface = type.isInterface();
        this.primitiveType = type.isPrimitive() ? type : null;
        this.arrayComponentType = type.isArray() ? type(type.getComponentType()) : null;
    }

    private ParameterizedType(Class<?> type, Class<?>... parameters)
    {
        requireNonNull(type, "type is null");
        this.type = toInternalIdentifier(type);
        this.className = getPathName(type);
        this.simpleName = type.getSimpleName();

        ImmutableList.Builder<String> builder = ImmutableList.builderWithExpectedSize(parameters.length);
        for (Class<?> parameter : parameters) {
            builder.add(toInternalIdentifier(parameter));
        }
        this.parameters = builder.build();

        this.isInterface = type.isInterface();
        this.primitiveType = type.isPrimitive() ? type : null;
        this.arrayComponentType = type.isArray() ? type(type.getComponentType()) : null;
    }

    private ParameterizedType(Class<?> type, ParameterizedType... parameters)
    {
        requireNonNull(type, "type is null");
        this.type = toInternalIdentifier(type);
        this.className = getPathName(type);
        this.simpleName = type.getSimpleName();

        ImmutableList.Builder<String> builder = ImmutableList.builderWithExpectedSize(parameters.length);
        for (ParameterizedType parameter : parameters) {
            builder.add(parameter.toString());
        }
        this.parameters = builder.build();

        this.isInterface = type.isInterface();
        this.primitiveType = type.isPrimitive() ? type : null;
        this.arrayComponentType = type.isArray() ? type(type.getComponentType()) : null;
    }

    public String getClassName()
    {
        return className;
    }

    public String getJavaClassName()
    {
        String javaClassName = this.javaClassName;
        if (javaClassName == null) {
            javaClassName = className.replace('/', '.');
            this.javaClassName = javaClassName;
        }
        return javaClassName;
    }

    public String getSimpleName()
    {
        return simpleName;
    }

    public String getType()
    {
        return type;
    }

    public Type getAsmType()
    {
        return Type.getObjectType(className);
    }

    public String getGenericSignature()
    {
        String genericSignature = this.genericSignature;
        if (genericSignature == null) {
            genericSignature = computeGenericSignature();
            this.genericSignature = genericSignature;
        }
        return genericSignature;
    }

    private String computeGenericSignature()
    {
        if (primitiveType != null || arrayComponentType != null) {
            return type;
        }
        StringBuilder sb = new StringBuilder();
        sb.append('L').append(className);
        if (!parameters.isEmpty()) {
            sb.append("<");
            for (String parameterType : parameters) {
                sb.append(parameterType);
            }
            sb.append(">");
        }
        sb.append(";");
        return sb.toString();
    }

    public boolean isGeneric()
    {
        return !parameters.isEmpty();
    }

    public boolean isInterface()
    {
        return isInterface;
    }

    @Nullable
    public Class<?> getPrimitiveType()
    {
        return primitiveType;
    }

    public boolean isPrimitive()
    {
        return primitiveType != null;
    }

    /**
     * Number of local variable or operand stack slots a value of this type
     * occupies: 2 for {@code long} and {@code double}, 0 for {@code void},
     * and 1 otherwise.
     */
    public int getSlotSize()
    {
        if (primitiveType == long.class || primitiveType == double.class) {
            return 2;
        }
        if (primitiveType == void.class) {
            return 0;
        }
        return 1;
    }

    @Nullable
    public ParameterizedType getArrayComponentType()
    {
        return arrayComponentType;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ParameterizedType that = (ParameterizedType) o;
        return type.equals(that.type);
    }

    @Override
    public int hashCode()
    {
        return type.hashCode();
    }

    @Override
    public String toString()
    {
        return getGenericSignature();
    }

    public static String getPathName(Class<?> n)
    {
        return n.getName().replace('.', '/');
    }

    private static String toInternalIdentifier(Class<?> clazz)
    {
        if (clazz.isArray()) {
            return "[" + toInternalIdentifier(clazz.getComponentType());
        }
        if (clazz.isPrimitive()) {
            return switch (clazz.getName()) {
                case "boolean" -> "Z";
                case "byte" -> "B";
                case "char" -> "C";
                case "short" -> "S";
                case "int" -> "I";
                case "long" -> "J";
                case "float" -> "F";
                case "double" -> "D";
                case "void" -> "V";
                default -> throw new IllegalArgumentException("Unrecognized type in compiler: " + clazz.getName());
            };
        }
        return "L" + getPathName(clazz) + ";";
    }
}
