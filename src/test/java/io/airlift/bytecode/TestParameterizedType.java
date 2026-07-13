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

import org.junit.jupiter.api.Test;

import static io.airlift.bytecode.ParameterizedType.type;
import static io.airlift.bytecode.ParameterizedType.typeFromJavaClassName;
import static org.assertj.core.api.Assertions.assertThat;

class TestParameterizedType
{
    @Test
    void testGetSlotSize()
    {
        assertThat(type(long.class).getSlotSize()).isEqualTo(2);
        assertThat(type(double.class).getSlotSize()).isEqualTo(2);
        assertThat(type(void.class).getSlotSize()).isEqualTo(0);

        assertThat(type(boolean.class).getSlotSize()).isEqualTo(1);
        assertThat(type(byte.class).getSlotSize()).isEqualTo(1);
        assertThat(type(char.class).getSlotSize()).isEqualTo(1);
        assertThat(type(short.class).getSlotSize()).isEqualTo(1);
        assertThat(type(int.class).getSlotSize()).isEqualTo(1);
        assertThat(type(float.class).getSlotSize()).isEqualTo(1);

        assertThat(type(Long.class).getSlotSize()).isEqualTo(1);
        assertThat(type(Object.class).getSlotSize()).isEqualTo(1);
        assertThat(type(long[].class).getSlotSize()).isEqualTo(1);
        assertThat(typeFromJavaClassName("java.lang.String").getSlotSize()).isEqualTo(1);
    }

    @Test
    void testTypeDescriptor()
    {
        assertThat(type(boolean.class).getType()).isEqualTo("Z");
        assertThat(type(byte.class).getType()).isEqualTo("B");
        assertThat(type(char.class).getType()).isEqualTo("C");
        assertThat(type(short.class).getType()).isEqualTo("S");
        assertThat(type(int.class).getType()).isEqualTo("I");
        assertThat(type(long.class).getType()).isEqualTo("J");
        assertThat(type(float.class).getType()).isEqualTo("F");
        assertThat(type(double.class).getType()).isEqualTo("D");
        assertThat(type(void.class).getType()).isEqualTo("V");

        assertThat(type(Object.class).getType()).isEqualTo("Ljava/lang/Object;");
        assertThat(type(int[].class).getType()).isEqualTo("[I");
        assertThat(type(long[][].class).getType()).isEqualTo("[[J");
        assertThat(type(String[].class).getType()).isEqualTo("[Ljava/lang/String;");
    }
}
