import dev.kikugie.stonecutter.build.StonecutterBuildExtension
import org.gradle.api.Project
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.File
import org.gradle.kotlin.dsl.getByType

val Project.sc: StonecutterBuildExtension get() = extensions.getByType()

/** A value from stonecutter.properties.toml (version tables resolved for this project). */
fun Project.prop(key: String): String = requireNotNull(findProperty(key)?.toString()) { "Missing property '$key' in $path" }

/** A value from stonecutter.properties.toml, or null when it is missing or empty (a dependency this version lacks). */
fun Project.optionalProp(key: String): String? = findProperty(key)?.toString()?.takeIf { it.isNotEmpty() }

/** The Minecraft version of this Stonecutter node. */
val Project.mcVersion: String get() = sc.current.version

/** Whether this node is part of the Cartridge Arcade add-on (D195): its branches are arcade-common and so on. */
val Project.isArcade: Boolean get() = sc.branch.id.startsWith("arcade-")

/** The loader part of this node's branch: common, fabric, neoforge or forge. */
val Project.part: String get() = sc.branch.id.removePrefix("arcade-")

/** The mod's own metadata: Cartridge's `mod.*`, or the add-on's `arcade.mod.*`. */
fun Project.modProp(key: String): String = prop(if (isArcade) "arcade.mod.$key" else "mod.$key")

/** The `common` node for the same Minecraft version as this loader node (the add-on's own for its loaders). */
val Project.commonPath: String get() = ":${if (isArcade) "arcade-" else ""}common:${sc.current.project}"

/** Cartridge's own node of a part, for the add-on to build against: `:common:1.21.1`, `:fabric:1.21.1`. */
fun Project.cartridgePath(part: String): String = ":$part:${sc.current.project}"

/**
 * A dev run's folder: `runs/<branch>/<side>` for 1.21.1 (where the self-tests' trigger files live), and
 * `runs/<branch>/<version>/<side>` for the other versions.
 */
fun Project.devRunDir(side: String): File =
    rootProject.file(if (mcVersion == "1.21.1") "runs/${sc.branch.id}/$side" else "runs/${sc.branch.id}/$mcVersion/$side")

/** Which of 1.21.1's data formats [convertData] changes for the version being built (Porting.md, Resources). */
data class DataFormats(
    /** Up to 1.20.4: item stacks name their item with `item`, not `id`; an item predicate's `items` is a list. */
    val itemField: Boolean,
    /** Up to 1.20.6: data folders are plural (`recipes/`, `loot_tables/`, `advancements/`, `tags/items/`). */
    val pluralFolders: Boolean,
    /** 1.21.2+: recipe ingredients `{"item": "x"}` / `{"tag": "y"}` become `"x"` / `"#y"`. */
    val ingredients: Boolean,
    /** 26.2+: an entity predicate's `type` became the `minecraft:entity_type` sub-predicate. */
    val entityTypeKey: Boolean,
    /**
     * 26.3+: lists of loot conditions become one condition (several in an `all_of`), lists of loot functions become
     * a `modifier`, and both name their type with `type` instead of `condition` / `function`; the
     * `recipe_unlocked` trigger's `recipe` became `recipes`.
     */
    val singleConditions: Boolean,
)

/**
 * Rewrites the data JSON in [resources] (written in 1.21.1's formats) for the version being built. Tags are left
 * alone; their format hasn't changed.
 */
