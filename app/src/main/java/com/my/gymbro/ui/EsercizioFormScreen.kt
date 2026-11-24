package com.my.gymbro.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.my.gymbro.data.Attrezzatura
import com.my.gymbro.data.Esercizio
import com.my.gymbro.data.GruppoMuscolare
import kotlinx.coroutines.launch

@Composable
fun EsercizioFormScreen(
    iniziale: Esercizio?,
    onSalva: (Esercizio) -> Unit,
    onAnnulla: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var nome by rememberSaveable { mutableStateOf(iniziale?.nome ?: "") }
    var gruppo by rememberSaveable { mutableStateOf(iniziale?.gruppoMuscolare ?: GruppoMuscolare.PETTO) }
    var attrezzo by rememberSaveable { mutableStateOf(iniziale?.attrezzatura ?: Attrezzatura.CORPO_LIBERO) }
    var descrizione by rememberSaveable { mutableStateOf(iniziale?.descrizione ?: "") }
    var immaginePath by rememberSaveable { mutableStateOf(iniziale?.immaginePath) }

    // Percorso persistito all'apertura: serve per ripulire i file orfani al salvataggio/annullamento.
    // rememberSaveable richiede un tipo non-nullable (T : Any): usiamo "" come sentinella di "nessuna immagine".
    val originale = rememberSaveable { iniziale?.immaginePath ?: "" }.ifBlank { null }

    fun puliziaInterim() {
        val corrente = immaginePath
        if (corrente != null && corrente != originale) ImageStorage.elimina(corrente)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val nuovo = ImageStorage.salva(context, uri)
            if (nuovo != null) {
                puliziaInterim() // scarta un'eventuale immagine scelta poco prima e non salvata
                immaginePath = nuovo
            }
        }
    }

    val scarta: () -> Unit = {
        puliziaInterim()
        onAnnulla()
    }

    val modificato = nome != (iniziale?.nome ?: "") ||
        gruppo != (iniziale?.gruppoMuscolare ?: GruppoMuscolare.PETTO) ||
        attrezzo != (iniziale?.attrezzatura ?: Attrezzatura.CORPO_LIBERO) ||
        descrizione != (iniziale?.descrizione ?: "") ||
        immaginePath != originale

    // Indietro/freccia: se ci sono modifiche non salvate si chiede conferma prima di perderle.
    var confermaUscita by rememberSaveable { mutableStateOf(false) }
    val annulla: () -> Unit = { if (modificato) confermaUscita = true else scarta() }
    if (confermaUscita) {
        DialogoScartaModifiche(
            onScarta = { confermaUscita = false; scarta() },
            onContinua = { confermaUscita = false }
        )
    }

    val salva: () -> Unit = {
        // Se l'immagine persistita è stata sostituita o rimossa, elimina il vecchio file.
        if (originale != null && originale != immaginePath) ImageStorage.elimina(originale)
        val base = iniziale ?: Esercizio(nome = "", gruppoMuscolare = gruppo, attrezzatura = attrezzo)
        onSalva(
            base.copy(
                nome = nome.trim(),
                gruppoMuscolare = gruppo,
                attrezzatura = attrezzo,
                descrizione = descrizione.trim().ifBlank { null },
                immaginePath = immaginePath
            )
        )
    }

    BackHandler(onBack = annulla)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BottoneIcona(Icons.AutoMirrored.Filled.ArrowBack, "Indietro", annulla)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Sopratitolo(if (iniziale == null) "Libreria" else iniziale.gruppoMuscolare.label)
            Text(
                (if (iniziale == null) "Nuovo esercizio" else "Modifica esercizio").uppercase(),
                style = MaterialTheme.typography.displaySmall
            )
        }

        ImmaginePicker(
            path = immaginePath,
            onScegli = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onRimuovi = {
                puliziaInterim()
                immaginePath = null
            }
        )

        OutlinedTextField(
            value = nome,
            onValueChange = { nome = it },
            label = { Text("Nome") },
            singleLine = true,
            shape = FormaCampo,
            colors = coloriCampo(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )

        EnumDropdown(
            label = "Gruppo muscolare",
            opzioni = GruppoMuscolare.entries,
            selezionato = gruppo,
            etichetta = { it.label },
            onSelezione = { gruppo = it }
        )

        EnumDropdown(
            label = "Attrezzatura",
            opzioni = Attrezzatura.entries,
            selezionato = attrezzo,
            etichetta = { it.label },
            onSelezione = { attrezzo = it }
        )

        // Muscoli coinvolti (aggiornati in base a nome e gruppo)
        muscoliCoinvolti(nome, gruppo)?.let { mc ->
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = bordoCard(),
                modifier = Modifier.fillMaxWidth().magnesite(RoundedCornerShape(22.dp))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val stretching = gruppo == GruppoMuscolare.STRETCHING
                    Sopratitolo(if (stretching) "Muscoli allungati" else "Muscoli coinvolti", colore = MaterialTheme.colorScheme.primary)
                    MappaMuscoli(mc, Modifier.fillMaxWidth(), allungamento = stretching)
                }
            }
        }

        OutlinedTextField(
            value = descrizione,
            onValueChange = { descrizione = it },
            label = { Text("Descrizione (opzionale)") },
            minLines = 3,
            shape = FormaCampo,
            colors = coloriCampo(),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BottoneSecondario("Annulla", annulla, Modifier.weight(1f).height(56.dp))
            BottonePrincipale(
                testo = "Salva",
                onClick = salva,
                enabled = nome.isNotBlank(),
                icona = Icons.Filled.Check,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ImmaginePicker(
    path: String?,
    onScegli: () -> Unit,
    onRimuovi: () -> Unit,
) {
    if (path != null && path.startsWith("asset://")) {
        // Immagine predefinita: mostra posizione iniziale e finale
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FaseImmagine("Inizio", path, Modifier.weight(1f))
            FaseImmagine("Fine", path.replace("/0.jpg", "/1.jpg"), Modifier.weight(1f))
        }
    } else {
        val shape = RoundedCornerShape(24.dp)
        val bmp = rememberImmagine(path, maxDim = 1024)
        Box(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = "Immagine esercizio",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Filled.AddPhotoAlternate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onScegli) {
            Text(if (path == null) "Scegli immagine" else "Cambia immagine")
        }
        if (path != null) {
            TextButton(onClick = onRimuovi) { Text("Rimuovi") }
        }
    }
}

@Composable
private fun FaseImmagine(label: String, path: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    val bmp = rememberImmagine(path, maxDim = 512)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = "$label esercizio",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Sopratitolo(label)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdown(
    label: String,
    opzioni: List<T>,
    selezionato: T,
    etichetta: (T) -> String,
    onSelezione: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = etichetta(selezionato),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            shape = FormaCampo,
            colors = coloriCampo(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            opzioni.forEach { opz ->
                DropdownMenuItem(
                    text = { Text(etichetta(opz)) },
                    onClick = {
                        onSelezione(opz)
                        expanded = false
                    }
                )
            }
        }
    }
}
