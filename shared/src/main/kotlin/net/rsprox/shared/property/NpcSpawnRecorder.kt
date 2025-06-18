package net.rsprox.shared.property

import net.rsprox.cache.api.Cache
import net.rsprox.protocol.exceptions.DecodeError
import java.io.File
import java.util.concurrent.ConcurrentHashMap

public data class NPCSpawnReal(
    val id: Int,
    val walkRange: Int? = null,
    val direction: Direction,
    val name: String,
    val x: Int,
    val y: Int,
    val z: Int
)

public enum class Direction {
    NORTH, NORTH_EAST, EAST, SOUTH_EAST,
    SOUTH, SOUTH_WEST, WEST, NORTH_WEST,
    NONE;

    public companion object {
        public fun fromJagAngle(angle: Int): Direction {
            if (angle < 0) return NONE
            return when ((angle / 256) and 0x7) {
                0 -> NORTH_WEST
                1 -> NORTH
                2 -> NORTH_EAST
                3 -> WEST
                4 -> EAST
                5 -> SOUTH_WEST
                6 -> SOUTH
                7 -> SOUTH_EAST
                else -> NONE
            }
        }
    }
}

/** Internal pairing of scene‐tile index + spawn for sorting. */
private data class IndexedSpawn(
    val sceneIndex: Int,
    val spawn: NPCSpawnReal
)

public object NpcSpawnRecorder {
    private val seen = ConcurrentHashMap.newKeySet<Int>()
    private val file = File("npc_spawns.json")
    private val indexedSpawns = mutableListOf<IndexedSpawn>()

    init {
        // fresh start each run
        if (file.exists()) file.delete()
        file.writeText("[]")
    }

    /** Your original static‐area predicate. */
    private fun inStaticAreaByTile(x: Int, y: Int): Boolean {
        val regionX = x shr 6
        val regionY = y shr 6
        return regionX in 0..99 && regionY in 1..255
    }

    /** Compute OSRS region ID from world‐tile coords. */
    private fun regionId(x: Int, y: Int): Int {
        val regionX = x shr 6
        val regionY = y shr 6
        return regionX * 256 + regionY
    }

    /**
     * On first sight of [index], record & regenerate JSON,
     * skipping followers, duplicates, and tiles outside static area.
     */
    public fun record(
        cache: Cache,
        index: Int,
        id: Int,
        name: String,
        level: Int,
        x: Int,
        y: Int,
        angle: Int
    ) {
        val npcType = cache.getNpcType(id)
            ?: throw DecodeError("Npc $id not found in cache $cache! Decoding cannot continue.")

        if (npcType.follower) return
        if (!inStaticAreaByTile(x, y)) return
        if (!seen.add(index)) return

        val dir = Direction.fromJagAngle(angle)
        val spawn = NPCSpawnReal(
            id = id,
            walkRange = null,
            direction = dir,
            name = name,
            x = x,
            y = y,
            z = level
        )
        indexedSpawns += IndexedSpawn(sceneIndex = index, spawn = spawn)
        writeAll()
    }

    private fun writeAll() {
        // sort globally by regionId → x → y → sceneIndex
        val sorted = indexedSpawns.sortedWith(
            compareBy<IndexedSpawn>(
                { regionId(it.spawn.x, it.spawn.y) },
                { it.spawn.x },
                { it.spawn.y },
                { it.sceneIndex }
            )
        )

        // group by regionId
        val groups = sorted.groupBy { regionId(it.spawn.x, it.spawn.y) }

        val lines = buildList {
            add("[")
            groups.toSortedMap().forEach { (rid, entries) ->
                // region header
                add("  // ----- REGION ID $rid -----")
                entries.forEachIndexed { idx, entry ->
                    val s = entry.spawn
                    add("  {")
                    add("    \"ID\": ${s.id},")
                    add("    \"walkRange\": ${s.walkRange ?: "null"},")
                    add("    \"direction\": \"${s.direction}\",")
                    add("    \"Nice name\": \"${s.name}\",")
                    add("    \"X\": ${s.x},")
                    add("    \"Z\": ${s.y},")
                    add("    \"Level\": ${s.z}")
                    // comma if it's not the very last spawn overall
                    val isLastOverall = rid == groups.keys.maxOrNull() && idx == entries.lastIndex
                    add("  }${if (!isLastOverall) "," else ""}")
                }
                add("") // blank line between regions
            }
            add("]")
        }

        file.writeText(lines.joinToString("\n"))
    }
}
