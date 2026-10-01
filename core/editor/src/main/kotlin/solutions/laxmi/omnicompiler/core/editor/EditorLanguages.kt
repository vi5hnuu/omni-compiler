package solutions.laxmi.omnicompiler.core.editor

import android.content.Context
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.DefaultGrammarDefinition
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import org.eclipse.tm4e.core.registry.IGrammarSource
import org.eclipse.tm4e.core.registry.IThemeSource
import org.json.JSONObject
import solutions.laxmi.omnicompiler.core.model.EditorTheme

/** A TextMate grammar bundled under `assets/textmate`. */
@JvmInline
value class GrammarId(val scopeName: String)

/**
 * Process-wide TextMate setup. Themes (one per [EditorTheme]) are registered once; grammars are parsed only
 * when a file of that language is first opened, together with the bundled grammars it includes. Sora's
 * registries are singletons, so every call here blocks and must run off the main thread.
 */
object EditorLanguages {

    private class Entry(val name: String, val grammarPath: String, val configPath: String?, val dependencies: List<String>)

    @Volatile private var definitions: Map<String, Entry>? = null
    private val loadedScopes = HashSet<String>()

    /** True once the editor themes are registered (the view's colour scheme depends on them). */
    val themesReady: Boolean get() = definitions != null

    /** Registers the editor themes and reads the grammar index; cheap, and done once per process. */
    fun ensureThemes(context: Context) {
        definitions ?: initialize(context.applicationContext)
    }

    /** Makes [grammar] (and the themes) ready for `TextMateLanguage.create`; `null` only prepares themes. */
    fun ensureLoaded(context: Context, grammar: GrammarId?) {
        val index = definitions ?: initialize(context.applicationContext)
        if (grammar != null) synchronized(this) { load(index, grammar.scopeName) }
    }

