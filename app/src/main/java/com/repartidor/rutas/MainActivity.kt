package com.repartidor.rutas

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.core.view.GravityCompat
import androidx.core.widget.doAfterTextChanged

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.repartidor.rutas.data.AppDatabase
import com.repartidor.rutas.data.Order
import com.repartidor.rutas.databinding.ActivityMainBinding
import com.repartidor.rutas.databinding.DialogAddOrderBinding
import com.repartidor.rutas.databinding.DialogCalculatorBinding
import com.repartidor.rutas.databinding.DialogClientProfileBinding
import com.repartidor.rutas.databinding.DialogClientsListBinding
import com.repartidor.rutas.databinding.DialogSettingsBinding
import coil.load
import coil.transform.CircleCropTransformation
import com.repartidor.rutas.databinding.DialogFinishDeliveryBinding
import com.repartidor.rutas.databinding.DialogStatsBinding
import com.repartidor.rutas.ui.ClientAdapter
import com.repartidor.rutas.ui.CobroAdapter
import com.repartidor.rutas.ui.OrderAdapter
import com.repartidor.rutas.util.LocationHelper
import com.repartidor.rutas.util.OrderCoordinatePolicy
import com.repartidor.rutas.util.RouteOptimizer
import com.repartidor.rutas.util.ShareLocationParser
import com.repartidor.rutas.util.TicketScanner
import com.repartidor.rutas.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: OrderAdapter
    private val dao by lazy { AppDatabase.getInstance(this).orderDao() }
    private val clientDao by lazy { AppDatabase.getInstance(this).clientDao() }

    private var fotoUriActual: Uri? = null
    private var currentDialogBinding: DialogAddOrderBinding? = null
    private var currentProfileBinding: DialogClientProfileBinding? = null
    private var isScanningTicket = false
    
    private var statsJob: Job? = null
    private var cobrosJob: Job? = null
    private var clientesJob: Job? = null
    
    private var tempCameraUri: Uri? = null
    private var pendingCameraScan: Boolean? = null

    private val seleccionarFoto = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) procesarFoto(uri) }
    private val tomarFoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { success -> if (success) tempCameraUri?.let { procesarFoto(it) } }

    private fun procesarFoto(uri: Uri) {
        try {
            if (isScanningTicket) {
                isScanningTicket = false
                currentDialogBinding?.let { db ->
                    TicketScanner.scanTicket(this, uri, { name, phone, price, address, notes ->
                        name?.let { db.inputNombre.setText(it) }
                        phone?.let { db.inputTelefono.setText(it) }
                        price?.let { db.inputPrecio.setText(it.toString()) }
                        address?.let { db.inputDireccion.setText(it) }
                        notes?.let { val actual = db.inputNotas.text.toString(); db.inputNotas.setText(if(actual.isBlank()) it else "$actual\n$it") }
                        Toast.makeText(this, "Ticket escaneado con éxito", Toast.LENGTH_SHORT).show()
                    }, { e -> AppLogger.log(this, "Error en TicketScanner", e); Toast.makeText(this, "Error al escanear: ${e.message}", Toast.LENGTH_SHORT).show() })
                }
            } else {
                fotoUriActual = uri
                try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
                currentDialogBinding?.let { db -> db.imgFotoPreview.setImageURI(uri); db.cardFotoPreview.visibility = View.VISIBLE; db.layoutUploadPrompt.visibility = View.GONE }
                currentProfileBinding?.let { db -> db.imgProfileFoto.load(uri) { transformations(CircleCropTransformation()) } }
            }
        } catch (e: Exception) { AppLogger.log(this, "Error en procesarFoto", e) }
    }

    private fun iniciarCamara(scanning: Boolean) {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) { pendingCameraScan = scanning; solicitarPermisoCamara.launch(Manifest.permission.CAMERA); return }
            pendingCameraScan = null
            isScanningTicket = scanning
            val photoFile = try { crearArchivoImagen() } catch (ex: Exception) { AppLogger.log(this, "Error al crear archivo de imagen", ex); null }
            photoFile?.also { val photoURI: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", it); tempCameraUri = photoURI; tomarFoto.launch(photoURI) }
        } catch (e: Exception) { AppLogger.log(this, "Error en iniciarCamara", e) }
    }

    private fun crearArchivoImagen(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

    private fun mostrarOpcionesImagen(scanning: Boolean) {
        val opciones = arrayOf("Cámara", "Galería")
        AlertDialog.Builder(this).setTitle("Seleccionar Imagen").setItems(opciones) { _, which ->
            when (which) {
                0 -> iniciarCamara(scanning)
                1 -> { isScanningTicket = scanning; seleccionarFoto.launch("image/*") }
            }
        }.show()
    }

    private val solicitarPermisoCamara = registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        val scanningPendiente = pendingCameraScan
        pendingCameraScan = null
        if (concedido && scanningPendiente != null) iniciarCamara(scanningPendiente)
        else if (!concedido) Toast.makeText(this, "Se necesita permiso de cámara para tomar fotos", Toast.LENGTH_LONG).show()
    }
    private val solicitarPermisosUbicacion = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permisos -> if (permisos[Manifest.permission.ACCESS_FINE_LOCATION] ?: false || permisos[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false) optimizarRuta() else Toast.makeText(this, "Se necesita el permiso de ubicacion", Toast.LENGTH_LONG).show() }

    private val exportarClientesLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) lifecycleScope.launch {
            try {
                val clientes = clientDao.getAll().first()
                val json = Gson().toJson(clientes)
                contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                Toast.makeText(this@MainActivity, "Exportado con éxito", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { AppLogger.log(this@MainActivity, "Error exportar", e) }
        }
    }

    private val importarClientesLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) lifecycleScope.launch {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val json = BufferedReader(InputStreamReader(inputStream)).readText()
                    val type = object : TypeToken<List<com.repartidor.rutas.data.Client>>() {}.type
                    val clientes: List<com.repartidor.rutas.data.Client> = Gson().fromJson(json, type)
                    clientes.forEach { client -> clientDao.insert(client.copy(id = 0)) }
                    Toast.makeText(this@MainActivity, "${clientes.size} clientes importados", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) { AppLogger.log(this@MainActivity, "Error importar", e) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val prefs = getSharedPreferences("RepaAppPrefs", MODE_PRIVATE)
            val themeMode = prefs.getInt("theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            AppCompatDelegate.setDefaultNightMode(themeMode)

            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            setupDrawer()

            val is24h = prefs.getBoolean("format24h", false)
            adapter = OrderAdapter(
                isFormat24h = is24h,
                onLlamar = { llamar(it) },
                onLongLlamar = { copiarAlPortapapeles(it) },
                onNavegar = { navegar(it) },
                onEntregado = { marcarEntregado(it) },
                onLongEntregado = { confirmarEntregaLarga(it) },
                onEliminar = { },
                onEditar = { mostrarDialogoEditarPedido(it) }
            )
            binding.recyclerOrders.layoutManager = LinearLayoutManager(this)
            binding.recyclerOrders.adapter = adapter
            binding.recyclerOrders.itemAnimator = null // Evitar conflictos con animaciones manuales

            lifecycleScope.launch { dao.observarPendientes().collectLatest { lista -> adapter.submitList(lista); binding.layoutVacio.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE } }
            lifecycleScope.launch { dao.observarTotalPropinas().collectLatest { totalPropinas -> binding.textTotalPropinas.text = "💰 Propinas: $%.2f".format(totalPropinas) } }

            binding.btnMenu.setOnClickListener { binding.drawerLayout.openDrawer(GravityCompat.START) }
            binding.btnOptimizar.setOnClickListener { verificarPermisoYOptimizar() }
            binding.btnLimpiarTodo.setOnClickListener { mostrarOpcionesEliminacion() }
            binding.fabAgregar.setOnClickListener { mostrarDialogoAgregarPedido(null) }
            binding.fabCalc.setOnClickListener { mostrarDialogoCalculadora() }
            procesarIntentCompartido(intent)
        } catch (e: Exception) { AppLogger.log(this, "Crash en onCreate", e) }
    }

    private fun setupDrawer() {
        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_ruta -> {}
                R.id.nav_clientes -> mostrarDialogoClientes()
                R.id.nav_entregar_dinero -> mostrarDialogoCobrosPendientes()
                R.id.nav_ganancias -> mostrarDialogoEstadisticas()
                R.id.nav_limpiar -> confirmarLimpiarEntregados()
                R.id.nav_reset_propinas -> confirmarResetPropinas()
                R.id.nav_config -> mostrarDialogoConfiguracion()
                R.id.nav_donar -> abrirPayPal()
                R.id.nav_feedback -> exportarLogs()
                R.id.nav_privacidad -> mostrarPrivacidad()
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun mostrarOpcionesEliminacion() {
        val opciones = arrayOf("Borrar toda la ruta", "Seleccionar pedidos para borrar")
        AlertDialog.Builder(this).setTitle("Acciones de Eliminación").setItems(opciones) { _, which ->
            when (which) {
                0 -> confirmarLimpiarTodo()
                1 -> mostrarDialogoSeleccionBorrar()
            }
        }.show()
    }

    private fun mostrarDialogoSeleccionBorrar() {
        lifecycleScope.launch {
            val pedidos = dao.obtenerPendientesLista()
            if (pedidos.isEmpty()) { Toast.makeText(this@MainActivity, "No hay pedidos", Toast.LENGTH_SHORT).show(); return@launch }
            val nombres = pedidos.map { "${it.nombre} ($${it.precio})" }.toTypedArray()
            val seleccionados = BooleanArray(pedidos.size) { false }
            AlertDialog.Builder(this@MainActivity).setTitle("Seleccionar pedidos").setMultiChoiceItems(nombres, seleccionados) { _, index, isChecked -> seleccionados[index] = isChecked }.setPositiveButton("Confirmar borrado") { _, _ -> lifecycleScope.launch { pedidos.filterIndexed { index, _ -> seleccionados[index] }.forEach { dao.eliminar(it.id) }; Toast.makeText(this@MainActivity, "Pedidos eliminados", Toast.LENGTH_SHORT).show() } }.setNegativeButton("Cancelar", null).show()
        }
    }

    private fun mostrarDialogoConfiguracion() {
        try {
            val prefs = getSharedPreferences("RepaAppPrefs", MODE_PRIVATE)
            val configBinding = DialogSettingsBinding.inflate(layoutInflater)
            if (prefs.getBoolean("format24h", false)) configBinding.toggleTimeFormat.check(R.id.btn24h)
            else configBinding.toggleTimeFormat.check(R.id.btn12h)
            val currentTheme = prefs.getInt("theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            when(currentTheme) {
                AppCompatDelegate.MODE_NIGHT_NO -> configBinding.toggleTheme.check(R.id.btnThemeLight)
                AppCompatDelegate.MODE_NIGHT_YES -> configBinding.toggleTheme.check(R.id.btnThemeDark)
                else -> configBinding.toggleTheme.check(R.id.btnThemeLight)
            }
            val dialog = AlertDialog.Builder(this, R.style.PremiumDialogTheme).setView(configBinding.root).create()
            configBinding.toggleTimeFormat.addOnButtonCheckedListener { _, checkedId, isChecked -> if (isChecked) { prefs.edit().putBoolean("format24h", checkedId == R.id.btn24h).apply(); recreate() } }
            configBinding.toggleTheme.addOnButtonCheckedListener { _, checkedId, isChecked -> if (isChecked) { val mode = if (checkedId == R.id.btnThemeDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO; prefs.edit().putInt("theme", mode).apply(); AppCompatDelegate.setDefaultNightMode(mode) } }
            configBinding.btnCerrarConfig.setOnClickListener { dialog.dismiss() }
            dialog.show()
        } catch (e: Exception) { AppLogger.log(this, "Error en mostrarDialogoConfiguracion", e) }
    }

    private fun exportarLogs() {
        val logFile = AppLogger.getLogFile(this)
        if (!logFile.exists()) { Toast.makeText(this, "No hay logs todavía", Toast.LENGTH_SHORT).show(); return }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", logFile)
        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_STREAM, uri); putExtra(Intent.EXTRA_SUBJECT, "RepaApp Debug Logs"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        startActivity(Intent.createChooser(intent, "Enviar logs por..."))
    }

    private fun confirmarLimpiarTodo() { AlertDialog.Builder(this).setTitle(R.string.dialog_limpiar_todo_titulo).setMessage(R.string.dialog_limpiar_todo_msg).setPositiveButton(R.string.dialog_confirmar) { _, _ -> lifecycleScope.launch { try { dao.eliminarTodo(); Toast.makeText(this@MainActivity, R.string.dialog_borrar_todo, Toast.LENGTH_SHORT).show() } catch (e: Exception) { AppLogger.log(this@MainActivity, "Error en eliminarTodo", e) } } }.setNegativeButton(R.string.dialog_cancelar, null).show() }
    private fun confirmarLimpiarEntregados() { lifecycleScope.launch { try { dao.limpiarEntregados(); Toast.makeText(this@MainActivity, R.string.limpiar_entregados, Toast.LENGTH_SHORT).show() } catch (e: Exception) { AppLogger.log(this@MainActivity, "Error en limpiarEntregados", e) } } }
    private fun confirmarResetPropinas() { AlertDialog.Builder(this).setTitle(R.string.dialog_reset_propinas_titulo).setMessage(R.string.dialog_reset_propinas_msg).setPositiveButton(R.string.dialog_confirmar) { _, _ -> lifecycleScope.launch { try { dao.reiniciarPropinas(); Toast.makeText(this@MainActivity, R.string.menu_reset_propinas, Toast.LENGTH_SHORT).show() } catch (e: Exception) { AppLogger.log(this@MainActivity, "Error en reiniciarPropinas", e) } } }.setNegativeButton(R.string.dialog_cancelar, null).show() }
    private fun abrirPayPal() { val url = "https://paypal.me/RepaApp"; val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)); startActivity(intent) }
    private fun mostrarPrivacidad() { AlertDialog.Builder(this).setTitle(R.string.privacidad_titulo).setMessage(R.string.privacidad_texto).setPositiveButton(R.string.dialog_cerrar, null).show() }

    private fun mostrarDialogoEstadisticas() {
        val statsBinding = DialogStatsBinding.inflate(layoutInflater)
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0); cal.set(java.util.Calendar.SECOND, 0)
        val inicioHoy = cal.timeInMillis; val finHoy = System.currentTimeMillis()
        cal.add(java.util.Calendar.DAY_OF_YEAR, -7); val inicioSemana = cal.timeInMillis
        statsJob?.cancel(); statsJob = lifecycleScope.launch { try { launch { dao.obtenerPropinasRango(inicioHoy, finHoy).collectLatest { statsBinding.textPropinaHoy.text = "$%.2f".format(it) } }; launch { dao.obtenerPropinasRango(inicioSemana, finHoy).collectLatest { statsBinding.textPropinaSemana.text = "$%.2f".format(it) } } } catch (e: Exception) { AppLogger.log(this@MainActivity, "Error en stats coroutine", e) } }
        AlertDialog.Builder(this).setView(statsBinding.root).setPositiveButton("Cerrar", null).setOnDismissListener { statsJob?.cancel() }.show()
    }

    private fun mostrarDialogoCobrosPendientes() {
        val dialogBinding = DialogClientsListBinding.inflate(layoutInflater)
        val cobroAdapter = CobroAdapter(onConfirmar = { order -> lifecycleScope.launch { try { dao.marcarCobroEntregado(order.id); Toast.makeText(this@MainActivity, "Cobro entregado", Toast.LENGTH_SHORT).show() } catch (e: Exception) { AppLogger.log(this@MainActivity, "Error en marcarCobroEntregado", e) } } })
        dialogBinding.recyclerClients.layoutManager = LinearLayoutManager(this); dialogBinding.recyclerClients.adapter = cobroAdapter
        cobrosJob?.cancel(); cobrosJob = lifecycleScope.launch { try { dao.observarPorCobrar().collectLatest { cobroAdapter.submitList(it) } } catch (e: Exception) { AppLogger.log(this@MainActivity, "Error en observarPorCobrar", e) } }
        AlertDialog.Builder(this).setTitle("Entregar Dinero").setView(dialogBinding.root).setPositiveButton("Cerrar", null).setOnDismissListener { cobrosJob?.cancel() }.show()
    }

    private fun mostrarDialogoCalculadora() {
        try {
            val calcBinding = DialogCalculatorBinding.inflate(layoutInflater)
            val dialog = AlertDialog.Builder(this, R.style.PremiumDialogTheme).setView(calcBinding.root).create()
            var displayValue = ""; var lastValue = 0.0; var currentOp = ""; var isNewOp = true
            fun updateDisplay() { calcBinding.calcDisplay.text = if (displayValue.isEmpty()) "0" else displayValue }
            val numClickListener = View.OnClickListener { v ->
                val btn = v as com.google.android.material.button.MaterialButton
                if (isNewOp) { displayValue = ""; isNewOp = false }
                displayValue += btn.text; updateDisplay()
            }
            listOf(R.id.btnCalc0, R.id.btnCalc1, R.id.btnCalc2, R.id.btnCalc3, R.id.btnCalc4, R.id.btnCalc5, R.id.btnCalc6, R.id.btnCalc7, R.id.btnCalc8, R.id.btnCalc9).forEach { id -> calcBinding.root.findViewById<View>(id).setOnClickListener(numClickListener) }
            calcBinding.btnCalcDot.setOnClickListener { if (!displayValue.contains(".")) { displayValue += if (displayValue.isEmpty()) "0." else "."; isNewOp = false; updateDisplay() } }
            calcBinding.btnCalcC.setOnClickListener { displayValue = ""; lastValue = 0.0; currentOp = ""; isNewOp = true; updateDisplay() }
            calcBinding.btnCalcDel.setOnClickListener { if (displayValue.isNotEmpty()) { displayValue = displayValue.dropLast(1); updateDisplay() } }
            val opClickListener = View.OnClickListener { v -> val op = (v as com.google.android.material.button.MaterialButton).text.toString(); if (displayValue.isNotEmpty()) { lastValue = displayValue.toDoubleOrNull() ?: 0.0; currentOp = op; isNewOp = true } }
            calcBinding.btnCalcPlus.setOnClickListener(opClickListener); calcBinding.btnCalcMinus.setOnClickListener(opClickListener); calcBinding.btnCalcMult.setOnClickListener(opClickListener); calcBinding.btnCalcDiv.setOnClickListener(opClickListener)
            calcBinding.btnCalcEqual.setOnClickListener { if (currentOp.isNotEmpty() && displayValue.isNotEmpty()) { val currentValue = displayValue.toDoubleOrNull() ?: 0.0; val result = when (currentOp) { "+" -> lastValue + currentValue; "-" -> lastValue - currentValue; "×" -> lastValue * currentValue; "÷" -> if (currentValue != 0.0) lastValue / currentValue else 0.0; else -> currentValue }; displayValue = if (result % 1 == 0.0) result.toInt().toString() else "%.2f".format(result); currentOp = ""; isNewOp = true; updateDisplay() } }
            calcBinding.btnCalcCerrar.setOnClickListener { dialog.dismiss() }
            dialog.show()
        } catch (e: Exception) { AppLogger.log(this, "Error en calculadora", e) }
    }

    private fun mostrarDialogoClientes() {
        try {
            val dialogBinding = DialogClientsListBinding.inflate(layoutInflater)
            val listaDialog = AlertDialog.Builder(this).setTitle("Mis Clientes").setView(dialogBinding.root).setPositiveButton("Cerrar", null).create()
            dialogBinding.btnExportar.setOnClickListener { exportarClientesLauncher.launch("repa_clientes_${System.currentTimeMillis()}.json") }
            dialogBinding.btnImportar.setOnClickListener { importarClientesLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) }
            val clientAdapter = ClientAdapter(onClientClick = { mostrarDialogoPerfilCliente(it) }, onDeleteClick = { client -> lifecycleScope.launch { try { clientDao.delete(client); Toast.makeText(this@MainActivity, "Cliente eliminado", Toast.LENGTH_SHORT).show() } catch (e: Exception) { AppLogger.log(this@MainActivity, "Error eliminar cliente", e) } } })
            dialogBinding.recyclerClients.layoutManager = LinearLayoutManager(this); dialogBinding.recyclerClients.adapter = clientAdapter
            fun observarClientes() {
                clientesJob?.cancel()
                clientesJob = lifecycleScope.launch { try { clientDao.getAll().collectLatest { clientAdapter.submitList(it) } } catch (e: Exception) { AppLogger.log(this@MainActivity, "Error observar", e) } }
            }
            var busquedaJob: Job? = null
            val watcher = dialogBinding.inputBuscarCliente.doAfterTextChanged { text ->
                busquedaJob?.cancel()
                val query = text?.toString()?.trim().orEmpty()
                busquedaJob = lifecycleScope.launch {
                    try {
                        if (query.isBlank()) {
                            observarClientes()
                        } else {
                            clientesJob?.cancel()
                            clientAdapter.submitList(clientDao.search("%$query%"))
                        }
                    } catch (e: Exception) {
                        AppLogger.log(this@MainActivity, "Error busqueda", e)
                    }
                }
            }
            observarClientes()
            listaDialog.setOnDismissListener {
                busquedaJob?.cancel()
                clientesJob?.cancel()
                dialogBinding.inputBuscarCliente.removeTextChangedListener(watcher)
                dialogBinding.recyclerClients.adapter = null
            }
            listaDialog.show()
        } catch (e: Exception) { AppLogger.log(this, "Error en mostrarDialogoClientes", e) }
    }

    private fun mostrarDialogoPerfilCliente(client: com.repartidor.rutas.data.Client) {
        try {
            val profileBinding = DialogClientProfileBinding.inflate(layoutInflater)
            currentProfileBinding = profileBinding; fotoUriActual = client.fotoDomicilioUri?.let { Uri.parse(it) }
            profileBinding.inputProfileNombre.setText(client.nombre); profileBinding.inputProfileTelefono.setText(client.telefono); profileBinding.inputProfileDireccion.setText(client.direccion); profileBinding.inputProfileNotas.setText(client.notasGenerales ?: "")
            profileBinding.imgProfileFoto.load(client.fotoDomicilioUri) { crossfade(true); placeholder(android.R.drawable.ic_menu_gallery); error(android.R.drawable.ic_menu_report_image); transformations(CircleCropTransformation()) }
            profileBinding.btnCambiarFotoPerfil.setOnClickListener { mostrarOpcionesImagen(false) }
            
            when(client.frecuenciaPropina) { 
                "Siempre" -> profileBinding.chipGroupPropinaFrecuencia.check(R.id.chipPropinaSiempre)
                "A veces" -> profileBinding.chipGroupPropinaFrecuencia.check(R.id.chipPropinaAveces)
                "Nunca" -> profileBinding.chipGroupPropinaFrecuencia.check(R.id.chipPropinaNunca)
            }

            val dialog = AlertDialog.Builder(this, R.style.PremiumDialogTheme).setView(profileBinding.root).create()
            
            profileBinding.containerProfile.alpha = 0f; profileBinding.containerProfile.scaleX = 0.8f; profileBinding.containerProfile.scaleY = 0.8f
            dialog.setOnShowListener { profileBinding.containerProfile.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(400).setInterpolator(OvershootInterpolator(1.2f)).start() }
            
            profileBinding.btnCerrarPerfil.setOnClickListener { dialog.dismiss() }
            profileBinding.btnGuardarPerfil.setOnClickListener {
                val nuevoNombre = profileBinding.inputProfileNombre.text.toString().trim()
                if (nuevoNombre.isEmpty()) { Toast.makeText(this@MainActivity, "Nombre requerido", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                val nuevaFrecuencia = when(profileBinding.chipGroupPropinaFrecuencia.checkedButtonId) { 
                    R.id.chipPropinaSiempre -> "Siempre"
                    R.id.chipPropinaAveces -> "A veces"
                    R.id.chipPropinaNunca -> "Nunca"
                    else -> null 
                }
                lifecycleScope.launch { try { clientDao.insert(client.copy(nombre = nuevoNombre, telefono = profileBinding.inputProfileTelefono.text.toString().trim(), direccion = profileBinding.inputProfileDireccion.text.toString().trim().ifEmpty { null }, notasGenerales = profileBinding.inputProfileNotas.text.toString().trim().ifEmpty { null }, frecuenciaPropina = nuevaFrecuencia, fotoDomicilioUri = fotoUriActual?.toString() ?: client.fotoDomicilioUri)); Toast.makeText(this@MainActivity, "Actualizado", Toast.LENGTH_SHORT).show(); dialog.dismiss() } catch (e: Exception) { AppLogger.log(this@MainActivity, "Error actualizar perfil", e) } }
            }

            profileBinding.btnCrearPedidoDesdePerfil.setOnClickListener { try { dialog.dismiss(); val coordenadas = if (client.latitud != null && client.longitud != null) Pair(client.latitud, client.longitud) else null; mostrarDialogoAgregarPedidoDesdePerfil(client, coordenadas) } catch (e: Exception) { AppLogger.log(this@MainActivity, "Error btnCrearPedido", e) } }
            dialog.show()
        } catch (e: Exception) { AppLogger.log(this, "Error perfil cliente", e) }
    }
    
    private fun mostrarDialogoAgregarPedidoDesdePerfil(client: com.repartidor.rutas.data.Client, coordenadas: Pair<Double, Double>?) { try { fotoUriActual = client.fotoDomicilioUri?.let { Uri.parse(it) }; mostrarDialogoAgregarPedido(coordenadas, orderDatosPrecargados = client) } catch (e: Exception) { AppLogger.log(this, "Error agregar desde perfil", e) } }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); procesarIntentCompartido(intent) }

    private fun procesarIntentCompartido(intent: Intent?) {
        try {
            if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
                val texto = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
                val coordenadas = ShareLocationParser.extraerCoordenadas(texto)
                if (coordenadas != null) mostrarDialogoAgregarPedido(coordenadas)
                else { Toast.makeText(this, "No se detectó ubicación", Toast.LENGTH_LONG).show(); mostrarDialogoAgregarPedido(null) }
            }
        } catch (e: Exception) { AppLogger.log(this, "Error compartido", e) }
    }

    private fun mostrarDialogoEditarPedido(order: Order) { mostrarDialogoAgregarPedido(if (order.latitud != null && order.longitud != null) Pair(order.latitud, order.longitud) else null, orderExistente = order) }

    private fun mostrarDialogoAgregarPedido(coordenadas: Pair<Double, Double>?, orderExistente: Order? = null, orderDatosPrecargados: com.repartidor.rutas.data.Client? = null) {
        try {
            val dialogBinding = DialogAddOrderBinding.inflate(layoutInflater)
            currentDialogBinding = dialogBinding
            if (orderExistente != null) { fotoUriActual = orderExistente.fotoUri?.let { Uri.parse(it) }; dialogBinding.inputNombre.setText(orderExistente.nombre); dialogBinding.inputTelefono.setText(orderExistente.telefono); dialogBinding.inputDireccion.setText(orderExistente.direccionTexto); dialogBinding.inputNotas.setText(orderExistente.notas); dialogBinding.inputPrecio.setText(orderExistente.precio.toString()); when(orderExistente.tipoPago) { "Transferencia" -> dialogBinding.chipGroupPago.check(R.id.chipTransferencia); "Pagado" -> dialogBinding.chipGroupPago.check(R.id.chipPagado); else -> dialogBinding.chipGroupPago.check(R.id.chipCobrar) }; dialogBinding.textTituloModal.text = "Editar Pedido" }
            else if (orderDatosPrecargados != null) { fotoUriActual = orderDatosPrecargados.fotoDomicilioUri?.let { Uri.parse(it) }; dialogBinding.inputNombre.setText(orderDatosPrecargados.nombre); dialogBinding.inputTelefono.setText(orderDatosPrecargados.telefono); dialogBinding.inputDireccion.setText(orderDatosPrecargados.direccion); dialogBinding.inputNotas.setText(orderDatosPrecargados.notasGenerales); dialogBinding.textTituloModal.text = "Nuevo Pedido" }
            if (fotoUriActual != null) { try { dialogBinding.imgFotoPreview.setImageURI(fotoUriActual); dialogBinding.cardFotoPreview.visibility = View.VISIBLE; dialogBinding.layoutUploadPrompt.visibility = View.GONE } catch (e: Exception) { AppLogger.log(this, "Error foto preview", e) } }
            lifecycleScope.launch { try { val lista = clientDao.getAll().first(); val adapterAuto = ArrayAdapter(this@MainActivity, android.R.layout.simple_dropdown_item_1line, lista.map { it.nombre }); dialogBinding.inputNombre.setAdapter(adapterAuto); dialogBinding.inputNombre.setOnItemClickListener { _, _, pos, _ -> val sel = lista.find { it.nombre == adapterAuto.getItem(pos) }; sel?.let { cli -> dialogBinding.inputTelefono.setText(cli.telefono); dialogBinding.inputDireccion.setText(cli.direccion); cli.fotoDomicilioUri?.let { u -> fotoUriActual = Uri.parse(u); dialogBinding.imgFotoPreview.setImageURI(fotoUriActual); dialogBinding.cardFotoPreview.visibility = View.VISIBLE; dialogBinding.layoutUploadPrompt.visibility = View.GONE }; if (cli.notasGenerales != null && dialogBinding.inputNotas.text.isNullOrBlank()) dialogBinding.inputNotas.setText(cli.notasGenerales) } } } catch (e: Exception) { AppLogger.log(this@MainActivity, "Error auto", e) } }
            if (coordenadas != null && coordenadas.first != null && coordenadas.second != null) { try { dialogBinding.textUbicacionDetectada.text = "Ubicación detectada: ${coordenadas.first}, ${coordenadas.second}"; dialogBinding.textUbicacionDetectada.visibility = View.VISIBLE } catch (e: Exception) { dialogBinding.textUbicacionDetectada.visibility = View.GONE } } else dialogBinding.textUbicacionDetectada.visibility = View.GONE
            dialogBinding.btnEscanearTicket.setOnClickListener { mostrarOpcionesImagen(true) }; dialogBinding.btnSeleccionarFoto.setOnClickListener { mostrarOpcionesImagen(false) }
            val dialogo = AlertDialog.Builder(this, R.style.PremiumDialogTheme).setView(dialogBinding.root).create()
            dialogBinding.containerModal.alpha = 0f; dialogBinding.containerModal.scaleX = 0.8f; dialogBinding.containerModal.scaleY = 0.8f
            dialogo.setOnShowListener { dialogBinding.containerModal.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(400).setInterpolator(OvershootInterpolator(1.2f)).start() }
            dialogBinding.btnCancelarModal.setOnClickListener { dialogo.dismiss() }
            dialogBinding.btnGuardarPedido.setOnClickListener {
                val nombre = dialogBinding.inputNombre.text.toString().trim()
                if (nombre.isEmpty()) { dialogBinding.inputNombre.error = "Nombre requerido"; return@setOnClickListener }
                dialogBinding.btnGuardarPedido.isEnabled = false; dialogBinding.btnGuardarPedido.text = "..."
                val tipoPago = when(dialogBinding.chipGroupPago.checkedButtonId) { R.id.chipTransferencia -> "Transferencia"; R.id.chipPagado -> "Pagado"; else -> "Cobrar" }
                lifecycleScope.launch { try {
                    val direccion = dialogBinding.inputDireccion.text.toString().trim()
                    val coordenadasIniciales = OrderCoordinatePolicy.initialCoordinatesForSave(orderExistente != null, orderExistente?.direccionTexto, direccion, coordenadas)
                    var lat = coordenadasIniciales?.first; var lon = coordenadasIniciales?.second
                    val link = ShareLocationParser.extraerCoordenadas(direccion)
                    if (link != null) { lat = link.first; lon = link.second }
                    if (lat == null && direccion.isNotEmpty()) { val res = withTimeoutOrNull(5000) { geocodificar(direccion) }; if (res != null) { lat = res.first; lon = res.second } }
                    val cliEx = clientDao.search(nombre).firstOrNull { it.nombre == nombre }
                    val habit = cliEx?.frecuenciaPropina
                    clientDao.insert(com.repartidor.rutas.data.Client(id = cliEx?.id ?: 0, nombre = nombre, telefono = dialogBinding.inputTelefono.text.toString().trim(), direccion = direccion.ifEmpty { null }, latitud = lat, longitud = lon, fotoDomicilioUri = fotoUriActual?.toString() ?: cliEx?.fotoDomicilioUri, notasGenerales = dialogBinding.inputNotas.text.toString().trim().ifEmpty { null }, frecuenciaPropina = habit))
                    if (orderExistente != null) dao.actualizar(orderExistente.copy(nombre = nombre, telefono = dialogBinding.inputTelefono.text.toString().trim(), direccionTexto = direccion.ifEmpty { null }, latitud = lat, longitud = lon, notas = dialogBinding.inputNotas.text.toString().trim().ifEmpty { null }, fotoUri = fotoUriActual?.toString(), precio = dialogBinding.inputPrecio.text.toString().toDoubleOrNull() ?: 0.0, tipoPago = tipoPago, frecuenciaPropina = habit))
                    else dao.insertar(Order(nombre = nombre, telefono = dialogBinding.inputTelefono.text.toString().trim(), direccionTexto = direccion.ifEmpty { null }, latitud = lat, longitud = lon, notas = dialogBinding.inputNotas.text.toString().trim().ifEmpty { null }, fotoUri = fotoUriActual?.toString(), precio = dialogBinding.inputPrecio.text.toString().toDoubleOrNull() ?: 0.0, tipoPago = tipoPago, frecuenciaPropina = habit))
                    Toast.makeText(this@MainActivity, "Listo", Toast.LENGTH_SHORT).show(); dialogo.dismiss()
                } catch (e: Exception) { AppLogger.log(this@MainActivity, "Error guardar", e); dialogBinding.btnGuardarPedido.isEnabled = true; dialogBinding.btnGuardarPedido.text = "Guardar" } }
            }
            dialogo.setOnDismissListener { currentDialogBinding = null; fotoUriActual = null }
            dialogo.show()
        } catch (e: Exception) { AppLogger.log(this, "Error global agregar", e) }
    }

    private suspend fun geocodificar(dir: String): Pair<Double, Double>? = withContext(Dispatchers.IO) { try { val geo = Geocoder(this@MainActivity, Locale.getDefault()); if (android.os.Build.VERSION.SDK_INT >= 33) { suspendCancellableCoroutine { cont -> geo.getFromLocationName(dir, 1) { res -> if (res.isNotEmpty()) cont.resume(Pair(res[0].latitude, res[0].longitude)) else cont.resume(null) } } } else { @Suppress("DEPRECATION") val res = geo.getFromLocationName(dir, 1); if (!res.isNullOrEmpty()) Pair(res[0].latitude, res[0].longitude) else null } } catch (e: Exception) { null } }
    private fun verificarPermisoYOptimizar() { if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) optimizarRuta() else solicitarPermisosUbicacion.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
    private fun optimizarRuta() { lifecycleScope.launch { try { val ubi = LocationHelper.obtenerUbicacionActual(this@MainActivity) ?: run { Toast.makeText(this@MainActivity, "Activa el GPS", Toast.LENGTH_LONG).show(); return@launch }; val pends = dao.obtenerPendientesLista() ?: emptyList(); val (ruta, _) = RouteOptimizer.optimizar(ubi.first, ubi.second, pends); if (ruta.isEmpty()) { Toast.makeText(this@MainActivity, "Sin ubicaciones", Toast.LENGTH_LONG).show(); return@launch }; dao.actualizarTodos(ruta.mapIndexed { i, o -> o.copy(secuencia = i) }); Toast.makeText(this@MainActivity, "Ruta optimizada", Toast.LENGTH_LONG).show() } catch (e: Exception) { AppLogger.log(this@MainActivity, "Error optimizar", e) } } }
    private fun llamar(o: Order) { try { val i = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${o.telefono}")); if (i.resolveActivity(packageManager) != null) startActivity(i) } catch (e: Exception) {} }
    
    private fun copiarAlPortapapeles(o: Order) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Teléfono Cliente", o.telefono)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Número copiado al portapapeles", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { AppLogger.log(this, "Error al copiar", e) }
    }

    private fun navegar(o: Order) { try { val lat = o.latitud ?: return; val lon = o.longitud ?: return; val i = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(o.nombre)})")); if (i.resolveActivity(packageManager) != null) startActivity(i) } catch (e: Exception) {} }
    
    private fun marcarEntregado(o: Order) { 
        try { 
            val fb = DialogFinishDeliveryBinding.inflate(layoutInflater)
            val dialog = AlertDialog.Builder(this, R.style.PremiumDialogTheme).setView(fb.root).create()
            fb.containerFinish.alpha = 0f; fb.containerFinish.scaleX = 0.8f; fb.containerFinish.scaleY = 0.8f
            dialog.setOnShowListener { fb.containerFinish.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(400).setInterpolator(OvershootInterpolator(1.2f)).start() }
            fb.btnCancelarEntrega.setOnClickListener { dialog.dismiss() }
            fb.btnConfirmarEntrega.setOnClickListener {
                val propina = fb.inputPropinaFinal.text.toString().toDoubleOrNull() ?: 0.0
                ejecutarEntrega(o, propina)
                dialog.dismiss()
            }
            dialog.show()
        } catch (e: Exception) { AppLogger.log(this, "Error en marcarEntregado", e) }
    }

    private fun confirmarEntregaLarga(o: Order) { AlertDialog.Builder(this).setTitle("Confirmar").setMessage("¿Entregado?").setPositiveButton("Sí") { _, _ -> ejecutarEntrega(o, 0.0) }.setNegativeButton("Cancelar", null).show() }
    private fun ejecutarEntrega(o: Order, p: Double) { try { val pos = adapter.currentList.indexOf(o); if (pos != -1) { val vh = binding.recyclerOrders.findViewHolderForAdapterPosition(pos); if (vh != null) vh.itemView.animate().translationY(100f).alpha(0f).setDuration(300).withEndAction { actualizarPedidoEntregado(o, p) }.start() else actualizarPedidoEntregado(o, p) } } catch (e: Exception) { actualizarPedidoEntregado(o, p) } }
    private fun actualizarPedidoEntregado(o: Order, p: Double) { lifecycleScope.launch { try { dao.marcarEntregado(o.id, p, System.currentTimeMillis()); com.google.android.material.snackbar.Snackbar.make(binding.root, "Entregado ✅", 5000).setAction("Deshacer") { lifecycleScope.launch { try { dao.actualizar(o.copy(entregado = false, propina = 0.0, entregadoEn = null)) } catch (e: Exception) {} } }.show() } catch (e: Exception) {} } }
    private fun eliminar(o: Order) { lifecycleScope.launch { try { dao.eliminar(o.id) } catch (e: Exception) {} } }
}
