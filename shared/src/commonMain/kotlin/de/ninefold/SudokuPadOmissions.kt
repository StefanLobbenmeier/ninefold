package de.ninefold

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/** Paths refer to expanded source JSON, not to the rendered puzzle. No source payload is retained. */
@Serializable
data class ImportOmission(val path: String, val kind: String, val detail: String)

internal fun sudokuPadOmissions(root: JsonObject, fpuzzles: Boolean, puzzle: Puzzle): List<ImportOmission> {
    val result = mutableListOf<ImportOmission>()
    fun meaningful(v: JsonElement): Boolean = when (v) {
        JsonNull -> false
        is JsonObject -> v.values.any(::meaningful)
        is JsonArray -> v.any(::meaningful)
        else -> (v as JsonPrimitive).contentOrNull?.let { it.isNotBlank() } == true
    }
    fun report(path: String, detail: String, kind: String = "ignored") { result += ImportOmission(path, kind, detail) }
    fun fields(obj: JsonObject, path: String, supported: String) {
        val known = supported.split(' ').toSet()
        obj.forEach { (key, value) -> if (key !in known && meaningful(value)) report("$path.$key", "Source property is not imported.") }
    }
    val metadata = "title author rules ruleset solution antiknight video videoUrl videos successMessage msgcorrect"
    fields(root, "$", "$metadata id size cellSize cells grid metadata cages killercage lines line arrows arrow overlays underlays foglight triggereffect solution thermometer palindrome circle difference ratio xv even odd littlekillersum")
    (root["metadata"] as? JsonObject)?.let { fields(it, "$.metadata", metadata) }
    val graphicalArrays = listOf("cages", "killercage", "lines", "line", "arrows", "arrow", "overlays", "underlays", "foglight", "triggereffect", "thermometer", "palindrome", "circle", "difference", "ratio", "xv", "even", "odd", "littlekillersum")
    graphicalArrays.forEach { key ->
        root[key]?.takeIf { meaningful(it) && it !is JsonArray }?.let {
            report("$.$key", "Expected an array; source content is not parsed.")
        }
    }
    if (!fpuzzles) for (key in listOf("thermometer", "palindrome", "circle", "difference", "ratio", "xv", "even", "odd", "littlekillersum")) {
        if (meaningful(root[key] ?: JsonNull)) report("$.$key", "This source feature is only parsed in f-puzzles format.")
    }
    val sourceMetadata = root["metadata"] as? JsonObject
    val sourceSolution = sourceMetadata?.get("solution") ?: root["solution"]
    if (meaningful(sourceSolution ?: JsonNull) && puzzle.solution == null)
        report("$.solution", "Solution is discarded because it does not produce 81 digits.")
    for (key in listOf("videos")) {
        val videos = ((root["metadata"] as? JsonObject)?.get(key) ?: root[key]) as? JsonArray
        (videos?.firstOrNull() as? JsonObject)?.let { fields(it, "$.videos[0]", "url") }
        if (videos != null && videos.size > 1) report("$.$key", "Only the first video is considered.", "partial")
    }
    fun objects(key: String, supported: String, inspect: (JsonObject, String) -> Unit = { _, _ -> }) {
        (root[key] as? JsonArray)?.forEachIndexed { i, value ->
            val path = "$.${key}[$i]"
            val obj = value as? JsonObject
            if (obj == null) report(path, "Entry cannot be parsed.") else { fields(obj, path, supported); inspect(obj, path) }
        }
    }
    fun point(v: JsonElement): Boolean = when (v) {
        is JsonArray -> v.size == 2 && v.all { (it as? JsonPrimitive)?.doubleOrNull != null }
        is JsonPrimitive -> Regex("[rR][1-9][cC][1-9]").matches(v.content)
        else -> false
    }
    fun points(v: JsonElement?, path: String, minimum: Int) {
        val array = v as? JsonArray
        if (array == null || array.count(::point) < minimum) report(path, "Geometry is dropped or incomplete.", "partial")
        array?.forEachIndexed { i, item -> if (!point(item)) report("$path[$i]", "Coordinate is ignored or truncated.", "partial") }
    }
    for (key in listOf("cells", "grid")) (root[key] as? JsonArray)?.forEachIndexed { r, row ->
        (row as? JsonArray)?.forEachIndexed { c, cell -> (cell as? JsonObject)?.let {
            val path = "$.${key}[$r][$c]"
            fields(it, path, "value given color")
            if (meaningful(it["given"] ?: JsonNull) && (it["given"] as? JsonPrimitive)?.booleanOrNull == null)
                report("$path.given", "Unrecognized fixed-clue flag; only boolean flags are supported.")
            if (meaningful(it["color"] ?: JsonNull) &&
                (it["color"] as? JsonPrimitive)?.contentOrNull?.let(::isImportedCellColor) != true)
                report("$path.color", "Cell shading is only imported for CSS hex colors.")
            val digit = (it["value"] as? JsonPrimitive)?.intOrNull
            if (meaningful(it["value"] ?: JsonNull) && digit !in 1..9) report("$.${key}[$r][$c].value", "Value is not imported as a digit.")
        } }
    }
    for (key in listOf("overlays", "underlays")) objects(key, "center width height backgroundColor borderColor stroke color textColor text rounded thickness") { obj, path ->
        if (obj["center"]?.let(::point) != true) report(path, "Overlay has no usable center and is dropped.")
    }
    for (key in listOf("lines", "line", "arrows", "arrow", "thermometer", "palindrome")) objects(key,
        when {
            key in listOf("thermometer", "palindrome") -> "lines"
            key in listOf("arrows", "arrow") -> if (fpuzzles) "lines cells wayPoints color thickness headLength" else "wayPoints color thickness headLength"
            else -> if (fpuzzles) "lines wayPoints outlineC lineC color thickness" else "wayPoints lineC color thickness"
        }) { obj, path ->
        if (fpuzzles && key in listOf("arrows", "arrow") && obj["lines"] is JsonArray) {
            for (style in listOf("color", "thickness", "headLength")) if (meaningful(obj[style] ?: JsonNull))
                report("$path.$style", "f-puzzles arrow styling is not imported.")
        }
        if (fpuzzles && obj["lines"] is JsonArray) (obj["lines"] as JsonArray).forEachIndexed { i, group -> points(group, "$path.lines[$i]", 2) }
        else points(obj["wayPoints"], "$path.wayPoints", 2)
        if (fpuzzles && key == "arrow" && ((obj["cells"] as? JsonArray)?.size ?: 0) > 1) report("$path.cells", "Only the first arrow bulb cell is used for validation.", "validation")
    }
    for (key in listOf("cages", "killercage")) objects(key, "cells value unique") { obj, path ->
        val value = (obj["value"] as? JsonPrimitive)?.content.orEmpty()
        if (meaningful(obj["unique"] ?: JsonNull) && (obj["unique"] as? JsonPrimitive)?.booleanOrNull == null)
            report("$path.unique", "Unrecognized cage uniqueness; only boolean flags are supported.")
        val marker = value.substringBefore(':').lowercase().trim()
        if (marker !in setOf("title", "author", "rules", "solution", "msgcorrect", "foglight")) {
            points(obj["cells"], "$path.cells", 1)
            if (value.isNotBlank() && value.toIntOrNull() == null) report("$path.value", "Non-numeric cage label and cage are dropped.")
        }
    }
    if (puzzle.cages.any { it.total == null && it.ruleTotal == null })
        report("$.rules", "Unlabelled cages preserve geometry and uniqueness only; additional cage rules require manual review in special rules.", "validation")
    objects("triggereffect", "trigger effect") { obj, path ->
        val trigger = obj["trigger"] as? JsonObject
        val effect = obj["effect"] as? JsonObject
        if ((effect?.get("type") as? JsonPrimitive)?.content != "foglight" || trigger == null) report(path, "Only foglight effects with a trigger are imported.")
        else {
            fields(trigger, "$path.trigger", "cell")
            fields(effect, "$path.effect", "type cells")
            if (trigger["cell"] == null || effect["cells"] == null) report(path, "Fog link is missing its trigger or reveal cells.", "partial")
        }
    }
    objects("littlekillersum", "cell cells direction value") { obj, path ->
        val position = (obj["cell"] as? JsonPrimitive)?.contentOrNull?.let {
            Regex("[rR](\\d+)[cC](\\d+)").matchEntire(it)
        }?.let { GridPoint(it.groupValues[1].toInt() - .5, it.groupValues[2].toInt() - .5) }
        if (fpuzzles && (position == null || puzzle.outsideClues.none { it.position == position }))
            report(path, "Little killer clue has no usable outside cell, direction, or total and is dropped.")
    }
    for (key in listOf("circle", "difference", "ratio", "xv", "even", "odd")) objects(key, "cells cell value width height baseC outlineC fontC")
    if (puzzle.lines.any { it.rule == LineRule.Unspecified } || listOf("difference", "ratio", "xv", "even", "odd").any { meaningful(root[it] ?: JsonNull) })
        report("$.rules", "Some graphical clues have no automatic validation; original rule text is preserved in special rules.", "validation")
    if (puzzle.rules.any { it.isNotBlank() && it != "Normal sudoku rules apply." })
        report("$.rules", "Original rule text is preserved for manual review in special rules; only modeled constraints are automatically checked.", "validation")
    return result
}
