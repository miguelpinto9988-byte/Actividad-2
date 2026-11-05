package com.hunter.training

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.text.InputType
import android.view.View
import android.widget.*
import org.json.JSONObject
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var content: LinearLayout
    private lateinit var db: AppDatabase
    private val worker = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("hunter", MODE_PRIVATE) }
    private var exercises = emptyList<Exercise>()
    private var routines = emptyList<Routine>()
    private var sessions = emptyList<Session>()
    private var screen = "home"
    private var active: JSONObject? = null
    private var timer: CountDownTimer? = null
    private var saving = false
    private val accent = Color.parseColor("#A991FA")

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_main)
        val root = findViewById<View>(R.id.root)
        root.setOnApplyWindowInsetsListener { v, insets ->
            v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        content = findViewById(R.id.content)
        db = AppDatabase.get(this)
        screen = state?.getString("screen") ?: "home"
        active = prefs.getString("active", null)?.let { runCatching { JSONObject(it) }.getOrNull() }
        findViewById<Button>(R.id.navHome).setOnClickListener { navigate("home") }
        findViewById<Button>(R.id.navLibrary).setOnClickListener { navigate("library") }
        findViewById<Button>(R.id.navRoutines).setOnClickListener { navigate("routines") }
        findViewById<Button>(R.id.navProgress).setOnClickListener { navigate("progress") }
        label("Preparando tu sistema…", 22)
        work({
            db.dao().seed(Codec.exercises(assets.open("exercises.json").bufferedReader().use { it.readText() }))
            refreshData()
        }) { render() }
    }
    override fun onSaveInstanceState(out: Bundle) { out.putString("screen", screen); super.onSaveInstanceState(out) }
    override fun onDestroy() { timer?.cancel(); worker.shutdown(); super.onDestroy() }
    @Deprecated("Legacy back handling for platform Activity")
    override fun onBackPressed() { if(screen != "home") navigate("home") else super.onBackPressed() }
    private fun work(task: () -> Unit, done: () -> Unit = {}) {
        worker.execute {
            try { task(); runOnUiThread { if(!isDestroyed) done() } }
            catch(e: Exception) { android.util.Log.e("HunterTraining", "Local storage operation failed", e); runOnUiThread { saving = false; if(!isDestroyed) AlertDialog.Builder(this).setTitle("No se pudo completar")
                .setMessage("Tus datos guardados se conservan. Inténtalo de nuevo.\n${e.localizedMessage}").setPositiveButton("Aceptar", null).show() } }
        }
    }
    private fun refreshData() { exercises = db.dao().exercises(); routines = db.dao().routines(); sessions = db.dao().sessions() }
    private fun navigate(to: String) { if(exercises.isEmpty()) return; screen = to; render() }
    private fun render() {
        timer?.cancel(); content.removeAllViews()
        val nav = mapOf(R.id.navHome to "home", R.id.navLibrary to "library", R.id.navRoutines to "routines", R.id.navProgress to "progress")
        nav.forEach { (id, target) -> findViewById<Button>(id).apply { setTextColor(if(screen == target) Color.WHITE else accent); alpha = if(screen == target) 1f else .72f } }
        when(screen) { "library" -> library(); "routines" -> routineList(); "progress" -> progress(); "session" -> workout(); else -> home() }
        findViewById<ScrollView>(R.id.scroll).post { findViewById<ScrollView>(R.id.scroll).scrollTo(0,0) }
    }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun label(text: String, size: Int = 15, parent: LinearLayout = content): TextView = TextView(this).also {
        it.text = text; it.textSize = size.toFloat(); it.setTextColor(if(size >= 20) Color.WHITE else Color.parseColor("#BEC4D6"))
        it.setPadding(0, dp(5), 0, dp(14)); it.setLineSpacing(dp(3).toFloat(), 1f); parent.addView(it)
    }
    private fun button(text: String, parent: LinearLayout = content, action: () -> Unit): Button = Button(this, null, 0, R.style.Action).also {
        it.text = text; parent.addView(it, LinearLayout.LayoutParams(-1,-2)); it.setOnClickListener { action() }
    }
    private fun card(tag: String, title: String, body: String, parent: LinearLayout = content): LinearLayout {
        val v = layoutInflater.inflate(R.layout.card_item, parent, false) as LinearLayout
        v.findViewById<TextView>(R.id.cardTag).text = tag.uppercase(Locale("es"))
        v.findViewById<TextView>(R.id.cardTitle).text = title
        v.findViewById<TextView>(R.id.cardBody).apply { text = body; visibility = if(body.isEmpty()) View.GONE else View.VISIBLE }
        parent.addView(v); return v.findViewById(R.id.cardActions)
    }
    private fun spinner(v: View, id: Int, options: List<String>): Spinner = v.findViewById<Spinner>(id).also {
        it.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
    }
    private fun level(e: Exercise) = listOf("Inicial", "Intermedio", "Avanzado")[e.level - 1]
    private fun ex(id: Int) = exercises.first { it.id == id }
    private fun unit(e: Exercise) = if(e.unit == "seconds") "seg" else "reps"
    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    private fun home() {
        val xp = sessions.sumOf { it.xp }
        val hero = layoutInflater.inflate(R.layout.hero_panel, content, false)
        hero.findViewById<TextView>(R.id.heroInfo).text = "RANGO ${RoutineGenerator.rank(xp)}   ·   NIVEL ${1 + xp / 250}\n$xp XP acumulados · ${sessions.size} sesiones"
        hero.findViewById<ProgressBar>(R.id.xpBar).apply { max = 250; progress = xp % 250 }
        hero.findViewById<Button>(R.id.heroAction).setOnClickListener { generator() }
        content.addView(hero)
        active?.let { button("Continuar entrenamiento") { navigate("session") } }
        label("Tu misión de hoy", 23)
        if(routines.isEmpty()) {
            card("MISIÓN 01", "Despierta tu potencial", "Crea una rutina adaptada a tu nivel y equipamiento. Cada serie registrada suma 10 XP.")
        } else {
            val r = routines.first()
            val items = Codec.plan(r.items)
            val actions = card("LISTO PARA ENTRENAR", r.name, "${items.size} ejercicios · ${items.sumOf { it.sets }} series · ${r.goal}")
            button("Ver rutina", actions) { routineDetail(r) }
        }
        card("ARSENAL", "100 ejercicios. Una sola biblioteca.", "Fuerza, cardio y movilidad. Las variantes están agrupadas dentro de cada familia.")
        label("Entrena a tu ritmo. La experiencia premia la constancia, no el peso que levantas. Descansar también forma parte del progreso.")
    }
    private fun normalize(s: String) = Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace("\\p{M}+".toRegex(), "")
    private fun library() {
        label("Tu arsenal", 28); label("100 familias · técnica y fuentes en cada ficha")
        val panel = layoutInflater.inflate(R.layout.search_panel, content, false); content.addView(panel)
        val search = panel.findViewById<EditText>(R.id.search)
        val categories = listOf("Todos", "Piernas", "Empuje", "Tirón", "Core", "Cardio", "Movilidad", "Potencia", "Transporte", "Equilibrio")
        val filter = spinner(panel, R.id.category, categories)
        val results = panel.findViewById<LinearLayout>(R.id.results)
        fun update() {
            results.removeAllViews(); val query = normalize(search.text.toString())
            val patterns = when(filter.selectedItemPosition) { 1 -> listOf("legs","hinge"); 2 -> listOf("push"); 3 -> listOf("pull"); 4 -> listOf("core"); 5 -> listOf("cardio"); 6 -> listOf("mobility"); 7 -> listOf("power"); 8 -> listOf("carry"); 9 -> listOf("balance"); else -> emptyList() }
            val matches = exercises.filter { (patterns.isEmpty() || it.pattern in patterns) && normalize("${it.name} ${it.muscles} ${it.equipment} ${it.variants}").contains(query) }
            panel.findViewById<TextView>(R.id.resultCount).text = "${matches.size} ejercicios encontrados"
            matches.forEach { e -> val actions = card("${level(e)} · ${e.equipment}", e.name, e.muscles, results); button("Ver técnica", actions) { exerciseDetail(e) } }
            if(matches.isEmpty()) label("No hay coincidencias. Prueba otro término o categoría.", parent = results)
        }
        search.addTextChangedListener(object: TextWatcher { override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {} ; override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { update() }; override fun afterTextChanged(s: Editable?) {} })
        filter.onItemSelectedListener = object: AdapterView.OnItemSelectedListener { override fun onNothingSelected(p: AdapterView<*>?) {}; override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { update() } }
        update()
    }
    private fun exerciseDetail(e: Exercise) {
        val text = "${e.muscles}\n${e.equipment} · ${level(e)}\n\n${e.instructions}\n\n" +
            (if(e.variants.isNotBlank()) "Variantes agrupadas (nombres de origen):\n${e.variants}\n\n" else "") +
            "Usa un recorrido cómodo y una carga controlable. Detén el ejercicio si aparece dolor. Las técnicas avanzadas requieren aprendizaje supervisado."
        AlertDialog.Builder(this).setTitle(e.name).setMessage(text).setPositiveButton("Cerrar", null)
            .setNeutralButton("Fuente original") { _, _ -> runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(e.source))) }.onFailure { toast("No hay navegador disponible") } }.show()
    }
    private fun generator() {
        val panel = layoutInflater.inflate(R.layout.form_panel, null)
        val goal = spinner(panel, R.id.goal, RoutineGenerator.goals)
        val lvl = spinner(panel, R.id.level, listOf("Inicial", "Intermedio", "Avanzado"))
        val equipment = spinner(panel, R.id.equipment, RoutineGenerator.equipmentOptions)
        val days = spinner(panel, R.id.days, listOf("1", "2", "3", "4")); days.setSelection(2)
        val dialog = AlertDialog.Builder(this).setTitle("Diseña tu misión").setView(panel).setNegativeButton("Cancelar", null).setPositiveButton("Generar", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val g = goal.selectedItem.toString(); val eq = equipment.selectedItem.toString()
            val plans = RoutineGenerator.generate(exercises, g, lvl.selectedItemPosition + 1, eq, days.selectedItemPosition + 1)
            val now = System.currentTimeMillis()
            val newRoutines = plans.mapIndexed { i, items -> Routine(UUID.randomUUID().toString(), "${g} · Día ${i+1}", g, Codec.plan(items), now) }
            dialog.dismiss()
            work({ newRoutines.forEach { db.dao().save(it) }; refreshData() }) {
                navigate("routines")
                if(eq == "Sin equipo" && g == "Fuerza general") toast("Sin equipo no se incluyen tirones con resistencia. Puedes añadirlos al disponer de material.")
            }
        } }; dialog.show()
    }
    private fun routineList() {
        label("Tus misiones", 28); label("Rutinas guardadas en este dispositivo")
        button("Generar rutina") { generator() }; button("Crear rutina manual") { createManual() }
        if(routines.isEmpty()) label("Todavía no hay rutinas. Genera una o elige tus ejercicios.")
        routines.forEach { r ->
            val items = Codec.plan(r.items)
            val actions = card(r.goal, r.name, "${items.size} ejercicios · ${items.sumOf { it.sets }} series")
            button("Abrir rutina", actions) { routineDetail(r) }
        }
    }
    private fun createManual() {
        val input = EditText(this).apply { hint = "Nombre de la rutina"; maxLines = 1; inputType = InputType.TYPE_CLASS_TEXT }
        val d = AlertDialog.Builder(this).setTitle("Nueva misión").setView(input).setPositiveButton("Crear", null).setNegativeButton("Cancelar", null).create()
        d.setOnShowListener { d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = input.text.toString().trim(); if(name.isBlank()) { input.error = "Escribe un nombre"; return@setOnClickListener }
            val r = Routine(UUID.randomUUID().toString(), name.take(80), "Personalizada", "[]", System.currentTimeMillis())
            work({ db.dao().save(r); refreshData() }) { routineDetail(r) }; d.dismiss()
        } }; d.show()
    }
    private fun routineDetail(r: Routine) {
        screen = "detail"; content.removeAllViews()
        label(r.name, 27)
        label("Antes: 5–10 minutos de calentamiento suave. Alterna sesiones con días de descanso y ajusta las cargas a tu experiencia.")
        val items = Codec.plan(r.items)
        if(items.isEmpty()) label("Añade al menos un ejercicio para empezar.")
        else button("Iniciar entrenamiento") { startWorkout(r) }
        button("Añadir ejercicio") { pickExercise(r) }
        button("Cambiar nombre") {
            val input = EditText(this).apply { setText(r.name); maxLines = 1 }
            val d = AlertDialog.Builder(this).setTitle("Nombre").setView(input).setPositiveButton("Guardar", null).setNegativeButton("Cancelar", null).create()
            d.setOnShowListener { d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString().trim(); if(name.isBlank()) { input.error = "Nombre requerido"; return@setOnClickListener }
                saveRoutine(r.copy(name = name.take(80))); d.dismiss()
            } }; d.show()
        }
        items.forEachIndexed { index, p ->
            val e = ex(p.exerciseId)
            val actions = card("${index+1} · ${e.equipment}", e.name, "${p.sets} × ${p.amount} ${unit(e)} · descanso ${p.rest} seg")
            button("Técnica", actions) { exerciseDetail(e) }
            button("Editar series y repeticiones", actions) { editItem(r, index) }
            button("Quitar", actions) { saveRoutine(r.copy(items = Codec.plan(items.filterIndexed { i, _ -> i != index }))) }
        }
        button("Eliminar rutina") { AlertDialog.Builder(this).setTitle("¿Eliminar esta rutina?").setMessage("El historial de sesiones se conserva.").setNegativeButton("Cancelar", null).setPositiveButton("Eliminar") { _, _ -> work({ db.dao().deleteRoutine(r.id); refreshData() }) { navigate("routines") } }.show() }
    }
    private fun saveRoutine(r: Routine) { work({ db.dao().save(r); refreshData() }) { routineDetail(r) } }
    private fun pickExercise(r: Routine) {
        val items = Codec.plan(r.items)
        val choices = exercises.filter { e -> items.none { it.exerciseId == e.id } }.sortedBy { it.name }
        if(choices.isEmpty()) { toast("Ya añadiste las 100 familias"); return }
        AlertDialog.Builder(this).setTitle("Elige un ejercicio").setItems(choices.map { "${it.name} · ${it.equipment}" }.toTypedArray()) { _, pos ->
            val e = choices[pos]; saveRoutine(r.copy(items = Codec.plan(items + PlanItem(e.id, 2, if(e.unit == "seconds") 30 else 10, 60))))
        }.setNegativeButton("Cancelar", null).show()
    }
    private fun editItem(r: Routine, index: Int) {
        val items = Codec.plan(r.items).toMutableList(); val old = items[index]; val e = ex(old.exerciseId)
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20),0,dp(20),0) }
        fun field(caption: String, value: Int): EditText { label(caption, parent=form); return EditText(this).apply { inputType=InputType.TYPE_CLASS_NUMBER; setText(value.toString()); form.addView(this) } }
        val sets = field("Series (1–10)", old.sets); val amount = field("${unit(e)} (1–${if(e.unit == "seconds") 3600 else 100})", old.amount); val rest = field("Descanso en segundos (10–600)", old.rest)
        val d = AlertDialog.Builder(this).setTitle(e.name).setView(form).setPositiveButton("Guardar", null).setNegativeButton("Cancelar",null).create()
        d.setOnShowListener { d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val s=sets.text.toString().toIntOrNull(); val a=amount.text.toString().toIntOrNull(); val t=rest.text.toString().toIntOrNull()
            if(s == null || s !in 1..10) { sets.error="Usa 1–10"; return@setOnClickListener }
            if(a == null || a !in 1..(if(e.unit == "seconds") 3600 else 100)) { amount.error="Valor fuera de rango"; return@setOnClickListener }
            if(t == null || t !in 10..600) { rest.error="Usa 10–600"; return@setOnClickListener }
            items[index]=PlanItem(e.id,s,a,t); saveRoutine(r.copy(items=Codec.plan(items))); d.dismiss()
        } }; d.show()
    }
    private fun persistActive() { prefs.edit().putString("active", active?.toString()).commit() }
    private fun startWorkout(r: Routine) {
        if(active != null) { AlertDialog.Builder(this).setTitle("Tienes una sesión abierta").setMessage("Continúala o descártala desde su pantalla antes de empezar otra.").setPositiveButton("Continuar") { _, _ -> navigate("session") }.setNegativeButton("Volver",null).show(); return }
        active=JSONObject().put("id",UUID.randomUUID().toString()).put("name",r.name).put("plan",r.items).put("logs","[]").put("start",System.currentTimeMillis())
        persistActive(); navigate("session")
    }
    private fun workout() {
        val a=active ?: run { navigate("home"); return }
        label("MISIÓN EN CURSO", 13); label(a.getString("name"), 26)
        val plans=Codec.plan(a.getString("plan")); val logs=Codec.logs(a.getString("logs"))
        label("${logs.size} / ${plans.sumOf { it.sets }} series registradas. Guarda cada serie al terminarla.")
        val restLabel=label("Descanso listo",20)
        fun countdown(seconds: Int) {
            timer?.cancel(); a.put("restUntil",System.currentTimeMillis()+seconds*1000L); persistActive()
            timer=object:CountDownTimer(seconds*1000L,1000) {
                override fun onTick(ms:Long) { restLabel.text="Descanso · ${(ms+999)/1000} s" }
                override fun onFinish() { restLabel.text="Descanso terminado" }
            }.start()
        }
        val remaining=((a.optLong("restUntil")-System.currentTimeMillis()+999)/1000).toInt()
        if(remaining>0) countdown(remaining)
        plans.forEach { p ->
            val e=ex(p.exerciseId); val done=logs.filter { it.exerciseId==e.id }
            val actions=card("${done.size} / ${p.sets} SERIES",e.name,"Objetivo: ${p.amount} ${unit(e)} · ${p.rest} s de descanso")
            button("Ver técnica",actions) { exerciseDetail(e) }
            if(done.isNotEmpty()) label(done.mapIndexed { i,l -> "Serie ${i+1}: ${l.amount} ${unit(e)} · ${l.weight} kg" }.joinToString("\n"), parent=actions)
            if(done.size < p.sets) {
                val inputs=layoutInflater.inflate(R.layout.set_input,actions,false); actions.addView(inputs)
                val amount=inputs.findViewById<EditText>(R.id.reps).apply { setText(p.amount.toString()); hint=unit(e); contentDescription="Cantidad de ${e.name}" }
                val weight=inputs.findViewById<EditText>(R.id.weight).apply { setText(done.lastOrNull()?.weight?.toString() ?: "0"); contentDescription="Peso de ${e.name}" }
                inputs.findViewById<Button>(R.id.addSet).setOnClickListener {
                    val n=amount.text.toString().toIntOrNull(); val kg=weight.text.toString().replace(',','.').toDoubleOrNull()
                    if(n==null || n !in 1..(if(e.unit=="seconds") 3600 else 100)) { amount.error="Cantidad no válida"; return@setOnClickListener }
                    if(kg==null || !kg.isFinite() || kg !in 0.0..500.0) { weight.error="Peso entre 0 y 500 kg"; return@setOnClickListener }
                    a.put("logs",Codec.logs(logs+SetLog(e.id,n,kg))); a.put("restUntil",System.currentTimeMillis()+p.rest*1000L); persistActive(); render()
                }
            }
            if(done.isNotEmpty()) button("Deshacer última serie",actions) {
                val updated=logs.toMutableList(); val idx=updated.indexOfLast { it.exerciseId==e.id }; updated.removeAt(idx)
                a.put("logs",Codec.logs(updated)); persistActive(); render()
            }
        }
        button("Finalizar sesión") {
            if(logs.isEmpty()) { toast("Registra al menos una serie"); return@button }
            val complete=plans.all { p -> logs.count { it.exerciseId==p.exerciseId }>=p.sets }
            if(!complete) AlertDialog.Builder(this).setTitle("Guardar sesión parcial").setMessage("Se guardarán ${logs.size} series y solo la experiencia de esas series.").setPositiveButton("Guardar") { _, _ -> finishWorkout(false) }.setNegativeButton("Continuar",null).show()
            else finishWorkout(true)
        }
        button("Descartar sesión") { AlertDialog.Builder(this).setTitle("¿Descartar sesión?").setMessage("Se perderán las series de esta sesión abierta.").setPositiveButton("Descartar") { _, _ -> active=null; persistActive(); navigate("home") }.setNegativeButton("Continuar",null).show() }
    }
    private fun finishWorkout(complete:Boolean) {
        if(saving) return
        val a=active ?: return; saving=true
        android.util.Log.i("HunterTraining", "Saving session")
        val logs=Codec.logs(a.getString("logs")); val xp=RoutineGenerator.xp(logs)
        val now=System.currentTimeMillis()
        val session=Session(a.getString("id"),a.getString("name"),now,(now-a.getLong("start")).coerceAtLeast(0),a.getString("logs"),xp,complete)
        work({ db.dao().finish(session); refreshData() }) {
            android.util.Log.i("HunterTraining", "Session saved; opening progress")
            active=null; persistActive(); saving=false; navigate("progress"); toast("Sesión guardada · +$xp XP")
        }
    }
    private fun progress() {
        label("Tu evolución",28)
        val xp=sessions.sumOf { it.xp }; val allLogs=sessions.flatMap { Codec.logs(it.logs) }
        card("RANGO ${RoutineGenerator.rank(xp)}", "Nivel ${1+xp/250}", "$xp XP · ${sessions.size} sesiones · ${allLogs.size} series\n${250-xp%250} XP para el próximo nivel")
        label("Historial de misiones",22)
        if(sessions.isEmpty()) label("Tu historia empieza con la primera serie. Completa una sesión para verla aquí.")
        val date=SimpleDateFormat("dd MMM yyyy · HH:mm",Locale("es"))
        sessions.forEach { s ->
            val logs=Codec.logs(s.logs)
            val actions=card(if(s.complete) "COMPLETADA" else "PARCIAL",s.name,"${date.format(Date(s.time))}\n${logs.size} series · ${s.duration/60000} min · +${s.xp} XP")
            button("Ver registro",actions) {
                AlertDialog.Builder(this).setTitle(s.name).setMessage(logs.groupBy { it.exerciseId }.entries.joinToString("\n\n") { (id, entries) ->
                    val e=ex(id); e.name+"\n"+entries.mapIndexed { i,l -> "${i+1}. ${l.amount} ${unit(e)} · ${l.weight} kg" }.joinToString("\n")
                }).setPositiveButton("Cerrar",null).show()
            }
        }
        button("Fuentes y acerca de") {
            val source=assets.open("SOURCES.txt").bufferedReader().use { it.readText() }
            AlertDialog.Builder(this).setTitle("Hunter Training 1.0").setMessage(source+"\nTodos tus datos se guardan en este dispositivo. Sin cuentas, anuncios ni seguimiento. Desinstalar elimina tus datos locales.").setPositiveButton("Cerrar",null).show()
        }
    }
}
