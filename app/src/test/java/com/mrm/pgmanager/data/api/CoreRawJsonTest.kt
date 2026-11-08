package com.mrm.pgmanager.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoreRawJsonTest {
    @Test fun `preserves exact nested config including numeric precision and escaped delimiters`() {
        val config = """{ "large": 9223372036854775807123456789, "precise": 0.12345678901234567890123456789,
            "nested": [ { "message": "} [ \\\" , {" }, [true, null] ] }"""
        val response = """{"before":[{"a":1}],"config":$config,"after":{"x":"y"}}"""
        assertEquals(config, rawJsonObjectField(response, "config"))
    }

    @Test fun `decodes an escaped field name and ignores surrounding whitespace`() {
        val response = """  { "name":"ok", "\u0063onfig" : {"a":1}, "other":true }  """
        assertEquals("""{"a":1}""", rawJsonObjectField(response, "config"))
    }

    @Test fun `rejects missing non-object duplicate or ambiguous fields`() {
        assertNull(rawJsonObjectField("""{"config":null}""", "config"))
        assertNull(rawJsonObjectField("""{"config":[]}""", "config"))
        assertNull(rawJsonObjectField("""{"other":{}}""", "config"))
        assertNull(rawJsonObjectField("""{"config":{},"\u0063onfig":{"x":1}}""", "config"))
        assertNull(rawJsonObjectField("""{"name":1,"name":2,"config":{}}""", "config"))
    }

    @Test fun `rejects malformed nesting strings and trailing data`() {
        for (response in listOf(
            """{"config":{"x":[1,2}}""",
            """{"config":{"text":"unterminated}}""",
            """{"config":{},}""",
            """{"config":{}} garbage""",
            """{"config" {}}"""
        )) {
            assertNull(rawJsonObjectField(response, "config"))
        }
    }
}
