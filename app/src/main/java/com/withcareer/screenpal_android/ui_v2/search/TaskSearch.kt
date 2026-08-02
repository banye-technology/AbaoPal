package com.withcareer.screenpal_android.ui_v2.search

import com.withcareer.screenpal_android.data.preference.Task

data class TaskSearchResult(
    val task: Task,
    val score: Int,
    val snippet: String
)

fun searchTasks(
    tasks: List<Task>,
    query: String,
    maxResults: Int = 50
): List<TaskSearchResult> {
    val normalized = query.trim()
    if (normalized.isEmpty()) return emptyList()

    val terms = normalized.split(Regex("\\s+")).filter { it.isNotBlank() }
    val effectiveTerms = if (terms.isEmpty()) listOf(normalized) else terms

    return tasks.asSequence()
        .mapNotNull { task ->
            val title = task.prompt
            val content = buildTaskContent(task)

            val matchAll = effectiveTerms.all { term ->
                title.contains(term, ignoreCase = true) || content.contains(term, ignoreCase = true)
            }
            if (!matchAll) return@mapNotNull null

            val score = computeScore(title = title, content = content, terms = effectiveTerms, createdAt = task.createdAt)
            val snippet = buildSnippet(title = title, content = content, terms = effectiveTerms)
            TaskSearchResult(task = task, score = score, snippet = snippet)
        }
        .sortedWith(compareByDescending<TaskSearchResult> { it.score }.thenByDescending { it.task.createdAt })
        .take(maxResults)
        .toList()
}

private fun buildTaskContent(task: Task): String {
    return buildString {
        task.steps.forEach { step ->
            if (step.thought.isNotBlank()) {
                append(step.thought)
                append('\n')
            }
            if (step.action.isNotBlank()) {
                append(step.action)
                append('\n')
            }
            val observation = step.observation
            if (!observation.isNullOrBlank()) {
                append(observation)
                append('\n')
            }
            val aiPrompt = step.aiPrompt
            if (!aiPrompt.isNullOrBlank()) {
                append(aiPrompt)
                append('\n')
            }
            val aiResponse = step.aiResponse
            if (!aiResponse.isNullOrBlank()) {
                append(aiResponse)
                append('\n')
            }
        }
    }
}

private fun computeScore(
    title: String,
    content: String,
    terms: List<String>,
    createdAt: Long
): Int {
    var score = 0
    for (term in terms) {
        val titleCount = countOccurrencesIgnoreCase(title, term)
        val contentCount = countOccurrencesIgnoreCase(content, term)

        if (titleCount > 0) {
            score += 120
            score += titleCount * 18
            if (title.startsWith(term, ignoreCase = true)) score += 40
            if (title.equals(term, ignoreCase = true)) score += 80
        }
        if (contentCount > 0) {
            score += contentCount * 10
        }
    }

    val recencyBoost = ((createdAt / 3_600_000L) % 200).toInt()
    score += recencyBoost
    return score
}

private fun buildSnippet(
    title: String,
    content: String,
    terms: List<String>
): String {
    val firstTerm = terms.firstOrNull().orEmpty()
    if (firstTerm.isNotEmpty() && title.contains(firstTerm, ignoreCase = true)) return title

    for (term in terms) {
        val index = content.indexOf(term, ignoreCase = true)
        if (index >= 0) return extractWindow(content, index, term.length, 64)
    }
    return extractWindow(content, 0, 0, 64)
}

private fun extractWindow(
    text: String,
    index: Int,
    matchLength: Int,
    windowSize: Int
): String {
    if (text.isBlank()) return ""
    val safeIndex = index.coerceIn(0, text.length)
    val start = (safeIndex - windowSize / 2).coerceAtLeast(0)
    val end = (safeIndex + matchLength + windowSize / 2).coerceAtMost(text.length)
    val prefix = if (start > 0) "…" else ""
    val suffix = if (end < text.length) "…" else ""
    return prefix + text.substring(start, end).trim().replace('\n', ' ') + suffix
}

private fun countOccurrencesIgnoreCase(text: String, term: String): Int {
    if (term.isBlank()) return 0
    var count = 0
    var idx = 0
    while (idx < text.length) {
        val next = text.indexOf(term, startIndex = idx, ignoreCase = true)
        if (next < 0) break
        count++
        idx = next + term.length
    }
    return count
}
