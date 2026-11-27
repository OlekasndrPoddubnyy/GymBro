package com.my.gymbro.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.my.gymbro.GymBroApplication
import com.my.gymbro.data.BackupNonValido
import com.my.gymbro.data.DatiBackup
import com.my.gymbro.data.Tema
import com.my.gymbro.data.codificaBackup
import com.my.gymbro.data.conImmaginiIn
import com.my.gymbro.data.decodificaBackup
import com.my.gymbro.data.eZip
import com.my.gymbro.data.immaginiDaSalvare
import com.my.gymbro.data.leggiBackupZip
import com.my.gymbro.data.scriviBackupZip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Un backup più grande di così non è un file di GymBro: non lo si carica in memoria. */
private const val BYTE_MASSIMI_BACKUP = 50L * 1024 * 1024

sealed interface StatoBackup {
    data object Nessuno : StatoBackup
    data class InCorso(val testo: String) : StatoBackup
    /** [immagini]: foto dello ZIP già estratte, da copiare al loro posto se si conferma (nome → file). */
    data class DaConfermare(val dati: DatiBackup, val immagini: Map<String, File> = emptyMap()) : StatoBackup
    data class Esito(val titolo: String, val testo: String) : StatoBackup
}

class BackupViewModel(private val app: GymBroApplication) : ViewModel() {

    var stato by mutableStateOf<StatoBackup>(StatoBackup.Nessuno)
        private set

    /** Dove si estraggono le foto di uno ZIP in attesa di conferma. */
    private val cartellaImportazione get() = File(app.cacheDir, "importazione_backup")

    fun chiudi() {
        if (stato is StatoBackup.InCorso) return
        cartellaImportazione.deleteRecursively() // foto di uno ZIP non confermato o letto a metà
        stato = StatoBackup.Nessuno
    }

    /** ZIP con il JSON e le foto scelte dall'utente: un telefono nuovo riceve tutto. */
    fun esporta(uri: Uri) = lavora("Esportazione…", "Esportazione non riuscita") {
        val dati = app.repository.leggiPerBackup(app.memoriaBuddy.esporta(), app.impostazioni.predefinitiEliminati)
        val testo = withContext(Dispatchers.Default) { codificaBackup(dati) }
        val foto = withContext(Dispatchers.IO) {
            val immagini = immaginiDaSalvare(dati.esercizi)
            val uscita = app.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("File non scrivibile")
            uscita.use { scriviBackupZip(it, testo, immagini) }
            immagini.size
        }
        StatoBackup.Esito(
            "Dati esportati",
            "${dati.schede.size} schede, ${dati.allenamenti} allenamenti e " +
                (if (foto == 1) "1 foto" else "$foto foto") + " degli esercizi salvati nel file.",
        )
    }

    /** Legge e controlla il file (ZIP, o JSON delle versioni precedenti); niente cambia finché l'utente non conferma. */
    fun leggi(uri: Uri) = lavora("Lettura del file…", "Importazione non riuscita") {
        withContext(Dispatchers.IO) {
            val ingresso = app.contentResolver.openInputStream(uri) ?: throw IOException("File non leggibile")
            BufferedInputStream(ingresso).use { flusso ->
                flusso.mark(4)
                val inizio = flusso.readNBytes(4)
                flusso.reset()
                if (eZip(inizio)) {
                    val zip = leggiBackupZip(flusso, cartellaImportazione)
                    val dati = decodificaBackup(zip.json)
                        .conImmaginiIn(ImageStorage.cartella(app), zip.immagini.keys)
                    StatoBackup.DaConfermare(dati, zip.immagini)
                } else {
                    StatoBackup.DaConfermare(decodificaBackup(leggiConLimite(flusso)))
                }
            }
        }
    }

    fun conferma() {
        val attesa = stato as? StatoBackup.DaConfermare ?: return
        val dati = attesa.dati
        if (AllenamentoEngine.inEsecuzione) {
            stato = StatoBackup.Esito("Allenamento in corso", "Termina l'allenamento prima di importare i dati.")
            return
        }
        lavora("Importazione…", "Importazione non riuscita") {
            val cartella = ImageStorage.cartella(app)
            val fotoPrima = app.repository.eserciziOra().mapNotNullTo(HashSet()) { it.immaginePath }
            // Le foto vanno al loro posto prima del database, che tiene solo quelle che trova.
            val copiate = withContext(Dispatchers.IO) {
                cartella.mkdirs()
                attesa.immagini.mapNotNull { (nome, file) ->
                    File(cartella, nome).takeIf { !it.exists() }?.also { file.copyTo(it) }
                }
            }
            try {
                app.repository.ripristina(dati) { path -> path.startsWith("asset://") || File(path).isFile }
            } catch (e: Exception) {
                withContext(Dispatchers.IO) { copiate.forEach { it.delete() } }
                throw e
            } finally {
                withContext(Dispatchers.IO) { cartellaImportazione.deleteRecursively() }
            }
            // Le foto degli esercizi sostituiti, che nessuno usa più.
            val fotoDopo = dati.esercizi.mapNotNullTo(HashSet()) { it.immaginePath }
            withContext(Dispatchers.IO) { (fotoPrima - fotoDopo).forEach(ImageStorage::elimina) }
            // Un allenamento interrotto si riferiva ai dati appena sostituiti: non si può più riprendere.
            app.ripresa.cancella()
            app.memoriaBuddy.ripristina(dati.buddy)
            app.impostazioni.ripristinaPredefinitiEliminati(dati.predefinitiEliminati)
            // Un backup di una versione precedente può non avere gli esercizi aggiunti dopo.
            val slugs = withContext(Dispatchers.IO) { app.assets.list("esercizi")?.toSet().orEmpty() }
            app.repository.assicuraEserciziPredefiniti(slugs, dati.predefinitiEliminati)
            val foto = attesa.immagini.size
            StatoBackup.Esito(
                "Dati importati",
                "${dati.schede.size} schede e ${dati.allenamenti} allenamenti ripristinati" +
                    (if (foto > 0) ", con ${if (foto == 1) "1 foto" else "$foto foto"} degli esercizi." else "."),
            )
        }
    }

