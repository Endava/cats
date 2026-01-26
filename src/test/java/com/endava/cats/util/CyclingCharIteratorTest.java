package com.endava.cats.util;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class CyclingCharIteratorTest {

    @Test
    void shouldReturnFirstCharacter() {
        CyclingCharIterator iterator = new CyclingCharIterator('a', 'b', 'c');

        char result = iterator.next();

        assertThat(result).isEqualTo('a');
    }

    @Test
    void shouldCycleThroughCharacters() {
        CyclingCharIterator iterator = new CyclingCharIterator('a', 'b', 'c');

        assertThat(iterator.next()).isEqualTo('a');
        assertThat(iterator.next()).isEqualTo('b');
        assertThat(iterator.next()).isEqualTo('c');
    }

    @Test
    void shouldWrapAroundAfterLastCharacter() {
        CyclingCharIterator iterator = new CyclingCharIterator('a', 'b', 'c');

        iterator.next();
        iterator.next();
        iterator.next();
        char result = iterator.next();

        assertThat(result).isEqualTo('a');
    }

    @Test
    void shouldContinueCyclingIndefinitely() {
        CyclingCharIterator iterator = new CyclingCharIterator('x', 'y');

        for (int i = 0; i < 10; i++) {
            char result = iterator.next();
            assertThat(result).isIn('x', 'y');
        }
    }

    @Test
    void shouldHandleSingleCharacter() {
        CyclingCharIterator iterator = new CyclingCharIterator('z');

        assertThat(iterator.next()).isEqualTo('z');
        assertThat(iterator.next()).isEqualTo('z');
        assertThat(iterator.next()).isEqualTo('z');
    }

    @Test
    void shouldHandleSpecialCharacters() {
        CyclingCharIterator iterator = new CyclingCharIterator('\\', '|', '/', '-');

        assertThat(iterator.next()).isEqualTo('\\');
        assertThat(iterator.next()).isEqualTo('|');
        assertThat(iterator.next()).isEqualTo('/');
        assertThat(iterator.next()).isEqualTo('-');
        assertThat(iterator.next()).isEqualTo('\\');
    }

    @Test
    void shouldMaintainStateAcrossMultipleCalls() {
        CyclingCharIterator iterator = new CyclingCharIterator('1', '2', '3');

        assertThat(iterator.next()).isEqualTo('1');
        assertThat(iterator.next()).isEqualTo('2');

        char result = iterator.next();
        assertThat(result).isEqualTo('3');

        assertThat(iterator.next()).isEqualTo('1');
    }

    @Test
    void shouldHandleRepeatedCharacters() {
        CyclingCharIterator iterator = new CyclingCharIterator('a', 'a', 'b');

        assertThat(iterator.next()).isEqualTo('a');
        assertThat(iterator.next()).isEqualTo('a');
        assertThat(iterator.next()).isEqualTo('b');
        assertThat(iterator.next()).isEqualTo('a');
    }

    @Test
    void shouldNotModifyOriginalArray() {
        char[] original = {'a', 'b', 'c'};
        CyclingCharIterator iterator = new CyclingCharIterator(original);

        iterator.next();
        iterator.next();

        assertThat(original[0]).isEqualTo('a');
        assertThat(original[1]).isEqualTo('b');
        assertThat(original[2]).isEqualTo('c');
    }

    @Test
    void shouldHandleMultipleIterators() {
        CyclingCharIterator iterator1 = new CyclingCharIterator('a', 'b');
        CyclingCharIterator iterator2 = new CyclingCharIterator('x', 'y');

        assertThat(iterator1.next()).isEqualTo('a');
        assertThat(iterator2.next()).isEqualTo('x');
        assertThat(iterator1.next()).isEqualTo('b');
        assertThat(iterator2.next()).isEqualTo('y');
    }

    @Test
    void shouldCycleCorrectlyAfterManyIterations() {
        CyclingCharIterator iterator = new CyclingCharIterator('a', 'b', 'c');

        for (int i = 0; i < 100; i++) {
            iterator.next();
        }

        assertThat(iterator.next()).isEqualTo('b');
    }
}
