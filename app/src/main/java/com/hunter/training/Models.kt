package com.hunter.training

import androidx.room.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

@Entity(tableName = "exercises")
data class Exercise(@PrimaryKey val id: Int, val family: String, val name: String, val pattern: String,
    val equipment: String, val level: Int, val unit: String, val instructions: String,
    val muscles: String, val variants: String, val source: String)
@Entity(tableName = "routines")
data class Routine(@PrimaryKey val id: String, val name: String, val goal: String, val items: String, val created: Long)
@Entity(tableName = "sessions")
data class Session(@PrimaryKey val id: String, val name: String, val time: Long, val duration: Long,
    val logs: String, val xp: Int, val complete: Boolean)
data class PlanItem(val exerciseId: Int, val sets: Int, val amount: Int, val rest: Int)
data class SetLog(val exerciseId: Int, val amount: Int, val weight: Double)

object Codec {
    fun exercises(text: String): List<Exercise> = JSONArray(text).let { a -> (0 until a.length()).map { i ->
        val j = a.getJSONObject(i)
        Exercise(j.getInt("id"), j.getString("family"), j.getString("name"), j.getString("pattern"),
            j.getString("equipment"), j.getInt("level"), j.getString("unit"), j.getString("instructions"),
            j.getString("muscles"), j.getString("variants"), j.getString("source"))
    } }
    fun plan(items: List<PlanItem>): String = JSONArray().apply { items.forEach { put(JSONObject()
        .put("id", it.exerciseId).put("sets", it.sets).put("amount", it.amount).put("rest", it.rest)) } }.toString()
    fun plan(text: String): List<PlanItem> = JSONArray(text).let { a -> (0 until a.length()).map { i ->
        val j = a.getJSONObject(i); PlanItem(j.getInt("id"), j.getInt("sets"), j.getInt("amount"), j.getInt("rest")) } }
    fun logs(items: List<SetLog>): String = JSONArray().apply { items.forEach { put(JSONObject()
        .put("id", it.exerciseId).put("amount", it.amount).put("weight", it.weight)) } }.toString()
    fun logs(text: String): List<SetLog> = JSONArray(text).let { a -> (0 until a.length()).map { i ->
        val j = a.getJSONObject(i); SetLog(j.getInt("id"), j.getInt("amount"), j.getDouble("weight")) } }
}
object RoutineGenerator {
    val goals = listOf("Fuerza general", "Acondicionamiento", "Movilidad")
    val equipmentOptions = listOf("Sin equipo", "Mancuernas", "Gimnasio completo")
    fun available(e: Exercise, equipment: String): Boolean = when (equipment) {
        "Sin equipo" -> e.equipment == "Sin equipo"
        "Mancuernas" -> e.equipment in listOf("Sin equipo", "Mancuernas")
        else -> true
    }
    fun generate(all: List<Exercise>, goal: String, level: Int, equipment: String, days: Int,
                 random: Random = Random.Default): List<List<PlanItem>> {
        require(days in 1..4 && level in 1..3)
        require(goal in goals && equipment in equipmentOptions)
        // Technical power and balance drills remain manual selections; automatic plans favor basic movements.
        val pool = all.filter { it.level <= level && available(it, equipment) && it.pattern !in listOf("power", "balance") }
        require(pool.isNotEmpty()) { "No hay ejercicios disponibles" }
        return (0 until days).map { day ->
            val chosen = mutableListOf<Exercise>()
            fun pick(pattern: String, count: Int = 1) {
                val candidates = pool.filter { it.pattern == pattern && it !in chosen }.shuffled(random)
                chosen.addAll(candidates.drop(day % candidates.size.coerceAtLeast(1)).plus(candidates).distinctBy { it.id }.take(count))
            }
            when(goal) {
                "Movilidad" -> pick("mobility", 6)
                "Acondicionamiento" -> { pick("cardio", 2); pick("legs"); pick("push"); pick("core"); pick("mobility") }
                else -> { pick("legs"); pick("hinge"); pick("push"); pick("pull"); pick("core"); pick("mobility") }
            }
            // Never substitute unavailable equipment or silently add duplicate families.
            chosen.map { e ->
                val timed = e.unit == "seconds"
                val sets = if(e.pattern in listOf("mobility", "cardio")) 1 else if(level == 1) 2 else 3
                val amount = if(timed) { if(e.pattern == "cardio") 120 else 25 } else if(goal == "Fuerza general") 10 else 12
                PlanItem(e.id, sets, amount, if(goal == "Fuerza general") 90 else 60)
            }
        }
    }
    fun xp(logs: List<SetLog>): Int = logs.size.coerceAtMost(40) * 10
    fun rank(xp: Int): String = when { xp >= 10000 -> "S"; xp >= 6000 -> "A"; xp >= 3000 -> "B"; xp >= 1500 -> "C"; xp >= 500 -> "D"; else -> "E" }
}
