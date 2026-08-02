package com.withcareer.screenpal_android.ui_v2.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.withcareer.screenpal_android.data.room.InstructionSetEntity

import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun V2Greeting(
    quickCommands: List<InstructionSetEntity>,
    onSampleClick: (String) -> Unit,
    onRunQuickCommand: (String) -> Unit
) {
    val samples = listOf(
        "用美团外卖帮我点杯蜜雪冰城",
        "用微信给主页第一个联系人发消息\"你好👋\"",
        "打开抖音搜索蔡徐坤并给第一个视频点赞",
        "使用 12306 帮我订一张深圳到长沙的火车票"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 360.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "你好，我是阿宝Pal",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "把你想做的事告诉我，我来帮你完成",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(1.dp),
                horizontalAlignment = Alignment.Start
            ) {
                if (quickCommands.isEmpty()) {
                    samples.forEach { sample ->
                        OutlinedCard(
                            modifier = Modifier
                                .wrapContentWidth()
                                .widthIn(max = 340.dp),
                            onClick = { onSampleClick(sample) }
                        ) {
                            Text(
                                text = sample,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                } else {
                    quickCommands.take(6).forEach { cmd ->
                        OutlinedCard(
                            modifier = Modifier
                                .wrapContentWidth()
                                .widthIn(max = 340.dp),
                            onClick = { onRunQuickCommand(cmd.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = cmd.title.ifBlank { "未命名指令" },
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                IconButton(onClick = { onRunQuickCommand(cmd.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "播放"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
