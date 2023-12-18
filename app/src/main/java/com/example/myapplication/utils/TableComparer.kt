package com.example.myapplication.utils

import com.example.myapplication.classes.PreprocessedTable
import kotlin.math.sqrt

class TableComparer {
    fun cosineSimilarity(vector1: FloatArray, vector2: FloatArray): Double {
        require(vector1.size == vector2.size) { "Vector dimensions do not match" }

        var dotProduct = 0.0
        var magnitude1 = 0.0
        var magnitude2 = 0.0

        for (i in vector1.indices) {
            dotProduct += vector1[i] * vector2[i]
            magnitude1 += vector1[i] * vector1[i]
            magnitude2 += vector2[i] * vector2[i]
        }

        if (magnitude1 == 0.0 || magnitude2 == 0.0) {
            return 0.0  // To handle division by zero
        }

        return dotProduct / (sqrt(magnitude1) * sqrt(magnitude2))
    }
    fun imageSimilarity(
        table1: PreprocessedTable,
        table2: PreprocessedTable
    ): List<Pair<String, String>> {
//        TODO("return list of image-path pair")

        val similarities = mutableMapOf<Pair<String, String>, Double>()

        for (vector1 in table1.vectors) {

            for (vector2 in table2.vectors) {
                val similarity1 = cosineSimilarity(vector1.embeddings, vector2.embeddings)
                val similarity2 = cosineSimilarity(vector1.objects, vector2.objects)
                similarities[Pair(vector1.name, vector2.name)] = (similarity1+similarity2)/2

            }
        }
        return similarities.toList().sortedByDescending { it.second }.map { it.first }


    }
}