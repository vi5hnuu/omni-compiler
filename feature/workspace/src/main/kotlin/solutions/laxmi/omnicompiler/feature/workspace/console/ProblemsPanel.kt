package solutions.laxmi.omnicompiler.feature.workspace.console

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp
import solutions.laxmi.omnicompiler.core.designsystem.component.EmptyState
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTextButton
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.core.model.CompileProblem
import solutions.laxmi.omnicompiler.core.model.RunRecord
import solutions.laxmi.omnicompiler.core.model.Verdict
import solutions.laxmi.omnicompiler.core.ui.VerdictBadge

/** Problems tab (design V2): parsed compiler errors/warnings with go-to-line, plus the raw output. */
@Composable
internal fun ProblemsPanel(run: RunRecord?, onGoTo: (CompileProblem) -> Unit, modifier: Modifier = Modifier) {
    val colors = OmniTheme.colors
    val problems = run?.problems.orEmpty()
    if (run == null || (problems.isEmpty() && run.verdict != Verdict.CE)) {
        EmptyState("No problems", "Compiler errors and warnings from the last run show up here.", modifier, icon = OmniIcons.Check)
        return
    }
    var rawOpen by rememberSaveable(run.id) { mutableStateOf(false) }
    LazyColumn(modifier) {
        if (run.verdict == Verdict.CE) {
            item {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VerdictBadge(Verdict.CE)
                    Text("Compile Error", style = OmniTheme.typography.bodyStrong, color = colors.textPrimary)
                    Text("0 tests run", style = OmniTheme.typography.monoSmall, color = colors.textTertiary)
                }
            }
        }
        items(problems) { problem ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .background(colors.surfaceRaised)
                    .drawBehind { drawRect(if (problem.isError) colors.accent else colors.textTertiary, size = size.copy(width = 2.dp.toPx())) }
                    .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        listOfNotNull(problem.fileName, problem.line?.toString(), problem.column?.toString()).joinToString(":"),
                        style = OmniTheme.typography.mono,
                        color = if (problem.isError) colors.accentText else colors.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    if (problem.line != null) OmniTextButton("Go to line", { onGoTo(problem) })
                }
                Text(problem.message, style = OmniTheme.typography.mono, color = colors.textPrimary)
            }
        }
        run.compileOutput?.takeIf { it.isNotBlank() }?.let { raw ->
            item {
                OmniTextButton(
                    if (rawOpen) "Hide raw compiler output" else "Raw compiler output · ${raw.lines().size} lines",
                    { rawOpen = !rawOpen },
                    Modifier.padding(horizontal = 12.dp),
                    color = colors.textSecondary,
                )
            }
            if (rawOpen) item { CodeBlock("stderr", raw, emptySet(), Modifier.padding(horizontal = 12.dp)) }
        }
    }
}
