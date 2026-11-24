package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.model.CraftProject
import com.example.data.model.SchoolCatalog
import com.example.data.model.SchoolUser
import com.example.ui.components.decodeAttachmentBitmap
import com.example.ui.components.readAttachmentFromUri
import com.example.ui.theme.*
import com.example.ui.viewmodel.CraftAiImageState

private enum class CraftFeedFilter(val label: String) {
    ALL("All Projects"),
    STUDENT("Students"),
    TEACHER("Teachers"),
    WITH_PHOTO("With Photo"),
}

/**
 * Doon Public School Craft & Project Showcase:
 * Allows Students, Teachers, and Principal to share creative craft ideas,
 * science exhibition models, and project skills with real project photos.
 */
@Composable
fun CraftStudioScreen(
    currentUser: SchoolUser,
    projects: List<CraftProject>,
    likedProjectIds: Set<String> = emptySet(),
    likingProjectIds: Set<String> = emptySet(),
    craftAiImageState: CraftAiImageState = CraftAiImageState.Idle,
    generatingPostId: String? = null,
    onGeneratePreviewImage: (title: String, category: String, description: String, materials: String) -> Unit = { _, _, _, _ -> },
    onGenerateCustomAiImage: (prompt: String) -> Unit = {},
    onEditAiImage: (sourceBase64: String, editPrompt: String) -> Unit = { _, _ -> },
    onClearPreviewImage: () -> Unit = {},
    onPublishProject: (CraftProject) -> Unit,
    onGenerateImageForPost: (CraftProject) -> Unit = {},
    onApplaudPost: (CraftProject) -> Unit,
    onDeletePost: (CraftProject) -> Unit,
    onShowMessage: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (onBack != null) {
        BackHandler(onBack = onBack)
    }

    val context = LocalContext.current
    var showComposer by remember { mutableStateOf(true) }
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(SchoolCatalog.craftCategories.first()) }
    var materials by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var manualPhotoBase64 by remember { mutableStateOf("") }
    var aiCreatePrompt by remember { mutableStateOf("") }
    var aiEditPrompt by remember { mutableStateOf("") }
    var feedFilter by remember { mutableStateOf(CraftFeedFilter.ALL) }
    var zoomProject by remember { mutableStateOf<CraftProject?>(null) }

    val aiSuccess = craftAiImageState as? CraftAiImageState.Success
    val effectiveImageBase64 = aiSuccess?.base64Data?.ifBlank { manualPhotoBase64 } ?: manualPhotoBase64
    val effectivePromptUsed = aiSuccess?.promptUsed ?: ""
    val isAiGenerating = craftAiImageState is CraftAiImageState.Loading

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            readAttachmentFromUri(context, uri, forceImage = true)
                .onSuccess {
                    onClearPreviewImage()
                    manualPhotoBase64 = it.base64Data
                    onShowMessage("Project photo attached! You can also edit it with Gemini AI.")
                }
                .onFailure {
                    onShowMessage(it.localizedMessage ?: "Unable to read selected photo.")
                }
        }
    }

    val activePreviewBitmap = remember(effectiveImageBase64) {
        decodeAttachmentBitmap(effectiveImageBase64)
    }

    val filteredProjects = remember(projects, feedFilter) {
        projects.filter { p ->
            when (feedFilter) {
                CraftFeedFilter.ALL -> true
                CraftFeedFilter.STUDENT -> p.authorRole == "STUDENT"
                CraftFeedFilter.TEACHER -> p.authorRole == "TEACHER" || p.authorRole == "ADMIN"
                CraftFeedFilter.WITH_PHOTO -> p.aiImageData.isNotBlank()
            }
        }.sortedWith(
            compareByDescending<CraftProject> { it.createdAt?.seconds ?: 0L }
                .thenByDescending { it.likesCount }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("craft_studio_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Hero Header
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = NavyDeep),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (onBack != null) {
                                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = GoldAmber)
                                }
                            }
                            Column {
                                Text("DOON PUBLIC SCHOOL CREATIVE LAB", style = MaterialTheme.typography.labelSmall, color = GoldAmber)
                                Text("Craft & Science Project Showcase", style = MaterialTheme.typography.titleLarge, color = Color.White)
                            }
                        }
                        FilledTonalButton(
                            onClick = { showComposer = !showComposer },
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = GoldAmber, contentColor = NavyDeep),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("craft_toggle_composer_btn"),
                        ) {
                            Icon(if (showComposer) Icons.Filled.ExpandLess else Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (showComposer) "Hide" else "Post Project")
                        }
                    }
                    Text(
                        "Students & Teachers can share Art, Craft & Science exhibition projects with real photos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
        }

        // Project Composer
        item {
            AnimatedVisibility(visible = showComposer) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Post New Craft / Science Project",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Craft / Project Title") },
                            placeholder = { Text("e.g. Hydraulic Robotic Arm from Cardboard") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("craft_title_input"),
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            SchoolCatalog.craftCategories.forEach { cat ->
                                FilterChip(
                                    selected = category == cat,
                                    onClick = { category = cat },
                                    label = { Text(cat) },
                                )
                            }
                        }

                        OutlinedTextField(
                            value = materials,
                            onValueChange = { materials = it },
                            label = { Text("Materials / Tools Used") },
                            placeholder = { Text("e.g. Syringes, cardboard, straws, LEDs") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("craft_materials_input"),
                        )

                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Project Description & How It Works") },
                            placeholder = { Text("Describe steps, materials, or science concept...") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth().testTag("craft_desc_input"),
                        )

                        OutlinedButton(
                            onClick = {
                                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            enabled = !isAiGenerating,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("craft_attach_photo_btn"),
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = "Pick Photo", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (effectiveImageBase64.isNotBlank()) "Pick Different Photo from Gallery" else "Attach Photo from Gallery")
                        }

                        // Gemini AI Image Studio (Create & Edit with gemini-nano-banana-2.1)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, IndigoAccent.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = IndigoAccent, modifier = Modifier.size(18.dp))
                                    Text(
                                        "AI Image Creator & Editor (Gemini)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                OutlinedTextField(
                                    value = aiCreatePrompt,
                                    onValueChange = { aiCreatePrompt = it.take(400) },
                                    label = { Text("Create Image Prompt (or leave blank to use project title)") },
                                    placeholder = { Text("e.g. Working solar-powered model village for science exhibition") },
                                    singleLine = false,
                                    maxLines = 2,
                                    modifier = Modifier.fillMaxWidth().testTag("craft_ai_create_prompt"),
                                )
                                FilledTonalButton(
                                    onClick = {
                                        if (aiCreatePrompt.isNotBlank()) {
                                            onGenerateCustomAiImage(aiCreatePrompt.trim())
                                        } else {
                                            onGeneratePreviewImage(title, category, description, materials)
                                        }
                                    },
                                    enabled = !isAiGenerating,
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = IndigoAccent,
                                        contentColor = Color.White,
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("craft_ai_create_btn"),
                                ) {
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Create Image with AI")
                                }

                                if (isAiGenerating) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.padding(vertical = 4.dp),
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = IndigoAccent,
                                        )
                                        Text(
                                            "Generating / editing image with Gemini (may take 30–60s)...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                val aiErr = craftAiImageState as? CraftAiImageState.Error
                                if (aiErr != null) {
                                    Surface(
                                        color = RoseContainer,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth().testTag("craft_ai_error_box"),
                                    ) {
                                        Text(
                                            aiErr.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = RoseError,
                                            modifier = Modifier.padding(10.dp),
                                        )
                                    }
                                }
                            }
                        }

                        if (activePreviewBitmap != null) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.5.dp, GoldAmber),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Image(
                                        bitmap = activePreviewBitmap,
                                        contentDescription = "Project preview image",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(210.dp)
                                            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(
                                                if (aiSuccess != null) Icons.Filled.AutoAwesome else Icons.Filled.PhotoCamera,
                                                contentDescription = null,
                                                tint = GoldAmberDark,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Text(
                                                if (aiSuccess != null) "AI Image Ready" else "Project Photo Ready",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                        TextButton(
                                            onClick = {
                                                manualPhotoBase64 = ""
                                                aiEditPrompt = ""
                                                onClearPreviewImage()
                                            },
                                        ) { Text("Remove") }
                                    }

                                    // Edit Current Image with Gemini AI
                                    Column(
                                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        OutlinedTextField(
                                            value = aiEditPrompt,
                                            onValueChange = { aiEditPrompt = it.take(350) },
                                            label = { Text("Edit This Image with AI") },
                                            placeholder = { Text("e.g. Add warm sunlight, make background a science fair") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth().testTag("craft_ai_edit_prompt"),
                                        )
                                        OutlinedButton(
                                            onClick = {
                                                onEditAiImage(effectiveImageBase64, aiEditPrompt.trim())
                                            },
                                            enabled = !isAiGenerating && aiEditPrompt.isNotBlank(),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 48.dp)
                                                .testTag("craft_ai_edit_btn"),
                                        ) {
                                            Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Apply AI Edit to Current Image")
                                        }
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                if (title.isBlank() || description.isBlank()) {
                                    onShowMessage("Please enter a project title and description before posting.")
                                    return@Button
                                }
                                val classInfo = when {
                                    currentUser.className.isNotBlank() -> "${currentUser.className}-${currentUser.section}"
                                    currentUser.subject.isNotBlank() -> currentUser.subject
                                    else -> currentUser.wing
                                }
                                onPublishProject(
                                    CraftProject(
                                        title = title.trim(),
                                        category = category,
                                        description = description.trim(),
                                        materials = materials.trim(),
                                        authorDocId = currentUser.id,
                                        authorName = currentUser.fullName,
                                        authorUserId = currentUser.userId,
                                        authorRole = currentUser.role,
                                        classInfo = classInfo,
                                        dateLabel = SchoolCatalog.todayIso(),
                                        aiImageData = effectiveImageBase64,
                                        aiPromptUsed = effectivePromptUsed,
                                        likesCount = 0L,
                                    )
                                )
                                title = ""
                                materials = ""
                                description = ""
                                manualPhotoBase64 = ""
                                aiCreatePrompt = ""
                                aiEditPrompt = ""
                                onClearPreviewImage()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = GoldAmber),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("craft_publish_btn"),
                        ) {
                            Icon(Icons.Filled.Publish, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Publish Project to School Feed")
                        }
                    }
                }
            }
        }

        // Feed Filters
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "SCHOOL CRAFT & PROJECT SHOWCASE (${filteredProjects.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    CraftFeedFilter.entries.forEach { f ->
                        FilterChip(
                            selected = feedFilter == f,
                            onClick = { feedFilter = f },
                            label = { Text(f.label) },
                        )
                    }
                }
            }
        }

        if (filteredProjects.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Filled.Brush, contentDescription = null, tint = GoldAmberDark, modifier = Modifier.size(34.dp))
                        Text("No projects in this filter yet", style = MaterialTheme.typography.titleMedium)
                        Text("Share a craft idea or science exhibition project above!", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(filteredProjects, key = { it.id }) { project ->
                val bmp = remember(project.aiImageData) { decodeAttachmentBitmap(project.aiImageData) }
                val isTeacherPost = project.authorRole == "TEACHER" || project.authorRole == "ADMIN"
                val canDelete = currentUser.role == "ADMIN" || project.authorDocId == currentUser.id

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("craft_post_card_${project.id}"),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f),
                            ) {
                                Surface(
                                    color = if (isTeacherPost) IndigoAccent else GoldSoft,
                                    shape = RoundedCornerShape(6.dp),
                                ) {
                                    Text(
                                        text = if (isTeacherPost) "TEACHER" else "STUDENT",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isTeacherPost) Color.White else NavyDeep,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    )
                                }
                                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)) {
                                    Text(
                                        project.category,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    )
                                }
                            }
                            Text(project.dateLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Text(project.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(project.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        if (project.materials.isNotBlank()) {
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(Icons.Filled.Build, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                    Text("Materials: ${project.materials}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }

                        if (bmp != null) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, GoldAmber.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { zoomProject = project },
                            ) {
                                Image(
                                    bitmap = bmp,
                                    contentDescription = "Photo for ${project.title}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(210.dp),
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "By ${project.authorName} (${project.classInfo})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )

                            val isLiked = project.id in likedProjectIds
                            val isLiking = project.id in likingProjectIds
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilledTonalButton(
                                    onClick = { onApplaudPost(project) },
                                    enabled = !isLiking,
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = if (isLiked) GoldSoft else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (isLiked) NavyDeep else MaterialTheme.colorScheme.onSurface,
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("craft_like_btn_${project.id}"),
                                ) {
                                    Icon(
                                        Icons.Filled.ThumbUp,
                                        contentDescription = if (isLiked) "Unlike project (liked)" else "Like project",
                                        tint = if (isLiked) GoldAmberDark else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (isLiked) "Liked • ${project.likesCount}" else "Like • ${project.likesCount}",
                                        fontWeight = if (isLiked) FontWeight.Bold else FontWeight.Medium,
                                    )
                                }
                                if (canDelete) {
                                    IconButton(onClick = { onDeletePost(project) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete project", tint = RoseError)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    zoomProject?.let { proj ->
        val fullBmp = remember(proj.aiImageData) { decodeAttachmentBitmap(proj.aiImageData) }
        if (fullBmp != null) {
            Dialog(onDismissRequest = { zoomProject = null }) {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.Black)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                proj.title,
                                color = Color.White,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { zoomProject = null }) {
                                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                            }
                        }
                        Image(
                            bitmap = fullBmp,
                            contentDescription = proj.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 240.dp, max = 440.dp),
                        )
                        Text(
                            "Project by ${proj.authorName} • ${proj.category}",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoldAmber,
                        )
                    }
                }
            }
        }
    }
}
