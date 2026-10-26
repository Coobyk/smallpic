package com.coobyk.smallpic

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coobyk.smallpic.MainViewModel.UiState
import com.coobyk.smallpic.ui.theme.SmallPicTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val pickImages = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(pickerLimit())
    ) { uris -> if (uris.isNotEmpty()) viewModel.optimize(uris) }

    private fun pickerLimit(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            MediaStore.getPickImagesMaxLimit().coerceIn(2, 30)
        } else {
            9
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val darkTheme = isSystemInDarkTheme()
            SmallPicTheme(darkTheme = darkTheme, dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Screen()
                }
            }
        }
    }

    @Composable
    private fun Screen() {
        val state by viewModel.state.collectAsStateWithLifecycle()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Spacer(Modifier.height(28.dp))
            Header()

            if (state.hasResults || state.optimizing) {
                Spacer(Modifier.height(28.dp))
                PickButton(enabled = !state.optimizing && !state.saving) {
                    pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }

                if (state.optimizing || state.saving) {
                    Spacer(Modifier.height(24.dp))
                    ProgressBlock(state)
                }

                if (state.hasResults) {
                    Spacer(Modifier.height(24.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.outcomes.forEach { outcome -> ResultCard(outcome) }
                    }
                    Spacer(Modifier.height(28.dp))
                    ActionsRow(state)
                }
            } else {
                // Nothing picked yet: one big tap area instead of a button
                // plus a separate empty-state card.
                Spacer(Modifier.height(24.dp))
                PickHero(modifier = Modifier.weight(1f)) {
                    pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
        }
    }

    @Composable
    private fun Header() {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The launcher foreground is white artwork on a 108dp viewport;
            // oversized and clipped so the mark fills the badge.
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    @Composable
    private fun PickButton(enabled: Boolean, onClick: () -> Unit) {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.pick_images),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }

    @Composable
    private fun ProgressBlock(state: UiState) {
        val label = if (state.saving) {
            stringResource(R.string.saving)
        } else {
            stringResource(R.string.optimizing, state.progressDone, state.progressTotal)
        }
        val fraction = if (state.progressTotal > 0) {
            state.progressDone.toFloat() / state.progressTotal
        } else {
            0f
        }
        val animatedFraction by animateFloatAsState(
            targetValue = fraction,
            animationSpec = spring(stiffness = 200f),
            label = "progress",
        )
        Column {
            LinearProgressIndicator(
                progress = { animatedFraction },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    @Composable
    private fun ResultCard(outcome: MainViewModel.Outcome) {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = outcome.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (outcome.compressed != null) {
                        Text(
                            text = "${viewModel.format(outcome.originalBytes)} → ${viewModel.format(outcome.compressed.size)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (outcome.originalBytes == 0) {
                        Text(
                            text = stringResource(R.string.row_failed_reason),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.row_skipped_reason),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                val (badgeText, badgeContainer, badgeContent) = when {
                    outcome.compressed != null -> {
                        val smaller = 100 - (outcome.compressed.size * 100 / outcome.originalBytes)
                        Triple(
                            stringResource(R.string.row_gain, smaller),
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    outcome.originalBytes == 0 -> Triple(
                        stringResource(R.string.row_failed_label),
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.onErrorContainer,
                    )
                    else -> Triple(
                        stringResource(R.string.row_skipped_label),
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(badgeContainer)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelMedium,
                        color = badgeContent,
                    )
                }
            }
        }
    }

    @Composable
    private fun PickHero(modifier: Modifier = Modifier, onClick: () -> Unit) {
        Card(
            onClick = onClick,
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            modifier = modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.pick_images),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    @Composable
    private fun ActionsRow(state: UiState) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    viewModel.save { saved ->
                        Toast.makeText(
                            applicationContext,
                            getString(R.string.saved_toast, saved),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                },
                enabled = state.ready,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.save), style = MaterialTheme.typography.labelLarge)
            }
            FilledTonalButton(
                onClick = {
                    viewModel.share { uris -> if (uris.isNotEmpty()) share(uris) }
                },
                enabled = state.ready,
                shape = CircleShape,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.share), style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    private fun share(uris: List<Uri>) {
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uris[0])
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/jpeg"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        }.apply { addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        startActivity(Intent.createChooser(intent, getString(R.string.share)))
    }
}
