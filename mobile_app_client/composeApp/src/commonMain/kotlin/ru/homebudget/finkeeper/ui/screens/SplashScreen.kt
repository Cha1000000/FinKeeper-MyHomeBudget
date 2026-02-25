package ru.homebudget.finkeeper.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import finkeeper.composeapp.generated.resources.Res
import finkeeper.composeapp.generated.resources.app_icon
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.homebudget.finkeeper.ui.components.GlassyCard
import ru.homebudget.finkeeper.ui.Strings

@Composable
fun SplashScreen(
    version: String = ru.homebudget.finkeeper.BuildConfig.APP_VERSION
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        GlassyCard(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            shape = RoundedCornerShape(32.dp),
            baseColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
            highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Icon
                Image(
                    painter = painterResource(Res.drawable.app_icon),
                    contentDescription = "App Icon",
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .size(96.dp)
                )

                // App Name
                Text(
                    text = Strings.APP_TITLE,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 40.sp,
                        letterSpacing = 2.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = Strings.APP_SUBTITLE,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                )
            }
        }

        // Version at the bottom
        Text(
            text = version,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )
    }
}
