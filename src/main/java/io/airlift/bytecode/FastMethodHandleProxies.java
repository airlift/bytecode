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
import com.google.common.collect.ImmutableMap;
import io.airlift.bytecode.control.TryCatch;
import io.airlift.bytecode.control.TryCatch.CatchBlock;
import io.airlift.bytecode.expression.BytecodeExpression;
import io.airlift.bytecode.instruction.InvokeInstruction.BootstrapMethod;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.MoreCollectors.onlyElement;
import static io.airlift.bytecode.Access.FINAL;
import static io.airlift.bytecode.Access.PUBLIC;
import static io.airlift.bytecode.Access.STATIC;
import static io.airlift.bytecode.Access.SYNTHETIC;
import static io.airlift.bytecode.Access.a;
import static io.airlift.bytecode.BytecodeUtils.uniqueClassName;
import static io.airlift.bytecode.ClassGenerator.classGenerator;
import static io.airlift.bytecode.Parameter.arg;
import static io.airlift.bytecode.ParameterizedType.type;
import static io.airlift.bytecode.ParameterizedType.typeFromJavaClassName;
import static io.airlift.bytecode.expression.BytecodeExpressions.constantClass;
import static io.airlift.bytecode.expression.BytecodeExpressions.constantLong;
import static io.airlift.bytecode.expression.BytecodeExpressions.constantString;
import static io.airlift.bytecode.expression.BytecodeExpressions.invokeDynamic;
import static io.airlift.bytecode.expression.BytecodeExpressions.invokeStatic;
import static io.airlift.bytecode.expression.BytecodeExpressions.newArray;
import static io.airlift.bytecode.expression.BytecodeExpressions.newInstance;
import static java.lang.invoke.MethodType.methodType;
import static java.util.Arrays.stream;

public final class FastMethodHandleProxies
{
    private static final String BASE_PACKAGE = FastMethodHandleProxies.class.getPackage().getName() + ".proxy";

    private static final Method LOOKUP_FIND_VIRTUAL;
    private static final Method LOOKUP_LOOKUP_CLASS;
    private static final Method CLASS_GET_CLASSLOADER;
    private static final Method CLASSLOADER_GET_CLASS;
    private static final Method METHODTYPE;
    private static final Method METHODHANDLE_INVOKE;
    private static final Method MAP_GET;

    static {
        try {
            LOOKUP_FIND_VIRTUAL = Lookup.class.getMethod("findVirtual", Class.class, String.class, MethodType.class);
            LOOKUP_LOOKUP_CLASS = Lookup.class.getMethod("lookupClass");
            CLASS_GET_CLASSLOADER = Class.class.getMethod("getClassLoader");
            CLASSLOADER_GET_CLASS = ClassLoader.class.getMethod("getClass");
            METHODTYPE = MethodType.class.getMethod("methodType", Class.class);
            METHODHANDLE_INVOKE = MethodHandle.class.getMethod("invokeWithArguments", Object[].class);
            MAP_GET = Map.class.getMethod("get", Object.class);
        }
        catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }

    private FastMethodHandleProxies() {}

    /**
     * Faster version of {@link MethodHandleProxies#asInterfaceInstance(Class, MethodHandle)}.
     *
     * @param <T> the desired type of the wrapper, a single-method interface
     * @param type a class object representing {@code T}
     * @param target the method handle to invoke from the wrapper
     * @return a correctly-typed wrapper for the given target
     */
    public static <T> T asInterfaceInstance(Class<T> type, MethodHandle target)
    {
        String className = uniqueClassName(BASE_PACKAGE, type.getSimpleName()).getClassName();
        return asInterfaceInstance(className, type, target);
    }

