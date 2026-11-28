package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.collab.SharedTrip
import com.example.collab.TripMember
import com.example.ui.SpaceMode

@Composable
fun CollabWorkspaceBar(
    spaceMode: SpaceMode,
    showGroupDirectory: Boolean,
    activeSpaceTitle: String,
    activeSharedTrip: SharedTrip?,
    activeTripMembers: List<TripMember>,
    userName: String,
    userPhotoUrl: String,
    onSelectSpaceMode: (SpaceMode) -> Unit,
    onOpenGroupDirectory: () -> Unit,
    onOpenMembersClick: () -> Unit,
    onInviteClick: () -> Unit,
    onAccountClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("workspace_segmented_control")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SpaceSegmentItem(
                            label = "Pribadi",
                            icon = Icons.Rounded.Smartphone,
                            selected = spaceMode == SpaceMode.PRIBADI,
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { onSelectSpaceMode(SpaceMode.PRIBADI) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("segment_pribadi")
                        )

                        SpaceSegmentItem(
                            label = "Grup",
                            icon = Icons.Rounded.Groups,
                            selected = spaceMode == SpaceMode.GRUP,
                            selectedContainerColor = MaterialTheme.colorScheme.tertiary,
                            selectedContentColor = MaterialTheme.colorScheme.onTertiary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { onSelectSpaceMode(SpaceMode.GRUP) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("segment_grup")
                        )
                    }
                }

                IconButton(
                    onClick = onAccountClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("open_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Pengaturan dan diagnostik",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onAccountClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("account_profile_button")
                ) {
                    MemberAvatar(
                        name = userName,
                        photoUrl = userPhotoUrl,
                        sizeDp = 34
                    )
                }
            }

            val isGroupAccent = spaceMode == SpaceMode.GRUP
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isGroupAccent) {
                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isGroupAccent) {
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (isGroupAccent) Icons.Rounded.Groups else Icons.Rounded.Smartphone,
                            contentDescription = null,
                            tint = if (isGroupAccent) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = activeSpaceTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isGroupAccent) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("active_space_title")
                        )

                        if (isGroupAccent && !showGroupDirectory && activeSharedTrip != null) {
                            val previewMembers = activeTripMembers.take(3)
                            if (previewMembers.isNotEmpty()) {
                                Row {
                                    previewMembers.forEachIndexed { idx, member ->
                                        MemberAvatar(
                                            name = member.displayName,
                                            photoUrl = member.photoUrl,
                                            sizeDp = 24,
                                            modifier = Modifier.offset(x = (-4 * idx).dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (isGroupAccent && !showGroupDirectory && activeSharedTrip != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = onOpenGroupDirectory,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("open_group_directory_button"),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SwapHoriz,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Ganti Grup",
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            OutlinedButton(
                                onClick = onOpenMembersClick,
                                modifier = Modifier
                                    .weight(1.1f)
                                    .height(48.dp)
                                    .testTag("open_members_screen_button"),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ManageAccounts,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Halaman Grup",
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            OutlinedButton(
                                onClick = onInviteClick,
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("invite_members_button"),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.GroupAdd,
                                    contentDescription = "Undang anggota",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Undang",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpaceSegmentItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    selectedContainerColor: Color,
    selectedContentColor: Color,
    unselectedContentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) selectedContainerColor else Color.Transparent,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) selectedContentColor else unselectedContentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) selectedContentColor else unselectedContentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
