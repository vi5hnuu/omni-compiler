package solutions.laxmi.omnicompiler.core.editor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import org.junit.Test
import org.junit.runner.RunWith

/** Guards the generated `textmate/languages.json`: every mapped grammar (and what it includes) loads lazily. */
@RunWith(AndroidJUnit4::class)
class EditorLanguagesTest {

    @Test
    fun everyMappedGrammarLoadsOnDemand() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = listOf(
            "a.awk", "a.sh", "a.fish", "a.c", "a.clj", "a.cob", "a.coffee", "a.cpp", "a.cr", "a.cs", "a.d", "a.dart",
            "a.erl", "a.f90", "a.fs", "a.go", "a.groovy", "a.scm", "a.hs", "a.java", "a.js", "a.jl", "a.kt", "a.lisp",
            "a.lua", "a.asm", "a.ml", "a.pas", "a.pl", "a.php", "a.ps1", "a.pro", "a.py", "a.r", "a.raku", "a.rb",
            "a.rs", "a.scala", "a.st", "a.sql", "a.swift", "a.ts", "a.v", "a.wat", "a.zig",
        )
        files.forEach { file ->
            val grammar = checkNotNull(EditorLanguages.grammarFor(file, languageBase = null, isEntry = false)) { file }
            EditorLanguages.ensureLoaded(context, grammar)
            TextMateLanguage.create(grammar.scopeName, false).destroy()
        }
    }
}
