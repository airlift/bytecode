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

import com.google.common.base.VerifyException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MutableCallSite;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.LongFunction;
import java.util.function.LongUnaryOperator;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodType.methodType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestFastMethodHandleProxies
{
    @Test
    void testBasic()
            throws ReflectiveOperationException
    {
        assertInterface(
                LongUnaryOperator.class,
                lookup().findStatic(getClass(), "increment", methodType(long.class, long.class)),
                addOne -> assertThat(addOne.applyAsLong(1)).isEqualTo(2L));
    }

    private static long increment(long x)
    {
        return x + 1;
    }

    @Test
    void testGeneric()
            throws ReflectiveOperationException
    {
        assertInterface(
                LongFunction.class,
                lookup().findStatic(getClass(), "incrementAndPrint", methodType(String.class, long.class)),
                print -> assertThat(print.apply(1)).isEqualTo("2"));
    }

    private static String incrementAndPrint(long x)
    {
        return String.valueOf(x + 1);
    }

    @Test
    void testObjectAndDefaultMethods()
            throws ReflectiveOperationException
    {
        assertInterface(
                StringLength.class,
                lookup().findStatic(getClass(), "stringLength", methodType(int.class, String.class)),
                length -> {
                    assertThat(length.length("abc")).isEqualTo(3);
                    assertThat(length.theAnswer()).isEqualTo(42);
                });
    }

    private static int stringLength(String s)
    {
        return s.length();
    }

    public interface StringLength
    {
        int length(String s);

        default int theAnswer()
        {
            return 42;
        }

        @Override
        String toString();
    }

    @Test
    void testUncheckedException()
            throws ReflectiveOperationException
    {
        assertInterface(
                Runnable.class,
                lookup().findStatic(getClass(), "throwUncheckedException", methodType(void.class)),
                runnable -> assertThatThrownBy(runnable::run)
                        .isInstanceOf(VerifyException.class));
    }

    private static void throwUncheckedException()
    {
        throw new VerifyException("unchecked");
    }

    @Test
    void testCheckedException()
            throws ReflectiveOperationException
    {
        assertInterface(
                Runnable.class,
                lookup().findStatic(getClass(), "throwCheckedException", methodType(void.class)),
                runnable -> assertThatThrownBy(runnable::run)
                        .isInstanceOf(UndeclaredThrowableException.class)
                        .hasCauseInstanceOf(IOException.class));
    }

    private static void throwCheckedException()
            throws IOException
    {
        throw new IOException("checked");
    }

    @Test
    void testMutableCallSite()
            throws ReflectiveOperationException
    {
        MethodHandle one = lookup().findStatic(getClass(), "one", methodType(int.class));
        MethodHandle two = lookup().findStatic(getClass(), "two", methodType(int.class));

        MutableCallSite callSite = new MutableCallSite(methodType(int.class));
        assertInterface(
                IntSupplier.class,
                callSite.dynamicInvoker(),
                supplier -> {
                    callSite.setTarget(one);
                    assertThat(supplier.getAsInt()).isEqualTo(1);
                    callSite.setTarget(two);
                    assertThat(supplier.getAsInt()).isEqualTo(2);
                });
    }

    private static int one()
    {
        return 1;
    }

    private static int two()
    {
        return 2;
    }

    private static <T> void assertInterface(Class<T> interfaceType, MethodHandle target, Consumer<T> consumer)
    {
        consumer.accept(MethodHandleProxies.asInterfaceInstance(interfaceType, target));
        consumer.accept(FastMethodHandleProxies.asInterfaceInstance(interfaceType, target));
    }
}
