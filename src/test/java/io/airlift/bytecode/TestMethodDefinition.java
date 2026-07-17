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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.bytecode.Access.FINAL;
import static io.airlift.bytecode.Access.PUBLIC;
import static io.airlift.bytecode.Access.STATIC;
import static io.airlift.bytecode.Access.a;
import static io.airlift.bytecode.Parameter.arg;
import static io.airlift.bytecode.ParameterizedType.type;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestMethodDefinition
{
    @Test
    void testParameterSlotLimit()
    {
        ClassDefinition classDefinition = new ClassDefinition(
                a(PUBLIC, FINAL),
                "test/ParameterSlots",
                type(Object.class));

        // 127 long parameters use 254 slots, plus one slot for "this"
        List<Parameter> longs = IntStream.range(0, 127)
                .mapToObj(i -> arg("p" + i, long.class))
                .collect(toImmutableList());

        assertThatCode(() -> classDefinition.declareMethod(a(PUBLIC), "instanceAtLimit", type(void.class), longs))
                .doesNotThrowAnyException();

        List<Parameter> tooMany = ImmutableList.<Parameter>builder()
                .addAll(longs)
                .add(arg("extra", int.class))
                .build();

        assertThatThrownBy(() -> classDefinition.declareMethod(a(PUBLIC), "instanceOverLimit", type(void.class), tooMany))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("256 slots used");

        // a static method does not use a slot for "this"
        assertThatCode(() -> classDefinition.declareMethod(a(PUBLIC, STATIC), "staticAtLimit", type(void.class), tooMany))
                .doesNotThrowAnyException();
    }

    @Test
    void testToSourceString()
    {
        ClassDefinition classDefinition = new ClassDefinition(
                a(PUBLIC),
                "test/Example",
                type(Object.class));

        MethodDefinition method = classDefinition.declareMethod(
                a(PUBLIC, STATIC),
                "add",
                type(int.class),
                ImmutableList.of(arg("a", int.class), arg("b", int.class)));

        assertThat(method.toSourceString()).isEqualTo("public static int add(int a, int b)");
    }
}
