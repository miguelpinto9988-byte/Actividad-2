package com.hunter.training

import org.junit.Test
import org.junit.Assert.*
import java.io.File
import kotlin.random.Random

class RoutineGeneratorTest {
    private fun catalog(): List<Exercise> = Codec.exercises(File("src/main/assets/exercises.json").readText())
    @Test fun catalogHasOneHundredDistinctFamilies() {
        val all=catalog(); assertEquals(100,all.size); assertEquals(100,all.map { it.family }.toSet().size)
        assertEquals(100,all.map { it.id }.toSet().size)
        assertTrue(all.all { it.instructions.isNotBlank() && it.source.startsWith("https://github.com/yuhonas/") })
    }
    @Test fun plansRespectEveryEquipmentLevelAndGoal() {
        val all=catalog()
        for(goal in RoutineGenerator.goals) for(equipment in RoutineGenerator.equipmentOptions) for(level in 1..3) for(days in 1..4) {
            val plans=RoutineGenerator.generate(all,goal,level,equipment,days,Random(37))
            assertEquals(days,plans.size)
            plans.forEach { plan ->
                assertTrue(plan.size>=4); assertEquals(plan.size,plan.map { it.exerciseId }.toSet().size)
                plan.forEach { p -> val e=all.first { it.id==p.exerciseId }
                    assertTrue(e.level<=level); assertTrue(RoutineGenerator.available(e,equipment)); assertTrue(p.sets>0)
                    if(goal=="Movilidad") assertEquals("mobility",e.pattern)
                    assertFalse(e.pattern=="power")
                }
            }
        }
    }
    @Test fun noEquipmentNeverAddsUnavailablePulls() {
        val all=catalog(); val p=RoutineGenerator.generate(all,"Fuerza general",1,"Sin equipo",1,Random(1)).first()
        assertEquals(setOf("legs","hinge","push","core","mobility"),p.map { id -> all.first { it.id==id.exerciseId }.pattern }.toSet())
    }
    @Test fun serializationPreservesLoggedWeightsAndPlan() {
        val logs=listOf(SetLog(2,8,12.5),SetLog(42,30,0.0))
        assertEquals(logs,Codec.logs(Codec.logs(logs)))
        val plan=listOf(PlanItem(2,3,8,90)); assertEquals(plan,Codec.plan(Codec.plan(plan)))
        assertEquals(20,RoutineGenerator.xp(logs)); assertEquals(0,RoutineGenerator.xp(emptyList()))
        assertEquals("D",RoutineGenerator.rank(500))
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsInvalidDayCount() {
        RoutineGenerator.generate(catalog(),"Fuerza general",1,"Sin equipo",9)
    }
}