fun convertData(resources: File, formats: DataFormats) {
    val data = resources.resolve("data")
    data.walkTopDown()
        .filter { it.isFile && it.extension == "json" && it.relativeTo(data).invariantSeparatorsPath.split('/')[1] != "tags" }
        .forEach { file ->
            val folder = file.relativeTo(data).invariantSeparatorsPath.split('/')[1]
            var json = JsonSlurper().parse(file)
            if (formats.itemField && (folder == "recipe" || folder == "advancement")) json = itemField(json)
            if (formats.ingredients && folder == "recipe") json = recipeIngredients(json)
            if (formats.entityTypeKey) json = entityTypeKey(json)
            if (formats.singleConditions) json = singleConditions(json)
            if (formats.singleConditions && folder == "advancement") json = recipesUnlocked(json)
            if (json != JsonSlurper().parse(file)) file.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(json)))
        }
    if (formats.pluralFolders) {
        val plural = mapOf("recipe" to "recipes", "loot_table" to "loot_tables", "advancement" to "advancements")
        data.listFiles().orEmpty().forEach { namespace ->
            plural.forEach { (from, to) -> namespace.resolve(from).takeIf { it.isDirectory }?.renameTo(namespace.resolve(to)) }
            val tags = namespace.resolve("tags")
            listOf("item", "block").forEach { tags.resolve(it).takeIf { d -> d.isDirectory }?.renameTo(tags.resolve(it + "s")) }
        }
    }
}

private fun itemField(value: Any?): Any? = when (value) {
    is List<*> -> value.map(::itemField)
    is Map<*, *> -> value.entries.associate { (key, child) ->
        val converted = itemField(child)
        when {
            // a stack ({"id": x, "count": n}): a recipe result or an advancement icon
            key == "id" && value.keys.all { it == "id" || it == "count" } -> "item" to converted
            key == "items" && converted is String -> key to listOf(converted)
            else -> key to converted
        }
    }
    else -> value
}

private fun recipeIngredients(recipe: Any?): Any? {
    if (recipe !is Map<*, *>) return recipe
    return recipe.mapValues { (key, value) ->
        when (key) {
            "ingredient", "ingredients", "base", "addition", "template" -> ingredient(value)
            "key" -> (value as Map<*, *>).mapValues { ingredient(it.value) }
            else -> value
        }
    }
}

private fun ingredient(value: Any?): Any? = when (value) {
    is List<*> -> value.map(::ingredient)
    is Map<*, *> -> value["item"] ?: value["tag"]?.let { "#$it" } ?: value
    else -> value
}

private fun entityTypeKey(value: Any?): Any? = when (value) {
    is List<*> -> value.map(::entityTypeKey)
    is Map<*, *> -> value.entries.associate { (key, child) ->
        val converted = entityTypeKey(child)
        if (key == "predicate" && value["condition"] == "minecraft:entity_properties" && converted is Map<*, *>) {
            key to converted.mapKeys { if (it.key == "type") "minecraft:entity_type" else it.key }
        } else {
            key to converted
        }
    }
    else -> value
}

private fun singleConditions(value: Any?): Any? = when (value) {
    is List<*> -> value.map(::singleConditions)
    is Map<*, *> -> value.entries.associate { (key, child) ->
        val converted = singleConditions(child)
        when {
            key == "conditions" && converted is List<*> -> "condition" to allOf(converted)
            (key == "entity" || key == "player") && converted is List<*> -> key to allOf(converted)
            key == "functions" && converted is List<*> -> "modifier" to (converted.singleOrNull() ?: converted)
            (key == "condition" || key == "function") && converted is String -> "type" to converted
            else -> key to converted
        }
    }
    else -> value
}

/** An advancement's `recipe_unlocked` criteria: `{"recipe": x}` became `{"recipes": x}` (26.3). */
private fun recipesUnlocked(value: Any?): Any? = when (value) {
    is List<*> -> value.map(::recipesUnlocked)
    is Map<*, *> -> {
        val conditions = value["conditions"]
        if (value["trigger"] == "minecraft:recipe_unlocked" && conditions is Map<*, *>) {
            value + ("conditions" to conditions.mapKeys { if (it.key == "recipe") "recipes" else it.key })
        } else {
            value.mapValues { recipesUnlocked(it.value) }
        }
    }
    else -> value
}

private fun allOf(conditions: List<*>): Any? =
    conditions.singleOrNull() ?: mapOf("type" to "minecraft:all_of", "terms" to conditions)
