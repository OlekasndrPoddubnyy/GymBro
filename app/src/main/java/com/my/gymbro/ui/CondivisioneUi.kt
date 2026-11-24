package com.my.gymbro.ui

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.text.TextPaint
import android.text.TextUtils
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.my.gymbro.data.QrPronto
import com.my.gymbro.data.SchedaCondivisa
import com.my.gymbro.data.eserciziNuovi
import com.my.gymbro.data.leggiQr
import com.my.gymbro.data.nomeLibero
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/** Il QR di una scheda da mostrare; [qr] null = la scheda è troppo grande per un QR leggibile. */
class QrDaMostrare(val nome: String, val qr: QrPronto?)

// ---------- Mostrare il QR ----------

@Composable
internal fun DialogoQr(q: QrDaMostrare, onChiudi: () -> Unit) {
    val pronto = q.qr
    if (pronto == null) {
        AlertDialog(
            onDismissRequest = onChiudi,
            title = { Text("Scheda troppo grande") },
            text = {
                Text(
                    "\"${q.nome}\" ha troppi esercizi per stare in un QR che si riesca a leggere. " +
                        "Duplicala, dividi gli esercizi tra le due copie e condividile una alla volta."
                )
            },
            confirmButton = { TextButton(onClick = onChiudi) { Text("OK") } },
        )
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var errore by remember { mutableStateOf<String?>(null) }
    val immagine = remember(pronto) { pronto.bitmap().asImageBitmap() }

    AlertDialog(
        onDismissRequest = onChiudi,
        modifier = Modifier.padding(horizontal = 16.dp),
        // Più largo del solito: un QR grande si inquadra meglio dall'altro telefono.
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(q.nome) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(
                    bitmap = immagine,
                    contentDescription = "QR della scheda ${q.nome}",
                    // Ingrandito senza sfumature: i quadratini restano netti.
                    filterQuality = FilterQuality.None,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                )
                Text(
                    "Sull'altro telefono: GymBro → Schede → Scansiona QR.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (pronto.noteTolte) {
                    Text(
                        "Le note non ci stavano: il QR contiene esercizi e serie, senza note.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                errore?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    errore = try {
                        condividiImmagine(context, pronto, q.nome)
                        null
                    } catch (e: Exception) {
                        "Immagine non condivisa (${e.message ?: e.javaClass.simpleName})."
                    }
                }
            }) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Condividi immagine")
            }
        },
        dismissButton = { TextButton(onClick = onChiudi) { Text("Chiudi") } },
    )
}

