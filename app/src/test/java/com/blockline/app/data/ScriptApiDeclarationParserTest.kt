package com.blockline.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptApiDeclarationParserTest {
    @Test
    fun parsesExportedDeclarationsAndMembers() {
        val source = """
            /** Controls a world. */
            export declare class World {
                /** Gets an entity by identifier. */
                getEntity(id: string): Entity | undefined;
                readonly isHardcore: boolean;
            }
            export declare const world: World;
            export declare function system(): void;
        """.trimIndent()

        val result = ScriptApiDeclarationParser.parse(source, "@minecraft/server", "2.10.0")
        assertEquals(listOf("World", "world", "system"), result.symbols.map { it.name })
        assertEquals("Controls a world.", result.symbols.first().documentation)
        assertEquals(listOf("getEntity", "isHardcore"), result.symbols.first().members.map { it.name })
        assertTrue(result.symbols.first().members.first().documentation.contains("Gets an entity"))
        assertTrue(result.symbols.first().members.first().signature.contains("Entity | undefined"))
    }

    @Test
    fun preservesUnparsedMembersAndRawSource() {
        val source = """
            export declare interface Options {
                name: string;
                [key: string]: unknown;
            }
        """.trimIndent()

        val result = ScriptApiDeclarationParser.parse(source, "@minecraft/server", "2.10.0")
        assertTrue(result.rawSource.contains("[key: string]"))
        assertTrue(result.symbols.single().rawDeclaration.contains("[key: string]"))
        assertTrue(result.symbols.single().unparsedMembers.any { it.contains("[key: string]") })
    }

    @Test
    fun toleratesMissingDocsAndEmptySource() {
        assertEquals("", ScriptApiDeclarationParser.parse("", "@minecraft/server", "2.10.0").rawSource)
        val parsed = ScriptApiDeclarationParser.parse(
            "export declare class Dimension { getBlock(location: Vector3): Block | undefined; }",
            "@minecraft/server",
            "2.10.0"
        )
        assertEquals("Dimension", parsed.symbols.single().name)
        assertEquals("getBlock", parsed.symbols.single().members.single().name)
    }

    @Test
    fun handlesFunctionTypeArrows() {
        val source = "export declare function run(callback: (value: string) => void): void;"
        val result = ScriptApiDeclarationParser.parse(source, "@minecraft/server", "2.10.0")
        assertEquals("function", result.symbols.single().kind)
        assertTrue(result.symbols.single().signature.contains("callback"))
    }
}
