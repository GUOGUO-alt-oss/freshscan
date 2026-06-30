package com.example.freshscan.data.history

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for the produce collection feature (v4.2).
 *
 * Tracks every unique produce type the user has ever scanned,
 * building a "Pokédex for produce." Each label can only appear
 * once (enforced by unique index on [label]).
 */
@Entity(
    tableName = "collected_produce",
    indices = [
        Index(value = ["first_scan_time"], name = "idx_cp_first_scan"),
        Index(value = ["label"], name = "idx_cp_label", unique = true)
    ]
)
data class CollectedProduceEntity(
    /** Auto-generated integer primary key. */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Raw model label (e.g. "Tomato_Cherry_Red"). */
    @ColumnInfo(name = "label")
    val label: String,

    /** Human-readable display name (e.g. "樱桃番茄"). */
    @ColumnInfo(name = "display_name")
    val displayName: String,

    /** Vegetable category for grouping (e.g. "水果", "蔬菜"). */
    @ColumnInfo(name = "category")
    val category: String = "",

    /** Unix timestamp (ms) of the first time this produce was scanned. */
    @ColumnInfo(name = "first_scan_time")
    val firstScanTime: Long,

    /** Total number of times this produce has been scanned. */
    @ColumnInfo(name = "scan_count")
    val scanCount: Int = 1,

    /** Whether this produce is considered rare (金色边框). */
    @ColumnInfo(name = "is_rare")
    val isRare: Boolean = false
)