/** Un pixel per quadratino: lo schermo lo ingrandisce. */
private fun QrPronto.bitmap(): Bitmap {
    val pixel = IntArray(lato * lato) { if (scuro[it]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
    return Bitmap.createBitmap(pixel, lato, lato, Bitmap.Config.ARGB_8888)
}

/** QR grande con il nome della scheda sotto, da mandare su WhatsApp o per email. */
private fun immagineDaCondividere(qr: QrPronto, nome: String): Bitmap {
    val k = (1080 / qr.lato).coerceAtLeast(4)
    val latoPx = qr.lato * k
    val titolo = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        textSize = latoPx / 16f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    val sotto = TextPaint(titolo).apply {
        textSize = latoPx / 28f
        typeface = Typeface.DEFAULT
        color = android.graphics.Color.DKGRAY
    }
    val altezzaTesti = (titolo.textSize * 1.4f + sotto.textSize * 2.2f).toInt()
    val bmp = Bitmap.createBitmap(latoPx, latoPx + altezzaTesti, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(android.graphics.Color.WHITE)
    val nero = Paint().apply { color = android.graphics.Color.BLACK }
    for (y in 0 until qr.lato) for (x in 0 until qr.lato) {
        if (qr.scuro[y * qr.lato + x]) {
            canvas.drawRect((x * k).toFloat(), (y * k).toFloat(), ((x + 1) * k).toFloat(), ((y + 1) * k).toFloat(), nero)
        }
    }
    val testo = TextUtils.ellipsize(nome, titolo, latoPx * 0.9f, TextUtils.TruncateAt.END).toString()
    canvas.drawText(testo, latoPx / 2f, latoPx + titolo.textSize * 0.6f, titolo)
    canvas.drawText(
        "GymBro → Schede → Scansiona QR → Da una foto",
        latoPx / 2f,
        latoPx + titolo.textSize * 0.6f + sotto.textSize * 1.8f,
        sotto
    )
    return bmp
}

private suspend fun condividiImmagine(context: Context, qr: QrPronto, nome: String) {
    val uri = withContext(Dispatchers.IO) {
        val cartella = File(context.cacheDir, "qr").apply { mkdirs() }
        val file = File(cartella, "scheda-gymbro.png")
        val bmp = immagineDaCondividere(qr, nome)
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
    val invio = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_TEXT, "Scheda \"$nome\" per GymBro: aprila da Schede → Scansiona QR → Da una foto.")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    invio.clipData = ClipData.newRawUri(null, uri)
    context.startActivity(Intent.createChooser(invio, "Condividi scheda"))
}

// ---------- Leggere il QR ----------

/**
 * Inquadra un QR con la fotocamera, oppure lo legge da una foto della galleria.
 * [onLetto] riceve il testo e se arriva da una foto; [onFotoSenzaQr] quando nella foto non c'è un QR.
 */
@Composable
internal fun ScansioneQr(
    onLetto: (testo: String, daFoto: Boolean) -> Unit,
    onFotoSenzaQr: () -> Unit,
    onIndietro: () -> Unit,
) {
    BackHandler(onBack = onIndietro)
    val context = LocalContext.current
    fun haPermesso() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    var permesso by remember { mutableStateOf(haPermesso()) }
    var chiesto by remember { mutableStateOf(false) }
    val richiesta = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        permesso = ok
        chiesto = true
    }
    LaunchedEffect(Unit) { if (!permesso) richiesta.launch(Manifest.permission.CAMERA) }
    // Il permesso può essere dato dalle impostazioni di sistema e poi si torna qui.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permesso = haPermesso() }

    val scope = rememberCoroutineScope()
    var leggendoFoto by remember { mutableStateOf(false) }
    val foto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        leggendoFoto = true
        scope.launch {
            val testo = withContext(Dispatchers.Default) {
                try { leggiQrDaFoto(context, uri) } catch (e: Exception) { null }
            }
            leggendoFoto = false
            if (testo == null) onFotoSenzaQr() else onLetto(testo, true)
        }
    }

    Column(Modifier.fillMaxSize()) {
        IntestazioneDettaglio(titolo = "Scansiona QR", sopratitolo = "Scheda condivisa", onIndietro = onIndietro)

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            when {
                leggendoFoto -> CircularProgressIndicator()
                permesso -> {
                    InquadraturaQr(onLetto = { onLetto(it, false) }, modifier = Modifier.fillMaxSize())
                    // Mirino: dove mettere il codice.
                    Box(
                        Modifier
                            .fillMaxWidth(0.7f)
                            .aspectRatio(1f)
                            .border(3.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(24.dp))
                    )
                }
                else -> Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        if (chiesto) "Per inquadrare il QR serve il permesso della fotocamera. " +
                            "Se non compare la richiesta, attivalo dalle impostazioni del telefono. " +
                            "Intanto puoi leggere il QR da una foto."
                        else "Serve il permesso della fotocamera per inquadrare il QR.",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    BottoneSecondario(
                        testo = "Consenti fotocamera",
                        onClick = { richiesta.launch(Manifest.permission.CAMERA) }
                    )
                }
            }
        }

        Text(
            "Inquadra il QR di una scheda GymBro: si apre da solo. Se te l'hanno mandato come immagine, usa \"Da una foto\".",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)
        )
        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            BottoneSecondario(
                testo = "Da una foto",
                onClick = { foto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                icona = Icons.Filled.PhotoLibrary,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Anteprima della fotocamera posteriore che cerca un QR in ogni fotogramma. */
@Composable
private fun InquadraturaQr(onLetto: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val ultimoOnLetto by rememberUpdatedState(onLetto)
    var nonDisponibile by remember { mutableStateOf(false) }
    val vista = remember {
        PreviewView(context).apply {
            // TextureView: convive meglio con gli angoli arrotondati e il mirino disegnati sopra.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner) {
        val principale = ContextCompat.getMainExecutor(context)
        val analisi = Executors.newSingleThreadExecutor()
        val futuro = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var chiuso = false

        futuro.addListener({
            if (chiuso) return@addListener
            try {
                val p = futuro.get().also { provider = it }
                val anteprima = Preview.Builder().build().also { it.setSurfaceProvider(vista.surfaceProvider) }
                val lettore = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
                            )
                            .build()
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                lettore.setAnalyzer(analisi, AnalizzatoreQr { testo ->
                    principale.execute { if (!chiuso) ultimoOnLetto(testo) }
                })
                val camera = if (p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) CameraSelector.DEFAULT_BACK_CAMERA
                else CameraSelector.DEFAULT_FRONT_CAMERA
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, camera, anteprima, lettore)
            } catch (e: Exception) {
                nonDisponibile = true
            }
        }, principale)

        onDispose {
            chiuso = true
            provider?.unbindAll()
            analisi.shutdown()
        }
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        AndroidView(factory = { vista }, modifier = Modifier.fillMaxSize())
        if (nonDisponibile) {
            Text(
                "Fotocamera non disponibile: usa \"Da una foto\".",
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp)
            )
        }
    }
}

/** Cerca un QR nella luminosità (piano Y) di ogni fotogramma. Gira sul thread dell'analisi. */
private class AnalizzatoreQr(private val onLetto: (String) -> Unit) : ImageAnalysis.Analyzer {
    private var luma = ByteArray(0)