    private fun lavora(testo: String, titoloErrore: String, blocco: suspend () -> StatoBackup) {
        if (stato is StatoBackup.InCorso) return
        stato = StatoBackup.InCorso(testo)
        viewModelScope.launch {
            stato = try {
                blocco()
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupNonValido) {
                StatoBackup.Esito(titoloErrore, e.message.orEmpty())
            } catch (e: Exception) {
                StatoBackup.Esito(titoloErrore, "Non è stato cambiato niente. (${e.message ?: e.javaClass.simpleName})")
            }
        }
    }

    private fun leggiConLimite(ingresso: InputStream): String {
        val byte = ingresso.readNBytes((BYTE_MASSIMI_BACKUP + 1).toInt())
        if (byte.size > BYTE_MASSIMI_BACKUP) throw BackupNonValido("Il file è troppo grande per essere un backup di GymBro.")
        return byte.decodeToString()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                BackupViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as GymBroApplication)
            }
        }
    }
}

/** Menu in alto a destra: tema dell'app ed esportazione/importazione dei dati. */
@Composable
internal fun MenuApp(corrente: Tema, onTema: (Tema) -> Unit) {
    val vm: BackupViewModel = viewModel(factory = BackupViewModel.Factory)
    var aperto by remember { mutableStateOf(false) }
    var crediti by remember { mutableStateOf(false) }
    val salva = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(vm::esporta)
    }
    // Molti file manager non riconoscono il tipo dei .json/.zip: si accetta qualsiasi file e lo si controlla.
    val apri = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::leggi)
    }

    Box {
        IconButton(onClick = { aperto = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
        }
        DropdownMenu(expanded = aperto, onDismissRequest = { aperto = false }) {
            Tema.entries.forEach { t ->
                DropdownMenuItem(
                    text = { Text("Tema ${t.label.lowercase()}") },
                    onClick = { onTema(t); aperto = false },
                    leadingIcon = {
                        if (t == corrente) Icon(Icons.Filled.Check, contentDescription = null)
                    }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Esporta dati") },
                onClick = { aperto = false; salva.launch("gymbro-backup-${LocalDate.now()}.zip") },
                leadingIcon = { Icon(Icons.Filled.FileUpload, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text("Importa dati") },
                onClick = { aperto = false; apri.launch(arrayOf("*/*")) },
                leadingIcon = { Icon(Icons.Filled.FileDownload, contentDescription = null) }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Informazioni") },
                onClick = { aperto = false; crediti = true },
                leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) }
            )
        }
    }

    if (crediti) {
        val context = LocalContext.current
        // Versione e numero di build: dicono quale APK ha installato chi usa l'app.
        val versione = remember {
            runCatching {
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                "${info.versionName} (build ${info.longVersionCode})"
            }.getOrDefault("?")
        }
        AlertDialog(
            onDismissRequest = { crediti = false },
            title = { Text("GymBro") },
            text = {
                Text(
                    "Versione $versione\n\n" +
                        "Cifre del timer: \"Ink number collection\", designed by Freepik (freepik.com).\n\n" +
                        "Font dei titoli: Rock Salt di Font Diner, licenza Apache 2.0."
                )
            },
            confirmButton = { TextButton(onClick = { crediti = false }) { Text("Chiudi") } }
        )
    }

    DialogoBackup(vm)
}

@Composable
private fun DialogoBackup(vm: BackupViewModel) {
    when (val s = vm.stato) {
        StatoBackup.Nessuno -> Unit
        is StatoBackup.InCorso -> AlertDialog(
            onDismissRequest = {},
            icon = { CircularProgressIndicator() },
            text = { Text(s.testo) },
            confirmButton = {},
        )
        is StatoBackup.DaConfermare -> AlertDialog(
            onDismissRequest = vm::chiudi,
            title = { Text("Sostituire tutti i dati?") },
            text = {
                val d = s.dati
                val quando = if (d.creatoIl > 0) {
                    " del " + DateTimeFormatter.ofPattern("d/M/yyyy HH:mm")
                        .format(Instant.ofEpochMilli(d.creatoIl).atZone(ZoneId.systemDefault()))
                } else ""
                Text(
                    "Backup$quando: ${d.esercizi.size} esercizi, ${d.schede.size} schede, ${d.allenamenti} allenamenti.\n\n" +
                        "Schede, storico e Gym Buddy di adesso verranno sostituiti da quelli del file. " +
                        "Se vuoi tenerli, annulla ed esporta prima i dati attuali.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = { TextButton(onClick = vm::conferma) { Text("Sostituisci") } },
            dismissButton = { TextButton(onClick = vm::chiudi) { Text("Annulla") } },
        )
        is StatoBackup.Esito -> AlertDialog(
            onDismissRequest = vm::chiudi,
            title = { Text(s.titolo) },
            text = { Text(s.testo) },
            confirmButton = { TextButton(onClick = vm::chiudi) { Text("OK") } },
        )
    }
}
