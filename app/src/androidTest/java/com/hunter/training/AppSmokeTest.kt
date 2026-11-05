package com.hunter.training

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    private fun views(v:View):List<View> = listOf(v) + if(v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun click(activity:MainActivity,text:String) {
        val b=views(activity.window.decorView).filterIsInstance<Button>().firstOrNull { it.text.toString()==text }
        assertNotNull("Button not found: $text",b); b!!.performClick()
    }
    private fun waitFor(s:ActivityScenario<MainActivity>,text:String) {
        val found=AtomicBoolean(false)
        repeat(100) {
            s.onActivity { a -> found.set(views(a.window.decorView).filterIsInstance<TextView>().any { it.text.toString().contains(text) }) }
            if(found.get()) return
            SystemClock.sleep(100)
        }
        screenshot("failure")
        var visible = ""
        s.onActivity { a -> visible = views(a.window.decorView).filterIsInstance<TextView>().joinToString(" | ") { it.text.toString() } }
        fail("Timed out waiting for $text; screen: $visible")
    }
    private fun screenshot(name:String) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val image=instrumentation.uiAutomation.takeScreenshot()
        val folder=File(instrumentation.targetContext.getExternalFilesDir(null),"screenshots").apply { mkdirs() }
        File(folder,"$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun browseGenerateLogAndRestore() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("hunter",0).edit().clear().commit()
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            waitFor(s,"Tu misión de hoy"); screenshot("01-home")
            s.onActivity { it.findViewById<Button>(R.id.navLibrary).performClick() }
            waitFor(s,"100 ejercicios encontrados")
            s.onActivity { it.findViewById<EditText>(R.id.search).setText("sentadilla") }
            waitFor(s,"1 ejercicios encontrados"); screenshot("02-library")
            s.onActivity { it.findViewById<Button>(R.id.navRoutines).performClick(); click(it,"Generar rutina") }
            // Dialog windows are outside activity decor; Espresso addresses the active window.
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText("Generar"))
                .perform(androidx.test.espresso.action.ViewActions.click())
            waitFor(s,"Abrir rutina")
            s.onActivity { click(it,"Abrir rutina") }
            waitFor(s,"Iniciar entrenamiento"); screenshot("03-routine")
            s.onActivity { click(it,"Iniciar entrenamiento") }
            waitFor(s,"MISIÓN EN CURSO")
            s.onActivity { click(it,"Registrar serie") }
            waitFor(s,"1 / "); s.recreate(); waitFor(s,"MISIÓN EN CURSO")
            screenshot("04-session")
            s.onActivity { click(it,"Finalizar sesión") }
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText("Guardar"))
                .perform(androidx.test.espresso.action.ViewActions.click())
            waitFor(s,"Tu evolución"); waitFor(s,"10 XP"); screenshot("05-progress")
            s.recreate(); waitFor(s,"Tu evolución"); waitFor(s,"10 XP")
        }
    }
    @Test fun roomPersistsRoutinesAndRejectsDuplicateSessionAwards() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        try {
            val r=Routine("test","Rutina","Fuerza","[]",1)
            db.dao().save(r); assertEquals(r,db.dao().routines().single())
            db.dao().save(r.copy(name="Editada")); assertEquals("Editada",db.dao().routines().single().name)
            val s=Session("same-id","Test",1,100,"[]",10,true)
            assertTrue(db.dao().finish(s)>0); assertEquals(-1L,db.dao().finish(s)); assertEquals(10,db.dao().sessions().sumOf { it.xp })
            db.dao().deleteRoutine("test"); assertTrue(db.dao().routines().isEmpty())
        } finally { db.close() }
    }
}
