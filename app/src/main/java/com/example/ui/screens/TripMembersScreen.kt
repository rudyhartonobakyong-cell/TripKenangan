package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.collab.SharedTrip
import com.example.collab.TripMember
import com.example.ui.TripBuddyViewModel
import com.example.ui.components.MemberAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripMembersScreen(
    trip: SharedTrip,
    members: List<TripMember>,
    currentUserId: String,
    isSubmitting: Boolean,
    onBack: () -> Unit,
    onInviteClick: () -> Unit,
    onTransferAdmin: (TripMember) -> Unit,
    onLeaveGroup: () -> Unit,
    onDeleteGroup: () -> Unit
) {
    BackHandler(onBack = onBack)

    val isCurrentUserOwner = TripBuddyViewModel.canDeleteGroup(trip, currentUserId)
    val memberCount = maxOf(members.size, trip.memberUids.size)
    val otherMembers = members.filter { it.uid != currentUserId && it.uid.isNotBlank() }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteConfirmInput by remember { mutableStateOf("") }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showOwnerLeaveBlockedDialog by remember { mutableStateOf(false) }
    var pendingAdminTransferTarget by remember { mutableStateOf<TripMember?>(null) }
    var showTransferPickerDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Halaman Grup",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "${trip.title} • $memberCount anggota",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("members_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Kembali"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Groups,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = trip.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Text(
                                text = "Kode Undangan: ${trip.inviteCode} • $memberCount anggota",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = if (isCurrentUserOwner) {
                                    "Peran Anda: Admin (Pemilik Grup)"
                                } else {
                                    "Peran Anda: Anggota"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }

                item {
                    Button(
                        onClick = onInviteClick,
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("members_invite_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GroupAdd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Undang anggota (${trip.inviteCode})")
                    }
                }

                item {
                    Text(
                        text = "Daftar Anggota ($memberCount)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                items(members, key = { it.uid.ifEmpty { it.id } }) { member ->
                    val isOwner = member.uid == trip.ownerId || member.role == "owner"
                    val isSelf = member.uid == currentUserId
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("member_row_${member.uid}")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                MemberAvatar(
                                    name = member.displayName,
                                    photoUrl = member.photoUrl,
                                    sizeDp = 48
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = if (isSelf) "${member.displayName} (Anda)" else member.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (isOwner) "Pemilik / Admin Grup" else "Anggota",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isOwner) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isOwner) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ) {
                                    Text(
                                        text = if (isOwner) "Admin" else "Anggota",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isOwner) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            if (isCurrentUserOwner && !isSelf) {
                                OutlinedButton(
                                    onClick = { pendingAdminTransferTarget = member },
                                    enabled = !isSubmitting,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("transfer_admin_button_${member.uid}"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AdminPanelSettings,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Jadikan Admin")
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Pengaturan Keanggotaan & Grup",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (isCurrentUserOwner) {
                                Text(
                                    text = if (otherMembers.isNotEmpty()) {
                                        "Sebagai pemilik (admin), Anda harus menyerahkan peran admin ke anggota lain terlebih dahulu sebelum keluar, atau hapus grup secara permanen."
                                    } else {
                                        "Anda adalah pemilik (admin) satu-satunya di grup ini. Untuk mengakhiri grup, gunakan tombol Hapus Grup."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                OutlinedButton(
                                    onClick = { showOwnerLeaveBlockedDialog = true },
                                    enabled = !isSubmitting,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("owner_leave_guard_button"),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ExitToApp,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Keluar dari Grup")
                                }

                                Button(
                                    onClick = {
                                        deleteConfirmInput = ""
                                        showDeleteDialog = true
                                    },
                                    enabled = !isSubmitting,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("delete_group_button"),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    if (isSubmitting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onError
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteForever,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Hapus Grup")
                                    }
                                }
                            } else {
                                Text(
                                    text = "Jika keluar, Anda akan dihapus dari daftar anggota grup ini.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                OutlinedButton(
                                    onClick = { showLeaveDialog = true },
                                    enabled = !isSubmitting,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("leave_group_button"),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    if (isSubmitting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.ExitToApp,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Keluar dari Grup")
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(12.dp)) }
            }
        }
    }

    if (showDeleteDialog) {
        val isConfirmationValid = TripBuddyViewModel.isValidDeleteGroupConfirmation(
            typedName = deleteConfirmInput,
            groupName = trip.title
        )
        AlertDialog(
            onDismissRequest = { if (!isSubmitting) showDeleteDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Hapus Grup Permanen?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Semua data grup \"${trip.title}\" (daftar anggota, lokasi kunjungan grup, komentar tanggal, dan kode undangan ${trip.inviteCode}) akan dihapus permanen dari Firestore.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Ketik nama grup \"${trip.title}\" untuk melanjutkan:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    OutlinedTextField(
                        value = deleteConfirmInput,
                        onValueChange = { deleteConfirmInput = it },
                        singleLine = true,
                        placeholder = { Text(trip.title) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("delete_group_confirm_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.error,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteGroup()
                    },
                    enabled = isConfirmationValid && !isSubmitting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_delete_group_button")
                ) {
                    Text("Hapus Grup")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    enabled = !isSubmitting
                ) {
                    Text("Batal")
                }
            }
        )
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSubmitting) showLeaveDialog = false },
            title = { Text("Keluar dari Grup?") },
            text = {
                Text(
                    text = "Anda yakin ingin keluar dari grup \"${trip.title}\"? Anda dapat bergabung lagi nanti menggunakan kode undangan."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLeaveDialog = false
                        onLeaveGroup()
                    },
                    enabled = !isSubmitting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_leave_group_button")
                ) {
                    Text("Keluar dari Grup")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLeaveDialog = false },
                    enabled = !isSubmitting
                ) {
                    Text("Batal")
                }
            }
        )
    }

    if (showOwnerLeaveBlockedDialog) {
        AlertDialog(
            onDismissRequest = { showOwnerLeaveBlockedDialog = false },
            title = { Text("Pemilik Tidak Dapat Langsung Keluar") },
            text = {
                Text(
                    text = if (otherMembers.isNotEmpty()) {
                        "Anda adalah admin grup \"${trip.title}\". Serahkan peran admin ke anggota lain terlebih dahulu sebelum keluar, atau hapus grup ini."
                    } else {
                        "Anda adalah pemilik satu-satunya di grup \"${trip.title}\". Gunakan \"Hapus Grup\" jika ingin mengakhiri grup ini."
                    }
                )
            },
            confirmButton = {
                if (otherMembers.isNotEmpty()) {
                    Button(
                        onClick = {
                            showOwnerLeaveBlockedDialog = false
                            showTransferPickerDialog = true
                        },
                        modifier = Modifier.testTag("pick_new_admin_button")
                    ) {
                        Text("Serahkan Admin")
                    }
                } else {
                    Button(
                        onClick = {
                            showOwnerLeaveBlockedDialog = false
                            deleteConfirmInput = ""
                            showDeleteDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("Hapus Grup")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showOwnerLeaveBlockedDialog = false }) {
                    Text("Tutup")
                }
            }
        )
    }

    if (showTransferPickerDialog) {
        AlertDialog(
            onDismissRequest = { showTransferPickerDialog = false },
            title = { Text("Pilih Admin Baru") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Pilih anggota yang akan menjadi pemilik/admin baru grup \"${trip.title}\":",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    otherMembers.forEach { candidate ->
                        OutlinedButton(
                            onClick = {
                                showTransferPickerDialog = false
                                pendingAdminTransferTarget = candidate
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("pick_admin_candidate_${candidate.uid}"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(candidate.displayName)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTransferPickerDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    pendingAdminTransferTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!isSubmitting) pendingAdminTransferTarget = null },
            title = { Text("Serahkan Peran Admin?") },
            text = {
                Text(
                    text = "${target.displayName} akan menjadi admin baru grup \"${trip.title}\". Anda akan menjadi anggota biasa dan dapat keluar dari grup kapan saja."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val chosen = target
                        pendingAdminTransferTarget = null
                        onTransferAdmin(chosen)
                    },
                    enabled = !isSubmitting,
                    modifier = Modifier.testTag("confirm_transfer_admin_button")
                ) {
                    Text("Serahkan Admin")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingAdminTransferTarget = null },
                    enabled = !isSubmitting
                ) {
                    Text("Batal")
                }
            }
        )
    }
}
