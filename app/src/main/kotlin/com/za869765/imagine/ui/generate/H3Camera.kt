package com.za869765.imagine.ui.generate

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ───────────────────────── v3.4.0 鏡頭：取景與運鏡一鍵選 ─────────────────────────
// 點一下就把提示詞裡的鏡頭句子換掉：範本裡原本的「full-body front view, static camera」與「The camera stays wide…」會被取代；
// 自己寫的提示詞沒有這些句子，就在最前面加一句「Camera: …」。之後再點別的選項會換掉上一次加的，不會越疊越多。
// 取景越近臉越清楚：筆電 672×1120 的雙人全身鏡頭臉很小、眼睛容易崩（使用者 11/14 看 209／226／227），七分身（232）就好很多。

private class Framing(val label: String, val phrase: String, val rule: String)
private class Move(val label: String, val phrase: String)

private val FRAMINGS = listOf(
    Framing("全身", "full-body front view", "the camera always shows the whole body from head to feet"),
    Framing("七分身", "three-quarter front view framed from the knees up", "the camera keeps the framing from the knees up, with the face large and clear"),
    Framing("半身近景", "medium close front view framed from the waist up", "the camera keeps the framing from the waist up, with the face large and clear"),
    Framing("臉部特寫", "close-up front view of the face and shoulders", "the camera stays on the face and shoulders, with a shallow depth of field and a softly blurred background"),
)
private val MOVES = listOf(
    Move("固定", "static camera"),
    Move("慢慢推近", "the camera slowly pushes in during the shot"),
    Move("慢慢拉遠", "the camera slowly pulls back during the shot"),
    Move("由低往上升", "the camera starts low near the floor and slowly rises to eye level while keeping the same framing width"),
    Move("左右橫移", "the camera slowly slides sideways from left to right"),
    Move("弧形環繞", "the camera slowly arcs a quarter circle around the subject"),
)
private const val MOVING_RULE = "the camera move is smooth and continuous, and the face, hair and body look exactly the same before, during and after the move"

private val PAIR = Regex("(" + FRAMINGS.joinToString("|") { Regex.escape(it.phrase) } + "), (" + MOVES.joinToString("|") { Regex.escape(it.phrase) } + ")")
private val RULE = Regex("(The camera stays wide and static[^.]*\\.|Shot: [^.]*\\.) ?")
private val LEAD = Regex("^Camera: [^.]*\\. (Shot: [^.]*\\. )?")

/** 把提示詞裡的鏡頭句子換成選的取景與運鏡 */
internal fun h3ApplyCamera(prompt: String, framing: Int, move: Int): String {
    val f = FRAMINGS[framing.coerceIn(0, FRAMINGS.lastIndex)]
    val m = MOVES[move.coerceIn(0, MOVES.lastIndex)]
    val pair = "${f.phrase}, ${m.phrase}"
    val rule = "Shot: " + (if (move == 0) f.rule else "the shot starts as a ${f.phrase}; $MOVING_RULE") + ". "
    var s = LEAD.replace(prompt.trimStart(), "")
    val hadPair = PAIR.containsMatchIn(s)
    val hadRule = RULE.containsMatchIn(s)
    if (hadPair) s = PAIR.replaceFirst(s, Regex.escapeReplacement(pair))
    if (hadRule) s = RULE.replaceFirst(s, Regex.escapeReplacement(rule))
    return when {
        hadPair && hadRule -> s
        hadPair -> s.trimEnd() + " " + rule.trimEnd()
        hadRule -> "Camera: $pair. $s"
        else -> "Camera: $pair. $rule$s"
    }
}

/** 生影頁提示詞下方的兩排選項：取景、運鏡。點了立刻改提示詞（之後還是可以手動改字）。 */
@Composable
internal fun H3CameraPicker(prompt: String, onPrompt: (String) -> Unit) {
    var framing by remember { mutableIntStateOf(-1) }
    var move by remember { mutableIntStateOf(-1) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("鏡頭（點了會直接改提示詞裡的鏡頭句子；取景越近臉越清楚）", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FRAMINGS.forEachIndexed { i, f ->
                FilterChip(selected = framing == i, onClick = {
                    framing = i
                    if (move < 0) move = 0
                    onPrompt(h3ApplyCamera(prompt, framing, move))
                }, label = { Text(f.label) })
            }
        }
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MOVES.forEachIndexed { i, m ->
                FilterChip(selected = move == i, onClick = {
                    move = i
                    if (framing < 0) framing = 0
                    onPrompt(h3ApplyCamera(prompt, framing, move))
                }, label = { Text(m.label) })
            }
        }
    }
}
