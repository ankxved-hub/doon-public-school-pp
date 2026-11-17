package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.SchoolCatalog
import com.example.data.model.SchoolInfo
import com.example.data.model.SchoolUser
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AppCreditsScreen(
    user: SchoolUser,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onContinue() }

    val todayIso = SchoolCatalog.todayIso()
    val todayReadable = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.US).format(Date())
    val specialDayInfo = when {
        todayIso.endsWith("-11-17") -> Triple(
            "Happy International Students' Day!",
            "17 November • Celebrating students' curiosity, dreams, and brighter future!",
            R.drawable.poster_students_day_nov17,
        )
        todayIso.endsWith("-11-15") -> Triple(
            "Happy National Philanthropy Day!",
            "15 November • Celebrating generosity, kindness, and social responsibility.",
            R.drawable.poster_philanthropy_day_nov15,
        )
        else -> null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(NavyDeep, NavyRoyal, NavyDeep)))
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 20.dp)
                .widthIn(max = 600.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Real School Campus Photo & Crest Banner
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
                border = BorderStroke(1.dp, GoldAmber.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.dps_etah_real_building_alt),
                        contentDescription = "Doon Public School Etah Real Campus Building",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(145.dp),
                    )
                    Surface(
                        color = Color.White,
                        shape = CircleShape,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .padding(10.dp)
                            .size(52.dp)
                            .align(Alignment.BottomStart),
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.dps_real_logo),
                            contentDescription = "Doon Public School Official Logo",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp)
                                .clip(CircleShape),
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = SchoolInfo.NAME.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = GoldAmber,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Welcome, ${user.fullName}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Surface(
                    color = GoldAmber.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, GoldAmber.copy(alpha = 0.6f)),
                ) {
                    Text(
                        text = "${user.role.replace('_', ' ')} • ${user.userId} • $todayReadable",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = GoldAmber,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }

            if (specialDayInfo != null) {
                val (dayTitle, daySubtitle, posterRes) = specialDayInfo
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.5.dp, GoldAmber),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Image(
                            painter = painterResource(id = posterRes),
                            contentDescription = dayTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(74.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Surface(color = GoldSoft, shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    text = "TODAY'S SPECIAL DAY",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = GoldAmberDark,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                            Text(
                                text = dayTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = daySubtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Main Credits Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.5.dp, GoldAmber.copy(alpha = 0.75f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Lead Creator Highlight
                    Surface(
                        color = NavyDeep,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(
                                color = GoldAmber,
                                shape = CircleShape,
                                modifier = Modifier.size(44.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Filled.Code,
                                        contentDescription = null,
                                        tint = NavyDeep,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "APP MADE BY",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = GoldAmber,
                                )
                                Text(
                                    text = "Vedant Pratap Singh",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                            Icon(
                                Icons.Filled.Verified,
                                contentDescription = "Lead Developer",
                                tint = GoldAmber,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    // Co-Developers Section
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Filled.Groups,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "CO-DEVELOPERS",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        listOf(
                            "Dev Pratap Singh",
                            "Ankur Kumar Gupta",
                            "Naitik Yadav",
                        ).forEach { coDev ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.LaptopMac,
                                        contentDescription = null,
                                        tint = GoldAmberDark,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        text = coDev,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    // Special Thanks & Teachers Gratitude Section
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Filled.WorkspacePremium,
                                contentDescription = null,
                                tint = GoldAmberDark,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "SPECIAL THANKS TO OUR MENTORS",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = GoldAmberDark,
                            )
                        }

                        Surface(
                            color = GoldSoft.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, GoldAmber.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                listOf(
                                    "Sudhakar Ojha Sir",
                                    "Uday Raj Sir",
                                    "Akash Sir",
                                ).forEach { mentor ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            Icons.Filled.Star,
                                            contentDescription = null,
                                            tint = GoldAmberDark,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Text(
                                            text = mentor,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = NavyDeep,
                                        )
                                    }
                                }
                                HorizontalDivider(
                                    color = GoldAmber.copy(alpha = 0.35f),
                                    modifier = Modifier.padding(vertical = 2.dp),
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Favorite,
                                        contentDescription = null,
                                        tint = RoseError,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = "& All Respected Teachers",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        fontStyle = FontStyle.Italic,
                                        color = NavyDeep,
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = onContinue,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NavyDeep,
                            contentColor = GoldAmber,
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("credits_continue_btn"),
                    ) {
                        Text(
                            text = "Continue to Dashboard",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            Text(
                text = "“${SchoolInfo.MOTTO}”",
                style = MaterialTheme.typography.labelSmall,
                fontStyle = FontStyle.Italic,
                color = GoldAmber.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