    override fun analyze(immagine: ImageProxy) {
        try {
            val w = immagine.width
            val h = immagine.height
            val piano = immagine.planes[0]
            val dati = piano.buffer.apply { rewind() }
            val passo = piano.rowStride
            if (luma.size < w * h) luma = ByteArray(w * h)
            if (passo == w) {
                dati.get(luma, 0, w * h)
            } else {
                // Righe con byte di riempimento alla fine: si copiano una a una.
                for (y in 0 until h) {
                    dati.position(y * passo)
                    dati.get(luma, y * w, w)
                }
            }
            leggiQr(PlanarYUVLuminanceSource(luma, w, h, 0, 0, w, h, false))?.let(onLetto)
        } catch (e: Exception) {
            // Un fotogramma non leggibile non ferma la scansione.
        } finally {
            immagine.close()
        }
    }
}

/** Il testo del QR nella foto [uri], o null. Le foto molto grandi vengono ridotte prima. */
private fun leggiQrDaFoto(context: Context, uri: Uri): String? {
    val sorgente = ImageDecoder.createSource(context.contentResolver, uri)
    val bmp = ImageDecoder.decodeBitmap(sorgente) { decoder, info, _ ->
        // Software: getPixels non funziona sulle bitmap in memoria grafica.
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val lato = maxOf(info.size.width, info.size.height)
        if (lato > 2048) {
            val s = 2048f / lato
            decoder.setTargetSize(
                (info.size.width * s).toInt().coerceAtLeast(1),
                (info.size.height * s).toInt().coerceAtLeast(1)
            )
        }
    }
    val w = bmp.width
    val h = bmp.height
    val pixel = IntArray(w * h)
    bmp.getPixels(pixel, 0, w, 0, 0, w, h)
    bmp.recycle()
    return leggiQr(RGBLuminanceSource(w, h, pixel), accurato = true)
}

// ---------- Anteprima della scheda ricevuta ----------

@Composable
internal fun AnteprimaSchedaCondivisa(
    scheda: SchedaCondivisa,
    nomiLibreria: List<String>,
    nomiSchede: Set<String>,
    salvando: Boolean,
    onSalva: () -> Unit,
    onIndietro: () -> Unit,
) {
    BackHandler(onBack = onIndietro)
    val nuovi = remember(scheda, nomiLibreria) { eserciziNuovi(scheda, nomiLibreria) }
    val nomeFinale = remember(scheda, nomiSchede) { nomeLibero(scheda.nome, nomiSchede) }
    val cs = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize()) {
        IntestazioneDettaglio(titolo = scheda.nome, sopratitolo = "Scheda ricevuta", onIndietro = onIndietro)

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "riassunto") {
                Surface(shape = RoundedCornerShape(20.dp), color = cs.surfaceContainer, border = bordoCard()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "${scheda.esercizi.size} esercizi · ${scheda.esercizi.sumOf { it.serie.size }} serie",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (nuovi.isNotEmpty()) {
                            Text(
                                if (nuovi.size == 1) "1 esercizio non è nella tua libreria: verrà aggiunto."
                                else "${nuovi.size} esercizi non sono nella tua libreria: verranno aggiunti.",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.tertiary
                            )
                        }
                        if (nomeFinale != scheda.nome) {
                            Text(
                                "Hai già una scheda con questo nome: questa sarà salvata come \"$nomeFinale\".",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            scheda.note?.let { note ->
                item(key = "note") {
                    Text(note, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
            }
            itemsIndexed(scheda.esercizi) { i, e ->
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = coloriCard(),
                    border = bordoCard(),
                    elevation = elevazioneCard()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Sopratitolo("Esercizio %02d".format(i + 1))
                        Text(e.nome, style = MaterialTheme.typography.titleMedium)
                        val superset = if (e.superset != null) " · Superset" else ""
                        Text(
                            "${e.gruppo.label} · ${e.modalita.label}$superset",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant
                        )
                        if (e.nome in nuovi) {
                            Text("Nuovo nella tua libreria", style = MaterialTheme.typography.labelMedium, color = cs.tertiary)
                        }
                        e.note?.let { nota ->
                            Text(nota, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(2.dp))
                        e.serie.forEachIndexed { n, s ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${n + 1}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = cs.primary,
                                    modifier = Modifier.width(20.dp)
                                )
                                Text(
                                    riassuntoSerie(e.modalita, s.durataSec, s.ripetizioni, s.pesoKg, s.recuperoSec),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BottonePrincipale(
                testo = if (salvando) "Salvataggio…" else "Salva tra le mie schede",
                onClick = onSalva,
                enabled = !salvando,
                icona = Icons.Filled.Download,
                modifier = Modifier.fillMaxWidth()
            )
            BottoneSecondario(
                testo = "Torna indietro",
                onClick = onIndietro,
                icona = Icons.AutoMirrored.Filled.ArrowBack,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