    private fun initialize(context: Context): Map<String, Entry> = synchronized(this) {
        definitions?.let { return it }
        FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(context.assets))
        val themes = ThemeRegistry.getInstance()
        EditorTheme.entries.filter { it != EditorTheme.AUTO }.forEach { theme ->
            val json = theme.palette().toTextMateThemeJson(theme.name)
            val model = ThemeModel(IThemeSource.fromString(IThemeSource.ContentType.JSON, json), theme.name)
            model.isDark = theme.palette().isDark
            themes.loadTheme(model)
        }
        readIndex(context).also { definitions = it }
    }

    /** Parses `languages.json` only (a few KB); grammar files stay unread until needed. */
    private fun readIndex(context: Context): Map<String, Entry> {
        val raw = context.assets.open(INDEX).bufferedReader().use { it.readText() }
        val languages = JSONObject(raw).getJSONArray("languages")
        return (0 until languages.length()).associate { i ->
            val item = languages.getJSONObject(i)
            val dependencies = item.optJSONArray("dependencies")
            item.getString("scopeName") to Entry(
                name = item.getString("name"),
                grammarPath = item.getString("grammar"),
                configPath = item.optString("languageConfiguration").ifEmpty { null },
                dependencies = List(dependencies?.length() ?: 0) { dependencies!!.getString(it) },
            )
        }
    }

    /** Caller holds the lock. Dependencies are marked first so include cycles terminate. */
    private fun load(index: Map<String, Entry>, scopeName: String) {
        if (!loadedScopes.add(scopeName)) return
        val entry = index[scopeName] ?: return
        try {
            entry.dependencies.forEach { load(index, it) }
            val stream = FileProviderRegistry.getInstance().tryGetInputStream(entry.grammarPath)
                ?: error("Missing bundled grammar ${entry.grammarPath}")
            val source = IGrammarSource.fromInputStream(stream, entry.grammarPath, Charsets.UTF_8)
            GrammarRegistry.getInstance().loadGrammar(
                DefaultGrammarDefinition.withLanguageConfiguration(source, entry.configPath, entry.name, scopeName),
            )
        } catch (e: Exception) {
            loadedScopes.remove(scopeName)
            throw e
        }
    }

    /**
     * Grammar for a file. The entry file is resolved by its runtime's language family because
     * extensions are ambiguous (`.pl` is Perl or Prolog); extra files go by extension first.
     */
    fun grammarFor(fileName: String, languageBase: String?, isEntry: Boolean): GrammarId? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val byBase = languageBase?.let { BASE_TO_GRAMMAR[it] }
        // Some files are known by their whole name rather than an extension (Makefile, Dockerfile).
        val byExt = NAME_TO_GRAMMAR[fileName.lowercase()] ?: EXTENSION_TO_GRAMMAR[ext]
        val name = if (isEntry) byBase ?: byExt else byExt ?: if (ext in PLAIN_EXTENSIONS) null else byBase
        return name?.let { SCOPES[it] }?.let(::GrammarId)
    }

    private const val INDEX = "textmate/languages.json"

    private val PLAIN_EXTENSIONS = setOf("txt", "in", "out", "csv", "tsv", "dat")

    /** Grammar file name → scope, matching `textmate/languages.json`. */
    private val SCOPES = mapOf(
        "awk" to "source.awk", "shellscript" to "source.shell", "fish" to "source.fish", "c" to "source.c",
        "clojure" to "source.clojure", "cobol" to "source.cobol", "coffee" to "source.coffee", "cpp" to "source.cpp",
        "crystal" to "source.crystal", "csharp" to "source.cs", "d" to "source.d", "dart" to "source.dart",
        "erlang" to "source.erlang", "fortran-free-form" to "source.fortran.free", "fsharp" to "source.fsharp",
        "go" to "source.go", "groovy" to "source.groovy", "scheme" to "source.scheme", "haskell" to "source.haskell",
        "java" to "source.java", "javascript" to "source.js", "julia" to "source.julia", "kotlin" to "source.kotlin",
        "common-lisp" to "source.commonlisp", "lua" to "source.lua", "asm" to "source.asm.x86_64", "ocaml" to "source.ocaml",
        "pascal" to "source.pascal", "perl" to "source.perl", "php" to "source.php", "powershell" to "source.powershell",
        "prolog" to "source.prolog", "python" to "source.python", "r" to "source.r", "raku" to "source.perl.6",
        "ruby" to "source.ruby", "rust" to "source.rust", "scala" to "source.scala", "smalltalk" to "source.smalltalk.gnu",
        "sql" to "source.sql", "swift" to "source.swift", "typescript" to "source.ts", "v" to "source.v",
        "wasm" to "source.wat", "zig" to "source.zig",
        "elixir" to "source.elixir", "nim" to "source.nim", "matlab" to "source.matlab", "html" to "text.html.basic",
        "css" to "source.css", "markdown" to "text.html.markdown", "json" to "source.json", "yaml" to "source.yaml",
        "xml" to "text.xml", "toml" to "source.toml", "make" to "source.makefile", "docker" to "source.dockerfile",
        "ini" to "source.ini",
    )

    /** ls-judge language family → grammar. Families without a permissive grammar are absent (plain text). */
    private val BASE_TO_GRAMMAR = mapOf(
        "awk" to "awk", "bash" to "shellscript", "zsh" to "shellscript", "fish" to "fish", "c" to "c", "cpp" to "cpp",
        "clojure" to "clojure", "cobol" to "cobol", "coffeescript" to "coffee", "crystal" to "crystal", "csharp" to "csharp",
        "d" to "d", "dart" to "dart", "erlang" to "erlang", "fortran" to "fortran-free-form", "fsharp" to "fsharp",
        "go" to "go", "groovy" to "groovy", "guile" to "scheme", "haskell" to "haskell", "java" to "java",
        "javascript" to "javascript", "deno" to "typescript", "bun" to "javascript", "julia" to "julia", "kotlin" to "kotlin",
        "lisp" to "common-lisp", "lua" to "lua", "nasm" to "asm", "ocaml" to "ocaml", "pascal" to "pascal", "perl" to "perl",
        "php" to "php", "powershell" to "powershell", "prolog" to "prolog", "python" to "python", "pypy" to "python",
        "r" to "r", "raku" to "raku", "ruby" to "ruby", "rust" to "rust", "scala" to "scala", "smalltalk" to "smalltalk",
        "sqlite" to "sql", "swift" to "swift", "typescript" to "typescript", "vlang" to "v", "wasm" to "wasm", "zig" to "zig",
        // Racket and Octave borrow their closest permissively licensed grammars (Scheme, MATLAB).
        "elixir" to "elixir", "nim" to "nim", "octave" to "matlab", "racket" to "scheme",
    )

    private val EXTENSION_TO_GRAMMAR = mapOf(
        "c" to "c", "h" to "cpp", "hh" to "cpp", "hpp" to "cpp", "hxx" to "cpp", "cc" to "cpp", "cpp" to "cpp", "cxx" to "cpp",
        "py" to "python", "java" to "java", "js" to "javascript", "mjs" to "javascript", "cjs" to "javascript",
        "ts" to "typescript", "go" to "go", "rs" to "rust", "rb" to "ruby", "php" to "php", "kt" to "kotlin", "kts" to "kotlin",
        "scala" to "scala", "sc" to "scala", "swift" to "swift", "cs" to "csharp", "dart" to "dart", "hs" to "haskell",
        "ml" to "ocaml", "mli" to "ocaml", "lua" to "lua", "pl" to "perl", "pm" to "perl", "r" to "r", "sql" to "sql",
        "sh" to "shellscript", "bash" to "shellscript", "zsh" to "shellscript", "fish" to "fish", "clj" to "clojure",
        "cljs" to "clojure", "coffee" to "coffee", "cr" to "crystal", "d" to "d", "erl" to "erlang", "hrl" to "erlang",
        "f90" to "fortran-free-form", "f95" to "fortran-free-form", "f03" to "fortran-free-form", "f" to "fortran-free-form",
        "fs" to "fsharp", "fsx" to "fsharp", "groovy" to "groovy", "scm" to "scheme", "ss" to "scheme", "jl" to "julia",
        "lisp" to "common-lisp", "cl" to "common-lisp", "lsp" to "common-lisp", "asm" to "asm", "nasm" to "asm", "s" to "asm",
        "pas" to "pascal", "pp" to "pascal", "ps1" to "powershell", "pro" to "prolog", "raku" to "raku", "rakumod" to "raku",
        "p6" to "raku", "st" to "smalltalk", "v" to "v", "wat" to "wasm", "wast" to "wasm", "zig" to "zig", "awk" to "awk",
        "cob" to "cobol", "cbl" to "cobol",
        "ex" to "elixir", "exs" to "elixir", "nim" to "nim", "nims" to "nim", "m" to "matlab", "rkt" to "scheme",
        "html" to "html", "htm" to "html", "css" to "css", "md" to "markdown", "markdown" to "markdown",
        "json" to "json", "yaml" to "yaml", "yml" to "yaml", "xml" to "xml", "svg" to "xml", "toml" to "toml",
        "mk" to "make", "ini" to "ini", "cfg" to "ini", "dockerfile" to "docker",
    )

    private val NAME_TO_GRAMMAR = mapOf(
        "makefile" to "make", "gnumakefile" to "make", "dockerfile" to "docker",
    )
}
