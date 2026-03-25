package org.puppylab.mypassword.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ParserTest {

    @BeforeEach
    void setUp() throws Exception {
    }

    @Test
    void testParse() {
        assertEquals(List.of(), Parser.parse(""));
        assertEquals(List.of(), Parser.parse(" "));
        assertEquals(List.of(), Parser.parse(" \n "));
        assertEquals(List.of("hello"), Parser.parse("hello"));
        assertEquals(List.of("hello"), Parser.parse(" hello "));
        assertEquals(List.of("hello"), Parser.parse(" hello \t\n "));
        assertEquals(List.of("hello", "world"), Parser.parse("hello world"));
        assertEquals(List.of("hello", "world"), Parser.parse(" hello\tworld "));
        assertEquals(List.of("hello", "world"), Parser.parse(" hello\nworld "));
        assertEquals(List.of("hello", "Mr Bob"), Parser.parse(" hello \"Mr Bob\" "));
        assertEquals(List.of("hello", "Mr Bob "), Parser.parse(" hello \"Mr Bob ")); // missing end quote
        assertEquals(List.of("hello", "Mr  Bob"), Parser.parse(" hello \"Mr  Bob\" "));
        assertEquals(List.of("hello", "Mr Bob", "and", "Ms Alice"),
                Parser.parse(" hello \"Mr Bob\"  and  \"Ms Alice\" "));
    }
}
