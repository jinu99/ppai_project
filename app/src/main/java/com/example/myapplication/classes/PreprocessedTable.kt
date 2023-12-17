package com.example.myapplication.classes

import kotlinx.serialization.Serializable

@Serializable
data class PreprocessedTable (
    val vectors: List<ImageVector>
)

@Serializable
data class ImageVector (
    val name: String,
    val objects: FloatArray,
    val embeddings: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ImageVector

        if (name != other.name) return false
        if (!objects.contentEquals(other.objects)) return false
        if (!embeddings.contentEquals(other.embeddings)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + objects.contentHashCode()
        result = 31 * result + embeddings.contentHashCode()
        return result
    }
}