    /**
     * Faster version of {@link MethodHandleProxies#asInterfaceInstance(Class, MethodHandle)}.
     *
     * @param <T> the desired type of the wrapper, a single-method interface
     * @param className the name of the generated class
     * @param type a class object representing {@code T}
     * @param target the method handle to invoke from the wrapper
     * @return a correctly-typed wrapper for the given target
     */
    public static <T> T asInterfaceInstance(String className, Class<T> type, MethodHandle target)
    {
        checkArgument(type.isInterface() && Modifier.isPublic(type.getModifiers()), "not a public interface: %s", type.getName());

        ClassDefinition classDefinition = new ClassDefinition(
                a(PUBLIC, FINAL, SYNTHETIC),
                typeFromJavaClassName(className),
                type(Object.class),
                type(type));

        classDefinition.declareDefaultConstructor(a(PUBLIC));

        Method method = getSingleAbstractMethod(type);
        target = target.asType(methodType(method.getReturnType(), method.getParameterTypes()));

        defineProxyMethod(classDefinition, method);

        defineBootstrapMethod(classDefinition);

        DynamicClassLoader dynamicClassLoader = new DynamicClassLoader(type.getClassLoader(), ImmutableMap.of(0L, target));
        Class<? extends T> newClass = classGenerator(dynamicClassLoader).defineClass(classDefinition, type);
        try {
            return newClass.getDeclaredConstructor().newInstance();
        }
        catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void defineProxyMethod(ClassDefinition classDefinition, Method target)
    {
        List<Parameter> parameters = new ArrayList<>();
        Class<?>[] parameterTypes = target.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            parameters.add(arg("arg" + i, parameterTypes[i]));
        }

        MethodDefinition method = classDefinition.declareMethod(
                a(PUBLIC),
                target.getName(),
                type(target.getReturnType()),
                parameters);

        BytecodeNode invocation = invokeDynamic(
                new BootstrapMethod(
                        classDefinition.getType(),
                        "$bootstrap",
                        type(CallSite.class),
                        ImmutableList.of(type(Lookup.class), type(String.class), type(MethodType.class))),
                ImmutableList.of(),
                target.getName(),
                type(target.getReturnType()),
                parameters.stream()
                        .map(BytecodeExpression::getType)
                        .collect(toImmutableList()),
                parameters)
                .ret();

        ImmutableList.Builder<ParameterizedType> exceptionTypes = ImmutableList.builder();
        exceptionTypes.add(type(RuntimeException.class), type(Error.class));
        for (Class<?> exceptionType : target.getExceptionTypes()) {
            method.addException(exceptionType.asSubclass(Throwable.class));
            exceptionTypes.add(type(exceptionType));
        }

        BytecodeNode throwUndeclared = new BytecodeBlock()
                .newObject(UndeclaredThrowableException.class)
                .append(OpCode.DUP_X1)
                .swap()
                .invokeConstructor(UndeclaredThrowableException.class, Throwable.class)
                .throwObject();

        invocation = new TryCatch(invocation, ImmutableList.of(
                new CatchBlock(new BytecodeBlock().throwObject(), exceptionTypes.build()),
                new CatchBlock(throwUndeclared, ImmutableList.of())));

        method.getBody().append(invocation);
    }

    private static void defineBootstrapMethod(ClassDefinition classDefinition)
    {
        Parameter callerLookup = arg("callerLookup", Lookup.class);

        MethodDefinition method = classDefinition.declareMethod(
                a(PUBLIC, STATIC),
                "$bootstrap",
                type(CallSite.class),
                callerLookup,
                arg("name", String.class),
                arg("type", MethodType.class));

        BytecodeExpression classLoader = callerLookup
                .invoke(LOOKUP_LOOKUP_CLASS)
                .invoke(CLASS_GET_CLASSLOADER);

        BytecodeExpression getCallSiteBindingsHandle = callerLookup.invoke(
                LOOKUP_FIND_VIRTUAL,
                classLoader.invoke(CLASSLOADER_GET_CLASS),
                constantString("getCallSiteBindings"),
                invokeStatic(METHODTYPE, constantClass(Map.class)));

        BytecodeExpression callSiteBindings = getCallSiteBindingsHandle.invoke(
                METHODHANDLE_INVOKE,
                newArray(type(Object[].class), ImmutableList.of(classLoader)));

        BytecodeExpression binding = callSiteBindings
                .cast(Map.class)
                .invoke(MAP_GET, constantLong(0).cast(Object.class))
                .cast(MethodHandle.class);

        BytecodeExpression callSite = newInstance(ConstantCallSite.class, binding);

        method.getBody().append(callSite.ret());
    }

    public static <T> Method getSingleAbstractMethod(Class<T> type)
    {
        return stream(type.getMethods())
                .filter(method -> Modifier.isAbstract(method.getModifiers()))
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> method.getDeclaringClass() != Object.class)
                .filter(FastMethodHandleProxies::notJavaObjectMethod)
                .collect(onlyElement());
    }

    private static boolean notJavaObjectMethod(Method method)
    {
        return notMethodMatches(method, "toString", String.class) &&
                notMethodMatches(method, "hashCode", int.class) &&
                notMethodMatches(method, "equals", boolean.class, Object.class);
    }

    private static boolean notMethodMatches(Method method, String name, Class<?> returnType, Class<?>... parameterTypes)
    {
        return method.getParameterCount() != parameterTypes.length ||
                method.getReturnType() != returnType ||
                !name.equals(method.getName()) ||
                !Arrays.equals(method.getParameterTypes(), parameterTypes);
    }
}
