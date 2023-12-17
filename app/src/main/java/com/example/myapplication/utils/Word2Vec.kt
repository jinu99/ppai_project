package com.example.myapplication.utils

import android.content.Context
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

@Serializable
data class Vectors(val data: Array<FloatArray>) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Vectors

        if (!data.contentDeepEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        return data.contentDeepHashCode()
    }
}

@OptIn(ExperimentalSerializationApi::class)
class Word2Vec(context: Context, fileName: String) {
    private val vectors: Vectors
    private val TAG = "Word2Vec"

    init {
        vectors = Json.decodeFromStream(context.assets.open(fileName))
    }

    fun vectorize(result: List<Pair<Int, Float>>): FloatArray {
        val vector = FloatArray(vectors.data[0].size)
        result.forEach { pair ->
            val idx = pair.first
            val score = pair.second

            vector.plus(vectors.data[idx].map { it*score })
        }
        return vector
    }
}