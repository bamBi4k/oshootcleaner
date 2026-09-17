package com.example.oshootcleaner

import android.net.Uri

enum class AnalysisSort(val id: String) {
    SIZE_DESC("size_desc"),
    SIZE_ASC("size_asc"),
    NAME("name"),
    DATE("date");

    companion object {
        fun fromId(id: String?): AnalysisSort =
            entries.find { it.id == id } ?: SIZE_DESC
    }
}

object AnalysisSorter {

    fun sortBigFiles(files: List<BigFile>, mode: AnalysisSort): List<BigFile> =
        when (mode) {
            AnalysisSort.SIZE_DESC -> files.sortedByDescending { it.sizeBytes }
            AnalysisSort.SIZE_ASC -> files.sortedBy { it.sizeBytes }
            AnalysisSort.NAME -> files.sortedBy { it.name.lowercase() }
            AnalysisSort.DATE -> files  // MediaStore query already returns newest-first
        }

    fun sortDuplicates(
        groups: List<DuplicateGroup>,
        mode: AnalysisSort
    ): List<DuplicateGroup> = when (mode) {
        AnalysisSort.SIZE_DESC -> groups.sortedByDescending { it.wastedBytes }
        AnalysisSort.SIZE_ASC -> groups.sortedBy { it.wastedBytes }
        AnalysisSort.NAME -> groups.sortedBy { it.files.firstOrNull()?.name?.lowercase() ?: "" }
        AnalysisSort.DATE -> groups  // no date info on groups
    }

    fun sortUnusedApps(
        apps: List<UnusedApp>,
        mode: AnalysisSort
    ): List<UnusedApp> = when (mode) {
        AnalysisSort.SIZE_DESC -> apps.sortedBy { it.lastUsedMs }      // oldest = largest
        AnalysisSort.SIZE_ASC -> apps.sortedByDescending { it.lastUsedMs }
        AnalysisSort.NAME -> apps.sortedBy { it.label.lowercase() }
        AnalysisSort.DATE -> apps.sortedBy { it.lastUsedMs }
    }

    fun matchesSearch(name: String, query: String): Boolean {
        if (query.isBlank()) return true
        return name.contains(query, ignoreCase = true)
    }

    fun matchesSearch(label: String, packageName: String, query: String): Boolean {
        if (query.isBlank()) return true
        return label.contains(query, ignoreCase = true) ||
                packageName.contains(query, ignoreCase = true)
    }
}

fun deleteUriSet(files: List<BigFile>): Set<Uri> = files.map { it.uri }.toSet()